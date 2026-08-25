package fr.eiter.plexiwine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

suspend fun WineAPI.adminUsers(): List<AdminUser> = withContext(Dispatchers.IO) {
    // Backend renvoie tous les comptes, invités inclus — l'onglet Comptes doit
    // les filtrer (parité webapp admin.js: `!username.startsWith("invite_")`),
    // ils ont leur propre onglet Invités.
    rawAdminUsers().filter { !it.username.lowercase().startsWith("invite_") }
}

suspend fun WineAPI.rawAdminUsers(): List<AdminUser> {
    val (body, code) = execute(requestBuilder("api/admin/users").get().build())
    if (code !in 200..299) return emptyList()
    // Array (parité Beer) ou ancien wrapper {users:[…]}
    try {
        val type = object : TypeToken<List<AdminUser>>() {}.type
        gson.fromJson<List<AdminUser>>(body, type)?.let { list ->
            // Si le JSON est un objet, Gson renvoie null ou crash — try wrap
            if (body.trimStart().startsWith("[")) return list
        }
    } catch (_: Exception) {
    }
    try {
        val wrap = gson.fromJson(body, AdminUsersWrap::class.java)
        if (wrap?.users != null) return wrap.users
    } catch (_: Exception) {
    }
    return try {
        val type = object : TypeToken<List<AdminUser>>() {}.type
        gson.fromJson<List<AdminUser>>(body, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun WineAPI.adminCreateUser(username: String, password: String, isAdmin: Boolean) {
    val json = gson.toJson(
        mapOf("username" to username, "password" to password, "is_admin" to isAdmin)
    )
    val (body, code) = execute(
        requestBuilder("api/admin/users").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) {
        val err = try {
            gson.fromJson(body, CreateInviteResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        throw WineAPI.ApiException(err ?: "Création compte impossible", code)
    }
}

suspend fun WineAPI.adminDeleteUser(username: String) {
    val enc = java.net.URLEncoder.encode(username, "UTF-8")
    val (_, code) = execute(requestBuilder("api/admin/users/$enc").delete().build())
    if (code !in 200..299) throw WineAPI.ApiException("Suppression impossible", code)
}

suspend fun WineAPI.adminSetAdmin(username: String, isAdmin: Boolean) {
    val enc = java.net.URLEncoder.encode(username, "UTF-8")
    val json = gson.toJson(mapOf("is_admin" to isAdmin))
    val (_, code) = execute(
        requestBuilder("api/admin/users/$enc").patch(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Changement admin impossible", code)
}

suspend fun WineAPI.adminSetPassword(username: String, password: String) {
    val enc = java.net.URLEncoder.encode(username, "UTF-8")
    val json = gson.toJson(mapOf("password" to password))
    val (_, code) = execute(
        requestBuilder("api/admin/users/$enc").patch(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Mot de passe non mis à jour", code)
}

suspend fun WineAPI.adminCleanupPhotos(): String {
    val (body, code) = execute(
        requestBuilder("api/admin/photos/cleanup").post("{}".toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Nettoyage impossible", code)
    val d = gson.fromJson(body, CleanupPhotosResponse::class.java)
    return d?.message
        ?: d?.detail
        ?: (d?.removed?.let { "Supprimé : $it photo(s)" })
        ?: "Photos nettoyées"
}

suspend fun WineAPI.adminReferentials(): ReferentialsResponse = withContext(Dispatchers.IO) {
    val (body, code) = execute(requestBuilder("api/admin/referentials").get().build())
    if (code !in 200..299) return@withContext ReferentialsResponse()
    gson.fromJson(body, ReferentialsResponse::class.java) ?: ReferentialsResponse()
}

suspend fun WineAPI.adminAddFlavor(name: String) {
    val json = gson.toJson(mapOf("name" to name, "kind" to "arome"))
    val (_, code) = execute(
        requestBuilder("api/admin/referentials/flavors").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Ajout arôme impossible", code)
}

suspend fun WineAPI.adminDeleteFlavor(id: Int) {
    val (_, code) = execute(requestBuilder("api/admin/referentials/flavors/$id").delete().build())
    if (code !in 200..299) throw WineAPI.ApiException("Suppression arôme impossible", code)
}

suspend fun WineAPI.adminAddRegion(name: String) {
    val json = gson.toJson(mapOf("name" to name))
    val (_, code) = execute(
        requestBuilder("api/admin/referentials/regions").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Ajout région impossible", code)
}

suspend fun WineAPI.adminDeleteRegion(id: Int) {
    val (_, code) = execute(requestBuilder("api/admin/referentials/regions/$id").delete().build())
    if (code !in 200..299) throw WineAPI.ApiException("Suppression région impossible", code)
}

// ── Feedback admin + réponses joueur ────────────────────────────────────

