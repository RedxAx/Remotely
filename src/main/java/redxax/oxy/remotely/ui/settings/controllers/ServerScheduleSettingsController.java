package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.backend.feature.AsyncServerScheduleFeature;
import restudio.rebase.schedule.ServerScheduleModels;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.PopupWidget;
import restudio.rescreen.ui.widgets.ScreenWindowWidget;
import restudio.rescreen.ui.widgets.ScrollSelectorWidget;
import restudio.rescreen.ui.widgets.SquareButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;

public final class ServerScheduleSettingsController {
    private static final String TAB = "Schedules";

    private final ReScreen owner;
    private final AsyncServerScheduleFeature feature;
    private long operationSequence;
    private final List<ServerScheduleModels.Schedule> schedules = new ArrayList<>();
    private boolean loaded;
    private boolean loading;
    private boolean action;
    private String loadError = "";
    private long generation;

    public ServerScheduleSettingsController(ReScreen owner, AsyncServerScheduleFeature feature) {
        this.owner = owner;
        this.feature = feature;
    }

    public List<Setting> getSettings() {
        ServerScheduleModels.Capabilities capabilities = feature == null
                ? ServerScheduleModels.Capabilities.unavailable("Scheduling Is Unavailable") : feature.scheduleCapabilities();
        Setting.Builder builder = new Setting.Builder("Server Schedules");
        if (!capabilities.available()) {
            builder.addRow("", new AnimatedButton.Builder().label("Scheduling Unavailable").active(false)
                    .hint(capabilities.reason()).build());
            return List.of(builder.build());
        }
        if (!capabilities.reason().isBlank()) {
            builder.addRow("", new AnimatedButton.Builder().label(capabilities.reason()).active(false).build());
        }
        builder.addRow("", new AnimatedButton.Builder().label("Add Schedule").active(!action)
                .accentType(ThemeManager.getAccent("nice")).onClick(() -> showEditor(null, List.of(defaultTask()))).build());
        ensureLoaded();
        if (!loaded) {
            String label = loading ? "Loading Schedules" : loadError.isBlank() ? "Schedules Unavailable" : "Load Failed";
            builder.addRow("", new AnimatedButton.Builder().label(label).active(false).hint(loadError).build());
            if (!loading) builder.addRow("", new AnimatedButton.Builder().label("Retry Load").onClick(this::load).build());
            return List.of(builder.build());
        }
        if (schedules.isEmpty()) {
            builder.addRow("", new AnimatedButton.Builder().label("No Schedules").active(false).build());
            return List.of(builder.build());
        }
        schedules.stream().sorted(Comparator.comparing(ServerScheduleModels.Schedule::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(schedule -> addSchedule(builder, schedule));
        return List.of(builder.build());
    }

    private void addSchedule(Setting.Builder builder, ServerScheduleModels.Schedule schedule) {
        String state = schedule.enabled() ? "Enabled" : "Disabled";
        String next = schedule.nextRunAt().isBlank() ? "No Next Run" : "Next " + displayTime(schedule.nextRunAt());
        String last = schedule.lastResult().isBlank() ? "Never Run" : schedule.lastResult();
        SquareButtonWidget run = new SquareButtonWidget.Builder().imagePath("start.png").hint("Run Now")
                .active(!action && feature.scheduleCapabilities().runNow()).accentType(ThemeManager.getAccent("nice"))
                .onClick(() -> run(schedule)).size(18, 18).build();
        SquareButtonWidget edit = new SquareButtonWidget.Builder().imagePath("edit.png").hint("Edit Schedule")
                .active(!action).onClick(() -> showEditor(schedule, schedule.tasks())).size(18, 18).build();
        SquareButtonWidget delete = new SquareButtonWidget.Builder().imagePath("delete.png").hint("Delete Schedule")
                .active(!action).accentType(ThemeManager.getAccent("danger")).onClick(() -> confirmDelete(schedule)).size(18, 18).build();
        MountableButtonWidget row = new MountableButtonWidget.Builder(schedule.name()).description(state + " · " + next)
                .hiddenText(last).addButton(run).addButton(edit).addButton(delete).build();
        builder.addRow("", row);
    }

    private void ensureLoaded() {
        if (!loaded && !loading && loadError.isBlank()) load();
    }

    private void load() {
        if (loading || feature == null) return;
        loading = true;
        loadError = "";
        long request = ++generation;
        feature.listSchedules().whenComplete((values, failure) -> ScreenManager.getInstance().execute(() -> {
            if (request != generation) return;
            loading = false;
            if (failure == null) {
                schedules.clear();
                if (values != null) schedules.addAll(values);
                loaded = true;
            } else {
                loaded = false;
                loadError = error(failure);
            }
            refresh();
        }));
    }

    private void showEditor(ServerScheduleModels.Schedule schedule, List<ServerScheduleModels.Task> seedTasks) {
        Screen screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null || action) return;
        ServerScheduleModels.Capabilities capabilities = feature.scheduleCapabilities();
        PopupWidget.Builder popup = new PopupWidget.Builder(schedule == null ? "Add Schedule" : "Edit Schedule")
                .pos(50, screen.height / 8).size(480, 430).setResizable(true).setMinSize(420, 330);
        TextInputWidget name = input(schedule == null ? "" : schedule.name(), "Schedule Name", 260);
        ToggleWidget enabled = new ToggleWidget.Builder().toggled(schedule == null || schedule.enabled()).build();
        ToggleWidget onlyOnline = new ToggleWidget.Builder().toggled(schedule != null && schedule.onlyWhenOnline()).build();
        List<String> timingOptions = new ArrayList<>();
        if (capabilities.oneTime()) timingOptions.add("One Time");
        if (capabilities.cron()) timingOptions.add("Recurring");
        int timingIndex = schedule != null && schedule.timing().type() == ServerScheduleModels.TimingType.CRON
                ? timingOptions.indexOf("Recurring") : timingOptions.indexOf("One Time");
        ScrollSelectorWidget timing = new ScrollSelectorWidget.Builder().options(timingOptions)
                .selectedIndex(Math.max(0, timingIndex)).size(150, 20).build();
        String timingValue = schedule == null ? (capabilities.oneTime() ? "" : "0 4 * * *")
                : schedule.timing().type() == ServerScheduleModels.TimingType.ONE_TIME
                ? schedule.timing().runAt() : schedule.timing().cron();
        String cron = schedule != null && schedule.timing().type() == ServerScheduleModels.TimingType.CRON ? schedule.timing().cron() : "0 4 * * *";
        String selectedPreset = ScheduleTimingGuide.preset(cron);
        ScrollSelectorWidget preset = new ScrollSelectorWidget.Builder().options(ScheduleTimingGuide.PRESETS)
                .selectedIndex(ScheduleTimingGuide.PRESETS.indexOf(selectedPreset)).size(150, 20).build();
        TextInputWidget recurringTime = input(ScheduleTimingGuide.time(cron), "HH:MM", 90);
        int selectedWeekDay = ScheduleTimingGuide.weekDay(cron);
        ScrollSelectorWidget weekDay = new ScrollSelectorWidget.Builder().options(ScheduleTimingGuide.WEEKDAYS_LIST)
                .selectedIndex(selectedWeekDay).size(120, 20).build();
        TextInputWidget monthDay = input(ScheduleTimingGuide.monthDay(cron), "1 To 31", 70);
        TextInputWidget advancedCron = input(cron, "Five Field Cron", 220);
        TextInputWidget when = input(timingValue, "2026-08-27T18:00:00Z", 260);
        TextInputWidget zone = input(schedule == null ? "UTC" : schedule.timing().zoneId(), "Time Zone", 180);
        popup.addRow("name", "Name", name);
        popup.addRow("enabled", "Enabled", enabled);
        popup.addRow("timing", "Timing", timing);
        if (capabilities.oneTime()) popup.addRow("schedule-when", "One Time UTC", when);
        if (capabilities.cron()) {
            popup.addRow("schedule-preset", "Recurring Pattern", preset);
            popup.addRow("schedule-time", "Recurring Time", recurringTime);
            popup.addRow("schedule-weekday", "Week Day", weekDay);
            popup.addRow("schedule-month-day", "Month Day", monthDay);
            popup.addRow("schedule-cron", "Advanced Cron", advancedCron);
            Runnable updateTimingFields = () -> updateTimingRows(popup.getWidget(), capabilities.oneTime(), capabilities.timeZone(),
                    "Recurring".equals(timing.getSelectedOption()), preset.getSelectedOption());
            timing.setOnChange(updateTimingFields);
            preset.setOnChange(updateTimingFields);
            if (capabilities.timeZone()) popup.addRow("schedule-zone", "Recurring Time Zone", zone);
            updateTimingFields.run();
        }
        popup.addRow("online", "Only While Running", onlyOnline);

        List<ServerScheduleModels.Task> tasks = seedTasks == null || seedTasks.isEmpty() ? List.of(defaultTask()) : List.copyOf(seedTasks);
        List<TaskEditor> editors = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            ServerScheduleModels.Task task = tasks.get(index);
            List<String> actions = actionOptions(capabilities.backup());
            int selected = Math.max(0, actions.indexOf(label(task.action())));
            ScrollSelectorWidget taskAction = new ScrollSelectorWidget.Builder().options(actions).selectedIndex(selected).size(95, 20).build();
            TextInputWidget payload = input(task.payload(), task.action() == ServerScheduleModels.Action.COMMAND ? "Command" : "Value", 135);
            TextInputWidget delay = input(String.valueOf(task.delaySeconds()), "Delay", 50);
            ToggleWidget continueOnFailure = new ToggleWidget.Builder().toggled(task.continueOnFailure()).size(36, 18).build();
            int taskIndex = index;
            TaskEditor editor = new TaskEditor(task.id(), taskAction, payload, delay, continueOnFailure);
            editors.add(editor);
            SquareButtonWidget up = new SquareButtonWidget.Builder().imagePath("up.png").hint("Move Up").active(index > 0)
                    .onClick(() -> reopenMoved(popup, schedule, editors, taskIndex, -1)).size(18, 18).build();
            SquareButtonWidget down = new SquareButtonWidget.Builder().imagePath("down.png").hint("Move Down").active(index + 1 < tasks.size())
                    .onClick(() -> reopenMoved(popup, schedule, editors, taskIndex, 1)).size(18, 18).build();
            SquareButtonWidget remove = new SquareButtonWidget.Builder().imagePath("delete.png").hint("Remove Task")
                    .active(tasks.size() > 1).accentType(ThemeManager.getAccent("danger"))
                    .onClick(() -> reopenWithout(popup, schedule, editors, taskIndex)).size(18, 18).build();
            popup.addRow("task-" + index, "Task " + (index + 1), taskAction, payload, delay, continueOnFailure, up, down, remove);
        }
        AnimatedButton addTask = new AnimatedButton.Builder().label("Add Task").active(tasks.size() < 32).onClick(() -> {
            List<ServerScheduleModels.Task> values = captureOrNotify(editors);
            if (values == null) return;
            values.add(defaultTask());
            popup.getWidget().setVisible(false);
            showEditor(schedule, values);
        }).build();
        popup.addRow("add-task", "", addTask);
        popup.addTitleAction("Save", () -> save(popup, schedule, name, enabled, timing, when, preset, recurringTime,
                        weekDay, monthDay, advancedCron, zone, onlyOnline, editors),
                PopupWidget.TitleActionRole.PRIMARY);
        PopupWidget widget = popup.build();
        screen.addDrawableChild(widget);
        widget.show();
    }

