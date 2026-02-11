package redxax.oxy.remotely.util;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import redxax.oxy.remotely.RemotelyClient;
import redxax.oxy.remotely.adapters.ICustomWidgetHolder;
import redxax.oxy.remotely.mixin.accessor.ScreenAccessor;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.ui.screens.auth.ReStudioLoginScreen;
import restudio.rescreen.ui.widgets.AnimatedButton;
import restudio.rescreen.ui.widgets.SquareButtonWidget;

import static redxax.oxy.remotely.config.Config.mainMenuStyle;
import static redxax.oxy.remotely.config.Config.remotelyDir;

public class ScreenInitHelper {

    public static void init(Screen screen, AbstractButton anchorButton) {
        init(screen, anchorButton, false);
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
                int buttonY = anchorY + anchorHeight + 5;
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
                int buttonY = anchorY + anchorHeight + 4;

                SquareButtonWidget serverBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("manager.png").onClick(() -> openServerManagerScreen(screen)).build();
                serverBtn.setPosition(startX, buttonY);
                widgetHolder.remotely$addWidget(serverBtn);

                SquareButtonWidget terminalBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("terminal.png").onClick(() -> openMultiTerminalScreen(screen)).build();
                terminalBtn.setPosition(startX + (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(terminalBtn);

                SquareButtonWidget explorerBtn = new SquareButtonWidget.Builder().entranceAnimation(false).imagePath("explorer.png").onClick(() -> openFileExplorerScreen(screen)).build();
                explorerBtn.setPosition(startX + 2 * (buttonSize + spacing), buttonY);
                widgetHolder.remotely$addWidget(explorerBtn);
            }
            case "Normal" -> {
                int buttonX = anchorX + 1;
                int buttonY = anchorY + anchorHeight + 18;
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

    private static void openServerManagerScreen(Screen screen) {
        if (!ReStudio.getInstance().isAuthenticated()) {
            RemotelyClient.INSTANCE.getHost().setScreen(new ReStudioLoginScreen(null, () -> openServerManagerScreen(screen)));
            return;
        }
        RemotelyClient.INSTANCE.openServerManager(screen);
    }

    private static void openMultiTerminalScreen(Screen screen) {
        RemotelyClient.INSTANCE.openMultiTerminal(screen);
    }

    private static void openFileExplorerScreen(Screen screen) {
        RemotelyClient.INSTANCE.openFileExplorer(screen, remotelyDir);
    }
}
