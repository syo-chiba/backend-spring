package com.example.backend_spring.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "flow_template_steps")
public class FlowTemplateStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int stepOrder;

    private Long participantId;

    @Column(nullable = false, length = 20)
    private String participantTypeSnapshot;

    @Column(nullable = false, length = 100)
    private String participantNameSnapshot;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private long version;

    protected FlowTemplateStep() {
    }

    public FlowTemplateStep(
            Long templateId,
            int stepOrder,
            Long participantId,
            String participantTypeSnapshot,
            String participantNameSnapshot) {
        this.templateId = templateId;
        this.stepOrder = stepOrder;
        this.participantId = participantId;
        this.participantTypeSnapshot = participantTypeSnapshot;
        this.participantNameSnapshot = participantNameSnapshot;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public String getParticipantTypeSnapshot() {
        return participantTypeSnapshot;
    }

    public String getParticipantNameSnapshot() {
        return participantNameSnapshot;
    }
}
