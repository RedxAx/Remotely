package redxax.oxy.remotely.web.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redxax.oxy.remotely.data.integrations.luckperms.ReSyncLuckPermsCodec;
import restudio.resync.permissions.LuckPermsManagementContract.Action;
import restudio.resync.permissions.LuckPermsManagementContract.AppliedEntity;
import restudio.resync.permissions.LuckPermsManagementContract.AuditEntry;
import restudio.resync.permissions.LuckPermsManagementContract.ChangeSet;
import restudio.resync.permissions.LuckPermsManagementContract.Conflict;
import restudio.resync.permissions.LuckPermsManagementContract.EffectivePreview;
import restudio.resync.permissions.LuckPermsManagementContract.EntityCreate;
import restudio.resync.permissions.LuckPermsManagementContract.EntityDelete;
import restudio.resync.permissions.LuckPermsManagementContract.EntityType;
import restudio.resync.permissions.LuckPermsManagementContract.GroupPage;
import restudio.resync.permissions.LuckPermsManagementContract.GroupSummary;
import restudio.resync.permissions.LuckPermsManagementContract.Invalidation;
import restudio.resync.permissions.LuckPermsManagementContract.NodeData;
import restudio.resync.permissions.LuckPermsManagementContract.NodeKind;
import restudio.resync.permissions.LuckPermsManagementContract.Overview;
import restudio.resync.permissions.LuckPermsManagementContract.PageRequest;
import restudio.resync.permissions.LuckPermsManagementContract.PreviewMatch;
import restudio.resync.permissions.LuckPermsManagementContract.PreviewRequest;
import restudio.resync.permissions.LuckPermsManagementContract.Request;
import restudio.resync.permissions.LuckPermsManagementContract.Response;
import restudio.resync.permissions.LuckPermsManagementContract.SaveResult;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectChange;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectDetail;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectRef;
import restudio.resync.permissions.LuckPermsManagementContract.SubjectType;
import restudio.resync.permissions.LuckPermsManagementContract.TrackChange;
import restudio.resync.permissions.LuckPermsManagementContract.TrackDetail;
import restudio.resync.permissions.LuckPermsManagementContract.UserPage;
import restudio.resync.permissions.LuckPermsManagementContract.UserSummary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class BrowserReSyncLuckPermsCodec implements ReSyncLuckPermsCodec {
    @Override
    public byte[] encode(Request request) {
        return BrowserJson.write(request(request)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Response decode(byte[] payload) {
        if (payload == null) return null;
        JsonElement value = BrowserJson.parse(new String(payload, StandardCharsets.UTF_8));
        return value == null || !value.isJsonObject() ? null : response(value.getAsJsonObject());
    }

    private static JsonObject request(Request value) {
        JsonObject result = new JsonObject();
        if (value == null) return result;
        put(result, "version", value.version());
        put(result, "requestId", value.requestId());
        putEnum(result, "action", value.action());
        put(result, "page", page(value.page()));
        put(result, "subject", subjectRef(value.subject()));
        put(result, "preview", previewRequest(value.preview()));
        put(result, "changes", changeSet(value.changes()));
        return result;
    }

    private static JsonObject page(PageRequest value) {
        if (value == null) return null;
        JsonObject result = new JsonObject();
        put(result, "cursor", value.cursor());
        put(result, "size", value.size());
        put(result, "query", value.query());
        return result;
    }

    private static JsonObject subjectRef(SubjectRef value) {
        if (value == null) return null;
        JsonObject result = new JsonObject();
        putEnum(result, "type", value.type());
        put(result, "id", value.id());
        return result;
    }

    private static JsonObject previewRequest(PreviewRequest value) {
        if (value == null) return null;
        JsonObject result = new JsonObject();
        put(result, "requestId", value.requestId());
        put(result, "subject", subjectRef(value.subject()));
        put(result, "permission", value.permission());
        put(result, "contexts", contexts(value.contexts()));
        put(result, "staged", changeSet(value.staged()));
        return result;
    }

    private static JsonObject changeSet(ChangeSet value) {
        if (value == null) return null;
        JsonObject result = new JsonObject();
        put(result, "operationId", value.operationId());
        result.add("subjects", array(value.subjects(), BrowserReSyncLuckPermsCodec::subjectChange));
        result.add("tracks", array(value.tracks(), BrowserReSyncLuckPermsCodec::trackChange));
        result.add("creates", array(value.creates(), BrowserReSyncLuckPermsCodec::entityCreate));
        result.add("deletes", array(value.deletes(), BrowserReSyncLuckPermsCodec::entityDelete));
        return result;
    }

    private static JsonObject subjectChange(SubjectChange value) {
        JsonObject result = new JsonObject();
        put(result, "subject", subjectRef(value.subject()));
        put(result, "baseRevision", value.baseRevision());
        put(result, "name", value.name());
        put(result, "primaryGroup", value.primaryGroup());
        put(result, "weight", value.weight());
        result.add("nodes", array(value.nodes(), BrowserReSyncLuckPermsCodec::node));
        return result;
    }

    private static JsonObject trackChange(TrackChange value) {
        JsonObject result = new JsonObject();
        put(result, "name", value.name());
        put(result, "baseRevision", value.baseRevision());
        result.add("groups", strings(value.groups()));
        return result;
    }

    private static JsonObject entityCreate(EntityCreate value) {
        JsonObject result = new JsonObject();
        putEnum(result, "type", value.type());
        put(result, "id", value.id());
        put(result, "username", value.username());
        return result;
    }

    private static JsonObject entityDelete(EntityDelete value) {
        JsonObject result = new JsonObject();
        putEnum(result, "type", value.type());
        put(result, "id", value.id());
        return result;
    }

    private static JsonObject node(NodeData value) {
        JsonObject result = new JsonObject();
        put(result, "id", value.id());
        putEnum(result, "kind", value.kind());
        put(result, "key", value.key());
        put(result, "value", value.value());
        put(result, "priority", value.priority());
        put(result, "contexts", contexts(value.contexts()));
        put(result, "expiresAt", value.expiresAt());
        return result;
    }

    private static Response response(JsonObject root) {
        return new Response(BrowserJson.integer(root, "version", 1), BrowserJson.string(root, "requestId"),
            action(root), BrowserJson.bool(root, "success", false), BrowserJson.string(root, "message"),
            BrowserJson.longValue(root, "revision", 0), overview(root, "overview"), userPage(root, "users"),
            groupPage(root, "groups"), objects(root, "tracks", BrowserReSyncLuckPermsCodec::track),
            subjectDetail(root, "subject"), effectivePreview(root, "preview"), saveResult(root, "save"),
            invalidation(root, "invalidation"));
    }

    private static Overview overview(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new Overview(BrowserJson.bool(value, "available", false), BrowserJson.string(value, "serverId"),
            BrowserJson.string(value, "serverName"), BrowserJson.string(value, "version"),
            BrowserJson.longValue(value, "loadedUsers", 0), BrowserJson.longValue(value, "knownUsers", 0),
            BrowserJson.longValue(value, "onlineUsers", 0), BrowserJson.longValue(value, "groups", 0),
            BrowserJson.longValue(value, "tracks", 0), BrowserJson.longValue(value, "revision", 0),
            BrowserJson.longValue(value, "lastChangedAt", 0), objects(value, "audit", BrowserReSyncLuckPermsCodec::audit));
    }

    private static AuditEntry audit(JsonObject value) {
        return new AuditEntry(BrowserJson.longValue(value, "changedAt", 0), BrowserJson.string(value, "action"),
            BrowserJson.string(value, "target"), BrowserJson.string(value, "actor"),
            BrowserJson.bool(value, "success", false), BrowserJson.string(value, "detail"));
    }

    private static UserPage userPage(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new UserPage(objects(value, "items", BrowserReSyncLuckPermsCodec::user),
            BrowserJson.string(value, "nextCursor"), BrowserJson.bool(value, "hasMore", false),
            BrowserJson.longValue(value, "total", 0), BrowserJson.longValue(value, "revision", 0));
    }

    private static UserSummary user(JsonObject value) {
        return new UserSummary(BrowserJson.string(value, "uniqueId"), BrowserJson.string(value, "username"),
            BrowserJson.string(value, "primaryGroup"), BrowserJson.bool(value, "online", false),
            BrowserJson.integer(value, "directNodes", 0), BrowserJson.string(value, "prefix"),
            BrowserJson.string(value, "suffix"), BrowserJson.longValue(value, "lastSeen", 0));
    }

    private static GroupPage groupPage(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new GroupPage(objects(value, "items", BrowserReSyncLuckPermsCodec::group),
            BrowserJson.string(value, "nextCursor"), BrowserJson.bool(value, "hasMore", false),
            BrowserJson.longValue(value, "total", 0), BrowserJson.longValue(value, "revision", 0));
    }

    private static GroupSummary group(JsonObject value) {
        return new GroupSummary(BrowserJson.string(value, "name"), BrowserJson.string(value, "displayName"),
            integer(value, "weight"), BrowserJson.integer(value, "directNodes", 0),
            BrowserJson.integer(value, "members", 0));
    }

    private static TrackDetail track(JsonObject value) {
        return new TrackDetail(BrowserJson.string(value, "name"), BrowserJson.longValue(value, "revision", 0),
            BrowserJson.strings(value, "groups"));
    }

    private static SubjectDetail subjectDetail(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new SubjectDetail(BrowserJson.longValue(value, "revision", 0), subjectRef(value, "subject"),
            BrowserJson.string(value, "name"), BrowserJson.string(value, "primaryGroup"), integer(value, "weight"),
            objects(value, "directNodes", BrowserReSyncLuckPermsCodec::node));
    }

    private static EffectivePreview effectivePreview(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new EffectivePreview(BrowserJson.string(value, "requestId"), subjectRef(value, "subject"),
            BrowserJson.string(value, "permission"), contexts(value, "contexts"),
            BrowserJson.bool(value, "allowed", false), BrowserJson.bool(value, "resolved", false),
            objects(value, "matches", BrowserReSyncLuckPermsCodec::match));
    }

    private static PreviewMatch match(JsonObject value) {
        return new PreviewMatch(node(value, "node"), subjectRef(value, "source"),
            BrowserJson.strings(value, "inheritancePath"), BrowserJson.bool(value, "effective", false),
            BrowserJson.string(value, "explanation"));
    }

    private static SaveResult saveResult(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new SaveResult(BrowserJson.string(value, "operationId"), BrowserJson.bool(value, "applied", false),
            BrowserJson.longValue(value, "revision", 0), objects(value, "conflicts", BrowserReSyncLuckPermsCodec::conflict),
            objects(value, "entities", BrowserReSyncLuckPermsCodec::appliedEntity));
    }

    private static Conflict conflict(JsonObject value) {
        return new Conflict(entityType(value, "type"), BrowserJson.string(value, "id"),
            BrowserJson.longValue(value, "expectedRevision", 0), BrowserJson.longValue(value, "actualRevision", 0),
            BrowserJson.string(value, "message"));
    }

    private static AppliedEntity appliedEntity(JsonObject value) {
        return new AppliedEntity(entityType(value, "type"), BrowserJson.string(value, "id"),
            BrowserJson.longValue(value, "revision", 0), BrowserJson.bool(value, "success", false),
            BrowserJson.string(value, "message"));
    }

    private static Invalidation invalidation(JsonObject root, String name) {
        JsonObject value = optionalObject(root, name);
        if (value == null) return null;
        return new Invalidation(BrowserJson.longValue(value, "revision", 0), entityTypes(value, "scopes"),
            BrowserJson.strings(value, "ids"), BrowserJson.string(value, "source"));
    }

    private static JsonObject contexts(Map<String, List<String>> values) {
        JsonObject result = new JsonObject();
        if (values == null) return result;
        values.forEach((key, value) -> result.add(key, strings(value)));
        return result;
    }

    private static Map<String, List<String>> contexts(JsonObject root, String name) {
        JsonElement element = BrowserJson.element(root, name);
        if (element == null || !element.isJsonObject()) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        element.getAsJsonObject().entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            result.put(entry.getKey(), value != null && value.isJsonArray() ? strings(value.getAsJsonArray()) : List.of());
        });
        return Map.copyOf(result);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        if (values != null) values.forEach(result::add);
        return result;
    }

    private static List<String> strings(JsonArray values) {
        List<String> result = new ArrayList<>();
        if (values != null) values.forEach(value -> {
            if (value != null && value.isJsonPrimitive()) result.add(value.getAsString());
        });
        return List.copyOf(result);
    }

    private static <T> JsonArray array(List<T> values, Function<T, JsonObject> encoder) {
        JsonArray result = new JsonArray();
        if (values != null) values.forEach(value -> {
            if (value != null) result.add(encoder.apply(value));
        });
        return result;
    }

    private static <T> List<T> objects(JsonObject root, String name, Function<JsonObject, T> decoder) {
        return BrowserJson.objects(root, name).stream().map(decoder).filter(value -> value != null).toList();
    }

    private static JsonObject optionalObject(JsonObject root, String name) {
        JsonElement value = BrowserJson.element(root, name);
        return value == null || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    private static Integer integer(JsonObject value, String name) {
        JsonElement element = BrowserJson.element(value, name);
        return element == null ? null : BrowserJson.integer(value, name, 0);
    }

    private static void put(JsonObject target, String name, JsonElement value) {
        if (value != null) target.add(name, value);
    }

    private static void put(JsonObject target, String name, String value) {
        if (value != null) target.addProperty(name, value);
    }

    private static void put(JsonObject target, String name, Number value) {
        if (value != null) target.addProperty(name, value);
    }

    private static void put(JsonObject target, String name, Boolean value) {
        if (value != null) target.addProperty(name, value);
    }

    private static void putEnum(JsonObject target, String name, Enum<?> value) {
        if (value != null) target.addProperty(name, value.name());
    }

    private static Action action(JsonObject value) {
        return switch (BrowserJson.string(value, "action")) {
            case "USERS" -> Action.USERS;
            case "GROUPS" -> Action.GROUPS;
            case "TRACKS" -> Action.TRACKS;
            case "SUBJECT" -> Action.SUBJECT;
            case "PREVIEW" -> Action.PREVIEW;
            case "SAVE" -> Action.SAVE;
            default -> Action.OVERVIEW;
        };
    }

    private static SubjectType subjectType(JsonObject value, String name) {
        return switch (BrowserJson.string(value, name)) {
            case "GROUP" -> SubjectType.GROUP;
            default -> SubjectType.USER;
        };
    }

    private static EntityType entityType(JsonObject value, String name) {
        return switch (BrowserJson.string(value, name)) {
            case "USER" -> EntityType.USER;
            case "TRACK" -> EntityType.TRACK;
            default -> EntityType.GROUP;
        };
    }

    private static NodeKind nodeKind(JsonObject value, String name) {
        return switch (BrowserJson.string(value, name)) {
            case "PERMISSION" -> NodeKind.PERMISSION;
            case "INHERITANCE" -> NodeKind.INHERITANCE;
            case "PREFIX" -> NodeKind.PREFIX;
            case "SUFFIX" -> NodeKind.SUFFIX;
            case "META" -> NodeKind.META;
            case "DISPLAY_NAME" -> NodeKind.DISPLAY_NAME;
            case "WEIGHT" -> NodeKind.WEIGHT;
            default -> NodeKind.UNKNOWN;
        };
    }

    private static Set<EntityType> entityTypes(JsonObject value, String name) {
        JsonElement element = BrowserJson.element(value, name);
        if (element == null || !element.isJsonArray()) return Set.of();
        Set<EntityType> result = new LinkedHashSet<>();
        element.getAsJsonArray().forEach(item -> {
            if (item != null && item.isJsonPrimitive()) result.add(entityType(item.getAsString()));
        });
        return Set.copyOf(result);
    }

    private static EntityType entityType(String value) {
        return switch (value) {
            case "USER" -> EntityType.USER;
            case "TRACK" -> EntityType.TRACK;
            default -> EntityType.GROUP;
        };
    }

    private static SubjectRef subjectRef(JsonObject value, String name) {
        JsonObject subject = optionalObject(value, name);
        return subject == null ? null : new SubjectRef(subjectType(subject, "type"), BrowserJson.string(subject, "id"));
    }

    private static NodeData node(JsonObject value, String name) {
        JsonObject node = optionalObject(value, name);
        return node == null ? null : node(node);
    }

    private static NodeData node(JsonObject value) {
        return new NodeData(BrowserJson.string(value, "id"), nodeKind(value, "kind"), BrowserJson.string(value, "key"),
            BrowserJson.bool(value, "value", false), integer(value, "priority"), contexts(value, "contexts"),
            longValue(value, "expiresAt"));
    }

    private static Long longValue(JsonObject value, String name) {
        JsonElement element = BrowserJson.element(value, name);
        return element == null ? null : BrowserJson.longValue(value, name, 0);
    }
}
