package redxax.oxy.remotely.flow.data;

import redxax.oxy.remotely.nodegraph.editor.GraphConnection;

public class FlowConnection implements GraphConnection {
    private String sourceNodeId;
    private String sourcePin;
    private String targetNodeId;
    private String targetPin;
    private String editorSourceNodeId;
    private String editorSourcePin;

    public FlowConnection() {
    }

    public FlowConnection(String sourceNodeId, String sourcePin, String targetNodeId, String targetPin) {
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

    public String getEditorSourceNodeId() {
        return editorSourceNodeId;
    }

    public void setEditorSourceNodeId(String editorSourceNodeId) {
        this.editorSourceNodeId = editorSourceNodeId;
    }

    public String getEditorSourcePin() {
        return editorSourcePin;
    }

    public void setEditorSourcePin(String editorSourcePin) {
        this.editorSourcePin = editorSourcePin;
    }
}
