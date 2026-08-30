package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rescreen.platform.Async;
import restudio.rebase.restudio.api.models.ServerModels;

public interface ReProxySettingsCapability {
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

    boolean authenticated();

    Availability availability();

    Async<ServerModels.ReProxySummary> summary();

    Async<ServerModels.ReProxyDomain> createDomain(String subdomain);

    Async<Void> deleteDomain(String domainId);

    Async<Void> stopTunnel(String tunnelId);

    void copyAddress(String address);

    static ReProxySettingsCapability unavailable(boolean authenticated, String reason) {
        String message = reason == null || reason.isBlank() ? "ReProxy Is Unavailable" : reason;
        return new ReProxySettingsCapability() {
            @Override
            public boolean authenticated() {
                return authenticated;
            }

            @Override
            public Availability availability() {
                return Availability.unavailable(message);
            }

            @Override
            public Async<ServerModels.ReProxySummary> summary() {
                return failed(message);
            }

            @Override
            public Async<ServerModels.ReProxyDomain> createDomain(String subdomain) {
                return failed(message);
            }

            @Override
            public Async<Void> deleteDomain(String domainId) {
                return failed(message);
            }

            @Override
            public Async<Void> stopTunnel(String tunnelId) {
                return failed(message);
            }

            @Override
            public void copyAddress(String address) {
            }

            private <T> Async<T> failed(String reason) {
                return Async.failed(new UnsupportedOperationException(reason));
            }
        };
    }
}
