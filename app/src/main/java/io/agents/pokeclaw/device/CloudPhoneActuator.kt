package io.agents.pokeclaw.device

import android.view.KeyEvent
import io.agents.pokeclaw.cloud.cloudphone.CloudPhoneClient
import io.agents.pokeclaw.cloud.cloudphone.model.ControlRespDto

/**
 * Remote actuator: drives a cloud-phone instance through the dyq cloudphone
 * `/control` API (InstanceControlReqVO). Use when the Agent runs off-device and
 * controls a selected cloud-phone instance rather than the local screen.
 */
class CloudPhoneActuator(
    private val client: CloudPhoneClient,
    private val instanceId: Long,
) : DeviceActuator {

    private fun res(r: ControlRespDto?): String = if (r?.success == true) "ok" else "error"

    override fun tap(x: Int, y: Int) = res(client.tap(instanceId, x, y))

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) =
        res(client.swipe(instanceId, x1, y1, x2, y2, durationMs.toInt()))

    override fun input(text: String) = res(client.input(instanceId, text))

    override fun back() = res(client.keyEvent(instanceId, KeyEvent.KEYCODE_BACK))

    override fun home() = res(client.keyEvent(instanceId, KeyEvent.KEYCODE_HOME))

    // remote app launch goes through the cloudphone AppMarket install/start API, not /control
    override fun launch(pkg: String) = "no_change"
}
