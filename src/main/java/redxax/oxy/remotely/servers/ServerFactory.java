package redxax.oxy.remotely.servers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.ArrayList;
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
            System.out.println("Fetching build info for type: " + serverType + ", version: " + serverVersion);
            String downloadURL = getDownloadURL(serverType, serverVersion);
            if (downloadURL == null) {
                System.err.println("Could not determine download URL for server type '" + serverType + "' and version '" + serverVersion + "'.");
                return 3;
            }
            System.out.println("Download URL determined: " + downloadURL);
            String fileName;
            Matcher matcher = Pattern.compile("([^/]+\\.jar)$").matcher(downloadURL);
            if (matcher.find()) {
                fileName = matcher.group(1);
            } else {
                fileName = "server.jar";
            }
            Path jarPath = serverDir.resolve(fileName);
            System.out.println("Starting download to: " + jarPath.toAbsolutePath());
            int downloadCode = downloadServerBuild(downloadURL, jarPath);
            if (downloadCode != 0) {
                System.err.println("Download failed with code: " + downloadCode);
                return downloadCode;
            }
            int scriptCode = createStartScript(serverDir, fileName, ramAmount, aikarsFlags);
            if (scriptCode != 0) {
                System.err.println("Failed to create start script with code: " + scriptCode);
                return scriptCode;
            }
            int iconCode = downloadServerIcon(serverType, serverDir);
            if (iconCode != 0) {
                System.err.println("Warning: Could not download server icon.");
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
            } else {
                System.out.println("eula.txt not found, it will likely be generated on first run. Please accept it manually.");
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

    private static String getDownloadURL(String serverType, String serverVersion) {
        try {
            String apiEndpoint = "https://mcjars.app/api/v1/builds/" + serverType.toUpperCase() + "/" + serverVersion + "/latest";
            System.out.println("Fetching server build info from: " + apiEndpoint);
            String jsonResponse = simpleHttpGet(apiEndpoint);
            if (jsonResponse == null) {
                System.err.println("Failed to fetch server build info from MCJars API.");
                return null;
            }
            Pattern jarUrlPattern = Pattern.compile("\"jarUrl\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = jarUrlPattern.matcher(jsonResponse);
            if (matcher.find()) {
                return matcher.group(1);
            } else {
                System.err.println("jarUrl not found in MCJars API response.");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error fetching download URL from MCJars API: " + e.getMessage());
            e.printStackTrace();
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
                System.out.println("Redirecting to: " + newUrl);
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
                try { in.close(); } catch (IOException ignored) {}
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

    private static int downloadServerIcon(String serverType, Path serverDir) {
        try {
            String apiEndpoint = "https://mcjars.app/api/v1/types";
            System.out.println("Fetching server type info from: " + apiEndpoint);
            String jsonResponse = simpleHttpGet(apiEndpoint);
            if (jsonResponse == null) {
                System.err.println("Failed to fetch types info from MCJars API.");
                return 1;
            }
            String regex = "\"" + serverType.toUpperCase() + "\"\\s*:\\s*\\{[^}]*\"icon\"\\s*:\\s*\"([^\"]+)\"";
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                String iconUrl = matcher.group(1);
                System.out.println("Icon URL determined: " + iconUrl);
                Path iconPath = serverDir.resolve("icon.png");
                int code = downloadBinaryFile(iconUrl, iconPath);
                if (code != 0) {
                    System.err.println("Failed to download icon file.");
                    return code;
                }
                System.out.println("Downloaded icon to: " + iconPath.toAbsolutePath());
                return 0;
            } else {
                System.err.println("Icon URL not found for server type: " + serverType);
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error downloading server icon: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private static int downloadBinaryFile(String fileURL, Path destination) {
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
                System.err.println("Server returned HTTP " + responseCode + " for file URL: " + connection.getURL());
                return 1;
            }
            in = connection.getInputStream();
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            return 0;
        } catch (IOException e) {
            System.err.println("Error downloading binary file from " + fileURL + ": " + e.getMessage());
            e.printStackTrace();
            return 1;
        } finally {
            if (in != null)
                try { in.close(); } catch (IOException ignore) {}
            if (connection != null)
                connection.disconnect();
        }
    }
}
