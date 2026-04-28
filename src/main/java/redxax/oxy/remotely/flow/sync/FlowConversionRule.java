package redxax.oxy.remotely.flow.sync;

public class FlowConversionRule {
    private String sourceTypeId;
    private String targetTypeId;

    public FlowConversionRule() {
    }

    public FlowConversionRule(String sourceTypeId, String targetTypeId) {
        this.sourceTypeId = sourceTypeId;
        this.targetTypeId = targetTypeId;
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
}
