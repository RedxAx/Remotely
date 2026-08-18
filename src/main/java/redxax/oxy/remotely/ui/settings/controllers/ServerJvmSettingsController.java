package redxax.oxy.remotely.ui.settings.controllers;

import redxax.oxy.remotely.util.BrowserSafeState;
import redxax.oxy.remotely.ui.settings.controllers.ServerJvmSettingsProvider.RuntimeOption;
import restudio.rescreen.ui.core.Screen;
import restudio.rescreen.ui.core.ScreenManager;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.settings.SettingsScreen;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerJvmSettingsController {
    private ServerJvmSettingsProvider provider;
    private static final int MIN_RAM_MB = 512;
    private Map<String, String> remoteVariables;

    public ServerJvmSettingsController(Object instance) {
        if (instance instanceof ServerJvmSettingsProvider settingsProvider) provider = settingsProvider;
    }

    public ServerJvmSettingsController(ServerJvmSettingsProvider provider) {
        this.provider = provider;
    }

    private ServerJvmSettingsProvider provider() {
        if (provider == null) throw new IllegalStateException("Java Settings Provider Is Unavailable");
        return provider;
    }

    public void bindToRemoteVariables(Map<String, String> vars) {
        this.remoteVariables = vars;
    }

    public List<Setting> getSettings() {
        Setting.Builder builder = new Setting.Builder("Java Configuration");

        if (remoteVariables != null) {
            TextInputWidget jarFile = new TextInputWidget.Builder()
                    .text(remoteVariables.getOrDefault("SERVER_JARFILE", "server.jar"))
                    .onChange(t -> remoteVariables.put("SERVER_JARFILE", t))
                    .size(500, 20)
                    .build();
            builder.addRow("Server Jar File", jarFile);

            TextInputWidget maxRam = new TextInputWidget.Builder()
                    .text(remoteVariables.getOrDefault("MAXIMUM_RAM", "90"))
                    .onChange(t -> remoteVariables.put("MAXIMUM_RAM", t))
                    .size(60, 20)
                    .build();
            builder.addRow("Max RAM (%)", maxRam);

        } else {
            ServerJvmSettingsProvider provider = provider();
            boolean isRemote = provider.remote();
            int maxSystemRamMb = provider.maximumMemoryMb();
            String currentJvmArgs = getJvmArgs();
            String currentJavaPath = parseJavaPath(currentJvmArgs);
            String remoteJavaPath = parseRemoteJavaPath(currentJvmArgs);

            if (isRemote) {
                List<RuntimeOption> runtimes = new ArrayList<>();
                RuntimeOption defaultRuntime = new RuntimeOption("Auto-install compatible Java", null, 0);
                runtimes.add(defaultRuntime);
                List<RuntimeOption> remoteRuntimes = provider.remoteRuntimes();
                runtimes.addAll(remoteRuntimes);
                if (remoteRuntimes.isEmpty()) {
                    provider.refreshRemoteRuntimes().thenRun(() -> ScreenManager.getInstance().execute(this::refreshSettings)).exceptionally(e -> null);
                }
                RuntimeOption selectedRuntime = runtimes.stream()
                        .filter(r -> Objects.equals(r.path(), remoteJavaPath))
                        .findFirst()
                        .orElse(defaultRuntime);
                DropDownWidget<RuntimeOption> javaDropdown = new DropDownWidget.Builder<>(runtimes)
                        .displayFunction(RuntimeOption::name)
                        .selectedItem(selectedRuntime)
                        .onSelectionChanged(runtime -> {
                            updateJvmArgs(null, parseRam(getJvmArgs()), parseAdditionalArgs(getJvmArgs()), runtime.path());
                            checkCompatibility(runtime);
                        })
                        .size(300, 20)
                        .build();
                builder.addRow("Remote Java Runtime", javaDropdown);

                TextInputWidget remoteJavaInput = new TextInputWidget.Builder()
                        .text(remoteJavaPath != null ? remoteJavaPath : "")
                        .placeholder("Remote Java Path (e.g., /usr/lib/jvm/java-21-openjdk/bin/java)")
                        .onChange(text -> updateJvmArgs(null, parseRam(getJvmArgs()), parseAdditionalArgs(getJvmArgs()), text))
                        .size(500, 20)
                        .build();
                builder.addRow("Manual Remote Java Path", remoteJavaInput);
            } else {
                List<RuntimeOption> runtimes = new ArrayList<>();
                RuntimeOption defaultRuntime = new RuntimeOption("Auto-detect (Default)", null, 0);
                runtimes.add(defaultRuntime);
                runtimes.addAll(provider.runtimes());

                RuntimeOption selectedRuntime = runtimes.stream()
                        .filter(r -> Objects.equals(r.path(), currentJavaPath))
                        .findFirst()
                        .orElse(defaultRuntime);

                DropDownWidget<RuntimeOption> javaDropdown = new DropDownWidget.Builder<>(runtimes)
                        .displayFunction(RuntimeOption::name)
                        .selectedItem(selectedRuntime)
                        .onSelectionChanged(runtime -> {
                            updateJvmArgs(runtime.path(), parseRam(getJvmArgs()), parseAdditionalArgs(getJvmArgs()), null);
                            checkCompatibility(runtime);
                        })
                        .size(300, 20)
                        .build();
                builder.addRow("Java Runtime", javaDropdown);
            }

            int currentRamMb = parseRam(currentJvmArgs);
            String additionalArgs = parseAdditionalArgs(currentJvmArgs);

            BrowserSafeState.ReferenceValue<DoubleSliderWidget> ramSliderRef = new BrowserSafeState.ReferenceValue<>();
            BrowserSafeState.ReferenceValue<TextInputWidget> ramInputRef = new BrowserSafeState.ReferenceValue<>();

            double sliderValue = Math.max(0, (double) (currentRamMb - MIN_RAM_MB) / (maxSystemRamMb - MIN_RAM_MB));

            DoubleSliderWidget ramSlider = new DoubleSliderWidget.Builder()
                    .value(sliderValue)
                    .onChange(() -> {
                        int newRam = MIN_RAM_MB + (int) (ramSliderRef.get().getValue() * (maxSystemRamMb - MIN_RAM_MB));
                        newRam = (newRam / 256) * 256;
                        ramInputRef.get().setText(String.valueOf(newRam));
                        updateJvmArgs(isRemote ? null : parseJavaPath(getJvmArgs()), newRam, parseAdditionalArgs(getJvmArgs()), isRemote ? parseRemoteJavaPath(getJvmArgs()) : null);
                    })
                    .size(230, 20)
                    .build();
            ramSliderRef.set(ramSlider);

            TextInputWidget ramInput = new TextInputWidget.Builder()
                    .text(String.valueOf(currentRamMb))
                    .size(60, 20)
                    .onChange(text -> {
                        try {
                            int newRam = Integer.parseInt(text);
                            if (newRam >= MIN_RAM_MB && newRam <= maxSystemRamMb) {
                                ramSliderRef.get().setValue((double) (newRam - MIN_RAM_MB) / (maxSystemRamMb - MIN_RAM_MB));
                                updateJvmArgs(isRemote ? null : parseJavaPath(getJvmArgs()), newRam, parseAdditionalArgs(getJvmArgs()), isRemote ? parseRemoteJavaPath(getJvmArgs()) : null);
                            }
                        } catch (NumberFormatException ignored) {}
                    })
                    .build();
            ramInputRef.set(ramInput);

            builder.addRow("Memory (MB)", ramSlider, ramInput);

            TextInputWidget jvmArgsInput = new TextInputWidget.Builder()
                    .text(additionalArgs)
                    .onChange(text -> updateJvmArgs(isRemote ? null : parseJavaPath(getJvmArgs()), parseRam(getJvmArgs()), text, isRemote ? parseRemoteJavaPath(getJvmArgs()) : null))
                    .build();
            builder.addRow("Additional JVM Arguments", jvmArgsInput);
        }

        return List.of(builder.build());
    }

    private String getJvmArgs() {
        return provider().jvmArgs();
    }

    private void setJvmArgs(String args) {
        provider().jvmArgs(args);
    }

    private void updateJvmArgs(String localJavaPath, int ramMb, String additionalArgs, String remoteJavaPath) {
        StringBuilder sb = new StringBuilder();
        boolean isRemote = provider().remote();

        if (isRemote && remoteJavaPath != null && !remoteJavaPath.isEmpty() && !remoteJavaPath.equals("java")) {
            sb.append("-Drebase.remote.java.path=\"").append(remoteJavaPath).append("\" ");
        } else if (!isRemote && localJavaPath != null && !localJavaPath.isEmpty()) {
            sb.append("-Drebase.java.path=\"").append(localJavaPath).append("\" ");
        }

        sb.append("-Xmx").append(ramMb).append("M ");
        sb.append("-Xms").append(ramMb).append("M ");
        sb.append(additionalArgs);
        setJvmArgs(sb.toString().trim());
    }

    private String parseJavaPath(String jvmArgs) {
        return parseArg(jvmArgs, "-Drebase\\.java\\.path=(?:\"([^\"]+)\"|([^\\s]+))", null);
    }

    private String parseRemoteJavaPath(String jvmArgs) {
        return parseArg(jvmArgs, "-Drebase\\.remote\\.java\\.path=(?:\"([^\"]+)\"|([^\\s]+))", null);
    }

    private int parseRam(String jvmArgs) {
        if (jvmArgs == null) return 4096;
        Pattern pattern = Pattern.compile("-Xmx(\\d+)([gGmMkK]?)");
        Matcher matcher = pattern.matcher(jvmArgs);
        int lastRam = 4096;
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                lastRam = switch (unit) {
                    case "g" -> value * 1024;
                    case "k" -> value / 1024;
                    default -> value;
                };
            } catch (NumberFormatException ignored) {
            }
        }
        return lastRam;
    }

    private String parseAdditionalArgs(String jvmArgs) {
        if (jvmArgs == null) return "";
        String temp = jvmArgs.replaceAll("-Drebase\\.java\\.path=(?:\"[^\"]+\"|[^\\s]+)", "");
        temp = temp.replaceAll("-Drebase\\.remote\\.java\\.path=(?:\"[^\"]+\"|[^\\s]+)", "");
        temp = temp.replaceAll("-Xmx\\d+[gGmMkK]?", "");
        temp = temp.replaceAll("-Xms\\d+[gGmMkK]?", "");
        return temp.trim().replaceAll("\\s+", " ");
    }

    private String parseArg(String text, String regex, String defaultValue) {
        if (text == null) return defaultValue;
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }
        return defaultValue;
    }

    private void checkCompatibility(RuntimeOption runtime) {
        if (runtime == null || runtime.majorVersion() == 0) return;

        String mcVersionStr = provider().version();
        if (mcVersionStr == null || mcVersionStr.isEmpty()) return;

        int minVersion = provider().minimumJavaVersion(mcVersionStr);
        int maxVersion = provider().maximumJavaVersion(mcVersionStr);
        int javaVersion = runtime.majorVersion();

        String warning = null;
        if (javaVersion < minVersion) {
            warning = "requires Java " + minVersion + "+";
        } else if (maxVersion > 0 && javaVersion > maxVersion) {
            warning = "supports up to Java " + maxVersion;
        }

        if (warning != null) {
            new Notification("Compatibility Warning",
                    String.format("Version %s %s. Selected Java %d.", mcVersionStr, warning, javaVersion),
                    Notification.Type.WARN);
        }
    }

    private void refreshSettings() {
        Screen currentScreen = ScreenManager.getInstance().getCurrentScreen();
        if (currentScreen instanceof SettingsScreen) {
            ((SettingsScreen) currentScreen).refreshTab("Java");
        }
    }

}
