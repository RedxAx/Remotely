package redxax.oxy.remotely.flow.data;

public class FlowVariable {
    private String name;
    private String type;
    private Object initialValue;

    public FlowVariable() {
    }

    public FlowVariable(String name, String type, Object initialValue) {
        this.name = name;
        this.type = type;
        this.initialValue = initialValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(Object initialValue) {
        this.initialValue = initialValue;
    }
}
