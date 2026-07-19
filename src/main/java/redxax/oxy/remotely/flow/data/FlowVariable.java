package redxax.oxy.remotely.flow.data;

public class FlowVariable {
    private String name;
    private String type;
    private Object initialValue;
    private String scope = "local";
    private String lifetime = "execution";
    private String owner = "graph";
    private String absencePolicy = "use_default";
    private String concurrencyPolicy = "isolated";

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

    public String getScope() {
        return scope != null && !scope.isBlank() ? scope : "local";
    }

    public void setScope(String scope) {
        this.scope = scope != null && !scope.isBlank() ? scope : "local";
    }

    public String getLifetime() {
        return lifetime != null && !lifetime.isBlank() ? lifetime : "execution";
    }

    public void setLifetime(String lifetime) {
        this.lifetime = lifetime != null && !lifetime.isBlank() ? lifetime : "execution";
    }

    public String getOwner() {
        return owner != null && !owner.isBlank() ? owner : "graph";
    }

    public void setOwner(String owner) {
        this.owner = owner != null && !owner.isBlank() ? owner : "graph";
    }

    public String getAbsencePolicy() {
        return absencePolicy != null && !absencePolicy.isBlank() ? absencePolicy : "use_default";
    }

    public void setAbsencePolicy(String absencePolicy) {
        this.absencePolicy = absencePolicy != null && !absencePolicy.isBlank() ? absencePolicy : "use_default";
    }

    public String getConcurrencyPolicy() {
        return concurrencyPolicy != null && !concurrencyPolicy.isBlank() ? concurrencyPolicy : "isolated";
    }

    public void setConcurrencyPolicy(String concurrencyPolicy) {
        this.concurrencyPolicy = concurrencyPolicy != null && !concurrencyPolicy.isBlank() ? concurrencyPolicy : "isolated";
    }
}
