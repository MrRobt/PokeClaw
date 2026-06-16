// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.remake

import io.agents.pokeclaw.utils.XLog

/**
 * Source-of-truth abstraction for the remake scene list
 * (US-D-028-REMAKE-SCENE-PICKER).
 *
 * The interface is intentionally tiny so the picker can be unit-tested
 * without depending on Retrofit / OkHttp / JSON parsing. The default
 * implementation [CatalogRemakeSceneFetcher] tries the remote lambda
 * (which will eventually be backed by `GET /aigc/remake/scenes`) and
 * gracefully falls back to the bundle seed [RemakeSceneCatalog] when:
 *   - the remote lambda is null (cloud endpoint not wired yet)
 *   - the remote lambda throws
 *   - the remote returns an empty list
 *
 * The fallback rule matches R5 design rule: "兜底：接口未上线前使用
 * 客户端 hardcode 的 6 场景（与 seed 同源），保持可演示".
 */
fun interface RemakeSceneFetcher {
    fun fetch(): List<RemakeScene>
}

class CatalogRemakeSceneFetcher(
    private val remote: (() -> List<RemakeScene>?)? = null,
    private val fallback: List<RemakeScene> = RemakeSceneCatalog.SCENES,
) : RemakeSceneFetcher {

    private val tag = "CatalogRemakeSceneFetcher"

    override fun fetch(): List<RemakeScene> {
        if (remote == null) {
            XLog.d(tag, "fetch: no remote; serving seed of ${fallback.size} scenes")
            return fallback
        }
        val remoteList = try {
            remote.invoke()
        } catch (t: Throwable) {
            XLog.w(tag, "fetch: remote threw ${t.javaClass.simpleName}; serving seed")
            return fallback
        }
        if (remoteList.isNullOrEmpty()) {
            XLog.d(tag, "fetch: remote returned null/empty; serving seed of ${fallback.size} scenes")
            return fallback
        }
        XLog.d(tag, "fetch: remote returned ${remoteList.size} scenes")
        return remoteList
    }
}
