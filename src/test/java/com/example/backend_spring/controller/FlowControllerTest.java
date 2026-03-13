package com.example.backend_spring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.backend_spring.domain.UserAccount;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.UserAccountRepository;
import com.example.backend_spring.security.FlowAuthorization;
import com.example.backend_spring.service.FlowService;

@ExtendWith(MockitoExtension.class)
class FlowControllerTest {

    @Mock
    private UserAccountRepository userRepo;

    @Mock
    private ParticipantRepository participantRepo;

    private StubFlowService flowService;
    private FlowAuthorization flowAuthorization;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        flowService = new StubFlowService();
        flowAuthorization = new FlowAuthorization(null, null, null, null) {
            @Override
            public boolean canManageFlow(Long flowId, org.springframework.security.core.Authentication authentication) {
                return true;
            }

            @Override
            public boolean canOperateActiveStep(Long flowId, org.springframework.security.core.Authentication authentication) {
                return true;
            }

            @Override
            public boolean isAdmin(org.springframework.security.core.Authentication authentication) {
                return true;
            }
        };
        FlowController controller = new FlowController(flowService, userRepo, participantRepo, flowAuthorization);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void create_whenServiceValidationFails_thenRedirectToNewWithError() throws Exception {
        flowService.createException = new IllegalArgumentException("validation failed");

        mockMvc.perform(post("/flows")
                        .param("title", "meeting")
                        .param("durationMinutes", "60")
                        .param("participantUserIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/new"))
                .andExpect(flash().attribute("error", "validation failed"));
    }

