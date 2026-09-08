package com.medicare.identity.dto.response;

import com.medicare.identity.entity.AuditEvent;
import com.medicare.identity.entity.AuditEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        AuditEventType eventType,
        UUID actorUserId,
        String actorClientId,
        UUID subjectUserId,
        Instant occurredAt,
        Map<String, Object> metadata
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorUserId(),
                event.getActorClientId(),
                event.getSubjectUserId(),
                event.getOccurredAt(),
                event.getMetadata()
        );
    }
}
