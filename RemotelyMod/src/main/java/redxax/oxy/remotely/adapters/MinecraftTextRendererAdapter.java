package redxax.oxy.remotely.adapters;

import dev.deftu.omnicore.api.client.render.OmniTextRenderer;
import net.minecraft.network.chat.Component;
//#if MC >= 1.21.9
import net.minecraft.network.chat.FontDescription;
//#endif
import net.minecraft.network.chat.Style;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#endif
//#if MC < 1.21.11
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;

public class MinecraftTextRendererAdapter implements ITextRenderer {

    public MinecraftTextRendererAdapter() {}

    @Override
    public void draw(IDrawContext ctx, String text, int x, int y, int color, boolean shadow) {
        if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) return;
        mcCtx.drawText(text, x, y, color, shadow);
    }

    @Override
    public void drawStyled(IDrawContext ctx, Object text, int x, int y, int color, boolean shadow) {
        if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) return;
        mcCtx.drawStyledText(text, x, y, color, shadow);
    }

    @Override
    public void draw(String s, int i, int i1, int i2, boolean b) {
        draw(null, s, i, i1, i2, b);
    }

    @Override
    public int getWidth(String text) {
        return OmniTextRenderer.width(text);
    }

    @Override
    public int getWidth(String s, restudio.rescreen.render.TextRenderer.FontStyle fontStyle) {
        return getWidth(s);
    }

    @Override
    public int getWidth(String text, Object font) {
        //#if MC >= 1.21.11
        if (font instanceof Identifier rl) {
        //#endif
        //#if MC < 1.21.11
        //$$ if (font instanceof ResourceLocation rl) {
        //#endif
            return OmniTextRenderer.width(Component.literal(text).setStyle(Style.EMPTY.withFont(
                //#if MC >= 1.21.9
                new FontDescription.Resource(rl)
                //#else
                //$$ rl
                //#endif
            )));
        }
        return getWidth(text);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        int textWidth = getWidth(text);
        if (textWidth <= maxWidth) {
            return text;
        }
        StringBuilder trimmed = new StringBuilder();
        for (char c : text.toCharArray()) {
            trimmed.append(c);
            if (getWidth(trimmed.toString()) > maxWidth) {
                trimmed.deleteCharAt(trimmed.length() - 1);
                break;
            }
        }
        return trimmed.toString();
    }
}
