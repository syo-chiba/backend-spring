package com.example.backend_spring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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
                        .param("participantUserIds", "1")
                        .param("externalParticipants", "A\nB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/new"))
                .andExpect(flash().attribute("error", "validation failed"));
    }

    @Test
    void addCandidate_whenSuccess_thenRedirectWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/flows/10/candidates")
                        .param("startAt", "2026-02-23T10:00"))
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
                        .param("participantUserIds", "1", "2")
                        .param("externalParticipants", "A\nB"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/5"));

        assertEquals(7L, flowService.createdByUserId);
        assertEquals("meeting", flowService.lastTitle);
        assertEquals(60, flowService.lastDurationMinutes);
        assertEquals(flowService.getReservableMinDate().atStartOfDay(), flowService.lastStartFrom);
        assertEquals(List.of(1L, 2L), flowService.lastParticipantIds);
        assertTrue(flowService.lastParticipants.containsAll(List.of("A", "B")));
    }

    private static class StubFlowService extends FlowService {
        private String lastTitle;
        private int lastDurationMinutes;
        private LocalDateTime lastStartFrom;
        private Long createdByUserId;
        private List<String> lastParticipants;
        private List<Long> lastParticipantIds;

        private Long addCandidateFlowId;
        private LocalDateTime addCandidateStartAt;

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
            this.lastParticipants = externalParticipants;
            return 5L;
        }

        @Override
        public void addCandidateToActiveStep(Long flowId, LocalDateTime startAt) {
            this.addCandidateFlowId = flowId;
            this.addCandidateStartAt = startAt;
        }

        @Override
        public void selectCandidateForActiveStep(Long flowId, Long candidateId) {
            if (selectException != null) {
                throw selectException;
            }
        }
    }
}
