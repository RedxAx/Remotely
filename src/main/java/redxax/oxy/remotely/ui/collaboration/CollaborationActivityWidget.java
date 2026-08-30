package redxax.oxy.remotely.ui.collaboration;

import restudio.rescreen.config.Config;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static restudio.rescreen.render.TextRenderer.tr;

public final class CollaborationActivityWidget {
    public record Message(String id, String text, float opacity) {
        public Message {
            id = id == null ? "" : id;
            text = text == null ? "" : text;
            opacity = Math.clamp(opacity, 0f, 1f);
        }
    }

    private static final int MESSAGE_GAP = 2;
    private static final int MAX_MESSAGES = 4;
    private final int maxMessageWidth;
    private final Map<String, MessageBubble> messageBubbles = new LinkedHashMap<>();
    private final List<MessageBubble> visibleMessages = new ArrayList<>();
    private final AnimatedButton[] typingDots = {
        new AnimatedButton.Builder().size(4, 4).animateLayout(false).entranceAnimation(false).active(false).build(),
        new AnimatedButton.Builder().size(4, 4).animateLayout(false).entranceAnimation(false).active(false).build(),
        new AnimatedButton.Builder().size(4, 4).animateLayout(false).entranceAnimation(false).active(false).build()
    };
    private boolean typing;

    public CollaborationActivityWidget(int maxMessageWidth) {
        this.maxMessageWidth = Math.max(24, maxMessageWidth);
        for (AnimatedButton dot : typingDots) {
            dot.setCursorHoverReactive(false);
        }
    }

    public void update(List<Message> nextMessages, boolean nextTyping, Accent accent) {
        List<Message> messages = nextMessages == null ? List.of() : nextMessages;
        int offset = Math.max(0, messages.size() - MAX_MESSAGES);
        List<Message> visible = messages.subList(offset, messages.size());
        messageBubbles.keySet().retainAll(visible.stream().map(Message::id).toList());
        visibleMessages.clear();
        for (Message message : visible) {
            MessageBubble bubble = messageBubbles.computeIfAbsent(message.id(), ignored -> new MessageBubble());
            bubble.update(message.text(), message.opacity(), accent, maxMessageWidth);
            if (!bubble.isBlank()) {
                visibleMessages.add(bubble);
            }
        }
        typing = nextTyping;
        for (AnimatedButton dot : typingDots) {
            dot.accentType = accent;
            dot.setOpacity(1f);
        }
    }

    public boolean isVisible() {
        return !visibleMessages.isEmpty() || typing;
    }

    public boolean showsTyping() {
        return typing;
    }

    public boolean hasMessages() {
        return !visibleMessages.isEmpty();
    }

    public int getWidth() {
        return visibleMessages.stream().mapToInt(MessageBubble::getWidth).max().orElse(0);
    }

    public int getHeight() {
        return visibleMessages.stream().mapToInt(MessageBubble::getHeight).sum()
            + Math.max(0, visibleMessages.size() - 1) * MESSAGE_GAP;
    }

    public void tick() {
        messageBubbles.values().forEach(MessageBubble::tick);
        for (AnimatedButton dot : typingDots) {
            dot.tick();
        }
    }

    public void renderMessages(IDrawContext context, int anchorRight, int anchorY, boolean below, int screenWidth) {
        int offset = 0;
        for (int index = visibleMessages.size() - 1; index >= 0; index--) {
            MessageBubble bubble = visibleMessages.get(index);
            if (below) {
                bubble.targetOffset(offset);
                offset += bubble.getHeight() + MESSAGE_GAP;
            } else {
                offset -= bubble.getHeight();
                bubble.targetOffset(offset);
                offset -= MESSAGE_GAP;
            }
        }
        if (below) {
            for (int index = visibleMessages.size() - 1; index >= 0; index--) {
                MessageBubble bubble = visibleMessages.get(index);
                int bubbleX = Math.clamp(anchorRight - bubble.getWidth(), 0, Math.max(0, screenWidth - bubble.getWidth()));
                bubble.render(context, bubbleX, anchorY);
            }
            return;
        }
        for (MessageBubble bubble : visibleMessages) {
            int bubbleX = Math.clamp(anchorRight - bubble.getWidth(), 0, Math.max(0, screenWidth - bubble.getWidth()));
            bubble.render(context, bubbleX, anchorY);
        }
    }

    public void renderMessages(IDrawContext context, int anchorRight, int anchorY, int screenWidth) {
        renderMessages(context, anchorRight, anchorY, false, screenWidth);
    }

