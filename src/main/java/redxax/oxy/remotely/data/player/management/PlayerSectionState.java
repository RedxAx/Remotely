package redxax.oxy.remotely.data.player.management;

public record PlayerSectionState<T>(Status status, T value, String source, long updatedAt, long revision, boolean writable, String message) {
    public enum Status {
        UNSUPPORTED,
        LOADING,
        READY,
        EMPTY,
        STALE,
        FAILED
    }

    public static <T> PlayerSectionState<T> unsupported() {
        return new PlayerSectionState<>(Status.UNSUPPORTED, null, null, 0L, 0L, false, "");
    }

    public static <T> PlayerSectionState<T> loading(T previous, String source) {
        return new PlayerSectionState<>(Status.LOADING, previous, source, 0L, 0L, false, "Loading");
    }

    public static <T> PlayerSectionState<T> ready(T value, String source, long updatedAt, long revision, boolean writable, boolean empty) {
        return new PlayerSectionState<>(empty ? Status.EMPTY : Status.READY, value, source, updatedAt, revision, writable, "");
    }

    public static <T> PlayerSectionState<T> failed(T previous, String source, String message) {
        return stale(previous, source, 0L, 0L, message);
    }

    public static <T> PlayerSectionState<T> stale(T previous, String source, long updatedAt, long revision, String message) {
        return new PlayerSectionState<>(previous == null ? Status.FAILED : Status.STALE, previous, source, updatedAt, revision, false, message == null ? "Data Unavailable" : message);
    }

    public boolean supported() {
        return status != Status.UNSUPPORTED;
    }
}