    private void reopenWithout(PopupWidget.Builder popup, ServerScheduleModels.Schedule schedule, List<TaskEditor> editors, int index) {
        List<ServerScheduleModels.Task> values = captureOrNotify(editors);
        if (values == null) return;
        if (values.size() <= 1) return;
        values.remove(index);
        popup.getWidget().setVisible(false);
        showEditor(schedule, values);
    }

    private void reopenMoved(PopupWidget.Builder popup, ServerScheduleModels.Schedule schedule, List<TaskEditor> editors,
                             int index, int movement) {
        List<ServerScheduleModels.Task> values = captureOrNotify(editors);
        int target = index + movement;
        if (values == null || target < 0 || target >= values.size()) return;
        Collections.swap(values, index, target);
        popup.getWidget().setVisible(false);
        showEditor(schedule, values);
    }

    private void save(PopupWidget.Builder popup, ServerScheduleModels.Schedule schedule, TextInputWidget name,
                      ToggleWidget enabled, ScrollSelectorWidget timing, TextInputWidget when, ScrollSelectorWidget preset,
                      TextInputWidget recurringTime, ScrollSelectorWidget weekDay, TextInputWidget monthDay,
                      TextInputWidget advancedCron, TextInputWidget zone,
                      ToggleWidget onlyOnline, List<TaskEditor> editors) {
        String scheduleName = name.getText().trim();
        if (scheduleName.isBlank()) {
            new Notification("Invalid Schedule", "Name Is Required", Notification.Type.WARN);
            return;
        }
        boolean oneTime = "One Time".equals(timing.getSelectedOption());
        String value;
        try {
            value = oneTime ? when.getText().trim() : ScheduleTimingGuide.cron(preset.getSelectedOption(), recurringTime.getText(),
                    Math.max(0, ScheduleTimingGuide.WEEKDAYS_LIST.indexOf(weekDay.getSelectedOption())), monthDay.getText(), advancedCron.getText());
            if (oneTime && !Instant.parse(value).isAfter(Instant.now())) throw new IllegalArgumentException("One Time Schedule Must Be In The Future");
            if (!oneTime) ScheduleTimingGuide.summary(preset.getSelectedOption(), recurringTime.getText(),
                    Math.max(0, ScheduleTimingGuide.WEEKDAYS_LIST.indexOf(weekDay.getSelectedOption())), monthDay.getText(), advancedCron.getText(), zone.getText());
        } catch (RuntimeException failure) {
            new Notification("Invalid Schedule", oneTime ? "Use A Future UTC Time Like 2026-08-27T18:00:00Z" : failure.getMessage(), Notification.Type.WARN);
            return;
        }
        if (value.isBlank()) {
            new Notification("Invalid Schedule", oneTime ? "Date And Time Are Required" : "Cron Is Required", Notification.Type.WARN);
            return;
        }
        List<ServerScheduleModels.Task> tasks;
        try {
            tasks = capture(editors);
        } catch (RuntimeException failure) {
            new Notification("Invalid Schedule", failure.getMessage(), Notification.Type.WARN);
            return;
        }
        String timingSummary;
        try {
            timingSummary = oneTime ? "Runs Once At " + displayTime(value) : ScheduleTimingGuide.summary(preset.getSelectedOption(), recurringTime.getText(),
                    Math.max(0, ScheduleTimingGuide.WEEKDAYS_LIST.indexOf(weekDay.getSelectedOption())), monthDay.getText(), advancedCron.getText(), zone.getText());
            if (!oneTime) ZoneId.of(zone.getText().isBlank() ? "UTC" : zone.getText().trim());
        } catch (RuntimeException failure) {
            new Notification("Invalid Schedule", "Choose A Valid Time Zone Like UTC Or Europe/Berlin", Notification.Type.WARN);
            return;
        }
        ServerScheduleModels.Timing scheduleTiming = new ServerScheduleModels.Timing(oneTime
                ? ServerScheduleModels.TimingType.ONE_TIME : ServerScheduleModels.TimingType.CRON,
                oneTime ? value : "", oneTime ? "" : value, zone.getText().trim());
        ServerScheduleModels.Mutation mutation = new ServerScheduleModels.Mutation(scheduleName, enabled.getValue(), scheduleTiming,
                onlyOnline.getValue(), tasks);
        action = true;
        popup.getWidget().setVisible(false);
        String key = operationKey();
        var operation = schedule == null ? feature.createSchedule(mutation, key)
                : feature.updateSchedule(schedule.id(), mutation, schedule.revision(), key);
        operation.whenComplete((updated, failure) -> ScreenManager.getInstance().execute(() -> {
            action = false;
            if (failure == null && updated != null) {
                upsert(updated);
                new Notification("Schedule Saved", timingSummary + " · Next " + displayTime(updated.nextRunAt()), Notification.Type.SUCCESS);
            } else {
                new Notification("Save Failed", error(failure), Notification.Type.ERROR);
            }
            refresh();
        }));
    }

