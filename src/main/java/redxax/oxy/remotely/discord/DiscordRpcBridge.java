package redxax.oxy.remotely.discord;

import redxax.oxy.remotely.config.RemotelyConfigManager;
import restudio.rebase.instance.Instance;
import restudio.rebase.instance.InstanceManager;

import java.lang.reflect.Method;

public final class DiscordRpcBridge {
    private static Object service;
    private static Class<?> serviceClass;
    private static boolean attempted;

    private DiscordRpcBridge() {
    }

    public static void start(RemotelyConfigManager configManager, InstanceManager instanceManager) {
        Object target = service(configManager, instanceManager);
        invoke(target, "start");
    }

    public static void shutdown() {
        invoke(service, "shutdown");
    }

    public static void reloadSettings() {
        invoke(service, "reloadSettings");
    }

    public static void refreshTrackedInstances() {
        invoke(service, "refreshTrackedInstances");
    }

    public static void trackInstance(Instance instance) {
        invoke(service, "trackInstance", new Class<?>[]{Instance.class}, instance);
    }

    public static void setManagerActive() {
        invoke(service, "setManagerActive");
    }

    public static void setGlobalSettingsActive() {
        invoke(service, "setGlobalSettingsActive");
    }

    public static void setServerSettingsActive(Instance instance) {
        invoke(service, "setServerSettingsActive", new Class<?>[]{Instance.class}, instance);
    }

    public static void setServerCreationActive() {
        invoke(service, "setServerCreationActive");
    }

    public static void setLocalTerminalActive() {
        invoke(service, "setLocalTerminalActive");
    }

    public static void setServerActive(Instance instance, String viewName) {
        invoke(service, "setServerActive", new Class<?>[]{Instance.class, String.class}, instance, viewName);
    }

    public static void setReSyncStudioActive(Instance instance, String serverTitle, String studioName) {
        invoke(service, "setReSyncStudioActive", new Class<?>[]{Instance.class, String.class, String.class}, instance, serverTitle, studioName);
    }

    public static void updateServerMetrics(Instance instance, int playerCount, long uptimeMs, double cpuPercent, long memoryBytes, long memoryLimitBytes) {
        invoke(service, "updateServerMetrics", new Class<?>[]{Instance.class, int.class, long.class, double.class, long.class, long.class}, instance, playerCount, uptimeMs, cpuPercent, memoryBytes, memoryLimitBytes);
    }

    public static void markUserActivity() {
        invoke(service, "markUserActivity");
    }

    private static Object service(RemotelyConfigManager configManager, InstanceManager instanceManager) {
        if (service != null || attempted) {
            return service;
        }
        attempted = true;
        try {
            serviceClass = Class.forName("redxax.oxy.remotely.discord.DiscordRpcService");
            service = serviceClass.getConstructor(RemotelyConfigManager.class, InstanceManager.class).newInstance(configManager, instanceManager);
        } catch (Throwable t) {
            service = null;
            serviceClass = null;
        }
        return service;
    }

    private static void invoke(Object target, String methodName) {
        invoke(target, methodName, new Class<?>[0]);
    }

    private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null || serviceClass == null) {
            return;
        }
        try {
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            method.invoke(target, args);
        } catch (Throwable ignored) {
        }
    }
}
