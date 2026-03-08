package redxax.oxy.remotely.data.flow.world;

public class WorldPortal {
    private String portalId;
    private String portalName;
    private String sourceWorld;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;
    private String destinationWorld;
    private double destinationX;
    private double destinationY;
    private double destinationZ;
    private float destinationYaw;
    private float destinationPitch;
    private boolean enabled;
    private long lastUsedAt;

    public String getPortalId() {
        return portalId;
    }

    public String getPortalName() {
        return portalName;
    }

    public String getSourceWorld() {
        return sourceWorld;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public String getDestinationWorld() {
        return destinationWorld;
    }

    public double getDestinationX() {
        return destinationX;
    }

    public double getDestinationY() {
        return destinationY;
    }

    public double getDestinationZ() {
        return destinationZ;
    }

    public float getDestinationYaw() {
        return destinationYaw;
    }

    public float getDestinationPitch() {
        return destinationPitch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }
}
