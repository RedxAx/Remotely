package redxax.oxy.remotely.util;

import restudio.rescreen.logging.LogSource;
import restudio.rescreen.logging.LogTypes;
import restudio.rescreen.logging.ReLog;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.DesktopRemotelyPaths;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.mixin.accessor.ScreenAccessor;
import redxax.oxy.remotely.quickserver.QuickServerManager;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import static redxax.oxy.remotely.config.Config.mainMenuStyle;

public class ScreenInitHelper {

    private static final Set<Screen> initializedTitleScreens = Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<Screen, List<AbstractButton>> titleScreenButtons = new WeakHashMap<>();

    public static void init(Screen screen, AbstractButton anchorButton) {
        init(screen, anchorButton, false);
    }

    public static void resetTitleScreen(Screen screen) {
        initializedTitleScreens.remove(screen);
        List<AbstractButton> buttons = titleScreenButtons.remove(screen);
        if (buttons == null) return;
        for (AbstractButton button : buttons) {
            ((ScreenAccessor) screen).remotely$removeWidget(button);
        }
    }

    public static void ensureTitleScreenButtons(Screen screen) {
        if (!(screen instanceof TitleScreen) || initializedTitleScreens.contains(screen)) return;

        String optionsButtonText = I18n.get("menu.options");
        AbstractButton optionsButton = screen.children().stream()
            .filter(child -> child instanceof AbstractButton)
            .map(child -> (AbstractButton) child)
            .filter(button -> button.getMessage().getString().equals(optionsButtonText))
            .findFirst()
            .orElse(null);

        if (optionsButton == null) {
            ReLog.logger(LogTypes.USER_INTERFACE).source(LogSource.application("Remotely Mod")).component(ScreenInitHelper.class).warn("Could not attach Remotely to the Options screen");
            initializedTitleScreens.add(screen);
            return;
        }

        init(screen, optionsButton, true);
        initializedTitleScreens.add(screen);
    }

