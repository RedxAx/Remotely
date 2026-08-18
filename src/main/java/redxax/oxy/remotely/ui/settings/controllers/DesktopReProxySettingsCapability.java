package redxax.oxy.remotely.ui.settings.controllers;

import restudio.rebase.platform.Async;
import restudio.rebase.restudio.ReStudio;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rescreen.util.FileUtils;

public final class DesktopReProxySettingsCapability implements ReProxySettingsCapability {
    @Override
    public boolean authenticated() {
        return ReStudio.getInstance().isAuthenticated();
    }

    @Override
    public Availability availability() {
        return Availability.supported();
    }

    @Override
    public Async<ServerModels.ReProxySummary> summary() {
        return ReStudio.getInstance().getApi().async().getReProxySummary();
    }

    @Override
    public Async<ServerModels.ReProxyDomain> createDomain(String subdomain) {
        return ReStudio.getInstance().getApi().async().createReProxyDomain(subdomain);
    }

    @Override
    public Async<Void> deleteDomain(String domainId) {
        return ReStudio.getInstance().getApi().async().deleteReProxyDomain(domainId);
    }

    @Override
    public Async<Void> stopTunnel(String tunnelId) {
        return ReStudio.getInstance().getApi().async().stopReProxyTunnel(tunnelId);
    }

    @Override
    public void copyAddress(String address) {
        FileUtils.setClipboard(address);
    }
}
