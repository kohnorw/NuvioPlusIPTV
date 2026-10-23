package com.nuvio.tv.livetv.sync

import android.content.Context
import android.util.Log
import com.nuvio.tv.livetv.data.LiveTvPreferences
import com.nuvio.tv.livetv.data.LiveTvRepository
import com.nuvio.tv.livetv.model.LiveChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the settings screen shows for Nuvio Bridge. */
data class BridgeSyncState(
    val connected: Boolean = false,
    val url: String? = null,
    val email: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncMs: Long = 0L
)

/**
 * Keeps Live TV favorites the same here and on a Nuvio Bridge server
 * (https://github.com/kohnorw/nuvio-bridge), both ways.
 *
 * The bridge keeps its own copy of your playlists, with its own channel keys, so channels are
 * matched by name. Sync is a three-way merge against what both sides agreed on last time: a
 * favorite added on either side is added to the other, one removed on either side is removed
 * from the other, and a favorite whose channel the other side doesn't have is left alone.
 *
 * It signs in with the Nuvio account this app is already signed in to: the bridge checks the
 * app's access token, so there is nothing to enter and no password is stored.
 */
@Singleton
class LiveTvBridgeSync @Inject constructor(
    @ApplicationContext context: Context,
    private val prefs: LiveTvPreferences,
    private val repository: LiveTvRepository,
    private val auth: io.github.jan.supabase.auth.Auth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = context.applicationContext.getSharedPreferences("livetv_bridge_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .dns(com.nuvio.tv.core.network.IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BridgeSyncState> = _state.asStateFlow()

    @Volatile private var started = false
    /** Set while this class writes favorites, so its own change doesn't start another sync. */
    @Volatile private var applying = false

    private fun load() = BridgeSyncState(
        connected = store.getBoolean(KEY_ON, true),
        url = BRIDGE_URL,
        email = runCatching { auth.currentUserOrNull()?.email }.getOrNull(),
        lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L)
    )

    /** The Nuvio sign-in this app already has. Null when signed out. */
    private fun token(): String? =
        runCatching { auth.currentSessionOrNull()?.accessToken }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Syncs once channels are loaded, after every favorite change, and every 15 minutes. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Wait for the channel list: matching needs names.
            repository.channels.first { it.isNotEmpty() }
            syncNow()
        }
        scope.launch {
            prefs.userState.map { it.favorites }.distinctUntilChanged().drop(1).debounce(3_000).collect {
                if (!applying) syncNow()
            }
        }
        scope.launch {
            while (isActive) {
                delay(15 * 60_000L)
                syncNow()
            }
        }
    }

    /** Turns syncing on again (it's on by default). */
    fun connect() {
        store.edit().putBoolean(KEY_ON, true).remove(KEY_LAST_SET).apply()
        _state.value = load().copy(message = "On")
        start()
        syncNowAsync()
    }

    fun disconnect() {
        store.edit().putBoolean(KEY_ON, false).remove(KEY_LAST_SET).apply()
        _state.value = load().copy(message = "Off")
    }

    fun syncNowAsync() { scope.launch { syncNow() } }

    private suspend fun syncNow() {
        if (!store.getBoolean(KEY_ON, true)) return
        val base = BRIDGE_URL
        val sid = token() ?: run {
            _state.value = load().copy(message = "Sign in to your Nuvio account to sync favorites")
            return
        }
        mutex.withLock {
            _state.value = load().copy(busy = true)
            runCatching { sync(base, sid) }
                .onSuccess { changed ->
                    val now = System.currentTimeMillis()
                    store.edit().putLong(KEY_LAST_SYNC, now).apply()
                    _state.value = _state.value.copy(
                        busy = false, lastSyncMs = now,
                        message = if (changed > 0) "Synced $changed favorite${if (changed == 1) "" else "s"}" else "Up to date"
                    )
                }
                .onFailure { e ->
                    Log.w(TAG, "Bridge sync failed", e)
                    _state.value = _state.value.copy(busy = false, message = e.message ?: "Sync failed")
                }
        }
    }

    /** One full two-way merge. Returns how many favorites changed on either side. */
    private suspend fun sync(base: String, sid: String): Int {
        val appChannels: List<LiveChannel> = repository.channels.value
        if (appChannels.isEmpty()) return 0

        // ---- the bridge's side
        val remote = getJson(base, sid, "/api/iptv/channels")
        val remoteChannels = remote.optJSONArray("channels") ?: JSONArray()
        val remoteKeyToName = HashMap<String, String>(remoteChannels.length())
        val remoteNameToKey = LinkedHashMap<String, String>()
        for (i in 0 until remoteChannels.length()) {
            val c = remoteChannels.optJSONObject(i) ?: continue
            val key = c.optString("key").ifBlank { null } ?: continue
            val n = norm(c.optString("name"))
            if (n.isEmpty()) continue
            remoteKeyToName[key] = n
            remoteNameToKey.putIfAbsent(n, key)
        }
        val remoteFavKeys = remote.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val b = remoteFavKeys.mapNotNull { remoteKeyToName[it] }.toCollection(LinkedHashSet())

        // ---- this TV's side
        val appKeyToName = appChannels.associate { it.key to norm(it.name) }
        val appNameToKey = LinkedHashMap<String, String>()
        appChannels.forEach { c -> appKeyToName[c.key]?.takeIf { it.isNotEmpty() }?.let { appNameToKey.putIfAbsent(it, c.key) } }
        val appFavKeys = prefs.userState.first().favorites
        val a = appFavKeys.mapNotNull { appKeyToName[it]?.takeIf { n -> n.isNotEmpty() } }.toCollection(LinkedHashSet())

        // A favorite the other side can't show counts as present there, so it's never dropped.
        val aFull = a + b.filter { it !in appNameToKey }
        val bFull = b + a.filter { it !in remoteNameToKey }
        val last = store.getStringSet(KEY_LAST_SET, null)
        val result: Set<String> = if (last == null) {
            // First sync: keep everything from both sides.
            LinkedHashSet(aFull + bFull)
        } else {
            LinkedHashSet<String>().apply {
                addAll(aFull.filter { it in bFull || it !in last })
                addAll(bFull.filter { it in aFull || it !in last })
            }
        }

        var changed = 0
        // ---- apply here: keep this TV's order, then new ones in the bridge's order
        val newAppKeys = ArrayList<String>()
        appFavKeys.forEach { k ->
            val n = appKeyToName[k]
            // Favorites whose channel isn't loaded now (hidden, playlist off) are left as they are.
            if (n == null || n.isEmpty() || n in result) newAppKeys += k
        }
        val haveNames = newAppKeys.mapNotNull { appKeyToName[it] }.toHashSet()
        result.forEach { n -> if (n !in haveNames) appNameToKey[n]?.let { newAppKeys += it } }
        if (newAppKeys != appFavKeys) {
            changed += (newAppKeys.toSet() - appFavKeys.toSet()).size + (appFavKeys.toSet() - newAppKeys.toSet()).size
            applying = true
            try {
                prefs.replaceFavorites(newAppKeys)
            } finally {
                delay(3_500) // past the change listener's debounce
                applying = false
            }
        }

        // ---- apply on the bridge (its API toggles one at a time)
        for (k in remoteFavKeys) {
            val n = remoteKeyToName[k] ?: continue
            if (n !in result) { toggleRemote(base, sid, k); changed++ }
        }
        for (n in result) {
            if (n in b) continue
            val k = remoteNameToKey[n] ?: continue
            toggleRemote(base, sid, k); changed++
        }

        store.edit().putStringSet(KEY_LAST_SET, HashSet(result)).apply()
        return changed
    }

    private fun toggleRemote(base: String, sid: String, key: String) {
        val body = JSONObject().put("key", key).toString().toRequestBody(json)
        val req = Request.Builder().url("$base/api/iptv/favorite").header("Authorization", "Bearer $sid").post(body).build()
        http.newCall(req).execute().use { res -> check(res) }
    }

    private fun getJson(base: String, sid: String, path: String): JSONObject {
        val req = Request.Builder().url(base + path).header("Authorization", "Bearer $sid").get().build()
        return http.newCall(req).execute().use { res ->
            check(res)
            JSONObject(res.body?.string().orEmpty())
        }
    }

    private fun check(res: okhttp3.Response) {
        if (res.code == 401) error("The bridge didn't accept your Nuvio sign-in. Try again shortly.")
        if (!res.isSuccessful) error("Bridge error ${res.code}")
    }

    companion object {
        private const val TAG = "LiveTvBridgeSync"
        const val BRIDGE_URL = "https://inuvio.kohnor.ca"
        private const val KEY_ON = "on"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_SET = "last_set"

        /** Channel names compared loosely: case, accents and punctuation ignored. */
        fun norm(name: String): String =
            java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "")
    }
}
