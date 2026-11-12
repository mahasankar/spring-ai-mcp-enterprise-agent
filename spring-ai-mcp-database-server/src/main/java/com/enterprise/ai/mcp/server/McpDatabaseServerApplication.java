
package com.enterprise.ai.mcp.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.enterprise.ai.mcp.server.tools.EnterpriseDatabaseTools;

@SpringBootApplication
public class McpDatabaseServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpDatabaseServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider enterpriseDatabaseToolCallbackProvider(
            EnterpriseDatabaseTools databaseTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(databaseTools)
                .build();
    }
}
