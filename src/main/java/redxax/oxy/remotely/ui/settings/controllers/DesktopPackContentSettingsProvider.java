package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.packcontent.PackContentRegistry;
import redxax.oxy.remotely.packcontent.RemotelyPackContentIntegration;
import restudio.rescreen.platform.Async;

import java.nio.file.Path;
import java.util.List;

public final class DesktopPackContentSettingsProvider implements PackContentSettingsProvider {
    @Override
    public Availability refreshAvailability() {
        return Availability.supported();
    }

    @Override
    public List<ProviderStatus> statuses() {
        return PackContentRegistry.get().statuses().stream()
                .map(status -> new ProviderStatus(status.workspaceName(), status.providerName(), rootName(status.root()), status.glyphCount(), status.frameCount(), status.diagnosticCount()))
                .toList();
    }

    @Override
    public List<Diagnostic> diagnostics() {
        return PackContentRegistry.get().diagnostics().stream()
                .distinct()
                .map(diagnostic -> new Diagnostic(diagnostic.providerId(), diagnostic.sourceFile(), diagnostic.message()))
                .toList();
    }

    @Override
    public Async<Integer> refresh() {
        return RemotelyPackContentIntegration.refreshAllInstances();
    }

    private String rootName(Path path) {
        if (path == null) {
            return "Not Detected";
        }
        return path.getFileName() == null ? path.toString() : path.getFileName().toString();
    }
}
