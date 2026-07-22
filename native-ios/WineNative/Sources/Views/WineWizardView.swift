import SwiftUI

struct WineWizardView: View {
    @EnvironmentObject private var app: AppModel
    @Binding var step: Int

    private var prefill: WineProduct? { app.wizardProduct }

    @State private var scannedCode = ""
    @State private var manualEAN = ""
    @State private var product: WineProduct?
    @State private var scanStatus = "Cadre le code-barres dans le rectangle"
    @State private var busy = false

    @State private var vivinoProducer = ""
    @State private var vivinoName = ""
    @State private var vivinoResults: [VivinoHit] = []
    @State private var vivinoError: String?
    @State private var showManual = false
    @State private var showEANManual = false
    @State private var manualName = ""
    @State private var manualProducer = ""
    @State private var styleOptions: [StyleOption] = []
    @State private var manualStyle = ""
    @State private var customStyle = ""

    @State private var showScanCamera = false
    @State private var showTastingCamera = false
    @State private var photoData: Data?
    @State private var photoPreview: UIImage?
    /// Lieu / lien où la vin a été dégustée (optionnel) — saisi à l'étape Photo.
    @State private var location = ""

    @State private var rating = 3.0
    @State private var comment = ""
    @State private var flavors = Set<String>()
    @State private var hops = Set<String>()
    @State private var customFlavorInput = ""
    @State private var customHopInput = ""
    @State private var flavorTags: [String] = []
    @State private var hopTags: [String] = []
    @State private var showFlavors = true
    @State private var showHops = true
    @State private var saving = false
    @State private var showDuplicate = false
    @State private var duplicateDetail = ""

