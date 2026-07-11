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
import androidx.lifecycle.lifecycleScope
import io.agents.pokeclaw.ClawApplication
import io.agents.pokeclaw.R
import io.agents.pokeclaw.cloud.CloudClientFactory
import io.agents.pokeclaw.cloud.lobster.client.BillingPricingClient
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lists the 4 [VendorBillingEntry] rows
 * (US-D-031-SETTINGS-BILLING-SECTION). Each row shows the vendor's
 * display name + status label (CONFIGURED / PLACEHOLDER / UNKNOWN).
 * Tapping a row toasts a "go to dyq backend" message — there's no
 * client-side configuration to edit yet.
 *
 * V1.0 改造：
 *  - onCreate 启动时通过 [BillingPricingClient] 拉取 dyq 真实定价
 *  - 拉取成功 → [VendorBillingRegistry.loadFromResp] 覆盖 SEED
 *  - 拉取失败/网络异常 → 回退 SEED + toast 提示
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
        adapter = VendorAdapter(this, VendorBillingRegistry.all())
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@setOnItemClickListener
            XLog.d(
                TAG,
                "vendor-billing: tap code=${entry.vendorCode} wf=${entry.workflowType} dim=${entry.billingDimension} status=${entry.status.name}",
            )
            Toast.makeText(this, TOAST_BACKEND_HINT, Toast.LENGTH_LONG).show()
        }

        // V1.0：从 dyq 拉取真实定价
        loadFromCloud()
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(VendorBillingRegistry.all())
    }

    private fun loadFromCloud() {
        val baseUrl = KVUtils.getDefaultCloudBaseUrl().takeIf { it.isNotBlank() }
        if (baseUrl == null) {
            XLog.d(TAG, "loadFromCloud: baseUrl 未配置，使用 SEED 兜底")
            return
        }
        val tokenStore = io.agents.pokeclaw.cloud.auth.AndroidKeystoreCloudDeviceTokenStore(applicationContext)
        val client = try {
            BillingPricingClient(CloudClientFactory.buildLobsterBillingApi(baseUrl, tokenStore))
        } catch (e: Exception) {
            XLog.w(TAG, "loadFromCloud: init failed, fall back to SEED", e)
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    client.listPricing()
                } catch (e: Exception) {
                    XLog.e(TAG, "loadFromCloud: network exception, fall back to SEED", e)
                    null
                }
            }
            when (result) {
                is BillingPricingClient.Result.OkList -> {
                    XLog.i(TAG, "loadFromCloud: ok ${result.entries.size} entries")
                    VendorBillingRegistry.loadFromResp(result.entries)
                    adapter.replaceAll(VendorBillingRegistry.all())
                }
                is BillingPricingClient.Result.Rejected -> {
                    XLog.w(TAG, "loadFromCloud: rejected=${result.message}, fall back to SEED")
                    Toast.makeText(
                        this@VendorBillingActivity,
                        "价格数据离线，使用本地默认",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                null -> {
                    Toast.makeText(
                        this@VendorBillingActivity,
                        "价格数据离线，使用本地默认",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
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