    @Test
    void addCandidate_whenSuccess_thenRedirectWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/flows/10/candidates")
                        .param("startDate", "2026-02-23")
                        .param("startTime", "10:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/10"))
                .andExpect(flash().attributeExists("message"));

        assertEquals(10L, flowService.addCandidateFlowId);
        assertNotNull(flowService.addCandidateStartAt);
    }

    @Test
    void selectCandidate_whenServiceFails_thenRedirectWithErrorMessage() throws Exception {
        flowService.selectException = new IllegalStateException("active step missing");

        mockMvc.perform(post("/flows/10/candidates/99/select"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/10"))
                .andExpect(flash().attribute("error", "active step missing"));
    }

    @Test
    void create_withPrincipal_mapsCreatedByUserId() throws Exception {
        UserAccount user = new UserAccount("admin", "pw", true);
        ReflectionTestUtils.setField(user, "id", 7L);

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/flows")
                        .principal(() -> "admin")
                        .param("title", "meeting")
                        .param("durationMinutes", "60")
                        .param("participantUserIds", "1", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/5"));

        assertEquals(7L, flowService.createdByUserId);
        assertEquals("meeting", flowService.lastTitle);
        assertEquals(60, flowService.lastDurationMinutes);
        assertEquals(flowService.getReservableMinDate().atStartOfDay(), flowService.lastStartFrom);
        assertEquals(List.of(1L, 2L), flowService.lastParticipantIds);
    }

    @Test
    void create_withStepSpecs_callsCreateFlowWithStepSpecs() throws Exception {
        mockMvc.perform(post("/flows")
                        .param("title", "meeting")
                        .param("durationMinutes", "60")
                        .param("stepParticipantIds", "11", "12")
                        .param("stepReservableFromDates", "2026-10-01", "2026-11-01")
                        .param("stepReservableToDates", "2026-10-31", "2026-11-30")
                        .param("stepAllowedWeekdayMasks", "62", "60")
                        .param("stepAllowedStartMinutes", "660", "720")
                        .param("stepAllowedEndMinutes", "1080", "1020"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/6"));

        assertEquals("meeting", flowService.lastTitle);
        assertEquals(2, flowService.lastStepSpecs.size());
        assertEquals(11L, flowService.lastStepSpecs.get(0).getParticipantId());
        assertEquals(LocalDate.of(2026, 10, 1), flowService.lastStepSpecs.get(0).getReservableFromDate());
        assertEquals(62, flowService.lastStepSpecs.get(0).getAllowedWeekdaysMask());
    }

    @Test
    void updateStepConstraints_whenSuccess_thenRedirectWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/flows/10/steps/99/constraints")
                        .param("reservableFromDate", "2026-10-01")
                        .param("reservableToDate", "2026-10-31")
                        .param("allowedWeekdaysMask", "62")
                        .param("allowedStartMinute", "660")
                        .param("allowedEndMinute", "1080"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/10/edit"))
                .andExpect(flash().attributeExists("message"));

        assertEquals(10L, flowService.lastConstraintFlowId);
        assertEquals(99L, flowService.lastConstraintStepId);
        assertEquals(LocalDate.of(2026, 10, 1), flowService.lastConstraintFromDate);
        assertEquals(62, flowService.lastConstraintWeekdayMask);
    }

    @Test
    void addStep_whenSuccess_thenRedirectWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/flows/10/steps")
                        .param("participantId", "500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/10/edit"))
                .andExpect(flash().attributeExists("message"));

        assertEquals(10L, flowService.lastAppendFlowId);
        assertEquals(500L, flowService.lastAppendParticipantId);
    }

    private static class StubFlowService extends FlowService {
        private String lastTitle;
        private int lastDurationMinutes;
        private LocalDateTime lastStartFrom;
        private Long createdByUserId;
        private List<Long> lastParticipantIds;
        private List<FlowService.StepCreationSpec> lastStepSpecs = List.of();

        private Long addCandidateFlowId;
        private LocalDateTime addCandidateStartAt;
        private Long lastConstraintFlowId;
        private Long lastConstraintStepId;
        private LocalDate lastConstraintFromDate;
        private LocalDate lastConstraintToDate;
        private Integer lastConstraintWeekdayMask;
        private Integer lastConstraintStartMinute;
        private Integer lastConstraintEndMinute;
        private Long lastAppendFlowId;
        private Long lastAppendParticipantId;

        private RuntimeException createException;
        private RuntimeException selectException;

        private StubFlowService() {
            super(null, null, null, null, Clock.systemDefaultZone());
        }

        @Override
        public Long createFlow(
                String title,
                int durationMinutes,
                LocalDateTime startFrom,
                Long createdByUserId,
                List<Long> participantIds,
                List<String> externalParticipants) {
            if (createException != null) {
                throw createException;
            }
            this.lastTitle = title;
            this.lastDurationMinutes = durationMinutes;
            this.lastStartFrom = startFrom;
            this.createdByUserId = createdByUserId;
            this.lastParticipantIds = participantIds;
            return 5L;
        }

        @Override
        public Long createFlowWithStepSpecs(
                String title,
                int durationMinutes,
                LocalDateTime startFrom,
                Long createdByUserId,
                List<FlowService.StepCreationSpec> stepSpecs,
                List<String> externalParticipants) {
            this.lastTitle = title;
            this.lastDurationMinutes = durationMinutes;
            this.lastStartFrom = startFrom;
            this.createdByUserId = createdByUserId;
            this.lastStepSpecs = stepSpecs;
            return 6L;
        }

        @Override
        public void addCandidateToActiveStep(Long flowId, LocalDateTime startAt) {
            this.addCandidateFlowId = flowId;
            this.addCandidateStartAt = startAt;
        }

        @Override
        public void updateStepReservableConstraints(
                Long flowId,
                Long stepId,
                LocalDate reservableFromDate,
                LocalDate reservableToDate,
                Integer allowedWeekdaysMask,
                Integer allowedStartMinute,
                Integer allowedEndMinute) {
            this.lastConstraintFlowId = flowId;
            this.lastConstraintStepId = stepId;
            this.lastConstraintFromDate = reservableFromDate;
            this.lastConstraintToDate = reservableToDate;
            this.lastConstraintWeekdayMask = allowedWeekdaysMask;
            this.lastConstraintStartMinute = allowedStartMinute;
            this.lastConstraintEndMinute = allowedEndMinute;
        }

        @Override
        public com.example.backend_spring.domain.FlowStep appendStepWithPreviousDefaults(Long flowId, Long participantId) {
            this.lastAppendFlowId = flowId;
            this.lastAppendParticipantId = participantId;
            return new com.example.backend_spring.domain.FlowStep(flowId, 1, participantId, "dummy");
        }

        @Override
        public void selectCandidateForActiveStep(Long flowId, Long candidateId) {
            if (selectException != null) {
                throw selectException;
            }
        }
    }
}
