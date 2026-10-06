package com.snothin.ghostsam.data.prefs

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Local payload profile (source record, files not copied): standard trio / standalone ko+ksud;
 *  SAF URIs; save/validation/hash all app-local (no su/Shizuku); sha256 digests recorded on save
 *  (blank = "-"); successCount > 0 shows a green list background. */
enum class PayloadProfileKind {
    Standard,
    Standalone;

    companion object {
        fun parse(raw: String?): PayloadProfileKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Standard
    }
}

data class PayloadProfile(
    val id: String,
    val name: String,
    val preloadPath: String,
    val ksudPath: String,
    val suDaemonPath: String,
    val koPath: String = "",
    val kind: PayloadProfileKind = PayloadProfileKind.Standard,
    val hashes: String = "",
    val createdAtMillis: Long,
    val editedAtMillis: Long,
    val lastRunAtMillis: Long? = null,
    val runCount: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
) {
    val isProven: Boolean get() = successCount > 0

    fun copyForEdit(
        name: String,
        preloadPath: String,
        ksudPath: String,
        suDaemonPath: String,
        koPath: String,
        hashes: String,
        editedAtMillis: Long = System.currentTimeMillis(),
    ): PayloadProfile = copy(
        name = name,
        preloadPath = preloadPath,
        ksudPath = ksudPath,
        suDaemonPath = suDaemonPath,
        koPath = koPath,
        hashes = hashes,
        editedAtMillis = editedAtMillis,
    )
}

class PayloadProfileStore(context: Context) {
    private val file = File(context.filesDir, "payload-profiles.json")

    companion object {
        /** Cross-instance lock (stores are created per call): prevents stats-update vs list-delete/save
         *  interleaving from losing updates or corrupting the same AtomicFile .new. */
        private val FILE_LOCK = Any()
    }

    fun update(transform: (List<PayloadProfile>) -> List<PayloadProfile>): List<PayloadProfile> =
        synchronized(FILE_LOCK) {
            val current = loadLocked()
            val next = transform(current)
            if (next !== current) saveLocked(next)
            next
        }

    /** All profiles (newest first). Per-entry isolation: a bad record is skipped, never clears the table. */
    fun load(): List<PayloadProfile> = synchronized(FILE_LOCK) { loadLocked() }

    private fun loadLocked(): List<PayloadProfile> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        buildList {
            for (i in 0 until array.length()) {
                val value = runCatching { array.getJSONObject(i) }.getOrNull() ?: continue
                payloadProfileFromJson(value)?.let { add(it) }
            }
        }.sortedByDescending(PayloadProfile::createdAtMillis)
    }.getOrDefault(emptyList())

    fun save(profiles: List<PayloadProfile>) {
        synchronized(FILE_LOCK) { saveLocked(profiles) }
    }

    private fun saveLocked(profiles: List<PayloadProfile>) {
        val array = JSONArray()
        profiles.forEach { p -> array.put(payloadProfileToJson(p)) }
        val target = AtomicFile(file)
        val output = target.startWrite()
        try {
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }
}

internal fun payloadProfileToJson(p: PayloadProfile): JSONObject = JSONObject()
    .put("id", p.id)
    .put("name", p.name)
    .put("preloadPath", p.preloadPath)
    .put("ksudPath", p.ksudPath)
    .put("suDaemonPath", p.suDaemonPath)
    .put("koPath", p.koPath)
    .put("kind", p.kind.name)
    .put("hashes", p.hashes)
    .put("createdAtMillis", p.createdAtMillis)
    .put("editedAtMillis", p.editedAtMillis)
    .put("lastRunAtMillis", p.lastRunAtMillis ?: JSONObject.NULL)
    .put("runCount", p.runCount)
    .put("successCount", p.successCount)
    .put("failCount", p.failCount)

internal fun payloadProfileFromJson(value: JSONObject): PayloadProfile? = runCatching {
    PayloadProfile(
        id = value.getString("id"),
        name = value.getString("name"),
        preloadPath = value.getString("preloadPath"),
        ksudPath = value.getString("ksudPath"),
        suDaemonPath = value.getString("suDaemonPath"),
        koPath = value.optString("koPath", ""),
        kind = PayloadProfileKind.parse(value.optString("kind")),
        hashes = value.optString("hashes", ""),
        createdAtMillis = value.getLong("createdAtMillis"),
        editedAtMillis = value.getLong("editedAtMillis"),
        lastRunAtMillis = if (value.isNull("lastRunAtMillis")) {
            null
        } else {
            value.getLong("lastRunAtMillis")
        },
        runCount = value.optInt("runCount", 0),
        successCount = value.optInt("successCount", 0),
        failCount = value.optInt("failCount", 0),
    )
}.getOrNull()

fun newPayloadProfile(
    name: String,
    preloadPath: String,
    ksudPath: String,
    suDaemonPath: String,
    koPath: String,
    kind: PayloadProfileKind,
    hashes: String,
): PayloadProfile = PayloadProfile(
    id = UUID.randomUUID().toString(),
    name = name,
    preloadPath = preloadPath,
    ksudPath = ksudPath,
    suDaemonPath = suDaemonPath,
    koPath = koPath,
    kind = kind,
    hashes = hashes,
    createdAtMillis = System.currentTimeMillis(),
    editedAtMillis = System.currentTimeMillis(),
)
