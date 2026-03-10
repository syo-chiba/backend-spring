package com.example.backend_spring.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.backend_spring.domain.StepCandidate;

public interface StepCandidateRepository extends JpaRepository<StepCandidate, Long> {

    List<StepCandidate> findByFlowStepIdOrderByStartAtAsc(Long flowStepId);

    boolean existsByStatusInAndStartAtGreaterThanEqualAndStartAtLessThan(
            Collection<String> statuses,
            LocalDateTime from,
            LocalDateTime toExclusive);

    List<StepCandidate> findByStatusInAndStartAtGreaterThanEqualAndStartAtLessThan(
            Collection<String> statuses,
            LocalDateTime from,
            LocalDateTime toExclusive);

    @Query(value = """
            SELECT
                f.title AS flowTitle,
                COALESCE(p.display_name, CONCAT('participant#', fs.participant_id)) AS participantName,
                sc.start_at AS startAt,
                sc.end_at AS endAt
            FROM step_candidates sc
            INNER JOIN flow_steps fs ON fs.id = sc.flow_step_id
            INNER JOIN flows f ON f.id = fs.flow_id
            LEFT JOIN participants p ON p.id = fs.participant_id
            WHERE ((:createdByUserId IS NULL AND f.created_by_user_id IS NULL) OR f.created_by_user_id = :createdByUserId)
              AND fs.status = 'ACTIVE'
              AND sc.status = 'PROPOSED'
              AND (:excludeFlowStepId IS NULL OR sc.flow_step_id <> :excludeFlowStepId)
              AND sc.start_at < :newEndAt
              AND sc.end_at > :newStartAt
            ORDER BY sc.start_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ConflictCandidateView> findFirstTimeConflictForOwner(
            @Param("createdByUserId") Long createdByUserId,
            @Param("excludeFlowStepId") Long excludeFlowStepId,
            @Param("newStartAt") LocalDateTime newStartAt,
            @Param("newEndAt") LocalDateTime newEndAt);

    @Query(value = """
            SELECT
                f.title AS flowTitle,
                COALESCE(p.display_name, CONCAT('participant#', fs.participant_id)) AS participantName,
                sc.start_at AS startAt,
                sc.end_at AS endAt
            FROM step_candidates sc
            INNER JOIN flow_steps fs ON fs.id = sc.flow_step_id
            INNER JOIN flows f ON f.id = fs.flow_id
            LEFT JOIN participants p ON p.id = fs.participant_id
            WHERE fs.participant_id = :participantId
              AND fs.status = 'ACTIVE'
              AND sc.status IN ('PROPOSED', 'SELECTED')
              AND (:excludeFlowStepId IS NULL OR sc.flow_step_id <> :excludeFlowStepId)
              AND sc.start_at < :newEndAt
              AND sc.end_at > :newStartAt
            ORDER BY sc.start_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ConflictCandidateView> findFirstTimeConflictForParticipant(
            @Param("participantId") Long participantId,
            @Param("excludeFlowStepId") Long excludeFlowStepId,
            @Param("newStartAt") LocalDateTime newStartAt,
            @Param("newEndAt") LocalDateTime newEndAt);

    interface ConflictCandidateView {
        String getFlowTitle();

        String getParticipantName();

        LocalDateTime getStartAt();

        LocalDateTime getEndAt();
    }
}
