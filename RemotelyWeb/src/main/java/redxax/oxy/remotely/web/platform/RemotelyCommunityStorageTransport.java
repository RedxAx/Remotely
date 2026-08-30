package redxax.oxy.remotely.web.platform;

import org.teavm.jso.JSBody;
import restudio.rebase.restudio.community.ReStudioCommunityStorageTransport;

final class RemotelyCommunityStorageTransport implements ReStudioCommunityStorageTransport {
    @Override
    public String read(String key) {
        return readValue(key);
    }

    @Override
    public void write(String key, String value) {
        writeValue(key, value);
    }

    @Override
    public void remove(String key) {
        removeValue(key);
    }

    @Override
    public String currentLocation() {
        return location();
    }

    @Override
    public void restoreLocation(String location) {
        restore(location);
    }

    @JSBody(params = {"key"}, script = "try { return sessionStorage.getItem(key) || ''; } catch (error) { return ''; }")
    private static native String readValue(String key);

    @JSBody(params = {"key", "value"}, script = "try { sessionStorage.setItem(key, value || ''); } catch (error) {}")
    private static native void writeValue(String key, String value);

    @JSBody(params = {"key"}, script = "try { sessionStorage.removeItem(key); } catch (error) {}")
    private static native void removeValue(String key);

    @JSBody(script = "try { const current = new URL(window.location.href); return current.pathname + current.search + current.hash; } catch (error) { return '/remotely-web/'; }")
    private static native String location();

    @JSBody(params = {"location"}, script = """
            if (!location || !(location.startsWith('/remotely-web') || location.startsWith('/feedback'))) return;
            try {
                const current = new URL(window.location.href);
                const target = new URL(location, current.origin);
                if (target.pathname !== current.pathname || target.search !== current.search || target.hash !== current.hash) {
                    window.history.replaceState({}, '', target.pathname + target.search + target.hash);
                }
            } catch (error) {}
            """)
    private static native void restore(String location);
}
