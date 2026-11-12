package com.enterprise.ai.mcp.server.controller;

import org.springframework.web.bind.annotation.*;

import com.enterprise.ai.mcp.server.tools.EnterpriseDatabaseTools;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class RefundApprovalController {

    private final EnterpriseDatabaseTools databaseTools;

    public RefundApprovalController(EnterpriseDatabaseTools databaseTools) {
        this.databaseTools = databaseTools;
    }

    @GetMapping("/pending-refunds")
    public Map<String, EnterpriseDatabaseTools.RefundRequest> listPending() {
        return databaseTools.getPendingApprovals();
    }

    @PostMapping("/approve-refund")
    public String approveRefund(@RequestParam("approvalId") String approvalId) {
        return databaseTools.executeApprovedRefund(approvalId);
    }
}