package io.agents.pokeclaw.cloud.modelhub.model

import com.google.gson.annotations.SerializedName
import io.agents.pokeclaw.vision.CandidateModel
import io.agents.pokeclaw.vision.YoloModelDescriptor

/** Wire DTOs for the cloud YOLO Model Hub (`/api/v1/...`). */

data class ResolveRequestDto(
    @SerializedName("software_key") val softwareKey: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    val activity: String? = null,
    @SerializedName("window_title") val windowTitle: String? = null,
    @SerializedName("task_goal") val taskGoal: String? = null,
    val category: String? = null,
    @SerializedName("current_version") val currentVersion: Int? = null,
)

data class CandidateDto(
    @SerializedName("model_id") val modelId: String? = null,
    val version: Int = 0,
    val checksum: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    val classes: List<String> = emptyList(),
    @SerializedName("shadow_only") val shadowOnly: Boolean = true,
) {
    fun toModel(): CandidateModel? =
        modelId?.let { CandidateModel(it, version, checksum, downloadUrl, classes, shadowOnly) }
}

data class ModelDescriptorDto(
    @SerializedName("software_key") val softwareKey: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("model_id") val modelId: String? = null,
    val version: Int = 0,
    val status: String = "",
    val source: String = "",
    @SerializedName("source_kind") val sourceKind: String = "",
    val classes: List<String> = emptyList(),
    @SerializedName("default_confidence") val defaultConfidence: Float = 0.35f,
    @SerializedName("confidence_thresholds") val confidenceThresholds: Map<String, Float> = emptyMap(),
    @SerializedName("download_url") val downloadUrl: String? = null,
    val checksum: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long = 0,
    val format: String = "stub-v1",
    @SerializedName("needs_data") val needsData: Boolean = false,
    @SerializedName("update_available") val updateAvailable: Boolean = false,
    val candidate: CandidateDto? = null,
) {
    fun toDescriptor(): YoloModelDescriptor = YoloModelDescriptor(
        softwareKey = softwareKey, packageName = packageName, modelId = modelId,
        version = version, status = status.ifBlank { "active" }, source = source,
        sourceKind = sourceKind, classes = classes, defaultConfidence = defaultConfidence,
        confidenceThresholds = confidenceThresholds, downloadUrl = downloadUrl,
        checksum = checksum, sizeBytes = sizeBytes, format = format,
        needsData = needsData, updateAvailable = updateAvailable, candidate = candidate?.toModel(),
    )
}

data class SampleAckDto(
    @SerializedName("sample_id") val sampleId: String? = null,
    @SerializedName("software_key") val softwareKey: String? = null,
    @SerializedName("sample_count") val sampleCount: Int = 0,
    val ingested: Int = 0,
)

data class TrainingJobDto(
    @SerializedName("job_id") val jobId: String? = null,
    @SerializedName("software_key") val softwareKey: String? = null,
    val status: String = "",
    @SerializedName("candidate_model_id") val candidateModelId: String? = null,
    @SerializedName("candidate_version") val candidateVersion: Int? = null,
    // Any? not Double: metrics mix numbers with "simulated": true; a Double adapter would throw on the boolean.
    val metrics: Map<String, Any?> = emptyMap(),
)

data class PromoteResultDto(
    val promoted: Boolean = false,
    @SerializedName("gate_passed") val gatePassed: Boolean = false,
    val forced: Boolean = false,
    val reasons: List<String> = emptyList(),
)

data class SoftwareSummaryDto(
    @SerializedName("software_key") val softwareKey: String? = null,
    val name: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    val category: String? = null,
    @SerializedName("needs_data") val needsData: Boolean = false,
    @SerializedName("dataset_sample_count") val datasetSampleCount: Int = 0,
    @SerializedName("model_count") val modelCount: Int = 0,
)

data class SoftwareListDto(val software: List<SoftwareSummaryDto> = emptyList())

data class DatasetSummaryDto(
    @SerializedName("software_key") val softwareKey: String? = null,
    @SerializedName("sample_count") val sampleCount: Int = 0,
    @SerializedName("class_names") val classNames: List<String> = emptyList(),
    @SerializedName("num_classes") val numClasses: Int = 0,
)

data class DatasetListDto(val datasets: List<DatasetSummaryDto> = emptyList())
