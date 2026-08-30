package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;

import java.util.List;

public interface PackContentSettingsProvider {
    record Availability(boolean available, String reason) {
        public Availability {
            reason = reason == null ? "" : reason.trim();
        }

        public static Availability supported() {
            return new Availability(true, "");
        }

        public static Availability unavailable(String reason) {
            return new Availability(false, reason);
        }
    }

    record ProviderStatus(String workspaceName, String providerName, String rootName, int glyphCount, int frameCount, int diagnosticCount) {
    }

    record Diagnostic(String providerId, String sourceFile, String message) {
    }

    Availability refreshAvailability();

    List<ProviderStatus> statuses();

    List<Diagnostic> diagnostics();

    Async<Integer> refresh();

    static PackContentSettingsProvider unavailable(String reason) {
        String message = reason == null || reason.isBlank() ? "Pack Content Refresh Is Unavailable" : reason;
        return new PackContentSettingsProvider() {
            @Override
            public Availability refreshAvailability() {
                return Availability.unavailable(message);
            }

            @Override
            public List<ProviderStatus> statuses() {
                return List.of();
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }

            @Override
            public Async<Integer> refresh() {
                return Async.failed(new UnsupportedOperationException(message));
            }
        };
    }
}
