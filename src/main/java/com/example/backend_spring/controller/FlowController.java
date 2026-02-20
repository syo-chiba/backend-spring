package com.example.backend_spring.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.backend_spring.service.FlowService;

@Controller
@RequestMapping("/flows")
public class FlowController {

    private final FlowService flowService;

    // datetime-local 用フォーマッタ（秒なし対策）
    private static final DateTimeFormatter DT_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    // 一覧（簡易）
    @GetMapping
    public String list() {
        return "flows/list";
    }

    // 作成画面表示
    @GetMapping("/new")
    public String newForm() {
        return "flows/new";
    }

    // 作成処理
    @PostMapping
    public String create(
            @RequestParam String title,
            @RequestParam int durationMinutes,
            @RequestParam String startFrom,
            @RequestParam String participants,
            Principal principal
    ) {

        LocalDateTime start = LocalDateTime.parse(startFrom, DT_LOCAL);

        List<String> participantList =
                Arrays.stream(participants.split("\\r?\\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        Long flowId = flowService.createFlow(
                title,
                durationMinutes,
                start,
                null,
                participantList
        );

        return "redirect:/flows/" + flowId;
    }

    // 詳細画面
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {

        var flow = flowService.getFlow(id);
        var steps = flowService.getSteps(id);
        var active = flowService.getActiveStep(id);
        var candidates = flowService.getCandidates(active.getId());

        model.addAttribute("flow", flow);
        model.addAttribute("steps", steps);
        model.addAttribute("activeStep", active);
        model.addAttribute("candidates", candidates);

        return "flows/detail";
    }

    // 候補追加
    @PostMapping("/{id}/candidates")
    public String addCandidate(
            @PathVariable Long id,
            @RequestParam String startAt
    ) {
        flowService.addCandidateToActiveStep(id, LocalDateTime.parse(startAt, DT_LOCAL));
        return "redirect:/flows/" + id;
    }
}
