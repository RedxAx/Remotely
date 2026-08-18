package redxax.oxy.remotely.ui.settings.data;

import restudio.rebase.platform.Async;

public interface ServerSettingsDocumentStore {
    Async<Document> read(String relativePath);

    Async<Void> write(String relativePath, String content);

    record Document(boolean exists, String content) {
        public Document {
            content = content == null ? "" : content;
        }

        public static Document missing() {
            return new Document(false, "");
        }
    }
}
