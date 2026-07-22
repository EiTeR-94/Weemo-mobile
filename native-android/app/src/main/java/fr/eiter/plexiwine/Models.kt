package fr.eiter.plexiwine

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class MeResponse(
    val user: String? = null,
    val auth: Boolean = false,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_invite") val isInvite: Boolean = false
)

data class LoginResponse(
    val ok: Boolean = false,
    val user: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean? = null,
    val error: String? = null
)

data class NativeJoinResponse(
    val ok: Boolean = false,
    @SerializedName("access_token") val accessToken: String? = null,
    val user: String? = null,
    val label: String? = null,
    @SerializedName("is_invite") val isInvite: Boolean = false,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    val error: String? = null
)

data class WineProduct(
    var ok: Boolean = true,
    var barcode: String = "",
    @SerializedName("wine_name") var wineName: String = "",
    var producer: String = "",
    var style: String = "Unknown",
    @SerializedName("wine_color") var styleFr: String? = null,
    var abv: Double? = null,
    var summary: String = "",
    @SerializedName("vivino_id") var vivinoId: Int? = null,
    var source: String? = null,
    @SerializedName("photo_url") var photoURL: String? = null
) {
    val displayStyle: String get() = styleFr ?: style

    companion object {
        fun fromCheckin(item: CheckinItem) = WineProduct(
            barcode = item.barcode.orEmpty(),
            wineName = item.wineName,
            producer = item.producer ?: "—",
            style = item.style ?: "Unknown",
            summary = "${item.wineName} — re-dégustation",
            vivinoId = item.vivinoId
        )

        fun fromWishlist(item: WishlistItem) = WineProduct(
            barcode = item.barcode.orEmpty(),
            wineName = item.wineName,
            producer = item.producer ?: "—",
            style = item.style ?: "Unknown",
            summary = "${item.wineName} — depuis À boire",
            source = "wishlist"
        )
    }
}

data class LookupResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val barcode: String? = null,
    @SerializedName("wine_name") val wineName: String? = null,
    val producer: String? = null,
    val style: String? = null,
    @SerializedName("wine_color") val styleFr: String? = null,
    val abv: Double? = null,
    val summary: String? = null,
    @SerializedName("vivino_id") val vivinoId: Int? = null,
    val source: String? = null,
    @SerializedName("photo_url") val photoURL: String? = null
) {
    fun asProduct(fallbackBarcode: String) = WineProduct(
        ok = ok,
        barcode = barcode ?: fallbackBarcode,
        wineName = wineName.orEmpty(),
        producer = producer.orEmpty(),
        style = style ?: "Unknown",
        styleFr = styleFr,
        abv = abv,
        summary = summary.orEmpty(),
        vivinoId = vivinoId,
        source = source,
        photoURL = photoURL
    )
}

data class CheckinItem(
    val id: Int = 0,
    @SerializedName("wine_name") val wineName: String = "",
    val producer: String? = null,
    @SerializedName("wine_color") val style: String? = null,
    val rating: Double = 0.0,
    val comment: String? = null,
    val barcode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("photo_url") val photoURL: String? = null,
    val flavors: List<String>? = null,
    val hops: List<String>? = null,
    @SerializedName("hidden_from_partner") val hiddenFromPartner: Boolean? = null,
    @SerializedName("vivino_id") val vivinoId: Int? = null,
    /** Lieu / lien de dégustation (optionnel). */
    val location: String? = null
)

data class HistoryStats(
    val total: Int = 0,
    @SerializedName("avg_rating") val avgRating: Double? = null,
    @SerializedName("top_styles") val topStyles: List<TopStyle>? = null,
    @SerializedName("top_colors") val topColors: List<TopStyle>? = null,
    val last: LastCheckin? = null
) {
    data class TopStyle(
        val style: String? = null,
        @SerializedName("color") val color: String? = null,
        val count: Int? = null
    )
    data class LastCheckin(@SerializedName("wine_name") val wineName: String? = null)
}

