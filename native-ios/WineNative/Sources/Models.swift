import Foundation

struct MeResponse: Decodable {
    let user: String?
    let username: String?
    let auth: Bool?
    let isAdmin: Bool?
    let isInvite: Bool?

    enum CodingKeys: String, CodingKey {
        case user, username, auth
        case isAdmin = "is_admin"
        case isInvite = "is_invite"
    }

    /// Backend Weeno renvoie `username` ; Beer renvoyait `user`.
    var resolvedUser: String? { user ?? username }
}

struct LoginResponse: Decodable {
    let ok: Bool
    let user: String?
    let isAdmin: Bool?
    let error: String?

    enum CodingKeys: String, CodingKey {
        case ok, user, error
        case isAdmin = "is_admin"
    }
}

struct NativeJoinResponse: Decodable {
    let ok: Bool
    let accessToken: String?
    let user: String?
    let label: String?
    let isInvite: Bool?
    let deviceId: String?
    let expiresAt: String?
    let error: String?

    enum CodingKeys: String, CodingKey {
        case ok, user, label, error
        case accessToken = "access_token"
        case isInvite = "is_invite"
        case deviceId = "device_id"
        case expiresAt = "expires_at"
    }
}

struct WineProduct: Codable, Equatable {
    var ok: Bool = true
    var barcode: String = ""
    var wineName: String = ""
    var producer: String = ""
    var style: String = "Unknown"
    var styleFr: String?
    var abv: Double?
    var summary: String = ""
    var vivinoBid: Int?
    var source: String?
    var photoURL: String?
    var vintage: Int?
    var region: String?
    var country: String?
    var grapes: [String]?
    var suggestedFlavors: [String]?

    enum CodingKeys: String, CodingKey {
        case ok, barcode, producer, style, abv, summary, source, vintage, region, country, grapes
        case wineName = "wine_name"
        case styleFr = "wine_color"
        case vivinoBid = "vivino_id"
        case photoURL = "photo_url"
        case suggestedFlavors = "suggested_flavors"
    }

    var displayStyle: String { styleFr ?? style }

    static func from(checkin: CheckinItem) -> WineProduct {
        WineProduct(
            barcode: checkin.barcode ?? "",
            wineName: checkin.wineName,
            producer: checkin.producer ?? "—",
            style: checkin.style ?? "Unknown",
            summary: "\(checkin.wineName) — re-dégustation",
            vivinoBid: checkin.vivinoBid
        )
    }
}

struct LookupResponse: Decodable {
    let ok: Bool
    let error: String?
    let barcode: String?
    let wineName: String?
    let producer: String?
    let style: String?
    let styleFr: String?
    let abv: Double?
    let summary: String?
    let vivinoBid: Int?
    let source: String?
    let photoURL: String?
    let vintage: Int?
    let region: String?
    let country: String?
    let suggestedFlavors: [String]?

    enum CodingKeys: String, CodingKey {
        case ok, error, barcode, producer, style, abv, summary, source, vintage, region, country
        case wineName = "wine_name"
        case styleFr = "wine_color"
        case vivinoBid = "vivino_id"
        case photoURL = "photo_url"
        case suggestedFlavors = "suggested_flavors"
    }

    init(
        ok: Bool, error: String?, barcode: String?, wineName: String?, producer: String?,
        style: String?, styleFr: String?, abv: Double?, summary: String?, vivinoBid: Int?,
        source: String?, photoURL: String?, vintage: Int? = nil, region: String? = nil,
        country: String? = nil, suggestedFlavors: [String]? = nil
    ) {
        self.ok = ok
        self.error = error
        self.barcode = barcode
        self.wineName = wineName
        self.producer = producer
        self.style = style
        self.styleFr = styleFr
        self.abv = abv
        self.summary = summary
        self.vivinoBid = vivinoBid
        self.source = source
        self.photoURL = photoURL
        self.vintage = vintage
        self.region = region
        self.country = country
        self.suggestedFlavors = suggestedFlavors
    }