    public static void init(Screen screen, AbstractButton anchorButton, boolean isTitleScreen) {
        if (anchorButton == null || !(screen instanceof ICustomWidgetHolder widgetHolder)) return;

        //#if MC >= 1.19.4
        int anchorX = anchorButton.getX();
        int anchorY = anchorButton.getY();
        int anchorHeight = anchorButton.getHeight();
        //#else
        //$$ int anchorX = anchorButton.x;
        //$$ int anchorY = anchorButton.y;
        //$$ int anchorHeight = anchorButton.getHeight();
        //#endif

        switch (mainMenuStyle) {
            case "Vanilla" -> {
                int buttonX = anchorX;
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 5, 20, isTitleScreen);
                int smallButtonWidth = 50;
                int largeButtonWidth = 100;
                int gap = 5;
                int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;

                //#if MC >= 1.19.4
                AbstractButton serverButton = Button.builder(Component.literal("Servers"), btn -> openServerManagerScreen(screen)).bounds(buttonX, buttonY, smallButtonWidth, 20).build();
                ((ScreenAccessor) screen).remotely$addRenderableWidget(serverButton);
                AbstractButton fileExplorerButton = Button.builder(Component.literal("File Explorer"), btn -> openFileExplorerScreen(screen)).bounds(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20).build();
                ((ScreenAccessor) screen).remotely$addRenderableWidget(fileExplorerButton);
                AbstractButton terminalButton = Button.builder(Component.literal("Terminal"), btn -> openMultiTerminalScreen(screen)).bounds(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20).build();
                ((ScreenAccessor) screen).remotely$addRenderableWidget(terminalButton);
                if (isTitleScreen) {
                    titleScreenButtons.put(screen, new ArrayList<>(List.of(serverButton, fileExplorerButton, terminalButton)));
                }
                //#else
                //$$ AbstractButton serverButton = new Button(buttonX, buttonY, smallButtonWidth, 20, Component.literal("Servers"), btn -> openServerManagerScreen(screen));
                //$$ ((ScreenAccessor) screen).remotely$addRenderableWidget(serverButton);
                //$$ AbstractButton fileExplorerButton = new Button(buttonX + smallButtonWidth + gap, buttonY, largeButtonWidth, 20, Component.literal("File Explorer"), btn -> openFileExplorerScreen(screen));
                //$$ ((ScreenAccessor) screen).remotely$addRenderableWidget(fileExplorerButton);
                //$$ AbstractButton terminalButton = new Button(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY, smallButtonWidth, 20, Component.literal("Terminal"), btn -> openMultiTerminalScreen(screen));
                //$$ ((ScreenAccessor) screen).remotely$addRenderableWidget(terminalButton);
                //#endif
            }
            case "Minimal" -> {
                int spacing = 4;
                int buttonSize = 18;
                int startX = anchorX + 1;
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 4, 18, isTitleScreen);

                SquareButtonWidget serverBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("manager.png").hint("Servers").onClick(() -> openServerManagerScreen(screen)).build();
                serverBtn.setPosition(startX, buttonY);
                widgetHolder.remotely$addWidget(serverBtn);

                SquareButtonWidget terminalBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("terminal.png").hint("Terminal").onClick(() -> openMultiTerminalScreen(screen)).build();
                terminalBtn.setPosition(startX + (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(terminalBtn);

                SquareButtonWidget explorerBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("explorer.png").hint("File Explorer").onClick(() -> openFileExplorerScreen(screen)).build();
                explorerBtn.setPosition(startX + 2 * (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(explorerBtn);
            }
            case "Normal" -> {
                int buttonX = anchorX + 1;
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 4, 18, isTitleScreen);
                int smallButtonWidth = 50;
                int largeButtonWidth = 100;
                int gap = 5;
                int totalWidth = smallButtonWidth * 2 + largeButtonWidth + gap * 2;
                int excessWidth = totalWidth - 200;
                largeButtonWidth -= excessWidth;

                AnimatedButton serverBtn = new AnimatedButton.Builder().entranceAnimation(false).label("Servers").onClick(() -> openServerManagerScreen(screen)).size(smallButtonWidth, 18).build();
                serverBtn.setPosition(buttonX, buttonY);
                widgetHolder.remotely$addWidget(serverBtn);

                AnimatedButton explorerBtn = new AnimatedButton.Builder().entranceAnimation(false).label("File Explorer").onClick(() -> openFileExplorerScreen(screen)).size(largeButtonWidth, 18).build();
                explorerBtn.setPosition(buttonX + smallButtonWidth + gap, buttonY);
                widgetHolder.remotely$addWidget(explorerBtn);

                AnimatedButton terminalBtn = new AnimatedButton.Builder().entranceAnimation(false).label("Terminal").onClick(() -> openMultiTerminalScreen(screen)).size(smallButtonWidth, 18).build();
                terminalBtn.setPosition(buttonX + smallButtonWidth + largeButtonWidth + gap * 2, buttonY);
                widgetHolder.remotely$addWidget(terminalBtn);
            }
        }
    }

    public static void addQuickServerButton(Screen screen, AbstractButton anchorButton) {
        if (anchorButton == null || !QuickServerManager.shouldShowGameButton()
            || !(screen instanceof ICustomWidgetHolder widgetHolder)) return;

        //#if MC >= 1.19.4
        int anchorX = anchorButton.getX();
        int anchorY = anchorButton.getY();
        int anchorHeight = anchorButton.getHeight();
        //#else
        //$$ int anchorX = anchorButton.x;
        //$$ int anchorY = anchorButton.y;
        //$$ int anchorHeight = anchorButton.getHeight();
        //#endif

        switch (mainMenuStyle) {
            case "Minimal" -> {
                int spacing = 4;
                int buttonSize = 18;
                int startX = anchorX + 1;
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 4, 18, false);
                SquareButtonWidget quickButton = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("duplicate.png").hint(QuickServerManager.isInQuickServer() ? "Terminal" : "Quick Server").onClick(() -> QuickServerManager.activateGameButton(screen)).build();
                quickButton.setPosition(startX + 3 * (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(quickButton);
            }
            case "Normal" -> {
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 4, 18, false);
                AnimatedButton quickButton = new AnimatedButton.Builder().entranceAnimation(false).label(QuickServerManager.isInQuickServer() ? "Terminal" : "Quick Server").onClick(() -> QuickServerManager.activateGameButton(screen)).size(100, 18).build();
                quickButton.setPosition(anchorX + 50, buttonY);
                widgetHolder.remotely$addWidget(quickButton);
            }
            case "Vanilla" -> {
                int buttonY = getAnchoredY(screen, anchorY, anchorHeight, 5, 20, false);
                //#if MC >= 1.19.4
                AbstractButton quickButton = Button.builder(Component.literal(QuickServerManager.isInQuickServer() ? "Terminal" : "Quick Server"), btn -> QuickServerManager.activateGameButton(screen)).bounds(anchorX + 50, buttonY, 100, 20).build();
                ((ScreenAccessor) screen).remotely$addRenderableWidget(quickButton);
                //#else
                //$$ AbstractButton quickButton = new Button(anchorX + 50, buttonY, 100, 20, Component.literal(QuickServerManager.isInQuickServer() ? "Terminal" : "Quick Server"), btn -> QuickServerManager.activateGameButton(screen));
                //$$ ((ScreenAccessor) screen).remotely$addRenderableWidget(quickButton);
                //#endif
            }
        }
    }

    private static int getAnchoredY(Screen screen, int anchorY, int anchorHeight, int offset, int buttonHeight, boolean isTitleScreen) {
        int buttonY = anchorY + anchorHeight + offset;
        if (!isTitleScreen) return buttonY;

        int topMargin = 6;
        int bottomY = screen.height - buttonHeight - topMargin;
        if (buttonY <= bottomY) return Math.max(topMargin, buttonY);

        int aboveY = anchorY - offset - buttonHeight;
        if (aboveY >= topMargin) return aboveY;

        return Math.max(topMargin, bottomY);
    }

    private static void openServerManagerScreen(Screen screen) {
        RemotelyClient.INSTANCE.openServerManager(screen);
    }

    private static void openMultiTerminalScreen(Screen screen) {
        RemotelyClient.INSTANCE.openMultiTerminal(screen);
    }

    private static void openFileExplorerScreen(Screen screen) {
        RemotelyClient.INSTANCE.openFileExplorer(screen, DesktopRemotelyPaths.appDir());
    }
}
