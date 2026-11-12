# Spring AI MCP Enterprise Agent

An enterprise-grade, fault-tolerant, distributed AI Agent architecture built with **Spring Boot 3.5**, **Spring AI 1.1**, **Model Context Protocol (MCP)**, and **Resilience4j**.

This repository demonstrates how to design and deploy an agentic customer support system that safely bridges Large Language Models (LLMs) with internal enterprise backend systems—incorporating **Human-in-the-Loop (HITL) governance**, **stateful multi-turn memory**, and **resilient circuit-breaking** over distributed Server-Sent Events (SSE).

---

## 🎯 Problem Statement & Enterprise Use Case

Deploying autonomous AI agents in enterprise environments presents three major engineering challenges:

1. **Tight Coupling & Security Risks:** Embedding database credentials and direct tool execution logic inside the user-facing LLM application violates separation of concerns and expands the attack surface.
2. **Unchecked Autonomous Actions:** Allowing an LLM to autonomously execute high-impact operations (such as issuing financial refunds or modifying records) without human oversight creates severe financial and compliance risks.
3. **Distributed System Fragility:** In microservice architectures, network latency, transient drops, or downstream tool server restarts can cause cascading failures or unhandled HTTP 500 errors if not properly isolated.

### How This Solution Solves It
* **Decoupled MCP Architecture:** Separates the AI reasoning gateway (`mcp-agent-client`) from the secure tool execution worker (`mcp-database-server`) using the **Model Context Protocol (MCP)** over SSE.
* **Human-in-the-Loop (HITL) Safety Gate:** High-risk financial actions (e.g., `request_refund`) do not execute immediately. Instead, the server intercepts the call, generates a unique `Approval ID`, freezes the transaction in a `PENDING_HUMAN_APPROVAL` state, and requires explicit supervisor authorization via an administrative endpoint before completing the refund.
* **Resilience4j Fault Tolerance:** Wraps LLM and remote MCP tool invocations in **Exponential Backoff Retries** and **Sliding-Window Circuit Breakers**, degrading gracefully with user-friendly messages during downstream outages.
* **Stateful Multi-Turn Memory:** Preserves conversational context across multiple turns per session using Spring AI's `MessageChatMemoryAdvisor`.

---

## 🛠️ Tech Stack

| Category | Technology / Version | Purpose |
| :--- | :--- | :--- |
| **Language & Runtime** | Java 21 | Core runtime environment |
| **Application Framework** | Spring Boot 3.5.15 | Microservice foundation, DI, and embedded Tomcat servers |
| **AI Framework** | Spring AI 1.1.8 | `ChatClient` orchestration, tool callbacks, and memory advisors |
| **Agent Protocol** | Model Context Protocol (MCP) SDK 0.18 | Standardized JSON-RPC over SSE tool discovery and execution |
| **Fault Tolerance** | Resilience4j 2.2.0 | `@Retry` (exponential backoff) and `@CircuitBreaker` AOP protection |
| **LLM Inference** | Groq Cloud (`openai/gpt-oss-20b`) | High-speed LLM reasoning via OpenAI-compatible API |
| **Build Tool** | Apache Maven | Multi-module independent dependency and build management |

---

## 📂 Repository Structure

```text
spring-ai-mcp-enterprise-agent/
├── README.md                        # Project overview, setup, and testing guide
├── ARCHITECTURE.md                  # Deep-dive system topology, sequence flows, and design decisions
├── mcp-agent-client/                # Port 8080: LLM Gateway, ChatMemory, & Resilience4j Client
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/enterprise/ai/mcp/client/
│       │   ├── McpAgentClientApplication.java
│       │   ├── controller/McpAgentClientController.java
│       │   └── service/EnterpriseAgentService.java
│       └── resources/application.properties
└── mcp-database-server/             # Port 8081: Secure MCP Worker Server & HITL Approval Engine
    ├── pom.xml
    └── src/main/
        ├── java/com/enterprise/ai/mcp/server/
        │   ├── McpDatabaseServerApplication.java
        │   ├── controller/RefundApprovalController.java
        │   └── tools/EnterpriseDatabaseTools.java
        └── resources/application.properties
```

---

## ⚙️ Prerequisites & Setup

