package redxax.oxy.remotely.flow.ui;

import com.google.gson.JsonObject;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;
import redxax.oxy.remotely.flow.ui.studio.StudioScreen;

import java.util.ArrayList;
import java.util.List;

public final class AutomationDefinitionDesignerScreen extends FocusedJsonResourceDesignerScreen {
    public AutomationDefinitionDesignerScreen(StudioScreen owner, String type, String resourceId, JsonObject resource,
                                              String serverId, Object parent) {
        super(owner, type, resourceId, resource, serverId, parent);
        applyDefaults();
    }

    @Override
    protected List<String> editorFields() {
        return switch (type) {
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION ->
                List.of("description", "valueType", "scope", "persistent", "defaultValue");
            case ReSyncResourceDragPayload.TIMER_DEFINITION ->
                List.of("description", "scope", "persistent", "defaultDuration", "defaultUnit", "tickInterval");
            case ReSyncResourceDragPayload.SCHEDULE_DEFINITION -> scheduleFields();
            default -> List.of("description");
        };
    }

    @Override
    protected boolean toggleField(String field) {
        return "persistent".equals(field) || super.toggleField(field);
    }

    @Override
    protected boolean customDropdownField(String field) {
        return List.of("valueType", "scope", "targetType", "targetId", "timingMode", "unit", "defaultUnit", "overlapPolicy",
            "existingTaskPolicy", "failurePolicy", "offlinePolicy", "missedRunPolicy").contains(field);
    }

    @Override
    protected boolean customRebuildOnSelection(String field) {
        return "targetType".equals(field) || "timingMode".equals(field) || "scope".equals(field);
    }

    @Override
    protected List<String> customSelectorOptions(String field) {
        return switch (field) {
            case "valueType" -> FlowDataType.values().stream()
                .filter(value -> value != FlowDataType.ANY && value != FlowDataType.EXECUTION)
                .map(FlowDataType::getId).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            case "scope" -> List.of("flow", "server", "player", "entity", "network");
            case "targetType" -> List.of("function", "flow");
            case "targetId" -> "flow".equalsIgnoreCase(jsonText("targetType")) ? flowOptions() : functionOptions();
            case "timingMode" -> List.of("after_delay", "at_time", "repeating", "cron");
            case "unit", "defaultUnit" -> List.of("ticks", "seconds", "minutes");
            case "overlapPolicy" -> List.of("skip", "queue", "parallel", "replace");
            case "existingTaskPolicy" -> List.of("replace", "keep", "fail");
            case "failurePolicy" -> List.of("continue", "stop");
            case "offlinePolicy" -> List.of("wait", "skip", "run_without_player", "cancel");
            case "missedRunPolicy" -> List.of("run_once", "skip", "cancel");
            default -> null;
        };
    }

    @Override
    protected String resourceSummary() {
        return switch (type) {
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION ->
                firstFilled(jsonText("valueType"), "any") + " · " + firstFilled(jsonText("scope"), "flow");
            case ReSyncResourceDragPayload.TIMER_DEFINITION ->
                firstFilled(jsonText("defaultDuration"), "0") + " " + firstFilled(jsonText("defaultUnit"), "seconds");
            case ReSyncResourceDragPayload.SCHEDULE_DEFINITION ->
                firstFilled(jsonText("timingMode"), "after_delay") + " · " + firstFilled(jsonText("targetId"), "Target");
            default -> id;
        };
    }

    @Override
    protected String resourceDisplayName() {
        return switch (type) {
            case ReSyncResourceDragPayload.VARIABLE_DEFINITION -> "Variable";
            case ReSyncResourceDragPayload.TIMER_DEFINITION -> "Timer";
            case ReSyncResourceDragPayload.SCHEDULE_DEFINITION -> "Schedule";
            default -> "Automation";
        };
    }

