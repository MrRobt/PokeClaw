# PokeClaw ↔ dyq 三段鉴权契约

**状态**：v1.0 · 2026-07-10 · 取代单一 deviceToken 鉴权

**目的**：在已有 `Authorization: Bearer <deviceToken>` 单段鉴权之上，引入**可选的** `X-Claw-Tenant-Id` / `X-Claw-User-Id` 两段，让多租户/多账号设备能正确归属到 dyq 端的 `claw_device.user_id` / `claw_device.tenant_id`。

---

## 1. HTTP 头定义

| 头 | 必填 | 格式 | 说明 |
|---|---|---|---|
| `Authorization` | ✅（除 `/register` 与 `/token/refresh`） | `Bearer <deviceToken>` | **主鉴权**。deviceToken 是 ClawDeviceJwtUtil 签发的 JWT，三端共享 |
| `X-Claw-Signature` | 仅 `tasks/{uuid}/result` 与 `events` 端点 | 64 字符 hex（HMAC-SHA256） | **签名**。对 `ts + "\n" + nonce + "\n" + path + "\n" + sha256_hex(body)` 计算 |
| `X-Claw-Timestamp` | 仅签名端点 | 毫秒 Unix 时间戳 | 5min 时间窗 |
| `X-Claw-Nonce` | 仅签名端点 | UUID 字符串，每次请求唯一 | Redis SETNX 5min TTL |
| `X-Claw-Tenant-Id` | ⛔ **可选** | 正整数（Long.parseLong ≥ 1） | 设备所属租户 |
| `X-Claw-User-Id` | ⛔ **可选** | 正整数（Long.parseLong ≥ 1） | 设备所属用户 |
| `X-Claw-App-Id` | ⛔ **可选** | 字符串，最大 64 字符 | 应用标识（`io.agents.pokeclaw`） |

---

## 2. 三段组合规则

**核心原则**：向后兼容。**老设备只发 deviceToken 时必须仍能正常工作**，新设备可以附加 tenantId/userId 表达更多上下文。

### 2.1 header 存在性矩阵

| 场景 | Authorization | tenantId | userId | dyq 行为 |
|---|---|---|---|---|
| **A. 老设备** | ✅ | ❌ | ❌ | 走原单段鉴权；`user_id` / `tenant_id` 在 controller / service 层按需从 `claw_device.user_id` 反查 |
| **B. 已登录用户** | ✅ | ✅ | ✅ | 三段都解析；优先用 header 的 user/tenant，**不再**回查 device 表 |
| **C. 匿名设备** | ✅ | ❌ | ✅ | 只解析 userId，tenant 仍走默认（0 或 device.tenant_id） |
| **D. 错误组合** | ❌ | ✅ | ✅ | 拒绝（401001，INVALID_BEARER） |
| **E. 损坏组合** | ✅ | ✅（非正整数） | ✅ | 拒绝（401005，INVALID_TENANT_ID） |

### 2.2 解析规则（dyq Filter 实现侧）

```java
// 伪代码（ClawDeviceSignatureFilter 解析段）
String tenantHeader = request.getHeader("X-Claw-Tenant-Id");
String userHeader = request.getHeader("X-Claw-User-Id");
Long tenantId = parseOptionalPositiveLong(tenantHeader);  // null if absent
Long userId = parseOptionalPositiveLong(userHeader);

// 校验逻辑
if (tenantHeader != null && tenantId == null) {
    writeError(response, 401005, "INVALID_TENANT_ID");
    return;
}
if (userHeader != null && userId == null) {
    writeError(response, 401006, "INVALID_USER_ID");
    return;
}

// 存入 request attribute（供下游 Controller / Service 读）
request.setAttribute("claw.tenantId", tenantId);
request.setAttribute("claw.userId", userId);
```

### 2.3 头缺省行为

- `X-Claw-Tenant-Id` 不存在 → tenantId = null，**不视为错误**
- `X-Claw-User-Id` 不存在 → userId = null，**不视为错误**
- 但 controller 如果标注 `@RequireTenant` 则**必须**有 tenantId，否则 401005

---

## 3. dyq 端 ThreadLocal 注入

### 3.1 ClawTenantContextHolder

```java
package com.douyouqu.dyq.module.claw.service.auth;

public class ClawTenantContextHolder {
    private static final ThreadLocal<ClawTenantContext> CTX = new ThreadLocal<>();
    
    public static void set(ClawTenantContext ctx) { CTX.set(ctx); }
    public static ClawTenantContext get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }  // 必须 finally 清理
    
    public static Long requireTenantId() {
        ClawTenantContext ctx = CTX.get();
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalStateException("tenant context required but absent");
        }
        return ctx.tenantId();
    }
    
    public static Long requireUserId() {
        ClawTenantContext ctx = CTX.get();
        if (ctx == null || ctx.userId() == null) {
            throw new IllegalStateException("user context required but absent");
        }
        return ctx.userId();
    }
}
```

