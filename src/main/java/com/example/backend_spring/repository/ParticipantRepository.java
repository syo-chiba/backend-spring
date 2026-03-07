package com.example.backend_spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend_spring.domain.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByParticipantTypeAndUserId(String participantType, Long userId);

    Optional<Participant> findByParticipantTypeAndDisplayName(String participantType, String displayName);
}
