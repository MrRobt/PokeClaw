// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// US-D-037 心跳 skillVersion 缓存 + 漂移检测

package io.agents.pokeclaw.cloud.util

class SkillVersionCache {
    @Volatile private var version: Int? = null

    fun current(): Int? = version

    fun update(remote: Int): Boolean {
        val prev = version
        version = remote
        return prev != null && prev != remote
    }
}