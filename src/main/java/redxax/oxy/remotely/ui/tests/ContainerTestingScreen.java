package redxax.oxy.remotely.ui.tests;

import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.*;

import java.util.List;

public class ContainerTestingScreen extends ReScreen {
    public ContainerTestingScreen() {
        super(Text.of("Container Testing Screen"));
    }

    @Override
    public void init() {
        super.init();
        AnimatedWidget.setCornerSpeedMultiplier(AnimatedWidget.EntranceCorner.TOP_LEFT, .2f);
        headerBuilder.addRight("/assets/remotely/icons/close.png", () -> client.setScreen(null), "Close Screen")
        .addRight("/assets/remotely/icons/sidepanel.png", () -> container.sidePanel().toggle(), "Toggle Side Panel")
        .addLeft("/assets/remotely/icons/remotely.png", () -> client.setScreen(new ContainerTestingScreen()), "Reload Screen").build();
        createContainer(5, 60, width - 10, height - 5).columns(3).addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Click Me")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Another Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Another Button")).onClick(() -> container.addWidget(new AnimatedButton.ButtonBuilder()
                        .size(100, 20).label(Text.literal("Button " + (container.getWidgets().size() + 1))).build())).build())
                .addWidget(new ScrollSelectorWidget.Builder().size(100, 20).options(List.of("Option 1", "Option 2", "Option 3", "Option 4", "Option 5")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Toggle Button")).onClick(() -> container.addWidget(new ToggleWidget.Builder().size(100, 20).build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Slider Button")).onClick(() -> container.addWidget(new DoubleSliderWidget.Builder().build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Increase Columns")).onClick(() -> container.columns(container.getColumns() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Decrease Columns")).onClick(() -> container.columns(container.getColumns() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Padding")).onClick(() -> container.padding(container.getPadding() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Padding")).onClick(() -> container.padding(container.getPadding() - 1)).build()

        );
        container.sidePanel().create().addWidget(new AnimatedButton.ButtonBuilder()
                .size(100, 20).label(Text.literal("Panel Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Columns")).onClick(() -> sidePanel.columns(sidePanel.getColumns() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Columns")).onClick(() -> sidePanel.columns(sidePanel.getColumns() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Another Button")).onClick(() ->
                        sidePanel.addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Button " + (sidePanel.getWidgets().size() + 1))).build())).build())
                .addWidget(new TextInputWidget.Builder().size(100, 20).placeholder("Type here...").text("Sample Text").build());

        tabs().addTab("Testing", "data");
        tabs().addTab("Widgets", "widgets");
        tabs().addTab("Settings", "settings");
        tabs().addTab("Animations", "animations");
        tabs().addTab("Containers", "containers");
        tabs().addTab("Side Panel", "sidepanel");
        tabs().addTab("Reorder", "reorder");
        tabs().addTab("Scrollable", "scrollable");
        tabs().addTab("State Cache", "statecache");
        tabs().addTab("Reorderable", "reorderable");
        tabsManager.builder().allowReorder(true).position(5, 35).size(width - 5, 18).enableStateCache(true).allowReorder(true).build();


    }
}
