package com.example.backend_spring.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend_spring.domain.Flow;
import com.example.backend_spring.domain.FlowStep;
import com.example.backend_spring.domain.FlowTemplate;
import com.example.backend_spring.domain.FlowTemplateStep;
import com.example.backend_spring.domain.Participant;
import com.example.backend_spring.domain.StepCandidate;
import com.example.backend_spring.repository.FlowRepository;
import com.example.backend_spring.repository.FlowStepRepository;
import com.example.backend_spring.repository.FlowTemplateRepository;
import com.example.backend_spring.repository.FlowTemplateStepRepository;
import com.example.backend_spring.repository.ParticipantRepository;
import com.example.backend_spring.repository.StepCandidateRepository;

@Service
public class FlowService {

    private static final List<String> BLOCKING_CANDIDATE_STATUSES = List.of("PROPOSED", "SELECTED");
    private static final List<String> WEEKDAY_HEADERS = List.of("\u65E5", "\u6708", "\u706B", "\u6C34", "\u6728", "\u91D1", "\u571F");
    private static final DateTimeFormatter TIME_LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter SCHEDULE_LABEL_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final double HOUR_HEIGHT_PX = 44.0;

    private final FlowRepository flowRepo;
    private final FlowStepRepository stepRepo;
    private final FlowTemplateRepository templateRepo;
    private final FlowTemplateStepRepository templateStepRepo;
    private final StepCandidateRepository candidateRepo;
    private final ParticipantRepository participantRepo;
    private final Clock clock;

    public FlowService(
            FlowRepository flowRepo,
            FlowStepRepository stepRepo,
            FlowTemplateRepository templateRepo,
            FlowTemplateStepRepository templateStepRepo,
            StepCandidateRepository candidateRepo,
            ParticipantRepository participantRepo,
            Clock clock) {
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
        this.templateRepo = templateRepo;
        this.templateStepRepo = templateStepRepo;
        this.candidateRepo = candidateRepo;
        this.participantRepo = participantRepo;
        this.clock = clock;
    }

    // Backward-compatible constructor for existing tests that directly instantiate FlowService.
    public FlowService(
            FlowRepository flowRepo,
            FlowStepRepository stepRepo,
            StepCandidateRepository candidateRepo,
            ParticipantRepository participantRepo,
            Clock clock) {
        this(flowRepo, stepRepo, null, null, candidateRepo, participantRepo, clock);
    }

