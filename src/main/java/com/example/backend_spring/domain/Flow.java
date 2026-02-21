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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Flow() {}

    public Flow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId) {
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.startFrom = startFrom;
        this.createdByUserId = createdByUserId;
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
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void moveToNextStep() {
        this.currentStepOrder++;
    }

    public void markDone() {
        this.status = "DONE";
    }
}
