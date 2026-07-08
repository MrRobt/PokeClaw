package io.agents.pokeclaw.vision

import io.agents.pokeclaw.utils.KVUtils

/**
 * KV-backed config for the vision / model-hub / collection subsystem. The model
 * hub and cloud-phone base URLs fall back to the shared `cloud_base_url`. Because
 * the collection environment is an internal cloud phone, collection defaults ON.
 */
object VisionConfig {
    private const val KEY_HUB_URL = "yolohub_base_url"
    private const val KEY_HUB_TOKEN = "yolohub_bearer_token"
    private const val KEY_CLOUDPHONE_URL = "cloudphone_base_url"
    private const val KEY_CLOUDPHONE_TOKEN = "cloudphone_bearer_token"
    private const val KEY_CLOUDPHONE_TENANT = "cloudphone_tenant_id"
    private const val KEY_COLLECT_ENABLED = "vision_collection_enabled"
    private const val KEY_UPLOAD_SCREENSHOTS = "vision_upload_screenshots"

    fun modelHubBaseUrl(): String =
        KVUtils.getString(KEY_HUB_URL, "").ifBlank { KVUtils.getString("cloud_base_url", "") }

    fun modelHubToken(): String = KVUtils.getString(KEY_HUB_TOKEN, "")

    fun cloudPhoneBaseUrl(): String =
        KVUtils.getString(KEY_CLOUDPHONE_URL, "").ifBlank { KVUtils.getString("cloud_base_url", "") }

    fun cloudPhoneToken(): String = KVUtils.getString(KEY_CLOUDPHONE_TOKEN, "")
    fun cloudPhoneTenant(): String = KVUtils.getString(KEY_CLOUDPHONE_TENANT, "")

    fun collectionEnabled(): Boolean = KVUtils.getBoolean(KEY_COLLECT_ENABLED, true)
    fun uploadScreenshots(): Boolean = KVUtils.getBoolean(KEY_UPLOAD_SCREENSHOTS, true)

    fun setModelHubBaseUrl(url: String) = KVUtils.putString(KEY_HUB_URL, url)
    fun setCloudPhoneBaseUrl(url: String) = KVUtils.putString(KEY_CLOUDPHONE_URL, url)
    fun setCollectionEnabled(v: Boolean) = KVUtils.putBoolean(KEY_COLLECT_ENABLED, v)

    fun hasHub(): Boolean = modelHubBaseUrl().isNotBlank()
}
