package redxax.oxy.common.config;

import redxax.oxy.common.terminal.MultiTerminalScreen;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static redxax.oxy.common.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.common.util.DevUtil.devPrint;

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
        devPrint("Applying theme with direct field mapping: " + theme.name);

        int foundKeys = 0;

        for (Map.Entry<String, Integer> entry : theme.colors.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            switch (key) {
                case "elementBorderColor" -> { Config.elementBorderColor = value; foundKeys++; }
                case "elementBackgroundColor" -> { Config.elementBackgroundColor = value; foundKeys++; }
                case "accentColor" -> { Config.accentColor = value; foundKeys++; }
                case "accentDarkColor" -> { Config.accentDarkColor = value; foundKeys++; }
                case "elementHoverBackgroundColor" -> { Config.elementHoverBackgroundColor = value; foundKeys++; }
                case "globalTextColor" -> { Config.globalTextColor = value; foundKeys++; }
                case "globalHoverTextColor" -> { Config.globalHoverTextColor = value; foundKeys++; }
                case "dangerAccentColor" -> { Config.dangerAccentColor = value; foundKeys++; }
                case "dangerDarkAccentColor" -> { Config.dangerDarkAccentColor = value; foundKeys++; }
                case "elementHoverBorderColor" -> { Config.elementHoverBorderColor = value; foundKeys++; }
                case "globalOuterBorder" -> { Config.globalOuterBorder = value; foundKeys++; }
                case "backgroundColor" -> { Config.backgroundColor = value; foundKeys++; }
                case "innerBackgroundColor" -> { Config.innerBackgroundColor = value; foundKeys++; }
                case "innerBorderColor" -> { Config.innerBorderColor = value; foundKeys++; }
                case "globalDarkTextColor" -> { Config.globalDarkTextColor = value; foundKeys++; }
                case "ModrinthBorderColor" -> { Config.ModrinthBorderColor = value; foundKeys++; }
                case "ModrinthBackgroundColor" -> { Config.ModrinthBackgroundColor = value; foundKeys++; }
                case "SpigotBorderColor" -> { Config.SpigotBorderColor = value; foundKeys++; }
                case "SpigotBackgroundColor" -> { Config.SpigotBackgroundColor = value; foundKeys++; }
                case "HangarBorderColor" -> { Config.HangarBorderColor = value; foundKeys++; }
                case "HangarBackgroundColor" -> { Config.HangarBackgroundColor = value; foundKeys++; }
                case "niceDarkAccentColor" -> { Config.niceDarkAccentColor = value; foundKeys++; }
                case "niceAccentColor" -> { Config.niceAccentColor = value; foundKeys++; }
                case "terminalStatusBarColor" -> { Config.terminalStatusBarColor = value; foundKeys++; }
                case "terminalTextColor" -> { Config.terminalTextColor = value; foundKeys++; }
                case "terminalTextInputColor" -> { Config.terminalTextInputColor = value; foundKeys++; }
                case "terminalTextWarnColor" -> { Config.terminalTextWarnColor = value; foundKeys++; }
                case "terminalTextErrorColor" -> { Config.terminalTextErrorColor = value; foundKeys++; }
                case "terminalTextInfoColor" -> { Config.terminalTextInfoColor = value; foundKeys++; }
                case "globalSelectionColor" -> { Config.globalSelectionColor = value; foundKeys++; }
                case "dangerLightAccentColor" -> { Config.dangerLightAccentColor = value; foundKeys++; }
                case "globalCursorColor" -> { Config.globalCursorColor = value; foundKeys++; }
                case "innerBackgroundSelectedColor" -> { Config.innerBackgroundSelectedColor = value; foundKeys++; }
                case "calmDarkAccentColor" -> { Config.calmDarkAccentColor = value; foundKeys++; }
                case "calmAccentColor" -> { Config.calmAccentColor = value; foundKeys++; }
            }
        }

        if (foundKeys < 20) {
            devPrint("Insufficient Color Mappings Found (Only " + foundKeys + " Were Found), Falling Back To Old Theme Mapping...");
            oldApplyTheme(theme);
        }

    }

    public static void oldApplyTheme(MultiTerminalScreen.Theme theme) {
        devPrint("Applying Theme: " + theme.name);
        for (Map.Entry<String, Integer> entry : theme.colors.entrySet()) {
            switch (entry.getKey()) {
                case "tabBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "tabBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "tabSelectedBorderColor" -> Config.accentColor = entry.getValue();
                case "tabSelectedBackgroundColor" -> Config.accentDarkColor = entry.getValue();
                case "tabBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "tabTextColor" -> Config.globalTextColor = entry.getValue();
                case "tabTextHoverColor" -> Config.globalHoverTextColor = entry.getValue();
                case "tabUnsavedBackgroundColor" -> Config.dangerAccentColor = entry.getValue();
                case "tabUnsavedBorderColor" -> Config.dangerDarkAccentColor = entry.getValue();
                case "tabBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "globalBottomBorder" -> Config.globalOuterBorder = entry.getValue();
                case "screensTitleTextColor" -> Config.globalTextColor = entry.getValue();
                case "terminalScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "explorerScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "serverScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "browserScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "editorScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "editorInnerBackgroundColor" -> Config.innerBackgroundColor = entry.getValue();
                case "editorBorderColor" -> Config.innerBorderColor = entry.getValue();
                case "deskScreenBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "deskInnerBackgroundColor" -> Config.innerBackgroundColor = entry.getValue();
                case "deskBorderColor" -> Config.innerBorderColor = entry.getValue();
                case "headerBackgroundColor" -> Config.innerBackgroundColor = entry.getValue();
                case "headerBorderColor" -> Config.innerBorderColor = entry.getValue();
                case "buttonBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "buttonBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "buttonTextColor" -> Config.globalTextColor = entry.getValue();
                case "buttonTextHoverColor" -> Config.globalHoverTextColor = entry.getValue();
                case "buttonBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "buttonBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "snippetPanelBackgroundColor" -> Config.innerBackgroundColor = entry.getValue();
                case "snippetPanelBorderColor" -> Config.innerBorderColor = entry.getValue();
                case "snippetElementBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "snippetElementBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "snippetElementBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "snippetElementBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "snippetElementSelectedBorderColor" -> Config.accentColor = entry.getValue();
                case "snippetElementSelectedBackgroundColor" -> Config.accentDarkColor = entry.getValue();
                case "snippetElementTextColor" -> Config.globalTextColor = entry.getValue();
                case "snippetElementTextHoverColor" -> Config.globalHoverTextColor = entry.getValue();
                case "snippetElementTextDimColor" -> Config.globalDarkTextColor = entry.getValue();
                case "ModrinthBorderColor" -> Config.ModrinthBorderColor = entry.getValue();
                case "ModrinthBackgroundColor" -> Config.ModrinthBackgroundColor = entry.getValue();
                case "SpigotBorderColor" -> Config.SpigotBorderColor = entry.getValue();
                case "SpigotBackgroundColor" -> Config.SpigotBackgroundColor = entry.getValue();
                case "HangarBorderColor" -> Config.HangarBorderColor = entry.getValue();
                case "HangarBackgroundColor" -> Config.HangarBackgroundColor = entry.getValue();
                case "explorerElementBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "explorerElementBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "explorerElementBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "explorerElementBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "explorerElementSelectedBorderColor" -> Config.accentColor = entry.getValue();
                case "explorerElementSelectedBackgroundColor" -> Config.accentDarkColor = entry.getValue();
                case "explorerElementFavoriteBackgroundColor" -> Config.niceDarkAccentColor = entry.getValue();
                case "explorerElementFavoriteSelectedBorderColor" -> Config.niceAccentColor = entry.getValue();
                case "explorerElementFavoriteBorderColor" -> Config.niceAccentColor = entry.getValue();
                case "explorerElementTextColor" -> Config.globalTextColor = entry.getValue();
                case "explorerElementTextDimColor" -> Config.globalDarkTextColor = entry.getValue();
                case "explorerElementTextHoverColor" -> Config.globalHoverTextColor = entry.getValue();
                case "serverElementBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "serverElementBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "serverElementBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "serverElementBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "serverElementSelectedBorderColor" -> Config.accentColor = entry.getValue();
                case "serverElementSelectedBackgroundColor" -> Config.accentDarkColor = entry.getValue();
                case "serverElementTextColor" -> Config.globalTextColor = entry.getValue();
                case "serverElementTextDimColor" -> Config.globalDarkTextColor = entry.getValue();
                case "serverElementTextHoverColor" -> Config.globalHoverTextColor = entry.getValue();
                case "browserElementBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "browserElementBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "browserElementBorderHoverColor" -> Config.elementHoverBorderColor = entry.getValue();
                case "browserElementBackgroundHoverColor" -> Config.elementHoverBackgroundColor = entry.getValue();
                case "browserElementTextColor" -> Config.globalTextColor = entry.getValue();
                case "browserElementTextDimColor" -> Config.globalDarkTextColor = entry.getValue();
                case "searchBarBackgroundColor" -> Config.elementBackgroundColor = entry.getValue();
                case "searchBarBorderColor" -> Config.elementBorderColor = entry.getValue();
                case "searchBarActiveBackgroundColor" -> Config.accentDarkColor = entry.getValue();
                case "searchBarActiveBorderColor" -> Config.accentColor = entry.getValue();
                case "searchBarExplorerActiveBackgroundColor" -> Config.niceDarkAccentColor = entry.getValue();
                case "searchBarExplorerActiveBorderColor" -> Config.niceAccentColor = entry.getValue();
                case "airBarBackgroundColor" -> Config.calmDarkAccentColor = entry.getValue();
                case "airBarBorderColor" -> Config.calmAccentColor = entry.getValue();
                case "terminalBackgroundColor" -> Config.backgroundColor = entry.getValue();
                case "terminalBorderColor" -> Config.innerBorderColor = entry.getValue();
                case "terminalStatusBarColor" -> Config.terminalStatusBarColor = entry.getValue();
                case "terminalTextColor" -> Config.terminalTextColor = entry.getValue();
                case "terminalTextInputColor" -> Config.terminalTextInputColor = entry.getValue();
                case "terminalTextSuggesterColor" -> Config.globalDarkTextColor = entry.getValue();
                case "terminalTextWarnColor" -> Config.terminalTextWarnColor = entry.getValue();
                case "terminalTextErrorColor" -> Config.terminalTextErrorColor = entry.getValue();
                case "terminalTextInfoColor" -> Config.terminalTextInfoColor = entry.getValue();
                case "terminalSelectionColor" -> Config.globalSelectionColor = entry.getValue();
                case "buttonTextDeleteColor" -> Config.dangerLightAccentColor = entry.getValue();
                case "buttonTextDeleteHoverColor" -> Config.dangerDarkAccentColor = entry.getValue();
                case "buttonTextCancelColor" -> Config.dangerLightAccentColor = entry.getValue();
                case "cursorColor" -> Config.globalCursorColor = entry.getValue();
                case "popupFieldBackgroundColor" -> Config.innerBackgroundColor = entry.getValue();
                case "popupFieldSelectedBackgroundColor" -> Config.innerBackgroundSelectedColor = entry.getValue();
                default -> devPrint("Unknown Theme Key: " + entry.getKey());
            }
        }
        convertOldToNewTheme(theme);
    }

    public static void convertOldToNewTheme(MultiTerminalScreen.Theme oldTheme) {
        devPrint("Converting Old Theme Format To New Format: " + oldTheme.name);
        MultiTerminalScreen.Theme newTheme = new MultiTerminalScreen.Theme();
        newTheme.name = oldTheme.name;
        newTheme.colors = new java.util.HashMap<>();
       Set<String> processedKeys = new HashSet<>();
        for (Map.Entry<String, Integer> entry : oldTheme.colors.entrySet()) {
            String oldKey = entry.getKey();
            Integer value = entry.getValue();

            String newKey = switch (oldKey) {
                case "tabBorderColor", "buttonBorderColor", "snippetElementBorderColor", "explorerElementBorderColor", "serverElementBorderColor", "browserElementBorderColor" -> "elementBorderColor";
                case "tabBackgroundColor", "buttonBackgroundColor", "snippetElementBackgroundColor", "explorerElementBackgroundColor", "serverElementBackgroundColor", "browserElementBackgroundColor" -> "elementBackgroundColor";
                case "tabSelectedBorderColor", "snippetElementSelectedBorderColor", "explorerElementSelectedBorderColor", "serverElementSelectedBorderColor", "searchBarActiveBorderColor" -> "accentColor";
                case "tabSelectedBackgroundColor", "snippetElementSelectedBackgroundColor", "explorerElementSelectedBackgroundColor", "serverElementSelectedBackgroundColor", "searchBarActiveBackgroundColor" -> "accentDarkColor";
                case "tabBackgroundHoverColor", "buttonBackgroundHoverColor", "snippetElementBackgroundHoverColor", "explorerElementBackgroundHoverColor", "serverElementBackgroundHoverColor", "browserElementBackgroundHoverColor" -> "elementHoverBackgroundColor";
                case "tabTextColor", "buttonTextColor", "snippetElementTextColor", "explorerElementTextColor", "serverElementTextColor", "browserElementTextColor", "screensTitleTextColor" -> "globalTextColor";
                case "tabTextHoverColor", "buttonTextHoverColor", "snippetElementTextHoverColor", "explorerElementTextHoverColor", "serverElementTextHoverColor" -> "globalHoverTextColor";
                case "snippetElementTextDimColor", "explorerElementTextDimColor", "serverElementTextDimColor", "terminalTextSuggesterColor" -> "globalDarkTextColor";
                case "tabUnsavedBackgroundColor", "buttonTextCancelColor" -> "dangerAccentColor";
                case "tabUnsavedBorderColor", "buttonTextDeleteHoverColor" -> "dangerDarkAccentColor";
                case "buttonTextDeleteColor" -> "dangerLightAccentColor";
                case "tabBorderHoverColor", "buttonBorderHoverColor", "snippetElementBorderHoverColor", "explorerElementBorderHoverColor", "serverElementBorderHoverColor", "browserElementBorderHoverColor" -> "elementHoverBorderColor";
                case "globalBottomBorder" -> "globalOuterBorder";
                case "terminalScreenBackgroundColor", "explorerScreenBackgroundColor", "serverScreenBackgroundColor", "browserScreenBackgroundColor", "editorScreenBackgroundColor", "deskScreenBackgroundColor" -> "backgroundColor";
                case "editorInnerBackgroundColor", "deskInnerBackgroundColor", "headerBackgroundColor", "snippetPanelBackgroundColor", "popupFieldBackgroundColor" -> "innerBackgroundColor";
                case "editorBorderColor", "deskBorderColor", "headerBorderColor", "snippetPanelBorderColor", "terminalBorderColor" -> "innerBorderColor";
                case "explorerElementFavoriteBackgroundColor" -> "niceDarkAccentColor";
                case "explorerElementFavoriteSelectedBorderColor", "explorerElementFavoriteBorderColor" -> "niceAccentColor";
                case "searchBarExplorerActiveBackgroundColor" -> "niceDarkAccentColor";
                case "searchBarExplorerActiveBorderColor" -> "niceAccentColor";
                case "airBarBackgroundColor" -> "calmDarkAccentColor";
                case "airBarBorderColor" -> "calmAccentColor";
                case "terminalStatusBarColor" -> "terminalStatusBarColor";
                case "terminalTextColor" -> "terminalTextColor";
                case "terminalTextInputColor" -> "terminalTextInputColor";
                case "terminalTextWarnColor" -> "terminalTextWarnColor";
                case "terminalTextErrorColor" -> "terminalTextErrorColor";
                case "terminalTextInfoColor" -> "terminalTextInfoColor";
                case "terminalSelectionColor" -> "globalSelectionColor";
                case "cursorColor" -> "globalCursorColor";
                case "popupFieldSelectedBackgroundColor" -> "innerBackgroundSelectedColor";
                case "ModrinthBorderColor" -> "ModrinthBorderColor";
                case "ModrinthBackgroundColor" -> "ModrinthBackgroundColor";
                case "SpigotBorderColor" -> "SpigotBorderColor";
                case "SpigotBackgroundColor" -> "SpigotBackgroundColor";
                case "HangarBorderColor" -> "HangarBorderColor";
                case "HangarBackgroundColor" -> "HangarBackgroundColor";

                default -> oldKey;
            };
            if (!processedKeys.contains(newKey)) {
                newTheme.colors.put(newKey, value);
                processedKeys.add(newKey);
            }
        }
        try {
            Path existingThemePath = null;
            try (var files = Files.list(THEMES_DIR)) {
                for (Path file : files.toList()) {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".yml")) {
                        try (BufferedReader reader = Files.newBufferedReader(file)) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("name:")) {
                                    String themeName = line.substring("name:".length()).trim();
                                    if (themeName.equals(oldTheme.name)) {
                                        existingThemePath = file;
                                        break;
                                    }
                                }
                                if (line.startsWith("colors:")) break;
                            }
                        }
                    }
                    if (existingThemePath != null) break;
                }
            }

            Path themePath;
            if (existingThemePath != null) {
                themePath = existingThemePath;
                devPrint("Overwriting existing theme file: " + themePath.getFileName());
            } else {
                String fileName = oldTheme.name + ".yml";
                themePath = THEMES_DIR.resolve(fileName);
                devPrint("Creating new theme file: " + themePath.getFileName());
            }

            try (BufferedWriter writer = Files.newBufferedWriter(themePath)) {
                writer.write("name: " + newTheme.name);
                writer.newLine();
                writer.write("colors:");
                writer.newLine();

                for (Map.Entry<String, Integer> entry : newTheme.colors.entrySet()) {
                    String colorHex = String.format("#%08X", entry.getValue());
                    writer.write("  " + entry.getKey() + ": \"" + colorHex + "\"");
                    writer.newLine();
                }

                devPrint("Converted theme saved to: " + themePath.getFileName());
            }
        } catch (IOException e) {
            devPrint("Error writing converted theme to file: " + e.getMessage());
        }
        devPrint("Theme Conversion Complete: " + processedKeys.size() + " Keys Mapped");
    }
}
