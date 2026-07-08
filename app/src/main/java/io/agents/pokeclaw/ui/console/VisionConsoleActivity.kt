package io.agents.pokeclaw.ui.console

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.agents.pokeclaw.base.BaseActivity
import io.agents.pokeclaw.vision.VisionConfig

/**
 * Console for the App-side Agent-controls-Claw + cloud-YOLO system. One screen with
 * the required management sections:
 *  1) cloud-phone / Claw instances (list + select)
 *  2) per-software model routing + detection preview + per-app model library
 *  3) auto-explore + YOLO data collection (start/stop) + live event stream
 *  4) datasets / collection records (by software_key)
 *  5) training status + model publish (promote) + rollback
 *  6) trajectory replay
 *
 * Programmatic views (matching OnDeviceConsoleActivity). Reachable via the launcher
 * ("Claw YOLO Console") or `adb am start -n io.agents.pokeclaw/.ui.console.VisionConsoleActivity`.
 */
class VisionConsoleActivity : BaseActivity() {

    private lateinit var vm: VisionConsoleViewModel
    private lateinit var log: TextView
    private lateinit var hubInput: EditText
    private lateinit var pkgInput: EditText
    private lateinit var stepsInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = VisionConsoleViewModel(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(header("PokeClaw · Agent → Claw · Cloud-YOLO Console"))

        hubInput = edit("Model Hub URL (e.g. http://10.0.2.2:8077)", VisionConfig.modelHubBaseUrl())
        root.addView(hubInput)
        root.addView(button("Save Hub URL") {
            vm.setHubUrl(hubInput.text.toString().trim()); appendUi("saved hub url")
        })

        pkgInput = edit("software_key / package (e.g. io.claw.app)", "io.claw.app")
        root.addView(pkgInput)

        root.addView(section("1) Cloud-phone / Claw instances"))
        root.addView(button("Load instances") { vm.loadInstances(::appendUi) })

        root.addView(section("2) Per-software model routing + library"))
        root.addView(button("Resolve model for package") { vm.resolveModel(pkg(), ::appendUi) })
        root.addView(button("Per-software model library") { vm.listSoftware(::appendUi) })

        root.addView(section("3) Auto-explore + collect YOLO data"))
        stepsInput = edit("explore steps", "20").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(stepsInput)
        root.addView(button("Start exploration") { vm.startExplore(pkg(), steps(), ::appendUi) })
        root.addView(button("Stop exploration") { vm.stopExplore(::appendUi) })

        root.addView(section("4) Datasets / collection records"))
        root.addView(button("List datasets (by software_key)") { vm.listDatasets(::appendUi) })

        root.addView(section("5) Training / publish / rollback"))
        root.addView(button("Trigger training (→ candidate)") { vm.triggerTraining(pkg(), ::appendUi) })
        root.addView(button("Promote candidate → active") { vm.promoteCandidate(false, ::appendUi) })
        root.addView(button("Force promote") { vm.promoteCandidate(true, ::appendUi) })
        root.addView(button("Rollback active") { vm.rollback(pkg(), ::appendUi) })

        root.addView(section("6) Trajectory replay"))
        root.addView(button("Show last trajectory") { appendUi(vm.trajectory()) })

        root.addView(section("Log"))
        log = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.DKGRAY)
            text = "ready.\n"
        }
        root.addView(log)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun pkg(): String = pkgInput.text.toString().trim()
    private fun steps(): Int = stepsInput.text.toString().trim().toIntOrNull() ?: 20

    private fun appendUi(s: String) = runOnUiThread { log.append("\n$s\n") }

    // ---- tiny view helpers ------------------------------------------------
    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(12))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun edit(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
