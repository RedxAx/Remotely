package redxax.oxy.remotely.ui.settings.data;

import restudio.rebase.platform.Async;
import restudio.rescreen.ui.settings.Setting;

import java.util.List;
import java.util.Map;

final class UnavailableServerSettingsDataController implements ServerSettingsDataController {
    @Override
    public Async<Void> load() {
        return Async.completed(null);
    }

    @Override
    public Async<Void> ready() {
        return load();
    }

    @Override
    public List<String> tabNames() {
        return List.of();
    }

    @Override
    public List<Setting> settings(String tab) {
        return List.of();
    }

    @Override
    public Map<String, List<Setting>> settingsByTab() {
        return Map.of();
    }

    @Override
    public List<String> documentPaths() {
        return List.of();
    }

    @Override
    public List<String> availableDocumentPaths() {
        return List.of();
    }

    @Override
    public List<String> unavailableDocumentPaths() {
        return List.of();
    }

    @Override
    public boolean isDocumentAvailable(String relativePath) {
        return false;
    }

    @Override
    public Map<String, String> changedFileContents() {
        return Map.of();
    }

    @Override
    public Async<Void> save(Object target) {
        return Async.failed(new UnsupportedOperationException("Server Settings Are Unavailable"));
    }
}
