package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ServerPlanSettingsController {

    private final AtomicReference<ServerModels.Plan> selectedPlan = new AtomicReference<>();
    private final List<ServerModels.Plan> availablePlans = new ArrayList<>();
    private DropDownWidget<ServerModels.Plan> planDropdown;

    public ServerPlanSettingsController() {
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Server Plan");

        planDropdown = new DropDownWidget.Builder<>(new ArrayList<ServerModels.Plan>())
                .displayFunction(plan -> plan.name + " (" + formatPrice(plan.priceCents) + ")")
                .onSelectionChanged(selectedPlan::set)
                .size(300, 20)
                .build();

        MountableButtonWidget planWidget = new MountableButtonWidget.Builder("Select Plan")
                .description("Choose a hosting plan for your server.")
                .addWidget(planDropdown)
                .build();

        builder.addRow("", true, 30, planWidget);

        loadPlans();

        return List.of(builder.build());
    }

    private void loadPlans() {
        ReStudio.getInstance().getApi().getPlans().thenAccept(plans -> ScreenManager.getInstance().execute(() -> {
            availablePlans.clear();
            availablePlans.addAll(plans);

            availablePlans.sort(Comparator.comparingLong(p -> p.priceCents));

            planDropdown.setItems(availablePlans, availablePlans.isEmpty() ? null : availablePlans.getFirst());
            if (!availablePlans.isEmpty()) {
                selectedPlan.set(availablePlans.getFirst());
            }
        })).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });
    }

    public String getSelectedPlanName() {
        ServerModels.Plan plan = selectedPlan.get();
        return plan != null ? plan.name : null;
    }

    private String formatPrice(long cents) {
        return String.format("$%.2f/mo", cents / 100.0);
    }
}
