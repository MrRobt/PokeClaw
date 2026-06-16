// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// TaskParser 单测 — Tier 1 正则路由：call/sms/alarm/timer/screenshot/back/home/url/settings/open_app
// 注意：依赖 Android Intent/Uri/AlarmClock，通过 testOptions.isReturnDefaultValues=true 让 JVM 测试通过。

package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskParserTest {

    // ── 已有测试 (保留) ──

    @Test
    fun `send message command routes to direct send message tool`() {
        val parsed = TaskParser.parse("send hi to Girlfriend on WhatsApp")
        assertNotNull(parsed)
        assertEquals("send_message", parsed!!.action)
        assertEquals("send_message", parsed.toolName)
        assertEquals("hi", parsed.toolParams!!["message"])
        assertEquals("Girlfriend", parsed.toolParams!!["contact"])
        assertEquals("WhatsApp", parsed.toolParams!!["app"])
    }

    @Test
    fun `send contextual message still falls through to agent`() {
        assertNull(TaskParser.parse("send that to Girlfriend on WhatsApp"))
    }

    @Test
    fun `email commands do not route to messaging app tool`() {
        assertNull(TaskParser.parse("send email to nicole@example.com"))
    }

    // ── parse 入口 ──

    @Test
    fun `parse - 不匹配任何 pattern 返回 null`() {
        assertNull(TaskParser.parse("draft an email about quarterly results"))
    }

    @Test
    fun `parse - 空字符串返回 null`() {
        assertNull(TaskParser.parse(""))
    }

    @Test
    fun `parse - 仅空白返回 null`() {
        assertNull(TaskParser.parse("   \t  "))
    }

    // ── matchCall ──

    @Test
    fun `call 含电话号码 返回 call action`() {
        val r = TaskParser.parse("call 13800138000")
        assertNotNull(r)
        assertEquals("call", r!!.action)
        assertNotNull("应包含 intent", r.intent)
        assertTrue("description 应提到 Dialing", r.description.contains("Dialing"))
    }

    @Test
    fun `call 带国际格式号码 (含 +)`() {
        val r = TaskParser.parse("call +1 555 123 4567")
        assertNotNull(r)
        assertEquals("call", r!!.action)
    }

    @Test
    fun `call 带连字符号码 应被 sanitize`() {
        val r = TaskParser.parse("call 555-123-4567")
        assertNotNull(r)
        assertEquals("call", r!!.action)
    }

    @Test
    fun `call 联系人名字 返回 null (Tier 1 fallthrough)`() {
        assertNull(TaskParser.parse("call mom"))
    }

    @Test
    fun `call 短数字 7位以下 不视为电话 返回 null`() {
        // [\d\s\-+()]{7,} 要求至少 7 个数字字符
        assertNull(TaskParser.parse("call 12345"))
    }

    @Test
    fun `call 中文 keyword 打電話 也匹配`() {
        val r = TaskParser.parse("打電話 13800138000")
        assertNotNull(r)
        assertEquals("call", r!!.action)
    }

    // ── matchSendMessage 扩展 ──

    @Test
    fun `send message 给联系人 默认走 WhatsApp`() {
        val r = TaskParser.parse("send hello world to John")
        assertNotNull(r)
        assertEquals("send_message", r!!.action)
        assertEquals("send_message", r.toolName)
        assertEquals("John", r.toolParams!!["contact"])
        assertEquals("hello world", r.toolParams!!["message"])
        assertEquals("WhatsApp", r.toolParams!!["app"])
    }

    @Test
    fun `send message 指定 telegram`() {
        val r = TaskParser.parse("send ping to Alice on telegram")
        assertNotNull(r)
        assertEquals("Telegram", r!!.toolParams!!["app"])
    }

    @Test
    fun `send message 指定 tg 规范化到 Telegram`() {
        val r = TaskParser.parse("send hi to bob on tg")
        assertNotNull(r)
        assertEquals("Telegram", r!!.toolParams!!["app"])
    }

    @Test
    fun `send message 指定 wa 规范化到 WhatsApp`() {
        val r = TaskParser.parse("send hi to bob on wa")
        assertNotNull(r)
        assertEquals("WhatsApp", r!!.toolParams!!["app"])
    }

    @Test
    fun `send message 指定 sms 规范化到 Messages`() {
        val r = TaskParser.parse("send hi to bob on sms")
        assertNotNull(r)
        assertEquals("Messages", r!!.toolParams!!["app"])
    }

    @Test
    fun `send message 联系人为邮箱地址 返回 null`() {
        assertNull(TaskParser.parse("send hi to [email protected]"))
    }

    @Test
    fun `send message 内容包含上下文代词 (this it) 返回 null`() {
        assertNull(TaskParser.parse("send this to bob"))
        assertNull(TaskParser.parse("send it to bob"))
    }

    @Test
    fun `send message message 含双引号应被 trim`() {
        val r = TaskParser.parse("""send "hello world" to Bob""")
        assertNotNull(r)
        assertEquals("hello world", r!!.toolParams!!["message"])
    }

    @Test
    fun `send message 联系人含双引号应被 trim`() {
        val r = TaskParser.parse("""send hi to "John Doe" """)
        assertNotNull(r)
        assertEquals("John Doe", r!!.toolParams!!["contact"])
    }

    // ── matchSms ──

    @Test
    fun `sms 含电话号码 返回 sms action`() {
        val r = TaskParser.parse("sms 13800138000 hello")
        assertNotNull(r)
        assertEquals("sms", r!!.action)
    }

    @Test
    fun `sms text 关键字 含电话号码`() {
        val r = TaskParser.parse("text 5551234567 hi there")
        assertNotNull(r)
        assertEquals("sms", r!!.action)
    }

    @Test
    fun `sms 联系人名而非电话 返回 null`() {
        assertNull(TaskParser.parse("sms John hello"))
    }

    // ── matchAlarm ──

    @Test
    fun `alarm 24 小时制 7 30`() {
        val r = TaskParser.parse("set alarm at 7:30")
        assertNotNull(r)
        assertEquals("alarm", r!!.action)
        assertTrue("description 应含 07:30", r.description.contains("07:30"))
    }

    @Test
    fun `alarm 24 小时制 23 45`() {
        val r = TaskParser.parse("set alarm at 23:45")
        assertNotNull(r)
        assertEquals("alarm", r!!.action)
        assertTrue("description 应含 23:45", r.description.contains("23:45"))
    }

    @Test
    fun `alarm am 12 应转为 0 点`() {
        val r = TaskParser.parse("set alarm at 12:00 am")
        assertNotNull(r)
        assertTrue("12am 应显示为 00:00", r!!.description.contains("00:00"))
    }

    @Test
    fun `alarm pm 12 应保持 12 点`() {
        val r = TaskParser.parse("set alarm at 12:00 pm")
        assertNotNull(r)
        assertTrue("12pm 应保持 12:00", r!!.description.contains("12:00"))
    }

    @Test
    fun `alarm pm 1 应转为 13 点`() {
        val r = TaskParser.parse("set alarm at 1:00 pm")
        assertNotNull(r)
        assertTrue("1pm 应显示为 13:00", r!!.description.contains("13:00"))
    }

    @Test
    fun `alarm wake me up at 6 30 am`() {
        val r = TaskParser.parse("wake me up at 6:30 am")
        assertNotNull(r)
        assertEquals("alarm", r!!.action)
    }

    @Test
    fun `alarm 中文 鬧鐘 8 00`() {
        val r = TaskParser.parse("設定鬧鐘 8:00")
        assertNotNull(r)
        assertEquals("alarm", r!!.action)
    }

    // ── matchTimer ──

    @Test
    fun `timer 5 minute 单位换算`() {
        val r = TaskParser.parse("set timer for 5 minutes")
        assertNotNull(r)
        assertEquals("timer", r!!.action)
    }

    @Test
    fun `timer 30 sec 单位换算`() {
        val r = TaskParser.parse("set timer for 30 sec")
        assertNotNull(r)
        assertEquals("timer", r!!.action)
    }

    @Test
    fun `timer 1 hour 单位换算`() {
        val r = TaskParser.parse("set timer for 1 hour")
        assertNotNull(r)
        assertEquals("timer", r!!.action)
    }

    @Test
    fun `timer countdown keyword`() {
        val r = TaskParser.parse("countdown 10 minutes")
        assertNotNull(r)
        assertEquals("timer", r!!.action)
    }

    @Test
    fun `timer 中文 計時 不支持分鐘单位 - 返回 null (Tier 1 fallthrough)`() {
        // 业务现状：TIMER_PATTERN 的 unit 只支持 second/sec/minute/min/hour/hr/s/m/h
        // 不支持"分鐘"。"計時 1 分鐘"也不匹配 matchOpenApp (regex 要求 \s+ 在"打開"后)，
        // 所以返回 null → 走 Tier 2/3 agent loop。
        assertNull(TaskParser.parse("計時 1 分鐘"))
    }

    // ── matchScreenshot ──

    @Test
    fun `screenshot take a screenshot`() {
        val r = TaskParser.parse("take a screenshot")
        assertNotNull(r)
        assertEquals("screenshot", r!!.action)
        assertEquals("take_screenshot", r.toolName)
        assertNotNull(r.toolParams)
        assertTrue(r.toolParams!!.isEmpty())
    }

    @Test
    fun `screenshot screencap 也匹配`() {
        val r = TaskParser.parse("screencap the screen")
        assertNotNull(r)
        assertEquals("screenshot", r!!.action)
    }

    @Test
    fun `screenshot 中文 截圖 也匹配`() {
        val r = TaskParser.parse("幫我截圖")
        assertNotNull(r)
        assertEquals("screenshot", r!!.action)
    }

    @Test
    fun `screenshot 影相 也匹配`() {
        val r = TaskParser.parse("影相啦")
        assertNotNull(r)
        assertEquals("screenshot", r!!.action)
    }

    // ── matchBackHome ──

    @Test
    fun `back 返回 back action`() {
        val r = TaskParser.parse("back")
        assertNotNull(r)
        assertEquals("back", r!!.action)
        assertEquals("system_key", r.toolName)
        assertEquals("back", r.toolParams!!["key"])
    }

    @Test
    fun `go back 返回 back action`() {
        val r = TaskParser.parse("go back")
        assertNotNull(r)
        assertEquals("back", r!!.action)
    }

    @Test
    fun `home 返回 home action`() {
        val r = TaskParser.parse("home")
        assertNotNull(r)
        assertEquals("home", r!!.action)
        assertEquals("home", r.toolParams!!["key"])
    }

    @Test
    fun `返回主頁 中文 返回 home action`() {
        val r = TaskParser.parse("返回主頁")
        assertNotNull(r)
        assertEquals("home", r!!.action)
    }

    @Test
    fun `返回 短中文 返回 back action`() {
        val r = TaskParser.parse("返回")
        assertNotNull(r)
        assertEquals("back", r!!.action)
    }

    @Test
    fun `back home 大小写不敏感`() {
        val r1 = TaskParser.parse("BACK")
        val r2 = TaskParser.parse("Home")
        assertEquals("back", r1!!.action)
        assertEquals("home", r2!!.action)
    }

    // ── matchOpenUrl ──

    @Test
    fun `open url https 返回 open_url`() {
        val r = TaskParser.parse("open https://example.com")
        assertNotNull(r)
        assertEquals("open_url", r!!.action)
        assertTrue("description 应含 URL", r.description.contains("https://example.com"))
    }

    @Test
    fun `go to url 也匹配`() {
        val r = TaskParser.parse("go to https://google.com")
        assertNotNull(r)
        assertEquals("open_url", r!!.action)
    }

    @Test
    fun `visit url 也匹配`() {
        val r = TaskParser.parse("visit https://github.com")
        assertNotNull(r)
        assertEquals("open_url", r!!.action)
    }

    @Test
    fun `navigate to url 也匹配`() {
        val r = TaskParser.parse("navigate to https://kotlinlang.org")
        assertNotNull(r)
        assertEquals("open_url", r!!.action)
    }

    @Test
    fun `打開 中文 url 也匹配`() {
        val r = TaskParser.parse("打開 https://example.com")
        assertNotNull(r)
        assertEquals("open_url", r!!.action)
    }

    @Test
    fun `非 http 协议 URL 走 open_app fallthrough (Tier 1 业务现状)`() {
        // 业务现状：URL_PATTERN 要求 http(s):// 开头；ftp:// 不匹配 open_url
        // 但 matchOpenApp 的 OPEN_APP_PATTERN 会接住"open ftp://example.com"
        // 把 ftp://example.com 当作 app_name
        val r = TaskParser.parse("open ftp://example.com")
        assertNotNull(r)
        assertEquals("open_app", r!!.action)
        assertTrue("app_name 应包含 ftp://example.com", (r.toolParams!!["app_name"] as String).contains("ftp://example.com"))
    }

    // ── matchOpenSettings ──

    @Test
    fun `open settings wifi 返回 open_settings`() {
        val r = TaskParser.parse("open wifi settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
        assertTrue("description 应提到 wifi", r.description.contains("wifi"))
    }

    @Test
    fun `open settings bluetooth 返回 open_settings`() {
        val r = TaskParser.parse("open bluetooth settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
        assertTrue(r!!.description.contains("bluetooth"))
    }

    @Test
    fun `open settings brightness 返回 open_settings`() {
        val r = TaskParser.parse("open brightness settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
    }

    @Test
    fun `open settings battery 返回 open_settings`() {
        val r = TaskParser.parse("open battery settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
    }

    @Test
    fun `open settings location 返回 open_settings`() {
        val r = TaskParser.parse("open location settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
    }

    @Test
    fun `open settings airplane 返回 open_settings`() {
        val r = TaskParser.parse("open airplane settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
    }

    @Test
    fun `open settings 通用 返回 open_settings`() {
        val r = TaskParser.parse("open settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
        assertTrue("description 应为 Opening Settings", r.description.contains("Opening Settings"))
    }

    @Test
    fun `go to settings 通用 也匹配`() {
        val r = TaskParser.parse("go to settings")
        assertNotNull(r)
        assertEquals("open_settings", r!!.action)
    }

    @Test
    fun `open settings 中文 設定 不匹配 - 返回 null (Tier 1 fallthrough)`() {
        // 业务现状：matchOpenSettings 的 regex `.*(?:open|go to|打開)\s*(?:the\s*)?settings.*`
        // 要求 "settings" 关键词，不支持纯"設定"。matchOpenApp 也不匹配（需\s+）。
        // 返回 null → 走 Tier 2/3 agent loop。
        assertNull(TaskParser.parse("打開設定"))
    }

    @Test
    fun `settings 但没有 settings 关键字 严格不应匹配`() {
        assertNull(TaskParser.parse("brightness please"))
    }

    // ── matchOpenApp ──

    @Test
    fun `open app name 返回 open_app (lowercase app_name)`() {
        // matchOpenApp 用 lower 后匹配，app_name 也是小写
        val r = TaskParser.parse("open YouTube")
        assertNotNull(r)
        assertEquals("open_app", r!!.action)
        assertEquals("open_app", r.toolName)
        assertEquals("youtube", r.toolParams!!["app_name"])
    }

    @Test
    fun `launch app name 返回 open_app`() {
        val r = TaskParser.parse("launch Chrome")
        assertNotNull(r)
        assertEquals("open_app", r!!.action)
    }

    @Test
    fun `start app name 返回 open_app`() {
        val r = TaskParser.parse("start Telegram")
        assertNotNull(r)
        assertEquals("open_app", r!!.action)
    }

    @Test
    fun `打開 中文 app 也匹配`() {
        val r = TaskParser.parse("打開 Gmail")
        assertNotNull(r)
        assertEquals("open_app", r!!.action)
    }

    // ── ParseResult 数据类 ──

    @Test
    fun `ParseResult description 默认空字符串`() {
        // 构造一个空 description 的 ParseResult
        val r = TaskParser.ParseResult(action = "x", intent = null, description = "")
        assertEquals("", r.description)
        assertEquals("x", r.action)
        assertNull(r.intent)
        assertNull(r.toolName)
        assertNull(r.toolParams)
    }
}