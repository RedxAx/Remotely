package redxax.oxy.remotely.servers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerFactory {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;

    public static int createServer(String serverName, String serverType, String serverVersion, String serverDirectory, String ramAmount, String aikarsFlags) {
        try {
            Path baseDir = Paths.get(serverDirectory);
            Path serverDir = baseDir.resolve(serverName);
            if (!Files.exists(serverDir)) {
                Files.createDirectories(serverDir);
                System.out.println("Created server directory: " + serverDir.toAbsolutePath());
            } else {
                System.out.println("Server directory already exists: " + serverDir.toAbsolutePath());
            }
            String typeLower = serverType.toLowerCase();
            String versionLower = serverVersion.toLowerCase();
            System.out.println("Attempting to get download URL for type: " + typeLower + ", version: " + versionLower);
            String url = getDownloadURL(typeLower, versionLower);
            if (url == null) {
                System.err.println("Could not determine download URL for server type '" + serverType + "' and version '" + serverVersion + "'.");
                System.err.println("This type/version might not be supported, require manual steps or the version might be invalid.");
                return 3;
            }
            System.out.println("Download URL determined: " + url);
            String fileName = "server.jar";
            boolean isInstaller = false;
            if (typeLower.equals("fabric") || typeLower.equals("quilt") || typeLower.equals("forge") || typeLower.equals("neoforge")) {
                isInstaller = true;
                Matcher installerMatcher = Pattern.compile("([^/]+\\.jar)$").matcher(url);
                if (installerMatcher.find()) {
                    fileName = installerMatcher.group(1);
                } else {
                    fileName = typeLower + "-installer.jar";
                }
                System.out.println("Note: Downloading an installer (" + fileName + ").");
            } else if (typeLower.equals("bungeecord")) {
                fileName = "BungeeCord.jar";
            } else if (typeLower.equals("paper") || typeLower.equals("velocity")) {
                Matcher apiMatcher = Pattern.compile("/downloads/([^/]+)$").matcher(url);
                if (apiMatcher.find()) {
                    fileName = apiMatcher.group(1);
                } else {
                    fileName = typeLower + "-" + versionLower + ".jar";
                }
            } else if (typeLower.equals("vanilla")) {
                Matcher vanillaMatcher = Pattern.compile("/([^/]+)/server.jar$").matcher(url);
                if (vanillaMatcher.find()) {
                    fileName = "vanilla-" + vanillaMatcher.group(1) + ".jar";
                } else {
                    fileName = "vanilla-" + versionLower + ".jar";
                }
            }
            Path jarPath = serverDir.resolve(fileName);
            System.out.println("Starting download to: " + jarPath.toAbsolutePath());
            int downloadCode = downloadServerBuild(url, jarPath);
            if (downloadCode != 0) {
                System.err.println("Download failed with code: " + downloadCode);
                return downloadCode;
            }
            if (isInstaller) {
                if (typeLower.equals("fabric") || typeLower.equals("quilt")) {
                    int setupCode = setupFabricServer(serverDir, jarPath, versionLower);
                    if (setupCode != 0) {
                        System.err.println("Failed to setup Fabric server with code: " + setupCode);
                        return setupCode;
                    }
                } else if (typeLower.equals("forge") || typeLower.equals("neoforge")) {
                    int setupCode = setupForgeNeoForgeServer(serverDir, jarPath);
                    if (setupCode != 0) {
                        System.err.println("Failed to setup " + serverType + " server with code: " + setupCode);
                        return setupCode;
                    }
                } else {
                    System.out.println("Skipping start script generation for installer (" + serverType + ").");
                }
            } else {
                int scriptCode = createStartScript(serverDir, fileName, ramAmount, aikarsFlags);
                if (scriptCode != 0) {
                    System.err.println("Failed to create start script with code: " + scriptCode);
                    return scriptCode;
                }
            }
            System.out.println("Server '" + serverName + "' setup process initiated successfully in " + serverDir.toAbsolutePath());
            Path eulaPath = serverDir.resolve("eula.txt");
            if (Files.exists(eulaPath)) {
                try {
                    List<String> lines = Files.readAllLines(eulaPath);
                    List<String> updatedLines = new ArrayList<>();
                    boolean accepted = false;
                    for (String line : lines) {
                        if (line.trim().startsWith("eula=")) {
                            updatedLines.add("eula=true");
                            accepted = true;
                        } else {
                            updatedLines.add(line);
                        }
                    }
                    if (!accepted) {
                        updatedLines.add("eula=true");
                    }
                    Files.write(eulaPath, updatedLines, StandardCharsets.UTF_8);
                    System.out.println("Accepted EULA in " + eulaPath.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Failed to accept EULA: " + e.getMessage());
                }
            }
            return 0;
        } catch (IOException e) {
            System.err.println("IO Error during server creation: " + e.getMessage());
            e.printStackTrace();
            return 98;
        } catch (Exception e) {
            System.err.println("Unexpected Error during server creation: " + e.getMessage());
            e.printStackTrace();
            return 99;
        }
    }

    public static int createServer(String serverName, String serverType, String serverVersion, String serverDirectory) {
        return createServer(serverName, serverType, serverVersion, serverDirectory, "2048", "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+UseStringDeduplication");
    }

    private static String getDownloadURL(String serverType, String serverVersion) {
        try {
            switch (serverType) {
                case "vanilla":
                    return getVanillaDownloadUrl(serverVersion);
                case "paper":
                    return getPaperDownloadUrl("paper", serverVersion);
                case "velocity":
                    return getPaperDownloadUrl("velocity", serverVersion);
                case "spigot":
                    System.err.println("Spigot server jars must be built using BuildTools.");
                    System.err.println("See: https://www.spigotmc.org/wiki/buildtools/");
                    return null;
                case "fabric":
                    System.out.println("Note: Fabric requires running the downloaded installer.");
                    String fabricMetaUrl = "https://meta.fabricmc.net/v2/versions/installer";
                    String fabricMetaResponse = simpleHttpGet(fabricMetaUrl);
                    if (fabricMetaResponse != null) {
                        Pattern fabricPattern = Pattern.compile("\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\",\\s*\"maven\"\\s*:\\s*\"[^\"]+\",\\s*\"version\"\\s*:\\s*\"[^\"]+\",\\s*\"stable\"\\s*:\\s*true\\s*\\}");
                        Matcher fabricMatcher = fabricPattern.matcher(fabricMetaResponse);
                        if (fabricMatcher.find()) {
                            return fabricMatcher.group(1);
                        } else {
                            System.err.println("Could not find stable installer URL in Fabric Meta API response.");
                            return null;
                        }
                    } else {
                        System.err.println("Failed to fetch Fabric Meta API.");
                        return null;
                    }
                case "forge":
                    System.out.println("Note: Downloading Forge installer.");
                    System.err.println("Automated download for Forge installers is complex. Please manually download the installer for Minecraft version " + serverVersion + " from https://files.minecraftforge.net/net/minecraftforge/forge/");
                    return null;
                case "neoforge":
                    System.out.println("Note: Downloading NeoForge installer.");
                    System.err.println("Automated download for NeoForge installers is complex. Please manually download the installer for Minecraft version " + serverVersion + " from https://neoforged.net/files");
                    return null;
                case "quilt":
                    System.out.println("Note: Quilt requires running the downloaded installer.");
                    String quiltMetaUrl = "https://meta.quiltmc.org/v3/versions/installer";
                    String quiltMetaResponse = simpleHttpGet(quiltMetaUrl);
                    if (quiltMetaResponse != null) {
                        Pattern quiltPattern = Pattern.compile("\\{\\s*\"version\"\\s*:\\s*\"[^\"]+\",\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher quiltMatcher = quiltPattern.matcher(quiltMetaResponse);
                        if (quiltMatcher.find()) {
                            return quiltMatcher.group(1);
                        } else {
                            System.err.println("Could not parse installer URL from Quilt Meta API response.");
                            return null;
                        }
                    } else {
                        System.err.println("Failed to fetch Quilt Meta API.");
                        return null;
                    }
                case "bungeecord":
                    System.out.println("Warning: Using BungeeCord CI build. Consider using Waterfall for more stability.");
                    return "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar";
                default:
                    System.err.println("Unknown server type: " + serverType);
                    return null;
            }
        } catch (IOException e) {
            System.err.println("IOException while determining download URL: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error while determining download URL: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String getPaperDownloadUrl(String project, String serverVersion) throws IOException {
        String buildsApiUrl = "https://api.papermc.io/v2/projects/" + project + "/versions/" + serverVersion + "/builds";
        System.out.println("Fetching builds from PaperMC API: " + buildsApiUrl);
        String buildsResponse = simpleHttpGet(buildsApiUrl);
        if (buildsResponse == null) {
            System.err.println("Failed to get builds from PaperMC API for " + project + " version: " + serverVersion);
            return null;
        }
        Pattern buildPattern = Pattern.compile("\"build\"\\s*:\\s*(\\d+)");
        Matcher buildMatcher = buildPattern.matcher(buildsResponse);
        int latestBuild = -1;
        while (buildMatcher.find()) {
            latestBuild = Math.max(latestBuild, Integer.parseInt(buildMatcher.group(1)));
        }
        if (latestBuild == -1) {
            System.err.println("Could not find any build number in PaperMC API response for " + project + " version: " + serverVersion);
            String versionCheckUrl = "https://api.papermc.io/v2/projects/" + project + "/versions/" + serverVersion;
            if (simpleHttpGet(versionCheckUrl) == null) {
                System.err.println("Version '" + serverVersion + "' does not exist for project '" + project + "'.");
            }
            return null;
        }
        System.out.println("Latest build identified: " + latestBuild);
        Pattern buildObjectPattern = Pattern.compile("\\{\\s*\"build\"\\s*:\\s*" + latestBuild + ",.*?\"downloads\"\\s*:\\s*\\{\\s*\"application\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher buildObjectMatcher = buildObjectPattern.matcher(buildsResponse);
        String downloadFileName = null;
        if (buildObjectMatcher.find()) {
            downloadFileName = buildObjectMatcher.group(1);
        }
        if (downloadFileName == null) {
            System.err.println("Could not extract download file name for build " + latestBuild + " from PaperMC API response.");
            downloadFileName = project + "-" + serverVersion + "-" + latestBuild + ".jar";
            System.err.println("Attempting fallback filename: " + downloadFileName);
        }
        return "https://api.papermc.io/v2/projects/" + project + "/versions/" + serverVersion + "/builds/" + latestBuild + "/downloads/" + downloadFileName;
    }

    private static String getVanillaDownloadUrl(String serverVersion) throws IOException {
        String manifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
        System.out.println("Fetching Vanilla version manifest...");
        String manifestJson = simpleHttpGet(manifestUrl);
        if (manifestJson == null) {
            System.err.println("Failed to fetch Vanilla version manifest.");
            return null;
        }
        String versionToFind = serverVersion;
        if (serverVersion.equalsIgnoreCase("latest")) {
            Pattern latestPattern = Pattern.compile("\"latest\"\\s*:\\s*\\{\\s*\"release\"\\s*:\\s*\"([^\"]+)\"");
            Matcher latestMatcher = latestPattern.matcher(manifestJson);
            if (latestMatcher.find()) {
                versionToFind = latestMatcher.group(1);
            } else {
                System.err.println("Could not determine latest release version from manifest.");
                return null;
            }
            System.out.println("Latest release version identified as: " + versionToFind);
        }
        Pattern versionPattern = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"" + Pattern.quote(versionToFind) + "\"\\s*,\\s*\"type\"\\s*:\\s*\"(release|snapshot)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
        Matcher versionMatcher = versionPattern.matcher(manifestJson);
        if (!versionMatcher.find()) {
            System.err.println("Could not find manifest URL for version: " + versionToFind + " in the main manifest.");
            return null;
        }
        String specificVersionManifestUrl = versionMatcher.group(2);
        System.out.println("Fetching manifest for version " + versionToFind + " from " + specificVersionManifestUrl);
        String specificManifestJson = simpleHttpGet(specificVersionManifestUrl);
        if (specificManifestJson == null) {
            System.err.println("Failed to fetch manifest for version: " + versionToFind);
            return null;
        }
        Pattern serverUrlPattern = Pattern.compile("\"downloads\"\\s*:\\s*\\{\\s*.*?\"server\"\\s*:\\s*\\{\\s*.*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher serverUrlMatcher = serverUrlPattern.matcher(specificManifestJson);
        if (serverUrlMatcher.find()) {
            return serverUrlMatcher.group(1);
        } else {
            System.err.println("Could not find server download URL in manifest for version: " + versionToFind);
            if (!specificManifestJson.contains("\"server\"")) {
                System.err.println("The manifest for " + versionToFind + " does not seem to contain a server download entry.");
            }
            return null;
        }
    }

    private static String extractJsonValue(String json, String key) {
        return extractJsonValue(json, key, false);
    }

    private static String extractJsonValue(String json, String key, boolean fullBlock) {
        if (json == null || key == null)
            return null;
        Pattern pattern;
        if (fullBlock) {
            pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\{.*?\\}|\\[.*?\\])", Pattern.DOTALL);
        } else {
            pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([^\"\\{\\[,]+)\"?");
        }
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static String simpleHttpGet(String urlString) throws IOException {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "RedxaxOxyServerFactory/1.0");
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            int redirects = 0;
            while (responseCode >= 300 && responseCode < 400 && redirects < 5) {
                String newUrl = connection.getHeaderField("Location");
                System.out.println("Redirecting to: " + newUrl);
                connection.disconnect();
                url = new URL(newUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", "RedxaxOxyServerFactory/1.0");
                connection.setInstanceFollowRedirects(true);
                responseCode = connection.getResponseCode();
                redirects++;
            }
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append(System.lineSeparator());
                }
                return response.toString();
            } else {
                System.err.println("HTTP GET request failed for " + urlString + " with response code: " + responseCode);
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        String line;
                        System.err.println("Error response:");
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println(line);
                        }
                    } catch (Exception e) {
                        System.err.println("Exception reading error stream: " + e.getMessage());
                    }
                } else {
                    System.err.println("No error stream available.");
                }
                return null;
            }
        } finally {
            if (reader != null)
                try { reader.close(); } catch (IOException e) {}
            if (inputStream != null)
                try { inputStream.close(); } catch (IOException e) {}
            if (connection != null)
                connection.disconnect();
        }
    }

    private static int downloadServerBuild(String fileURL, Path destination) {
        HttpURLConnection connection = null;
        InputStream in = null;
        try {
            URL url = new URL(fileURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "RedxaxOxyServerFactory/1.0");
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.err.println("Server returned HTTP " + responseCode + " for final URL: " + connection.getURL());
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        String line;
                        System.err.println("Error response:");
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println(line);
                        }
                    } catch (Exception e) {
                        System.err.println("Exception reading error stream: " + e.getMessage());
                    }
                } else {
                    System.err.println("No error stream available.");
                }
                return 1;
            }
            long fileSize = connection.getContentLengthLong();
            System.out.println("Downloading " + (fileSize > 0 ? (fileSize / 1024 / 1024) + " MB" : "Unknown size") + " from " + connection.getURL() + " ...");
            in = connection.getInputStream();
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Download complete: " + destination.getFileName());
            return 0;
        } catch (IOException e) {
            System.err.println("Error downloading server build from " + fileURL + ": " + e.getMessage());
            e.printStackTrace();
            return 1;
        } finally {
            if (in != null)
                try { in.close(); } catch (IOException e) {}
            if (connection != null)
                connection.disconnect();
        }
    }

    private static int createStartScript(Path serverDir, String jarName, String ramAmount, String aikarsFlags) {
        try {
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            String ramDigits = ramAmount.replaceAll("[^0-9]", "");
            if (ramDigits.isEmpty()) {
                System.err.println("Invalid RAM amount specified: " + ramAmount + ". Using default 1024M.");
                ramDigits = "1024";
            }
            String memSettings = "-Xms" + ramDigits + "M -Xmx" + ramDigits + "M";
            String flags = (aikarsFlags != null && !aikarsFlags.trim().isEmpty()) ? " " + aikarsFlags.trim() : "";
            String safeJarName = jarName.contains(" ") ? "\"" + jarName + "\"" : jarName;
            String javaCommand = "java " + memSettings + flags + " -jar " + safeJarName + " nogui";
            if (windows) {
                Path batFile = serverDir.resolve("start.bat");
                String batContent = "@echo off\n" + javaCommand + "\npause";
                Files.writeString(batFile, batContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Created Windows start script: " + batFile.getFileName());
            } else {
                Path shFile = serverDir.resolve("start.sh");
                String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
                Files.write(shFile, shContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = new java.util.HashSet<>();
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(shFile, perms);
                } catch (UnsupportedOperationException | IOException | SecurityException e) {
                    if (!shFile.toFile().setExecutable(true, false)) {
                        System.err.println("Warning: Could not set start.sh as executable using fallback. You may need to run 'chmod +x start.sh' manually.");
                    } else {
                        System.out.println("Set start.sh as executable using fallback method.");
                    }
                    if (!(e instanceof UnsupportedOperationException)) {
                        System.err.println("Warning: Could not set POSIX permissions for start.sh: " + e.getMessage());
                    }
                }
                System.out.println("Created Linux/macOS start script: " + shFile.getFileName());
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Error creating start script: " + e.getMessage());
            e.printStackTrace();
            return 2;
        }
    }

    private static int runJavaProcess(Path workingDirectory, Path jarPath, List<String> args) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-jar");
            command.add(jarPath.toAbsolutePath().toString());
            command.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDirectory.toFile());
            pb.redirectErrorStream(true);
            System.out.println("Running command: " + String.join(" ", command) + " in directory: " + workingDirectory.toAbsolutePath());
            process = pb.start();
            Process finalProcess = process;
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Installer Output] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("[Installer Output] Error reading process output: " + e.getMessage());
                }
            }).start();
            int exitCode = process.waitFor();
            System.out.println("Installer process finished with exit code: " + exitCode);
            return exitCode;
        } catch (IOException e) {
            System.err.println("Error running installer process: " + e.getMessage());
            e.printStackTrace();
            return 100;
        } catch (InterruptedException e) {
            System.err.println("Installer process was interrupted: " + e.getMessage());
            e.printStackTrace();
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return 101;
        }
    }

    private static int setupFabricServer(Path serverDir, Path installerJar, String mcVersion) {
        List<String> args = new ArrayList<>();
        args.add("server");
        args.add("-mcversion");
        args.add(mcVersion);
        args.add("-dir");
        args.add(".");
        args.add("-installDeps");
        args.add("--no-gui");
        int exitCode = runJavaProcess(serverDir, installerJar, args);
        if (exitCode == 0) {
            System.out.println("Fabric installation completed successfully.");
            Path launchJar = serverDir.resolve("fabric-server-launch.jar");
            if (!Files.exists(launchJar)) {
                try {
                    launchJar = Files.list(serverDir)
                            .filter(p -> p.getFileName().toString().toLowerCase().contains("fabric-server"))
                            .max(Comparator.comparingLong(p -> p.toFile().length()))
                            .orElse(null);
                } catch (IOException e) {
                    System.err.println("Error finding Fabric server jar: " + e.getMessage());
                    return 102;
                }
            }
            if (launchJar != null) {
                int scriptCode = createStartScript(serverDir, launchJar.getFileName().toString(), "2048", "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+UseStringDeduplication");
                if (scriptCode != 0) {
                    System.err.println("Failed to create start script for Fabric server with code: " + scriptCode);
                    return scriptCode;
                }
            } else {
                System.err.println("Could not locate Fabric server launch jar after installation.");
                return 103;
            }
            try {
                Files.deleteIfExists(installerJar);
                System.out.println("Deleted Fabric installer jar: " + installerJar.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to delete Fabric installer jar: " + e.getMessage());
            }
        } else {
            System.err.println("Fabric installation failed.");
            return exitCode;
        }
        return 0;
    }

    private static int setupForgeNeoForgeServer(Path serverDir, Path installerJar) {
        List<String> args = new ArrayList<>();
        args.add("--installServer");
        int exitCode = runJavaProcess(serverDir, installerJar, args);
        if (exitCode == 0) {
            System.out.println("Forge/NeoForge installation completed successfully.");
            Path serverJar = null;
            try {
                serverJar = Files.list(serverDir)
                        .filter(p -> p.toString().endsWith(".jar") && !p.getFileName().toString().equals(installerJar.getFileName().toString()))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            } catch (IOException e) {
                System.err.println("Error finding Forge/NeoForge server jar: " + e.getMessage());
                return 102;
            }
            if (serverJar != null) {
                int scriptCode = createStartScript(serverDir, serverJar.getFileName().toString(), "2048", "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+UseStringDeduplication");
                if (scriptCode != 0) {
                    System.err.println("Failed to create start script for Forge/NeoForge server with code: " + scriptCode);
                    return scriptCode;
                }
            } else {
                System.err.println("Could not locate Forge/NeoForge server jar after installation.");
                return 103;
            }
            try {
                Files.deleteIfExists(installerJar);
                System.out.println("Deleted Forge/NeoForge installer jar: " + installerJar.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to delete Forge/NeoForge installer jar: " + e.getMessage());
            }
        } else {
            System.err.println("Forge/NeoForge installation failed.");
            return exitCode;
        }
        return 0;
    }
}
