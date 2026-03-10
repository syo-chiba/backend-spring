package com.example.backend_spring.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "flows")
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private int durationMinutes;

    private String status = "IN_PROGRESS";

    private int currentStepOrder = 1;

    private LocalDateTime startFrom;

    private Long createdByUserId;

    private Long sourceTemplateId;

    private String sourceTemplateNameSnapshot;

    private Integer stepCycleSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Flow() {}

    public Flow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId) {
        this(title, durationMinutes, startFrom, createdByUserId, null, null);
    }

    public Flow(
            String title,
            int durationMinutes,
            LocalDateTime startFrom,
            Long createdByUserId,
            Long sourceTemplateId,
            String sourceTemplateNameSnapshot) {
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.startFrom = startFrom;
        this.createdByUserId = createdByUserId;
        this.sourceTemplateId = sourceTemplateId;
        this.sourceTemplateNameSnapshot = sourceTemplateNameSnapshot;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getStatus() { return status; }
    public int getCurrentStepOrder() { return currentStepOrder; }
    public LocalDateTime getStartFrom() { return startFrom; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getSourceTemplateId() { return sourceTemplateId; }
    public String getSourceTemplateNameSnapshot() { return sourceTemplateNameSnapshot; }
    public Integer getStepCycleSize() { return stepCycleSize; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updateBasics(String title, int durationMinutes, LocalDateTime startFrom) {
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.startFrom = startFrom;
    }

    public void moveToNextStep() {
        this.currentStepOrder++;
    }

    public void moveToStep(int stepOrder) {
        if (stepOrder < 1) {
            throw new IllegalArgumentException("stepOrder must be >= 1");
        }
        this.currentStepOrder = stepOrder;
        this.status = "IN_PROGRESS";
    }

    public void ensureStepCycleSize(int stepCycleSize) {
        if (stepCycleSize < 1) {
            throw new IllegalArgumentException("stepCycleSize must be >= 1");
        }
        if (this.stepCycleSize == null || this.stepCycleSize < 1) {
            this.stepCycleSize = stepCycleSize;
        }
    }

    public void markDone() {
        this.status = "DONE";
    }
}
