package com.example.backend_spring.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.backend_spring.domain.Flow;
import com.example.backend_spring.domain.FlowStep;
import com.example.backend_spring.repository.FlowRepository;
import com.example.backend_spring.repository.FlowStepRepository;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.UserAccountRepository;

@Component("flowAuthorization")
public class FlowAuthorization {

    private final FlowRepository flowRepo;
    private final FlowStepRepository stepRepo;
    private final ParticipantRepository participantRepo;
    private final UserAccountRepository userRepo;

    public FlowAuthorization(
            FlowRepository flowRepo,
            FlowStepRepository stepRepo,
            ParticipantRepository participantRepo,
            UserAccountRepository userRepo) {
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
        this.participantRepo = participantRepo;
        this.userRepo = userRepo;
    }

    public boolean canManageFlow(Long flowId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Optional<Long> currentUserId = currentUserId(authentication);
        if (currentUserId.isEmpty()) {
            return false;
        }
        return flowRepo.findById(flowId)
                .map(Flow::getCreatedByUserId)
                .filter(currentUserId.get()::equals)
                .isPresent();
    }

    public boolean canOperateActiveStep(Long flowId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        Optional<Long> currentUserId = currentUserId(authentication);
        if (currentUserId.isEmpty()) {
            return false;
        }

        Optional<Flow> flowOpt = flowRepo.findById(flowId);
        if (flowOpt.isEmpty()) {
            return false;
        }

        Flow flow = flowOpt.get();
        FlowStep activeStep = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (activeStep == null || !"ACTIVE".equals(activeStep.getStatus()) || activeStep.getParticipantId() == null) {
            return false;
        }

        return participantRepo.findById(activeStep.getParticipantId())
                .map(p -> p.getUserId() != null && p.getUserId().equals(currentUserId.get()))
                .orElse(false);
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Optional<Long> currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        return userRepo.findByUsername(authentication.getName()).map(u -> u.getId());
    }
}
