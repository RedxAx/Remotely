package redxax.oxy.remotely.adapters;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import redxax.oxy.remotely.util.ImageUtil;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.IMatrixStack;

public class MinecraftDrawContextAdapter implements IDrawContext {
    private final DrawContext dc;
    private final MatrixStack matrices;

    private final Deque<int[]> scissorStack = new ArrayDeque<>();
    private final Deque<List<int[]>> scissorStateStack = new ArrayDeque<>();

    public MinecraftDrawContextAdapter(DrawContext dc) {
        this.dc = dc;
        this.matrices = dc.getMatrices();
    }

    public DrawContext getMcContext() {
        return dc;
    }

    public MatrixStack getMcMatrices() {
        return matrices;
    }

    @Override
    public IMatrixStack getMatrices() {
        return new IMatrixStack() {
            @Override
            public void push() {
                matrices.push();
            }

            @Override
            public void pop() {
                matrices.pop();
            }

            @Override
            public void translate(float x, float y, float z) {
                matrices.translate(x, y, z);
            }

            @Override
            public void scale(float x, float y, float z) {
                matrices.scale(x, y, z);
            }

            @Override
            public void rotate(float angle, float x, float y, float z) {
                matrices.multiply(RotationAxis.of(new Vector3f(x, y, z)).rotationDegrees(angle));
            }

            @Override
            public void multiply(float angle) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            }
        };
    }

    @Override
    public void pushScissorState() {
        scissorStateStack.push(new ArrayList<>(scissorStack));
    }

    @Override
    public void popScissorState() {
        if (!scissorStateStack.isEmpty()) {
            List<int[]> previous = scissorStateStack.pop();
            scissorStack.clear();
            scissorStack.addAll(previous);
            if (scissorStack.isEmpty()) {
                dc.disableScissor();
            } else {
                int[] r = scissorStack.peek();
                dc.enableScissor(r[0], r[1], r[2], r[3]);
            }
        }
    }

    @Override
    public void clearScissor() {
        scissorStack.clear();
        dc.disableScissor();
    }

    @Override
    public void enableScissor(float x1, float y1, float x2, float y2) {
        int sx1 = (int) x1;
        int sy1 = (int) y1;
        int sx2 = (int) x2;
        int sy2 = (int) y2;
        if (!scissorStack.isEmpty()) {
            int[] p = scissorStack.peek();
            sx1 = Math.max(sx1, p[0]);
            sy1 = Math.max(sy1, p[1]);
            sx2 = Math.min(sx2, p[2]);
            sy2 = Math.min(sy2, p[3]);
        }
        int[] rect = new int[] {sx1, sy1, sx2, sy2};
        scissorStack.push(rect);
        dc.enableScissor(rect[0], rect[1], rect[2], rect[3]);
    }

    @Override
    public boolean scissorsContains(int x, int y) {
        if (scissorStack.isEmpty()) return true;
        int[] r = scissorStack.peek();
        return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    @Override
    public void disableScissor() {
        if (!scissorStack.isEmpty()) scissorStack.pop();
        if (scissorStack.isEmpty()) {
            dc.disableScissor();
        } else {
            int[] r = scissorStack.peek();
            dc.enableScissor(r[0], r[1], r[2], r[3]);
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        dc.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        dc.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2, boolean horizontal) {
        if (!horizontal) {
            dc.fillGradient(x1, y1, x2, y2, color1, color2);
        } else {
            int mid = (x1 + x2) / 2;
            dc.fillGradient(x1, y1, mid, y2, color1, color2);
            dc.fillGradient(mid, y1, x2, y2, color2, color1);
        }
    }

    @Override
    public void fillRoundedRectWithBorders(int x, int y, int width, int height, float roundness, int bgColor, int borderColor, int currentOuterBorderColor) {
        dc.fill(x, y, x + width, y + height, bgColor);
        dc.fill(x, y, x + width, y + 1, borderColor);
        dc.fill(x, y + height - 1, x + width, y + height, borderColor);
        dc.fill(x, y, x + 1, y + height, borderColor);
        dc.fill(x + width - 1, y, x + width, y + height, borderColor);
        dc.fill(x - 1, y - 1, x + width + 1, y, currentOuterBorderColor);
        dc.fill(x - 1, y + height, x + width + 1, y + height + 1, currentOuterBorderColor);
        dc.fill(x - 1, y, x, y + height, currentOuterBorderColor);
        dc.fill(x + width, y, x + width + 1, y + height, currentOuterBorderColor);
    }

    @Override
    public void drawAnimatedCornerGradient(float x1, float y1, float x2, float y2, int color) {
        int start = (color & 0x00FFFFFF) | 0x40000000;
        dc.fillGradient((int) x1, (int) y1, (int) x2, (int) y2, start, 0x00000000);
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        dc.drawText(MinecraftClient.getInstance().textRenderer, text, x, y, color, shadow);
    }

    @Override
    public void drawBufferedImage(BufferedImage image, float x, float y, float width, float height) {
        ImageUtil.drawBufferedImage(dc, image, (int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void drawPixelArt(BufferedImage bufferedImage, float v, float v1, float v2, float v3) {
        ImageUtil.drawPixelArt(dc, (int) v, (int) v1, (int) v2, (int) v3, bufferedImage);
    }
}