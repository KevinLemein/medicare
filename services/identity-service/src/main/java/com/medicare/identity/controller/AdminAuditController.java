package com.medicare.identity.controller;

import com.medicare.identity.common.response.ApiResponse;
import com.medicare.identity.dto.response.AuditEventResponse;
import com.medicare.identity.dto.response.PageResponse;
import com.medicare.identity.repository.AuditEventRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only audit-trail retrieval. Restricted to SYSTEM_ADMIN by
 * SecurityConfig's "/admin/**" matcher.
 */
@RestController
@RequestMapping("/admin/audit-events")
public class AdminAuditController {

    private final AuditEventRepository auditEventRepository;

    public AdminAuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditEventResponse>>> findBySubjectUser(
            @RequestParam UUID subjectUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var events = auditEventRepository
                .findBySubjectUserId(subjectUserId, PageRequest.of(page, size))
                .map(AuditEventResponse::from);

        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(events), "Audit events retrieved", null));
    }
}
