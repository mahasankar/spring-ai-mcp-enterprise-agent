package com.enterprise.ai.mcp.client.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import org.springframework.stereotype.Service;

@Service
public class EnterpriseAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnterpriseAgentService.class);

    private final ChatClient chatClient;

    public EnterpriseAgentService(ChatModel chatModel, SyncMcpToolCallbackProvider toolProvider, ChatMemory chatMemory) {
    	ToolCallback[] tools = toolProvider.getToolCallbacks();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(tools)
                .defaultSystem(
                        "You are an enterprise support agent. "
                        + "Use available MCP tools to retrieve customer order history or process refunds.")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * Executes the chat call wrapped with Resilience4j Retry, Circuit Breaker, and Fallback.
     */
    @Retry(name = "enterpriseAgentService", fallbackMethod = "chatFallback")
    @CircuitBreaker(name = "enterpriseAgentService", fallbackMethod = "chatFallback")
    public String executeChat(String message, String conversationId) {
        LOGGER.info("Executing chat call via LLM & MCP Tools for conversationId: [{}]", conversationId);

        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * Graceful fallback method triggered if retries are exhausted or the circuit breaker trips.
     * Note: Must have the exact same parameters plus a Throwable as the final argument.
     */
    public String chatFallback(String message, String conversationId, Throwable ex) {
        LOGGER.error("Resilience4j Fallback activated due to error: {}", ex.getMessage());
        return "**System Notice:** We are currently experiencing temporary connectivity issues with our enterprise database or AI gateway. " +
               "Your request could not be completed right now. Please try again shortly. (Error: " + ex.getMessage() + ")";
    }
}