    @Override
    protected String jsonResourceDescription(String field, String label) {
        return switch (field) {
            case "valueType" -> "Value type used by every Get, Set, event, and numeric operation for this Variable.";
            case "scope" -> "Owner of each runtime instance.\nFlow: current execution.\nServer: this server.\nPlayer or Entity: one value per owner.\nNetwork: one value per network owner.";
            case "persistent" -> "On: restore this definition's runtime state after a server restart.\nOff: discard it when the server stops.";
            case "defaultValue" -> "Value returned before this Variable is set.";
            case "defaultDuration" -> "Duration used when Timer Start does not provide one.";
            case "defaultUnit" -> "Unit for the default duration and tick interval.";
            case "tickInterval" -> "Heartbeat interval for Timer Event: Tick.\n0 disables Tick events.";
            case "targetType" -> "Function is recommended and provides typed arguments.\nFlow enters through Schedule Event.";
            case "targetId" -> "Function or Flow invoked when this Schedule fires.";
            case "timingMode" -> "After Delay: run once after a duration.\nAt Time: run once at a date and time.\nRepeating: run on an interval.\nCron: run from a cron pattern.";
            case "overlapPolicy" -> "Behavior when the previous run is still active.";
            case "existingTaskPolicy" -> "Behavior when the same Schedule and owner are already active.";
            case "failurePolicy" -> "Continue keeps repeating after a failure.\nStop ends the task.";
            case "offlinePolicy" -> "Behavior when a player-scoped Schedule fires while its player is offline.";
            case "missedRunPolicy" -> "Behavior when this server was offline at the next scheduled time.";
            default -> super.jsonResourceDescription(field, label);
        };
    }

    private List<String> scheduleFields() {
        List<String> fields = new ArrayList<>(List.of("description", "targetType", "targetId", "timingMode"));
        switch (jsonText("timingMode").toLowerCase()) {
            case "at_time" -> {
                fields.add("dateTime");
                fields.add("timeZone");
            }
            case "cron" -> {
                fields.add("cron");
                fields.add("timeZone");
            }
            case "repeating" -> {
                fields.add("duration");
                fields.add("unit");
                fields.add("initialDelay");
            }
            default -> {
                fields.add("duration");
                fields.add("unit");
            }
        }
        fields.add("scope");
        fields.add("persistent");
        fields.add("overlapPolicy");
        fields.add("existingTaskPolicy");
        fields.add("failurePolicy");
        fields.add("missedRunPolicy");
        if ("player".equalsIgnoreCase(jsonText("scope"))) {
            fields.add("offlinePolicy");
        }
        return List.copyOf(fields);
    }

    private void applyDefaults() {
        if (jsonText("scope").isBlank()) {
            resource.addProperty("scope", ReSyncResourceDragPayload.VARIABLE_DEFINITION.equals(type) ? "flow" : "server");
        }
        if (!resource.has("persistent")) {
            resource.addProperty("persistent", false);
        }
        if (ReSyncResourceDragPayload.VARIABLE_DEFINITION.equals(type) && jsonText("valueType").isBlank()) {
            resource.addProperty("valueType", "boolean");
        }
        if (ReSyncResourceDragPayload.TIMER_DEFINITION.equals(type)) {
            if (jsonText("defaultUnit").isBlank()) resource.addProperty("defaultUnit", "seconds");
            if (!resource.has("defaultDuration")) resource.addProperty("defaultDuration", 60);
            if (!resource.has("tickInterval")) resource.addProperty("tickInterval", 0);
        }
        if (ReSyncResourceDragPayload.SCHEDULE_DEFINITION.equals(type)) {
            if (jsonText("targetType").isBlank()) resource.addProperty("targetType", "function");
            if (jsonText("timingMode").isBlank()) resource.addProperty("timingMode", "after_delay");
            if (jsonText("unit").isBlank()) resource.addProperty("unit", "seconds");
            if (!resource.has("duration")) resource.addProperty("duration", 60);
            if (jsonText("timeZone").isBlank()) resource.addProperty("timeZone", "UTC");
            if (jsonText("overlapPolicy").isBlank()) resource.addProperty("overlapPolicy", "skip");
            if (jsonText("existingTaskPolicy").isBlank()) resource.addProperty("existingTaskPolicy", "replace");
            if (jsonText("failurePolicy").isBlank()) resource.addProperty("failurePolicy", "continue");
            if (jsonText("offlinePolicy").isBlank()) resource.addProperty("offlinePolicy", "wait");
            if (jsonText("missedRunPolicy").isBlank()) resource.addProperty("missedRunPolicy", "run_once");
        }
    }
}
