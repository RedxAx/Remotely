package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.MountableButtonWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class ServerPlanSettingsController {

    private final AtomicReference<ServerModels.Plan> selectedPlan = new AtomicReference<>();
    private final List<ServerModels.Plan> availablePlans = new ArrayList<>();
    private DropDownWidget<ServerModels.Plan> planDropdown;
    private TextInputWidget subdomainInput;
    private String selectedPlanName;

    public ServerPlanSettingsController() {
    }

    public void selectPlanByName(String planName) {
        selectedPlanName = planName;
        if (planDropdown == null || availablePlans.isEmpty() || planName == null || planName.isBlank()) {
            return;
        }
        ServerModels.Plan preferred = null;
        for (ServerModels.Plan candidate : availablePlans) {
            if (candidate != null && candidate.name != null && candidate.name.equalsIgnoreCase(planName)) {
                preferred = candidate;
                break;
            }
        }
        planDropdown.setItems(availablePlans, preferred);
        selectedPlan.set(planDropdown.getSelectedItem());
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Server Plan");

        planDropdown = new DropDownWidget.Builder<ServerModels.Plan>(new ArrayList<>())
                .displayFunction(this::formatPlanOption)
                .onSelectionChanged(plan -> {
                    selectedPlan.set(plan);
                    if (plan != null && plan.name != null && !plan.name.isBlank()) {
                        selectedPlanName = plan.name;
                    }
                })
                .size(150, 20)
                .build();

        MountableButtonWidget planWidget = new MountableButtonWidget.Builder("Reactor Plans")
                .description("Choose the plan for this Reactor server.")
                .addWidget(planDropdown)
                .build();

        builder.addRow("", true, 30, planWidget);

        subdomainInput = new TextInputWidget.Builder()
                .placeholder("Optional (e.g. myserver)")
                .size(150, 20)
                .build();

        MountableButtonWidget subdomainWidget = new MountableButtonWidget.Builder("Custom Subdomain")
                .description("Set a custom subdomain (e.g. myserver.restudiomc.net).")
                .addWidget(subdomainInput)
                .build();

        builder.addRow("", true, 30, subdomainWidget);

        loadPlans();

        return List.of(builder.build());
    }

    private void loadPlans() {
        ReStudio.getInstance().getApi().getPlans().thenAccept(plans -> ScreenManager.getInstance().execute(() -> {
            availablePlans.clear();
            if (plans != null) {
                availablePlans.addAll(plans);
            }

            availablePlans.sort(Comparator.comparingLong(p -> p.priceCents));

            if (planDropdown == null) {
                return;
            }

            ServerModels.Plan preferred = selectedPlan.get();
            if (preferred == null && selectedPlanName != null && !selectedPlanName.isBlank()) {
                for (ServerModels.Plan candidate : availablePlans) {
                    if (candidate != null && candidate.name != null && candidate.name.equalsIgnoreCase(selectedPlanName)) {
                        preferred = candidate;
                        break;
                    }
                }
            }

            if (availablePlans.isEmpty()) {
                planDropdown.setItems(List.of(), null);
                selectedPlan.set(null);
                return;
            }

            planDropdown.setItems(availablePlans, preferred);
            selectedPlan.set(planDropdown.getSelectedItem());
        })).exceptionally(e -> {
            ScreenManager.getInstance().execute(() -> {
                if (planDropdown != null) {
                    planDropdown.setItems(List.of(), null);
                }
                selectedPlan.set(null);
            });
            return null;
        });
    }

    public String getSelectedPlanName() {
        ServerModels.Plan plan = selectedPlan.get();
        return plan != null ? plan.name : null;
    }

    public String getSubdomain() {
        return subdomainInput != null ? subdomainInput.getText() : null;
    }

    private String formatPlanOption(ServerModels.Plan plan) {
        if (plan == null) {
            return "Select Plan";
        }
        String name = plan.name == null || plan.name.isBlank() ? "Plan" : plan.name;
        long cents = Math.max(0L, plan.priceCents);
        return String.format(Locale.US, "%s - $%.2f/mo", name, cents / 100.0);
    }
}
