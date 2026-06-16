// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * AIGC template row used by the picker / renderer
 * (US-D-030-CHANNEL-CODE-TEMPLATE-FILTER). Lightweight DTO; the
 * full AIGC cloud row may carry more fields, but the channel
 * filter only needs the channel discriminator + a name/ID.
 */
data class AigcTemplate(
    val id: String,
    val name: String,
    val channelCode: String?,
)
