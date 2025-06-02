package redxax.oxy.remotely.ui.tests;

import net.minecraft.text.Text;
import redxax.oxy.remotely.ui.ReScreen;
import redxax.oxy.remotely.ui.widgets.*;

import java.util.List;
import java.util.Random;

public class ContainerTestingScreen extends ReScreen {
    private final Random random = new Random();

    public ContainerTestingScreen() {
        super(Text.of("Container Testing Screen"));
    }

    private AnimatedWidget randomWidget() {
        int minW = 20, maxW = 150, minH = 18, maxH = 100;
        int w = minW + random.nextInt(maxW - minW + 1);
        int h = minH + random.nextInt(maxH - minH + 1);
        return new AnimatedButton.ButtonBuilder().size(w, h).label(Text.literal(w + "x" + h)).build();
    }

    @Override
    public void init() {
        super.init();
        AnimatedWidget.setCornerSpeedMultiplier(AnimatedWidget.EntranceCorner.TOP_LEFT, .2f);

        Container restrictedContainer = createContainer(String.valueOf("Container 1".hashCode()), 5, 60, width - 10, height - 5);
                restrictedContainer.columns(3)
                .layoutStyle(Container.LayoutStyle.RESTRICTED)
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Click Me")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Another Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Another Button")).onClick(() -> restrictedContainer.addWidget(
                        new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Button " + (restrictedContainer.getWidgets().size() + 1))).build())).build())
                .addWidget(new ScrollSelectorWidget.Builder().size(100, 20).options(List.of("Option 1", "Option 2", "Option 3", "Option 4", "Option 5")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Toggle Button")).onClick(() -> restrictedContainer.addWidget(new ToggleWidget.Builder().size(100, 20).build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Slider Button")).onClick(() -> restrictedContainer.addWidget(new DoubleSliderWidget.Builder().build())).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Increase Columns")).onClick(() -> restrictedContainer.columns(restrictedContainer.getColumns() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Decrease Columns")).onClick(() -> restrictedContainer.columns(restrictedContainer.getColumns() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Padding")).onClick(() -> restrictedContainer.padding(restrictedContainer.getPadding() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Padding")).onClick(() -> restrictedContainer.padding(restrictedContainer.getPadding() - 1)).build());

        Container managedContainer = createContainer(String.valueOf("ManagedContainer".hashCode()), 5, 60, width - 10, height - 5);
        managedContainer.columns(3).layoutStyle(Container.LayoutStyle.MANAGED)
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Increase Columns")).onClick(() -> managedContainer.columns(managedContainer.getColumns() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.of("Decrease Columns")).onClick(() -> managedContainer.columns(managedContainer.getColumns() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Padding")).onClick(() -> managedContainer.padding(managedContainer.getPadding() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Padding")).onClick(() -> managedContainer.padding(managedContainer.getPadding() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(120, 20).label(Text.literal("Add Random Widget")).onClick(() -> managedContainer.addWidget(randomWidget())).build());

        Container freeContainer = createContainer(String.valueOf("FreeContainer".hashCode()), 5, 60, width - 10, height - 5);
        freeContainer.layoutStyle(Container.LayoutStyle.FREE)
                .addWidget(new AnimatedButton.ButtonBuilder().size(120, 20).label(Text.literal("Add Random Widget")).onClick(() -> freeContainer.addWidget(randomWidget())).build());

        SidePanel sidePanel = createSidePanel("Global Panel").y(60).height(height - 5).width(width - 50);
        sidePanel.addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Panel Button")).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Increase Columns")).onClick(() -> sidePanel.container().columns(sidePanel.container().getColumns() + 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Decrease Columns")).onClick(() -> sidePanel.container().columns(sidePanel.container().getColumns() - 1)).build())
                .addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Add Another Button")).onClick(() ->
                        sidePanel.addWidget(new AnimatedButton.ButtonBuilder().size(100, 20).label(Text.literal("Button " + (sidePanel.container().getWidgets().size() + 1))).build())).build())
                .addWidget(new TextInputWidget.Builder().size(100, 20).placeholder("Type here...").text("Sample Text").build());

        tabs().addTab("Restricted", restrictedContainer);
        tabs().addTab("Managed", managedContainer);
        tabs().addTab("Free", freeContainer);

        tabsManager.builder().allowReorder(true).allowAdd(true).position(5, 36).size(width - 5, 18).onPlusButtonClicked(() -> tabs().addTab("Tab " + tabsManager.getTabs().size(), createContainer(5, 60, width - 10, height - 5)
                        .addWidget(new AnimatedButton(0, 0, 100, 20, Text.of("Berger " + tabsManager.getTabs().size()))))).build();
        tabsManager.setActiveTab(restrictedContainer);
        headerBuilder.addRight("/assets/remotely/icons/close.png", () -> client.setScreen(null), "Close Screen")
                .addRight("/assets/remotely/icons/unmerge.png", sidePanel::toggle, "Toggle Side Panel")
                .addLeft("/assets/remotely/icons/remotely.png", () -> client.setScreen(new ContainerTestingScreen()), "Reload Screen")
                .build();
    }
}