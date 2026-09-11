package redxax.oxy.remotely.web.platform;

import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rescreen.platform.Async;
import restudio.rescreen.platform.browser.BrowserFile;
import restudio.rescreen.platform.browser.BrowserHostActionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BrowserTransferBridge {
    private BrowserTransferBridge() {
    }

    public static TransferSource source(BrowserFile file) {
        Objects.requireNonNull(file, "file");
        return TransferSource.fromDataUrl(file.name(), file.dataUrl());
    }

    public static TransferSource source(BrowserHostActionHandler host, BrowserFile file) {
        Objects.requireNonNull(file, "file");
        if (host == null || file.selectionId() <= 0 || file.fileIndex() < 0) return source(file);
        return new BrowserFileSource(host, file, new SelectionLease(host, file.selectionId(), 1));
    }

    public static List<TransferSource> sources(List<BrowserFile> files) {
        return files == null ? List.of() : files.stream().filter(Objects::nonNull).map(BrowserTransferBridge::source).toList();
    }

    public static List<TransferSource> sources(List<BrowserFile> files, BrowserHostActionHandler host) {
        if (files == null || files.isEmpty()) return List.of();
        Map<Integer, Integer> handleCounts = new HashMap<>();
        files.stream().filter(Objects::nonNull)
                .filter(file -> host != null && file.selectionId() > 0 && file.fileIndex() >= 0)
                .forEach(file -> handleCounts.merge(file.selectionId(), 1, Integer::sum));
        Map<Integer, SelectionLease> leases = new HashMap<>();
        List<TransferSource> result = new ArrayList<>();
        for (BrowserFile file : files) {
            if (file == null) continue;
            if (host != null && file.selectionId() > 0 && file.fileIndex() >= 0) {
                SelectionLease lease = leases.computeIfAbsent(file.selectionId(), ignored -> new SelectionLease(host, file.selectionId(), handleCounts.getOrDefault(file.selectionId(), 1)));
                result.add(new BrowserFileSource(host, file, lease));
            } else {
                result.add(source(file));
            }
        }
        return List.copyOf(result);
    }

    public static TransferSink downloadSink(BrowserHostActionHandler host, String name, String contentType) {
        Objects.requireNonNull(host, "host");
        int downloadId = host.beginChunkedDownload(name, contentType);
        if (downloadId < 0) {
            throw new IllegalStateException("Browser Download Is Unavailable");
        }
        return new ChunkedDownloadSink(host, downloadId, name);
    }

    public static Async<Void> abort(TransferSink sink) {
        if (sink instanceof ChunkedDownloadSink chunked) {
            return chunked.abort();
        }
        return sink == null ? Async.completed(null) : sink.close();
    }

    public static void cancelDownload(BrowserHostActionHandler host, int downloadId) {
        if (host != null && downloadId >= 0) {
            host.cancelChunkedDownload(downloadId);
        }
    }

    static void release(TransferSource source) {
        if (source instanceof BrowserFileSource browserFile) browserFile.release();
    }

    static final class BrowserFileSource implements TransferSource {
        private final BrowserHostActionHandler host;
        private final BrowserFile file;
        private final SelectionLease lease;
        private long offset;
        private boolean ended;
        private boolean released;

        private BrowserFileSource(BrowserHostActionHandler host, BrowserFile file, SelectionLease lease) {
            this.host = host;
            this.file = file;
            this.lease = lease;
        }

        BrowserFile file() {
            return file;
        }

        void release() {
            if (released) return;
            released = true;
            lease.release();
        }

        @Override
        public String name() {
            return file.name();
        }

        @Override
        public long size() {
            return Math.max(0L, file.size());
        }

        @Override
        public Async<Chunk> next() {
            if (ended) return Async.completed(Chunk.end());
            Async<Chunk> result = Async.pending();
            int readId = host.readFileChunk(file, offset, MAX_CHUNK_BYTES, chunk -> {
                if (result.isDone()) return;
                if (chunk == null) {
                    ended = true;
                    release();
                    result.fail(new IllegalStateException("Browser File Read Failed"));
                    return;
                }
                byte[] bytes = chunk.bytes();
                offset += bytes.length;
                ended = chunk.last();
                if (ended) release();
                try {
                    result.complete(new Chunk(bytes, ended));
                } catch (Throwable failure) {
                    ended = true;
                    release();
                    result.fail(failure);
                }
            });
            if (readId < 0) {
                ended = true;
                release();
                result.fail(new IllegalStateException("Browser File Read Is Unavailable"));
                return result;
            }
            result.onCancel(() -> host.cancelFileChunk(readId));
            return result;
        }
    }

    private static final class SelectionLease {
        private final BrowserHostActionHandler host;
        private final int selectionId;
        private int remaining;

        private SelectionLease(BrowserHostActionHandler host, int selectionId, int remaining) {
            this.host = host;
            this.selectionId = selectionId;
            this.remaining = Math.max(1, remaining);
        }

        private synchronized void release() {
            if (remaining <= 0) return;
            remaining--;
            if (remaining == 0) host.releaseFileSelection(selectionId);
        }
    }

    private static final class ChunkedDownloadSink implements TransferSink {
        private final BrowserHostActionHandler host;
        private final int downloadId;
        private final String name;
        private boolean terminated;

        private ChunkedDownloadSink(BrowserHostActionHandler host, int downloadId, String name) {
            this.host = host;
            this.downloadId = downloadId;
            this.name = name == null || name.isBlank() ? "download" : name.strip();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public synchronized Async<Void> write(TransferSource.Chunk chunk) {
            if (terminated) return Async.failed(new IllegalStateException("Browser Download Is Closed"));
            if (chunk == null) return Async.failed(new NullPointerException("chunk"));
            try {
                if (!host.appendChunk(downloadId, chunk.bytes())) {
                    return Async.failed(new IllegalStateException("Browser Download Is Closed"));
                }
                return Async.completed(null);
            } catch (Throwable failure) {
                return Async.failed(failure);
            }
        }

        @Override
        public synchronized Async<Void> close() {
            if (terminated) return Async.completed(null);
            terminated = true;
            try {
                if (!host.finishChunkedDownload(downloadId)) {
                    return Async.failed(new IllegalStateException("Browser Download Failed"));
                }
                return Async.completed(null);
            } catch (Throwable failure) {
                return Async.failed(failure);
            }
        }

        private synchronized Async<Void> abort() {
            if (terminated) return Async.completed(null);
            terminated = true;
            try {
                host.cancelChunkedDownload(downloadId);
                return Async.completed(null);
            } catch (Throwable failure) {
                return Async.failed(failure);
            }
        }
    }
}
