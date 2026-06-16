// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.settings

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.agents.pokeclaw.R
import io.agents.pokeclaw.utils.XLog

/**
 * Lists the 4 [VendorBillingEntry] rows
 * (US-D-031-SETTINGS-BILLING-SECTION). Each row shows the vendor's
 * display name + status label (CONFIGURED / PLACEHOLDER / UNKNOWN).
 * Tapping a row toasts a "go to dyq backend" message — there's no
 * client-side configuration to edit yet.
 */
class VendorBillingActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "VendorBillingActivity"
        private const val TOAST_BACKEND_HINT =
            "请前往 dyq 后台调整对应 vendor 的 credit_cost"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: VendorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor_billing)
        title = getString(R.string.settings_billing_group_title)

        listView = findViewById(R.id.lv_vendor_billing)
        val entries = VendorBillingRegistry.all()
        XLog.i(TAG, "vendor-billing: listed ${entries.size} entries")

        adapter = VendorAdapter(this, entries)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@setOnItemClickListener
            XLog.d(
                TAG,
                "vendor-billing: tap code=${entry.vendorCode} wf=${entry.workflowType} dim=${entry.billingDimension} status=${entry.status.name}",
            )
            Toast.makeText(this, TOAST_BACKEND_HINT, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(VendorBillingRegistry.all())
    }

    private class VendorAdapter(
        activity: Activity,
        initial: List<VendorBillingEntry>,
    ) : ArrayAdapter<VendorBillingEntry>(
        activity,
        android.R.layout.simple_list_item_2,
        android.R.id.text1,
        initial,
    ) {
        fun replaceAll(items: List<VendorBillingEntry>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = getItem(position) ?: return view
            val title = view.findViewById<TextView>(android.R.id.text1)
            val subtitle = view.findViewById<TextView>(android.R.id.text2)
            title.text = item.displayName
            subtitle.text = "${item.statusLabel()}  ·  ${item.vendorCode}/${item.workflowType}/${item.billingDimension}"
            return view
        }
    }
}
