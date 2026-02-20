package com.example.backend_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.backend_spring.domain.FlowStep;

public interface FlowStepRepository extends JpaRepository<FlowStep, Long> {

    List<FlowStep> findByFlowIdOrderByStepOrder(Long flowId);

    FlowStep findByFlowIdAndStepOrder(Long flowId, int stepOrder);
}
