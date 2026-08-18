package redxax.oxy.remotely.util;

import restudio.rebase.platform.Async;
import restudio.rebase.platform.jvm.JvmAsyncBridge;

import java.util.concurrent.CompletionStage;

public final class DesktopAsyncTools {
    private DesktopAsyncTools() {
    }

    public static <T> Async<T> adapt(CompletionStage<T> value) {
        return JvmAsyncBridge.fromFuture(value);
    }
}
