package com.enterprise.ai.mcp.server.tools;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseDatabaseTools {

    // Simulated secure in-memory database of pending approvals
    private final Map<String, RefundRequest> pendingApprovals = new ConcurrentHashMap<>();

    public record RefundRequest(String orderId, double amount, String reason, String status) {}

    @Tool(name = "search_orders", description = "Fetch customer order history and account status using a unique customer ID.")
    public String fetchCustomerOrderHistory(String customerId) {
        if ("CUST-8849".equalsIgnoreCase(customerId)) {
            return "Customer: John Doe | Status: ACTIVE | Last Order: #ORD-5541 | Item: Enterprise Cloud Cluster | Price: $150 | Delivery Status: Delivered (Damaged reported)";
        }
        return "Customer ID " + customerId + " not found in the enterprise repository.";
    }

    @Tool(name = "request_refund", description = "Request an account refund for a specific order. Note: High-value actions require human approval.")
    public String requestAccountRefund(String orderId, double amount, String reason) {
        String approvalId = "APP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        
        RefundRequest request = new RefundRequest(orderId, amount, reason, "PENDING_HUMAN_APPROVAL");
        pendingApprovals.put(approvalId, request);

        return String.format("REFUND_PENDING_APPROVAL [Approval ID: %s]: Request for order %s in the amount of $%.2f has been logged. Reason: '%s'. Action is frozen pending explicit human supervisor authorization.", 
                approvalId, orderId, amount, reason);
    }
    
    @Tool(name = "check_refund_status", description = "Check the current approval and execution status of a refund request using its Approval ID.")
    public String checkRefundStatus(String approvalId) {
        RefundRequest request = pendingApprovals.get(approvalId.trim());
        if (request == null) {
            return "Error: Approval ID " + approvalId + " not found.";
        }
        return String.format("Refund Request [%s] for Order %s ($%.2f). Status: %s. Reason: %s", 
                approvalId, request.orderId(), request.amount(), request.status(), request.reason());
    }

    // Method for human supervisor to approve the action
    public String executeApprovedRefund(String approvalId) {
        RefundRequest request = pendingApprovals.get(approvalId);
        if (request == null) {
            return "Error: Approval ID " + approvalId + " not found.";
        }
        
        // Mark as approved / execute database credit
        pendingApprovals.put(approvalId, new RefundRequest(request.orderId(), request.amount(), request.reason(), "APPROVED_AND_EXECUTED"));
        
        return String.format("SUCCESS: Refund for order %s ($%.2f) has been successfully authorized and processed by human supervisor.", 
                request.orderId(), request.amount());
    }
    
    public Map<String, RefundRequest> getPendingApprovals() {
        return pendingApprovals;
    }
}