    private List<ServerScheduleModels.Task> capture(List<TaskEditor> editors) {
        List<ServerScheduleModels.Task> tasks = new ArrayList<>();
        for (int index = 0; index < editors.size(); index++) {
            TaskEditor editor = editors.get(index);
            ServerScheduleModels.Action action = parseAction(editor.action().getSelectedOption());
            String payload = editor.payload().getText();
            if (action == ServerScheduleModels.Action.COMMAND && payload.isBlank()) {
                throw new IllegalArgumentException("Command Is Required For Task " + (index + 1));
            }
            int delay;
            try {
                delay = Integer.parseInt(editor.delay().getText().trim());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Task Delay Must Be A Number");
            }
            if (delay < 0 || delay > 86400) throw new IllegalArgumentException("Task Delay Must Be Between 0 And 86400");
            tasks.add(new ServerScheduleModels.Task(editor.id(), index + 1, action, payload, delay,
                    editor.continueOnFailure().getValue()));
        }
        return tasks;
    }

    private List<ServerScheduleModels.Task> captureOrNotify(List<TaskEditor> editors) {
        try {
            return capture(editors);
        } catch (RuntimeException failure) {
            new Notification("Invalid Task", failure.getMessage(), Notification.Type.WARN);
            return null;
        }
    }

