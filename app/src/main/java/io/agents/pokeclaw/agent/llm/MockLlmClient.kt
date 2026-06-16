// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.agent.tool.ToolSpecification

/**
 * Deterministic debug-only LLM used for emulator smoke tests without a real model or backend.
 */
class MockLlmClient : LlmClient {

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        return LlmResponse(
            text = responseFor(messages),
            toolExecutionRequests = emptyList(),
            modelName = MODEL_NAME
        )
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener
    ): LlmResponse {
        val text = responseFor(messages)
        val response = LlmResponse(
            text = text,
            toolExecutionRequests = emptyList(),
            modelName = MODEL_NAME
        )
        listener.onPartialText(text)
        listener.onComplete(response)
        return response
    }

    private fun responseFor(messages: List<ChatMessage>): String {
        val latestUserText = messages.asReversed()
            .filterIsInstance<UserMessage>()
            .firstOrNull()
            ?.singleText()
            .orEmpty()
            .trim()

        return when {
            latestUserText.contains("only OK", ignoreCase = true) -> "OK"
            latestUserText.contains("ping", ignoreCase = true) -> "mock-pong"
            latestUserText.isBlank() -> "Mock response ready."
            else -> "Mock response: $latestUserText"
        }
    }

    companion object {
        const val MODEL_NAME = "mock-llm"
        const val BASE_URL_PREFIX = "mock://"
    }
}
