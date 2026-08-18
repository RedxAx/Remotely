package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.util.TaskSchedulers;
import restudio.rebase.backend.RemotePath;
import restudio.rebase.backend.TransferSink;
import restudio.rebase.backend.TransferSource;
import restudio.rebase.platform.Async;
import restudio.rebase.platform.TaskScheduler;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class BrowserServerFileTransfer {
    static final long MAX_UPLOAD_BYTES = 32L * 1024 * 1024;
    private static final long PROGRESS_UPDATE_INTERVAL_MILLIS = 150L;
    private final BrowserRemotelyServerApi api;
    private final String serverId;
    private final TaskScheduler scheduler;

    BrowserServerFileTransfer(BrowserRemotelyServerApi api, String serverId) {
        this(api, serverId, TaskSchedulers.current());
    }

    BrowserServerFileTransfer(BrowserRemotelyServerApi api, String serverId, TaskScheduler scheduler) {
        this.api = api;
        this.serverId = serverId;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    Async<Void> upload(List<TransferSource> sources, RemotePath destination, BiConsumer<Long, Long> progress) {
        if (sources == null || sources.isEmpty()) return Async.completed(null);
        if (sources.stream().anyMatch(source -> source == null || invalidName(source.name()))) {
            sources.forEach(BrowserTransferBridge::release);
            return Async.failed(new IllegalArgumentException("File Name Is Invalid"));
        }
        long total = 0;
        for (TransferSource source : sources) {
            long size = Math.max(0, source.size());
            if (size > MAX_UPLOAD_BYTES - total) {
                sources.forEach(BrowserTransferBridge::release);
                return Async.failed(new IllegalArgumentException("File Upload Exceeds Limit"));
            }
            total += size;
        }
        Async<Void> result = Async.pending();
        BiConsumer<Long, Long> throttledProgress = throttleProgress(progress);
        Async<?>[] active = new Async<?>[1];
        result.onCancel(() -> {
            Async<?> request = active[0];
            if (request != null) request.cancel();
        });
        uploadNext(sources, 0, normalizeDirectory(destination), 0, total, throttledProgress, active, result);
        return result;
    }

    static BiConsumer<Long, Long> throttleProgress(BiConsumer<Long, Long> callback) {
        if (callback == null) return null;
        return new BiConsumer<>() {
            private long lastUpdate;
            private long lastTransferred = Long.MIN_VALUE;

            @Override
            public void accept(Long transferred, Long total) {
                long currentTransferred = transferred == null ? 0L : Math.max(0L, transferred);
                long currentTotal = total == null ? 0L : Math.max(0L, total);
                long now = System.currentTimeMillis();
                if (currentTransferred < currentTotal && lastTransferred != Long.MIN_VALUE
                        && now - lastUpdate < PROGRESS_UPDATE_INTERVAL_MILLIS) return;
                if (currentTransferred == lastTransferred && currentTransferred < currentTotal) return;
                lastTransferred = currentTransferred;
                lastUpdate = now;
                callback.accept(currentTransferred, currentTotal);
            }
        };
    }

    Async<Void> download(List<RemotePath> sources, TransferSink destination, BiConsumer<Long, Long> progress, BooleanSupplier cancelled) {
        if (sources == null || sources.size() != 1) return Async.failed(new IllegalArgumentException("Select One File To Download"));
        if (destination == null) return Async.failed(new IllegalArgumentException("Download Destination Is Required"));
        RemotePath source = sources.getFirst();
        if (source == null || source.isRoot()) return Async.failed(new IllegalArgumentException("Select A File To Download"));
        return api.downloadFileData(serverId, normalizeFile(source), destination, progress, cancelled);
    }

    private void uploadNext(List<TransferSource> sources, int index, String directory, long sent, long total,
                            BiConsumer<Long, Long> progress, Async<?>[] active, Async<Void> result) {
        if (result.isDone()) return;
        if (index >= sources.size()) {
            result.complete(null);
            return;
        }
        TransferSource source = sources.get(index);
        if (source instanceof BrowserTransferBridge.BrowserFileSource browserFile) {
            Async<Void> request = api.uploadBrowserFile(serverId, directory, browserFile.name(), browserFile.file(),
                    (transferred, sourceTotal) -> {
                        if (progress != null) progress.accept(sent + transferred, total);
                    });
            active[0] = request;
            request.whenComplete((ignored, failure) -> {
                browserFile.release();
                if (result.isDone()) return;
                if (failure != null) {
                    result.fail(failure);
                    return;
                }
                uploadNext(sources, index + 1, directory, sent + source.size(), total, progress, active, result);
            });
            return;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(source.size(), MAX_UPLOAD_BYTES));
        collect(source, output, sent, total, progress, active, result, bytes -> {
            Async<Void> request = api.uploadFileData(serverId, directory, source.name(), bytes);
            active[0] = request;
            request.whenComplete((ignored, failure) -> {
                if (result.isDone()) return;
                if (failure != null) {
                    result.fail(failure);
                    return;
                }
                uploadNext(sources, index + 1, directory, sent + bytes.length, total, progress, active, result);
            });
        });
    }

    private void collect(TransferSource source, ByteArrayOutputStream output, long sent, long total,
                         BiConsumer<Long, Long> progress, Async<?>[] active, Async<Void> result,
                         Consumer<byte[]> complete) {
        if (result.isDone()) return;
        Async<TransferSource.Chunk> next = nextChunk(source);
        active[0] = next;
        next.whenComplete((chunk, failure) -> {
            if (result.isDone()) return;
            if (failure != null) {
                result.fail(failure);
                return;
            }
            byte[] bytes = chunk == null ? new byte[0] : chunk.bytes();
            if ((long) output.size() + bytes.length > MAX_UPLOAD_BYTES) {
                result.fail(new IllegalArgumentException("File Upload Exceeds Limit"));
                return;
            }
            output.write(bytes, 0, bytes.length);
            if (progress != null) progress.accept(sent + output.size(), total);
            if (chunk == null || chunk.last()) complete.accept(output.toByteArray());
            else collect(source, output, sent, total, progress, active, result, complete);
        });
    }

    private Async<TransferSource.Chunk> nextChunk(TransferSource source) {
        Async<TransferSource.Chunk> result = Async.pending();
        TaskScheduler.ScheduledTask[] scheduled = new TaskScheduler.ScheduledTask[1];
        Async<?>[] sourceRequest = new Async<?>[1];
        try {
            scheduled[0] = scheduler.schedule(() -> {
                if (result.isDone()) return;
                try {
                    Async<TransferSource.Chunk> request = Objects.requireNonNull(source.next(), "transfer chunk result");
                    sourceRequest[0] = request;
                    request.whenComplete((chunk, failure) -> {
                        if (result.isDone()) return;
                        if (failure == null) result.complete(chunk);
                        else result.fail(failure);
                    });
                } catch (Throwable failure) {
                    result.fail(failure);
                }
            }, Duration.ZERO);
        } catch (Throwable failure) {
            result.fail(failure);
        }
        result.onCancel(() -> {
            TaskScheduler.ScheduledTask task = scheduled[0];
            if (task != null) task.cancel();
            Async<?> request = sourceRequest[0];
            if (request != null) request.cancel();
        });
        return result;
    }

    private static String normalizeDirectory(RemotePath path) {
        String value = path == null || path.isRoot() ? "/" : path.asString();
        if (value.indexOf('\\') >= 0 || value.indexOf(':') >= 0 || value.contains("..")) throw new IllegalArgumentException("File Path Is Invalid");
        return value.startsWith("/") ? value : "/" + value;
    }

    private static String normalizeFile(RemotePath path) {
        String value = path.asString();
        if (value.indexOf('\\') >= 0 || value.indexOf(':') >= 0 || value.contains("..")) throw new IllegalArgumentException("File Path Is Invalid");
        return value.startsWith("/") ? value : "/" + value;
    }

    private static boolean invalidName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return true;
        return name.chars().anyMatch(character -> character < 32 || character == 127);
    }
}
