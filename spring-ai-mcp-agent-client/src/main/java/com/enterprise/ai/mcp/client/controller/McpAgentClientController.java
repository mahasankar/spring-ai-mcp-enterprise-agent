package com.enterprise.ai.mcp.client.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enterprise.ai.mcp.client.service.EnterpriseAgentService;

import io.modelcontextprotocol.client.McpSyncClient;

@RestController
public class McpAgentClientController {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(McpAgentClientController.class);

    private final SyncMcpToolCallbackProvider toolProvider;
    private final List<McpSyncClient> mcpClients;
    private final EnterpriseAgentService agentService;

	private ToolCallback[] tools;
    
    public McpAgentClientController(
            SyncMcpToolCallbackProvider toolProvider,
            List<McpSyncClient> mcpClients,
            EnterpriseAgentService agentService) {
        this.toolProvider = toolProvider;
        this.mcpClients = mcpClients;
        this.agentService = agentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void diagnoseMcpConnection() {
    	LOGGER.info("\n===== MCP DIAGNOSTICS =====");
    	LOGGER.info("MCP clients created: " + mcpClients.size());

        for (McpSyncClient client : mcpClients) {
            try {
                var result = client.listTools();
                LOGGER.info("Server tools discovered: " + result.tools().size());
                result.tools().forEach(tool -> LOGGER.info("Tool: " + tool.name()));
            } catch (Exception e) {
                LOGGER.error("MCP listTools() failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        try {
            this.tools = toolProvider.getToolCallbacks();
            LOGGER.info("Spring AI tool callbacks: " + this.tools.length);
        } catch (Exception e) {
        	LOGGER.error("Tool callback registration failed: " + e.getMessage());
            e.printStackTrace();
        }
        LOGGER.info("===== END MCP DIAGNOSTICS =====\n");
    }

    @GetMapping("/agent/chat")
    public String chat(
            @RequestParam("q") String message,
            @RequestParam(value = "conversationId", defaultValue = "default-session") String conversationId) {

    	LOGGER.info("Received message request: [{}] for conversationId: [{}]", message, conversationId);
        return agentService.executeChat(message, conversationId);
    }
}