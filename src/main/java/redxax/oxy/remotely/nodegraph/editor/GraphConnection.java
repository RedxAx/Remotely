package redxax.oxy.remotely.nodegraph.editor;

public interface GraphConnection {
    String getSourceNodeId();

    String getSourcePin();

    String getTargetNodeId();

    String getTargetPin();
}
