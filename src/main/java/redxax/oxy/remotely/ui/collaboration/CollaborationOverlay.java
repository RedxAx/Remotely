package redxax.oxy.remotely.ui.collaboration;

import redxax.oxy.remotely.collaboration.CollaborationService;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static restudio.rescreen.render.TextRenderer.tr;

public final class CollaborationOverlay {
    private static final int MAX_MESSAGES = 4;
    private final AvatarProvider avatars;
    private final Consumer<Runnable> dispatcher;
    private final Map<String, CollaborationCursorWidget> cursors = new HashMap<>();
    private final Map<String, IconButton> badges = new HashMap<>();
    private final Map<String, CollaborationActivityWidget> activities = new HashMap<>();
    private final Map<String, Deque<ChatBubble>> messages = new HashMap<>();
    private final Map<String, Long> messageIds = new HashMap<>();
    private final Consumer<CollaborationService.Message> messageListener;
    private CollaborationService service;
    private Consumer<CollaborationService.Message> messageObserver = ignored -> {
    };

    public CollaborationOverlay(AvatarProvider avatars, Consumer<Runnable> dispatcher) {
        this.avatars = avatars != null ? avatars : new CollaborationAvatarResolver();
        this.dispatcher = dispatcher != null ? dispatcher : Runnable::run;
        messageListener = message -> this.dispatcher.accept(() -> receive(message));
    }

    public void bind(CollaborationService service, Consumer<CollaborationService.Message> observer) {
        if (this.service == service) {
            messageObserver = observer != null ? observer : ignored -> {
            };
            return;
        }
        if (this.service != null) {
            this.service.removeMessageListener(messageListener);
        }
        this.service = service;
        messageObserver = observer != null ? observer : ignored -> {
        };
        if (service != null) {
            service.addMessageListener(messageListener);
        }
    }

    public CollaborationService service() {
        return service;
    }

    public Identifier avatar(CollaborationService.Presence presence) {
        return avatars.resolve(presence != null ? presence.identity() : null);
    }

    public Identifier avatar(CollaborationService.Identity identity) {
        return avatars.resolve(identity);
    }

    public int color(CollaborationService.Presence presence) {
        if (presence == null) {
            return 0xFF4E8CFF;
        }
        return presence.customColor() ? presence.color() : CollaborationVisuals.avatarColor(avatar(presence), presence.color());
    }

    public Accent accent(CollaborationService.Presence presence) {
        return CollaborationVisuals.accent(color(presence));
    }

    public Activity activity(CollaborationService.Presence presence) {
        if (presence == null) {
            return new Activity(List.of(), false);
        }
        long now = System.currentTimeMillis();
        Deque<ChatBubble> bubbles = messages.get(presence.sessionId());
        if (bubbles == null) {
            return new Activity(List.of(), presence.typing());
        }
        bubbles.removeIf(bubble -> bubble.expiresAt() <= now);
        if (bubbles.isEmpty()) {
            messages.remove(presence.sessionId());
            return new Activity(List.of(), presence.typing());
        }
        return new Activity(bubbles.stream()
            .map(bubble -> new CollaborationActivityWidget.Message(
                bubble.id(), bubble.message(), bubble.opacity(now)))
            .toList(), presence.typing());
    }

    public IconButton badge(String slot, CollaborationService.Presence presence,
                            CollaborationService.Identity identity, int color, int maxNameWidth) {
        String name = compactName(identity != null ? identity.displayName() : null, maxNameWidth);
        IconButton badge = badges.computeIfAbsent(slot, ignored -> {
            IconButton created = new IconButton.Builder()
                .label(name)
                .size(Math.max(24, tr.getWidth(name) + 6), 18)
                .iconSize(16)
                .iconPadding(1)
                .iconOffsetX(1)
                .centered(false)
                .autoWidthOnTextChange(true)
                .animateLayout(true)
                .animateLayoutPosition(false)
                .entranceCorner(AnimatedWidget.EntranceCorner.CENTER)
                .active(false)
                .build();
            created.setCursorHoverReactive(false);
            return created;
        });
        CollaborationActivityWidget activity = activityWidget(slot, presence, 160, color);
        Accent accent = CollaborationVisuals.accent(color);
        String content = activity.showsTyping() ? "" : name;
        if (!Objects.equals(badge.getMessage(), content)) {
            badge.setMessage(content);
        }
        Identifier avatar = avatar(identity);
        if (!Objects.equals(badge.getIconId(), avatar)) {
            badge.setIcon(avatar);
        }
        badge.accentType = accent;
        if (activity.showsTyping()) {
            badge.setWidth(40);
        }
        return badge;
    }

