package com.example.backend_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.backend_spring.domain.FlowTemplate;

public interface FlowTemplateRepository extends JpaRepository<FlowTemplate, Long> {

    @Query("""
            SELECT t
            FROM FlowTemplate t
            WHERE t.isActive = true
              AND t.deletedAt IS NULL
              AND (t.createdByUserId = :userId OR t.visibility = 'SHARED')
            ORDER BY t.updatedAt DESC, t.id DESC
            """)
    List<FlowTemplate> findAvailableForUser(@Param("userId") Long userId);
}
