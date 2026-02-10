package redxax.oxy.remotely.adapters;

import redxax.oxy.remotely.rematrix.mc.RematrixScreen;
import restudio.rescreen.ui.core.Screen;

public class ReScreenWrapper extends RematrixScreen {
    public ReScreenWrapper(Screen libScreen) {
        super(libScreen);
    }

    public static boolean toggleScreen() {
        return RematrixScreen.toggleScreen();
    }
}
