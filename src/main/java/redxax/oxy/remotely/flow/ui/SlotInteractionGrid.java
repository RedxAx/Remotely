package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.platform.IDrawContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
}
