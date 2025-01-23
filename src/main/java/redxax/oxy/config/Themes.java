package redxax.oxy.config;

import redxax.oxy.terminal.MultiTerminalScreen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static redxax.oxy.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.util.DevUtil.devPrint;

public class Themes {

    public static void importThemesFromJar() {
        try {
            if (!Files.exists(THEMES_DIR)) {
                Files.createDirectories(THEMES_DIR);
            }
            URL dirURL = Themes.class.getClassLoader().getResource("assets/remotely/themes/");
            if (dirURL != null && dirURL.getProtocol().equals("jar")) {
                JarURLConnection jarConn = (JarURLConnection) dirURL.openConnection();
                try (JarFile jar = jarConn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("assets/remotely/themes/") && name.endsWith(".yml") && !entry.isDirectory()) {
                            String fileName = name.substring(name.lastIndexOf("/") + 1);
                            Path outputPath = THEMES_DIR.resolve(fileName);
                            if (!Files.exists(outputPath)) {
                                try (InputStream is = Themes.class.getClassLoader().getResourceAsStream(name);
                                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                                     BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        writer.write(line);
                                        writer.newLine();
                                    }
                                } catch (IOException e) {
                                    devPrint("Failed to import theme file " + fileName + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            } else if (dirURL != null && dirURL.getProtocol().equals("file")) {
                Path themesPath = Path.of(dirURL.toURI());
                Files.list(themesPath).filter(path -> path.toString().endsWith(".yml")).forEach(path -> {
                    Path outputPath = THEMES_DIR.resolve(path.getFileName());
                    if (!Files.exists(outputPath)) {
                        try {
                            Files.copy(path, outputPath);
                        } catch (IOException e) {
                            devPrint("Failed to copy theme file " + path.getFileName() + ": " + e.getMessage());
                        }
                    }
                });
            } else {
                devPrint("Theme resources not found in jar.");
            }
        } catch (Exception e) {
            devPrint("Error importing themes from jar: " + e.getMessage());
        }
    }

    public static int parseHexColor(String hex) {
        hex = hex.replace("#", "");
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        return (int) Long.parseLong(hex, 16);
    }

    public static void applyTheme(MultiTerminalScreen.Theme theme) {
        devPrint("Applying theme: " + theme.name);
        for (Map.Entry<String, Integer> entry : theme.colors.entrySet()) {
            switch (entry.getKey()) {
                case "tabBorderColor" -> Config.tabBorderColor = entry.getValue();
                case "tabBackgroundColor" -> Config.tabBackgroundColor = entry.getValue();
                case "tabSelectedBorderColor" -> Config.tabSelectedBorderColor = entry.getValue();
                case "tabSelectedBackgroundColor" -> Config.tabSelectedBackgroundColor = entry.getValue();
                case "tabBackgroundHoverColor" -> Config.tabBackgroundHoverColor = entry.getValue();
                case "tabTextColor" -> Config.tabTextColor = entry.getValue();
                case "tabTextHoverColor" -> Config.tabTextHoverColor = entry.getValue();
                case "tabUnsavedBackgroundColor" -> Config.tabUnsavedBackgroundColor = entry.getValue();
                case "tabUnsavedBorderColor" -> Config.tabUnsavedBorderColor = entry.getValue();
                case "tabBorderHoverColor" -> Config.tabBorderHoverColor = entry.getValue();
                case "globalBottomBorder" -> Config.globalBottomBorder = entry.getValue();
                case "screensTitleTextColor" -> Config.screensTitleTextColor = entry.getValue();
                case "terminalScreenBackgroundColor" -> Config.terminalScreenBackgroundColor = entry.getValue();
                case "explorerScreenBackgroundColor" -> Config.explorerScreenBackgroundColor = entry.getValue();
                case "serverScreenBackgroundColor" -> Config.serverScreenBackgroundColor = entry.getValue();
                case "browserScreenBackgroundColor" -> Config.browserScreenBackgroundColor = entry.getValue();
                case "editorScreenBackgroundColor" -> Config.editorScreenBackgroundColor = entry.getValue();
                case "editorInnerBackgroundColor" -> Config.editorInnerBackgroundColor = entry.getValue();
                case "editorBorderColor" -> Config.editorBorderColor = entry.getValue();
                case "deskScreenBackgroundColor" -> Config.deskScreenBackgroundColor = entry.getValue();
                case "deskInnerBackgroundColor" -> Config.deskInnerBackgroundColor = entry.getValue();
                case "deskBorderColor" -> Config.deskBorderColor = entry.getValue();
                case "headerBackgroundColor" -> Config.headerBackgroundColor = entry.getValue();
                case "headerBorderColor" -> Config.headerBorderColor = entry.getValue();
                case "buttonBackgroundColor" -> Config.buttonBackgroundColor = entry.getValue();
                case "buttonBorderColor" -> Config.buttonBorderColor = entry.getValue();
                case "buttonTextColor" -> Config.buttonTextColor = entry.getValue();
                case "buttonTextHoverColor" -> Config.buttonTextHoverColor = entry.getValue();
                case "buttonBorderHoverColor" -> Config.buttonBorderHoverColor = entry.getValue();
                case "buttonBackgroundHoverColor" -> Config.buttonBackgroundHoverColor = entry.getValue();
                case "snippetPanelBackgroundColor" -> Config.snippetPanelBackgroundColor = entry.getValue();
                case "snippetPanelBorderColor" -> Config.snippetPanelBorderColor = entry.getValue();
                case "snippetElementBackgroundColor" -> Config.snippetElementBackgroundColor = entry.getValue();
                case "snippetElementBorderColor" -> Config.snippetElementBorderColor = entry.getValue();
                case "snippetElementBorderHoverColor" -> Config.snippetElementBorderHoverColor = entry.getValue();
                case "snippetElementBackgroundHoverColor" -> Config.snippetElementBackgroundHoverColor = entry.getValue();
                case "snippetElementSelectedBorderColor" -> Config.snippetElementSelectedBorderColor = entry.getValue();
                case "snippetElementSelectedBackgroundColor" -> Config.snippetElementSelectedBackgroundColor = entry.getValue();
                case "snippetElementTextColor" -> Config.snippetElementTextColor = entry.getValue();
                case "snippetElementTextHoverColor" -> Config.snippetElementTextHoverColor = entry.getValue();
                case "snippetElementTextDimColor" -> Config.snippetElementTextDimColor = entry.getValue();
                case "ModrinthBorderColor" -> Config.ModrinthBorderColor = entry.getValue();
                case "ModrinthBackgroundColor" -> Config.ModrinthBackgroundColor = entry.getValue();
                case "SpigotBorderColor" -> Config.SpigotBorderColor = entry.getValue();
                case "SpigotBackgroundColor" -> Config.SpigotBackgroundColor = entry.getValue();
                case "HangarBorderColor" -> Config.HangarBorderColor = entry.getValue();
                case "HangarBackgroundColor" -> Config.HangarBackgroundColor = entry.getValue();
                case "explorerElementBackgroundColor" -> Config.explorerElementBackgroundColor = entry.getValue();
                case "explorerElementBorderColor" -> Config.explorerElementBorderColor = entry.getValue();
                case "explorerElementBorderHoverColor" -> Config.explorerElementBorderHoverColor = entry.getValue();
                case "explorerElementBackgroundHoverColor" -> Config.explorerElementBackgroundHoverColor = entry.getValue();
                case "explorerElementSelectedBorderColor" -> Config.explorerElementSelectedBorderColor = entry.getValue();
                case "explorerElementSelectedBackgroundColor" -> Config.explorerElementSelectedBackgroundColor = entry.getValue();
                case "explorerElementFavoriteBackgroundColor" -> Config.explorerElementFavoriteBackgroundColor = entry.getValue();
                case "explorerElementFavoriteSelectedBorderColor" -> Config.explorerElementFavoriteSelectedBorderColor = entry.getValue();
                case "explorerElementFavoriteBorderColor" -> Config.explorerElementFavoriteBorderColor = entry.getValue();
                case "explorerElementTextColor" -> Config.explorerElementTextColor = entry.getValue();
                case "explorerElementTextDimColor" -> Config.explorerElementTextDimColor = entry.getValue();
                case "explorerElementTextHoverColor" -> Config.explorerElementTextHoverColor = entry.getValue();
                case "serverElementBackgroundColor" -> Config.serverElementBackgroundColor = entry.getValue();
                case "serverElementBorderColor" -> Config.serverElementBorderColor = entry.getValue();
                case "serverElementBorderHoverColor" -> Config.serverElementBorderHoverColor = entry.getValue();
                case "serverElementBackgroundHoverColor" -> Config.serverElementBackgroundHoverColor = entry.getValue();
                case "serverElementSelectedBorderColor" -> Config.serverElementSelectedBorderColor = entry.getValue();
                case "serverElementSelectedBackgroundColor" -> Config.serverElementSelectedBackgroundColor = entry.getValue();
                case "serverElementTextColor" -> Config.serverElementTextColor = entry.getValue();
                case "serverElementTextDimColor" -> Config.serverElementTextDimColor = entry.getValue();
                case "serverElementTextHoverColor" -> Config.serverElementTextHoverColor = entry.getValue();
                case "browserElementBackgroundColor" -> Config.browserElementBackgroundColor = entry.getValue();
                case "browserElementBorderColor" -> Config.browserElementBorderColor = entry.getValue();
                case "browserElementBorderHoverColor" -> Config.browserElementBorderHoverColor = entry.getValue();
                case "browserElementBackgroundHoverColor" -> Config.browserElementBackgroundHoverColor = entry.getValue();
                case "browserElementTextColor" -> Config.browserElementTextColor = entry.getValue();
                case "browserElementTextDimColor" -> Config.browserElementTextDimColor = entry.getValue();
                case "searchBarBackgroundColor" -> Config.searchBarBackgroundColor = entry.getValue();
                case "searchBarBorderColor" -> Config.searchBarBorderColor = entry.getValue();
                case "searchBarActiveBackgroundColor" -> Config.searchBarActiveBackgroundColor = entry.getValue();
                case "searchBarActiveBorderColor" -> Config.searchBarActiveBorderColor = entry.getValue();
                case "searchBarExplorerActiveBackgroundColor" -> Config.searchBarExplorerActiveBackgroundColor = entry.getValue();
                case "searchBarExplorerActiveBorderColor" -> Config.searchBarExplorerActiveBorderColor = entry.getValue();
                case "airBarBackgroundColor" -> Config.airBarBackgroundColor = entry.getValue();
                case "airBarBorderColor" -> Config.airBarBorderColor = entry.getValue();
                case "terminalBackgroundColor" -> Config.terminalBackgroundColor = entry.getValue();
                case "terminalBorderColor" -> Config.terminalBorderColor = entry.getValue();
                case "terminalCursorColor" -> Config.terminalCursorColor = entry.getValue();
                case "terminalStatusBarColor" -> Config.terminalStatusBarColor = entry.getValue();
                case "terminalTextColor" -> Config.terminalTextColor = entry.getValue();
                case "terminalTextInputColor" -> Config.terminalTextInputColor = entry.getValue();
                case "terminalTextSuggesterColor" -> Config.terminalTextSuggesterColor = entry.getValue();
                case "terminalTextWarnColor" -> Config.terminalTextWarnColor = entry.getValue();
                case "terminalTextErrorColor" -> Config.terminalTextErrorColor = entry.getValue();
                case "terminalTextInfoColor" -> Config.terminalTextInfoColor = entry.getValue();
                case "terminalSelectionColor" -> Config.terminalSelectionColor = entry.getValue();
                case "buttonTextDeleteColor" -> Config.buttonTextDeleteColor = entry.getValue();
                case "buttonTextDeleteHoverColor" -> Config.buttonTextDeleteHoverColor = entry.getValue();
                case "buttonTextExplorerColor" -> Config.buttonTextExplorerColor = entry.getValue();
                case "buttonTextExplorerHoverColor" -> Config.buttonTextExplorerHoverColor = entry.getValue();
                case "buttonTextBrowseColor" -> Config.buttonTextBrowseColor = entry.getValue();
                case "buttonTextBrowseHoverColor" -> Config.buttonTextBrowseHoverColor = entry.getValue();
                case "buttonTextStartColor" -> Config.buttonTextStartColor = entry.getValue();
                case "buttonTextStopColor" -> Config.buttonTextStopColor = entry.getValue();
                case "buttonTextCancelColor" -> Config.buttonTextCancelColor = entry.getValue();
                case "cursorColor" -> Config.cursorColor = entry.getValue();
                case "popupFieldBackgroundColor" -> Config.popupFieldBackgroundColor = entry.getValue();
                case "popupFieldSelectedBackgroundColor" -> Config.popupFieldSelectedBackgroundColor = entry.getValue();
                default -> devPrint("Unknown theme key: " + entry.getKey());
            }
        }
    }
}
