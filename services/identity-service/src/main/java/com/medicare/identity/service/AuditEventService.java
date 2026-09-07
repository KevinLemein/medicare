package com.medicare.identity.service;

import com.medicare.identity.entity.AuditEvent;
import com.medicare.identity.entity.AuditEventType;
import com.medicare.identity.repository.AuditEventRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;
@Service

public class AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void recordByUser(AuditEventType type, UUID actorUserId, UUID subjectUserId, Map<String, Object> metadata) {
        save(type, actorUserId, null, subjectUserId, metadata);
    }

    public void recordByClient(AuditEventType type, String actorClientId, UUID subjectUserId, Map<String, Object> metadata) {
        save(type, null, actorClientId, subjectUserId, metadata);
    }

    public void recordSystem(AuditEventType type, UUID subjectUserId, Map<String, Object> metadata) {
        save(type, null, null, subjectUserId, metadata);
    }

    private void save(AuditEventType type, UUID actorUserId, String actorClientId, UUID subjectUserId, Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        event.setEventType(type);
        event.setActorUserId(actorUserId);
        event.setActorClientId(actorClientId);
        event.setSubjectUserId(subjectUserId);
        event.setMetadata(metadata);
        auditEventRepository.save(event);
    }
}
