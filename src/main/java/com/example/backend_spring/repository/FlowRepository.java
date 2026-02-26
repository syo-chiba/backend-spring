package com.example.backend_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend_spring.domain.Flow;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    List<Flow> findAllByOrderByIdDesc();
}