    func asProduct(fallbackBarcode: String) -> WineProduct {
        WineProduct(
            ok: ok,
            barcode: barcode ?? fallbackBarcode,
            wineName: wineName ?? "",
            producer: producer ?? "",
            style: style ?? styleFr ?? "autre",
            styleFr: styleFr ?? style,
            abv: abv,
            summary: summary ?? "",
            vivinoBid: vivinoBid,
            source: source,
            photoURL: photoURL,
            vintage: vintage,
            region: region,
            country: country,
            suggestedFlavors: suggestedFlavors
        )
    }
}

extension String {
    var ifEmptyNil: String? { isEmpty ? nil : self }
}

struct CheckinItem: Identifiable, Codable, Hashable {
    let id: Int
    let wineName: String
    let producer: String?
    let style: String?
    let rating: Double
    let comment: String?
    let barcode: String?
    let createdAt: String?
    let photoURL: String?
    let photoPath: String?
    let flavors: [String]?
    let hops: [String]?
    let hiddenFromPartner: Bool?
    let vivinoBid: Int?
    /// Lieu / lien où le vin a été dégusté (optionnel).
    let location: String?

    enum CodingKeys: String, CodingKey {
        case id, producer, rating, comment, barcode, flavors, hops, location
        case wineName = "wine_name"
        case style = "wine_color"
        case createdAt = "created_at"
        case photoURL = "photo_url"
        case photoPath = "photo_path"
        case hiddenFromPartner = "hidden_from_partner"
        case vivinoBid = "vivino_id"
    }

    /// Weeno stocke `photo_path` (pas `photo_url` Beer).
    var resolvedPhoto: String? { photoURL ?? photoPath }
}

struct HistoryStats: Codable {
    let total: Int
    let avgRating: Double?
    let topStyles: [TopStyle]?
    let topColors: [TopStyle]?
    let last: LastCheckin?

    enum CodingKeys: String, CodingKey {
        case total, last
        case avgRating = "avg_rating"
        case topStyles = "top_styles"
        case topColors = "top_colors"
    }

    init(total: Int, avgRating: Double?, topStyles: [TopStyle]?, topColors: [TopStyle]? = nil, last: LastCheckin?) {
        self.total = total
        self.avgRating = avgRating
        self.topStyles = topStyles
        self.topColors = topColors
        self.last = last
    }

    struct TopStyle: Codable {
        let style: String?
        let color: String?
        let count: Int?
    }

    struct LastCheckin: Codable {
        let wineName: String?
        enum CodingKeys: String, CodingKey { case wineName = "wine_name" }
    }
}

struct StyleOption: Codable, Identifiable {
    let value: String
    let label: String
    var id: String { value }
}

struct WishlistItem: Identifiable, Decodable {
    let id: Int
    let wineName: String
    let producer: String?
    let style: String?
    let barcode: String?
    let note: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, producer, style, barcode, note
        case wineName = "wine_name"
        case createdAt = "created_at"
    }
}

struct GiftIdea: Identifiable, Codable {
    let id: String
    let wineName: String
    let producer: String?
    let style: String?
    let rating: Double?
    let comment: String?
    let photoPath: String?
    let createdAt: String?
    let likedBy: String?
    let forUser: String?

