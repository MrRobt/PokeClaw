package io.agents.pokeclaw.collect

import io.agents.pokeclaw.cloud.modelhub.ModelHubClient
import io.agents.pokeclaw.utils.XLog

/** Uploads collected samples to the hub, bucketed by software_key. */
class SampleUploader(private val hub: ModelHubClient) {

    fun upload(sample: CollectedSample): Boolean {
        val ack = hub.uploadSample(sample.softwareKey, SamplePayloadBuilder.build(sample))
        XLog.d(TAG, "upload ${sample.softwareKey} step${sample.stepIndex} -> count=${ack?.sampleCount}")
        return ack != null
    }

    fun uploadBatch(softwareKey: String, samples: List<CollectedSample>): Int {
        if (samples.isEmpty()) return 0
        val ack = hub.uploadSamples(softwareKey, samples.map { SamplePayloadBuilder.build(it) })
        return ack?.ingested ?: 0
    }

    companion object {
        private const val TAG = "PokeClaw/SampleUploader"
    }
}
