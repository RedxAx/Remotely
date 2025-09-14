package redxax.oxy.remotely.adapters;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;

public class MinecraftTextRendererAdapter implements ITextRenderer {
    private final TextRenderer tr;

    public MinecraftTextRendererAdapter() {
        this.tr = MinecraftClient.getInstance().textRenderer;
    }

    @Override
    public void draw(IDrawContext ctx, String text, int x, int y, int color, boolean shadow) {
        if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) {
            return;
        }
        Matrix4f matrix = mcCtx.getMcMatrices().peek().getPositionMatrix();
        Immediate vcp = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        String clean = text.replaceAll("literal\\{", "").replaceAll("\\[style\\{", "").replaceAll("}", "");
        if (clean.endsWith("]}")) {
            clean = clean.substring(0, clean.length() - 2);
        }

        int parsedColor = color;
        if (text.contains("color=")) {
            String colorCode = text.split("color=")[1].split(",")[0];
            try {
                parsedColor = Integer.parseInt(colorCode.replace("#", ""), 16);
            } catch (NumberFormatException e) {
                parsedColor = 0xFFFFFF;
            }
        }

        Identifier fontId = null;
        if (text.contains("font=")) {
            fontId = Identifier.tryParse(text.split("font=")[1].split("]")[0]);
        }

        Text styledText = Text.literal(clean);
        if (fontId != null) {
            styledText = styledText.getWithStyle(styledText.getStyle().withFont(fontId)).getFirst();
        }

        tr.draw(styledText, x, y, parsedColor, shadow, matrix, vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        vcp.draw();
    }

    @Override
    public void draw(String s, int i, int i1, int i2, boolean b) {

    }

    @Override
    public int getWidth(String text) {
        return tr.getWidth(text);
    }

    @Override
    public int getWidth(String s, restudio.rescreen.render.TextRenderer.FontStyle fontStyle) {
        return tr.getWidth(s);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        return tr.trimToWidth(text, maxWidth);
    }
}
