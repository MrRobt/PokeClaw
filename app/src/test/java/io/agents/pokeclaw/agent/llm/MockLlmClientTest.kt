// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import io.agents.pokeclaw.agent.AgentConfig
import io.agents.pokeclaw.agent.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLlmClientTest {

    @Test
    fun factoryCreatesDebugMockClientForMockBaseUrl() {
        val client = LlmClientFactory.create(
            AgentConfig(
                apiKey = "mock-key",
                baseUrl = "mock://llm",
                modelName = "mock-llm",
                provider = LlmProvider.OPENAI,
            )
        )

        assertTrue(client is MockLlmClient)
    }

    @Test
    fun mockClientReturnsDeterministicResponse() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("please reply with only OK")),
            toolSpecs = emptyList(),
        )

        assertEquals("OK", response.text)
        assertEquals(MockLlmClient.MODEL_NAME, response.modelName)
    }

    // --- responseFor branches ---

    @Test
    fun responseForPingReturnsMockPong() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("ping the service")),
            toolSpecs = emptyList(),
        )
        assertEquals("mock-pong", response.text)
    }

    @Test
    fun responseForNoUserMessageReturnsReadyMessage() {
        // UserMessage.from("") / from("   ") throws IllegalArgumentException upstream;
        // a messages list with no UserMessage at all reaches the blank branch.
        val response = MockLlmClient().chat(
            messages = listOf(AiMessage.from("previous assistant turn")),
            toolSpecs = emptyList(),
        )
        assertEquals("Mock response ready.", response.text)
    }

    @Test
    fun responseForArbitraryTextEchoesWithPrefix() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("what is 2+2")),
            toolSpecs = emptyList(),
        )
        assertEquals("Mock response: what is 2+2", response.text)
    }

    @Test
    fun responseForUsesOnlyLatestUserMessage() {
        // First user message "ping" — should be ignored.
        // Latest user message "echo me" — should be the only one used.
        val response = MockLlmClient().chat(
            messages = listOf(
                UserMessage.from("ping"),
                AiMessage.from("pong earlier"),
                UserMessage.from("echo me"),
            ),
            toolSpecs = emptyList(),
        )
        assertEquals("Mock response: echo me", response.text)
    }

    @Test
    fun responseForPingIsCaseInsensitive() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("PING loudly")),
            toolSpecs = emptyList(),
        )
        assertEquals("mock-pong", response.text)
    }

    @Test
    fun responseForOnlyOKIsCaseInsensitive() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("REPLY WITH ONLY OK please")),
            toolSpecs = emptyList(),
        )
        assertEquals("OK", response.text)
    }

    // --- chat method signature ---

    @Test
    fun chatReturnsEmptyToolExecutionRequests() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("hello")),
            toolSpecs = emptyList(),
        )
        assertNotNull(response.toolExecutionRequests)
        assertTrue(response.toolExecutionRequests.isEmpty())
    }

    @Test
    fun chatAlwaysSetsModelName() {
        val response = MockLlmClient().chat(
            messages = listOf(UserMessage.from("hi")),
            toolSpecs = emptyList(),
        )
        assertEquals("mock-llm", response.modelName)
    }

    // --- streaming variant ---

    @Test
    fun chatStreamingEmitsTextAndCompleteToListener() {
        var partial = ""
        var completed: LlmResponse? = null
        val client = MockLlmClient()
        client.chatStreaming(
            messages = listOf(UserMessage.from("please reply with only OK")),
            toolSpecs = emptyList(),
            listener = object : StreamingListener {
                override fun onPartialText(token: String) { partial = token }
                override fun onComplete(response: LlmResponse) { completed = response }
                override fun onError(error: Throwable) { /* not expected */ }
            }
        )
        assertEquals("OK", partial)
        assertNotNull(completed)
        assertEquals("OK", completed!!.text)
        assertEquals("mock-llm", completed!!.modelName)
    }

    @Test
    fun chatStreamingReturnsSameResponse() {
        val client = MockLlmClient()
        val result = client.chatStreaming(
            messages = listOf(UserMessage.from("ping")),
            toolSpecs = emptyList(),
            listener = object : StreamingListener {
                override fun onPartialText(token: String) {}
                override fun onComplete(response: LlmResponse) {}
                override fun onError(error: Throwable) {}
            }
        )
        assertEquals("mock-pong", result.text)
    }

    // --- companion constants ---

    @Test
    fun modelNameConstantIsStable() {
        assertEquals("mock-llm", MockLlmClient.MODEL_NAME)
    }

    @Test
    fun baseUrlPrefixConstantIsStable() {
        assertEquals("mock://", MockLlmClient.BASE_URL_PREFIX)
    }
}
