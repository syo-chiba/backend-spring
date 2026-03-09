package com.example.backend_spring.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "flow_templates")
public class FlowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false, length = 20)
    private String visibility = "PRIVATE";

    @Column(nullable = false)
    private boolean isActive = true;

    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private long version;

    protected FlowTemplate() {
    }

    public FlowTemplate(String name, String description, int durationMinutes, Long createdByUserId, String visibility) {
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.createdByUserId = createdByUserId;
        this.visibility = visibility;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public String getVisibility() {
        return visibility;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void markUsedNow() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
