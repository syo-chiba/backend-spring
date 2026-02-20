package com.example.backend_spring.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.backend_spring.repository.UserAccountRepository;
import com.example.backend_spring.service.FlowService;

@Controller
@RequestMapping("/flows")
public class FlowController {

    private final FlowService flowService;
    private final UserAccountRepository userRepo;

    private static final DateTimeFormatter DT_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public FlowController(FlowService flowService, UserAccountRepository userRepo) {
        this.flowService = flowService;
        this.userRepo = userRepo;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("flows", flowService.listFlows());
        return "flows/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("minDate", flowService.getReservableMinDate());
        model.addAttribute("maxDate", flowService.getReservableMaxDate());
        model.addAttribute("unavailableDates", flowService.getUnavailableDates());
        return "flows/new";
    }

    @PostMapping
    public String create(
            @RequestParam String title,
            @RequestParam int durationMinutes,
            @RequestParam String startFrom,
            @RequestParam String participants,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            LocalDateTime start = LocalDateTime.parse(startFrom, DT_LOCAL);

            List<String> participantList = Arrays.stream(participants.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            Long createdByUserId = null;
            if (principal != null) {
                createdByUserId = userRepo.findByUsername(principal.getName())
                        .map(u -> u.getId())
                        .orElse(null);
            }

            Long flowId = flowService.createFlow(title, durationMinutes, start, createdByUserId, participantList);
            return "redirect:/flows/" + flowId;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/flows/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {

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
        model.addAttribute("unavailableDates", flowService.getUnavailableDates());

        return "flows/detail";
    }

    @PostMapping("/{id}/candidates")
    public String addCandidate(
            @PathVariable Long id,
            @RequestParam String startAt,
            RedirectAttributes redirectAttributes) {
        try {
            flowService.addCandidateToActiveStep(id, LocalDateTime.parse(startAt, DT_LOCAL));
            redirectAttributes.addFlashAttribute("message", "候補を追加しました。");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/flows/" + id;
    }

    @PostMapping("/{id}/candidates/{candidateId}/select")
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
}
