package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.util.Notification;

public final class BrowserUpdateMonitor {
    private static boolean notificationShown;

    private BrowserUpdateMonitor() {
    }

    public static void start() {
        notificationShown = false;
        startNative();
    }

    public static void close() {
        notificationShown = false;
        closeNative();
    }

    public static void updated() {
        if (notificationShown) return;
        notificationShown = true;
        ScreenManager.getInstance().execute(() -> new Notification.Builder()
                .message("Remotely-Web Updated")
                .description("Click Here To Refresh")
                .type(Notification.Type.INFO)
                .action(BrowserUpdateMonitor::refresh)
                .autoSlideOut(false)
                .build());
    }

    private static void refresh() {
        refreshNative();
    }

    @JSBody(script = """
            if (window.__remotelyUpdateInterval) window.clearInterval(window.__remotelyUpdateInterval);
            const script = document.getElementById("remotely-bundle");
            if (!script || !script.src) return;
            const buildMeta = document.querySelector('meta[name="remotely-build-id"]');
            const localBuildId = buildMeta && buildMeta.content ? buildMeta.content.trim() : "";
            const markerUrl = new URL("../remotely-web-build-id.txt", script.src).toString();
            let signature = "";
            let stopped = false;
            const readSignature = function(response) {
                return response.headers.get("etag") || response.headers.get("last-modified") || response.headers.get("content-length") || "";
            };
            const notify = function() {
                if (stopped) return;
                stopped = true;
                window.clearInterval(window.__remotelyUpdateInterval);
                window.__remotelyUpdateInterval = 0;
                javaMethods.get("redxax.oxy.remotely.web.platform.BrowserUpdateMonitor.updated()V").invoke();
            };
            const readMarker = function() {
                return fetch(markerUrl, {cache: "no-store", credentials: "same-origin"}).then(function(response) {
                    if (!response.ok) return "";
                    return response.text().then(function(value) { return value.trim(); });
                }).catch(function() { return ""; });
            };
            const check = function() {
                return Promise.all([
                    fetch(script.src, {method: "HEAD", cache: "no-store", credentials: "same-origin"}).then(function(response) {
                        return response.ok ? readSignature(response) : "";
                    }).catch(function() { return ""; }),
                    readMarker()
                ]).then(function(values) {
                    const current = values[0];
                    const remoteBuildId = values[1];
                    if (remoteBuildId && localBuildId !== remoteBuildId) {
                        notify();
                        return;
                    }
                    if (!signature) {
                        signature = current;
                        return;
                    }
                    if (current && current !== signature) {
                        notify();
                    }
                }).catch(function() {});
            };
            check().finally(function() {
                if (!stopped) window.__remotelyUpdateInterval = window.setInterval(check, 30000);
            });
            """)
    private static native void startNative();

    @JSBody(script = """
            if (window.__remotelyUpdateInterval) window.clearInterval(window.__remotelyUpdateInterval);
            window.__remotelyUpdateInterval = 0;
            """)
    private static native void closeNative();

    @JSBody(script = """
            const reload = function() {
                const target = new URL(window.location.href);
                target.searchParams.set("_remotelyRefresh", String(Date.now()));
                window.location.replace(target.toString());
            };
            if (!window.caches) {
                reload();
                return;
            }
            window.caches.keys().then(function(keys) {
                return Promise.all(keys.map(function(key) { return window.caches.delete(key); }));
            }).then(reload, reload);
            """)
    private static native void refreshNative();
}