    private void run(ServerScheduleModels.Schedule schedule) {
        if (action) return;
        action = true;
        feature.runSchedule(schedule.id(), operationKey()).whenComplete((run, failure) -> ScreenManager.getInstance().execute(() -> {
            action = false;
            new Notification(failure == null ? "Schedule Started" : "Run Failed",
                    failure == null ? schedule.name() : error(failure), failure == null ? Notification.Type.SUCCESS : Notification.Type.ERROR);
            loadError = "";
            loaded = false;
            refresh();
        }));
    }

    private void confirmDelete(ServerScheduleModels.Schedule schedule) {
        Screen screen = ScreenManager.getInstance().getCurrentScreen();
        if (screen == null || action) return;
        PopupWidget.Builder popup = new PopupWidget.Builder("Delete Schedule").pos(70, screen.height / 4).size(360, 150);
        popup.addRow("confirm", "Delete " + schedule.name() + "?");
        popup.addTitleAction("Delete", () -> {
            popup.getWidget().setVisible(false);
            action = true;
            feature.deleteSchedule(schedule.id(), schedule.revision(), operationKey()).whenComplete((ignored, failure) ->
                    ScreenManager.getInstance().execute(() -> {
                        action = false;
                        if (failure == null) {
                            schedules.removeIf(value -> value.id().equals(schedule.id()));
                            new Notification("Schedule Deleted", schedule.name(), Notification.Type.INFO);
                        } else {
                            new Notification("Delete Failed", error(failure), Notification.Type.ERROR);
                        }
                        refresh();
                    }));
        }, PopupWidget.TitleActionRole.DESTRUCTIVE);
        PopupWidget widget = popup.build();
        screen.addDrawableChild(widget);
        widget.show();
    }

