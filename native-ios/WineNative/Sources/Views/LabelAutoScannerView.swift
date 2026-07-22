import AVFoundation
import SwiftUI
import UIKit
import Vision

/// Caméra live façon Vivino : détecte texte d'étiquette stable → capture auto.
struct LabelAutoScannerView: UIViewControllerRepresentable {
    var onCapture: (UIImage) -> Void
    var onCancel: () -> Void

    func makeUIViewController(context: Context) -> LabelAutoScannerVC {
        let vc = LabelAutoScannerVC()
        vc.onCapture = onCapture
        vc.onCancel = onCancel
        return vc
    }

    func updateUIViewController(_ uiViewController: LabelAutoScannerVC, context: Context) {}
}

final class LabelAutoScannerVC: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onCapture: ((UIImage) -> Void)?
    var onCancel: (() -> Void)?

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "weenobis.label.scan")
    private var preview: AVCaptureVideoPreviewLayer?
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()

    private let statusLabel = UILabel()
    private let guideView = UIView()
    private let cancelBtn = UIButton(type: .system)
    private let shutterBtn = UIButton(type: .system)

    private var frameCounter = 0
    private var lastTextSignature = ""
    private var stableCount = 0
    private var capturing = false
    private var fired = false
    private let minStableFrames = 4          // ~0.6–0.8 s selon throttle
    private let analyzeEveryN = 4
    private let minChars = 12
    private let minLines = 2

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
        let h = w * 1.15
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
            self.videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "weenobis.label.frames"))
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
        if fired || capturing { return }
        frameCounter += 1
        guard frameCounter % analyzeEveryN == 0 else { return }
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let req = VNRecognizeTextRequest { [weak self] request, _ in
            guard let self, !self.fired, !self.capturing else { return }
            let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
            let lines: [String] = observations.compactMap { $0.topCandidates(1).first?.string }
            let text = lines.joined(separator: "\n")
            let sig = lines.prefix(6).joined(separator: "|").lowercased()
            let charCount = text.filter { !$0.isWhitespace }.count
            let good = charCount >= self.minChars && lines.count >= self.minLines

            DispatchQueue.main.async {
                if good {
                    if sig == self.lastTextSignature, !sig.isEmpty {
                        self.stableCount += 1
                    } else {
                        self.lastTextSignature = sig
                        self.stableCount = 1
                    }
                    if self.stableCount >= self.minStableFrames {
                        self.setStatus("Étiquette détectée — capture…")
                        self.guideView.layer.borderColor = UIColor.systemGreen.cgColor
                        self.fireCapture()
                    } else {
                        self.setStatus("Étiquette vue — tiens stable… (\(self.stableCount)/\(self.minStableFrames))")
                        self.guideView.layer.borderColor = UIColor.systemYellow.cgColor
                    }
                } else {
                    self.stableCount = 0
                    self.lastTextSignature = ""
                    self.setStatus("Cadre l’étiquette dans le cadre")
                    self.guideView.layer.borderColor = UIColor.white.withAlphaComponent(0.85).cgColor
                }
            }
        }
        req.recognitionLevel = .fast
        req.usesLanguageCorrection = false
        // Crop approx centre (cadre guide)
        req.regionOfInterest = CGRect(x: 0.12, y: 0.18, width: 0.76, height: 0.64)

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
}

extension LabelAutoScannerVC: AVCapturePhotoCaptureDelegate {
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
            self?.onCapture?(image)
        }
    }
}