1. **Java Development Kit (JDK) 21** installed and configured on your `PATH`.
2. **Apache Maven 3.9+** (or use the included `./mvnw` wrapper).
3. **Groq API Key:** Obtain a free or enterprise API key from [Groq Console](https://console.groq.com/).

### Environment Configuration
Export your Groq API key as an environment variable before starting the client:

**Linux / macOS:**
```bash
export GROQ_API_KEY="gsk_your_actual_api_key_here"
```

**Windows (PowerShell):**
```powershell
$env:GROQ_API_KEY="gsk_your_actual_api_key_here"
```

---

## 🚀 Building & Running the System

> **Important Startup Order:** Always start the **MCP Database Server (Port 8081)** first so that the SSE transport endpoint is active when the **MCP Agent Client (Port 8080)** boots up and performs its initial tool-discovery handshake.

### Step 1: Start the MCP Database Server (Port 8081)
Open a terminal in the root directory and run:
```bash
cd mcp-database-server
mvn clean spring-boot:run
```
*Verify that the server starts on port `8081` and registers the MCP SSE endpoints (`/sse` and `/mcp/message`).*

### Step 2: Start the MCP Agent Client (Port 8080)
Open a second terminal in the root directory and run:
```bash
cd mcp-agent-client
mvn clean spring-boot:run
```
*Verify that the client connects to `http://localhost:8081` and discovers the remote MCP tools on startup.*

---

## 🧪 End-to-End Testing Guide

You can test all capabilities of the enterprise agent using `curl` or Postman.

### Scenario 1: Read-Only Tool Execution & Chat Memory
Query order history for an existing customer (`CUST-8849`):
```bash
curl -G "http://localhost:8080/agent/chat" \
  --data-urlencode "q=Get me the order history for customer CUST-8849" \
  --data-urlencode "conversationId=session-001"
```
**Follow-up Turn (Testing Stateful Memory):**
```bash
curl -G "http://localhost:8080/agent/chat" \
  --data-urlencode "q=Which of those orders had the highest amount?" \
  --data-urlencode "conversationId=session-001"
```
*Expected Result:* The agent remembers the context from `session-001` without needing the customer ID repeated.

---

### Scenario 2: Human-in-the-Loop (HITL) Refund Workflow
1. **Initiate Refund Request:**
   ```bash
   curl -G "http://localhost:8080/agent/chat" \
     --data-urlencode "q=Please process a refund for order ORD-1002 for customer CUST-8849 because the item arrived damaged." \
     --data-urlencode "conversationId=session-001"
   ```
   *Expected Result:* The agent intercepts the request and responds with a generated **`Approval ID`** (e.g., `APR-XXXX`), explaining that supervisor approval is required before funds can be released.

2. **Supervisor Approval (Admin Endpoint on Port 8081):**
   Approve the pending request directly on the backend server using the `Approval ID` returned above:
   ```bash
   curl -X POST "http://localhost:8081/admin/refunds/approve/{APPROVAL_ID}"
   ```

3. **Verify Refund Completion via Agent:**
   ```bash
   curl -G "http://localhost:8080/agent/chat" \
     --data-urlencode "q=Can you check the refund status for {APPROVAL_ID}?" \
     --data-urlencode "conversationId=session-001"
   ```
   *Expected Result:* The agent queries `check_refund_status` via MCP and confirms that the refund has been approved and processed.

---

### Scenario 3: Fault Tolerance & Circuit Breaker Verification
1. Stop the `mcp-database-server` process (`Ctrl + C` in Terminal 1).
2. Send a query to the client on port `8080`:
   ```bash
   curl -G "http://localhost:8080/agent/chat" \
     --data-urlencode "q=Get order history for CUST-8849" \
     --data-urlencode "conversationId=session-002"
   ```
3. **Expected Result:** Resilience4j attempts exponential backoff retries, trips the circuit breaker, and invokes `chatFallback(...)`—returning a graceful system notice instead of a raw HTTP 500 stack trace. *(Note: After restarting port `8081`, restart port `8080` so the synchronous SSE transport establishes a fresh session handshake).*

---

## 📖 Further Documentation

For detailed architecture diagrams, sequence flows, Resilience4j threshold configurations, and distributed session lifecycle notes, please refer to **[ARCHITECTURE.md](./ARCHITECTURE.md)**.