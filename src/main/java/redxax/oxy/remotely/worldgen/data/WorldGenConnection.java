package redxax.oxy.remotely.worldgen.data;

import redxax.oxy.remotely.nodegraph.editor.GraphConnection;

public class WorldGenConnection implements GraphConnection {
    private String sourceNodeId;
    private String sourcePin;
    private String targetNodeId;
    private String targetPin;

    public WorldGenConnection() {
    }

    public WorldGenConnection(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
        this.sourceNodeId = sourceNodeId;
        this.sourcePin = sourcePin;
        this.targetNodeId = targetNodeId;
        this.targetPin = targetPin;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getSourcePin() {
        return sourcePin;
    }

    public void setSourcePin(String sourcePin) {
        this.sourcePin = sourcePin;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getTargetPin() {
        return targetPin;
    }

    public void setTargetPin(String targetPin) {
        this.targetPin = targetPin;
    }
}
