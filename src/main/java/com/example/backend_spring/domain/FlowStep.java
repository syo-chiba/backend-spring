package com.example.backend_spring.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flow_steps")
public class FlowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flowId;

    private int stepOrder;

    private String participantName;

    private String status = "PENDING";

    private LocalDateTime confirmedStartAt;

    private LocalDateTime confirmedEndAt;

    protected FlowStep() {}

    public FlowStep(Long flowId, int stepOrder, String participantName) {
        this.flowId = flowId;
        this.stepOrder = stepOrder;
        this.participantName = participantName;
    }

    public Long getId() { return id; }
    public Long getFlowId() { return flowId; }
    public int getStepOrder() { return stepOrder; }
    public String getParticipantName() { return participantName; }
    public String getStatus() { return status; }
    public LocalDateTime getConfirmedStartAt() { return confirmedStartAt; }
    public LocalDateTime getConfirmedEndAt() { return confirmedEndAt; }

    public void activate() {
        this.status = "ACTIVE";
    }

    public void confirm(LocalDateTime start, LocalDateTime end) {
        this.status = "CONFIRMED";
        this.confirmedStartAt = start;
        this.confirmedEndAt = end;
    }

    public void skip() {
        this.status = "SKIPPED";
    }
}
