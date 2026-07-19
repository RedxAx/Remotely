package redxax.oxy.remotely.flow.sync;

public class FlowConversionRule {
    private String sourceTypeId;
    private String targetTypeId;
    private String implementationId;
    private boolean safe = true;
    private boolean lossy;
    private int cost = 1;
    private String availability;

    public FlowConversionRule() {
    }

    public FlowConversionRule(String sourceTypeId, String targetTypeId) {
        this.sourceTypeId = sourceTypeId;
        this.targetTypeId = targetTypeId;
    }

    public FlowConversionRule(String sourceTypeId, String targetTypeId, String implementationId, boolean safe, boolean lossy,
                              int cost, String availability) {
        this.sourceTypeId = sourceTypeId;
        this.targetTypeId = targetTypeId;
        this.implementationId = implementationId;
        this.safe = safe;
        this.lossy = lossy;
        this.cost = cost;
        this.availability = availability;
    }

    public String getSourceTypeId() {
        return sourceTypeId;
    }

    public void setSourceTypeId(String sourceTypeId) {
        this.sourceTypeId = sourceTypeId;
    }

    public String getTargetTypeId() {
        return targetTypeId;
    }

    public void setTargetTypeId(String targetTypeId) {
        this.targetTypeId = targetTypeId;
    }

    public String getImplementationId() {
        return implementationId;
    }

    public void setImplementationId(String implementationId) {
        this.implementationId = implementationId;
    }

    public boolean isSafe() {
        return safe;
    }

    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    public boolean isLossy() {
        return lossy;
    }

    public void setLossy(boolean lossy) {
        this.lossy = lossy;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }
}
