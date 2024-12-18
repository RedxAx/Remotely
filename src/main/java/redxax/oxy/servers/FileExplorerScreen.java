package redxax.oxy.servers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.fileeditor.FileEditorScreen;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileExplorerScreen extends Screen {
    private final MinecraftClient minecraftClient;
    private final Screen parent;
    private final ServerInfo serverInfo;
    private List<Path> fileEntries;
    private float smoothOffset = 0;
    private int entryHeight = 25;
    private int baseColor = 0xFF181818;
    private int explorerBgColor = 0xFF242424;
    private int explorerBorderColor = 0xFF555555;
    private int entryBgColor = 0xFF2C2C2C;
    private int entryBorderColor = 0xFF444444;
    private int highlightColor = 0xFF444444;
    private int textColor = 0xFFFFFFFF;
    private Path currentPath;
    private float targetOffset = 0;
    private float scrollSpeed = 0.2f;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private List<Path> selectedPaths = new ArrayList<>();
    private int lastSelectedIndex = -1;
    private List<Path> clipboard = new ArrayList<>();
    private boolean isCut = false;
    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_INTERVAL = 500;
    private int lastClickedIndex = -1;

    public FileExplorerScreen(MinecraftClient mc, Screen parent, ServerInfo info) {
        super(Text.literal("File Explorer"));
        this.minecraftClient = mc;
        this.parent = parent;
        this.serverInfo = info;
        this.fileEntries = new ArrayList<>();
        this.currentPath = Paths.get(serverInfo.path).toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        super.init();
        loadDirectory(currentPath);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == this.minecraftClient.options.backKey.getDefaultKey().getCode()) {
            navigateUp();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copySelected();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            cutSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            paste();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            long currentTime = System.currentTimeMillis();
            boolean isDoubleClick = false;
            if (lastClickedIndex != -1 && (currentTime - lastClickTime) < DOUBLE_CLICK_INTERVAL && lastClickedIndex != -1) {
                isDoubleClick = true;
            }
            lastClickTime = currentTime;
            int explorerX = 50;
            int explorerY = 60;
            int explorerWidth = this.width - 100;
            int explorerHeight = this.height - 100;
            int backButtonX = this.width - 120;
            int backButtonY = 5;
            int btnW = 50;
            int btnH = 20;
            int closeButtonX = this.width - 60;
            int closeButtonY = 5;
            int closeBtnW = 50;
            int closeBtnH = 20;

            if (mouseX >= backButtonX && mouseX <= backButtonX + btnW &&
                    mouseY >= backButtonY && mouseY <= backButtonY + btnH) {
                navigateUp();
                return true;
            }

            if (mouseX >= closeButtonX && mouseX <= closeButtonX + closeBtnW &&
                    mouseY >= closeButtonY && mouseY <= closeButtonY + closeBtnH) {
                minecraftClient.setScreen(parent);
                return true;
            }

            if (mouseX >= explorerX && mouseX <= explorerX + explorerWidth &&
                    mouseY >= explorerY && mouseY <= explorerY + explorerHeight) {
                int relativeY = (int) mouseY - explorerY + (int) smoothOffset;
                int clickedIndex = relativeY / entryHeight;
                if (clickedIndex >= 0 && clickedIndex < fileEntries.size()) {
                    Path selectedPath = fileEntries.get(clickedIndex);
                    if (isDoubleClick) {
                        if (Files.isDirectory(selectedPath)) {
                            loadDirectory(selectedPath);
                        } else {
                            if (isSupportedFile(selectedPath)) {
                                minecraftClient.setScreen(new FileEditorScreen(minecraftClient, this, selectedPath, serverInfo));
                            }
                        }
                        lastClickedIndex = -1;
                        return true;
                    } else {
                        boolean ctrl = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) ||
                                (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
                        boolean shift = (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) ||
                                (GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
                        if (ctrl) {
                            if (selectedPaths.contains(selectedPath)) {
                                selectedPaths.remove(selectedPath);
                            } else {
                                selectedPaths.add(selectedPath);
                            }
                            lastSelectedIndex = clickedIndex;
                        } else if (shift && lastSelectedIndex != -1) {
                            int start = Math.min(lastSelectedIndex, clickedIndex);
                            int end = Math.max(lastSelectedIndex, clickedIndex);
                            for (int i = start; i <= end; i++) {
                                Path path = fileEntries.get(i);
                                if (!selectedPaths.contains(path)) {
                                    selectedPaths.add(path);
                                }
                            }
                        } else {
                            selectedPaths.clear();
                            selectedPaths.add(selectedPath);
                            lastSelectedIndex = clickedIndex;
                        }
                        lastClickedIndex = clickedIndex;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetOffset -= verticalAmount * entryHeight * 0.5f;
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, fileEntries.size() * entryHeight - (this.height - 100))));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, baseColor, baseColor);
        super.render(context, mouseX, mouseY, delta);

        int explorerX = 50;
        int explorerY = 60;
        int explorerWidth = this.width - 100;
        int explorerHeight = this.height - 100;

        context.fill(explorerX, explorerY, explorerX + explorerWidth, explorerY + explorerHeight, explorerBgColor);
        drawInnerBorder(context, explorerX, explorerY, explorerWidth, explorerHeight, explorerBorderColor);

        int headerY = explorerY - 25;
        context.fill(explorerX, headerY, explorerX + explorerWidth, headerY + 25, explorerBgColor);
        drawInnerBorder(context, explorerX, headerY, explorerWidth, 25, explorerBorderColor);
        context.drawText(this.textRenderer, Text.literal("Name"), explorerX + 10, headerY + 5, textColor, false);
        context.drawText(this.textRenderer, Text.literal("Size"), explorerX + 250, headerY + 5, textColor, false);
        context.drawText(this.textRenderer, Text.literal("Created"), explorerX + 350, headerY + 5, textColor, false);

        int titleBarHeight = 30;
        context.fill(0, 0, this.width, titleBarHeight, 0xFF222222);
        drawInnerBorder(context, 0, 0, this.width, titleBarHeight, 0xFF333333);

        String titleText = "File Explorer - " + currentPath.toString();
        if (!currentPath.equals(Paths.get(serverInfo.path).toAbsolutePath().normalize())) {
            titleText += "/";
        }
        context.drawText(this.textRenderer, Text.literal(titleText), 10, 10, textColor, false);

        int backButtonX = this.width - 120;
        int backButtonY = 5;
        int btnW = 50;
        int btnH = 20;
        boolean hoveredBack = mouseX >= backButtonX && mouseX <= backButtonX + btnW &&
                mouseY >= backButtonY && mouseY <= backButtonY + btnH;
        int bgBack = hoveredBack ? highlightColor : explorerBgColor;
        context.fill(backButtonX, backButtonY, backButtonX + btnW, backButtonY + btnH, bgBack);
        drawInnerBorder(context, backButtonX, backButtonY, btnW, btnH, explorerBorderColor);
        int twb = minecraftClient.textRenderer.getWidth("Back");
        int txb = backButtonX + (btnW - twb) / 2;
        int ty = backButtonY + (btnH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Back"), txb, ty, textColor, false);

        int closeButtonX = this.width - 60;
        int closeButtonY = 5;
        int closeBtnW = 50;
        int closeBtnH = 20;
        boolean hoveredClose = mouseX >= closeButtonX && mouseX <= closeButtonX + closeBtnW &&
                mouseY >= closeButtonY && mouseY <= closeButtonY + closeBtnH;
        int bgClose = hoveredClose ? highlightColor : explorerBgColor;
        context.fill(closeButtonX, closeButtonY, closeButtonX + closeBtnW, closeButtonY + closeBtnH, bgClose);
        drawInnerBorder(context, closeButtonX, closeButtonY, closeBtnW, closeBtnH, explorerBorderColor);
        int tcw = minecraftClient.textRenderer.getWidth("Close");
        int tcx = closeButtonX + (closeBtnW - tcw) / 2;
        int tcy = closeButtonY + (closeBtnH - minecraftClient.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, Text.literal("Close"), tcx, tcy, textColor, false);

        smoothOffset += (targetOffset - smoothOffset) * scrollSpeed;

        int visibleEntries = explorerHeight / entryHeight;
        int startIndex = (int)Math.floor(smoothOffset / entryHeight) - 1;
        if (startIndex < 0) startIndex = 0;
        int endIndex = startIndex + visibleEntries + 2;
        if (endIndex > fileEntries.size()) endIndex = fileEntries.size();

        for (int entryIndex = startIndex; entryIndex < endIndex; entryIndex++) {
            Path entry = fileEntries.get(entryIndex);
            int entryY = explorerY + (entryIndex * entryHeight) - (int)smoothOffset;

            float opacity = 1.0f;
            if (entryY < explorerY) {
                float distAbove = explorerY - entryY;
                if (distAbove < 10) {
                    opacity = 1.0f - (distAbove / 10.0f);
                } else {
                    opacity = 0.0f;
                }
            }

            int bottomEdge = entryY + entryHeight;
            if (bottomEdge > explorerY + explorerHeight) {
                float distBelow = bottomEdge - (explorerY + explorerHeight);
                if (distBelow < 10) {
                    float fade = 1.0f - (distBelow / 10.0f);
                    if (fade < opacity) {
                        opacity = fade;
                    }
                } else {
                    opacity = 0.0f;
                }
            }

            if (opacity <= 0.0f) continue;

            boolean hovered = mouseX >= explorerX && mouseX <= explorerX + explorerWidth &&
                    mouseY >= entryY && mouseY < entryY + entryHeight;
            boolean isSelected = selectedPaths.contains(entry);
            int bg = isSelected ? 0xFF555555 : (hovered ? highlightColor : entryBgColor);
            int bgWithOpacity = blendColor(bg, opacity);
            int borderWithOpacity = blendColor(entryBorderColor, opacity);
            int textWithOpacity = blendColor(textColor, opacity);

            context.fill(explorerX, entryY, explorerX + explorerWidth, entryY + entryHeight, bgWithOpacity);
            drawInnerBorder(context, explorerX, entryY, explorerWidth, entryHeight, borderWithOpacity);

            String namePrefix = Files.isDirectory(entry) ? "[DIR] " : "[FILE]";
            String displayName = namePrefix + " " + entry.getFileName().toString();
            String size = Files.isDirectory(entry) ? "-" : getFileSize(entry);
            String created = getCreationDate(entry);

            context.drawText(this.textRenderer, Text.literal(displayName), explorerX + 10, entryY + 5, textWithOpacity, false);
            context.drawText(this.textRenderer, Text.literal(size), explorerX + 250, entryY + 5, textWithOpacity, false);
            context.drawText(this.textRenderer, Text.literal(created), explorerX + 350, entryY + 5, textWithOpacity, false);
        }

        context.fillGradient(explorerX, explorerY, explorerX + explorerWidth, explorerY + 10, 0x80000000, 0x00000000);
        context.fillGradient(explorerX, explorerY + explorerHeight - 10, explorerX + explorerWidth, explorerY + explorerHeight, 0x00000000, 0x80000000);
    }

    private void navigateUp() {
        Path parentPath = currentPath.getParent();
        if (parentPath != null && parentPath.startsWith(Paths.get(serverInfo.path).toAbsolutePath().normalize())) {
            loadDirectory(parentPath);
        } else {
            minecraftClient.setScreen(parent);
        }
    }

    private void loadDirectory(Path dir) {
        fileEntries.clear();
        selectedPaths.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                fileEntries.add(entry);
            }
        } catch (IOException e) {
            if (serverInfo.terminal != null) {
                serverInfo.terminal.appendOutput("Error reading directory: " + e.getMessage() + "\n");
            }
        }
        currentPath = dir.toAbsolutePath().normalize();
        sortFileEntries();
        targetOffset = 0;
    }

    private void sortFileEntries() {
        Comparator<Path> comparator = Comparator.comparing(Path::getFileName);
        fileEntries.sort(comparator);
    }

    private boolean isSupportedFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".json") || fileName.endsWith(".yml") || fileName.endsWith(".yaml") || fileName.endsWith(".conf") || fileName.endsWith(".properties") ||
                fileName.endsWith(".xml") || fileName.endsWith(".cfg") || fileName.endsWith(".ini");
    }

    private String getFileSize(Path file) {
        try {
            long size = Files.size(file);
            return humanReadableByteCountBin(size);
        } catch (IOException e) {
            return "N/A";
        }
    }

    private String humanReadableByteCountBin(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String getCreationDate(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return dateFormat.format(attrs.creationTime().toMillis());
        } catch (IOException e) {
            return "N/A";
        }
    }

    private void drawInnerBorder(DrawContext context, int x, int y, int w, int h, int c) {
        context.fill(x, y, x + w, y + 1, c);
        context.fill(x, y + h - 1, x + w, y + h, c);
        context.fill(x, y, x + 1, y + h, c);
        context.fill(x + w - 1, y, x + w, y + h, c);
    }

    private int blendColor(int color, float opacity) {
        int a = (int) ((color >> 24 & 0xFF) * opacity);
        int r = (color >> 16 & 0xFF);
        int g = (color >> 8 & 0xFF);
        int b = (color & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void copySelected() {
        clipboard.clear();
        clipboard.addAll(selectedPaths);
        isCut = false;
    }

    private void cutSelected() {
        clipboard.clear();
        clipboard.addAll(selectedPaths);
        isCut = true;
    }

    private void deleteSelected() {
        List<Path> toRemove = new ArrayList<>();
        for (Path path : selectedPaths) {
            try {
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(path);
                }
                toRemove.add(path);
            } catch (IOException e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("Error deleting " + path.getFileName() + ": " + e.getMessage() + "\n");
                }
            }
        }
        fileEntries.removeAll(toRemove);
        selectedPaths.removeAll(toRemove);
    }

    private void paste() {
        List<Path> toDelete = new ArrayList<>();
        for (Path src : clipboard) {
            Path dest = currentPath.resolve(src.getFileName());
            if (src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                continue;
            }
            try {
                if (Files.exists(dest)) {
                    if (Files.isDirectory(src)) {
                        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path targetDir = dest.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectory(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, dest.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    if (Files.isDirectory(src)) {
                        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path targetDir = dest.resolve(currentPath.relativize(dir));
                                if (!Files.exists(targetDir)) {
                                    Files.createDirectory(targetDir);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, dest.resolve(currentPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                if (isCut && !src.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                    toDelete.add(src);
                }
            } catch (IOException e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("Error pasting " + src.getFileName() + ": " + e.getMessage() + "\n");
                }
            }
        }
        for (Path path : toDelete) {
            try {
                if (Files.isDirectory(path)) {
                    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(path);
                }
            } catch (IOException e) {
                if (serverInfo.terminal != null) {
                    serverInfo.terminal.appendOutput("Error deleting " + path.getFileName() + ": " + e.getMessage() + "\n");
                }
            }
        }
        if (isCut) {
            clipboard.clear();
            isCut = false;
            selectedPaths.clear();
        }
        loadDirectory(currentPath);
    }

    private void deletePath(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(path);
        }
    }
}