### 3.2 ClawTenantContext 数据类

```java
package com.douyouqu.dyq.module.claw.service.auth;

public record ClawTenantContext(
    Long tenantId,  // nullable
    Long userId,    // nullable
    String deviceId // 来自 JWT，必填
) {}
```

### 3.3 ClawTenantContextInterceptor

```java
@Component
public class ClawTenantContextInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        Long tenantId = (Long) req.getAttribute("claw.tenantId");
        Long userId = (Long) req.getAttribute("claw.userId");
        String deviceId = (String) req.getAttribute("claw.deviceId");
        if (deviceId != null) {
            ClawTenantContextHolder.set(new ClawTenantContext(tenantId, userId, deviceId));
        }
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        ClawTenantContextHolder.clear();
    }
}
```

注册在 `ClawWebMvcConfig`（claw 模块下）的 `InterceptorRegistry.addPathPatterns("/api/claw-device/**", "/claw/app/**")`。

### 3.4 异常错误码（与现有 401xxx 体系一致）

| code | 含义 |
|---|---|
| 401001 | INVALID_SIGNATURE（沿用） |
| 401002 | TIMESTAMP_EXPIRED（沿用） |
| 401003 | NONCE_DUPLICATE（沿用） |
| 401004 | DEVICE_MISMATCH（沿用） |
| **401005** | **INVALID_TENANT_ID**（新增） |
| **401006** | **INVALID_USER_ID**（新增） |

---

## 4. PokeClaw 端发送规则

### 4.1 CloudClientFactory / DeviceTokenInterceptor 改造点

`DeviceTokenInterceptor` 在 `Authorization` 之外按需补 `X-Claw-Tenant-Id` / `X-Claw-User-Id`：

```kotlin
// DeviceTokenInterceptor.kt
override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val builder = request.newBuilder()
    val url = request.url.toString()
    if (shouldAddAuth(url)) {
        val token = tokenStore.snapshot()?.deviceToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
            // 新增：三段鉴权
            tokenStore.snapshot()?.tenantId?.let { tid ->
                if (tid > 0) builder.addHeader("X-Claw-Tenant-Id", tid.toString())
            }
            tokenStore.snapshot()?.userId?.let { uid ->
                if (uid > 0) builder.addHeader("X-Claw-User-Id", uid.toString())
            }
        }
    }
    return chain.proceed(builder.build())
}
```

### 4.2 tokenStore 扩展

`CloudDeviceTokenSnapshot` 增加 `tenantId: Long?` + `userId: Long?` 字段（默认 null，老用户保持兼容）；`KVUtils` 提供 setter/clearer；`VendorBillingActivity` / `SkillMarketActivity` / `LlmConfigActivity` 等用户配置入口在用户**显式登录**后写入。

### 4.3 默认值

- 首次安装：tenantId = null，userId = null → 等同老设备
- 用户首次填写 API key / 登录 dyq 后台：写入 tenant/user
- 用户登出：清空（回到匿名设备状态）

---

## 5. 错误码响应

所有 401xxx 响应统一格式：

```json
{
  "code": 401005,
  "msg": "INVALID_TENANT_ID",
  "data": null
}
```

HTTP status 仍为 401。`HmacAuthException` 新增 factory：

```kotlin
HmacAuthException.forCode(401005) // → HmacAuthException(401005, "INVALID_TENANT_ID")
HmacAuthException.forCode(401006) // → HmacAuthException(401006, "INVALID_USER_ID")
```

---

## 6. 端到端验证（V1.0 验收）

| 用例 | 设备 | tenantId | userId | 期望 |
|---|---|---|---|---|
| 老设备兼容 | 发 Authorization only | — | — | 200 OK，controller 走 device 表反查 |
| 已登录 | 发全三段 | 1 | 100 | 200 OK，user_id=100 写日志/账单 |
| 损坏 tenant | 发 401005 | "abc" | 100 | 401 INVALID_TENANT_ID |
| 损坏 user | 发全三段 | 1 | "-1" | 401 INVALID_USER_ID |
| Bearer 缺 | 任何 | 任何 | 任何 | 401 INVALID_BEARER |
| tenant 缺但 user 有 | 任何 | — | 100 | 200 OK，tenant 走默认 |

---

## 7. 引用

- spec: `api-contracts/device.openapi.yaml` v1.1.0 §4（device 鉴权基线）
- 现有 filter: `dyq-module-claw/.../security/ClawDeviceSignatureFilter.java`
- 现有 client 拦截器: `app/.../cloud/auth/DeviceTokenInterceptor.kt`（含在 `CloudClientFactory.kt`）
- 现有 token store: `app/.../cloud/auth/CloudDeviceTokenStore.kt`
- V1.1.0 增量（待合并）：本契约 §3 全部新增
