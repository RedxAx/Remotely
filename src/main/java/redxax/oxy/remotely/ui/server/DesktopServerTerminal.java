package redxax.oxy.remotely.ui.server;

import redxax.oxy.remotely.RemotelyServerApi;
import restudio.rebase.backend.TerminalSessionProvider;
import restudio.rebase.backend.TerminalSize;
import restudio.rebase.restudio.api.models.ServerModels;
import restudio.rebase.ui.widgets.DesktopTerminalEngineProvider;
import restudio.rebase.ui.widgets.TerminalEngine;
import restudio.rebase.ui.widgets.TerminalEngineProvider;
import restudio.rescreen.render.TextRenderer;
import restudio.rescreen.text.FontRegistry;

public final class DesktopServerTerminal extends ServerTerminal {
    private static final TerminalEngineProvider ENGINE_PROVIDER = new DesktopTerminalEngineProvider();

    public DesktopServerTerminal(ServerScreenHost host, RemotelyServerApi api, ServerModels.ClientServerView server,
                                 int x, int y, int width, int height, TerminalSessionProvider provider) {
        super(host, api, server, x, y, width, height, provider, createEngine(width, height));
    }

    public static synchronized DesktopServerTerminal getOrCreate(String id, ServerScreenHost host,
                                                                  RemotelyServerApi api,
                                                                  ServerModels.ClientServerView server,
                                                                  int x, int y, int width, int height,
                                                                  TerminalSessionProvider provider) {
        String cacheId = id == null || id.isBlank() ? serverId(server) : id;
        if (cacheId.isBlank()) return new DesktopServerTerminal(host, api, server, x, y, width, height, provider);
        ServerTerminal existing = cached(cacheId);
        if (existing instanceof DesktopServerTerminal desktop) return desktop;
        if (existing != null) {
            uncached(cacheId, existing);
            existing.shutdown();
        }
        DesktopServerTerminal created = new DesktopServerTerminal(host, api, server, x, y, width, height, provider);
        created.cacheId = cacheId;
        cache(cacheId, created);
        return created;
    }

    public static void shutdown(String id) {
        ServerTerminal.shutdown(id);
    }

    public static void shutdownAll() {
        ServerTerminal.shutdownAll();
    }

    private static TerminalEngine createEngine(int width, int height) {
        int charWidth = FontRegistry.MONO_FONT != null && TextRenderer.tr.supportsFont(FontRegistry.MONO_FONT)
                ? TextRenderer.tr.getWidth("W", FontRegistry.MONO_FONT) : TextRenderer.tr.getWidth("R");
        int lineHeight = Math.max(1, TextRenderer.tr.fontHeight + 2);
        TerminalSize size = new TerminalSize(Math.max(1, (width - 4) / Math.max(1, charWidth)),
                Math.max(1, (height - 4) / lineHeight));
        return ENGINE_PROVIDER.create(size, ignored -> {
        });
    }
}
