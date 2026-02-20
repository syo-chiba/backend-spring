package com.example.backend_spring.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private final FlowRepository flowRepo;
    private final FlowStepRepository stepRepo;
    private final StepCandidateRepository candidateRepo;

    public FlowService(FlowRepository flowRepo, FlowStepRepository stepRepo, StepCandidateRepository candidateRepo) {
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
        this.candidateRepo = candidateRepo;
    }

    // ===== Flow作成 =====
    @Transactional
    public Long createFlow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId, List<String> participants) {
        Flow flow = new Flow(title, durationMinutes, startFrom, createdByUserId);
        Flow saved = flowRepo.save(flow);

        List<FlowStep> steps = new ArrayList<>();
        int order = 1;
        for (String p : participants) {
            String name = p == null ? "" : p.trim();
            if (name.isEmpty()) continue;

            FlowStep step = new FlowStep(saved.getId(), order, name);
            if (order == 1) {
                step.activate(); // 最初だけACTIVE
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

    // ===== 参照系 =====
    public Flow getFlow(Long id) {
        return flowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
    }

    public List<FlowStep> getSteps(Long flowId) {
        return stepRepo.findByFlowIdOrderByStepOrder(flowId);
    }

    public FlowStep getActiveStep(Long flowId) {
        Flow flow = getFlow(flowId);

        FlowStep step = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (step == null) {
            throw new IllegalStateException("現在ステップが見つかりません。flowId=" + flowId + ", currentStepOrder=" + flow.getCurrentStepOrder());
        }
        // DB上で状態がズレたときに即検知する
        if (!"ACTIVE".equals(step.getStatus())) {
            throw new IllegalStateException("現在ステップがACTIVEではありません。status=" + step.getStatus());
        }
        return step;
    }

    public List<StepCandidate> getCandidates(Long flowStepId) {
        return candidateRepo.findByFlowStepIdOrderByStartAtAsc(flowStepId);
    }

    // ===== 候補追加（MVP）=====
    @Transactional
    public void addCandidateToActiveStep(Long flowId, LocalDateTime startAt) {
        Flow flow = getFlow(flowId);
        FlowStep active = getActiveStep(flowId); // ACTIVEチェック込み

        // startAtの下限チェック（任意だが実務的に入れておくと良い）
        // 直前確定後の制約は、確定機能実装後に強化する
        if (startAt.isBefore(flow.getStartFrom())) {
            throw new IllegalArgumentException("候補日時が開始可能日時より前です。startAt=" + startAt + ", startFrom=" + flow.getStartFrom());
        }

        LocalDateTime endAt = startAt.plusMinutes(flow.getDurationMinutes());
        candidateRepo.save(new StepCandidate(active.getId(), startAt, endAt));
    }
}
