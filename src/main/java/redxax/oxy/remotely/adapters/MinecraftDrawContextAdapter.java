package redxax.oxy.remotely.adapters;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;
import restudio.rescreen.platform.IMatrixStack;
import restudio.rescreen.platform.IDrawContext;
//? if < 1.21.6
import net.minecraft.client.util.math.MatrixStack;
import restudio.rescreen.render.TextRenderer;

import java.awt.image.BufferedImage;
import redxax.oxy.remotely.util.ImageUtil;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;

public class MinecraftDrawContextAdapter implements IDrawContext {
    private final DrawContext dc;
    //? if >= 1.21.6 {
    /*private final Matrix3x2fStack matrices;
    *///?} else {
    private final MatrixStack matrices;
     //?}
    private final float renderScale;

    public MinecraftDrawContextAdapter(DrawContext dc, float renderScale) {
        this.dc = dc;
        this.matrices = dc.getMatrices();
        this.renderScale = renderScale;
    }

    public MinecraftDrawContextAdapter(DrawContext dc) {
        this(dc, 1.0f);
    }

    public DrawContext getMcContext() {
        return dc;
    }

    //? if >= 1.21.6 {
    /*public Matrix3x2fStack getMcMatrices() {
        return matrices;
    }
    *///?} else {
    public MatrixStack getMcMatrices() {
        return matrices;
    }
    //?}

    @Override
    public IMatrixStack getMatrices() {
        return new IMatrixStack() {
            @Override public void push() {
                //? if >= 1.21.6 {
                /*matrices.pushMatrix();
                *///?} else {
                matrices.push();
                 //?}
            }
            @Override public void pop() {
                //? if >= 1.21.6 {
                /*matrices.popMatrix();
                *///?} else {
                matrices.pop();
                 //?}
            }
            @Override public void translate(float x, float y, float z) {
                //? if >= 1.21.6 {
                /*matrices.translate(x, y);
                *///?} else {
                matrices.translate(x, y, z);
                 //?}
            }
            @Override public void scale(float v, float v1, float v2) {
                //? if >= 1.21.6 {
                /*matrices.scale(v, v1);
                *///?} else {
                matrices.scale(v, v1, v2);
                 //?}
            }
            @Override public void rotate(float v, float v1, float v2, float v3) {
                //? if >= 1.21.6 {
                /*matrices.rotate((float) Math.toRadians(v));
                *///?} else {
                matrices.multiply(RotationAxis.of(new Vector3f(v1, v2, v3)).rotationDegrees(v));
                 //?}
            }
            @Override public void multiply(float v) {
                //? if >= 1.21.6 {
                /*matrices.rotate((float) Math.toRadians(v));
                *///?} else {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(v));
                 //?}
            }
        };
    }

    @Override public void pushScissorState() {}
    @Override public void popScissorState() {}
    @Override public void clearScissor() {}

    @Override
    public void enableScissor(float x1, float y1, float x2, float y2) {
        //? if >= 1.21.6 {
        /*dc.enableScissor((int) x1, (int) y1, (int) x2, (int) y2);
        *///?} else {
        dc.enableScissor((int) (x1 * renderScale), (int) (y1 * renderScale), (int) (x2 * renderScale), (int) (y2 * renderScale));
         //?}
    }

    @Override
    public boolean scissorsContains(int i, int i1) {
        //? if >= 1.21.6 {
        /*return dc.scissorContains(i, i1);
        *///?} else {
        return dc.scissorContains((int)(i * renderScale), (int)(i1 * renderScale));
         //?}
    }

    @Override
    public void disableScissor() {
        dc.disableScissor();
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        dc.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void fillGradient(int i, int i1, int i2, int i3, int i4, int i5) {
        dc.fillGradient(i, i1, i2, i3, i4, i5);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2, boolean horizontal) {
        if (!horizontal) {
            dc.fillGradient(x1, y1, x2, y2, color1, color2);
        } else {
            float a1 = (float)(color1 >> 24 & 255);
            float r1 = (float)(color1 >> 16 & 255);
            float g1 = (float)(color1 >> 8 & 255);
            float b1 = (float)(color1 & 255);

            float a2 = (float)(color2 >> 24 & 255);
            float r2 = (float)(color2 >> 16 & 255);
            float g2 = (float)(color2 >> 8 & 255);
            float b2 = (float)(color2 & 255);

            int width = x2 - x1;
            if (width <= 0) return;

            for (int i = 0; i < width; i++) {
                float t = (width == 1) ? 0.0f : (float) i / (float) (width - 1);

                int a = (int) (a1 * (1 - t) + a2 * t);
                int r = (int) (r1 * (1 - t) + r2 * t);
                int g = (int) (g1 * (1 - t) + g2 * t);
                int b = (int) (b1 * (1 - t) + b2 * t);

                int interpolatedColor = (a << 24) | (r << 16) | (g << 8) | b;

                dc.fill(x1 + i, y1, x1 + i + 1, y2, interpolatedColor);
            }
        }
    }

    @Override public void fillRoundedRectWithBorders(int i, int i1, int i2, int i3, float v, int i4, int i5, int i6) {}
    @Override public void drawAnimatedCornerGradient(float v, float v1, float v2, float v3, int i) {}

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        TextRenderer.getTr().draw(this, text, x, y, color, shadow);
    }

    @Override
    public void drawStyledText(Object text, int x, int y, int color, boolean shadow) {
        TextRenderer.drawStyled(this, text, x, y, color, shadow);
    }

    @Override
    public void drawBufferedImage(BufferedImage image, float x, float y, float width, float height) {
        ImageUtil.drawBufferedImage(dc, image, (int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void drawPixelArt(BufferedImage bufferedImage, float v, float v1, float v2, float v3) {
        ImageUtil.drawPixelArt(dc, (int) v, (int) v1, (int) v2, (int) v3, bufferedImage);
    }

    @Override
    public void drawBufferedImage(Identifier identifier, float v, float v1, float v2, float v3) {
        drawBufferedImage(ResourceManager.getInstance().getImage(identifier), v, v1, v2, v3);
    }

    @Override
    public void drawPixelArt(Identifier identifier, float v, float v1, float v2, float v3) {
        drawPixelArt(ResourceManager.getInstance().getImage(identifier), v, v1, v2, v3);
    }

    @Override
    public void drawInvertedRect(float v, float v1, float v2, float v3) {
        dc.fill(RenderLayer.getGuiTextHighlight(), (int) v, (int) v1, (int) v2, (int) v3, 0xFF0000FF);
    }
}