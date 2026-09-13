package redxax.oxy.remotely.web.platform;

import restudio.rebase.backend.TransferSource;
import restudio.rebase.restudio.marketplace.MarketplaceDetailsProvider;
import restudio.rebase.ui.widgets.editor.MarkdownMediaUploadSupport;
import restudio.rebase.ui.widgets.editor.TextAreaWidget;
import restudio.rescreen.platform.browser.BrowserFile;
import restudio.rescreen.platform.browser.BrowserHostActionHandler;

import java.util.ArrayList;
import java.util.List;

final class BrowserMarkdownImageUploadSupport {
    private static Object owner;

    private BrowserMarkdownImageUploadSupport() {
    }

    static void install(Object value, BrowserHostActionHandler host, MarketplaceDetailsProvider provider) {
        owner = value;
        TextAreaWidget.setMarkdownImageUploadHandler(new TextAreaWidget.MarkdownImageUploadHandler() {
            @Override
            public boolean pasteUploadAndInsert(TextAreaWidget editor, String projectId, String category) {
                return false;
            }

            @Override
            public boolean pasteFilesAndInsert(TextAreaWidget editor, String projectId, String category, List<?> files) {
                if (host == null || provider == null || projectId == null || projectId.isBlank()
                        || category == null || category.isBlank() || files == null || files.isEmpty()) return false;
                List<BrowserFile> media = new ArrayList<>();
                for (Object value : files) {
                    if (value instanceof BrowserFile file && MarkdownMediaUploadSupport.supported(file.name(), file.type())) {
                        media.add(file);
                    }
                }
                if (media.isEmpty()) return false;
                List<TransferSource> sources = BrowserTransferBridge.sources(media, host);
                if (sources.size() != media.size()) return false;
                List<MarkdownMediaUploadSupport.MediaFile> uploads = new ArrayList<>(media.size());
                for (int index = 0; index < media.size(); index++) {
                    BrowserFile file = media.get(index);
                    uploads.add(new MarkdownMediaUploadSupport.MediaFile(file.name(), file.type(), sources.get(index)));
                }
                return MarkdownMediaUploadSupport.uploadAndInsert(editor, projectId, category, provider, uploads,
                        () -> sources.forEach(BrowserTransferBridge::release));
            }
        });
    }

    static void close(Object value) {
        if (owner != value) return;
        owner = null;
        TextAreaWidget.setMarkdownImageUploadHandler(null);
    }
}
