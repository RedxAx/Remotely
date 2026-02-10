package redxax.oxy.remotely.adapters;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import redxax.oxy.remotely.rematrix.RematrixContext;
import redxax.oxy.remotely.rematrix.mc.RematrixMcContext;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.IMatrixStack;
import restudio.rescreen.util.ResourceManager;

public class MinecraftDrawContextAdapter implements IDrawContext {
    private final RematrixContext ctx;

    public MinecraftDrawContextAdapter(@NotNull RematrixContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public IMatrixStack getMatrices() {
        return new IMatrixStack() {
            @Override public void push() { ctx.matrices().push(); }
            @Override public void pop() { ctx.matrices().pop(); }
            @Override public void translate(float x, float y, float z) { ctx.matrices().translate(x, y, z); }
            @Override public void scale(float x, float y, float z) { ctx.matrices().scale(x, y, z); }
            @Override public void rotate(float angle, float x, float y, float z) { ctx.matrices().rotate(angle, x, y, z); }
            @Override public void multiply(float angle) { ctx.matrices().multiply(angle); }
        };
    }

    @Override public void pushScissorState() { ctx.scissors().pushState(); }
    @Override public void popScissorState() { ctx.scissors().popState(); }
    @Override public void clearScissor() { ctx.scissors().clear(); }

    @Override
    public void enableScissor(float x1, float y1, float x2, float y2) {
        ctx.scissors().enable(x1, y1, x2, y2);
    }

    @Override
    public boolean scissorsContains(int i, int i1) {
        return ctx.scissors().contains(i, i1);
    }

    @Override
    public void disableScissor() {
        ctx.scissors().disable();
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.fill(x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void fillGradient(int i, int i1, int i2, int i3, int i4, int i5) {
        fillGradient(i, i1, i2, i3, i4, i5, false);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2, boolean horizontal) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.fillGradient(x1, y1, x2, y2, color1, color2, horizontal);
        }
    }

    @Override public void fillRoundedRectWithBorders(int i, int i1, int i2, int i3, float v, int i4, int i5, int i6) {}
    @Override public void drawAnimatedCornerGradient(float v, float v1, float v2, float v3, int i) {}

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.drawText(text, x, y, color, shadow);
        }
    }

    @Override
    public void drawStyledText(Object text, int x, int y, int color, boolean shadow) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.drawStyledText(text, x, y, color, shadow);
        }
    }

    @Override
    public void drawBufferedImage(BufferedImage image, float x, float y, float width, float height) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.drawBufferedImage(image, x, y, width, height);
        }
    }

    @Override
    public void drawPixelArt(BufferedImage bufferedImage, float x, float y, float width, float height) {
        drawBufferedImage(bufferedImage, x, y, width, height);
    }

    @Override
    public void drawBufferedImage(restudio.rescreen.util.Identifier identifier, float v, float v1, float v2, float v3) {
        drawBufferedImage(ResourceManager.getInstance().getImage(identifier), v, v1, v2, v3);
    }

    @Override
    public void drawPixelArt(restudio.rescreen.util.Identifier identifier, float x, float y, float width, float height) {
        drawBufferedImage(identifier, x, y, width, height);
    }

    @Override
    public void drawInvertedRect(float x1, float y1, float x2, float y2) {
        if (ctx instanceof RematrixMcContext mc) {
            mc.drawInvertedRect(x1, y1, x2, y2);
        }
    }
}
