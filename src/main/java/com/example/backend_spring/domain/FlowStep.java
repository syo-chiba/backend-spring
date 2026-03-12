package com.example.backend_spring.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flow_steps")
public class FlowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long flowId;

    private int stepOrder;

    @Column(name = "participant_id")
    private Long participantId;

    @Transient
    private String participantName;

    private String status = "PENDING";

    private LocalDateTime confirmedStartAt;

    private LocalDateTime confirmedEndAt;

    private LocalDate reservableFromDate;

    private LocalDate reservableToDate;

    private int allowedWeekdaysMask = 127;

    private int allowedStartMinute = 0;

    private int allowedEndMinute = 1440;

    protected FlowStep() {}

    public FlowStep(Long flowId, int stepOrder, String participantName) {
        this(flowId, stepOrder, null, participantName);
    }

    public FlowStep(Long flowId, int stepOrder, Long participantId, String participantName) {
        this.flowId = flowId;
        this.stepOrder = stepOrder;
        this.participantId = participantId;
        this.participantName = participantName;
    }

    public Long getId() { return id; }
    public Long getFlowId() { return flowId; }
    public int getStepOrder() { return stepOrder; }
    public Long getParticipantId() { return participantId; }
    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }
    public String getStatus() { return status; }
    public LocalDateTime getConfirmedStartAt() { return confirmedStartAt; }
    public LocalDateTime getConfirmedEndAt() { return confirmedEndAt; }
    public LocalDate getReservableFromDate() { return reservableFromDate; }
    public LocalDate getReservableToDate() { return reservableToDate; }
    public int getAllowedWeekdaysMask() { return allowedWeekdaysMask; }
    public int getAllowedStartMinute() { return allowedStartMinute; }
    public int getAllowedEndMinute() { return allowedEndMinute; }

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

    public void reassignParticipant(Long participantId, String participantName) {
        this.participantId = participantId;
        this.participantName = participantName;
    }

    public void updateReservableConstraints(
            LocalDate reservableFromDate,
            LocalDate reservableToDate,
            int allowedWeekdaysMask,
            int allowedStartMinute,
            int allowedEndMinute) {
        this.reservableFromDate = reservableFromDate;
        this.reservableToDate = reservableToDate;
        this.allowedWeekdaysMask = allowedWeekdaysMask;
        this.allowedStartMinute = allowedStartMinute;
        this.allowedEndMinute = allowedEndMinute;
    }

    public void copyReservableConstraintsFrom(FlowStep source) {
        if (source == null) {
            return;
        }
        updateReservableConstraints(
                source.getReservableFromDate(),
                source.getReservableToDate(),
                source.getAllowedWeekdaysMask(),
                source.getAllowedStartMinute(),
                source.getAllowedEndMinute());
    }
}
