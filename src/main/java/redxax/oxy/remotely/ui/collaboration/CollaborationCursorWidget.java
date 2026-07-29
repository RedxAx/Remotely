package redxax.oxy.remotely.ui.collaboration;

import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.theme.Accent;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.util.Identifier;

import java.util.List;
import java.util.Objects;

import static restudio.rescreen.config.Config.mouseSize;
import static restudio.rescreen.config.Config.tailSize;
import static restudio.rescreen.render.TextRenderer.tr;

public final class CollaborationCursorWidget {
    private static final int CURSOR_SIZE = (int) mouseSize + 2;
    private static final int TAIL_OFFSET = 4;
    private final IconButton main = new IconButton.Builder().size(CURSOR_SIZE, CURSOR_SIZE).iconSize(CURSOR_SIZE)
        .iconPadding(1).centered(true).animateLayout(false)
        .entranceCorner(AnimatedWidget.EntranceCorner.CENTER).active(false).build();
    private final AnimatedButton tail = new AnimatedButton.Builder().size((int) tailSize, (int) tailSize)
        .animateLayout(false).enableHoverColors(false)
        .entranceCorner(AnimatedWidget.EntranceCorner.CENTER).active(true).build();
    private final IconButton label = new IconButton.Builder().size(80, 15).iconPadding(4).animateLayout(true)
        .animateLayoutPosition(false).centered(true)
        .entranceCorner(AnimatedWidget.EntranceCorner.CENTER).active(false).build();
    private final CollaborationActivityWidget activity;
    private Identifier avatarId;
    private String name = "";
    private int color;

    public CollaborationCursorWidget(int maxMessageWidth) {
        activity = new CollaborationActivityWidget(maxMessageWidth);
        main.setCursorHoverReactive(false);
        tail.setCursorHoverReactive(false);
        label.setCursorHoverReactive(false);
    }

    public void update(String nextName, int nextColor, Identifier nextAvatar,
                       List<CollaborationActivityWidget.Message> messages, boolean typing) {
        Accent accent = CollaborationVisuals.accent(nextColor);
        tail.accentType = accent;
        if (!Objects.equals(name, nextName) || color != nextColor) {
            name = nextName != null ? nextName : "Collaborator";
            color = nextColor;
            main.accentType = accent;
            label.accentType = accent;
            label.setMessage(name);
            label.setWidth(Math.max(18, tr.getWidth(name) + 8));
        }
        activity.update(messages, typing, accent);
        if (!Objects.equals(avatarId, nextAvatar)) {
            avatarId = nextAvatar;
            main.setIcon(nextAvatar);
        }
    }

    public void tick() {
        main.tick();
        tail.tick();
        label.tick();
        activity.tick();
    }

    public void render(IDrawContext context, int x, int y, int screenWidth, int screenHeight) {
        boolean inlineTyping = activity.showsTyping();
        label.setMessage(inlineTyping ? "" : name);
        label.setWidth(inlineTyping ? 26 : Math.max(18, tr.getWidth(name) + 8));
        int labelX = Math.clamp(x + CURSOR_SIZE + 4, 0, Math.max(0, screenWidth - label.getWidth()));
        int labelY = Math.clamp(y + CURSOR_SIZE + 1, 0, Math.max(0, screenHeight - label.getHeight()));
        main.setPosition(x + 1, y + 3);
        tail.setPosition(main.getX() + CURSOR_SIZE / 2 + TAIL_OFFSET,
            main.getY() + CURSOR_SIZE / 2 + TAIL_OFFSET);
        label.setPosition(labelX, labelY);
        tail.render(context, x, y, 0);
        main.render(context, x, y, 0);
        label.render(context, x, y, 0);
        if (inlineTyping) {
            activity.renderInline(context, labelX, labelY, label.getWidth(), label.getHeight());
        }
        if (activity.hasMessages()) {
            int desiredAnchorY = labelY + label.getHeight() - 1;
            int activityAnchorY = Math.clamp(desiredAnchorY, 0,
                Math.max(0, screenHeight - activity.getHeight()));
            activity.renderMessages(context, labelX + label.getWidth(), activityAnchorY, true, screenWidth);
        }
    }
}
