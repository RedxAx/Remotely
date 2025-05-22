package redxax.oxy.remotely.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.config.Config;

import static redxax.oxy.remotely.config.Config.globalMovementSpeed;
import static redxax.oxy.remotely.config.Config.loading;

public class LoadingAnimation {

    private static final List<AnimationSquare> squares = new ArrayList<>();
    private static boolean initialized = false;
    private static final Random random = new Random();
    private static float hoverWaveProgress = 0f;
    private static float selectWaveProgress = 0f;
    private static boolean hoverWaveActive = false;
    private static boolean selectWaveActive = false;
    private static float hoverWaveDelay = 0f;
    private static float selectWaveDelay = 0f;

    private static void initialize(int screenWidth, int screenHeight) {
        AnimationSquare.nextId = 1;
        int size = 30;
        int gap = 20;
        float totalWidth = 3 * size + 2 * gap;
        float startX = screenWidth / 2f - totalWidth / 2f;
        float baseY = screenHeight / 2f;

        squares.clear();
        for (int i = 0; i < 3; i++) {
            float x = startX + i * (size + gap);
            AnimationSquare square = new AnimationSquare(x, baseY, size, null, i);
            square.phase = i * 0.5f;
            squares.add(square);
        }
        for (AnimationSquare square : squares) {
            randomSplit(square, 0);
        }
        initialized = true;
        hoverWaveProgress = 0f;
        selectWaveProgress = 0f;
        hoverWaveActive = false;
        selectWaveActive = false;
        hoverWaveDelay = getNextWaveDelay();
        selectWaveDelay = getNextWaveDelay();
    }

    private static void randomSplit(AnimationSquare square, int depth) {
        if (depth >= 3) return;
        double chance = 0.50 + (depth == 2 ? 0.01 : 0.0);
        if (random.nextDouble() < chance || (depth == 0 && random.nextDouble() < 0.01)) {
            square.split();
            for (AnimationSquare child : square.children) {
                randomSplit(child, depth + 1);
            }
        }
    }

    private static float getNextWaveDelay() {
        return 1.5f + random.nextFloat() * 2.5f;
    }

