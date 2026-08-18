package redxax.oxy.remotely.config;

import java.util.List;

public interface RemotelyViewStateStore {
    State getViewState();

    void setViewState(State state);

    record TerminalTab(String serverId, String name) {
        public TerminalTab {
            serverId = normalize(serverId);
            name = normalize(name);
        }
    }

    record State(String hostKey, String serverId, String tabId, String viewId, List<TerminalTab> terminalTabs,
                 int terminalTabIndex) {
        public State {
            hostKey = normalize(hostKey);
            serverId = normalize(serverId);
            tabId = normalize(tabId);
            viewId = normalize(viewId);
            terminalTabs = terminalTabs == null ? List.of() : terminalTabs.stream()
                    .filter(tab -> tab != null && !tab.serverId().isBlank())
                    .distinct()
                    .toList();
            terminalTabIndex = terminalTabs.isEmpty() ? 0 : Math.clamp(terminalTabIndex, 0, terminalTabs.size() - 1);
        }

        public State(String hostKey, String serverId, String tabId, String viewId) {
            this(hostKey, serverId, tabId, viewId, List.of(), 0);
        }

        public static State empty() {
            return new State("", "", "", "", List.of(), 0);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
