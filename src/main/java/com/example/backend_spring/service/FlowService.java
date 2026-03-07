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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend_spring.domain.Flow;
import com.example.backend_spring.domain.FlowStep;
import com.example.backend_spring.domain.StepCandidate;
import com.example.backend_spring.repository.FlowRepository;
import com.example.backend_spring.repository.FlowStepRepository;
import com.example.backend_spring.repository.StepCandidateRepository;

@Service
public class FlowService {

    private static final List<String> BLOCKING_CANDIDATE_STATUSES = List.of("PROPOSED", "SELECTED");
    private static final List<String> WEEKDAY_HEADERS = List.of("\u65E5", "\u6708", "\u706B", "\u6C34", "\u6728", "\u91D1", "\u571F");
    private static final DateTimeFormatter TIME_LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final double HOUR_HEIGHT_PX = 44.0;

    private final FlowRepository flowRepo;
    private final FlowStepRepository stepRepo;
    private final StepCandidateRepository candidateRepo;
    private final Clock clock;

    public FlowService(
            FlowRepository flowRepo,
            FlowStepRepository stepRepo,
            StepCandidateRepository candidateRepo,
            Clock clock) {
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
        this.candidateRepo = candidateRepo;
        this.clock = clock;
    }

    @Transactional
    public Long createFlow(String title, int durationMinutes, LocalDateTime startFrom, Long createdByUserId, List<String> participants) {
        validateReservableDateTime(startFrom, "\u8abf\u6574\u958b\u59cb\u65e5\u6642");

        Flow flow = new Flow(title, durationMinutes, startFrom, createdByUserId);
        Flow saved = flowRepo.save(flow);

        List<FlowStep> steps = new ArrayList<>();
        int order = 1;
        for (String p : participants) {
            String name = p == null ? "" : p.trim();
            if (name.isEmpty()) {
                continue;
            }

            FlowStep step = new FlowStep(saved.getId(), order, name);
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

    public Flow getFlow(Long id) {
        return flowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + id));
    }

    public List<FlowStep> getSteps(Long flowId) {
        return stepRepo.findByFlowIdOrderByStepOrder(flowId);
    }

    public Optional<FlowStep> findActiveStep(Long flowId) {
        Flow flow = getFlow(flowId);

        FlowStep step = stepRepo.findByFlowIdAndStepOrder(flowId, flow.getCurrentStepOrder());
        if (step == null || !"ACTIVE".equals(step.getStatus())) {
            return Optional.empty();
        }
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
                        day.toString(),
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

        assertNoOwnerTimeOverlap(flow, startAt, endAt);

        candidateRepo.save(new StepCandidate(active.getId(), startAt, endAt));
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

    private List<CalendarSourceEvent> collectCalendarSourceEvents(List<Flow> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }

        List<CalendarSourceEvent> events = new ArrayList<>();
        for (Flow flow : flows) {
            if (flow == null || flow.getId() == null) {
                continue;
            }

            List<FlowStep> steps = stepRepo.findByFlowIdOrderByStepOrder(flow.getId());
            for (FlowStep step : steps) {
                if ("CONFIRMED".equals(step.getStatus())
                        && step.getConfirmedStartAt() != null
                        && step.getConfirmedEndAt() != null) {
                    events.add(new CalendarSourceEvent(
                            step.getConfirmedStartAt(),
                            step.getConfirmedEndAt(),
                            "CONFIRMED",
                            buildEventTitle(flow, step.getParticipantName()),
                            buildTooltip(flow, step.getParticipantName(), "CONFIRMED", step.getConfirmedStartAt(), step.getConfirmedEndAt())));
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

                    events.add(new CalendarSourceEvent(
                            candidate.getStartAt(),
                            candidate.getEndAt(),
                            candidate.getStatus(),
                            buildEventTitle(flow, step.getParticipantName()),
                            buildTooltip(flow, step.getParticipantName(), candidate.getStatus(), candidate.getStartAt(), candidate.getEndAt())));
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

    private String buildTooltip(Flow flow, String participantName, String status, LocalDateTime startAt, LocalDateTime endAt) {
        return buildEventTitle(flow, participantName)
                + " [" + status + "] "
                + startAt.toString()
                + " - "
                + endAt.toString();
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
                style);
    }

    private MonthlyCalendarEvent toMonthlyCalendarEvent(CalendarSourceEvent event) {
        return new MonthlyCalendarEvent(
                event.title,
                formatTimeRange(event.startAt, event.endAt),
                event.tooltip,
                event.status,
                toTypeClass(event.status));
    }

    private String formatTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        return TIME_LABEL_FORMAT.format(startAt) + " - " + TIME_LABEL_FORMAT.format(endAt);
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

    private void assertNoOwnerTimeOverlap(Flow flow, LocalDateTime newStartAt, LocalDateTime newEndAt) {
        var conflict = candidateRepo.findFirstTimeConflictForOwner(flow.getCreatedByUserId(), newStartAt, newEndAt);
        if (conflict.isPresent()) {
            var c = conflict.get();
            throw new IllegalArgumentException(
                    "\u5019\u88dc\u6642\u9593\u304c\u91cd\u8907\u3057\u3066\u3044\u307e\u3059: \u30d5\u30ed\u30fc=" + c.getFlowTitle()
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

        private CalendarSourceEvent(
                LocalDateTime startAt,
                LocalDateTime endAt,
                String status,
                String title,
                String tooltip) {
            this.startAt = startAt;
            this.endAt = endAt;
            this.status = status;
            this.title = title;
            this.tooltip = tooltip;
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

        public CalendarEvent(
                String title,
                String timeLabel,
                String tooltip,
                String status,
                String typeClass,
                String style) {
            this.title = title;
            this.timeLabel = timeLabel;
            this.tooltip = tooltip;
            this.status = status;
            this.typeClass = typeClass;
            this.style = style;
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

        public MonthlyCalendarEvent(
                String title,
                String timeLabel,
                String tooltip,
                String status,
                String typeClass) {
            this.title = title;
            this.timeLabel = timeLabel;
            this.tooltip = tooltip;
            this.status = status;
            this.typeClass = typeClass;
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
    }
}