    public IconButton renderAttachment(IDrawContext context, Attachment attachment,
                                       int screenWidth, int screenHeight) {
        if (attachment == null || attachment.anchor() == null) {
            return null;
        }
        IconButton badge = badge(attachment.slot(), attachment.presence(), attachment.identity(),
            attachment.color(), attachment.maxNameWidth());
        Bounds anchor = attachment.anchor();
        int x = switch (attachment.placement()) {
            case ABOVE, BELOW, OVERLAY -> anchor.x();
            case LEFT -> anchor.x() - badge.getWidth() - attachment.gap();
            case RIGHT -> anchor.right() + attachment.gap();
        };
        int y = switch (attachment.placement()) {
            case ABOVE -> anchor.y() - badge.getHeight() - attachment.gap();
            case BELOW -> anchor.bottom() + attachment.gap();
            case LEFT, RIGHT, OVERLAY -> anchor.y();
        };
        badge.setPosition(Math.clamp(x, 0, Math.max(0, screenWidth - badge.getWidth())),
            Math.clamp(y, 0, Math.max(0, screenHeight - badge.getHeight())));
        boolean below = attachment.placement() == Placement.BELOW;
        if (attachment.messages() && !below) {
            renderMessages(context, attachment.slot(), attachment.presence(), badge,
                Order.ABOVE, attachment.maxMessageWidth(), screenWidth, screenHeight);
        }
        badge.render(context, 0, 0, 0);
        if (attachment.typing()) {
            renderTyping(context, attachment.slot(), attachment.presence(), badge, attachment.maxMessageWidth());
        }
        if (attachment.messages() && below) {
            renderMessages(context, attachment.slot(), attachment.presence(), badge,
                Order.BELOW, attachment.maxMessageWidth(), screenWidth, screenHeight);
        }
        return badge;
    }

    public void renderAttachments(IDrawContext context, Iterable<CollaborationService.Presence> collaborators,
                                  Function<CollaborationService.Presence, Attachment> attachmentResolver,
                                  int screenWidth, int screenHeight) {
        if (collaborators == null || attachmentResolver == null) {
            return;
        }
        for (CollaborationService.Presence presence : collaborators) {
            if (presence == null || (service != null && service.isSelf(presence))) {
                continue;
            }
            Attachment attachment = attachmentResolver.apply(presence);
            if (attachment != null) {
                renderAttachment(context, attachment, screenWidth, screenHeight);
            }
        }
    }

    public void renderCursor(IDrawContext context, CollaborationService.Presence presence,
                             int x, int y, int screenWidth, int screenHeight) {
        if (presence == null || presence.identity() == null) {
            return;
        }
        CollaborationCursorWidget cursor = cursors.computeIfAbsent(presence.sessionId(),
            ignored -> new CollaborationCursorWidget(180));
        Activity activity = activity(presence);
        cursor.update(compactName(presence.identity().displayName(), 72), color(presence), avatar(presence),
            activity.messages(), activity.typing());
        cursor.render(context, x, y, screenWidth, screenHeight);
    }

    public void renderMessages(IDrawContext context, String slot, CollaborationService.Presence presence,
                               AnimatedWidget anchor, Order order, int maxMessageWidth,
                               int screenWidth, int screenHeight) {
        if (anchor == null || presence == null) {
            return;
        }
        CollaborationActivityWidget activity = activityWidget(slot, presence, maxMessageWidth, color(presence));
        if (!activity.hasMessages()) {
            return;
        }
        boolean below = order == Order.BELOW;
        int activityHeight = activity.getHeight();
        int desiredAnchorY = below ? anchor.getY() + anchor.getHeight() - 1 : anchor.getY() - 1;
        int activityAnchorY = below
            ? Math.clamp(desiredAnchorY, 0, Math.max(0, screenHeight - activityHeight))
            : Math.clamp(desiredAnchorY, Math.min(activityHeight, screenHeight), screenHeight);
        activity.renderMessages(context, anchor.getX() + anchor.getWidth(), activityAnchorY, below, screenWidth);
    }