    @Transactional
    public Long createFlow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId, List<String> participants) {
        return createFlow(title, durationMinutes, startFrom, createdByUserId, List.of(), participants);
    }

    @Transactional
    public Long createFlow(
            String title,
            int durationMinutes,
            LocalDateTime startFrom,
            Long createdByUserId,
            List<Long> participantIds,
            List<String> externalParticipants) {
        validateReservableDateTime(startFrom, "\u8abf\u6574\u958b\u59cb\u65e5\u6642");

        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException("担当ユーザーを1名以上選択してください。");
        }

        Flow flow = new Flow(title, durationMinutes, startFrom, createdByUserId);
        Flow saved = flowRepo.save(flow);

        List<FlowStep> steps = new ArrayList<>();
        int order = 1;
        Set<Long> seenParticipantIds = new java.util.LinkedHashSet<>();
        List<Long> safeParticipantIds = participantIds == null ? List.of() : participantIds;
        for (Long participantId : safeParticipantIds) {
            if (participantId == null || !seenParticipantIds.add(participantId)) {
                continue;
            }
            Participant participant = participantRepo.findById(participantId)
                    .orElseThrow(() -> new IllegalArgumentException("参加者が見つかりません。participantId=" + participantId));

            if (!"USER".equals(participant.getParticipantType())) {
                throw new IllegalArgumentException("担当ユーザーとして選択できない参加者です。participantId=" + participantId);
            }

            FlowStep step = new FlowStep(saved.getId(), order, participant.getId(), participant.getDisplayName());
            if (order == 1) {
                step.activate();
            }
            steps.add(step);
            order++;
        }

        List<String> safeExternalParticipants = externalParticipants == null ? List.of() : externalParticipants;
        for (String p : safeExternalParticipants) {
            String name = p == null ? "" : p.trim();
            if (name.isEmpty()) {
                continue;
            }

            Participant participant = resolveOrCreateParticipant(name);
            FlowStep step = new FlowStep(saved.getId(), order, participant.getId(), participant.getDisplayName());
            if (order == 1) {
                step.activate();
            }
            steps.add(step);
            order++;
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException("\u53c2\u52a0\u8005\u304c1\u4eba\u3082\u3044\u307e\u305b\u3093\u3002");
        }

        stepRepo.saveAll(steps);
        return saved.getId();
    }

    @Transactional
    public void reassignStepParticipant(Long flowId, Long stepId, Long participantId) {
        FlowStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("ステップが見つかりません。stepId=" + stepId));
        if (!step.getFlowId().equals(flowId)) {
            throw new IllegalArgumentException("指定されたフローのステップではありません。");
        }

        Participant participant = participantRepo.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("参加者が見つかりません。participantId=" + participantId));
        if (!"USER".equals(participant.getParticipantType())) {
            throw new IllegalArgumentException("担当ユーザーとして設定できない参加者です。");
        }

        step.reassignParticipant(participant.getId(), participant.getDisplayName());
        stepRepo.save(step);
    }

    public List<Flow> listFlows(String status, String keyword, String sort) {
        Comparator<Flow> comparator;
        if ("created_asc".equals(sort)) {
            comparator = Comparator.comparing(Flow::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Flow::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(Flow::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Flow::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        return flowRepo.findAll().stream()
                .filter(flow -> normalizedStatus.isEmpty() || normalizedStatus.equals(flow.getStatus()))
                .filter(flow -> normalizedKeyword.isEmpty() ||
                        (flow.getTitle() != null && flow.getTitle().toLowerCase().contains(normalizedKeyword)))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public List<Flow> listFlows() {
        return listFlows(null, null, "created_asc");
    }

    public List<FlowTemplate> listAvailableTemplates(Long userId) {
        if (templateRepo == null) {
            return List.of();
        }
        if (userId == null) {
            return List.of();
        }
        return templateRepo.findAvailableForUser(userId);
    }

    @Transactional
    public Long createTemplateFromFlow(
            Long flowId,
            String templateName,
            String description,
            String visibility,
            Long createdByUserId) {
        if (createdByUserId == null) {
            throw new IllegalArgumentException("テンプレート作成ユーザーが不正です。");
        }
        requireTemplateFeatureAvailable();

        Flow flow = getFlow(flowId);
        List<FlowStep> steps = getSteps(flowId);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("ステップが存在しないためテンプレート化できません。");
        }

        String normalizedName = templateName == null ? "" : templateName.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("テンプレート名を入力してください。");
        }

        String normalizedVisibility = "SHARED".equals(visibility) ? "SHARED" : "PRIVATE";
        String normalizedDescription = description == null ? null : description.trim();

        FlowTemplate template = templateRepo.save(new FlowTemplate(
                normalizedName,
                normalizedDescription,
                flow.getDurationMinutes(),
                createdByUserId,
                normalizedVisibility));

        List<FlowTemplateStep> templateSteps = new ArrayList<>();
        for (FlowStep step : steps) {
            Participant participant = resolveParticipantForTemplateSnapshot(step.getParticipantId(), step.getParticipantName());
            templateSteps.add(new FlowTemplateStep(
                    template.getId(),
                    step.getStepOrder(),
                    participant.getId(),
                    participant.getParticipantType(),
                    participant.getDisplayName()));
        }
        templateStepRepo.saveAll(templateSteps);
        return template.getId();
    }

    @Transactional
    public Long createFlowFromTemplate(Long templateId, String title, Long createdByUserId) {
        if (createdByUserId == null) {
            throw new IllegalArgumentException("ログインユーザーが見つかりません。");
        }
        requireTemplateFeatureAvailable();

        FlowTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("テンプレートが見つかりません。templateId=" + templateId));
        if (!template.isActive()) {
            throw new IllegalArgumentException("無効なテンプレートです。");
        }
        if (!createdByUserId.equals(template.getCreatedByUserId()) && !"SHARED".equals(template.getVisibility())) {
            throw new IllegalArgumentException("このテンプレートを利用する権限がありません。");
        }

        List<FlowTemplateStep> templateSteps = templateStepRepo.findByTemplateIdOrderByStepOrderAsc(templateId);
        if (templateSteps.isEmpty()) {
            throw new IllegalArgumentException("テンプレートにステップがありません。");
        }

        String flowTitle = title == null || title.isBlank() ? template.getName() : title.trim();
        LocalDateTime startFrom = getReservableMinDate().atStartOfDay();

        Flow flow = flowRepo.save(new Flow(
                flowTitle,
                template.getDurationMinutes(),
                startFrom,
                createdByUserId,
                template.getId(),
                template.getName()));

        List<FlowStep> steps = new ArrayList<>();
        int order = 1;
        for (FlowTemplateStep templateStep : templateSteps) {
            Participant participant = resolveParticipantForTemplateStep(templateStep);
            FlowStep step = new FlowStep(
                    flow.getId(),
                    order,
                    participant.getId(),
                    participant.getDisplayName());
            if (order == 1) {
                step.activate();
            }
            steps.add(step);
            order++;
        }
        stepRepo.saveAll(steps);

        template.markUsedNow();
        templateRepo.save(template);
        return flow.getId();
    }

    public Map<Long, String> buildFlowScheduleLabels(List<Flow> flows) {
        Map<Long, String> labels = new HashMap<>();
        if (flows == null || flows.isEmpty()) {
            return labels;
        }

        for (Flow flow : flows) {
            if (flow == null || flow.getId() == null) {
                continue;
            }

            List<FlowStep> steps = stepRepo.findByFlowIdOrderByStepOrder(flow.getId());
            Optional<FlowStep> confirmed = steps.stream()
                    .filter(s -> s.getConfirmedStartAt() != null && s.getConfirmedEndAt() != null)
                    .min(Comparator.comparing(FlowStep::getStepOrder));

            if (confirmed.isPresent()) {
                FlowStep step = confirmed.get();
                labels.put(flow.getId(), formatScheduleLabel(step.getConfirmedStartAt(), step.getConfirmedEndAt()));
                continue;
            }

            FlowStep active = stepRepo.findByFlowIdAndStepOrder(flow.getId(), flow.getCurrentStepOrder());
            if (active != null && active.getId() != null) {
                Optional<StepCandidate> candidate = candidateRepo.findByFlowStepIdOrderByStartAtAsc(active.getId()).stream()
                        .filter(c -> BLOCKING_CANDIDATE_STATUSES.contains(c.getStatus()))
                        .findFirst();
                if (candidate.isPresent()) {
                    StepCandidate c = candidate.get();
                    labels.put(flow.getId(), formatScheduleLabel(c.getStartAt(), c.getEndAt()));
                }
            }
        }
        return labels;
    }

    public Flow getFlow(Long id) {
        return flowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
    }

    @Transactional
    public void updateFlow(Long id, String title, int durationMinutes, LocalDateTime startFrom) {
        validateReservableDateTime(startFrom, "調整開始日時");
        Flow flow = getFlow(id);
        flow.updateBasics(title, durationMinutes, startFrom);
        flowRepo.save(flow);
    }

    @Transactional
    public void updateConfirmedStepSchedule(Long flowId, Long stepId, LocalDateTime startAt) {
        validateReservableDateTime(startAt, "面談設定日時");

        Flow flow = getFlow(flowId);
        FlowStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("ステップが見つかりません。stepId=" + stepId));
        if (!step.getFlowId().equals(flowId)) {
            throw new IllegalArgumentException("指定されたフローのステップではありません。");
        }
        if (step.getConfirmedStartAt() == null || step.getConfirmedEndAt() == null) {
            throw new IllegalArgumentException("未設定のステップはこの画面から変更できません。");
        }

        LocalDateTime endAt = startAt.plusMinutes(flow.getDurationMinutes());
        assertNoOwnerTimeOverlap(flow, startAt, endAt, stepId);
        step.confirm(startAt, endAt);
        stepRepo.save(step);
    }

    @Transactional
    public void finalizeStepAssignmentAndSchedule(
            Long flowId,
            Long stepId,
            Long participantId,
            LocalDateTime startAt) {
        validateReservableDateTime(startAt, "面談設定日時");

        Flow flow = getFlow(flowId);
        FlowStep step = stepRepo.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("ステップが見つかりません。stepId=" + stepId));
        if (!step.getFlowId().equals(flowId)) {
            throw new IllegalArgumentException("指定されたフローのステップではありません。");
        }

        Participant participant = participantRepo.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("参加者が見つかりません。participantId=" + participantId));
        if (!"USER".equals(participant.getParticipantType())) {
            throw new IllegalArgumentException("担当ユーザーとして設定できない参加者です。");
        }

        LocalDateTime endAt = startAt.plusMinutes(flow.getDurationMinutes());
        assertNoOwnerTimeOverlap(flow, startAt, endAt, stepId);

        step.reassignParticipant(participant.getId(), participant.getDisplayName());
        step.confirm(startAt, endAt);
        stepRepo.save(step);
    }

    @Transactional
    public void deleteFlow(Long id) {
        Flow flow = getFlow(id);
        flowRepo.delete(flow);
    }

    public List<FlowStep> getSteps(Long flowId) {
        return withParticipantNames(stepRepo.findByFlowIdOrderByStepOrder(flowId));
    }

    public Optional<FlowStep> findActiveStep(Long flowId) {
        Flow flow = getFlow(flowId);

        FlowStep step = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (step == null || !"ACTIVE".equals(step.getStatus())) {
            return Optional.empty();
        }
        applyParticipantName(step);
        return Optional.of(step);
    }

    public FlowStep getActiveStep(Long flowId) {
        return findActiveStep(flowId)
                .orElseThrow(() -> new IllegalStateException("\u73fe\u5728\u30b9\u30c6\u30c3\u30d7\u304cACTIVE\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002flowId=" + flowId));
    }

    public List<StepCandidate> getCandidates(Long flowStepId) {
        return candidateRepo.findByFlowStepIdOrderByStartAtAsc(flowStepId);
    }

    public LocalDate getReservableMinDate() {
        return LocalDate.now(clock).plusDays(1);
    }

    public LocalDate getReservableMaxDate() {
        return getReservableMinDate().plusMonths(3);
    }

    public WeeklyCalendarView buildWeeklyCalendarView(List<Flow> flows, LocalDate cursorDate) {
        LocalDate baseDate = cursorDate == null ? LocalDate.now(clock) : cursorDate;
        LocalDate weekStart = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate today = LocalDate.now(clock);

        List<CalendarSourceEvent> events = collectCalendarSourceEvents(flows);
        List<WeeklyCalendarDay> days = new ArrayList<>(7);

        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            List<CalendarEvent> dayEvents = events.stream()
                    .filter(event -> day.equals(event.startAt.toLocalDate()))
                    .sorted(Comparator.comparing(event -> event.startAt))
                    .map(this::toWeeklyCalendarEvent)
                    .collect(Collectors.toList());

            days.add(new WeeklyCalendarDay(
                    formatWeekDayLabel(day),
                    day.equals(today),
                    dayEvents));
        }

        return new WeeklyCalendarView(
                weekStart.toString(),
                weekStart.minusWeeks(1).toString(),
                weekStart.plusWeeks(1).toString(),
                DATE_LABEL_FORMAT.format(weekStart) + " - " + DATE_LABEL_FORMAT.format(weekEnd),
                buildHourLabels(),
                days);
    }

    public MonthlyCalendarView buildMonthlyCalendarView(List<Flow> flows, LocalDate cursorDate) {
        LocalDate baseDate = cursorDate == null ? LocalDate.now(clock) : cursorDate;
        LocalDate monthStart = baseDate.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate gridEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        LocalDate today = LocalDate.now(clock);

        List<CalendarSourceEvent> events = collectCalendarSourceEvents(flows);
        List<MonthlyCalendarWeek> weeks = new ArrayList<>();

        LocalDate cursor = gridStart;
        while (!cursor.isAfter(gridEnd)) {
            List<MonthlyCalendarDay> days = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate day = cursor.plusDays(i);
                List<MonthlyCalendarEvent> dayEvents = events.stream()
                        .filter(event -> day.equals(event.startAt.toLocalDate()))
                        .sorted(Comparator.comparing(event -> event.startAt))
                        .map(this::toMonthlyCalendarEvent)
                        .collect(Collectors.toList());

                days.add(new MonthlyCalendarDay(
                        formatMonthDayLabel(day, monthStart),
                        day.getMonth().equals(monthStart.getMonth()),
                        day.equals(today),
                        dayEvents));
            }
            weeks.add(new MonthlyCalendarWeek(days));
            cursor = cursor.plusWeeks(1);
        }

        String monthLabel = monthStart.getYear() + "\u5E74" + monthStart.getMonthValue() + "\u6708";
        return new MonthlyCalendarView(
                monthLabel,
                monthStart.toString(),
                monthStart.minusMonths(1).toString(),
                monthStart.plusMonths(1).toString(),
                WEEKDAY_HEADERS,
                weeks);
    }

    @Transactional
    public void addCandidateToActiveStep(Long flowId, LocalDateTime startAt) {
        Flow flow = getFlow(flowId);
        FlowStep active = getActiveStep(flowId);

        validateReservableDateTime(startAt, "\u5019\u88dc\u65e5\u6642");

        if (startAt.isBefore(flow.getStartFrom())) {
            throw new IllegalArgumentException("\u5019\u88dc\u65e5\u6642\u304c\u958b\u59cb\u53ef\u80fd\u65e5\u6642\u3088\u308a\u524d\u3067\u3059\u3002startAt=" + startAt + ", startFrom=" + flow.getStartFrom());
        }

        LocalDateTime endAt = startAt.plusMinutes(flow.getDurationMinutes());

        assertNoOwnerTimeOverlap(flow, startAt, endAt, null);

        StepCandidate created = candidateRepo.save(new StepCandidate(active.getId(), startAt, endAt));
        // New behavior: when a date/time is entered, it is fixed immediately.
        selectCandidateForActiveStep(flowId, created.getId());
    }

    @Transactional
    public void selectCandidateForActiveStep(Long flowId, Long candidateId) {
        Flow flow = getFlow(flowId);
        FlowStep active = getActiveStep(flowId);

        StepCandidate candidate = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("\u5019\u88dc\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002candidateId=" + candidateId));

        if (!candidate.getFlowStepId().equals(active.getId())) {
            throw new IllegalArgumentException("ACTIVE\u30b9\u30c6\u30c3\u30d7\u306e\u5019\u88dc\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002candidateId=" + candidateId);
        }

        candidate.select();
        active.confirm(candidate.getStartAt(), candidate.getEndAt());

        List<StepCandidate> sameStepCandidates = candidateRepo.findByFlowStepIdOrderByStartAtAsc(active.getId());
        for (StepCandidate each : sameStepCandidates) {
            if (!each.getId().equals(candidate.getId()) && "PROPOSED".equals(each.getStatus())) {
                each.reject();
                candidateRepo.save(each);
            }
        }

        stepRepo.save(active);
        candidateRepo.save(candidate);

        flow.moveToNextStep();
        FlowStep next = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (next == null) {
            flow.markDone();
        } else {
            next.activate();
            stepRepo.save(next);
        }

        flowRepo.save(flow);
    }

    private List<String> buildHourLabels() {
        List<String> labels = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            labels.add(Integer.toString(hour));
        }
        return labels;
    }

    private String formatWeekDayLabel(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth() + "(" + WEEKDAY_HEADERS.get(date.getDayOfWeek().getValue() % 7) + ")";
    }

    private String formatMonthDayLabel(LocalDate day, LocalDate monthStart) {
        if (day.getMonth().equals(monthStart.getMonth())) {
            return Integer.toString(day.getDayOfMonth());
        }
        return day.getMonthValue() + "/" + day.getDayOfMonth();
    }

    private List<CalendarSourceEvent> collectCalendarSourceEvents(List<Flow> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }

        List<CalendarSourceEvent> events = new ArrayList<>();
        Map<Long, String> participantNameCache = new HashMap<>();
        for (Flow flow : flows) {
            if (flow == null || flow.getId() == null) {
                continue;
            }

            List<FlowStep> steps = withParticipantNames(stepRepo.findByFlowIdOrderByStepOrder(flow.getId()));
            for (FlowStep step : steps) {
                if ("CONFIRMED".equals(step.getStatus())
                        && step.getConfirmedStartAt() != null
                        && step.getConfirmedEndAt() != null) {
                    String participantName = resolveParticipantDisplayName(step, participantNameCache);
                    events.add(new CalendarSourceEvent(
                            step.getConfirmedStartAt(),
                            step.getConfirmedEndAt(),
                            "CONFIRMED",
                            buildEventTitle(flow, participantName),
                            buildTooltip(flow, participantName, "CONFIRMED", step.getConfirmedStartAt(), step.getConfirmedEndAt()),
                            "/flows/" + flow.getId()));
                }

                if (!"ACTIVE".equals(step.getStatus()) || step.getId() == null) {
                    continue;
                }

                List<StepCandidate> candidates = candidateRepo.findByFlowStepIdOrderByStartAtAsc(step.getId());
                for (StepCandidate candidate : candidates) {
                    if (!BLOCKING_CANDIDATE_STATUSES.contains(candidate.getStatus())
                            || candidate.getStartAt() == null
                            || candidate.getEndAt() == null) {
                        continue;
                    }
                    String participantName = resolveParticipantDisplayName(step, participantNameCache);

                    events.add(new CalendarSourceEvent(
                            candidate.getStartAt(),
                            candidate.getEndAt(),
                            candidate.getStatus(),
                            buildEventTitle(flow, participantName),
                            buildTooltip(flow, participantName, candidate.getStatus(), candidate.getStartAt(), candidate.getEndAt()),
                            "/flows/" + flow.getId()));
                }
            }
        }

        return events.stream()
                .sorted(Comparator.comparing((CalendarSourceEvent event) -> event.startAt)
                        .thenComparing(event -> event.endAt))
                .collect(Collectors.toList());
    }

    private String buildEventTitle(Flow flow, String participantName) {
        String baseTitle = "#" + flow.getId();
        if (flow.getTitle() != null && !flow.getTitle().isBlank()) {
            baseTitle += " " + flow.getTitle();
        }
        if (participantName != null && !participantName.isBlank()) {
            baseTitle += " / " + participantName;
        }
        return baseTitle;
    }

    private Participant resolveOrCreateParticipant(String displayName) {
        return participantRepo.findByParticipantTypeAndDisplayName("USER", displayName)
                .or(() -> participantRepo.findByParticipantTypeAndDisplayName("EXTERNAL", displayName))
                .orElseGet(() -> participantRepo.save(new Participant("EXTERNAL", null, displayName)));
    }

    private Participant resolveParticipantForTemplateSnapshot(Long participantId, String participantName) {
        if (participantId != null) {
            return participantRepo.findById(participantId)
                    .orElseGet(() -> resolveOrCreateParticipant(participantName == null ? "Unknown" : participantName));
        }
        return resolveOrCreateParticipant(participantName == null ? "Unknown" : participantName);
    }

    private Participant resolveParticipantForTemplateStep(FlowTemplateStep templateStep) {
        if (templateStep.getParticipantId() != null) {
            Optional<Participant> existing = participantRepo.findById(templateStep.getParticipantId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        String name = templateStep.getParticipantNameSnapshot();
        if ("USER".equals(templateStep.getParticipantTypeSnapshot())) {
            return participantRepo.findByParticipantTypeAndDisplayName("USER", name)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "テンプレートのユーザー参加者が見つかりません: " + name));
        }
        return participantRepo.findByParticipantTypeAndDisplayName("EXTERNAL", name)
                .orElseGet(() -> participantRepo.save(new Participant("EXTERNAL", null, name)));
    }

    private String resolveParticipantDisplayName(FlowStep step, Map<Long, String> cache) {
        if (step.getParticipantId() != null) {
            if (cache.containsKey(step.getParticipantId())) {
                return cache.get(step.getParticipantId());
            }

            String name = participantRepo.findById(step.getParticipantId())
                    .map(Participant::getDisplayName)
                    .orElse("Unknown");
            cache.put(step.getParticipantId(), name);
            return name;
        }
        return "Unknown";
    }

    private List<FlowStep> withParticipantNames(List<FlowStep> steps) {
        for (FlowStep step : steps) {
            applyParticipantName(step);
        }
        return steps;
    }

    private void applyParticipantName(FlowStep step) {
        if (step.getParticipantId() == null) {
            step.setParticipantName("Unknown");
            return;
        }
        String name = participantRepo.findById(step.getParticipantId())
                .map(Participant::getDisplayName)
                .orElse("Unknown");
        step.setParticipantName(name);
    }

    private String buildTooltip(Flow flow, String participantName, String status, LocalDateTime startAt, LocalDateTime endAt) {
        return buildEventTitle(flow, participantName)
                + " [" + toStatusLabelJa(status) + "] "
                + startAt.toString()
                + " - "
                + endAt.toString();
    }

    private String toStatusLabelJa(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        switch (status) {
            case "IN_PROGRESS":
                return "進行中";
            case "DONE":
                return "完了";
            case "PENDING":
                return "未着手";
            case "ACTIVE":
                return "対応中";
            case "CONFIRMED":
                return "確定";
            case "SKIPPED":
                return "スキップ";
            case "PROPOSED":
                return "候補";
            case "SELECTED":
                return "選択済み";
            case "REJECTED":
                return "却下";
            default:
                return status;
        }
    }

    private CalendarEvent toWeeklyCalendarEvent(CalendarSourceEvent event) {
        long minutesFromMidnight = event.startAt.getHour() * 60L + event.startAt.getMinute();
        long rawDurationMinutes = Duration.between(event.startAt, event.endAt).toMinutes();
        long durationMinutes = Math.max(15L, rawDurationMinutes <= 0 ? 15L : rawDurationMinutes);
        double topPx = (minutesFromMidnight / 60.0) * HOUR_HEIGHT_PX;
        double heightPx = Math.max(14.0, (durationMinutes / 60.0) * HOUR_HEIGHT_PX);

        String style = String.format(Locale.ROOT, "top: %.2fpx; height: %.2fpx;", topPx, heightPx);
        return new CalendarEvent(
                event.title,
                formatTimeRange(event.startAt, event.endAt),
                event.tooltip,
                event.status,
                toTypeClass(event.status),
                style,
                event.detailUrl);
    }

    private MonthlyCalendarEvent toMonthlyCalendarEvent(CalendarSourceEvent event) {
        return new MonthlyCalendarEvent(
                event.title,
                formatTimeRange(event.startAt, event.endAt),
                event.tooltip,
                event.status,
                toTypeClass(event.status),
                event.detailUrl);
    }

    private String formatTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        return TIME_LABEL_FORMAT.format(startAt) + " - " + TIME_LABEL_FORMAT.format(endAt);
    }

    private String formatScheduleLabel(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            return "-";
        }
        return SCHEDULE_LABEL_FORMAT.format(startAt) + " ~ " + TIME_LABEL_FORMAT.format(endAt);
    }

    private String toTypeClass(String status) {
        if ("CONFIRMED".equals(status)) {
            return "calendar-event-confirmed";
        }
        if ("SELECTED".equals(status)) {
            return "calendar-event-selected";
        }
        return "calendar-event-proposed";
    }

    private void assertNoOwnerTimeOverlap(Flow flow, LocalDateTime newStartAt, LocalDateTime newEndAt, Long excludeStepId) {
        Long excludeFlowStepId = excludeStepId;
        var candidateConflict = candidateRepo.findFirstTimeConflictForOwner(
                flow.getCreatedByUserId(),
                excludeFlowStepId,
                newStartAt,
                newEndAt);
        if (candidateConflict.isPresent()) {
            var c = candidateConflict.get();
            throw new IllegalArgumentException(
                    "\u5019\u88dc\u6642\u9593\u304c\u91cd\u8907\u3057\u3066\u3044\u307e\u3059: \u30d5\u30ed\u30fc=" + c.getFlowTitle()
                            + ", \u53c2\u52a0\u8005=" + c.getParticipantName()
                            + ", \u65e2\u5b58=" + c.getStartAt() + " - " + c.getEndAt());
        }

        var confirmedConflict = stepRepo.findFirstConfirmedConflictForOwner(
                flow.getCreatedByUserId(), excludeStepId, newStartAt, newEndAt);
        if (confirmedConflict.isPresent()) {
            var c = confirmedConflict.get();
            throw new IllegalArgumentException(
                    "\u78ba\u5b9a\u6e08\u307f\u6642\u9593\u304c\u91cd\u8907\u3057\u3066\u3044\u307e\u3059: \u30d5\u30ed\u30fc=" + c.getFlowTitle()
                            + ", \u53c2\u52a0\u8005=" + c.getParticipantName()
                            + ", \u65e2\u5b58=" + c.getStartAt() + " - " + c.getEndAt());
        }
    }

    private void validateReservableDateTime(LocalDateTime dateTime, String label) {
        LocalDate minDate = getReservableMinDate();
        LocalDate maxDate = getReservableMaxDate();

        LocalDate target = dateTime.toLocalDate();
        if (target.isBefore(minDate) || target.isAfter(maxDate)) {
            throw new IllegalArgumentException(label + "\u306f\u7fcc\u65e5\u304b\u30893\u30f6\u6708\u4ee5\u5185\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002value=" + dateTime);
        }
    }

    private static class CalendarSourceEvent {
        private final LocalDateTime startAt;
        private final LocalDateTime endAt;
        private final String status;
        private final String title;
        private final String tooltip;
        private final String detailUrl;

        private CalendarSourceEvent(
                LocalDateTime startAt,
                LocalDateTime endAt,
                String status,
                String title,
                String tooltip,
                String detailUrl) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.status = status;
            this.title = title;
            this.tooltip = tooltip;
            this.detailUrl = detailUrl;
        }
    }

    private void requireTemplateFeatureAvailable() {
        if (templateRepo == null || templateStepRepo == null) {
            throw new IllegalStateException("Template repositories are not configured.");
        }
    }

    public static class WeeklyCalendarView {
        private final String weekStart;
        private final String prevWeekStart;
        private final String nextWeekStart;
        private final String weekLabel;
        private final List<String> hourLabels;
        private final List<WeeklyCalendarDay> days;

        public WeeklyCalendarView(
                String weekStart,
                String prevWeekStart,
                String nextWeekStart,
                String weekLabel,
                List<String> hourLabels,
                List<WeeklyCalendarDay> days) {
            this.weekStart = weekStart;
            this.prevWeekStart = prevWeekStart;
            this.nextWeekStart = nextWeekStart;
            this.weekLabel = weekLabel;
            this.hourLabels = hourLabels;
            this.days = days;
        }

        public String getWeekStart() {
            return weekStart;
        }

        public String getPrevWeekStart() {
            return prevWeekStart;
        }

        public String getNextWeekStart() {
            return nextWeekStart;
        }

        public String getWeekLabel() {
            return weekLabel;
        }

        public List<String> getHourLabels() {
            return hourLabels;
        }

        public List<WeeklyCalendarDay> getDays() {
            return days;
        }
    }

    public static class WeeklyCalendarDay {
        private final String label;
        private final boolean today;
        private final List<CalendarEvent> events;

        public WeeklyCalendarDay(String label, boolean today, List<CalendarEvent> events) {
            this.label = label;
            this.today = today;
            this.events = events;
        }

        public String getLabel() {
            return label;
        }

        public boolean isToday() {
            return today;
        }

        public List<CalendarEvent> getEvents() {
            return events;
        }
    }

    public static class CalendarEvent {
        private final String title;
        private final String timeLabel;
        private final String tooltip;
        private final String status;
        private final String typeClass;
        private final String style;
        private final String detailUrl;

        public CalendarEvent(
                String title,
                String timeLabel,
                String tooltip,
                String status,
                String typeClass,
                String style,
                String detailUrl) {
            this.title = title;
            this.timeLabel = timeLabel;
            this.tooltip = tooltip;
            this.status = status;
            this.typeClass = typeClass;
            this.style = style;
            this.detailUrl = detailUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getTimeLabel() {
            return timeLabel;
        }

        public String getTooltip() {
            return tooltip;
        }

        public String getStatus() {
            return status;
        }

        public String getTypeClass() {
            return typeClass;
        }

        public String getStyle() {
            return style;
        }

        public String getDetailUrl() {
            return detailUrl;
        }
    }

    public static class MonthlyCalendarView {
        private final String monthLabel;
        private final String monthStart;
        private final String prevMonthStart;
        private final String nextMonthStart;
        private final List<String> weekdayHeaders;
        private final List<MonthlyCalendarWeek> weeks;

        public MonthlyCalendarView(
                String monthLabel,
                String monthStart,
                String prevMonthStart,
                String nextMonthStart,
                List<String> weekdayHeaders,
                List<MonthlyCalendarWeek> weeks) {
            this.monthLabel = monthLabel;
            this.monthStart = monthStart;
            this.prevMonthStart = prevMonthStart;
            this.nextMonthStart = nextMonthStart;
            this.weekdayHeaders = weekdayHeaders;
            this.weeks = weeks;
        }

        public String getMonthLabel() {
            return monthLabel;
        }

        public String getMonthStart() {
            return monthStart;
        }

        public String getPrevMonthStart() {
            return prevMonthStart;
        }

        public String getNextMonthStart() {
            return nextMonthStart;
        }

        public List<String> getWeekdayHeaders() {
            return weekdayHeaders;
        }

        public List<MonthlyCalendarWeek> getWeeks() {
            return weeks;
        }
    }

    public static class MonthlyCalendarWeek {
        private final List<MonthlyCalendarDay> days;

        public MonthlyCalendarWeek(List<MonthlyCalendarDay> days) {
            this.days = days;
        }

        public List<MonthlyCalendarDay> getDays() {
            return days;
        }
    }

    public static class MonthlyCalendarDay {
        private final String dayLabel;
        private final boolean inCurrentMonth;
        private final boolean today;
        private final List<MonthlyCalendarEvent> events;

        public MonthlyCalendarDay(
                String dayLabel,
                boolean inCurrentMonth,
                boolean today,
                List<MonthlyCalendarEvent> events) {
            this.dayLabel = dayLabel;
            this.inCurrentMonth = inCurrentMonth;
            this.today = today;
            this.events = events;
        }

        public String getDayLabel() {
            return dayLabel;
        }

        public boolean isInCurrentMonth() {
            return inCurrentMonth;
        }

        public boolean isToday() {
            return today;
        }

        public List<MonthlyCalendarEvent> getEvents() {
            return events;
        }
    }

    public static class MonthlyCalendarEvent {
        private final String title;
        private final String timeLabel;
        private final String tooltip;
        private final String status;
        private final String typeClass;
        private final String detailUrl;

        public MonthlyCalendarEvent(
                String title,
                String timeLabel,
                String tooltip,
                String status,
                String typeClass,
                String detailUrl) {
            this.title = title;
            this.timeLabel = timeLabel;
            this.tooltip = tooltip;
            this.status = status;
            this.typeClass = typeClass;
            this.detailUrl = detailUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getTimeLabel() {
            return timeLabel;
        }

        public String getTooltip() {
            return tooltip;
        }

        public String getStatus() {
            return status;
        }

        public String getTypeClass() {
            return typeClass;
        }

        public String getDetailUrl() {
            return detailUrl;
        }
    }
}
