package redxax.oxy.remotely.collaboration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class CollaborationService {
    private static final Channel DISCONNECTED = new Channel() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void publishPresence(PresenceUpdate update) {
        }

        @Override
        public void publishMessage(MessageDraft message) {
        }
    };

    private final ConcurrentHashMap<String, ResourceChange> changes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedMessage> messages = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Consumer<Message>> messageListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Consumer<List<Presence>>> presenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Consumer<ResourceChange>> resourceChangeListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Runnable> activityListeners = new CopyOnWriteArraySet<>();
    private volatile Channel channel = DISCONNECTED;
    private volatile PresenceState presenceState = PresenceState.empty();
    private volatile Identity localIdentity;
    private volatile PresenceUpdate localPresence = PresenceUpdate.inactive();

    public CollaborationService(String clientId) {
        String subjectId = clientId != null ? clientId.trim() : "";
        localIdentity = subjectId.isBlank() ? null : new Identity(subjectId, "Collaborator", "", "client");
    }

    public void bind(Channel channel) {
        this.channel = channel != null ? channel : DISCONNECTED;
    }

    public Channel channel() {
        return channel;
    }

    public boolean publishPresence(String resourceType, String resourceId, String viewId,
                                   double x, double y, boolean active, boolean typing) {
        return publishPresence(new Target(resourceType, resourceId, viewId), x, y, active, typing);
    }

    public boolean publishPresence(Target target, double x, double y, boolean active, boolean typing) {
        PresenceUpdate update = new PresenceUpdate(target, x, y, active, typing);
        localPresence = update;
        if (!channel.available()) {
            return false;
        }
        channel.publishPresence(update);
        return true;
    }

    public boolean publishMessage(String message) {
        return publishMessage(localPresence.target(), message);
    }

    public boolean publishMessage(Target target, String message) {
        String text = message != null ? message.trim() : "";
        if (text.isBlank() || !channel.available()) {
            return false;
        }
        channel.publishMessage(new MessageDraft(target, text));
        return true;
    }

    public PresenceUpdate localPresence() {
        return localPresence;
    }

    public void identify(Identity identity) {
        localIdentity = hasSubject(identity) ? identity : null;
    }

    public boolean acceptSnapshot(String nextSelfSessionId, List<Presence> nextCollaborators) {
        return acceptSnapshot(nextSelfSessionId, null, List.of(), nextCollaborators);
    }

    public boolean acceptSnapshot(String nextSelfSessionId, Identity nextSelfIdentity, List<String> nextOwnSessionIds,
                                  List<Presence> nextCollaborators) {
        PresenceState previous = presenceState;
        String selfSessionId = nextSelfSessionId != null ? nextSelfSessionId : "";
        ArrayList<Presence> received = new ArrayList<>();
        if (nextCollaborators != null) {
            nextCollaborators.stream()
                .filter(Objects::nonNull)
                .filter(collaborator -> !collaborator.sessionId().isBlank())
                .forEach(received::add);
        }
        Identity selfIdentity = hasSubject(nextSelfIdentity) ? nextSelfIdentity : received.stream()
            .filter(collaborator -> selfSessionId.equals(collaborator.sessionId()))
            .map(Presence::identity)
            .filter(this::hasSubject)
            .findFirst()
            .orElse(localIdentity);
        LinkedHashSet<String> ownSessionIds = new LinkedHashSet<>();
        if (!selfSessionId.isBlank()) {
            ownSessionIds.add(selfSessionId);
        }
        if (nextOwnSessionIds != null) {
            nextOwnSessionIds.stream().filter(Objects::nonNull).filter(id -> !id.isBlank()).forEach(ownSessionIds::add);
        }
        LinkedHashMap<String, Presence> collaborators = new LinkedHashMap<>();
        for (Presence collaborator : received) {
            if (!ownSessionIds.contains(collaborator.sessionId())) {
                collaborators.put(collaborator.sessionId(), collaborator);
            }
        }
        ArrayList<Presence> snapshot = new ArrayList<>(collaborators.values());
        snapshot.sort(Comparator.comparing(value -> value.identity() != null ? value.identity().displayName() : "",
            String.CASE_INSENSITIVE_ORDER));
        PresenceState current = new PresenceState(Map.copyOf(collaborators), List.copyOf(snapshot), Set.copyOf(ownSessionIds), selfIdentity);
        presenceState = current;
        boolean changed = !activitySignature(previous.snapshot()).equals(activitySignature(current.snapshot()));
        if (changed) {
            activityListeners.forEach(Runnable::run);
        }
        presenceListeners.forEach(listener -> listener.accept(current.snapshot()));
        return changed;
    }

    public List<Presence> snapshot() {
        return presenceState.snapshot();
    }

    public List<Presence> at(String resourceType, String resourceId) {
        return snapshot().stream()
            .filter(presence -> presence.target().matches(resourceType, resourceId))
            .toList();
    }

    public Presence presence(String sessionId) {
        return sessionId != null ? presenceState.collaborators().get(sessionId) : null;
    }

    public boolean isSelf(Presence presence) {
        if (presence == null) {
            return false;
        }
        return presenceState.ownSessionIds().contains(presence.sessionId());
    }

    public boolean isOwnSession(String sessionId) {
        return sessionId != null && presenceState.ownSessionIds().contains(sessionId);
    }

    public boolean isOwnChange(ResourceChange change) {
        return change != null && isOwnSession(change.authorSessionId());
    }

    private boolean hasSubject(Identity identity) {
        return identity != null && identity.subjectId() != null && !identity.subjectId().isBlank();
    }

    public void acceptResourceChange(ResourceChange change) {
        if (change != null && !change.target().resourceType().isBlank() && !change.target().resourceId().isBlank()) {
            changes.put(change.target().key(), change);
            resourceChangeListeners.forEach(listener -> listener.accept(change));
        }
    }

    public ResourceChange resourceChange(String resourceType, String resourceId) {
        return changes.get(new Target(resourceType, resourceId, "").key());
    }

    public boolean acceptMessage(Message message) {
        if (message == null || message.id().isBlank() || message.authorSessionId().isBlank()
            || message.message().isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        messages.entrySet().removeIf(entry -> now - entry.getValue().receivedAt() > 10_000L);
        if (messages.putIfAbsent(message.id(), new CachedMessage(message, now)) != null) {
            return false;
        }
        messageListeners.forEach(listener -> listener.accept(message));
        return true;
    }

    public void addMessageListener(Consumer<Message> listener) {
        if (listener == null) {
            return;
        }
        messageListeners.add(listener);
        long now = System.currentTimeMillis();
        messages.values().stream()
            .filter(message -> now - message.receivedAt() <= 10_000L)
            .sorted(Comparator.comparingLong(value -> value.message().sentAt()))
            .forEach(message -> listener.accept(message.message()));
    }

    public void removeMessageListener(Consumer<Message> listener) {
        messageListeners.remove(listener);
    }

    public void addPresenceListener(Consumer<List<Presence>> listener) {
        if (listener != null) {
            presenceListeners.add(listener);
            listener.accept(snapshot());
        }
    }

    public void removePresenceListener(Consumer<List<Presence>> listener) {
        presenceListeners.remove(listener);
    }

    public void addResourceChangeListener(Consumer<ResourceChange> listener) {
        if (listener != null) {
            resourceChangeListeners.add(listener);
        }
    }

    public void removeResourceChangeListener(Consumer<ResourceChange> listener) {
        resourceChangeListeners.remove(listener);
    }

    public void addActivityListener(Runnable listener) {
        if (listener != null) {
            activityListeners.add(listener);
        }
    }

    public void removeActivityListener(Runnable listener) {
        activityListeners.remove(listener);
    }

    public void connectionReady() {
        if (channel.available()) {
            channel.publishPresence(localPresence);
        }
    }

    public void connectionLost() {
        clearTransientState(false);
    }

    public void clear() {
        clearTransientState(true);
    }

    private void clearTransientState(boolean resetLocalPresence) {
        presenceState = PresenceState.empty();
        changes.clear();
        messages.clear();
        if (resetLocalPresence) {
            localPresence = PresenceUpdate.inactive();
        }
        activityListeners.forEach(Runnable::run);
        presenceListeners.forEach(listener -> listener.accept(List.of()));
    }

    private String activitySignature(Iterable<Presence> presence) {
        ArrayList<String> activity = new ArrayList<>();
        for (Presence collaborator : presence) {
            Identity identity = collaborator.identity();
            activity.add(collaborator.sessionId() + '\u0000' + collaborator.resourceType() + '\u0000'
                + collaborator.resourceId() + '\u0000' + collaborator.viewId() + '\u0000' + collaborator.active()
                + '\u0000' + collaborator.typing() + '\u0000'
                + (identity != null ? identity.displayName() : ""));
        }
        activity.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join("\u0001", activity);
    }

    public interface Channel {
        boolean available();

        void publishPresence(PresenceUpdate update);

        void publishMessage(MessageDraft message);
    }

    public record Target(String resourceType, String resourceId, String viewId) {
        public Target {
            resourceType = resourceType != null ? resourceType : "";
            resourceId = resourceId != null ? resourceId : "";
            viewId = viewId != null ? viewId : "";
        }

        public boolean matches(String type, String id) {
            return Objects.equals(resourceType, type) && Objects.equals(resourceId, id);
        }

        public String key() {
            return resourceType + '\u0000' + resourceId;
        }
    }

    public record PresenceUpdate(Target target, double x, double y, boolean active, boolean typing) {
        public PresenceUpdate {
            target = target != null ? target : new Target("", "", "");
            x = Math.clamp(x, 0.0, 1.0);
            y = Math.clamp(y, 0.0, 1.0);
        }

        public static PresenceUpdate inactive() {
            return new PresenceUpdate(new Target("", "", ""), 0.0, 0.0, false, false);
        }
    }

    public record MessageDraft(Target target, String message) {
        public MessageDraft {
            target = target != null ? target : new Target("", "", "");
            message = message != null ? message.trim() : "";
        }
    }

    public record Identity(String subjectId, String displayName, String avatar, String source) {
        public Identity {
            subjectId = subjectId != null ? subjectId : "";
            displayName = displayName != null && !displayName.isBlank() ? displayName : "Collaborator";
            avatar = avatar != null ? avatar : "";
            source = source != null ? source : "";
        }
    }

    public record Presence(String sessionId, String clientId, Identity identity, String resourceType, String resourceId,
                           String viewId, double x, double y, boolean active, boolean typing, int color, boolean customColor, long updatedAt) {
        public Presence {
            sessionId = sessionId != null ? sessionId : "";
            clientId = clientId != null ? clientId : "";
            resourceType = resourceType != null ? resourceType : "";
            resourceId = resourceId != null ? resourceId : "";
            viewId = viewId != null ? viewId : "";
        }

        public Target target() {
            return new Target(resourceType, resourceId, viewId);
        }
    }

    public record ResourceChange(String type, String resourceId, String authorSessionId, Identity author,
                                 long changedAt, boolean deleted) {
        public ResourceChange {
            type = type != null ? type : "";
            resourceId = resourceId != null ? resourceId : "";
            authorSessionId = authorSessionId != null ? authorSessionId : "";
        }

        public Target target() {
            return new Target(type, resourceId, "");
        }
    }

    public record Message(String id, String authorSessionId, Identity author, String resourceType, String resourceId,
                          int color, String message, long sentAt) {
        public Message {
            id = id != null ? id : "";
            authorSessionId = authorSessionId != null ? authorSessionId : "";
            resourceType = resourceType != null ? resourceType : "";
            resourceId = resourceId != null ? resourceId : "";
            message = message != null ? message : "";
        }

        public Target target() {
            return new Target(resourceType, resourceId, "");
        }
    }

    private record CachedMessage(Message message, long receivedAt) {
    }

    private record PresenceState(Map<String, Presence> collaborators, List<Presence> snapshot, Set<String> ownSessionIds,
                                 Identity selfIdentity) {
        private static PresenceState empty() {
            return new PresenceState(Map.of(), List.of(), Set.of(), null);
        }
    }
}
