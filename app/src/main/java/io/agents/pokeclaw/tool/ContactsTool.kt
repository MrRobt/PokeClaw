// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.tool

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import io.agents.pokeclaw.utils.XLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contacts 工具：search / add / update。
 *
 * 权限：READ_CONTACTS / WRITE_CONTACTS。
 * 无权限 → 返回 PERMISSION_DENIED。
 *
 * Action 路径：
 *  - search_contact(query)                          → List<Contact>
 *  - add_contact(name, phone, email?)                → contactId
 *  - update_contact(contactId, phone? | email?)      → updated rows
 */
class ContactsTool(private val appContext: Context) : BaseTool() {

    companion object {
        private const val TAG = "PokeClaw/ContactsTool"
        const val TOOL_NAME = "contacts"
    }

    override fun getName(): String = TOOL_NAME

    override fun getDisplayName(): String = "Contacts"

    override fun getDescriptionEN(): String =
        "Search, add, or update contacts. Actions: search_contact | add_contact | update_contact."

    override fun getDescriptionCN(): String =
        "搜索、新增或更新联系人。操作：search_contact（搜索） | add_contact（新增） | update_contact（更新）。"

    override fun getParameters(): List<ToolParameter> = listOf(
        ToolParameter("action", "string", "search_contact | add_contact | update_contact", true),
        ToolParameter("query", "string", "Name or phone keyword (search)", false),
        ToolParameter("name", "string", "Display name (add)", false),
        ToolParameter("phone", "string", "Phone number (add/update)", false),
        ToolParameter("email", "string", "Email address (add/update)", false),
        ToolParameter("contactId", "long", "Contact ID to update (update)", false),
    )

    override fun execute(params: Map<String, Any>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: return ToolResult.error("Missing action")
        XLog.d(TAG, "contacts-tool: action=$action")

        if (!hasReadPermission()) {
            return ToolResult.error("PERMISSION_DENIED: READ_CONTACTS not granted")
        }
        return try {
            when (action) {
                "search_contact" -> {
                    val query = params["query"] as? String ?: return ToolResult.error("Missing query")
                    searchContact(query)
                }
                "add_contact" -> {
                    if (!hasWritePermission()) return ToolResult.error("PERMISSION_DENIED: WRITE_CONTACTS not granted")
                    val name = params["name"] as? String ?: return ToolResult.error("Missing name")
                    val phone = params["phone"] as? String ?: return ToolResult.error("Missing phone")
                    val email = params["email"] as? String
                    addContact(name, phone, email)
                }
                "update_contact" -> {
                    if (!hasWritePermission()) return ToolResult.error("PERMISSION_DENIED: WRITE_CONTACTS not granted")
                    val id = (params["contactId"] as? Number)?.toLong() ?: return ToolResult.error("Missing contactId")
                    val phone = params["phone"] as? String
                    val email = params["email"] as? String
                    if (phone == null && email == null) return ToolResult.error("Must provide phone or email to update")
                    updateContact(id, phone, email)
                }
                else -> ToolResult.error("Unknown action: $action. Allowed: search_contact | add_contact | update_contact")
            }
        } catch (e: SecurityException) {
            XLog.w(TAG, "contacts-tool: 权限被拒", e)
            ToolResult.error("PERMISSION_DENIED: ${e.message}")
        } catch (e: Exception) {
            XLog.e(TAG, "contacts-tool: 执行失败", e)
            ToolResult.error("CONTACTS_ERROR: ${e.message}")
        }
    }

    private fun hasReadPermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWritePermission(): Boolean {
        return appContext.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun searchContact(query: String): ToolResult {
        val resolver: ContentResolver = appContext.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val cursor = resolver.query(uri, projection, selection, selectionArgs, "${ContactsContract.Contacts.DISPLAY_NAME} ASC")
            ?: return ToolResult.error("Cursor null")

        val contacts = JSONArray()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1) ?: ""
                contacts.put(JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("hasPhone", it.getInt(2) > 0)
                })
            }
        }
        val summary = "Found ${contacts.length()} contacts matching '$query'"
        XLog.i(TAG, "contacts-tool: search query=$query count=${contacts.length()}")
        val payload = JSONObject().apply {
            put("contacts", contacts)
            put("summary", summary)
        }
        return ToolResult.success(payload.toString())
    }

    private fun addContact(name: String, phone: String, email: String?): ToolResult {
        val resolver = appContext.contentResolver

        // 插入空 contact → 拿到 rawContactId
        val rawContactValues = android.content.ContentValues().apply {
            put(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.local")
            put(ContactsContract.RawContacts.ACCOUNT_NAME, "PokeClaw")
        }
        val rawContactUri = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, rawContactValues)
            ?: return ToolResult.error("Insert rawContact returned null")
        val rawContactId = rawContactUri.lastPathSegment?.toLongOrNull() ?: -1L

        // 插入姓名
        val nameValues = android.content.ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        }
        resolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        // 插入电话
        val phoneValues = android.content.ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        }
        resolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)

        // 插入邮箱（如果提供）
        if (!email.isNullOrEmpty()) {
            val emailValues = android.content.ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, emailValues)
        }

        XLog.i(TAG, "contacts-tool: add_contact rawContactId=$rawContactId name=$name")
        val payload = JSONObject().apply {
            put("rawContactId", rawContactId)
            put("name", name)
        }
        return ToolResult.success(payload.toString())
    }

    private fun updateContact(rawContactId: Long, phone: String?, email: String?): ToolResult {
        val resolver = appContext.contentResolver
        var updated = 0

        if (phone != null) {
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, values)
            updated++
        }

        if (email != null) {
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
            }
            resolver.insert(ContactsContract.Data.CONTENT_URI, values)
            updated++
        }

        XLog.i(TAG, "contacts-tool: update_contact rawContactId=$rawContactId fields=$updated")
        val payload = JSONObject().apply {
            put("rawContactId", rawContactId)
            put("updatedFields", updated)
        }
        return ToolResult.success(payload.toString())
    }
}
