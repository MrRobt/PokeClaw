package io.agents.pokeclaw.explore

import io.agents.pokeclaw.vision.UiNode
import java.security.MessageDigest

/**
 * Stable structural hash of a screen for dedup. Deliberately ignores volatile
 * detail (dynamic text, exact pixel coords) so the same *page* hashes the same
 * across visits, while a structurally different page hashes differently.
 */
object StateHasher {
    fun hash(pkg: String?, activity: String?, nodes: List<UiNode>): String {
        val sig = StringBuilder()
        sig.append(pkg ?: "").append('|').append(activity ?: "").append("||")
        nodes.asSequence()
            .map { n -> "${n.role}:${n.fineRole}:${n.resourceId ?: ""}:${bucket(n)}" }
            .sorted()
            .forEach { sig.append(it).append(';') }
        return sha256(sig.toString())
    }

    // coarse spatial bucket so small layout shifts don't change the hash
    private fun bucket(n: UiNode): String = "${n.centerX / 80},${n.centerY / 80}"

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .substring(0, 24)
}
