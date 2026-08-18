package redxax.oxy.remotely.session;

import redxax.oxy.remotely.ui.server.containers.PlayersContainer;
import redxax.oxy.remotely.ui.server.ResourceContainerAdapter;
import redxax.oxy.remotely.ui.server.ServerScreenHost;
import restudio.rebase.api.unified.internal.StandardOutputStateParser;
import restudio.rebase.ui.widgets.TerminalWidget;
import java.util.function.BiConsumer;

public class TerminalSession {
    private final Object tabId;
    private Object instance;
    private String localTerminalId;
    private boolean isLocalTerminalMode;

    private TerminalWidget terminalWidget;
    private StandardOutputStateParser standardParser;
    private BiConsumer<Integer, String> streamDataParser;
    private ServerScreenHost.TerminalDataStream dataStream;
    private ResourceContainerAdapter resourceContainer;
    private PlayersContainer playersContainer;

    public TerminalSession(Object tabId, Object instance, String localTerminalId) {
        this.tabId = tabId;
        this.instance = instance;
        this.localTerminalId = localTerminalId;
        this.isLocalTerminalMode = (instance == null);
    }

    public Object getTabId() { return tabId; }
    public Object getInstance() { return instance; }
    public void setInstance(Object instance) {
        this.instance = instance;
        this.isLocalTerminalMode = instance == null;
    }
    public String getLocalTerminalId() { return localTerminalId; }
    public boolean isLocalTerminalMode() { return isLocalTerminalMode; }

    public TerminalWidget getTerminalWidget() { return terminalWidget; }
    public void setTerminalWidget(TerminalWidget terminalWidget) { this.terminalWidget = terminalWidget; }

    public StandardOutputStateParser getStandardParser() { return standardParser; }
    public void setStandardParser(StandardOutputStateParser standardParser) { this.standardParser = standardParser; }

    public BiConsumer<Integer, String> getStreamDataParser() { return streamDataParser; }
    public void setStreamDataParser(BiConsumer<Integer, String> streamDataParser) { this.streamDataParser = streamDataParser; }

    public ServerScreenHost.TerminalDataStream getDataStream() { return dataStream; }
    public void setDataStream(ServerScreenHost.TerminalDataStream dataStream) { this.dataStream = dataStream; }

    public ResourceContainerAdapter getResourceContainer() { return resourceContainer; }
    public void setResourceContainer(ResourceContainerAdapter resourceContainer) { this.resourceContainer = resourceContainer; }

    public PlayersContainer getPlayersContainer() { return playersContainer; }
    public void setPlayersContainer(PlayersContainer playersContainer) { this.playersContainer = playersContainer; }

    public void cleanup() {
        ServerScreenHost.TerminalDataStream stream = dataStream;
        dataStream = null;
        if (stream != null) {
            stream.stop();
        }
        if (resourceContainer != null) {
            resourceContainer.cleanup();
            resourceContainer = null;
        }
        playersContainer = null;
    }
}
