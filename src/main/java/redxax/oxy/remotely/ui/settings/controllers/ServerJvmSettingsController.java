package redxax.oxy.remotely.ui.settings.controllers;

import com.sun.management.OperatingSystemMXBean;
import restudio.rebase.Rebase;
import restudio.rebase.backend.ServerBackend;
import restudio.rebase.java.JavaManager;
import restudio.rebase.java.JavaRuntime;
import restudio.rescreen.ui.settings.Setting;
import restudio.rescreen.ui.widgets.DoubleSliderWidget;
import restudio.rescreen.ui.widgets.DropDownWidget;
import restudio.rescreen.ui.widgets.TextInputWidget;
import restudio.rescreen.util.Notification;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerJvmSettingsController {
    private final restudio.rebase.instance.Instance instance;
    private final JavaManager javaManager;
    private static final int MIN_RAM_MB = 512;
    private final int maxSystemRamMb;
    private final boolean isRemote;
    private Map<String, String> remoteVariables;

    public ServerJvmSettingsController(restudio.rebase.instance.Instance instance) {
        this.instance = instance;
        this.javaManager = Rebase.get().getJavaManager();

        ServerBackend backend = instance.getBackend();
        this.isRemote = backend != null && !"LOCAL".equalsIgnoreCase(backend.getFileSystem().getMetadata("type"));

        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        this.maxSystemRamMb = (int) (osBean.getTotalMemorySize() / 1024 / 1024);
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
            builder.addRow("Server Jar File", true, 20, jarFile);

            TextInputWidget maxRam = new TextInputWidget.Builder()
                    .text(remoteVariables.getOrDefault("MAXIMUM_RAM", "90"))
                    .onChange(t -> remoteVariables.put("MAXIMUM_RAM", t))
                    .size(60, 20)
                    .build();
            builder.addRow("Max RAM (%)", true, 20, maxRam);

        } else {
            String currentJvmArgs = getJvmArgs();
            String currentJavaPath = parseJavaPath(currentJvmArgs);
            String remoteJavaPath = parseRemoteJavaPath(currentJvmArgs);

            if (isRemote) {
                TextInputWidget remoteJavaInput = new TextInputWidget.Builder()
                        .text(remoteJavaPath != null ? remoteJavaPath : "java")
                        .placeholder("Remote Java Path (e.g., /usr/lib/jvm/java-21-openjdk/bin/java)")
                        .onChange(text -> updateJvmArgs(null, parseRam(getJvmArgs()), parseAdditionalArgs(getJvmArgs()), text))
                        .size(500, 20)
                        .build();
                builder.addRow("Remote Java Path", true, 20, remoteJavaInput);
            } else {
                List<JavaRuntime> runtimes = new ArrayList<>();
                JavaRuntime defaultRuntime = new JavaRuntime("Auto-detect (Default)", null, false);
                runtimes.add(defaultRuntime);
                runtimes.addAll(javaManager.getRuntimes());

                JavaRuntime selectedRuntime = runtimes.stream()
                        .filter(r -> Objects.equals(r.getPath(), currentJavaPath))
                        .findFirst()
                        .orElse(defaultRuntime);

                DropDownWidget<JavaRuntime> javaDropdown = new DropDownWidget.Builder<>(runtimes)
                        .displayFunction(JavaRuntime::getName)
                        .selectedItem(selectedRuntime)
                        .onSelectionChanged(runtime -> {
                            updateJvmArgs(runtime.getPath(), parseRam(getJvmArgs()), parseAdditionalArgs(getJvmArgs()), null);
                            checkCompatibility(runtime);
                        })
                        .size(300, 20)
                        .build();
                builder.addRow("Java Runtime", true, 20, javaDropdown);
            }

            int currentRamMb = parseRam(currentJvmArgs);
            String additionalArgs = parseAdditionalArgs(currentJvmArgs);

            AtomicReference<DoubleSliderWidget> ramSliderRef = new AtomicReference<>();
            AtomicReference<TextInputWidget> ramInputRef = new AtomicReference<>();

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

            builder.addRow("Memory (MB)", true, 20, ramSlider, ramInput);

            TextInputWidget jvmArgsInput = new TextInputWidget.Builder()
                    .text(additionalArgs)
                    .onChange(text -> updateJvmArgs(isRemote ? null : parseJavaPath(getJvmArgs()), parseRam(getJvmArgs()), text, isRemote ? parseRemoteJavaPath(getJvmArgs()) : null))
                    .build();
            builder.addRow("Additional JVM Arguments", true, 20, jvmArgsInput);
        }

        return List.of(builder.build());
    }

    private String getJvmArgs() {
        return instance.getJvmArgs();
    }

    private void setJvmArgs(String args) {
        instance.setJvmArgs(args);
    }

    private void updateJvmArgs(String localJavaPath, int ramMb, String additionalArgs, String remoteJavaPath) {
        StringBuilder sb = new StringBuilder();

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

    private void checkCompatibility(JavaRuntime runtime) {
        if (runtime == null || runtime.getMajorVersion() == 0) return;

        String mcVersionStr = instance.getVersionId();
        if (mcVersionStr == null || mcVersionStr.isEmpty()) return;

        Pattern pattern = Pattern.compile("1\\.(\\d+)(\\.\\d+)?");
        Matcher matcher = pattern.matcher(mcVersionStr);
        if (!matcher.find()) return;

        int majorMc = Integer.parseInt(matcher.group(1));
        int javaVersion = runtime.getMajorVersion();

        String warning = null;
        if (majorMc >= 21 && javaVersion < 21) warning = "requires Java 21+";
        else if (majorMc >= 18 && javaVersion < 17) warning = "requires Java 17+";
        else if (majorMc == 17 && javaVersion < 16) warning = "requires Java 16+";

        if (warning != null) {
            new Notification("Compatibility Warning",
                    String.format("Version %s %s. Selected Java %d.", mcVersionStr, warning, javaVersion),
                    Notification.Type.WARN);
        }
    }
}
