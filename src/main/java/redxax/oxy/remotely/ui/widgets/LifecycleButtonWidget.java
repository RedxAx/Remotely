package redxax.oxy.remotely.ui.widgets;

import restudio.rebase.instance.InstanceState;
import restudio.rescreen.theme.ThemeManager;
import restudio.rescreen.ui.widgets.IconButton;
import restudio.rescreen.util.Identifier;

public class LifecycleButtonWidget extends IconButton {
    private InstanceState renderedState;
    private boolean renderedKillConfirmation;
    private boolean renderedKilling;

    public LifecycleButtonWidget(Runnable action) {
        super(0, 0, 18, 18, "", Identifier.icon("start.png"));
        this.action = action;
        autoWidthOnTextChange = true;
        setAnimateLayout(true);
        elevateOnFocused = false;
        update(InstanceState.STOPPED);
    }

    public void update(InstanceState state) {
        update(state, false, false);
    }

    public void update(InstanceState state, boolean killConfirmation, boolean killing) {
        InstanceState current = state == null ? InstanceState.STOPPED : state;
        if (current == renderedState && killConfirmation == renderedKillConfirmation && killing == renderedKilling) {
            return;
        }
        renderedState = current;
        renderedKillConfirmation = killConfirmation;
        renderedKilling = killing;
        if (current == InstanceState.STOPPED || current == InstanceState.CRASHED) {
            setMessage("");
            setWidth(18);
            setIcon("start.png");
            setHint("Start Server");
            setAccent(current == InstanceState.CRASHED ? ThemeManager.getAccent("danger") : ThemeManager.getAccent("nice"));
            return;
        }
        String label = title(current);
        if (current == InstanceState.STOPPING) {
            label = killing ? "Killing" : killConfirmation ? "Kill Server?" : label;
        }
        setMessage(label);
        if (current == InstanceState.STARTING) {
            setIcon(Identifier.animatedIcon("loadingGreen"));
            setHint("Starting Server");
            setAccent(ThemeManager.getAccent("nice"));
        } else if (current == InstanceState.STOPPING) {
            setIcon(killConfirmation ? Identifier.icon("report.png") : Identifier.animatedIcon("loadingRed"));
            setHint(killConfirmation ? "Confirm Kill Server" : "Stopping Server");
            setAccent(ThemeManager.getAccent("danger"));
        } else if (current == InstanceState.SAVED || current == InstanceState.SAVING) {
            setIcon("stop.png");
            setHint("Stop Server");
            setAccent(ThemeManager.getAccent("calm"));
        } else {
            setIcon("stop.png");
            setHint("Stop Server");
            setAccent(ThemeManager.getAccent("danger"));
        }
    }

    public static boolean canStart(InstanceState state) {
        return state == null || state == InstanceState.STOPPED || state == InstanceState.CRASHED;
    }

    private String title(InstanceState state) {
        String value = state.name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
