package com.example.backend_spring.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.backend_spring.domain.Participant;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.UserAccountRepository;
import com.example.backend_spring.security.FlowAuthorization;
import com.example.backend_spring.service.FlowService;

@Controller
@RequestMapping("/flows")
public class FlowController {

    private final FlowService flowService;
    private final UserAccountRepository userRepo;
    private final ParticipantRepository participantRepo;
    private final FlowAuthorization flowAuthorization;

    private static final DateTimeFormatter DT_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public FlowController(
            FlowService flowService,
            UserAccountRepository userRepo,
            ParticipantRepository participantRepo,
            FlowAuthorization flowAuthorization) {
        this.flowService = flowService;
        this.userRepo = userRepo;
        this.participantRepo = participantRepo;
        this.flowAuthorization = flowAuthorization;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String keyword,
            @RequestParam(required = false, defaultValue = "created_asc") String sort,
            @RequestParam(required = false, defaultValue = "week") String view,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String weekStart,
            Model model) {
        String normalizedSort = "created_desc".equals(sort) ? "created_desc" : "created_asc";
        String toggleSort = "created_asc".equals(normalizedSort) ? "created_desc" : "created_asc";
        String normalizedView = "month".equals(view) ? "month" : "week";
        var flows = flowService.listFlows(status, keyword, normalizedSort);
        LocalDate requestedCursor = parseDate(cursor != null ? cursor : weekStart);

        model.addAttribute("flows", flows);
        model.addAttribute("viewMode", normalizedView);
        if ("month".equals(normalizedView)) {
            var monthCalendar = flowService.buildMonthlyCalendarView(flows, requestedCursor);
            model.addAttribute("monthCalendar", monthCalendar);
            model.addAttribute("viewLabel", monthCalendar.getMonthLabel());
            model.addAttribute("currentCursor", monthCalendar.getMonthStart());
            model.addAttribute("prevCursor", monthCalendar.getPrevMonthStart());
            model.addAttribute("nextCursor", monthCalendar.getNextMonthStart());
            model.addAttribute("todayCursor", LocalDate.now().withDayOfMonth(1).toString());
        } else {
            var weekCalendar = flowService.buildWeeklyCalendarView(flows, requestedCursor);
            model.addAttribute("weekCalendar", weekCalendar);
            model.addAttribute("viewLabel", weekCalendar.getWeekLabel());
            model.addAttribute("currentCursor", weekCalendar.getWeekStart());
            model.addAttribute("prevCursor", weekCalendar.getPrevWeekStart());
            model.addAttribute("nextCursor", weekCalendar.getNextWeekStart());
            model.addAttribute("todayCursor", LocalDate.now().toString());
        }
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("toggleSort", toggleSort);
        return "flows/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        model.addAttribute("userParticipants", loadUserParticipants());
        return "flows/new";
    }

    @PostMapping
    public String create(
            @RequestParam String title,
            @RequestParam int durationMinutes,
            @RequestParam(required = false) String participants,
            @RequestParam(required = false) String externalParticipants,
            @RequestParam(required = false) List<Long> participantUserIds,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDateTime start = flowService.getReservableMinDate().atStartOfDay();

            String rawExternalParticipants = externalParticipants;
            if ((rawExternalParticipants == null || rawExternalParticipants.isBlank())
                    && participants != null) {
                // Backward compatibility with older form field name.
                rawExternalParticipants = participants;
            }

            List<String> externalParticipantList = Arrays.stream((rawExternalParticipants == null ? "" : rawExternalParticipants).split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            List<Long> selectedParticipantIds = participantUserIds == null ? List.of() : participantUserIds;

            Long createdByUserId = null;
            if (principal != null) {
                createdByUserId = userRepo.findByUsername(principal.getName())
                        .map(u -> u.getId())
                        .orElse(null);
            }

            Long flowId = flowService.createFlow(
                    title,
                    durationMinutes,
                    start,
                    createdByUserId,
                    selectedParticipantIds,
                    externalParticipantList);
            return "redirect:/flows/" + flowId;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/flows/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, Authentication authentication) {

        var flow = flowService.getFlow(id);
        var steps = flowService.getSteps(id);
        var activeOpt = flowService.findActiveStep(id);
        var candidates = activeOpt.map(step -> flowService.getCandidates(step.getId())).orElse(List.of());

        model.addAttribute("flow", flow);
        model.addAttribute("steps", steps);
        model.addAttribute("activeStep", activeOpt.orElse(null));
        model.addAttribute("candidates", candidates);
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        model.addAttribute("canManageFlow", flowAuthorization.canManageFlow(id, authentication));
        model.addAttribute("canOperateActiveStep", flowAuthorization.canOperateActiveStep(id, authentication));
        model.addAttribute("isAdmin", flowAuthorization.isAdmin(authentication));

        return "flows/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String editForm(@PathVariable Long id, Model model) {
        var flow = flowService.getFlow(id);
        model.addAttribute("flow", flow);
        model.addAttribute("steps", flowService.getSteps(id));
        model.addAttribute("userParticipants", loadUserParticipants());
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        return "flows/edit";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String update(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam int durationMinutes,
            RedirectAttributes redirectAttributes) {
        try {
            var flow = flowService.getFlow(id);
            flowService.updateFlow(id, title, durationMinutes, flow.getStartFrom());
            redirectAttributes.addFlashAttribute("message", "更新しました。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/flows/" + id + "/edit";
        }
        return "redirect:/flows/" + id;
    }

    @PostMapping("/{id}/steps/{stepId}/assignee")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String updateAssignee(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @RequestParam Long participantId,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.reassignStepParticipant(id, stepId, participantId);
            redirectAttributes.addFlashAttribute("message", "担当ユーザーを更新しました。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        flowService.deleteFlow(id);
        redirectAttributes.addFlashAttribute("message", "削除しました。");
        return "redirect:/flows";
    }

    @PostMapping("/{id}/candidates")
    @PreAuthorize("@flowAuthorization.canOperateActiveStep(#id, authentication)")
    public String addCandidate(
            @PathVariable Long id,
            @RequestParam String startAt,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.addCandidateToActiveStep(id, LocalDateTime.parse(startAt, DT_LOCAL));
            redirectAttributes.addFlashAttribute("message", "日時を設定しました。");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id;
    }

    @PostMapping("/{id}/candidates/{candidateId}/select")
    @PreAuthorize("@flowAuthorization.canOperateActiveStep(#id, authentication)")
    public String selectCandidate(
            @PathVariable Long id,
            @PathVariable Long candidateId,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.selectCandidateForActiveStep(id, candidateId);
            redirectAttributes.addFlashAttribute("message", "候補を確定しました。");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id;
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private List<Participant> loadUserParticipants() {
        return participantRepo.findAll().stream()
                .filter(p -> "USER".equals(p.getParticipantType()))
                .sorted(Comparator.comparing(Participant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }
}
