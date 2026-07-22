import SwiftUI

struct WineWizardView: View {
    @EnvironmentObject private var app: AppModel
    @Binding var step: Int

    private var prefill: WineProduct? { app.wizardProduct }

    @State private var scannedCode = ""
    @State private var product: WineProduct?
    @State private var scanStatus = "Cadre l’étiquette — touche pour photo"
    @State private var busy = false
    @State private var labelPreview: UIImage?

    @State private var vivinoQuery = ""
    @State private var vivinoProducer = ""
    @State private var vivinoVintage = ""
    @State private var vivinoResults: [VivinoHit] = []
    @State private var vivinoError: String?
    @State private var showManual = false
    @State private var manualName = ""
    @State private var manualProducer = ""
    @State private var manualVintage = ""
    @State private var manualRegion = ""
    @State private var styleOptions: [StyleOption] = []
    @State private var manualStyle = ""
    @State private var customStyle = ""

    @State private var showScanCamera = false
    @State private var showTastingCamera = false
    @State private var photoData: Data?
    @State private var photoPreview: UIImage?
    /// Lieu / lien où le vin a été dégusté (optionnel) — saisi à l'étape Photo.
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
    @State private var showHops = false
    @State private var showFlavorBrowse = false
    @State private var noteVintage = ""
    @State private var noteColor = ""
    @State private var noteRegion = ""
    @State private var noteCountry = ""
    @State private var noteAbv = ""
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

    // MARK: - Step 1 (parité webapp Weeno)

