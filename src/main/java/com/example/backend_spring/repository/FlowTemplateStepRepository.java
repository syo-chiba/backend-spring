package com.example.backend_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend_spring.domain.FlowTemplateStep;

public interface FlowTemplateStepRepository extends JpaRepository<FlowTemplateStep, Long> {

    List<FlowTemplateStep> findByTemplateIdOrderByStepOrderAsc(Long templateId);
}
