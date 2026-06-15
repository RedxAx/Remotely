package redxax.oxy.remotely.session;

import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import redxax.oxy.remotely.ui.server.containers.ResourceContainer;
import restudio.rebase.api.unified.internal.StandardOutputStateParser;
import restudio.rebase.instance.Instance;
import restudio.rebase.ui.widgets.TerminalWidget;
import java.util.function.BiConsumer;

public class TerminalSession {
    private final Object tabId;
    private Instance instance;
    private String localTerminalId;
    private boolean isLocalTerminalMode;

    private TerminalWidget terminalWidget;
    private StandardOutputStateParser standardParser;
    private BiConsumer<Integer, String> streamDataParser;
    private ResourceContainer resourceContainer;
    private PlayersContainer playersContainer;

    public TerminalSession(Object tabId, Instance instance, String localTerminalId) {
        this.tabId = tabId;
        this.instance = instance;
        this.localTerminalId = localTerminalId;
        this.isLocalTerminalMode = (instance == null);
    }

    public Object getTabId() { return tabId; }
    public Instance getInstance() { return instance; }
    public String getLocalTerminalId() { return localTerminalId; }
    public boolean isLocalTerminalMode() { return isLocalTerminalMode; }

    public TerminalWidget getTerminalWidget() { return terminalWidget; }
    public void setTerminalWidget(TerminalWidget terminalWidget) { this.terminalWidget = terminalWidget; }

    public StandardOutputStateParser getStandardParser() { return standardParser; }
    public void setStandardParser(StandardOutputStateParser standardParser) { this.standardParser = standardParser; }

    public BiConsumer<Integer, String> getStreamDataParser() { return streamDataParser; }
    public void setStreamDataParser(BiConsumer<Integer, String> streamDataParser) { this.streamDataParser = streamDataParser; }

    public ResourceContainer getResourceContainer() { return resourceContainer; }
    public void setResourceContainer(ResourceContainer resourceContainer) { this.resourceContainer = resourceContainer; }

    public PlayersContainer getPlayersContainer() { return playersContainer; }
    public void setPlayersContainer(PlayersContainer playersContainer) { this.playersContainer = playersContainer; }

    public void cleanup() {
        if (resourceContainer != null) {
            resourceContainer.cleanup();
        }
        if (playersContainer != null) {
            playersContainer.fullRefresh();
        }

    }
}
