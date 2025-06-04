package redxax.oxy.remotely.servers;

import redxax.oxy.remotely.util.Notification;

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
import java.nio.file.attribute.PosixFilePermission;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static redxax.oxy.remotely.config.Config.remotelyDir;
import static redxax.oxy.remotely.util.DevUtil.devPrint;

public class ServerFactory {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    public static Notification notification;

    private static Path getCacheDirectory() throws IOException {
        Path cacheDir = Paths.get(remotelyDir.toString(), "data", "cache", "ServerBuilds");
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
            devPrint("Created server cache directory: " + cacheDir.toAbsolutePath());
        }
        return cacheDir;
    }

    public static void createServerAsync(String serverName, String serverType, String serverVersion, String serverDirectory, String ramAmount, String aikarsFlags, ServerCreationCallback callback) {
        new Thread(() -> {
            int exitCode = createServer(serverName, serverType, serverVersion, serverDirectory, ramAmount, aikarsFlags);
            if (callback != null) {
                callback.onServerCreationComplete(exitCode);
            }
        }, "ServerCreationThread").start();
    }

    public interface ServerCreationCallback {
        void onServerCreationComplete(int exitCode);
    }

    public static int createServer(String serverName, String serverType, String serverVersion, String serverDirectory, String ramAmount, String aikarsFlags) {
        try {
            notification = new Notification("Preparing To Create..", "Buckle Up!", Notification.Type.INFO);
            Path baseDir = Paths.get(serverDirectory);
            Path serverDir = baseDir.resolve(serverName);
            if (!Files.exists(serverDir)) {
                Files.createDirectories(serverDir);
            }
            String downloadURL = getDownloadURL(serverType, serverVersion);
            if (downloadURL == null) {
                return 3;
            }
            String fileName = "server.jar";
            Path jarPath = serverDir.resolve(fileName);
            int downloadCode = downloadServerBuild(downloadURL, jarPath, serverVersion);
            if (downloadCode != 0) {
                return downloadCode;
            }
            int scriptCode = ramAmount.isEmpty() ? 0 : createStartScript(serverDir, fileName, ramAmount, aikarsFlags);
            if (scriptCode != 0) {
                return scriptCode;
            }
            devPrint("Server '" + serverName + "' setup process initiated successfully in " + serverDir.toAbsolutePath());
            ServerManagerScreen.addServer(serverName, serverDir.toString(), serverType, serverVersion, false, null);
            return 0;
        } catch (IOException e) {
            return 98;
        } catch (Exception e) {
            return 99;
        }
    }

    public static String getDownloadURL(String serverType, String serverVersion) {
        try {
            String apiEndpoint = "https://mcjars.app/api/v1/builds/" + serverType.toUpperCase() + "/" + serverVersion + "/latest";
            String jsonResponse = simpleHttpGet(apiEndpoint);
            if (jsonResponse == null) {
                return null;
            }
            Pattern jarUrlPattern = Pattern.compile("\"jarUrl\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = jarUrlPattern.matcher(jsonResponse);
            if (matcher.find() && matcher.group(1) != null && !matcher.group(1).isEmpty()) {
                return matcher.group(1);
            } else {
                Pattern zipUrlPattern = Pattern.compile("\"zipUrl\"\\s*:\\s*\"([^\"]*)\"");
                Matcher zipMatcher = zipUrlPattern.matcher(jsonResponse);
                if (zipMatcher.find() && zipMatcher.group(1) != null && !zipMatcher.group(1).isEmpty()) {
                    return zipMatcher.group(1);
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            devPrint("Error fetching download URL from MCJars API: " + e.getMessage());
            return null;
        }
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
            connection.setRequestProperty("User-Agent", "RemotelyOS/2.0");
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            int redirects = 0;
            while (responseCode >= 300 && responseCode < 400 && redirects < 5) {
                String newUrl = connection.getHeaderField("Location");
                devPrint("Redirecting to: " + newUrl);
                connection.disconnect();
                url = new URL(newUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", "RemotelyOS/2.0");
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
                try { reader.close(); } catch (IOException ignored) {}
            if (inputStream != null)
                try { inputStream.close(); } catch (IOException ignored) {}
            if (connection != null)
                connection.disconnect();
        }
    }

    private static int downloadServerBuild(String fileURL, Path destination, String serverVersion) {
        try {
            String serverType = "unknown";
            Matcher typeMatcher = Pattern.compile("/(\\w+)/").matcher(fileURL);
            if (typeMatcher.find()) {
                serverType = typeMatcher.group(1).toLowerCase();
            }
            String fileName;
            Matcher fileNameMatcher = Pattern.compile("([^/]+\\.(jar|jar\\.zip))$").matcher(fileURL);
            if (fileNameMatcher.find()) {
                fileName = fileNameMatcher.group(1);
            } else {
                fileName = "server.jar";
            }
            Path cacheDir = getCacheDirectory();
            Path cachedFile = cacheDir.resolve(serverType + "_" + serverVersion + "_" + fileName);
            if (Files.exists(cachedFile)) {
                devPrint("Using cached server file: " + cachedFile.getFileName());
                if (fileURL.toLowerCase().endsWith(".zip")) {
                    unzip(cachedFile, destination.getParent());
                } else {
                    Files.copy(cachedFile, destination.getParent().resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                }
                return 0;
            }
            notification.change("Downloading server...", "This Might Take Some Time..", Notification.Type.INFO, null);
            notification.loading = true;
            notification.autoSlideOut = false;
            HttpURLConnection connection = null;
            InputStream in = null;
            try {
                URL url = new URL(fileURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", "RemotelyOS/2.0");
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
                devPrint("Downloading " + (fileSize > 0 ? (fileSize / 1024 / 1024) + " MB" : "Unknown size") + " from " + connection.getURL() + " ...");
                in = connection.getInputStream();
                Files.copy(in, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                if (fileURL.toLowerCase().endsWith(".zip")) {
                    unzip(cachedFile, destination.getParent());
                } else {
                    Files.copy(cachedFile, destination.getParent().resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                }
                return 0;
            } catch (IOException e) {
                return 1;
            } finally {
                if (in != null)
                    try { in.close(); } catch (IOException ignored) {}
                if (connection != null) connection.disconnect();
            }
        } catch (IOException e) {
            return 1;
        }
    }

    private static void unzip(Path zipFilePath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(destDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null && !Files.exists(newPath.getParent())) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }


    private static int createStartScript(Path serverDir, String jarName, String ramAmount, String aikarsFlags) {
        try {
            boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            String ramDigits = ramAmount.replaceAll("[^0-9]", "");
            if (ramDigits.isEmpty()) {
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
            } else {
                Path shFile = serverDir.resolve("start.sh");
                String shContent = "#!/bin/bash\ncd \"$(dirname \"$0\")\"\n" + javaCommand;
                Files.writeString(shFile, shContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = new java.util.HashSet<>();
                    perms.add(PosixFilePermission.OWNER_READ);
                    perms.add(PosixFilePermission.OWNER_WRITE);
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    perms.add(PosixFilePermission.GROUP_READ);
                    perms.add(PosixFilePermission.GROUP_EXECUTE);
                    perms.add(PosixFilePermission.OTHERS_READ);
                    perms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(shFile, perms);
                } catch (UnsupportedOperationException | IOException | SecurityException e) {
                    if (!shFile.toFile().setExecutable(true, false)) {
                        System.err.println("Warning: Could not set start.sh as executable using fallback. You may need to run 'chmod +x start.sh' manually.");
                    }
                    if (!(e instanceof UnsupportedOperationException)) {
                        System.err.println("Warning: Could not set POSIX permissions for start.sh: " + e.getMessage());
                    }
                }
            }
            return 0;
        } catch (IOException e) {
            devPrint("Error creating start script: " + e.getMessage());
            return 2;
        }
    }
}
