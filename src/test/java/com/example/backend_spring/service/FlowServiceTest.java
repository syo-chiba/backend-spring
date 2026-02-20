package com.example.backend_spring.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.backend_spring.domain.Flow;
import com.example.backend_spring.domain.FlowStep;
import com.example.backend_spring.domain.StepCandidate;
import com.example.backend_spring.repository.FlowRepository;
import com.example.backend_spring.repository.FlowStepRepository;
import com.example.backend_spring.repository.StepCandidateRepository;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    @Mock
    private FlowRepository flowRepo;

    @Mock
    private FlowStepRepository stepRepo;

    @Mock
    private StepCandidateRepository candidateRepo;

    private FlowService flowService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-20T00:00:00Z"), ZoneId.systemDefault());
        flowService = new FlowService(flowRepo, stepRepo, candidateRepo, fixedClock);
    }

    @Test
    void createFlow_shouldRejectOutOfRangeStartFrom() {
        LocalDateTime invalidStart = LocalDateTime.of(2026, 2, 20, 10, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> flowService.createFlow("面談", 60, invalidStart, 1L, List.of("A")));

        assertTrue(ex.getMessage().contains("翌日から3ヶ月以内"));
        verify(flowRepo, never()).save(any(Flow.class));
    }

    @Test
    void addCandidateToActiveStep_shouldRejectDateConflictAcrossAllUsers() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        FlowStep active = new FlowStep(10L, 1, "A");
        active.activate();

        when(flowRepo.findById(10L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(10L, 1)).thenReturn(active);
        when(candidateRepo.existsByStatusInAndStartAtGreaterThanEqualAndStartAtLessThan(anyList(), any(), any()))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> flowService.addCandidateToActiveStep(10L, LocalDateTime.of(2026, 2, 23, 11, 0)));

        assertTrue(ex.getMessage().contains("既に予約候補"));
        verify(candidateRepo, never()).save(any(StepCandidate.class));
    }

    @Test
    void selectCandidateForActiveStep_shouldConfirmAndAdvanceToNextStep() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        FlowStep active = new FlowStep(20L, 1, "A");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 1L);

        FlowStep next = new FlowStep(20L, 2, "B");

        StepCandidate candidate = new StepCandidate(1L, LocalDateTime.of(2026, 2, 22, 10, 0), LocalDateTime.of(2026, 2, 22, 11, 0));

        when(flowRepo.findById(20L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(20L, 1)).thenReturn(active);
        when(candidateRepo.findById(99L)).thenReturn(Optional.of(candidate));
        when(stepRepo.findByFlowIdAndStepOrder(20L, 2)).thenReturn(next);

        flowService.selectCandidateForActiveStep(20L, 99L);

        assertTrue("SELECTED".equals(candidate.getStatus()));
        assertTrue("CONFIRMED".equals(active.getStatus()));
        assertTrue("ACTIVE".equals(next.getStatus()));

        verify(candidateRepo).save(candidate);
        verify(stepRepo).save(active);
        verify(stepRepo).save(next);
        verify(flowRepo).save(flow);
    }
}
