package redxax.oxy.remotely.ui.tests;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import redxax.oxy.remotely.config.Config;
import redxax.oxy.remotely.ui.widgets.*;
import redxax.oxy.remotely.util.Notification;

import java.util.Arrays;
import java.util.List;

public class WidgetTestScreen extends Screen {

    private PopupWidget testPopup;

    protected WidgetTestScreen() {
        super(Text.of("Berger"));
    }

    @Override
    public void init() {
        super.init();

        AnimatedButton cancelButton = new AnimatedButton.Builder()
                .label(Text.of("Cancel"))
                .size(80, 20)
                .onClick(() -> testPopup.hide())
                .accentType(Config.AccentType.DANGER)
                .build();

        AnimatedButton okButton = new AnimatedButton.Builder()
                .label(Text.of("OK"))
                .size(80, 20)
                .onClick(() -> {
                    new Notification("Confirmed!", Notification.Type.SUCCESS);
                    testPopup.hide();
                })
                .build();

        this.testPopup = new PopupWidget.Builder("Upgraded Popup")
                .pos((width / 2) - (350 / 2), 50)
                .size(350, 500)
                .setResizable(true)
                .setMinSize(250, 300)
                .onClose(() -> new Notification("Popup closed!", Notification.Type.INFO))

                .addTextField("Username", "RedxAx", (newValue) -> System.out.println("Username changed: " + newValue))
                .addDropdown("Difficulty", Arrays.asList("Easy", "Normal", "Hard", "Nightmare", "Ree*"), "Normal", String::toString, (selection) -> System.out.println("Difficulty: " + selection))

                .addRow("Game Settings", true, 20,
                        new TabSwitchWidget.Builder()
                                .options(Arrays.asList("Survival", "Creative", "Spectator"))
                                .onChange((index) -> System.out.println("Mode index: " + index))
                                .build(),
                        new ScrollSelectorWidget.Builder()
                                .options(List.of("Day", "Night", "Twilight"))
                                .onChange((index) -> System.out.println("Time index: " + index))
                                .build()
                )

                .addDoubleSlider("Volume", 0.75, (value) -> System.out.println("Volume set to: " + value))
                .addTextArea("Description", "This is a multi-line text area.\nIt supports scrolling and text editing.", 100, (text) -> System.out.println("Description updated"))

                .addRow("Toggles", false, 18,
                        new ToggleWidget.Builder().toggled(true).build(),
                        new ToggleWidget.Builder().build()
                )

                .addRow("", false, 20, cancelButton, okButton).build();

        this.testPopup.hide();

        addDrawableChild(new AnimatedButton.Builder().pos(10, 10).size(120, 20).label(Text.of("Show Popup")).onClick(this.testPopup::show).build());
        addDrawableChild(this.testPopup);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (testPopup.visible) {
                testPopup.hide();
                return true;
            }
            MinecraftClient.getInstance().setScreen(new WidgetsTestingScreen());
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}