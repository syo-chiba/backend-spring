package com.example.backend_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.backend_spring.domain.StepCandidate;

public interface StepCandidateRepository extends JpaRepository<StepCandidate, Long> {

	List<StepCandidate> findByFlowStepIdOrderByStartAtAsc(Long flowStepId);
}
