package redxax.oxy.remotely.ui.integrations.luckperms;

import restudio.rescreen.util.Identifier;

import java.util.function.Consumer;

public class LuckPermsServerIcons {
    public Identifier icon(String instanceId) {
        return Identifier.icon("server.png");
    }

    public void load(String instanceId, Consumer<Identifier> loaded) {
        if (loaded != null) loaded.accept(icon(instanceId));
    }

    public void refresh() {
    }
}
