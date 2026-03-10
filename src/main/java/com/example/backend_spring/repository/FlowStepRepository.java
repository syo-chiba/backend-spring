package com.example.backend_spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.backend_spring.domain.FlowStep;
import java.time.LocalDateTime;

public interface FlowStepRepository extends JpaRepository<FlowStep, Long> {

    List<FlowStep> findByFlowIdOrderByStepOrder(Long flowId);

    FlowStep findByFlowIdAndStepOrder(Long flowId, int stepOrder);

    @Query(value = """
            SELECT
                f.title AS flowTitle,
                COALESCE(p.display_name, CONCAT('participant#', fs.participant_id)) AS participantName,
                fs.confirmed_start_at AS startAt,
                fs.confirmed_end_at AS endAt
            FROM flow_steps fs
            INNER JOIN flows f ON f.id = fs.flow_id
            LEFT JOIN participants p ON p.id = fs.participant_id
            WHERE ((:createdByUserId IS NULL AND f.created_by_user_id IS NULL) OR f.created_by_user_id = :createdByUserId)
              AND fs.confirmed_start_at IS NOT NULL
              AND fs.confirmed_end_at IS NOT NULL
              AND (:excludeStepId IS NULL OR fs.id <> :excludeStepId)
              AND fs.confirmed_start_at < :newEndAt
              AND fs.confirmed_end_at > :newStartAt
            ORDER BY fs.confirmed_start_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ConflictStepView> findFirstConfirmedConflictForOwner(
            @Param("createdByUserId") Long createdByUserId,
            @Param("excludeStepId") Long excludeStepId,
            @Param("newStartAt") LocalDateTime newStartAt,
            @Param("newEndAt") LocalDateTime newEndAt);

    @Query(value = """
            SELECT
                f.title AS flowTitle,
                COALESCE(p.display_name, CONCAT('participant#', fs.participant_id)) AS participantName,
                fs.confirmed_start_at AS startAt,
                fs.confirmed_end_at AS endAt
            FROM flow_steps fs
            INNER JOIN flows f ON f.id = fs.flow_id
            LEFT JOIN participants p ON p.id = fs.participant_id
            WHERE fs.participant_id = :participantId
              AND fs.confirmed_start_at IS NOT NULL
              AND fs.confirmed_end_at IS NOT NULL
              AND (:excludeStepId IS NULL OR fs.id <> :excludeStepId)
              AND fs.confirmed_start_at < :newEndAt
              AND fs.confirmed_end_at > :newStartAt
            ORDER BY fs.confirmed_start_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ConflictStepView> findFirstConfirmedConflictForParticipant(
            @Param("participantId") Long participantId,
            @Param("excludeStepId") Long excludeStepId,
            @Param("newStartAt") LocalDateTime newStartAt,
            @Param("newEndAt") LocalDateTime newEndAt);

    interface ConflictStepView {
        String getFlowTitle();

        String getParticipantName();

        LocalDateTime getStartAt();

        LocalDateTime getEndAt();
    }
}
