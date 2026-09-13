package redxax.oxy.remotely.packcontent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemotelyPackContentIntegrationTest {
    @Test
    void resolvesWorkspaceRelativeEditorPathBeforeFindingNexoRoot() {
        Path workspace = Path.of("C:\\servers\\example");

        Path file = RemotelyPackContentIntegration.resolveEditorPath(workspace, "plugins\\Nexo\\glyphs\\test.yml");

        assertEquals(workspace.resolve("plugins").resolve("Nexo").resolve("glyphs").resolve("test.yml"), file);
        assertEquals(workspace.resolve("plugins").resolve("Nexo"), RemotelyPackContentIntegration.contentRootFor(workspace, file));
    }

    @Test
    void preservesAbsoluteEditorPath() {
        Path workspace = Path.of("C:\\servers\\example");
        Path file = Path.of("D:\\shared\\plugins\\Nexo\\glyphs\\icons.yml");

        assertEquals(file, RemotelyPackContentIntegration.resolveEditorPath(workspace, file.toString()));
        assertEquals(Path.of("D:\\shared\\plugins\\Nexo"), RemotelyPackContentIntegration.contentRootFor(workspace, file));
    }

    @Test
    void resolvesRelativeEditorPathForUnixBackendsOnDesktop() {
        Path workspace = Path.of("/home/container");

        Path file = RemotelyPackContentIntegration.resolveEditorPath(workspace, "plugins/Nexo/glyphs/test.yml");

        assertEquals(workspace.resolve("plugins").resolve("Nexo").resolve("glyphs").resolve("test.yml"), file);
        assertEquals(workspace.resolve("plugins").resolve("Nexo"), RemotelyPackContentIntegration.contentRootFor(workspace, file));
    }
}
