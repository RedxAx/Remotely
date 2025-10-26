package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.instance.Instance;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.ui.widgets.ToggleWidget;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ServerPerformanceSettingsController {
    private final Instance instance;

    public ServerPerformanceSettingsController(Instance instance) {
        this.instance = instance;
    }

    public List<Setting> getSettings() {
        Setting.Builder performance = new Setting.Builder("Performance Settings");

        int viewDistance = Integer.parseInt(instance.getServerProperties().getProperty("view-distance", "8"));
        DoubleSliderWidget viewDistanceSlider = createIntSlider(2, 32, viewDistance, val -> instance.getServerProperties().setProperty("view-distance", String.valueOf(val)));
        performance.addRow("View Distance", true, 20, viewDistanceSlider);

        int simDistance = Integer.parseInt(instance.getServerProperties().getProperty("simulation-distance", "8"));
        DoubleSliderWidget simDistanceSlider = createIntSlider(2, 32, simDistance, val -> instance.getServerProperties().setProperty("simulation-distance", String.valueOf(val)));
        performance.addRow("Simulation Distance", true, 20, simDistanceSlider);

        TextInputWidget memoryWidget = new TextInputWidget.Builder()
                .text(instance.getSettings().getProperty("memory", "4G"))
                .onChange(val -> instance.getSettings().setProperty("memory", val))
                .build();
        performance.addRow("Memory (e.g., 4G, 2048M)", true, 20, memoryWidget);

        ToggleWidget aikarsFlagsWidget = new ToggleWidget.Builder()
                .toggled(Boolean.parseBoolean(instance.getSettings().getProperty("aikars_flags", "true")))
                .onChange(val -> instance.getSettings().setProperty("aikars_flags", String.valueOf(val)))
                .build();
        performance.addRow("Use Aikar's Flags", false, 20, aikarsFlagsWidget);

        return List.of(performance.build());
    }

    private DoubleSliderWidget createIntSlider(int min, int max, int currentValue, java.util.function.Consumer<Integer> setter) {
        AtomicReference<DoubleSliderWidget> sliderRef = new AtomicReference<>();
        DoubleSliderWidget slider = new DoubleSliderWidget.Builder()
                .value((double) (currentValue - min) / (max - min))
                .onChange(() -> {
                    int newValue = min + (int) Math.round(sliderRef.get().getValue() * (max - min));
                    setter.accept(newValue);
                    sliderRef.get().label = String.valueOf(newValue);
                })
                .label(String.valueOf(currentValue))
                .build();
        sliderRef.set(slider);
        return slider;
    }
}