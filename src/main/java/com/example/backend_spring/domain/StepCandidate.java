package com.example.backend_spring.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "step_candidates")
public class StepCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flowStepId;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String status = "PROPOSED";

    protected StepCandidate() {}

    public StepCandidate(Long flowStepId, LocalDateTime startAt, LocalDateTime endAt) {
        this.flowStepId = flowStepId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() { return id; }
    public Long getFlowStepId() { return flowStepId; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public String getStatus() { return status; }

    public void select() {
        this.status = "SELECTED";
    }

    public void reject() {
        this.status = "REJECTED";
    }
}
