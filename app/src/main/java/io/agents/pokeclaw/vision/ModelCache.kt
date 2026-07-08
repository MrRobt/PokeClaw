package io.agents.pokeclaw.vision

import java.io.File
import java.security.MessageDigest

/**
 * On-device cache of downloaded YOLO model artifacts, keyed by software_key.
 *
 * Layout:
 * ```
 * <root>/<safeKey>/v<version>/model.bin
 * <root>/<safeKey>/active.json      # {version, checksum, model_id}
 * ```
 * The root dir is injected (in production `context.filesDir/yolo_models`), so the
 * cache is pure and unit-tested against a temp dir.
 */
class ModelCache(private val root: File) {

    init { root.mkdirs() }

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
    fun softwareDir(key: String) = File(root, safe(key))
    fun versionDir(key: String, version: Int) = File(softwareDir(key), "v$version")
    fun modelFile(key: String, version: Int) = File(versionDir(key, version), "model.bin")
    private fun pointerFile(key: String) = File(softwareDir(key), "active.json")

    fun isCached(key: String, version: Int): Boolean = modelFile(key, version).isFile

    data class Pointer(val version: Int, val checksum: String?, val modelId: String?)

    fun cached(key: String): Pointer {
        val p = pointerFile(key)
        if (!p.isFile) return Pointer(0, null, null)
        return try {
            val t = p.readText()
            Pointer(
                version = Regex("\"version\"\\s*:\\s*(\\d+)").find(t)?.groupValues?.get(1)?.toInt() ?: 0,
                checksum = Regex("\"checksum\"\\s*:\\s*\"([^\"]*)\"").find(t)?.groupValues?.get(1),
                modelId = Regex("\"model_id\"\\s*:\\s*\"([^\"]*)\"").find(t)?.groupValues?.get(1),
            )
        } catch (e: Exception) {
            Pointer(0, null, null)
        }
    }

    fun cachedVersion(key: String): Int = cached(key).version
    fun cachedChecksum(key: String): String? = cached(key).checksum

    /** Persist bytes for a version, verify checksum, update the active pointer. */
    fun store(key: String, version: Int, modelId: String?, checksum: String?, bytes: ByteArray): File {
        val f = modelFile(key, version)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        if (checksum != null && !checksum.equals(sha256(bytes), ignoreCase = true)) {
            f.delete()
            throw IllegalStateException("checksum mismatch for $key v$version")
        }
        pointerFile(key).writeText(
            "{\"version\":$version," +
                "\"checksum\":${checksum?.let { "\"$it\"" } ?: "null"}," +
                "\"model_id\":${modelId?.let { "\"$it\"" } ?: "null"}}"
        )
        return f
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun clear(key: String) { softwareDir(key).deleteRecursively() }
}
