// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 skillVersion 缓存 + 漂移检测

package io.agents.pokeclaw.cloud.util

/**
 * 缓存心跳响应中的 skillVersion 字段，检测远端版本号变化（drift）。
 *
 * 漂移（drift）定义：远端版本与上次缓存值不一致（升级或降级都算）。
 * 首次 update 不算漂移（无历史对比基准）。
 *
 * Thread-safe：`update()` 通过 synchronized 保证 read-modify-write 原子性，
 * 避免心跳 RPC 多线程并发触发时的 lost-update 漏报。
 */
class SkillVersionCache {
    private val lock = Any()

    @Volatile private var version: Int? = null

    /** 返回当前缓存的 skillVersion；未初始化时返回 null。 */
    fun current(): Int? = version

    /**
     * 用最新远端版本更新缓存。
     *
     * @param remote 远端心跳响应的 skillVersion
     * @return 是否检测到漂移：仅当存在历史值且与 remote 不等时返回 true；
     *         首次 update 或值未变化时返回 false
     */
    fun update(remote: Int): Boolean = synchronized(lock) {
        val prev = version
        version = remote
        prev != null && prev != remote
    }
}