package redxax.oxy.remotely.ui.server;

import restudio.rebase.ui.screens.resources.ResourceContainer;
import restudio.rescreen.ui.rescreen.ReScreen;
import restudio.rescreen.ui.widgets.AnimatedWidget;
import restudio.rescreen.ui.widgets.RowWidget;
import restudio.rebase.backend.CapabilityDescriptor;

import java.util.Map;

public final class CanonicalResourceContainerAdapter implements ResourceContainerAdapter {
    private final ResourceContainer delegate;

    public CanonicalResourceContainerAdapter(ResourceContainer delegate) {
        this.delegate = delegate;
    }

    @Override
    public AnimatedWidget widget() {
        return delegate;
    }

    @Override
    public RowWidget selectorsRow() {
        return delegate.getSelectorsRow();
    }

    @Override
    public void setHost(ReScreen host) {
        delegate.setHost(host);
    }

    @Override
    public void openInstanceResources() {
        delegate.openInstanceResources();
    }

    @Override
    public void showUpdateAllDialog() {
        delegate.showUpdateAllDialog();
    }

    @Override
    public void search(String query) {
        delegate.search(query);
    }

    @Override
    public void setSelectorsVisible(boolean visible) {
        delegate.setSelectorsVisible(visible);
    }

    @Override
    public void ensureSelectorsSynced() {
        delegate.ensureSelectorsSynced();
    }

    @Override
    public void loadResources() {
        delegate.loadResources();
    }

    @Override
    public void loadResources(boolean force) {
        delegate.loadResources(force);
    }

    @Override
    public void resetLoadingState() {
        delegate.resetLoadingState();
    }

    @Override
    public Map<String, CapabilityDescriptor> capabilities() {
        return delegate.capabilities();
    }

    @Override
    public void clearSelectionOutsideResource(double mouseX, double mouseY) {
        delegate.clearSelectionOutsideResource(mouseX, mouseY);
    }

    @Override
    public void cleanup() {
        delegate.cleanup();
    }
}