    private void upsert(ServerScheduleModels.Schedule schedule) {
        schedules.removeIf(value -> value.id().equals(schedule.id()));
        schedules.add(schedule);
    }

    private void refresh() {
        ScreenManager manager = ScreenManager.getInstance();
        List<SettingsScreen> screens = new ArrayList<>();
        if (manager.getCurrentScreen() instanceof SettingsScreen settings) screens.add(settings);
        if (manager.getDesktopWindowsOverlay() != null) {
            for (ScreenWindowWidget window : manager.getDesktopWindowsOverlay().getWindows()) {
                if (window.getScreen() instanceof SettingsScreen settings && !screens.contains(settings)) screens.add(settings);
            }
        }
        screens.forEach(settings -> settings.refreshTab(TAB));
    }

    private List<String> actionOptions(boolean backup) {
        List<String> values = new ArrayList<>(List.of("Start", "Stop", "Restart", "Command"));
        if (backup) values.add("Backup");
        return values;
    }

    private String label(ServerScheduleModels.Action action) {
        String value = action.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private ServerScheduleModels.Action parseAction(String value) {
        return ServerScheduleModels.Action.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private ServerScheduleModels.Task defaultTask() {
        return new ServerScheduleModels.Task("", 1, ServerScheduleModels.Action.RESTART, "", 0, false);
    }

    private TextInputWidget input(String value, String placeholder, int width) {
        return new TextInputWidget.Builder().text(value == null ? "" : value).placeholder(placeholder).size(width, 20).build();
    }

    private String operationKey() {
        return System.currentTimeMillis() + "-" + (++operationSequence);
    }

    private String error(Throwable failure) {
        Throwable value = failure;
        while (value != null && value.getCause() != null) value = value.getCause();
        String message = value == null ? "Schedule Operation Failed" : value.getMessage();
        return message == null || message.isBlank() ? "Schedule Operation Failed" : message;
    }

    private static String displayTime(String value) {
        if (value == null || value.isBlank()) return "Not Scheduled";
        try {
            Instant.parse(value);
            return value.length() >= 16 ? value.substring(0, 10) + " " + value.substring(11, 16) + " UTC" : value;
        } catch (RuntimeException failure) {
            return value;
        }
    }

    private static void updateTimingRows(PopupWidget popup, boolean oneTime, boolean timeZone, boolean recurring, String preset) {
        if (oneTime) popup.setRowVisibility("schedule-when", !recurring);
        popup.setRowVisibility("schedule-preset", recurring);
        popup.setRowVisibility("schedule-time", recurring && (ScheduleTimingGuide.DAILY.equals(preset)
                || ScheduleTimingGuide.WEEKDAYS.equals(preset) || ScheduleTimingGuide.WEEKLY.equals(preset)
                || ScheduleTimingGuide.MONTHLY.equals(preset)));
        popup.setRowVisibility("schedule-weekday", recurring && ScheduleTimingGuide.WEEKLY.equals(preset));
        popup.setRowVisibility("schedule-month-day", recurring && ScheduleTimingGuide.MONTHLY.equals(preset));
        popup.setRowVisibility("schedule-cron", recurring && ScheduleTimingGuide.ADVANCED.equals(preset));
        if (timeZone) popup.setRowVisibility("schedule-zone", recurring);
    }

    public void cleanup() {
        generation++;
    }

    private record TaskEditor(String id, ScrollSelectorWidget action, TextInputWidget payload, TextInputWidget delay,
                              ToggleWidget continueOnFailure) {
    }
}