    enum CodingKeys: String, CodingKey {
        case producer, style, rating, comment, username
        case wineName = "wine_name"
        case wineColor = "wine_color"
        case photoPath = "photo_path"
        case createdAt = "created_at"
        case likedBy = "liked_by"
        case forUser = "for"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        wineName = try c.decode(String.self, forKey: .wineName)
        producer = try c.decodeIfPresent(String.self, forKey: .producer)
        let styleVal = try c.decodeIfPresent(String.self, forKey: .style)
        let colorVal = try c.decodeIfPresent(String.self, forKey: .wineColor)
        style = styleVal ?? colorVal
        rating = try c.decodeIfPresent(Double.self, forKey: .rating)
        comment = try c.decodeIfPresent(String.self, forKey: .comment)
        photoPath = try c.decodeIfPresent(String.self, forKey: .photoPath)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        let likedVal = try c.decodeIfPresent(String.self, forKey: .likedBy)
        let userVal = try c.decodeIfPresent(String.self, forKey: .username)
        likedBy = likedVal ?? userVal
        forUser = try c.decodeIfPresent(String.self, forKey: .forUser)
        id = "\(wineName)-\(likedBy ?? "")-\(createdAt ?? "")"
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(wineName, forKey: .wineName)
        try c.encodeIfPresent(producer, forKey: .producer)
        try c.encodeIfPresent(style, forKey: .style)
        try c.encodeIfPresent(rating, forKey: .rating)
        try c.encodeIfPresent(comment, forKey: .comment)
        try c.encodeIfPresent(photoPath, forKey: .photoPath)
        try c.encodeIfPresent(createdAt, forKey: .createdAt)
        try c.encodeIfPresent(likedBy, forKey: .likedBy)
        try c.encodeIfPresent(forUser, forKey: .forUser)
    }
}

struct CoupleStats: Codable {
    let users: [CoupleUser]?
    let giftIdeas: [GiftIdea]?

    enum CodingKeys: String, CodingKey {
        case users
        case giftIdeas = "gift_ideas"
    }

    struct CoupleUser: Codable, Identifiable {
        let username: String
        let total: Int
        var id: String { username }
    }
}

struct VisionKeyDetail: Identifiable {
    let index: Int
    let lastStatus: String
    let rateLimited: Bool
    let lastError: String?
    var id: Int { index }
}

struct VisionStatus {
    let available: Bool
    let keys: Int
    let detail: [VisionKeyDetail]
}

struct AdminUser: Identifiable, Codable {
    let username: String
    let isAdmin: Bool
    let checkins: Int
    let createdAt: String?
    let photos: Int?
    let lastCheckinAt: String?
    let stylesCount: Int?
    let breweriesCount: Int?

    var id: String { username }

    enum CodingKeys: String, CodingKey {
        case username, checkins, photos
        case isAdmin = "is_admin"
        case createdAt = "created_at"
        case lastCheckinAt = "last_checkin_at"
        case stylesCount = "styles_count"
        case breweriesCount = "breweries_count"
    }
}

struct InviteClientProfile: Codable {
    let browser: String?
    let os: String?
    let device: String?

    var isKnown: Bool {
        guard let browser, browser != "—", !browser.isEmpty else { return false }
        return true
    }
}

struct InviteItem: Identifiable, Codable {
    let id: Int
    let label: String?
    let username: String?
    let url: String?
    let createdAt: String?
    let expiresAt: String?
    let linkExpiresAt: String?
    let active: Bool?
    let linkActive: Bool?
    let revokedAt: String?
    let redeemedAt: String?
    let lastUsedAt: String?
    let reactivationPending: Bool?
    let canExtend: Bool?
    let canReissue: Bool?
    let permanent: Bool?
    let validityLabel: String?
    let checkins: Int?
    let redeemIp: String?
    let lastUsedIp: String?
    let deviceShort: String?
    let redeemClient: InviteClientProfile?
    let lastClient: InviteClientProfile?
    let ipLog: [InviteIpEntry]?
    let emailHint: String?
    let emailRequired: Bool?

