package io.agents.pokeclaw.cloud.cloudphone

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.agents.pokeclaw.cloud.CloudClientFactory
import io.agents.pokeclaw.cloud.cloudphone.model.ConnectionDto
import io.agents.pokeclaw.cloud.cloudphone.model.ControlReqDto
import io.agents.pokeclaw.cloud.cloudphone.model.ControlRespDto
import io.agents.pokeclaw.cloud.cloudphone.model.InstanceDto
import io.agents.pokeclaw.cloud.cloudphone.model.InstancePageDto
import io.agents.pokeclaw.utils.XLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 * Cloud-phone instance client. Talks to `dyq-module-cloudphone`'s admin API so the
 * console can list/select a cloud-phone (Claw) instance, read its connection info,
 * and remote-control it (tap/swipe/input/key) via the `/control` `InstanceControlReqVO`
 * contract. Responses use the yudao `CommonResult{code,data,msg}` envelope.
 */
class CloudPhoneClient(
    baseUrl: String,
    private val bearerToken: String? = null,
    private val tenantId: String? = null,
    private val http: OkHttpClient = defaultHttp(),
    private val gson: Gson = Gson(),
) {
    private val base = CloudClientFactory.normalizeBaseUrl(baseUrl).trimEnd('/')

    companion object {
        private const val TAG = "PokeClaw/CloudPhoneClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun newReq(path: String): Request.Builder {
        val b = Request.Builder().url("$base$path")
        if (!bearerToken.isNullOrBlank()) b.addHeader("Authorization", "Bearer $bearerToken")
        if (!tenantId.isNullOrBlank()) b.addHeader("tenant-id", tenantId)
        return b
    }

    private fun exec(request: Request): String? = try {
        http.newCall(request).execute().use { r ->
            val t = r.body?.string()
            if (!r.isSuccessful) {
                XLog.w(TAG, "HTTP ${r.code} ${request.url}: ${t?.take(200)}")
                null
            } else t
        }
    } catch (e: Exception) {
        XLog.e(TAG, "exec failed ${request.url}", e)
        null
    }

    /** Unwrap a yudao CommonResult<T>; returns data only on code==0. */
    private fun <T> unwrap(text: String?, dataType: Type): T? {
        if (text.isNullOrBlank()) return null
        return try {
            val obj = gson.fromJson(text, JsonObject::class.java)
            val code = obj.get("code")?.asInt ?: -1
            if (code != 0) {
                XLog.w(TAG, "dyq code=$code msg=${obj.get("msg")}")
                return null
            }
            val data = obj.get("data") ?: return null
            gson.fromJson(data, dataType)
        } catch (e: Exception) {
            XLog.e(TAG, "unwrap failed", e)
            null
        }
    }

    fun listInstances(pageNo: Int = 1, pageSize: Int = 50): List<InstanceDto> {
        val text = exec(newReq("/admin-api/cloudphone/instance/page?pageNo=$pageNo&pageSize=$pageSize").get().build())
        val page: InstancePageDto? = unwrap(text, InstancePageDto::class.java)
        return page?.list ?: emptyList()
    }

    fun getConnection(id: Long): ConnectionDto? {
        val text = exec(newReq("/admin-api/cloudphone/instance/connection?id=$id").get().build())
        return unwrap(text, ConnectionDto::class.java)
    }

    fun control(instanceId: Long, actionType: String, params: Map<String, Any?>): ControlRespDto? {
        val body = gson.toJson(ControlReqDto(instanceId, actionType, params)).toRequestBody(JSON)
        val text = exec(newReq("/admin-api/cloudphone/instance/control").post(body).build())
        return unwrap(text, ControlRespDto::class.java)
    }

    fun startInstance(id: Long): Boolean =
        exec(newReq("/admin-api/cloudphone/instance/start?id=$id").post("".toRequestBody(JSON)).build()) != null

    fun stopInstance(id: Long): Boolean =
        exec(newReq("/admin-api/cloudphone/instance/stop?id=$id").post("".toRequestBody(JSON)).build()) != null

    // convenience actions mapping to InstanceControlReqVO actionType + params
    fun tap(id: Long, x: Int, y: Int) = control(id, "click", mapOf("x" to x, "y" to y))
    fun swipe(id: Long, sx: Int, sy: Int, ex: Int, ey: Int, dur: Int = 300) =
        control(id, "swipe", mapOf("startX" to sx, "startY" to sy, "endX" to ex, "endY" to ey, "duration" to dur))
    fun input(id: Long, text: String) = control(id, "input", mapOf("text" to text))
    fun keyEvent(id: Long, keyCode: Int) = control(id, "keyEvent", mapOf("keyCode" to keyCode))
}
