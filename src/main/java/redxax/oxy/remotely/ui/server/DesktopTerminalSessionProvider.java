package redxax.oxy.remotely.ui.server;

import restudio.rebase.backend.TerminalSession;
import restudio.rebase.backend.TerminalSessionProvider;
import restudio.rebase.backend.TerminalSize;
import restudio.rebase.backend.feature.TerminalFeature;
import restudio.rebase.instance.Instance;
import restudio.rebase.terminal.TerminalProcessManager;

import java.util.Objects;

public final class DesktopTerminalSessionProvider implements TerminalSessionProvider {
    private final Instance instance;

    public DesktopTerminalSessionProvider(Instance instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    @Override
    public TerminalSession open(TerminalSize size) throws Exception {
        return open(size, false);
    }

    @Override
    public TerminalSession open(TerminalSize size, boolean serverProcess) throws Exception {
        if (instance.getBackend() == null) {
            throw new IllegalStateException("Server Backend Is Unavailable");
        }
        TerminalSize resolved = size == null ? new TerminalSize(120, 32) : size;
        if (isLocal()) {
            TerminalSession session = TerminalProcessManager.openServerSession(instance, resolved, serverProcess);
            if (session == null) {
                throw new IllegalStateException(serverProcess ? "Server Process Could Not Start" : "Server Process Is Not Running");
            }
            return session;
        }
        TerminalFeature feature = instance.getBackend().getFeature(TerminalFeature.class)
                .orElseThrow(() -> new UnsupportedOperationException("Terminal Attach Is Unavailable"));
        return feature.openSession(resolved.columns(), resolved.rows(), null);
    }

    private boolean isLocal() {
        String type = instance.getBackendConfig() == null ? null : instance.getBackendConfig().type;
        return type == null || type.isBlank() || "LOCAL".equalsIgnoreCase(type);
    }
}