    public void renderInline(IDrawContext context, int x, int y, int width, int height) {
        if (!showsTyping()) {
            return;
        }
        int dotsWidth = 16;
        int startX = x + Math.max(0, (width - dotsWidth) / 2);
        int baseY = y + Math.max(0, (height - 4) / 2) - 1;
        double phase = System.currentTimeMillis() / 145.0;
        for (int index = 0; index < typingDots.length; index++) {
            AnimatedButton dot = typingDots[index];
            int lift = (int) Math.round((Math.sin(phase + index * 1.8) + 1.0) * 1.5);
            dot.setPosition(startX + index * 6, baseY - lift);
            dot.render(context, 0, 0, 0);
        }
    }

    private static List<String> wrap(String message, int width) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : message.split("\\R", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line.toString() + " " + word;
                if (tr.getWidth(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                splitWord(word, width, lines, line);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private static void splitWord(String word, int width, List<String> lines, StringBuilder remainder) {
        StringBuilder part = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            if (!part.isEmpty() && tr.getWidth(part.toString() + character) > width) {
                lines.add(part.toString());
                part.setLength(0);
            }
            part.append(character);
        }
        remainder.append(part);
    }

    private static final class MessageBubble {
        private final AnimatedButton body = new AnimatedButton.Builder().size(24, 16).animateLayout(false)
            .entranceCorner(AnimatedWidget.EntranceCorner.CENTER).active(false).build();
        private final List<AnimatedButton> lines = new ArrayList<>();
        private double targetOffsetY;
        private double offsetY;
        private boolean offsetInitialized;

        private MessageBubble() {
            body.setCursorHoverReactive(false);
        }

        private void update(String message, float opacity, Accent accent, int maxWidth) {
            List<String> nextLines = wrap(message, maxWidth);
            while (lines.size() < nextLines.size()) {
                AnimatedButton line = new AnimatedButton.Builder().size(24, ITextRenderer.fontHeight + 1).animateLayout(false)
                    .transparent(true).entranceCorner(AnimatedWidget.EntranceCorner.CENTER).active(false).build();
                line.setCursorHoverReactive(false);
                lines.add(line);
            }
            int contentWidth = 0;
            for (int index = 0; index < lines.size(); index++) {
                AnimatedButton line = lines.get(index);
                boolean visible = index < nextLines.size();
                line.visible = visible;
                if (visible) {
                    String text = nextLines.get(index);
                    line.setMessage(text);
                    line.setOpacity(opacity);
                    line.accentType = accent;
                    contentWidth = Math.max(contentWidth, tr.getWidth(text));
                }
            }
            body.setWidth(Math.max(24, contentWidth + 8));
            body.setHeight(Math.max(16, nextLines.size() * (ITextRenderer.fontHeight + 1) + 4));
            body.setOpacity(opacity);
            body.accentType = accent;
        }

        private boolean isBlank() {
            return lines.stream().noneMatch(line -> line.visible && !line.getMessage().isBlank());
        }

        private int getWidth() {
            return body.getWidth();
        }

        private int getHeight() {
            return body.getHeight();
        }

        private void tick() {
            body.tick();
            lines.forEach(AnimatedButton::tick);
        }

        private void targetOffset(double targetOffsetY) {
            this.targetOffsetY = targetOffsetY;
            if (!offsetInitialized) {
                offsetY = targetOffsetY;
                offsetInitialized = true;
            }
        }

        private void render(IDrawContext context, int x, int anchorY) {
            if (!Config.animationsEnabled) {
                offsetY = targetOffsetY;
            } else {
                double factor = 1.0 - Math.exp(-18.0 * Math.max(0f, Config.deltaTime));
                offsetY += (targetOffsetY - offsetY) * factor;
                if (Math.abs(targetOffsetY - offsetY) < 0.05) {
                    offsetY = targetOffsetY;
                }
            }
            body.setPosition(x, anchorY + (int) Math.round(offsetY));
            body.render(context, 0, 0, 0);
            int lineY = body.getY() + 2;
            for (AnimatedButton line : lines) {
                if (!line.visible) {
                    continue;
                }
                line.setPosition(body.getX() + 4, lineY);
                line.setWidth(Math.max(1, body.getWidth() - 8));
                line.render(context, 0, 0, 0);
                lineY += ITextRenderer.fontHeight + 1;
            }
        }
    }
}
