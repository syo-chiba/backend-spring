package com.example.backend_spring.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.backend_spring.domain.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByParticipantTypeAndUserId(String participantType, Long userId);

    Optional<Participant> findByParticipantTypeAndDisplayName(String participantType, String displayName);

    @Query(value = """
            SELECT p.*
            FROM participants p
            INNER JOIN users u ON u.id = p.user_id
            WHERE p.participant_type = 'USER'
            ORDER BY p.display_name
            """, nativeQuery = true)
    List<Participant> findActiveUserParticipants();
}
