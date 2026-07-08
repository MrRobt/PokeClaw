package io.agents.pokeclaw.cloud.modelhub

import com.google.gson.Gson
import io.agents.pokeclaw.cloud.CloudClientFactory
import io.agents.pokeclaw.cloud.modelhub.model.DatasetListDto
import io.agents.pokeclaw.cloud.modelhub.model.ModelDescriptorDto
import io.agents.pokeclaw.cloud.modelhub.model.PromoteResultDto
import io.agents.pokeclaw.cloud.modelhub.model.ResolveRequestDto
import io.agents.pokeclaw.cloud.modelhub.model.SampleAckDto
import io.agents.pokeclaw.cloud.modelhub.model.SoftwareListDto
import io.agents.pokeclaw.cloud.modelhub.model.TrainingJobDto
import io.agents.pokeclaw.utils.XLog
import io.agents.pokeclaw.vision.YoloModelDescriptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cloud YOLO Model Hub client (OkHttp + Gson, synchronous — call off the main
 * thread). Covers model routing/download, dataset upload by software_key, training
 * trigger, promote, rollback, and registry reads for the console.
 */
class ModelHubClient(
    baseUrl: String,
    private val bearerToken: String? = null,
    private val http: OkHttpClient = defaultHttp(),
    private val gson: Gson = Gson(),
) {
    private val base = CloudClientFactory.normalizeBaseUrl(baseUrl).trimEnd('/')

    companion object {
        private const val TAG = "PokeClaw/ModelHubClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private fun newReq(url: String): Request.Builder {
        val b = Request.Builder().url(url)
        if (!bearerToken.isNullOrBlank()) b.addHeader("Authorization", "Bearer $bearerToken")
        return b
    }

    private fun <T> call(request: Request, cls: Class<T>): T? {
        return try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string()
                if (!resp.isSuccessful) {
                    XLog.w(TAG, "HTTP ${resp.code} ${request.url}: ${text?.take(300)}")
                    return null
                }
                if (text.isNullOrBlank()) null else unwrapEnvelope(text, cls)
            }
        } catch (e: Exception) {
            XLog.e(TAG, "call failed ${request.url}", e)
            null
        }
    }

    /** Accept either a bare JSON body (Python hub) or a yudao CommonResult{code,data,msg} (dyq Java). */
    private fun <T> unwrapEnvelope(text: String, cls: Class<T>): T? {
        return try {
            val root = com.google.gson.JsonParser.parseString(text)
            val payload = if (root.isJsonObject) {
                val o = root.asJsonObject
                if (o.has("code") && o.has("data")) o.get("data") else root
            } else {
                root
            }
            if (payload.isJsonNull) null else gson.fromJson(payload, cls)
        } catch (e: Exception) {
            XLog.e(TAG, "parse failed", e)
            null
        }
    }

    private fun <T> get(path: String, cls: Class<T>): T? = call(newReq("$base$path").get().build(), cls)

    private fun <T> post(path: String, body: Any, cls: Class<T>): T? =
        call(newReq("$base$path").post(gson.toJson(body).toRequestBody(JSON)).build(), cls)

    // ---- model routing + artifact download --------------------------------
    fun resolve(req: ResolveRequestDto): YoloModelDescriptor? =
        post("/api/v1/models/resolve", req, ModelDescriptorDto::class.java)?.toDescriptor()

    /** Download the model artifact to [dest]; returns the bytes (for checksum verify), or null. */
    fun download(downloadUrl: String, dest: File): ByteArray? {
        // hub returns an absolute URL (Python) or a relative /api/v1/... path (dyq Java)
        val url = if (downloadUrl.startsWith("http", ignoreCase = true)) downloadUrl else "$base$downloadUrl"
        return try {
            http.newCall(newReq(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    XLog.w(TAG, "download ${resp.code} $downloadUrl")
                    return null
                }
                val bytes = resp.body?.bytes() ?: return null
                dest.parentFile?.mkdirs()
                dest.writeBytes(bytes)
                XLog.i(TAG, "downloaded ${bytes.size} bytes -> ${dest.name}")
                bytes
            }
        } catch (e: Exception) {
            XLog.e(TAG, "download failed $downloadUrl", e)
            null
        }
    }

    // ---- dataset ingest by software_key -----------------------------------
    fun uploadSample(softwareKey: String, payload: Map<String, Any?>): SampleAckDto? =
        post("/api/v1/datasets/$softwareKey/samples", payload, SampleAckDto::class.java)

    fun uploadSamples(softwareKey: String, payloads: List<Map<String, Any?>>): SampleAckDto? =
        post("/api/v1/datasets/$softwareKey/samples/batch", mapOf("samples" to payloads), SampleAckDto::class.java)

    fun listDatasets(): DatasetListDto? = get("/api/v1/datasets", DatasetListDto::class.java)

    // ---- training + publish -----------------------------------------------
    fun triggerTraining(softwareKey: String, notes: String? = null): TrainingJobDto? =
        post("/api/v1/training/$softwareKey/trigger", mapOf("notes" to notes), TrainingJobDto::class.java)

    fun getTraining(jobId: String): TrainingJobDto? =
        get("/api/v1/training/$jobId", TrainingJobDto::class.java)

    fun promote(modelId: String, force: Boolean = false): PromoteResultDto? =
        post("/api/v1/models/$modelId/promote", mapOf("force" to force), PromoteResultDto::class.java)

    @Suppress("UNCHECKED_CAST")
    fun rollback(softwareKey: String): Map<String, Any?>? =
        post("/api/v1/software/$softwareKey/rollback", emptyMap<String, Any?>(), Map::class.java) as Map<String, Any?>?

    // ---- registry ----------------------------------------------------------
    fun listSoftware(): SoftwareListDto? = get("/api/v1/software", SoftwareListDto::class.java)

    fun registerSoftware(softwareKey: String, name: String?, packageName: String?, category: String?): Boolean {
        val body = mapOf("software_key" to softwareKey, "name" to name, "package_name" to packageName, "category" to category)
        return post("/api/v1/software/register", body, Map::class.java) != null
    }
}
