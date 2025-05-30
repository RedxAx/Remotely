package redxax.oxy.remotely.ui.tests;

import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.AnimatedButton;
import redxax.oxy.remotely.ui.widgets.AnimatedWidget;
import redxax.oxy.remotely.ui.widgets.DoubleSliderWidget;
import redxax.oxy.remotely.ui.widgets.ToggleWidget;

public class ContainerTestingScreen extends ReScreen {
    protected ContainerTestingScreen(Text title) {
        super(title);
    }

    @Override
    public void init() {
        super.init();
        AnimatedWidget.setCornerSpeedMultiplier(AnimatedWidget.EntranceCorner.TOP_LEFT, .2f);
        headerBuilder.addRight("/assets/remotely/icons/close.png", () -> client.setScreen(null), "Close Screen");
        headerBuilder.addLeft("/assets/remotely/icons/remotely.png", () -> client.setScreen(new ContainerTestingScreen(Text.of("Container Testing"))), "Reload Screen").build();
        container.pos(5, 60).size(width - 10, height - 5).columns(1).addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Click Me")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Another Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Yet Another Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Yet Another Other Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Another Button")).onClick(() -> container.addWidget(new AnimatedButton.ButtonBuilder()
                        .size(100, 20).label(Text.literal("Button " + (container.getWidgets().size() + 1)))
                        .build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Toggle Button")).onClick(() -> container.addWidget(new ToggleWidget.Builder().build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Slider Button")).onClick(() -> container.addWidget(new DoubleSliderWidget.Builder().build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Padding")).onClick(() -> container.padding(container.getPadding() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Padding")).onClick(() -> container.padding(container.getPadding() - 1)).build()
        );
    }
}