data class StyleOption(val value: String = "", val label: String = "")

data class WishlistItem(
    val id: Int = 0,
    @SerializedName("wine_name") val wineName: String = "",
    val producer: String? = null,
    val style: String? = null,
    val barcode: String? = null,
    val note: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class GiftIdea(
    @SerializedName("wine_name") val wineName: String = "",
    val producer: String? = null,
    val style: String? = null,
    val rating: Double? = null,
    val comment: String? = null,
    @SerializedName("photo_path") val photoPath: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("liked_by") val likedBy: String? = null,
    @SerializedName("for") val forUser: String? = null
) {
    val id: String get() = "$wineName-${likedBy.orEmpty()}-${createdAt.orEmpty()}"
}

data class CoupleStats(
    val users: List<CoupleUser>? = null,
    @SerializedName("gift_ideas") val giftIdeas: List<GiftIdea>? = null
) {
    data class CoupleUser(val username: String = "", val total: Int = 0)
}

data class VivinoSearchResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val results: List<VivinoHit>? = null
)

data class VivinoHit(
    val bid: Int = 0,
    @SerializedName("wine_name") val wineName: String = "",
    val producer: String? = null,
    @SerializedName("wine_color") val styleFr: String? = null,
    @SerializedName("photo_url") val photoURL: String? = null
)

data class FlavorsResponse(
    val flavors: List<String>? = null,
    @SerializedName("suggested_flavors") val suggestedFlavors: List<String>? = null,
    val hops: List<String>? = null,
    @SerializedName("suggested_hops") val suggestedHops: List<String>? = null,
    @SerializedName("show_flavors_block") val showFlavorsBlock: Boolean? = null,
    @SerializedName("show_hops_block") val showHopsBlock: Boolean? = null
)

data class CreateCheckinResult(
    val ok: Boolean? = null,
    val id: Int? = null,
    val duplicate: Boolean? = null,
    val error: String? = null,
    @SerializedName("previous_checkin") val previousCheckin: PreviousCheckin? = null,
    /** Weeno Quest loot (null si RPG off / non autorisé) */
    val rpg: RpgLoot? = null
)

data class PreviousCheckin(
    @SerializedName("wine_name") val wineName: String? = null,
    val rating: Double? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DecodeBarcodeResponse(
    val ok: Boolean = false,
    val barcode: String? = null,
    val error: String? = null
)

data class OkResponse(
    val ok: Boolean? = null,
    val error: String? = null
)

data class VersionResponse(val version: String? = null)

data class PatchnotesResponse(
    val version: String? = null,
    val markdown: String? = null
)

data class PendingCheckin(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val barcode: String = "",
    val wineName: String = "",
    val producer: String = "",
    val style: String = "Unknown",
    val abv: String = "",
    val summary: String = "",
    val rating: Double = 3.0,
    val flavors: List<String> = emptyList(),
    val hops: List<String> = emptyList(),
    val comment: String = "",
    val vivinoId: String = "",
    val force: Boolean = false,
    /** Absolute path to local JPEG, or null */
    val photoPath: String? = null,
    /** Lieu / lien de dégustation (optionnel). */
    val location: String? = null
)

enum class NetworkStatus(val label: String) {
    ONLINE("En ligne"),
    SERVER_UNREACHABLE("Serveur injoignable"),
    OFFLINE("Hors ligne")
}

enum class WeenoSheet {
    HISTORY, GALLERY, WISHLIST, GIFTS, PENDING, DETAIL, EDIT, ADMIN, PATCHNOTES, GRIMOIRE, RPG_ADMIN
}

data class ToastPayload(
    val message: String,
    val variant: Variant = Variant.INFO,
    val detail: String? = null,
    /** Libellé court type iOS (« Invitation », « Succès »…) — optionnel. */
    val label: String? = null
) {
    enum class Variant { INFO, SUCCESS, WARN, ERROR, DUPLICATE }
}
