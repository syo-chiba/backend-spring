package com.example.backend_spring.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.backend_spring.domain.UserAccount;
import com.example.backend_spring.repository.UserAccountRepository;
import com.example.backend_spring.service.FlowService;

@ExtendWith(MockitoExtension.class)
class FlowControllerTest {

    @Mock
    private FlowService flowService;

    @Mock
    private UserAccountRepository userRepo;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FlowController controller = new FlowController(flowService, userRepo);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void create_whenServiceValidationFails_thenRedirectToNewWithError() throws Exception {
        when(flowService.createFlow(anyString(), anyInt(), any(LocalDateTime.class), any(), anyList()))
                .thenThrow(new IllegalArgumentException("validation failed"));

        mockMvc.perform(post("/flows")
                        .param("title", "面談")
                        .param("durationMinutes", "60")
                        .param("startFrom", "2026-02-22T10:00")
                        .param("participants", "Aさん\nBさん"))
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
                .andExpect(flash().attribute("message", "候補を追加しました。"));

        verify(flowService).addCandidateToActiveStep(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void selectCandidate_whenServiceFails_thenRedirectWithErrorMessage() throws Exception {
        doThrow(new IllegalStateException("active step missing"))
                .when(flowService).selectCandidateForActiveStep(10L, 99L);

        mockMvc.perform(post("/flows/10/candidates/99/select"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/10"))
                .andExpect(flash().attribute("error", "active step missing"));
    }

    @Test
    void create_withPrincipal_mapsCreatedByUserId() throws Exception {
        UserAccount user = new UserAccount("admin", "pw", true, "ROLE_ADMIN");
        ReflectionTestUtils.setField(user, "id", 7L);

        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(flowService.createFlow(anyString(), anyInt(), any(LocalDateTime.class), any(), anyList())).thenReturn(5L);

        mockMvc.perform(post("/flows")
                        .principal(() -> "admin")
                        .param("title", "面談")
                        .param("durationMinutes", "60")
                        .param("startFrom", "2026-02-22T10:00")
                        .param("participants", "Aさん\nBさん"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flows/5"));

        verify(flowService).createFlow(anyString(), anyInt(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(7L), anyList());
    }
}
