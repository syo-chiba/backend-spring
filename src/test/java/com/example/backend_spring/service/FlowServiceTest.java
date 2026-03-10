package com.example.backend_spring.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.StepCandidateRepository;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    @Mock
    private FlowRepository flowRepo;

    @Mock
    private FlowStepRepository stepRepo;

    @Mock
    private StepCandidateRepository candidateRepo;

    @Mock
    private ParticipantRepository participantRepo;

    private FlowService flowService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-20T00:00:00Z"), ZoneId.systemDefault());
        flowService = new FlowService(flowRepo, stepRepo, candidateRepo, participantRepo, fixedClock);
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
    void listFlows_shouldFilterByStatusAndKeyword() {
        Flow f1 = new Flow("面談A", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        Flow f2 = new Flow("定例B", 60, LocalDateTime.of(2026, 2, 22, 9, 0), 1L);
        ReflectionTestUtils.setField(f1, "status", "IN_PROGRESS");
        ReflectionTestUtils.setField(f2, "status", "DONE");

        when(flowRepo.findAll()).thenReturn(List.of(f1, f2));

        List<Flow> filtered = flowService.listFlows("DONE", "定例", "created_desc");

        assertEquals(1, filtered.size());
        assertEquals("定例B", filtered.get(0).getTitle());
    }

    @Test
    void listFlows_shouldSortByCreatedAtAsc() {
        Flow older = new Flow("old", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        Flow newer = new Flow("new", 60, LocalDateTime.of(2026, 2, 22, 9, 0), 1L);

        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 2, 21, 8, 0));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 2, 22, 8, 0));

        when(flowRepo.findAll()).thenReturn(List.of(newer, older));

        List<Flow> sorted = flowService.listFlows("", "", "created_asc");

        assertEquals("old", sorted.get(0).getTitle());
        assertEquals("new", sorted.get(1).getTitle());
    }


    @Test
    void addCandidateToActiveStep_shouldRejectWhenTimeOverlapsForSameOwner() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 77L);
        FlowStep active = new FlowStep(30L, 1, "A");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 1L);

        StepCandidateRepository.ConflictCandidateView conflict = new StepCandidateRepository.ConflictCandidateView() {
            public String getFlowTitle() { return "既存フロー"; }
            public String getParticipantName() { return "Bさん"; }
            public LocalDateTime getStartAt() { return LocalDateTime.of(2026, 2, 23, 10, 0); }
            public LocalDateTime getEndAt() { return LocalDateTime.of(2026, 2, 23, 11, 0); }
        };

        when(flowRepo.findById(30L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(30L, 1)).thenReturn(active);
        when(candidateRepo.findFirstTimeConflictForOwner(eq(77L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(conflict));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> flowService.addCandidateToActiveStep(30L, LocalDateTime.of(2026, 2, 23, 10, 30)));

        assertTrue(ex.getMessage().contains("重複"));
        verify(candidateRepo, never()).save(any(StepCandidate.class));
    }

    @Test
    void addCandidateToActiveStep_shouldAllowWhenBoundaryTouches() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 77L);
        FlowStep active = new FlowStep(31L, 1, "A");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 1L);

        when(flowRepo.findById(31L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(31L, 1)).thenReturn(active);
        when(candidateRepo.findFirstTimeConflictForOwner(eq(77L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(stepRepo.findFirstConfirmedConflictForOwner(eq(77L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(candidateRepo.save(any(StepCandidate.class))).thenAnswer(invocation -> {
            StepCandidate saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 501L);
            return saved;
        });
        when(candidateRepo.findById(501L)).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            StepCandidate c = new StepCandidate(1L, LocalDateTime.of(2026, 2, 23, 11, 0), LocalDateTime.of(2026, 2, 23, 12, 0));
            ReflectionTestUtils.setField(c, "id", id);
            return Optional.of(c);
        });
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(1L)).thenAnswer(invocation -> {
            StepCandidate c = new StepCandidate(1L, LocalDateTime.of(2026, 2, 23, 11, 0), LocalDateTime.of(2026, 2, 23, 12, 0));
            ReflectionTestUtils.setField(c, "id", 501L);
            return List.of(c);
        });
        when(stepRepo.findByFlowIdAndStepOrder(31L, 2)).thenReturn(null);
        when(stepRepo.save(any(FlowStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.addCandidateToActiveStep(31L, LocalDateTime.of(2026, 2, 23, 11, 0));

        verify(candidateRepo, times(2)).save(any(StepCandidate.class));
    }

    @Test
    void addCandidateToActiveStep_shouldAllowWhenDifferentOwnerHasConflictSlot() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 88L);
        FlowStep active = new FlowStep(32L, 1, "A");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 1L);

        when(flowRepo.findById(32L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(32L, 1)).thenReturn(active);
        when(candidateRepo.findFirstTimeConflictForOwner(eq(88L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(stepRepo.findFirstConfirmedConflictForOwner(eq(88L), any(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(candidateRepo.save(any(StepCandidate.class))).thenAnswer(invocation -> {
            StepCandidate saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 502L);
            return saved;
        });
        when(candidateRepo.findById(502L)).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            StepCandidate c = new StepCandidate(1L, LocalDateTime.of(2026, 2, 23, 10, 0), LocalDateTime.of(2026, 2, 23, 11, 0));
            ReflectionTestUtils.setField(c, "id", id);
            return Optional.of(c);
        });
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(1L)).thenAnswer(invocation -> {
            StepCandidate c = new StepCandidate(1L, LocalDateTime.of(2026, 2, 23, 10, 0), LocalDateTime.of(2026, 2, 23, 11, 0));
            ReflectionTestUtils.setField(c, "id", 502L);
            return List.of(c);
        });
        when(stepRepo.findByFlowIdAndStepOrder(32L, 2)).thenReturn(null);
        when(stepRepo.save(any(FlowStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.addCandidateToActiveStep(32L, LocalDateTime.of(2026, 2, 23, 10, 0));

        verify(candidateRepo, times(2)).save(any(StepCandidate.class));
    }

    @Test
    void addCandidateToActiveStep_shouldRejectWhenEarlierThanPreviousStepEnd() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        ReflectionTestUtils.setField(flow, "currentStepOrder", 2);

        FlowStep previous = new FlowStep(40L, 1, "A");
        previous.confirm(LocalDateTime.of(2026, 2, 23, 10, 0), LocalDateTime.of(2026, 2, 23, 11, 0));

        FlowStep active = new FlowStep(40L, 2, "B");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 2L);

        when(flowRepo.findById(40L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(40L, 2)).thenReturn(active);
        when(stepRepo.findByFlowIdAndStepOrder(40L, 1)).thenReturn(previous);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> flowService.addCandidateToActiveStep(40L, LocalDateTime.of(2026, 2, 23, 10, 30)));

        assertTrue(ex.getMessage().contains("前ステップ終了日時より後"));
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
        ReflectionTestUtils.setField(candidate, "id", 99L);

        StepCandidate another = new StepCandidate(1L, LocalDateTime.of(2026, 2, 23, 10, 0), LocalDateTime.of(2026, 2, 23, 11, 0));
        ReflectionTestUtils.setField(another, "id", 100L);

        when(flowRepo.findById(20L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(20L, 1)).thenReturn(active);
        when(candidateRepo.findById(99L)).thenReturn(Optional.of(candidate));
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(1L)).thenReturn(List.of(candidate, another));
        when(stepRepo.findByFlowIdAndStepOrder(20L, 2)).thenReturn(next);

        flowService.selectCandidateForActiveStep(20L, 99L);

        assertTrue("SELECTED".equals(candidate.getStatus()));
        assertTrue("REJECTED".equals(another.getStatus()));
        assertTrue("CONFIRMED".equals(active.getStatus()));
        assertTrue("ACTIVE".equals(next.getStatus()));

        verify(candidateRepo).save(candidate);
        verify(stepRepo).save(active);
        verify(stepRepo).save(next);
        verify(flowRepo).save(flow);
    }

    @Test
    void selectCandidateForActiveStep_shouldAppendNewStepWhenLastStepIsSelected() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        ReflectionTestUtils.setField(flow, "currentStepOrder", 2);
        ReflectionTestUtils.setField(flow, "status", "IN_PROGRESS");

        FlowStep lastStep = new FlowStep(21L, 2, "B");
        lastStep.activate();
        ReflectionTestUtils.setField(lastStep, "id", 2L);

        FlowStep firstStep = new FlowStep(21L, 1, "A");
        firstStep.confirm(LocalDateTime.of(2026, 2, 22, 9, 0), LocalDateTime.of(2026, 2, 22, 10, 0));
        ReflectionTestUtils.setField(firstStep, "id", 1L);

        StepCandidate candidate = new StepCandidate(2L, LocalDateTime.of(2026, 2, 24, 10, 0), LocalDateTime.of(2026, 2, 24, 11, 0));
        ReflectionTestUtils.setField(candidate, "id", 199L);

        when(flowRepo.findById(21L)).thenReturn(Optional.of(flow));
        when(stepRepo.findByFlowIdAndStepOrder(21L, 2)).thenReturn(lastStep);
        when(candidateRepo.findById(199L)).thenReturn(Optional.of(candidate));
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(2L)).thenReturn(List.of(candidate));
        when(stepRepo.findByFlowIdAndStepOrder(21L, 3)).thenReturn(null);
        when(stepRepo.findByFlowIdAndStepOrder(21L, 1)).thenReturn(firstStep);
        when(stepRepo.save(any(FlowStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flowService.selectCandidateForActiveStep(21L, 199L);

        assertEquals(3, flow.getCurrentStepOrder());
        assertEquals("IN_PROGRESS", flow.getStatus());
        verify(stepRepo, times(3)).save(any(FlowStep.class));
        verify(flowRepo).save(flow);
    }

    @Test
    void buildWeeklyCalendarView_shouldContainConfirmedAndProposedEvents() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        ReflectionTestUtils.setField(flow, "id", 20L);

        FlowStep confirmed = new FlowStep(20L, 1, "A");
        confirmed.confirm(LocalDateTime.of(2026, 2, 23, 10, 0), LocalDateTime.of(2026, 2, 23, 11, 0));

        FlowStep active = new FlowStep(20L, 2, "B");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 200L);

        StepCandidate proposed = new StepCandidate(200L,
                LocalDateTime.of(2026, 2, 24, 9, 0),
                LocalDateTime.of(2026, 2, 24, 10, 0));

        when(stepRepo.findByFlowIdOrderByStepOrder(20L)).thenReturn(List.of(confirmed, active));
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(200L)).thenReturn(List.of(proposed));

        FlowService.WeeklyCalendarView view = flowService.buildWeeklyCalendarView(
                List.of(flow),
                java.time.LocalDate.of(2026, 2, 23));

        assertEquals("2026-02-23", view.getWeekStart());
        assertEquals(24, view.getHourLabels().size());
        assertEquals("0", view.getHourLabels().get(0));
        assertEquals("23", view.getHourLabels().get(23));
        assertEquals(7, view.getDays().size());

        List<FlowService.CalendarEvent> allEvents = view.getDays().stream()
                .flatMap(day -> day.getEvents().stream())
                .collect(Collectors.toList());

        assertEquals(2, allEvents.size());
        assertTrue(allEvents.stream().anyMatch(e -> "CONFIRMED".equals(e.getStatus())));
        assertTrue(allEvents.stream().anyMatch(e -> "PROPOSED".equals(e.getStatus())));
        assertTrue(allEvents.stream().allMatch(e -> e.getStyle().contains("top:")));
        assertTrue(allEvents.stream().allMatch(e -> e.getStyle().contains("height:")));
        assertTrue(allEvents.stream().allMatch(e -> !e.getTimeLabel().contains("/")));
    }

    @Test
    void buildMonthlyCalendarView_shouldBuildMonthCellsAndTimeLabels() {
        Flow flow = new Flow("面談", 60, LocalDateTime.of(2026, 2, 21, 9, 0), 1L);
        ReflectionTestUtils.setField(flow, "id", 33L);

        FlowStep active = new FlowStep(33L, 1, "A");
        active.activate();
        ReflectionTestUtils.setField(active, "id", 301L);

        StepCandidate proposed = new StepCandidate(301L,
                LocalDateTime.of(2026, 3, 3, 12, 0),
                LocalDateTime.of(2026, 3, 3, 13, 0));

        when(stepRepo.findByFlowIdOrderByStepOrder(33L)).thenReturn(List.of(active));
        when(candidateRepo.findByFlowStepIdOrderByStartAtAsc(301L)).thenReturn(List.of(proposed));

        FlowService.MonthlyCalendarView view = flowService.buildMonthlyCalendarView(List.of(flow), LocalDate.of(2026, 3, 10));

        assertEquals("2026年3月", view.getMonthLabel());
        assertEquals("2026-03-01", view.getMonthStart());
        assertEquals(7, view.getWeekdayHeaders().size());
        assertTrue(view.getWeeks().size() >= 4);

        List<FlowService.MonthlyCalendarEvent> allEvents = view.getWeeks().stream()
                .flatMap(week -> week.getDays().stream())
                .flatMap(day -> day.getEvents().stream())
                .collect(Collectors.toList());

        assertEquals(1, allEvents.size());
        assertTrue(allEvents.get(0).getTitle().contains("#33"));
        assertEquals("12:00 - 13:00", allEvents.get(0).getTimeLabel());
    }
}
