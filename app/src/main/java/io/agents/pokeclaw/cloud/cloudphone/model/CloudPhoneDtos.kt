package io.agents.pokeclaw.cloud.cloudphone.model

import com.google.gson.annotations.SerializedName

/** DTOs for the dyq cloudphone admin API (`/admin-api/cloudphone/instance/...`). */

data class InstanceDto(
    val id: Long? = null,
    @SerializedName("instanceNo") val instanceNo: String? = null,
    @SerializedName("instanceName") val instanceName: String? = null,
    @SerializedName("instanceStatus") val instanceStatus: Int? = null,
    @SerializedName("powerStatus") val powerStatus: Int? = null,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("connectionProtocol") val connectionProtocol: String? = null,
    @SerializedName("connectionUrl") val connectionUrl: String? = null,
) {
    fun label(): String = instanceName ?: instanceNo ?: "instance#$id"
}

data class InstancePageDto(
    val list: List<InstanceDto> = emptyList(),
    val total: Long = 0,
)

data class ConnectionDto(
    @SerializedName("instanceId") val instanceId: Long? = null,
    @SerializedName("connectionProtocol") val connectionProtocol: String? = null,
    @SerializedName("connectionUrl") val connectionUrl: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("accessKey") val accessKey: String? = null,
)

/** Matches dyq InstanceControlReqVO: actionType ∈ click|doubleClick|longClick|swipe|input|keyEvent. */
data class ControlReqDto(
    @SerializedName("instanceId") val instanceId: Long,
    @SerializedName("actionType") val actionType: String,
    val params: Map<String, Any?> = emptyMap(),
)

data class ControlRespDto(
    @SerializedName("instanceId") val instanceId: Long? = null,
    val success: Boolean = false,
    @SerializedName("taskId") val taskId: String? = null,
    val message: String? = null,
)
