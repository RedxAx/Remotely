package redxax.oxy.remotely.ui.collaboration;

import redxax.oxy.remotely.util.BrowserSafeState;

import restudio.rescreen.theme.Accent;
import restudio.rescreen.util.Identifier;

import java.util.Map;

public final class CollaborationVisuals {
    private static final Map<Identifier, Integer> AVATAR_COLORS = BrowserSafeState.map();
    private static final Map<Integer, Accent> ACCENTS = BrowserSafeState.map();

    public static int avatarColor(Identifier avatar, int fallback) {
        if (avatar == null) {
            return fallback;
        }
        int dominant = AVATAR_COLORS.computeIfAbsent(avatar, CollaborationVisuals::stableColor);
        return (dominant & 0x00FFFFFF) == 0 ? fallback : 0xFF000000 | (dominant & 0x00FFFFFF);
    }

    private static int stableColor(Identifier avatar) {
        int value = avatar.toString().hashCode();
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        int red = 96 + (value & 0x7F);
        int green = 96 + (value >>> 8 & 0x7F);
        int blue = 96 + (value >>> 16 & 0x7F);
        return red << 16 | green << 8 | blue;
    }

    public static int blend(Iterable<Integer> colors, int fallback) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        if (colors != null) {
            for (Integer color : colors) {
                if (color == null) {
                    continue;
                }
                red += color >> 16 & 0xFF;
                green += color >> 8 & 0xFF;
                blue += color & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return fallback;
        }
        int blendedRed = (int) (red / count);
        int blendedGreen = (int) (green / count);
        int blendedBlue = (int) (blue / count);
        return 0xFF000000 | blendedRed << 16 | blendedGreen << 8 | blendedBlue;
    }

    public static Accent accent(int color) {
        return ACCENTS.computeIfAbsent(color,
            value -> Accent.fromBaseColor("Collaborator " + Integer.toHexString(value), value));
    }

    private CollaborationVisuals() {
    }
}
