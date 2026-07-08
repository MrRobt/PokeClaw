package io.agents.pokeclaw.device

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Base64
import io.agents.pokeclaw.service.ClawAccessibilityService
import io.agents.pokeclaw.utils.XLog
import java.io.ByteArrayOutputStream

/**
 * Local observer: captures the current screen from the accessibility service —
 * full UI tree (`getScreenTreeFull()`), foreground package (root node), and an
 * optional JPEG screenshot (base64). Runs on the cloud phone itself.
 */
class AccessibilityObserver : DeviceObserver {

    override fun observe(captureScreenshot: Boolean): DeviceObservation {
        val svc = ClawAccessibilityService.getConnectedInstance(2000L)
        if (svc == null) {
            XLog.w(TAG, "no accessibility service connected")
            return DeviceObservation.EMPTY
        }
        val xml = try { svc.getScreenTreeFull() } catch (e: Exception) { XLog.e(TAG, "tree failed", e); null }
        val pkg = try { svc.rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }

        var w = 0
        var h = 0
        var b64: String? = null
        if (captureScreenshot) {
            val bmp = try { svc.takeScreenshot(3000L) } catch (e: Exception) { XLog.e(TAG, "screenshot failed", e); null }
            if (bmp != null) {
                w = bmp.width
                h = bmp.height
                b64 = encode(bmp)
            }
        }
        if (w == 0 || h == 0) {
            val dm = Resources.getSystem().displayMetrics
            w = dm.widthPixels
            h = dm.heightPixels
        }
        return DeviceObservation(pkg = pkg, activity = null, uiXml = xml, screenshotB64 = b64, width = w, height = h)
    }

    private fun encode(bmp: Bitmap): String? = try {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        XLog.e(TAG, "encode failed", e)
        null
    }

    companion object {
        private const val TAG = "PokeClaw/A11yObserver"
    }
}
