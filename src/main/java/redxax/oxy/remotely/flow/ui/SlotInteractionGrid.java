package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.platform.IDrawContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SlotInteractionGrid {
    private SlotInteractionGrid() {
    }

    record SlotRect(int slot, int x, int y, int size) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    static int hitSlot(Collection<SlotRect> slots, int mouseX, int mouseY) {
        for (SlotRect slot : slots) {
            if (slot.contains(mouseX, mouseY)) {
                return slot.slot();
            }
        }
        return -1;
    }

    static Stroke beginStroke(Collection<SlotRect> slots, int mouseX, int mouseY) {
        Stroke stroke = new Stroke(slots);
        stroke.moveTo(mouseX, mouseY);
        return stroke;
    }

    static List<Integer> slotRange(int startSlot, int endSlot, int columns, int rows) {
        if (startSlot < 0 || endSlot < 0 || columns <= 0 || rows <= 0) {
            return List.of();
        }
        int startRow = startSlot / columns;
        int startCol = startSlot % columns;
        int endRow = endSlot / columns;
        int endCol = endSlot % columns;
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);
        int minCol = Math.min(startCol, endCol);
        int maxCol = Math.max(startCol, endCol);
        if (maxRow >= rows) {
            return List.of();
        }
        List<Integer> slots = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                slots.add(row * columns + col);
            }
        }
        return slots;
    }

    static void drawHighlight(IDrawContext context, int x, int y, int width, int height, int color, boolean outline) {
        if (color == 0) {
            return;
        }
        int fill = (color & 0x00FFFFFF) | 0x55000000;
        context.fill(x, y, x + width, y + height, fill);
        if (outline) {
            int border = (color & 0x00FFFFFF) | 0xCC000000;
            context.fillBorder(x, y, x + width, y + height, 1, border);
        }
    }

    static final class Stroke {
        private final List<SlotRect> slots;
        private final Set<Integer> touchedSlots = new LinkedHashSet<>();
        private int lastX = Integer.MIN_VALUE;
        private int lastY = Integer.MIN_VALUE;

        private Stroke(Collection<SlotRect> slots) {
            this.slots = slots != null ? new ArrayList<>(slots) : List.of();
        }

        void moveTo(int mouseX, int mouseY) {
            if (lastX == Integer.MIN_VALUE || lastY == Integer.MIN_VALUE) {
                addHit(mouseX, mouseY);
                lastX = mouseX;
                lastY = mouseY;
                return;
            }
            int dx = mouseX - lastX;
            int dy = mouseY - lastY;
            int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(1, smallestSlotSize() / 2));
            for (int i = 1; i <= steps; i++) {
                int x = lastX + Math.round(dx * (i / (float) steps));
                int y = lastY + Math.round(dy * (i / (float) steps));
                addHit(x, y);
            }
            lastX = mouseX;
            lastY = mouseY;
        }

        List<Integer> slots() {
            return new ArrayList<>(touchedSlots);
        }

        boolean isEmpty() {
            return touchedSlots.isEmpty();
        }

        private void addHit(int mouseX, int mouseY) {
            int slot = hitSlot(slots, mouseX, mouseY);
            if (slot >= 0) {
                touchedSlots.add(slot);
            }
        }

        private int smallestSlotSize() {
            int size = Integer.MAX_VALUE;
            for (SlotRect slot : slots) {
                if (slot.size() > 0) {
                    size = Math.min(size, slot.size());
                }
            }
            return size == Integer.MAX_VALUE ? 16 : size;
        }
    }
}