    private var stepWeeno: some View {
        Group {
            WeenoLead(text: "Scan d’étiquette ou recherche Vivino.")

            VStack(alignment: .leading, spacing: 10) {
                Text("Scan d’étiquette")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                Button { showScanCamera = true } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Theme.photoBg)
                            .frame(minHeight: 180)
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
                        if let labelPreview {
                            Image(uiImage: labelPreview)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 240)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .padding(8)
                        } else {
                            VStack(spacing: 8) {
                                Text("🍾").font(.system(size: 36))
                                Text("Cadre l’étiquette")
                                    .font(.system(size: Theme.Font.lead, weight: .semibold))
                                    .foregroundStyle(Theme.text)
                                Text("touche pour prendre une photo")
                                    .font(.system(size: Theme.Font.ghost))
                                    .foregroundStyle(Theme.muted)
                            }
                        }
                    }
                }
                .buttonStyle(.plain)
                Text(scanStatus)
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                if busy {
                    ProgressView().tint(Theme.accent).frame(maxWidth: .infinity)
                } else if labelPreview != nil {
                    WeenoPrimaryButton(title: "Lancer le scan", disabled: false, busy: false) {
                        if let img = labelPreview {
                            Task { await processScanPhoto(img) }
                        }
                    }
                }
            }
            .beerCard()

            VStack(alignment: .leading, spacing: 10) {
                Text("Chercher sur Vivino")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                Text("Max 5 suggestions (le 1er est souvent le bon).")
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                WeenoField(label: "Domaine, cuvée…", text: $vivinoQuery, placeholder: "ex. Bachelet Saint-Aubin Le Charmois")
                WeenoField(label: "Producteur", text: $vivinoProducer, placeholder: "ex. Domaine Nicolas")
                WeenoField(label: "Millésime", text: $vivinoVintage, placeholder: "2019", keyboard: .numberPad)
                WeenoPrimaryButton(
                    title: busy ? "Recherche…" : "Chercher sur Vivino",
                    disabled: vivinoQuery.trimmingCharacters(in: .whitespaces).count < 2
                        && vivinoProducer.trimmingCharacters(in: .whitespaces).count < 2,
                    busy: busy
                ) {
                    Task { await searchVivino() }
                }

                if let vivinoError {
                    Text(vivinoError).font(.footnote).foregroundStyle(Theme.muted)
                }
                ForEach(vivinoResults) { hit in
                    Button { Task { await selectVivino(hit) } } label: {
                        HStack(spacing: 10) {
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
                                .accessibilityLabel("Photo du vin depuis Vivino")
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
                    WeenoField(label: "Nom / cuvée *", text: $manualName, placeholder: "ex. Saint-Aubin 1er Cru…")
                    WeenoField(label: "Producteur", text: $manualProducer, placeholder: "ex. Domaine Nicolas")
                    WeenoField(label: "Année / millésime", text: $manualVintage, placeholder: "2019", keyboard: .numberPad)
                    WeenoFormSelectField(
                        label: "Couleur",
                        value: manualStyle,
                        options: manualStyleOptions,
                        onSelect: { manualStyle = $0 }
                    )
                    .padding(.top, 10)
                    if manualStyle == "__other__" {
                        WeenoField(label: "Couleur", text: $customStyle, placeholder: "ex. orange, fortifié…")
                    }
                    WeenoField(label: "Région", text: $manualRegion, placeholder: "ex. Saint-Aubin, Bourgogne…")
                    WeenoSecondaryButton(title: "Continuer sans Vivino") {
                        Task { await saveManualProduct() }
                    }
                }
                .font(.system(size: Theme.Font.field))
                .foregroundStyle(Theme.muted)
                .tint(Theme.accent)
            }
            .beerCard()

            if let product, !product.wineName.isEmpty {
                WeenoPreviewCard(product: product)
                WeenoSecondaryButton(title: "Changer de vin") {
                    clearProduct()
                    labelPreview = nil
                }
                WeenoPrimaryButton(title: "Continuer → photo") { step = 2 }
            }
        }
    }

    // MARK: - Step 2

    private var stepPhoto: some View {
        Group {
            WeenoLead(text: "Photo du verre / bouteille et lieu.")

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

    // MARK: - Step 3 (parité webapp Note)

    private var stepRating: some View {
        Group {
            if let product, !product.wineName.isEmpty {
                WeenoLead(text: product.wineName)
            } else {
                WeenoLead(text: "Pas de vin identifié — retourne à l’étape 1 ou cherche sur Vivino.")
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("Note")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                VivinoRatingSlider(rating: $rating)
            }
            .beerCard()

            VStack(alignment: .leading, spacing: 8) {
                Text("Arômes & structure")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                Text("Top Vivino en or si dispo. × pour retirer. Ajoute les tiens.")
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                if !flavors.isEmpty {
                    CustomTagChips(selected: $flavors, customOnly: flavors)
                }
                CustomTagInput(
                    placeholder: "ex. pierre chaude, salin…",
                    input: $customFlavorInput,
                    selected: $flavors,
                    maxCount: 8
                )
                Button {
                    showFlavorBrowse.toggle()
                } label: {
                    Text(showFlavorBrowse ? "Masquer les tags prédéfinis" : "Parcourir les tags prédéfinis…")
                        .font(.system(size: Theme.Font.ghost, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                }
                if showFlavorBrowse, !flavorTags.isEmpty {
                    FlavorTagGrid(title: "Tags", tags: flavorTags, selected: $flavors, maxCount: 8)
                }
            }
            .beerCard()

            VStack(alignment: .leading, spacing: 10) {
                Text("Détails")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                WeenoField(label: "Millésime", text: $noteVintage, placeholder: "2019", keyboard: .numberPad)
                WeenoFormSelectField(
                    label: "Couleur",
                    value: noteColor,
                    options: [
                        ("", "—"),
                        ("rouge", "Rouge"), ("blanc", "Blanc"), ("rose", "Rosé"),
                        ("effervescent", "Effervescent"), ("orange", "Orange"),
                        ("fortifie", "Fortifié"), ("autre", "Autre"),
                    ],
                    onSelect: { noteColor = $0 }
                )
                WeenoField(label: "Région", text: $noteRegion, placeholder: "ex. Saint-Aubin…")
                WeenoField(label: "Pays", text: $noteCountry, placeholder: "France")
                WeenoField(label: "Degré (%)", text: $noteAbv, placeholder: "13.5", keyboard: .decimalPad)
            }
            .beerCard()

            VStack(alignment: .leading, spacing: 6) {
                Text("Commentaire")
                    .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                    .foregroundStyle(Theme.text)
                TextField("Nez, bouche, accord…", text: $comment, axis: .vertical)
                    .lineLimit(2...5)
                    .onChange(of: comment, perform: { v in
                        if v.count > 500 { comment = String(v.prefix(500)) }
                    })
                    .padding(12)
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .foregroundStyle(Theme.text)
                Text("\(comment.count)/500")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .beerCard()

            if let product, !product.wineName.isEmpty {
                WeenoSecondaryButton(title: "+ Ajouter à la liste « À boire »") {
                    Task { await addToWishlist(product) }
                }
            }

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

    private func processScanPhoto(_ image: UIImage) async {
        labelPreview = image
        guard let raw = image.jpegData(compressionQuality: 0.92) else { return }
        let jpeg = WineImageUtils.compressJPEG(raw)
        busy = true
        scanStatus = "Analyse de l’étiquette…"
        defer { busy = false }
        do {
            let scan = try await app.api.labelScan(jpeg: jpeg)
            if let n = scan.wineName, !n.isEmpty {
                vivinoQuery = [scan.producer, n].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
            }
            if let p = scan.producer, !p.isEmpty { vivinoProducer = p }
            if let v = scan.vintage { vivinoVintage = String(v); manualVintage = String(v) }
            if let c = scan.wineColor, !c.isEmpty { manualStyle = c }
            if let r = scan.region, !r.isEmpty { manualRegion = r }

            if !scan.candidates.isEmpty {
                vivinoResults = Array(scan.candidates.prefix(5))
                scanStatus = "Étiquette lue — tape le bon vin (\(scan.candidates.count) suggestion(s))"
                app.showToast("\(scan.candidates.count) suggestion(s)", variant: .success)
            } else if scan.aiAvailable {
                scanStatus = "Étiquette lue — aucun candidat Vivino, affine la recherche"
                if vivinoQuery.count >= 2 { await searchVivino() }
            } else {
                let raw = (scan.aiError ?? "").lowercased()
                if raw.contains("429") || raw.contains("quota") || raw.contains("rate") {
                    scanStatus = "Scan temporairement saturé — réessaie dans 1 min ou saisie manuelle"
                } else if raw.contains("clé") || raw.contains("key") || raw.contains("aucune") {
                    scanStatus = "Scan IA indisponible (config serveur) — saisie / Vivino manuelle"
                } else if !raw.isEmpty {
                    scanStatus = "Échec scan : \(scan.aiError!.prefix(120))"
                } else {
                    scanStatus = "Scan indisponible — saisis le vin ou cherche sur Vivino"
                }
                showManual = true
            }
        } catch let err {
            let m = err.localizedDescription
            if m.contains("429") || m.lowercased().contains("quota") {
                scanStatus = "Scan saturé (limite API) — réessaie plus tard"
            } else {
                scanStatus = "Erreur scan : \(m.prefix(140))"
            }
        }
    }

    private func saveManualProduct() async {
        let name = manualName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else {
            app.showToast("Nom / cuvée requis", variant: .warn)
            return
        }
        let color = manualStyle == "__other__"
            ? (customStyle.isEmpty ? "autre" : customStyle)
            : (manualStyle.isEmpty ? "autre" : manualStyle)
        var summary = ""
        if !manualVintage.isEmpty { summary += manualVintage }
        if !manualRegion.isEmpty {
            if !summary.isEmpty { summary += " · " }
            summary += manualRegion
        }
        product = WineProduct(
            barcode: "",
            wineName: name,
            producer: manualProducer.isEmpty ? "—" : manualProducer,
            style: color,
            styleFr: color,
            summary: summary
        )
        scanStatus = "Saisie manuelle ✓"
        step = 2
    }

    private func searchVivino() async {
        var parts = [vivinoQuery, vivinoProducer, vivinoVintage]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let q = parts.joined(separator: " ")
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
        // Parité webapp : sélection immédiate puis enrichissement GET /api/vivino/{id}
        product = WineProduct(
            wineName: hit.wineName,
            producer: hit.producer ?? "—",
            style: hit.styleFr ?? "autre",
            styleFr: hit.styleFr,
            vivinoBid: hit.bid > 0 ? hit.bid : nil,
            source: "vivino",
            photoURL: hit.photoURL,
            vintage: hit.vintage,
            region: hit.region,
            country: hit.country
        )
        if let v = hit.vintage { manualVintage = String(v) }
        if let c = hit.styleFr { manualStyle = c }
        vivinoResults = []
        scanStatus = "Fiche sélectionnée — enrichissement…"
        busy = true
        defer { busy = false }
        guard hit.bid > 0 else {
            scanStatus = "Vin prêt — continue vers la photo"
            app.showToast("Vin sélectionné ✓", variant: .success)
            return
        }
        do {
            let res = try await app.api.vivinoFetch(
                bid: hit.bid,
                wineName: hit.wineName,
                producer: hit.producer ?? "",
                vintage: hit.vintage
            )
            if res.ok {
                var p = res.asProduct(fallbackBarcode: "")
                if p.wineName.isEmpty { p.wineName = hit.wineName }
                if p.producer.isEmpty { p.producer = hit.producer ?? "—" }
                product = p
                if let sug = res.suggestedFlavors, !sug.isEmpty {
                    flavors = Set(sug)
                }
                scanStatus = "Vin prêt — continue vers la photo"
                app.showToast("Vin sélectionné ✓", variant: .success)
            } else {
                scanStatus = "Base OK (enrichissement partiel)"
            }
        } catch {
            scanStatus = "Base OK — enrichissement indisponible"
        }
    }

    private func processTastingPhoto(_ image: UIImage) async {
        guard let raw = image.jpegData(compressionQuality: 0.92) else { return }
        let jpeg = WineImageUtils.compressJPEG(raw)
        photoData = jpeg
        photoPreview = UIImage(data: jpeg)
    }

    private func loadNotation() async {
        showHops = false
        hopTags = []
        hops = []
        showFlavors = true
        // Tags prédéfinis depuis /api/config (+ déjà sélectionnés via enrichissement Vivino)
        if app.networkStatus == .online {
            do {
                flavorTags = try await app.api.configFlavors()
            } catch {
                flavorTags = []
            }
        }
        if flavors.isEmpty, let sug = product?.suggestedFlavors, !sug.isEmpty {
            flavors = Set(sug)
        }
        if let p = product {
            if noteVintage.isEmpty, let v = p.vintage { noteVintage = String(v) }
            if noteColor.isEmpty, let c = p.styleFr ?? (p.style != "Unknown" ? p.style : nil) {
                noteColor = c
            }
            if noteRegion.isEmpty, let r = p.region { noteRegion = r }
            if noteCountry.isEmpty, let c = p.country { noteCountry = c }
            if noteAbv.isEmpty, let a = p.abv { noteAbv = String(a) }
        }
    }

    private func save(force: Bool) async {
        guard var product else { return }
        // Appliquer détails étape Note (parité webapp)
        if let v = Int(noteVintage), v > 0 { product.vintage = v }
        if !noteColor.isEmpty {
            product.style = noteColor
            product.styleFr = noteColor
        }
        if !noteRegion.isEmpty { product.region = noteRegion }
        if !noteCountry.isEmpty { product.country = noteCountry }
        if let a = Double(noteAbv.replacingOccurrences(of: ",", with: ".")) { product.abv = a }
        self.product = product
        saving = true
        defer { saving = false }
        do {
            let msg = try await app.saveCheckin(
                product: product,
                rating: rating,
                flavors: Array(flavors),
                hops: [],
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
        scanStatus = "Cadre l’étiquette — touche pour photo"
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
        labelPreview = nil
        vivinoQuery = ""
        vivinoProducer = ""
        vivinoVintage = ""
        vivinoResults = []
        manualName = ""
        manualProducer = ""
        manualVintage = ""
        manualRegion = ""
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
        noteVintage = ""
        noteColor = ""
        noteRegion = ""
        noteCountry = ""
        noteAbv = ""
        showFlavorBrowse = false
        scanStatus = "Cadre l’étiquette — touche pour photo"
        duplicateDetail = ""
    }
}