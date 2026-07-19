package redxax.oxy.remotely.flow.sync;

import redxax.oxy.remotely.flow.data.FlowTypeRef;

import java.util.List;

public class FlowPropertyMetadata {
    private String family;
    private String property;
    private FlowTypeRef type;
    private List<String> actions;
    private boolean readable;
    private boolean writable;
    private boolean observable;
    private boolean invokable;
    private String owner;

    public FlowPropertyMetadata() {
    }

    public FlowPropertyMetadata(String family, String property, FlowTypeRef type, List<String> actions, boolean readable,
                                boolean writable, boolean observable, boolean invokable, String owner) {
        this.family = family;
        this.property = property;
        this.type = type;
        this.actions = actions;
        this.readable = readable;
        this.writable = writable;
        this.observable = observable;
        this.invokable = invokable;
        this.owner = owner;
    }

    public String getFamily() {
        return family;
    }

    public String getProperty() {
        return property;
    }

    public FlowTypeRef getType() {
        return type;
    }

    public List<String> getActions() {
        return actions != null ? actions : List.of();
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isObservable() {
        return observable;
    }

    public boolean isInvokable() {
        return invokable;
    }

    public String getOwner() {
        return owner;
    }
}
