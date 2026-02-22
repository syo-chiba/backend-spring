package com.example.backend_spring.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend_spring.domain.Flow;
import com.example.backend_spring.domain.FlowStep;
import com.example.backend_spring.domain.StepCandidate;
import com.example.backend_spring.repository.FlowRepository;
import com.example.backend_spring.repository.FlowStepRepository;
import com.example.backend_spring.repository.StepCandidateRepository;

@Service
public class FlowService {

    private static final List<String> BLOCKING_CANDIDATE_STATUSES = List.of("PROPOSED", "SELECTED");

    private final FlowRepository flowRepo;
    private final FlowStepRepository stepRepo;
    private final StepCandidateRepository candidateRepo;
    private final Clock clock;

    public FlowService(
            FlowRepository flowRepo,
            FlowStepRepository stepRepo,
            StepCandidateRepository candidateRepo,
            Clock clock) {
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
        this.candidateRepo = candidateRepo;
        this.clock = clock;
    }

    @Transactional
    public Long createFlow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId, List<String> participants) {
        validateReservableDateTime(startFrom, "調整開始日時");

        Flow flow = new Flow(title, durationMinutes, startFrom, createdByUserId);
        Flow saved = flowRepo.save(flow);

        List<FlowStep> steps = new ArrayList<>();
        int order = 1;
        for (String p : participants) {
            String name = p == null ? "" : p.trim();
            if (name.isEmpty()) {
                continue;
            }

            FlowStep step = new FlowStep(saved.getId(), order, name);
            if (order == 1) {
                step.activate();
            }
            steps.add(step);
            order++;
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException("参加者が1人もいません。");
        }

        stepRepo.saveAll(steps);
        return saved.getId();
    }

    public List<Flow> listFlows(String status, String keyword, String sort) {
        Comparator<Flow> comparator;
        if ("created_asc".equals(sort)) {
            comparator = Comparator.comparing(Flow::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Flow::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(Flow::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Flow::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        return flowRepo.findAll().stream()
                .filter(flow -> normalizedStatus.isEmpty() || normalizedStatus.equals(flow.getStatus()))
                .filter(flow -> normalizedKeyword.isEmpty() ||
                        (flow.getTitle() != null && flow.getTitle().toLowerCase().contains(normalizedKeyword)))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public List<Flow> listFlows() {
        return listFlows(null, null, "created_asc");
    }

    public Flow getFlow(Long id) {
        return flowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
    }

    public List<FlowStep> getSteps(Long flowId) {
        return stepRepo.findByFlowIdOrderByStepOrder(flowId);
    }

    public Optional<FlowStep> findActiveStep(Long flowId) {
        Flow flow = getFlow(flowId);

        FlowStep step = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (step == null || !"ACTIVE".equals(step.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(step);
    }

    public FlowStep getActiveStep(Long flowId) {
        return findActiveStep(flowId)
                .orElseThrow(() -> new IllegalStateException("現在ステップがACTIVEではありません。flowId=" + flowId));
    }

    public List<StepCandidate> getCandidates(Long flowStepId) {
        return candidateRepo.findByFlowStepIdOrderByStartAtAsc(flowStepId);
    }


    public LocalDate getReservableMinDate() {
        return LocalDate.now(clock).plusDays(1);
    }

    public LocalDate getReservableMaxDate() {
        return getReservableMinDate().plusMonths(3);
    }

    @Transactional
    public void addCandidateToActiveStep(Long flowId, LocalDateTime startAt) {
        Flow flow = getFlow(flowId);
        FlowStep active = getActiveStep(flowId);

        validateReservableDateTime(startAt, "候補日時");

        if (startAt.isBefore(flow.getStartFrom())) {
            throw new IllegalArgumentException("候補日時が開始可能日時より前です。startAt=" + startAt + ", startFrom=" + flow.getStartFrom());
        }

        LocalDate targetDate = startAt.toLocalDate();
        if (hasDateConflict(targetDate)) {
            throw new IllegalArgumentException("この日付は既に予約候補があるため選択できません。date=" + targetDate);
        }

        LocalDateTime endAt = startAt.plusMinutes(flow.getDurationMinutes());

        assertNoOwnerTimeOverlap(flow, startAt, endAt);

        candidateRepo.save(new StepCandidate(active.getId(), startAt, endAt));
    }

    @Transactional
    public void selectCandidateForActiveStep(Long flowId, Long candidateId) {
        Flow flow = getFlow(flowId);
        FlowStep active = getActiveStep(flowId);

        StepCandidate candidate = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("候補が見つかりません。candidateId=" + candidateId));

        if (!candidate.getFlowStepId().equals(active.getId())) {
            throw new IllegalArgumentException("ACTIVEステップの候補ではありません。candidateId=" + candidateId);
        }

        candidate.select();
        active.confirm(candidate.getStartAt(), candidate.getEndAt());

        List<StepCandidate> sameStepCandidates = candidateRepo.findByFlowStepIdOrderByStartAtAsc(active.getId());
        for (StepCandidate each : sameStepCandidates) {
            if (!each.getId().equals(candidate.getId()) && "PROPOSED".equals(each.getStatus())) {
                each.reject();
                candidateRepo.save(each);
            }
        }

        stepRepo.save(active);
        candidateRepo.save(candidate);

        flow.moveToNextStep();
        FlowStep next = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (next == null) {
            flow.markDone();
        } else {
            next.activate();
            stepRepo.save(next);
        }

        flowRepo.save(flow);
    }


    /**
     * 旧実装との互換レイヤー。
     * 日付単位の衝突判定は廃止済みのため常にfalseを返す。
     * （過去ブランチの呼び出し残骸があってもコンパイルを壊さないための保険）
     */
    @Deprecated(forRemoval = true)
    private boolean hasDateConflict(LocalDate ignoredDate) {
        return false;
    }

    private void assertNoOwnerTimeOverlap(Flow flow, LocalDateTime newStartAt, LocalDateTime newEndAt) {
        var conflict = candidateRepo.findFirstTimeConflictForOwner(flow.getCreatedByUserId(), newStartAt, newEndAt);
        if (conflict.isPresent()) {
            var c = conflict.get();
            throw new IllegalArgumentException(
                    "候補時間が重複しています: フロー=" + c.getFlowTitle()
                            + ", 参加者=" + c.getParticipantName()
                            + ", 既存=" + c.getStartAt() + " - " + c.getEndAt());
        }
    }

    private void validateReservableDateTime(LocalDateTime dateTime, String label) {
        LocalDate minDate = getReservableMinDate();
        LocalDate maxDate = getReservableMaxDate();

        LocalDate target = dateTime.toLocalDate();
        if (target.isBefore(minDate) || target.isAfter(maxDate)) {
            throw new IllegalArgumentException(label + "は翌日から3ヶ月以内で指定してください。value=" + dateTime);
        }
    }
}
