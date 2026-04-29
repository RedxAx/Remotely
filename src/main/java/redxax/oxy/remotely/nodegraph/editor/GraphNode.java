package redxax.oxy.remotely.nodegraph.editor;

import java.util.Map;

public interface GraphNode {
    String getType();

    double getX();

    void setX(double x);

    double getY();

    void setY(double y);

    Map<String, Object> getInputValues();

    void setInputValue(String key, Object value);
}
