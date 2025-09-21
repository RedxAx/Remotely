package redxax.oxy.remotely.adapters;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import restudio.rebase.ui.text.StyledText;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.ITextRenderer;

public class MinecraftTextRendererAdapter implements ITextRenderer {
    private final TextRenderer tr;

    public MinecraftTextRendererAdapter() {
        this.tr = MinecraftClient.getInstance().textRenderer;
    }

    @Override
    public void draw(IDrawContext ctx, String text, int x, int y, int color, boolean shadow) {
        if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) return;
        Matrix4f matrix = mcCtx.getMcMatrices().peek().getPositionMatrix();
        Immediate vcp = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        tr.draw(text, (float)x, (float)y, color, shadow, matrix, vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        vcp.draw();
    }

    @Override
    public void drawStyled(IDrawContext ctx, Object text, int x, int y, int color, boolean shadow) {
        if (text instanceof net.minecraft.text.Text mcText) {
            if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) return;
            Matrix4f matrix = mcCtx.getMcMatrices().peek().getPositionMatrix();
            Immediate vcp = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

            int finalColor = (color == 0) ? 0xFFFFFFFF : color;

            tr.draw(mcText, (float) x, (float) y, finalColor, shadow, matrix, vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            vcp.draw();
        } else if (text instanceof StyledText styledText) {
            if ((styledText.color >> 24 & 0xFF) == 0) {
                return;
            }
            if (!(ctx instanceof MinecraftDrawContextAdapter mcCtx)) return;
            Matrix4f matrix = mcCtx.getMcMatrices().peek().getPositionMatrix();
            Immediate vcp = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

            net.minecraft.text.MutableText renderText = net.minecraft.text.Text.literal(styledText.text);
            if (styledText.font instanceof net.minecraft.util.Identifier fontId) {
                renderText.setStyle(net.minecraft.text.Style.EMPTY.withFont(fontId));
            }

            tr.draw(renderText, (float) x, (float) y, styledText.color, shadow, matrix, vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            vcp.draw();
        } else {
            draw(ctx, String.valueOf(text), x, y, color, shadow);
        }
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