    private var manualStyleOptions: [(String, String)] {
        var opts: [(String, String)] = [("", "Choisir…")]
        opts.append(contentsOf: styleOptions.filter { !$0.value.isEmpty }.map { ($0.value, $0.label) })
        opts.append(("__other__", "Autre (saisir manuellement)"))
        return opts
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Group {
                    switch step {
                    case 1: stepWeeno
                    case 2: stepPhoto
                    default: stepRating
                    }
                }
                .id(step)
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .animation(.easeInOut(duration: 0.25), value: step)
        }
        .background(Theme.bg)
        .scrollDismissesKeyboard(.interactively)
        .dismissKeyboardOnTap()
        .fullScreenCover(isPresented: $showScanCamera) {
            CameraPicker { image in Task { await processScanPhoto(image) } }
        }
        .fullScreenCover(isPresented: $showTastingCamera) {
            CameraPicker { image in Task { await processTastingPhoto(image) } }
        }
        .onAppear {
            applyPrefillIfNeeded()
            Task { styleOptions = (try? await app.api.styles()) ?? [] }
        }
        .onChange(of: app.wizardStep, perform: { _ in applyPrefillIfNeeded() })
        .onChange(of: app.wizardProduct, perform: { _ in applyPrefillIfNeeded() })
        .onChange(of: step, perform: { newStep in
            app.wizardStep = newStep
            if newStep == 3 { Task { await loadNotation() } }
        })
        .alert("Déjà dégustée", isPresented: $showDuplicate) {
            Button("Annuler", role: .cancel) {}
            Button("Noter à nouveau") { Task { await save(force: true) } }
        } message: {
            Text(duplicateDetail.isEmpty
                 ? "Ajouter cette nouvelle note à ton historique ?"
                 : duplicateDetail)
        }
    }

    // MARK: - Step 1

    private var stepWeeno: some View {
        Group {
            WeenoLead(text: "Scan EAN optionnel — ou cherche directement sur Vivino.")

            VStack(spacing: 0) {
                ZStack(alignment: .bottom) {
                    BarcodeScannerView { code in
                        scannedCode = code
                        manualEAN = code
                        app.showToast(
                            "Code-barres lu ✓",
                            variant: .success,
                            detail: code,
                            label: "Scan",
                            durationMs: 2400
                        )
                        Task { await lookupEAN(code) }
                    }
                    .frame(height: min(min((UIScreen.main.bounds.width - 32) * 0.75, UIScreen.main.bounds.height * 0.48), 320))
                    .background(Theme.photoBg)
                    .overlay {
                        ScanViewfinderOverlay()
                    }

                    Button { showScanCamera = true } label: {
                        Text("Prendre photo")
                            .font(.system(size: Theme.Font.ghost, weight: .semibold))
                            .foregroundStyle(Theme.text)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Theme.card.opacity(0.92))
                            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.btn).stroke(Theme.border))
                            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.btn))
                    }
                    .padding(.bottom, 14)
                }
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
                .background(Theme.card)
            }

            Text(scanStatus)
                .font(.system(size: Theme.Font.lead * 0.94))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)

            VStack(alignment: .leading, spacing: 10) {
                Text("Chercher sur Vivino")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                Text("Top 5 résultats seulement (limite Vivino dans le HTML). Utilise Producteur + Nom pour faire apparaître ta vin précise dans ces 5.")
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                WeenoField(label: "Producteur (optionnel)", text: $vivinoProducer, placeholder: "ex. Les Intenables")
                WeenoField(label: "Nom de la vin", text: $vivinoName, placeholder: "ex. Mama Whipa")
                WeenoPrimaryButton(title: busy ? "Recherche…" : "Chercher sur Vivino", disabled: vivinoName.count < 2 && vivinoProducer.count < 2, busy: busy) {
                    Task { await searchVivino() }
                }

                if let vivinoError {
                    Text(vivinoError).font(.footnote).foregroundStyle(Theme.muted)
                }
                ForEach(vivinoResults) { hit in
                    Button { Task { await selectVivino(hit) } } label: {
                        HStack(spacing: 10) {
                            // Use AsyncImage for external Vivino labels to guarantee loading (bypasses custom homelab download path that had pinning/transport issues)
                            if let urlStr = hit.photoURL, let url = URL(string: urlStr) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image.resizable().aspectRatio(contentMode: .fill)
                                    case .failure:
                                        RoundedRectangle(cornerRadius: 8).fill(Theme.card).overlay(Text("🍷").font(.caption2))
                                    default:
                                        RoundedRectangle(cornerRadius: 8).fill(Theme.card).overlay(ProgressView().scaleEffect(0.6))
                                    }
                                }
                                .frame(width: 44, height: 44)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .accessibilityLabel("Photo de la vin depuis Vivino")
                            } else {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Theme.card)
                                    .frame(width: 44, height: 44)
                                    .overlay(Text("🍷").font(.caption2).foregroundStyle(Theme.muted))
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(hit.wineName).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                                Text([hit.producer, hit.styleFr].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption).foregroundStyle(Theme.muted)
                        }
                        .padding(10)
                        .background(Theme.bg)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }

                DisclosureGroup("Saisie manuelle (secours)", isExpanded: $showManual) {
                    WeenoField(label: "Nom de la vin", text: $manualName, placeholder: "ex. Mama Whipa")
                    WeenoField(label: "Producteur", text: $manualProducer, placeholder: "ex. Les Intenables")
                    WeenoFormSelectField(
                        label: "Style",
                        value: manualStyle,
                        options: manualStyleOptions,
                        onSelect: { manualStyle = $0 }
                    )
                    .padding(.top, 10)
                    if manualStyle == "__other__" {
                        WeenoField(label: "Style", text: $customStyle, placeholder: "Ex: Gose, Table Weeno, etc.")
                    }
                    WeenoSecondaryButton(title: "Continuer") {
                        Task { await saveManualProduct() }
                    }
                }
                .font(.system(size: Theme.Font.field))
                .foregroundStyle(Theme.muted)
                .tint(Theme.accent)
            }
            .beerCard()

            DisclosureGroup("Code illisible ? Saisie EAN à la main", isExpanded: $showEANManual) {
                WeenoField(label: "Code EAN", text: $manualEAN, placeholder: "ex. 5411680001111", keyboard: .numberPad)
                WeenoSecondaryButton(title: "Identifier par EAN") {
                    Task { await lookupEAN(manualEAN) }
                }
            }
            .font(.system(size: Theme.Font.field))
            .foregroundStyle(Theme.muted)
            .tint(Theme.accent)

            if let product, !product.wineName.isEmpty {
                WeenoPreviewCard(product: product)
                // (legacy comment removed)
                WeenoSecondaryButton(title: "+ Ajouter à la liste « À boire »") {
                    Task { await addToWishlist(product) }
                }
                WeenoPrimaryButton(title: "Continuer → photo") { step = 2 }
            }
        }
    }

    // MARK: - Step 2

    private var stepPhoto: some View {
        Group {
            WeenoLead(text: "Photo du verre (optionnel) et lieu de dégustation.")

            Button { showTastingCamera = true } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.border, style: StrokeStyle(lineWidth: 2, dash: [6]))
                        .background(Theme.card)
                        .frame(minHeight: 180)
                    if let photoPreview {
                        Image(uiImage: photoPreview)
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 280)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .padding(8)
                    } else {
                        Text("📷 Prendre une photo")
                            .font(.system(size: 16))
                            .foregroundStyle(Theme.muted)
                    }
                }
            }
            .buttonStyle(.plain)

            // Lieu / lien — idée couple : savoir où (et le quand vient de created_at)
            VStack(alignment: .leading, spacing: 8) {
                Text("Où as-tu dégusté ?")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                Text("Nom du lieu et/ou lien (Maps, resto…) — optionnel.")
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                WeenoField(
                    label: "Lieu ou lien",
                    text: $location,
                    placeholder: "ex. Chez nous · Producteur X · https://maps.app.goo.gl/…"
                )
                Text("\(min(location.count, 300))/300")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .beerCard()

            WeenoSecondaryButton(title: "← Retour") { step = 1 }
            WeenoPrimaryButton(title: "Continuer → note") {
                step = 3
                Task { await loadNotation() }
            }
        }
    }

    private var flavorTagsTitle: String {
        guard let product,
              !product.displayStyle.isEmpty,
              product.displayStyle != "Unknown" else { return "Goûts" }
        return "Goûts \(product.displayStyle)"
    }

    // MARK: - Step 3

    private var stepRating: some View {
        Group {
            if let product, !product.wineName.isEmpty {
                WeenoLead(text: product.wineName)
            } else {
                WeenoLead(text: "Pas de vin identifiée — retourne à l'étape 1 ou cherche sur Vivino.")
            }

            VStack(alignment: .leading, spacing: 10) {
                VivinoRatingSlider(rating: $rating)
            }
            .beerCard()

            if showFlavors {
                if !flavorTags.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        FlavorTagGrid(title: flavorTagsTitle, tags: flavorTags, selected: $flavors, maxCount: 8)
                    }
                    .beerCard()
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("Goûts perso")
                        .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                        .foregroundStyle(Theme.text)
                    CustomTagInput(
                        placeholder: "ex. pneus, sucrée, vanille fumée…",
                        input: $customFlavorInput,
                        selected: $flavors,
                        maxCount: 8
                    )
                    CustomTagChips(selected: $flavors, customOnly: flavors.subtracting(Set(flavorTags)))
                    Text("Libre — 8 goûts max au total")
                        .font(.system(size: Theme.Font.lead * 0.94))
                        .foregroundStyle(Theme.muted)
                }
                .beerCard()
            }
            if showHops {
                if !hopTags.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        FlavorTagGrid(title: "Houblons", tags: hopTags, selected: $hops, maxCount: 6)
                    }
                    .beerCard()
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("Houblons perso")
                        .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                        .foregroundStyle(Theme.text)
                    CustomTagInput(
                        placeholder: "ex. Citra, Mosaic, Galaxy…",
                        input: $customHopInput,
                        selected: $hops,
                        maxCount: 6,
                        onRegister: { name in Task { try? await app.api.addHop(name) } }
                    )
                    CustomTagChips(selected: $hops, customOnly: hops.subtracting(Set(hopTags)))
                    Text("Max ~6 houblons")
                        .font(.system(size: Theme.Font.lead * 0.94))
                        .foregroundStyle(Theme.muted)
                }
                .beerCard()
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Commentaire (optionnel, 300 car.)")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                TextField("Terrasse, avec elle, à refaire…", text: $comment, axis: .vertical)
                    .lineLimit(2...4)
                    .onChange(of: comment, perform: { v in
                        if v.count > 300 { comment = String(v.prefix(300)) }
                    })
                    .padding(12)
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .foregroundStyle(Theme.text)
                Text("\(comment.count)/300")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .beerCard()

            WeenoSecondaryButton(title: "← Retour") { step = 2 }
            WeenoPrimaryButton(
                title: saving ? "Enregistrement…" : "Enregistrer",
                disabled: product == nil || rating < 0.25,
                busy: saving
            ) {
                Task { await save(force: false) }
            }
        }
    }

    // MARK: - Actions

    private func lookupEAN(_ code: String) async {
        let digits = code.filter(\.isNumber)
        guard digits.count >= 8 else {
            scanStatus = "Code trop court"
            app.showToast("Code-barres trop court", variant: .warn)
            return
        }
        busy = true
        scanStatus = "Recherche…"
        defer { busy = false }

        if app.networkStatus != .online {
            scannedCode = digits
            scanStatus = "Hors ligne — saisie manuelle ou Vivino"
            return
        }

        do {
            let res = try await app.api.lookup(barcode: digits)
            if res.ok {
                product = res.asProduct(fallbackBarcode: digits)
                scanStatus = "Vin identifiée ✓"
                app.showToast("Vin identifiée ✓", variant: .success)
            } else {
                product = nil
                scannedCode = digits
                scanStatus = res.error ?? "Introuvable"
                app.showToast(res.error ?? "Vin introuvable", variant: .warn)
            }
        } catch let err {
            scanStatus = err.localizedDescription
        }
    }

    private func processScanPhoto(_ image: UIImage) async {
        guard let raw = image.jpegData(compressionQuality: 0.92) else { return }
        let jpeg = WineImageUtils.compressJPEG(raw)
        busy = true
        scanStatus = "Décodage photo…"
        defer { busy = false }
        do {
            let scan = try await app.api.scanPhoto(jpeg: jpeg)
            if scan.ok {
                let digits = scan.barcode ?? ""
                scannedCode = digits
                manualEAN = digits
                product = scan.asProduct(fallbackBarcode: digits)
                scanStatus = "Vin identifiée ✓"
                app.showToast(
                    "Code-barres lu ✓",
                    variant: .success,
                    detail: digits.isEmpty ? nil : digits,
                    label: "Scan photo",
                    durationMs: 2400
                )
            } else {
                scanStatus = scan.error ?? "Code illisible"
            }
        } catch let err {
            scanStatus = err.localizedDescription
        }
    }

    private func saveManualProduct() async {
        let style = manualStyle == "__other__" ? (customStyle.isEmpty ? "Unknown" : customStyle) : (manualStyle.isEmpty ? "Unknown" : manualStyle)
        let digits = manualEAN.filter(\.isNumber)
        busy = true
        defer { busy = false }
        if digits.count >= 8, app.networkStatus == .online {
            do {
                let res = try await app.api.saveProduct(
                    barcode: digits,
                    wineName: manualName,
                    producer: manualProducer.isEmpty ? "—" : manualProducer,
                    style: style
                )
                product = res.asProduct(fallbackBarcode: digits)
                scannedCode = digits
                step = 2
                return
            } catch {
                // fallback local
            }
        }
        product = WineProduct(
            barcode: digits,
            wineName: manualName,
            producer: manualProducer.isEmpty ? "—" : manualProducer,
            style: style
        )
        step = 2
    }

    private func searchVivino() async {
        let q = [vivinoProducer, vivinoName].filter { !$0.isEmpty }.joined(separator: " ")
        guard q.count >= 2 else { return }
        busy = true
        vivinoError = nil
        defer { busy = false }
        do {
            let res = try await app.api.vivinoSearch(query: q)
            if res.ok, let hits = res.results, !hits.isEmpty {
                vivinoResults = hits
            } else {
                vivinoResults = []
                vivinoError = res.error ?? "Aucun résultat"
            }
        } catch let err {
            vivinoResults = []
            vivinoError = err.localizedDescription
        }
    }

    private func selectVivino(_ hit: VivinoHit) async {
        busy = true
        defer { busy = false }
        let ean = scannedCode.filter(\.isNumber)
        do {
            let res: LookupResponse
            if ean.count >= 8 {
                res = try await app.api.linkProduct(
                    bid: hit.bid,
                    barcode: ean,
                    wineName: hit.wineName,
                    producer: hit.producer ?? ""
                )
            } else {
                res = try await app.api.vivinoFetch(
                    bid: hit.bid,
                    barcode: scannedCode,
                    wineName: hit.wineName,
                    producer: hit.producer ?? ""
                )
            }
            if res.ok {
                product = res.asProduct(fallbackBarcode: ean.isEmpty ? scannedCode : ean)
                scanStatus = "Vivino ✓"
                vivinoResults = []
            } else {
                vivinoError = res.error ?? "Fiche introuvable"
            }
        } catch let err {
            vivinoError = err.localizedDescription
        }
    }

    private func processTastingPhoto(_ image: UIImage) async {
        guard let raw = image.jpegData(compressionQuality: 0.92) else { return }
        let jpeg = WineImageUtils.compressJPEG(raw)
        photoData = jpeg
        photoPreview = UIImage(data: jpeg)
    }

    private func loadNotation() async {
        guard let product, app.networkStatus == .online else { return }
        do {
            let n = try await app.api.flavors(style: product.style, description: product.summary)
            flavorTags = n.flavors ?? []
            hopTags = n.hops ?? []
            showFlavors = n.showFlavorsBlock ?? true
            showHops = n.showHopsBlock ?? true
            flavors = Set(n.suggestedFlavors ?? [])
            hops = Set(n.suggestedHops ?? [])
        } catch {
            flavorTags = []
            hopTags = []
        }
    }

    private func save(force: Bool) async {
        guard let product else { return }
        saving = true
        defer { saving = false }
        do {
            let msg = try await app.saveCheckin(
                product: product,
                rating: rating,
                flavors: Array(flavors),
                hops: Array(hops),
                comment: comment,
                photoJPEG: photoData,
                force: force,
                location: location
            )
            if msg.hasPrefix("duplicate|") {
                let parts = msg.split(separator: "|").map(String.init)
                if parts.count >= 4 {
                    duplicateDetail = "\(parts[1]) — \(WineFormatters.ratingLabel(Double(parts[2]) ?? 0)) ★ · \(WineFormatters.formatDate(parts[3]))\n\nAjouter cette nouvelle note à ton historique ?"
                }
                showDuplicate = true
                return
            }
            let variant: ToastPayload.Variant = msg.contains("✓") ? .success
                : msg.contains("iPhone") ? .info : .success
            app.showToast(msg, variant: variant)
            app.hapticSuccess()
            try? await Task.sleep(nanoseconds: 900_000_000)
            resetWizard()
        } catch let err {
            app.showToast(err.localizedDescription, variant: .error, durationMs: 4200)
        }
    }

    private func applyPrefillIfNeeded() {
        if app.wizardStep != step { step = app.wizardStep }
        guard let p = prefill, !p.wineName.isEmpty else { return }
        product = p
        if step == 3 { Task { await loadNotation() } }
    }

    private func clearProduct() {
        product = nil
        vivinoResults = []
        scanStatus = "Cadre le code-barres dans le rectangle"
    }

    private func addToWishlist(_ product: WineProduct) async {
        do {
            try await app.api.addWishlist(
                wineName: product.wineName,
                producer: product.producer,
                style: product.style,
                barcode: product.barcode
            )
            app.showToast("Ajouté à « À boire » ✓", variant: .success)
        } catch let err {
            app.showToast(err.localizedDescription, variant: .error)
        }
    }

    private func resetWizard() {
        app.clearWizardPrefill()
        step = 1
        product = nil
        scannedCode = ""
        manualEAN = ""
        vivinoProducer = ""
        vivinoName = ""
        vivinoResults = []
        manualName = ""
        manualProducer = ""
        manualStyle = ""
        customStyle = ""
        photoData = nil
        photoPreview = nil
        location = ""
        rating = 3.0
        comment = ""
        flavors = []
        hops = []
        customFlavorInput = ""
        customHopInput = ""
        scanStatus = "Cadre le code-barres dans le rectangle"
        duplicateDetail = ""
    }
}