    public void renderTyping(IDrawContext context, String slot, CollaborationService.Presence presence,
                             AnimatedWidget anchor, int maxMessageWidth) {
        if (anchor == null || presence == null) {
            return;
        }
        CollaborationActivityWidget activity = activityWidget(slot, presence, maxMessageWidth, color(presence));
        if (activity.showsTyping()) {
            int textX = anchor.getX() + 20;
            activity.renderInline(context, textX, anchor.getY(), Math.max(16, anchor.getWidth() - 22),
                anchor.getHeight());
        }
    }

    public void tick() {
        badges.values().forEach(IconButton::tick);
        cursors.values().forEach(CollaborationCursorWidget::tick);
        activities.values().forEach(CollaborationActivityWidget::tick);
    }

    public void close() {
        if (service != null) {
            service.removeMessageListener(messageListener);
            service = null;
        }
        messages.clear();
        messageIds.clear();
        cursors.clear();
        badges.clear();
        activities.clear();
    }

    private CollaborationActivityWidget activityWidget(String slot, CollaborationService.Presence presence,
                                                        int maxMessageWidth, int color) {
        CollaborationActivityWidget activity = activities.computeIfAbsent(slot,
            ignored -> new CollaborationActivityWidget(maxMessageWidth));
        Activity state = activity(presence);
        activity.update(state.messages(), state.typing(), CollaborationVisuals.accent(color));
        return activity;
    }

    private void receive(CollaborationService.Message message) {
        if (message == null || service == null || service.isOwnSession(message.authorSessionId())) {
            return;
        }
        long now = System.currentTimeMillis();
        messageIds.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        String messageId = !message.id().isBlank() ? message.id() : message.authorSessionId() + ':' + now;
        if (messageIds.putIfAbsent(messageId, now) != null) {
            return;
        }
        Deque<ChatBubble> bubbles = messages.computeIfAbsent(message.authorSessionId(),
            ignored -> new ArrayDeque<>());
        while (bubbles.size() >= MAX_MESSAGES) {
            bubbles.removeFirst();
        }
        bubbles.addLast(new ChatBubble(messageId, message.message(), now + 8000L));
        messageObserver.accept(message);
    }

    public static String compactName(String name, int maxWidth) {
        String value = name == null || name.isBlank() ? "Collaborator" : name.trim();
        while (value.length() > 1 && tr.getWidth(value) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return Objects.equals(value, name) ? value : value + "…";
    }

    public enum Order {
        ABOVE,
        BELOW
    }

    @FunctionalInterface
    public interface AvatarProvider {
        Identifier resolve(CollaborationService.Identity identity);
    }

    public enum Placement {
        ABOVE,
        BELOW,
        LEFT,
        RIGHT,
        OVERLAY
    }

    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public static Bounds of(AnimatedWidget widget) {
            return widget != null
                ? new Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())
                : new Bounds(0, 0, 0, 0);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }

    public record Attachment(String slot, CollaborationService.Presence presence,
                             CollaborationService.Identity identity, int color, Bounds anchor,
                             Placement placement, int gap, int maxNameWidth, int maxMessageWidth,
                             boolean messages, boolean typing) {
        public Attachment {
            slot = slot != null ? slot : "";
            anchor = anchor != null ? anchor : new Bounds(0, 0, 0, 0);
            placement = placement != null ? placement : Placement.ABOVE;
            gap = Math.max(0, gap);
            maxNameWidth = Math.max(24, maxNameWidth);
            maxMessageWidth = Math.max(24, maxMessageWidth);
        }

        public static Attachment above(String slot, CollaborationService.Presence presence,
                                       CollaborationService.Identity identity, int color,
                                       AnimatedWidget widget) {
            return new Attachment(slot, presence, identity, color, Bounds.of(widget),
                Placement.ABOVE, 4, 92, 160, false, true);
        }
    }

    public record Activity(List<CollaborationActivityWidget.Message> messages, boolean typing) {
        public Activity {
            messages = messages != null ? List.copyOf(messages) : List.of();
        }
    }

    private record ChatBubble(String id, String message, long expiresAt) {
        private float opacity(long now) {
            return Math.clamp((expiresAt - now) / 2500f, 0f, 1f);
        }
    }
}