    enum CodingKeys: String, CodingKey {
        case id, label, username, url, active, permanent, checkins
        case createdAt = "created_at"
        case expiresAt = "expires_at"
        case linkExpiresAt = "link_expires_at"
        case linkActive = "link_active"
        case revokedAt = "revoked_at"
        case redeemedAt = "redeemed_at"
        case lastUsedAt = "last_used_at"
        case reactivationPending = "reactivation_pending"
        case canExtend = "can_extend"
        case canReissue = "can_reissue"
        case validityLabel = "validity_label"
        case redeemIp = "redeem_ip"
        case lastUsedIp = "last_used_ip"
        case deviceShort = "device_short"
        case redeemClient = "redeem_client"
        case lastClient = "last_client"
        case ipLog = "ip_log"
        case emailHint = "email_hint"
        case emailRequired = "email_required"
    }

    var statusText: String {
        if revokedAt != nil { return "Révoquée" }
        if reactivationPending == true { return "Réactivation" }
        if redeemedAt != nil { return "Utilisée · lien mort" }
        if active == false { return "Expirée" }
        if linkActive == false { return "Lien expiré" }
        return "En attente"
    }
}

struct CreateInviteResponse: Decodable {
    let ok: Bool?
    let url: String?
    let error: String?
}

struct PatchnotesResponse: Decodable {
    let version: String?
    let markdown: String?
}

struct PendingCheckin: Identifiable, Codable {
    let id: UUID
    let createdAt: Date
    var barcode: String
    var wineName: String
    var producer: String
    var style: String
    var abv: String
    var summary: String
    var rating: Double
    var flavors: [String]
    var hops: [String]
    var comment: String
    var vivinoBid: String
    var force: Bool
    var photoJPEGBase64: String?
    /// Lieu / lien de dégustation (optionnel). Optional for legacy offline queue JSON.
    var location: String? = nil
}

struct PreviousCheckin: Decodable {
    let wineName: String?
    let rating: Double?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case rating
        case wineName = "wine_name"
        case createdAt = "created_at"
    }
}

struct CreateCheckinResult: Decodable {
    let ok: Bool?
    let id: Int?
    let duplicate: Bool?
    let error: String?
    let previousCheckin: PreviousCheckin?
    let rpg: RpgLoot?

    enum CodingKeys: String, CodingKey {
        case ok, id, duplicate, error, rpg
        case previousCheckin = "previous_checkin"
    }

    init(ok: Bool?, id: Int?, duplicate: Bool?, error: String?, previousCheckin: PreviousCheckin?, rpg: RpgLoot?) {
        self.ok = ok
        self.id = id
        self.duplicate = duplicate
        self.error = error
        self.previousCheckin = previousCheckin
        self.rpg = rpg
    }
}

struct DecodeBarcodeResponse: Decodable {
    let ok: Bool
    let barcode: String?
    let error: String?
}

struct ReferentialEntry: Codable, Identifiable {
    let name: String
    let preset: Bool?
    let deletable: Bool?
    let refId: Int?
    let kind: String?
    var id: String { "\(refId.map(String.init) ?? "x")-\(name)" }

    enum CodingKeys: String, CodingKey {
        case name, preset, deletable, kind
        case refId = "id"
    }
}

struct ReferentialsResponse: Codable {
    let colors: [ReferentialEntry]?
    let grapes: [ReferentialEntry]?
    let flavors: [ReferentialEntry]?
    let regions: [ReferentialEntry]?
    // legacy beer keys (ignored)
    let styles: [ReferentialEntry]?
    let hops: [ReferentialEntry]?
}

struct InviteIpEntry: Codable {
    let ip: String?
    let firstSeen: String?
    let lastSeen: String?

    enum CodingKeys: String, CodingKey {
        case ip
        case firstSeen = "first_seen"
        case lastSeen = "last_seen"
    }
}

struct VivinoSearchResponse: Decodable {
    let ok: Bool
    let error: String?
    let results: [VivinoHit]?
}

struct VivinoHit: Decodable, Identifiable, Hashable {
    let bid: Int
    let wineName: String
    let producer: String?
    let styleFr: String?
    let photoURL: String?
    let vintage: Int?
    let country: String?
    let region: String?
    let vivinoRating: Double?
    let vivinoURL: String?

