package redxax.oxy.remotely.flow.ui;

import restudio.rescreen.util.Notification;

public interface ResourceFlowReferenceHost {
    boolean useResourceInFlow(String resourceType, String resourceId);

    static boolean use(String resourceType, String resourceId, Object... candidates) {
        if (candidates != null) {
            for (Object candidate : candidates) {
                if (candidate instanceof ResourceFlowReferenceHost host) {
                    return host.useResourceInFlow(resourceType, resourceId);
                }
            }
        }
        new Notification("Use In Flow", "Open This Resource In ReSync Studio", Notification.Type.ERROR);
        return false;
    }
}
