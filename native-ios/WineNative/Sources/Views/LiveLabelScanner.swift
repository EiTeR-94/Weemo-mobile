import AVFoundation
import SwiftUI
import UIKit
import Vision

/// Caméra live : détecte texte stable → capture auto **croppée au cadre guide**.
struct LiveLabelScanner: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> LiveLabelScannerVC {
        let vc = LiveLabelScannerVC()
        vc.onCapture = onImage
        vc.onCancel = onCancel
        return vc
    }

    func updateUIViewController(_ uiViewController: LiveLabelScannerVC, context: Context) {}
}

final class LiveLabelScannerVC: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onCapture: ((UIImage) -> Void)?
    var onCancel: (() -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "weeno.label.scan")
    private var preview: AVCaptureVideoPreviewLayer?
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()

    private let statusLabel = UILabel()
    private let guideView = UIView()
    private let cancelBtn = UIButton(type: .system)
    private let shutterBtn = UIButton(type: .system)

    private var frameCounter = 0
    private var stableCount = 0
    private var capturing = false
    private var fired = false
    /// Assez de temps pour cadrer avant capture (à ~3-4 frames analysées/sec avec
    /// analyzeEveryN=3, 7 frames stables ≈ 2s — contre 4 ≈ 1-1,2s, beaucoup trop court,
    /// la capture partait avant que l'utilisateur ait fini de cadrer/stabiliser).
    private let minStableFrames = 7
    private let analyzeEveryN = 3
    private let minChars = 10
    private let minLines = 2
    /// Une page de livre/document a beaucoup plus de texte qu'une étiquette de vin
    /// dans le cadre — filtre les faux positifs "texte présent mais pas une étiquette".
    private let maxChars = 200
    private let maxLines = 10
    /// Une étiquette a toujours une ligne "titre" (nom du vin/producteur) nettement
    /// plus grande que le reste ; une page de texte uniforme n'en a aucune. Relevé un
    /// peu pour exiger que l'étiquette remplisse vraiment le cadre.
    private let minHeadlineHeight: CGFloat = 0.06
    /// Mots-clés très caractéristiques d'un produit qui N'EST PAS du vin (compléments
    /// alimentaires, cosmétiques, nourriture emballée) — un pot de créatine a le même
    /// profil "headline + texte structuré" qu'une étiquette de vin pour les heuristiques
    /// ci-dessus, donc la forme seule ne suffit pas à les distinguer. La mention légale
    /// obligatoire (tableau nutritionnel, "complément alimentaire"…) si.
    private let nonWineKeywords = [
        "COMPLEMENT ALIMENTAIRE", "COMPLÉMENT ALIMENTAIRE", "DIETARY SUPPLEMENT",
        "SUPPLEMENT FACTS", "CREATINE", "CRÉATINE", "MONOHYDRATE", "WHEY",
        "VALEUR ENERGETIQUE", "VALEUR ÉNERGÉTIQUE", "NUTRITION FACTS",
        "APPORT JOURNALIER", "PROTEIN POWDER", "SHAMPOOING", "GEL DOUCHE",
        "CREME HYDRATANTE", "CRÈME HYDRATANTE", "DENTIFRICE",
    ]
    private var analyzing = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupUI()
        checkAuthAndStart()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
        layoutGuide()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    private func setupUI() {
        statusLabel.text = "Cadre l’étiquette dans le cadre"
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
        statusLabel.backgroundColor = UIColor.black.withAlphaComponent(0.45)
        statusLabel.layer.cornerRadius = 10
        statusLabel.clipsToBounds = true
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(statusLabel)

        guideView.layer.borderColor = UIColor.white.withAlphaComponent(0.85).cgColor
        guideView.layer.borderWidth = 2.5
        guideView.layer.cornerRadius = 14
        guideView.backgroundColor = .clear
        guideView.isUserInteractionEnabled = false
        view.addSubview(guideView)

        cancelBtn.setTitle("Annuler", for: .normal)
        cancelBtn.setTitleColor(.white, for: .normal)
        cancelBtn.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        cancelBtn.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelBtn.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cancelBtn)

        shutterBtn.setTitle("Photo manuelle", for: .normal)
        shutterBtn.setTitleColor(.black, for: .normal)
        shutterBtn.backgroundColor = UIColor.white.withAlphaComponent(0.92)
        shutterBtn.layer.cornerRadius = 22
        shutterBtn.titleLabel?.font = .systemFont(ofSize: 14, weight: .bold)
        shutterBtn.contentEdgeInsets = UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)
        shutterBtn.addTarget(self, action: #selector(manualCapture), for: .touchUpInside)
        shutterBtn.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(shutterBtn)

        NSLayoutConstraint.activate([
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            statusLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 40),

            cancelBtn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            cancelBtn.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),

            shutterBtn.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            shutterBtn.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -14),
        ])
    }

    private func layoutGuide() {
        let w = view.bounds.width * 0.78
        let h = w / 0.72
        guideView.frame = CGRect(
            x: (view.bounds.width - w) / 2,
            y: (view.bounds.height - h) / 2 - 20,
            width: w,
            height: h
        )
    }

    private func checkAuthAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] ok in
                DispatchQueue.main.async {
                    if ok { self?.configureSession() }
                    else { self?.setStatus("Caméra refusée"); self?.onCancel?() }
                }
            }
        default:
            setStatus("Autorise la caméra dans Réglages")
        }
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo

            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.session.canAddInput(input)
            else {
                DispatchQueue.main.async { self.setStatus("Caméra indisponible") }
                return
            }
            self.session.addInput(input)

            if self.session.canAddOutput(self.photoOutput) {
                self.session.addOutput(self.photoOutput)
            }

            self.videoOutput.alwaysDiscardsLateVideoFrames = true
            self.videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            self.videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "weeno.label.frames"))
            if self.session.canAddOutput(self.videoOutput) {
                self.session.addOutput(self.videoOutput)
            }
            if let conn = self.videoOutput.connection(with: .video), conn.isVideoOrientationSupported {
                conn.videoOrientation = .portrait
            }

            self.session.commitConfiguration()

            DispatchQueue.main.async {
                let layer = AVCaptureVideoPreviewLayer(session: self.session)
                layer.videoGravity = .resizeAspectFill
                layer.frame = self.view.bounds
                self.view.layer.insertSublayer(layer, at: 0)
                self.preview = layer
            }

            self.session.startRunning()
            DispatchQueue.main.async {
                self.setStatus("Cadre l’étiquette — détection auto…")
            }
        }
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        if fired || capturing || analyzing { return }
        frameCounter += 1
        guard frameCounter % analyzeEveryN == 0 else { return }
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        analyzing = true
        let req = VNRecognizeTextRequest { [weak self] request, _ in
            defer { self?.analyzing = false }
            guard let self, !self.fired, !self.capturing else { return }
            let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
            let lines: [String] = observations.compactMap { $0.topCandidates(1).first?.string }
            let text = lines.joined(separator: "\n")
            let charCount = text.filter { !$0.isWhitespace }.count
            let hasHeadline = (observations.map { $0.boundingBox.height }.max() ?? 0) >= self.minHeadlineHeight
            // Rejet dur si le texte contient un marqueur typique d'un produit non-vin —
            // pas de soft reset ici, on ne veut clairement pas déclencher dessus.
            let upperText = text.uppercased()
            let looksNonWine = self.nonWineKeywords.contains { upperText.contains($0) }
            let good = !looksNonWine && charCount >= self.minChars && charCount <= self.maxChars
                && lines.count >= self.minLines && lines.count <= self.maxLines
                && hasHeadline

            DispatchQueue.main.async {
                if looksNonWine {
                    self.stableCount = 0
                    self.setStatus("Ça ne ressemble pas à une étiquette de vin")
                    self.guideView.layer.borderColor = UIColor.white.withAlphaComponent(0.85).cgColor
                } else if good {
                    self.stableCount = min(self.stableCount + 1, self.minStableFrames)
                    if self.stableCount >= self.minStableFrames {
                        self.setStatus("Étiquette détectée — capture…")
                        self.guideView.layer.borderColor = UIColor.systemGreen.cgColor
                        self.fireCapture()
                    } else {
                        self.setStatus("Étiquette vue — tiens stable… (\(self.stableCount)/\(self.minStableFrames))")
                        self.guideView.layer.borderColor = UIColor.systemYellow.cgColor
                    }
                } else {
                    // Soft reset : une frame floue ne remet pas à zéro
                    if self.stableCount > 0 { self.stableCount -= 1 }
                    if self.stableCount == 0 {
                        self.setStatus("Cadre l’étiquette dans le cadre")
                        self.guideView.layer.borderColor = UIColor.white.withAlphaComponent(0.85).cgColor
                    } else {
                        self.setStatus("Étiquette vue — tiens stable… (\(self.stableCount)/\(self.minStableFrames))")
                    }
                }
            }
        }
        req.recognitionLevel = .fast
        req.usesLanguageCorrection = false
        // ROI centre ≈ cadre guide (Vision: origin bas-gauche normalisé)
        req.regionOfInterest = CGRect(x: 0.11, y: 0.18, width: 0.78, height: 0.64)

        let handler = VNImageRequestHandler(cvPixelBuffer: pb, orientation: .up, options: [:])
        try? handler.perform([req])
    }

    private func fireCapture() {
        guard !fired, !capturing else { return }
        capturing = true
        fired = true
        takePhoto()
    }

    @objc private func manualCapture() {
        guard !capturing else { return }
        capturing = true
        fired = true
        setStatus("Photo manuelle…")
        takePhoto()
    }

    private func takePhoto() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let settings = AVCapturePhotoSettings()
            self.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }

    @objc private func cancelTapped() {
        sessionQueue.async { [weak self] in self?.session.stopRunning() }
        onCancel?()
    }

    private func setStatus(_ s: String) {
        statusLabel.text = "  \(s)  "
    }

    /// Crop l’image full-res au cadre guide (preview aspectFill).
    private func cropToGuide(_ image: UIImage) -> UIImage {
        guard let preview else { return image }
        // Guide en coords layer preview
        let guideInPreview = preview.convert(guideView.frame, from: view.layer)
        // → rect normalisé dans l’espace buffer caméra
        let meta = preview.metadataOutputRectConverted(fromLayerRect: guideInPreview)
        // Légère marge intérieure
        let inset: CGFloat = 0.03
        var r = meta.insetBy(dx: meta.width * inset, dy: meta.height * inset)
        r = r.intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        guard r.width > 0.05, r.height > 0.05 else { return image }

        // Dessine l’image orientée puis crop
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = true
        let oriented = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        guard let cg = oriented.cgImage else { return image }
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        // metadataOutputRect : origin bas-gauche Vision/AVFoundation → flip Y pour UIImage top-left
        let crop = CGRect(
            x: r.origin.x * w,
            y: (1 - r.origin.y - r.height) * h,
            width: r.width * w,
            height: r.height * h
        ).integral
        guard crop.width > 32, crop.height > 32,
              let cut = cg.cropping(to: crop)
        else { return image }
        return UIImage(cgImage: cut, scale: oriented.scale, orientation: .up)
    }
}

extension LiveLabelScannerVC: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        sessionQueue.async { [weak self] in self?.session.stopRunning() }
        if let error {
            DispatchQueue.main.async { [weak self] in
                self?.setStatus("Erreur photo : \(error.localizedDescription)")
                self?.capturing = false
                self?.fired = false
            }
            return
        }
        guard let data = photo.fileDataRepresentation(),
              let image = UIImage(data: data)
        else {
            DispatchQueue.main.async { [weak self] in
                self?.setStatus("Capture vide")
                self?.capturing = false
                self?.fired = false
            }
            return
        }
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let cropped = self.cropToGuide(image)
            self.onCapture?(cropped)
        }
    }
}
