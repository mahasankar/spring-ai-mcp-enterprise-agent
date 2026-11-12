# System Architecture & Design Specifications

This document outlines the architectural design, communication protocols, fault-tolerance mechanisms, and security patterns implemented in the **Spring AI MCP Enterprise Agent**.

---

## 1. High-Level System Topology

The system follows a distributed, decoupled microservice pattern where AI orchestration is strictly isolated from enterprise data access and sensitive business actions.

```text
+-------------------------------------------------------------------+
|                        End User / API Client                      |
+-------------------------------------------------------------------+
                                  |
                                  | HTTP GET /agent/chat
                                  v
+-------------------------------------------------------------------+
|                   mcp-agent-client (Port 8080)                    |
|                                                                   |
|  +-------------------------------------------------------------+  |
|  |                 McpAgentClientController                    |  |
|  +-------------------------------------------------------------+  |
|                                 |                                 |
|                                 v                                 |
|  +-------------------------------------------------------------+  |
|  |             EnterpriseAgentService (AOP Proxy)              |  |
|  |  [@Retry] -> [@CircuitBreaker] -> [chatFallback]            |  |
|  +-------------------------------------------------------------+  |
|         |                                             |           |
|         v                                             v           |
|  +-----------------------------+       +-----------------------+  |
|  |  MessageChatMemoryAdvisor   |       |  Spring AI ChatClient |  |
|  |  (In-Memory Window Store)   |       |  (Groq LLM Gateway)   |  |
|  +-----------------------------+       +-----------------------+  |
|                                                       |           |
|                                                       v           |
|                                        +-----------------------+  |
|                                        | SyncMcpToolCallback   |  |
|                                        | Provider (MCP Client) |  |
|                                        +-----------------------+  |
+-------------------------------------------------------------------+
                                                        |
                                                        | JSON-RPC over SSE
                                                        | (HTTP Persistent Stream)
                                                        v
+-------------------------------------------------------------------+
|                  mcp-database-server (Port 8081)                  |
|                                                                   |
|  +-------------------------------------------------------------+  |
|  |             WebMvcSseServerTransportProvider                |  |
|  +-------------------------------------------------------------+  |
|                                 |                                 |
|                                 v                                 |
|  +-------------------------------------------------------------+  |
|  |                  EnterpriseDatabaseTools                    |  |
|  |  - search_orders()                                          |  |
|  |  - request_refund()  ---> [Creates PENDING Approval Record] |  |
|  |  - check_refund_status()                                    |  |
|  +-------------------------------------------------------------+  |
|                                 ^                                 |
|                                 | Supervisor Approval             |
|  +-------------------------------------------------------------+  |
|  |            RefundApprovalController (REST Admin)            |  |
|  |            POST /admin/refunds/approve/{approvalId}         |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+
```

---

## 2. Core Design Decisions

### A. Decoupling via Model Context Protocol (MCP)
Rather than compiling database repositories and business tools directly into the LLM client application, the system uses **MCP over Server-Sent Events (SSE)**:
* **Dynamic Tool Discovery:** During startup, `mcp-agent-client` performs an SSE handshake with `mcp-database-server`, dynamically discovering all `@Tool`-annotated methods exposed by `MethodToolCallbackProvider`.
* **Zero-Trust Boundary:** The LLM gateway never holds database credentials or direct write privileges. It can only invoke strictly typed JSON-RPC tool schemas exposed over the MCP transport.

### B. Singleton `ChatClient` Optimization
In `EnterpriseAgentService`, the Spring AI `ChatClient` is constructed once during bean initialization (constructor injection) rather than per HTTP request.
* **Benefit:** Avoids repeated builder allocations, advisor chain assembly, and tool callback binding on every chat turn, ensuring low latency and clean memory management under concurrent load.

### C. Human-in-the-Loop (HITL) Governance Engine
Autonomous agents must not execute irreversible financial mutations without verification. This is enforced at the tool layer inside `EnterpriseDatabaseTools`:
1. When the LLM determines a refund is requested, it invokes `request_refund(customerId, orderId, reason)`.
2. Instead of immediately crediting the account, the server creates a stateful approval ticket stored in a thread-safe `ConcurrentHashMap` with status `PENDING_HUMAN_APPROVAL` and returns a unique `Approval ID`.
3. Only an authorized human supervisor hitting the out-of-band REST endpoint (`POST /admin/refunds/approve/{approvalId}`) can transition the state to `APPROVED`.

### D. Layered Fault Tolerance (Resilience4j)
Distributed tool calls over HTTP/SSE are susceptible to transient network failures or downstream worker restarts. `EnterpriseAgentService.executeChat(...)` is decorated with Spring AOP aspects:
* **Retry Policy (`@Retry`):** Configured for up to `3` attempts with an initial wait of `2s` and an exponential backoff multiplier of `2` (`2s` $\rightarrow$ `4s`).
* **Circuit Breaker (`@CircuitBreaker`):** Uses a `COUNT_BASED` sliding window of `10` calls with a `50%` failure rate threshold. When tripped to `OPEN`, it fails fast for `10s` before allowing `3` probe calls in `HALF_OPEN` state.
* **Graceful Fallback (`chatFallback`):** Intercepts exceptions (such as `ConnectException` or `CallNotPermittedException`) and returns a clean, informative system notice rather than exposing stack traces to end users.

---

## 3. Sequence Flow: HITL Refund Execution

```text
User                 Client (8080)             Groq LLM              MCP Server (8081)         Supervisor
 |                        |                       |                         |                      |
 |-- "Refund ORD-1002" -->|                       |                         |                      |
 |                        |-- Prompt + Tools ---->|                         |                      |
 |                        |<-- ToolCall: refund --|                         |                      |
 |                        |                       |                         |                      |
 |                        |-- MCP JSON-RPC: request_refund() -------------->|                      |
 |                        |                       |                         |-- Generate APR-101   |
 |                        |<-- Return "Pending Approval: APR-101" ----------|                      |
 |                        |                       |                         |                      |
 |                        |-- Tool Result ------->|                         |                      |
 |                        |<-- Formatted Reply ---|                         |                      |
 |<-- "Approval ID:       |                       |                         |                      |
 |     APR-101 needed" ---|                       |                         |                      |
 |                        |                       |                         |                      |
 |                        |                       |                         |<-- POST /approve/ ---|
 |                        |                       |                         |    APR-101           |
 |                        |                       |                         |-- Status = APPROVED  |
```

---

## 4. Operational Notes & Production Considerations

* **Stateful SSE Session Lifecycle:** The standard `WebMvcSseServerTransportProvider` maintains active client session IDs in volatile server memory. If `mcp-database-server` (port `8081`) restarts, its in-memory session registry is cleared, causing subsequent calls from an older client session to receive a `404 Session not found` response until `mcp-agent-client` (port `8080`) restarts and establishes a fresh SSE handshake.
* **Scaling to Multi-Node Production:** For horizontal scaling across multiple Kubernetes pods, replace `InMemoryChatMemoryRepository` with a distributed `ChatMemoryRepository` (e.g., JDBC/PostgreSQL or Redis) and persist HITL approval tickets in an ACID-compliant relational database.