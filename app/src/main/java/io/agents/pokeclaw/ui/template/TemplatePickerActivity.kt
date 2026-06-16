// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * UI to browse AIGC templates and filter by source channel
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER). The chip group at the
 * top lets the user pick a [TemplateChannel]; the [ListView]
 * shows the matching subset. Cloud sync is owned by the dyq
 * backend — until that wire is up we fall back to
 * [CatalogAigcTemplateFetcher] / [AigcTemplateCatalog].
 *
 * Tapping a row currently toasts a placeholder message; the
 * [ParamsFormRenderer] integration (US-D-029) is the next step.
 */
class TemplatePickerActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "TemplatePickerActivity"
    }

    private lateinit var chipGroup: ChipGroup
    private lateinit var listView: ListView
    private lateinit var adapter: TemplateAdapter

    private val fetcher = CatalogAigcTemplateFetcher()
    private var currentChannel: TemplateChannel = TemplateChannel.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_template_picker)
        title = getString(R.string.template_picker_title)

        chipGroup = findViewById(R.id.cg_template_channels)
        listView = findViewById(R.id.lv_templates)
        adapter = TemplateAdapter(this, emptyList())
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            XLog.i(
                TAG, "template: tapped id=${item.id} channel=${item.channelCode ?: "<null>"}",
            )
            Toast.makeText(
                this,
                "已选模板: ${item.name}（表单渲染占位）",
                Toast.LENGTH_SHORT,
            ).show()
        }

        buildChips()
        applyChannel(TemplateChannel.ALL)
    }

    private fun buildChips() {
        chipGroup.removeAllViews()
        // ALL first, then specific channels, then UNKNOWN last.
        val ordered = listOf(
            TemplateChannel.ALL,
            TemplateChannel.PIXVERSE,
            TemplateChannel.COMFYUI,
            TemplateChannel.MUXI_CANVAS,
            TemplateChannel.UNKNOWN,
        )
        for (ch in ordered) {
            val chip = Chip(this).apply {
                text = ch.displayLabel
                isCheckable = true
                isCheckedIconVisible = true
                tag = ch
                isChecked = (ch == TemplateChannel.ALL)
            }
            chip.setOnClickListener { _ ->
                if (chip.isChecked) {
                    applyChannel(ch)
                } else {
                    // Re-select because we always want exactly one chip
                    // checked — radio-style behavior.
                    chip.isChecked = true
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun applyChannel(channel: TemplateChannel) {
        currentChannel = channel
        // Ensure the correct chip is selected.
        for (i in 0 until chipGroup.childCount) {
            val c = chipGroup.getChildAt(i) as? Chip ?: continue
            c.isChecked = (c.tag == channel)
        }
        val all: List<AigcTemplate> = fetcher.fetch()
        val shown = TemplateChannelFilter.filter(all, channel)
        XLog.d(
            TAG,
            "template-filter: channel=${channel.name} showing=${shown.size}",
        )
        adapter.replaceAll(shown)
    }

    /** Visible for the [TemplateAdapter]. */
    internal fun currentChannel(): TemplateChannel = currentChannel

    private class TemplateAdapter(
        activity: Activity,
        initial: List<AigcTemplate>,
    ) : ArrayAdapter<AigcTemplate>(
        activity,
        android.R.layout.simple_list_item_2,
        android.R.id.text1,
        initial,
    ) {
        fun replaceAll(items: List<AigcTemplate>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = getItem(position) ?: return view
            val title = view.findViewById<TextView>(android.R.id.text1)
            val subtitle = view.findViewById<TextView>(android.R.id.text2)
            title.text = item.name
            subtitle.text = "channel: ${item.channelCode ?: "<unknown>"} · id: ${item.id}"
            return view
        }
    }
}
