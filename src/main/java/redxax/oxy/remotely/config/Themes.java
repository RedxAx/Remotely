package redxax.oxy.remotely.config;

import redxax.oxy.remotely.terminal.MultiTerminalScreen;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import static redxax.oxy.remotely.terminal.MultiTerminalScreen.THEMES_DIR;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

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
        devPrint("Applying theme using reflection: " + theme.name);
        int appliedCount = 0;
        for (Map.Entry<String, Integer> entry : theme.colors.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            try {
                Field field = Config.class.getField(key);
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    field.setInt(null, value);
                    appliedCount++;
                } else {
                    devPrint("Field found but not static int: " + key);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                devPrint("No matching Config field for key: " + key);
            }
        }
        devPrint("Applied " + appliedCount + " color mappings");
    }
}