    var id: Int { bid > 0 ? bid : wineName.hashValue }

    enum CodingKeys: String, CodingKey {
        case bid, producer, vintage, country, region
        case wineName = "wine_name"
        case styleFr = "wine_color"
        case photoURL = "photo_url"
        case vivinoRating = "vivino_rating"
        case vivinoURL = "vivino_url"
        case vivinoId = "vivino_id"
        case id = "id"
    }

    init(bid: Int, wineName: String, producer: String? = nil, styleFr: String? = nil,
         photoURL: String? = nil, vintage: Int? = nil, country: String? = nil,
         region: String? = nil, vivinoRating: Double? = nil, vivinoURL: String? = nil) {
        self.bid = bid
        self.wineName = wineName
        self.producer = producer
        self.styleFr = styleFr
        self.photoURL = photoURL
        self.vintage = vintage
        self.country = country
        self.region = region
        self.vivinoRating = vivinoRating
        self.vivinoURL = vivinoURL
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        wineName = (try? c.decode(String.self, forKey: .wineName)) ?? ""
        producer = try? c.decode(String.self, forKey: .producer)
        styleFr = try? c.decode(String.self, forKey: .styleFr)
        photoURL = try? c.decode(String.self, forKey: .photoURL)
        country = try? c.decode(String.self, forKey: .country)
        region = try? c.decode(String.self, forKey: .region)
        vivinoURL = try? c.decode(String.self, forKey: .vivinoURL)
        vivinoRating = try? c.decode(Double.self, forKey: .vivinoRating)
        if let v = try? c.decode(Int.self, forKey: .vintage) { vintage = v }
        else if let d = try? c.decode(Double.self, forKey: .vintage) { vintage = Int(d) }
        else { vintage = nil }
        if let b = try? c.decode(Int.self, forKey: .bid) { bid = b }
        else if let b = try? c.decode(Int.self, forKey: .vivinoId) { bid = b }
        else if let b = try? c.decode(Int.self, forKey: .id) { bid = b }
        else if let d = try? c.decode(Double.self, forKey: .vivinoId) { bid = Int(d) }
        else { bid = 0 }
    }
}

/// Résultat POST /api/label-scan (parité webapp Gemini + candidats Vivino).
struct LabelScanResult {
    let ok: Bool
    let aiAvailable: Bool
    let aiError: String?
    let wineName: String?
    let producer: String?
    let wineColor: String?
    let vintage: Int?
    let abv: Double?
    let region: String?
    let candidates: [VivinoHit]
    let vivinoQuery: String?
    let labelPhotoPath: String?
}

struct FlavorsResponse: Decodable {
    let flavors: [String]?
    let suggestedFlavors: [String]?
    let hops: [String]?
    let suggestedHops: [String]?
    let showFlavorsBlock: Bool?
    let showHopsBlock: Bool?

    enum CodingKeys: String, CodingKey {
        case flavors, hops
        case suggestedFlavors = "suggested_flavors"
        case suggestedHops = "suggested_hops"
        case showFlavorsBlock = "show_flavors_block"
        case showHopsBlock = "show_hops_block"
    }

    init(
        flavors: [String]?, suggestedFlavors: [String]?, hops: [String]?,
        suggestedHops: [String]?, showFlavorsBlock: Bool?, showHopsBlock: Bool?
    ) {
        self.flavors = flavors
        self.suggestedFlavors = suggestedFlavors
        self.hops = hops
        self.suggestedHops = suggestedHops
        self.showFlavorsBlock = showFlavorsBlock
        self.showHopsBlock = showHopsBlock
    }
}

struct OKResponse: Decodable {
    let ok: Bool?
    let error: String?
}

// (legacy structs removed)

struct CheckinsListResponse: Decodable {
    let items: [CheckinItem]?
    let count: Int?
    let limit: Int?
    let offset: Int?
}