    public static void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!loading) {
            if (initialized) {
                for (AnimationSquare square : squares) {
                    square.isSplit = false;
                    square.children.clear();
                }
                initialized = false;
            }
            return;
        }
        if (!initialized) {
            initialize(screenWidth, screenHeight);
        }
        float dt = Config.deltaTime;
        if (!hoverWaveActive) {
            hoverWaveDelay -= dt;
            if (hoverWaveDelay <= 0f) {
                hoverWaveActive = true;
                hoverWaveProgress = 0f;
            }
        }
        if (!selectWaveActive) {
            selectWaveDelay -= dt;
            if (selectWaveDelay <= 0f) {
                selectWaveActive = true;
                selectWaveProgress = 0f;
            }
        }
        if (hoverWaveActive) {
            hoverWaveProgress += dt * 1.5f;
            if (hoverWaveProgress >= 1f) {
                hoverWaveActive = false;
                hoverWaveDelay = getNextWaveDelay();
                hoverWaveProgress = 0f;
                clearHoverWave(squares);
            }
        }
        if (selectWaveActive) {
            selectWaveProgress += dt * 1.5f;
            if (selectWaveProgress >= 1f) {
                selectWaveActive = false;
                selectWaveDelay = getNextWaveDelay();
                selectWaveProgress = 0f;
                clearSelectWave(squares);
            }
        }
        for (AnimationSquare square : squares) {
            square.update();
        }
        if (hoverWaveActive) {
            animateHoverWave(hoverWaveProgress);
        }
        if (selectWaveActive) {
            animateSelectWave(selectWaveProgress);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        for (AnimationSquare square : squares) {
            square.render(context, mc, mouseX, mouseY);
        }
    }

    private static void animateHoverWave(float progress) {
        List<AnimationSquare> flat = new ArrayList<>();
        flattenSquares(LoadingAnimation.squares, flat);
        int total = flat.size();
        float window = 0.25f;
        for (int i = 0; i < total; i++) {
            float t = (float) i / (float) (total - 1);
            flat.get(i).animatedHover = progress >= t - window / 2f && progress <= t + window / 2f;
        }
    }

    private static void animateSelectWave(float progress) {
        List<AnimationSquare> flat = new ArrayList<>();
        flattenSquares(LoadingAnimation.squares, flat);
        int total = flat.size();
        float window = 0.25f;
        for (int i = 0; i < total; i++) {
            float t = (float) i / (float) (total - 1);
            flat.get(i).animatedSelected = progress >= t - window / 2f && progress <= t + window / 2f;
        }
    }

    private static void clearHoverWave(List<AnimationSquare> list) {
        for (AnimationSquare square : list) {
            square.animatedHover = false;
            if (square.isSplit) {
                clearHoverWave(square.children);
            }
        }
    }

    private static void clearSelectWave(List<AnimationSquare> list) {
        for (AnimationSquare square : list) {
            square.animatedSelected = false;
            if (square.isSplit) {
                clearSelectWave(square.children);
            }
        }
    }

    private static void flattenSquares(List<AnimationSquare> list, List<AnimationSquare> out) {
        for (AnimationSquare square : list) {
            out.add(square);
            if (square.isSplit) {
                flattenSquares(square.children, out);
            }
        }
    }

    public static boolean mousePressed(int mouseX, int mouseY, int button) {
        return mousePressedRecursive(squares, mouseX, mouseY, button);
    }

    private static boolean mousePressedRecursive(List<AnimationSquare> list, int mouseX, int mouseY, int button) {
        for (AnimationSquare square : list) {
            if (square.isMouseOver(mouseX, mouseY)) {
                handleSquarePress(square, mouseX, mouseY, button);
                return true;
            }
            if (square.isSplit) {
                if (mousePressedRecursive(square.children, mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void handleSquarePress(AnimationSquare square, int mouseX, int mouseY, int button) {
        if (button == 0) {
            square.startDragging(mouseX, mouseY);
            long now = System.currentTimeMillis();
            if (now - square.lastClickTime < 500) {
                square.clickCount++;
            } else {
                square.clickCount = 1;
            }
            square.lastClickTime = now;
            if (square.clickCount >= 3) {
                square.split();
                square.clickCount = 0;
            }
        }
        if (button == 1) {
            square.toggleAdvancedMode();
        }
    }

    public static void mouseReleased(int mouseX, int mouseY, int button) {
        mouseReleasedRecursive(squares, mouseX, mouseY, button);
    }

    private static void mouseReleasedRecursive(List<AnimationSquare> list, int mouseX, int mouseY, int button) {
        for (AnimationSquare square : list) {
            if (square.dragging) {
                square.stopDragging();
            }
            if (square.isSplit) {
                mouseReleasedRecursive(square.children, mouseX, mouseY, button);
            }
        }
    }

    public static void mouseDragged(int mouseX, int mouseY, int button) {
        mouseDraggedRecursive(squares, mouseX, mouseY, button);
    }

    private static void mouseDraggedRecursive(List<AnimationSquare> list, int mouseX, int mouseY, int button) {
        for (AnimationSquare square : list) {
            if (square.dragging) {
                square.updateDragging(mouseX, mouseY);
            }
            if (square.isSplit) {
                mouseDraggedRecursive(square.children, mouseX, mouseY, button);
            }
        }
    }

    public static class AnimationSquare {
        static int nextId = 1;
        final int id;

        float baseX, baseY;
        float currentX, currentY;
        int size;

        float amplitude = 15f;
        float phase = 0f;

        boolean dragging = false;
        float dragOffsetX, dragOffsetY;

        int clickCount = 0;
        long lastClickTime = 0;
        boolean advancedMode = false;
        float colorShift = 0f;

        boolean isSplit = false;
        List<AnimationSquare> children = new ArrayList<>();

        boolean isChild;

        boolean animatedHover = false;
        boolean animatedSelected = false;

        public AnimationSquare(float x, float y, int size, AnimationSquare parent, int index) {
            this.baseX = x;
            this.baseY = y;
            this.currentX = x;
            this.currentY = y;
            this.size = size;
            this.isChild = parent != null;
            this.id = nextId++;
        }

        public void update() {
            if (isSplit) {
                for (AnimationSquare child : children) {
                    child.update();
                }
                return;
            }
            if (!dragging) {
                float time = (float) (System.nanoTime() / 1_000_000_000.0);
                float targetY = baseY + amplitude * (float) Math.sin(globalMovementSpeed * time + phase);
                currentY += (targetY - currentY) * Config.globalMovementSpeed * Config.deltaTime;
                currentX += (baseX - currentX) * Config.globalMovementSpeed * Config.deltaTime;
            }
            if (advancedMode) {
                colorShift += 100 * Config.deltaTime;
            }
        }

        public void render(DrawContext context, MinecraftClient mc, int mouseX, int mouseY) {
            if (isSplit) {
                for (AnimationSquare child : children) {
                    child.render(context, mc, mouseX, mouseY);
                }
                return;
            }
            boolean hovered = isMouseOver(mouseX, mouseY) || animatedHover;
            boolean selected = dragging || animatedSelected;
            int uniqueHash = String.valueOf(id).hashCode() + id;
            int textColor = Config.getElementBackgroundColor(uniqueHash, hovered, selected, true, false, false, false);
            Render.drawCustomButton(context, (int) currentX, (int) currentY, String.valueOf(id), mc, hovered, false, false, selected, true, size, size, textColor, textColor, mouseX, mouseY, "");
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            int hitboxPadding = isChild ? 2 : 0;
            return mouseX >= currentX - hitboxPadding && mouseX <= currentX + size + hitboxPadding && mouseY >= currentY - hitboxPadding && mouseY <= currentY + size + hitboxPadding;
        }

        public void startDragging(int mouseX, int mouseY) {
            dragging = true;
            dragOffsetX = mouseX - currentX;
            dragOffsetY = mouseY - currentY;
        }

        public void updateDragging(int mouseX, int mouseY) {
            if (dragging) {
                currentX = mouseX - dragOffsetX;
                currentY = mouseY - dragOffsetY;
            }
        }

        public void stopDragging() {
            dragging = false;
        }

        public void toggleAdvancedMode() {
            advancedMode = !advancedMode;
        }

        public void split() {
            if (isSplit) return;
            isSplit = true;
            children.clear();
            int padding = Math.max(2, size / 10);
            int newSize = (size - padding) / 2;
            int childIndex = 0;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    float childX = baseX + col * (newSize + padding);
                    float childY = baseY + row * (newSize + padding);
                    AnimationSquare child = new AnimationSquare(childX, childY, newSize, this, childIndex++);
                    child.phase = phase + (row + col) * 0.2f;
                    children.add(child);
                }
            }
        }
    }
}
