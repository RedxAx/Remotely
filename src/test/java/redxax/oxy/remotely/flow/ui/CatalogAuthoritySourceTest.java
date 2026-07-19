package redxax.oxy.remotely.flow.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogAuthoritySourceTest {
    @Test
    void customContentEditorsUseTheContextualServerCatalog() throws Exception {
        for (Path path : List.of(
            source("ContentDesignerScreen.java"),
            Path.of("src", "main", "java", "redxax", "oxy", "remotely", "flow", "ui", "studio", "ReSyncContentBrowserWidget.java")
        )) {
            String code = Files.readString(path);
            assertTrue(code.contains("server:custom_content:asset"), path.toString());
            assertFalse(code.contains("server:custom_content:nexo_"), path.toString());
            assertFalse(code.contains("PackContentRegistry"), path.toString());
        }
    }

    @Test
    void nodeEditorDoesNotSynthesizeServerMinecraftCatalogs() throws Exception {
        String code = Files.readString(source("NodeWidget.java"));

        assertFalse(code.contains("MinecraftAssetsManager"));
        assertFalse(code.contains("fallbackSoundOptions"));
        assertFalse(code.contains("minecraftParticleOptions"));
        assertFalse(code.contains("minecraftBlockOptions"));
        assertFalse(code.contains("server:custom_content:nexo_"));
        assertTrue(code.contains("refreshDependentCatalogs"));
        assertTrue(code.contains("metadata.getContextKeys().contains(contextKey)"));
        assertFalse(code.contains("isCustomContentProviderInput"));
        assertTrue(code.contains("parameter.getTypeRef()"));
        assertTrue(code.contains("meta.getWidgetType()"));
        assertTrue(code.contains("return NodeDefinition.WidgetType.SEARCHABLE_LIST"));
        assertTrue(code.contains("showNodeInputSelectorAtScreen"));
        assertTrue(code.contains("hasLastScreenMouse ? lastScreenX"));
    }

    @Test
    void customContentSelectorsUseTheInvokingClickX() throws Exception {
        String code = Files.readString(source("ContentDesignerScreen.java"));

        assertTrue(code.contains("ScreenManager.getInstance()"));
        assertTrue(code.contains("showStudioSelector(options, selected, selectorX, selectorY"));
        assertTrue(code.contains("StudioDocumentLifecycleScreen, StudioSelectorView"));
        assertTrue(code.contains("handleActiveStudioSelectorMouseClicked(event)"));
        assertTrue(code.contains("handleActiveStudioSelectorMouseScrolled(event)"));
        assertFalse(code.contains("activeSearchSelector"));
        assertFalse(code.contains("renderActiveSearchSelector"));
    }

    @Test
    void customContentFlowBranchSelectorsStayInScreenCoordinates() throws Exception {
        String code = Files.readString(source("NodeWidget.java"));

        assertTrue(code.contains("showNodeInputSelectorAtScreen(options, selected, onSelected, selectorX, selectorY)"));
        assertFalse(code.contains("showNodeInputSelector(options, selected, onSelected, selectorX, selectorY)"));
        assertFalse(code.contains("lastOutputClickX"));
    }

    @Test
    void flowNodeFavoriteFeatureIsRemoved() throws Exception {
        String designer = Files.readString(source("FlowGraphDesignerScreen.java"));
        String preferences = Files.readString(Path.of("src", "main", "java", "redxax", "oxy", "remotely", "flow", "registry", "NodeDiscoveryPreferences.java"));

        assertFalse(designer.contains("Favorite"));
        assertFalse(designer.contains("showNodeDiscoveryMenu"));
        assertFalse(preferences.contains("Favorite"));
        assertFalse(preferences.contains("favorite"));
    }

    @Test
    void advancementFunctionInputsPreserveRichCatalogItems() throws Exception {
        String code = Files.readString(source("AdvancementDesignerScreen.java"));

        assertTrue(code.contains("openCatalogSearchSelector"));
        assertTrue(code.contains("item.getDescription()"));
        assertTrue(code.contains("item.getIcon()"));
        assertTrue(code.contains("item.getGroup()"));
        assertTrue(code.contains("item.getMetadata().get(\"aliases\")"));
        assertTrue(code.contains("catalogLabel(catalogSource"));
    }

    @Test
    void designerFunctionBindingsPreserveRichCatalogItems() throws Exception {
        String support = Files.readString(source("CompactBindingSupport.java"));
        assertTrue(support.contains("functionInputChoices"));
        assertTrue(support.contains("OptionCatalogCache.getInstance().getItems"));
        assertTrue(support.contains("item.getDescription()"));
        assertTrue(support.contains("item.getIcon()"));
        assertTrue(support.contains("item.getGroup()"));
        assertTrue(support.contains("item.getMetadata().get(\"aliases\")"));

        for (String name : List.of(
            "GuiDesignerScreen.java",
            "RecipeDesignerScreen.java",
            "DialogDesignerScreen.java",
            "FocusedJsonResourceDesignerScreen.java",
            "AdvancementDesignerScreen.java"
        )) {
            assertTrue(Files.readString(source(name)).contains("CompactBindingSupport.functionInputChoices"), name);
        }
    }

    @Test
    void itemPreviewsUseContextualAuthoritativeAssets() throws Exception {
        String code = Files.readString(source("ItemIconPreview.java"));

        assertTrue(code.contains("server:custom_content:asset"));
        assertTrue(code.contains("getItemsAcrossContexts"));
        assertTrue(code.contains("item.getMetadata().get(\"provider\")"));
        assertFalse(code.contains("server:custom_content:nexo_"));
    }

    @Test
    void customContentChangesInvalidateTheContextualCatalogAuthority() throws Exception {
        String code = Files.readString(Path.of("src", "main", "java", "redxax", "oxy", "remotely", "data", "flow", "FlowManager.java"));

        assertTrue(code.contains("server:custom_content:asset"));
        assertFalse(code.contains("server:custom_content:nexo_"));
    }

    private Path source(String name) {
        return Path.of("src", "main", "java", "redxax", "oxy", "remotely", "flow", "ui", name);
    }
}
