package com.example.backend_spring.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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
            Authentication authentication,
            Model model) {
        String normalizedSort = "created_desc".equals(sort) ? "created_desc" : "created_asc";
        String toggleSort = "created_asc".equals(normalizedSort) ? "created_desc" : "created_asc";
        String normalizedView = "month".equals(view) ? "month" : "week";
        var flows = flowService.listFlows(status, keyword, normalizedSort);
        var flowScheduleLabels = flowService.buildFlowScheduleLabels(flows);
        LocalDate requestedCursor = parseDate(cursor != null ? cursor : weekStart);

        model.addAttribute("flows", flows);
        model.addAttribute("flowScheduleLabels", flowScheduleLabels);
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
        model.addAttribute("isAdmin", flowAuthorization.isAdmin(authentication));
        model.addAttribute("currentDisplayName", resolveCurrentDisplayName(authentication));
        return "flows/list";
    }

    @GetMapping("/new")
    public String newForm(
            Model model,
            Principal principal) {
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("userParticipants", loadUserParticipants());
        return "flows/new";
    }

    @PostMapping
    public String create(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "60") int durationMinutes,
            @RequestParam(required = false) List<Long> participantUserIds,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String startTime,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDateTime start = flowService.getReservableMinDate().atStartOfDay();
            if (startDate != null && !startDate.isBlank() && startTime != null && !startTime.isBlank()) {
                start = parseDateAndTime(startDate, startTime);
            }

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
                    List.of());
            return "redirect:/flows/" + flowId;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/flows/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable Long id,
            @RequestParam(required = false) String calendarCursor,
            Model model,
            Authentication authentication,
            Principal principal) {

        var flow = flowService.getFlow(id);
        var steps = flowService.getSteps(id);
        var activeOpt = flowService.findActiveStep(id);
        var candidates = activeOpt.map(step -> flowService.getCandidates(step.getId())).orElse(List.of());
        LocalDate userCalendarCursor = parseDate(calendarCursor);

        model.addAttribute("flow", flow);
        model.addAttribute("steps", steps);
        model.addAttribute("activeStep", activeOpt.orElse(null));
        model.addAttribute("candidates", candidates);
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        model.addAttribute("canManageFlow", flowAuthorization.canManageFlow(id, authentication));
        model.addAttribute("canOperateActiveStep", flowAuthorization.canOperateActiveStep(id, authentication));
        model.addAttribute("isAdmin", flowAuthorization.isAdmin(authentication));
        addFlowParticipantWeekCalendarModel(model, id, userCalendarCursor);

        return "flows/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String editForm(
            @PathVariable Long id,
            @RequestParam(required = false) String calendarCursor,
            Model model,
            Principal principal) {
        var flow = flowService.getFlow(id);
        LocalDate userCalendarCursor = parseDate(calendarCursor);
        model.addAttribute("flow", flow);
        model.addAttribute("steps", flowService.getSteps(id));
        model.addAttribute("userParticipants", loadUserParticipants());
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        addFlowParticipantWeekCalendarModel(model, id, userCalendarCursor);
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

    @PostMapping("/{id}/steps/{stepId}/schedule")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String updateStepSchedule(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @RequestParam String startDate,
            @RequestParam String startTime,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.updateConfirmedStepSchedule(id, stepId, parseDateAndTime(startDate, startTime));
            redirectAttributes.addFlashAttribute("message", "面談設定日時を更新しました。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id + "/edit";
    }

    @PostMapping("/{id}/steps/{stepId}/finalize")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String finalizeStep(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @RequestParam Long participantId,
            @RequestParam String startDate,
            @RequestParam String startTime,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.finalizeStepAssignmentAndSchedule(
                    id,
                    stepId,
                    participantId,
                    parseDateAndTime(startDate, startTime));
            redirectAttributes.addFlashAttribute("message", "担当ユーザーと面談設定日時を確定しました。");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id + "/edit";
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

    @PostMapping("/{id}/templates")
    @PreAuthorize("@flowAuthorization.canManageFlow(#id, authentication)")
    public String saveTemplate(
            @PathVariable Long id,
            @RequestParam String templateName,
            @RequestParam(required = false) String templateDescription,
            @RequestParam(required = false, defaultValue = "PRIVATE") String visibility,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "テンプレート機能は現在保留中です。");
        return "redirect:/flows/" + id;
    }

    @PostMapping("/{id}/candidates")
    @PreAuthorize("@flowAuthorization.canOperateActiveStep(#id, authentication)")
    public String addCandidate(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String startTime,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.addCandidateToActiveStep(id, parseDateAndTime(startDate, startTime));
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

    @PostMapping("/from-template")
    public String createFromTemplate(
            @RequestParam Long templateId,
            @RequestParam(required = false) String title,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "テンプレート機能は現在保留中です。");
        return "redirect:/flows/new";
    }

    private LocalDateTime parseDateAndTime(String startDate, String startTime) {
        try {
            LocalDate date = LocalDate.parse(startDate);
            LocalTime time = LocalTime.parse(startTime);
            if (!(time.getMinute() == 0 || time.getMinute() == 30) || time.getSecond() != 0 || time.getNano() != 0) {
                throw new IllegalArgumentException("設定時間は30分刻みで指定してください。");
            }
            return date.atTime(time);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("設定日付または設定時間の形式が不正です。");
        }
    }

    private List<Participant> loadUserParticipants() {
        return participantRepo.findActiveUserParticipants().stream()
                .sorted(Comparator.comparing(Participant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private Long resolveCurrentUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepo.findByUsername(principal.getName())
                .map(u -> u.getId())
                .orElse(null);
    }

    private String resolveCurrentDisplayName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return "";
        }

        return userRepo.findByUsername(authentication.getName())
                .map(u -> participantRepo.findByParticipantTypeAndUserId("USER", u.getId())
                        .map(Participant::getDisplayName)
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(u.getUsername()))
                .orElse(authentication.getName());
    }

    private void addFlowParticipantWeekCalendarModel(Model model, Long flowId, LocalDate cursor) {
        var userWeekCalendar = flowService.buildWeeklyCalendarViewForFlowParticipants(flowId, cursor);
        model.addAttribute("userWeekCalendar", userWeekCalendar);
        model.addAttribute("userWeekLabel", userWeekCalendar.getWeekLabel());
        model.addAttribute("userWeekCurrentCursor", userWeekCalendar.getWeekStart());
        model.addAttribute("userWeekPrevCursor", userWeekCalendar.getPrevWeekStart());
        model.addAttribute("userWeekNextCursor", userWeekCalendar.getNextWeekStart());
        model.addAttribute("userWeekTodayCursor", LocalDate.now().toString());
    }
}
