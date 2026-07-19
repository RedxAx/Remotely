package redxax.oxy.remotely.flow.sync;

import redxax.oxy.remotely.data.flow.OptionCatalogItem;

import java.util.ArrayList;
import java.util.List;

public class OptionCatalogSnapshot {
    public static final int CURRENT_VERSION = 2;
    private int version = CURRENT_VERSION;
    private String sourceId = "";
    private String contextKey = "";
    private String revision = "";
    private long sequence;
    private List<String> values = new ArrayList<>();
    private List<OptionCatalogItem> items = new ArrayList<>();
    private String status = "available";
    private String diagnostic = "";

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getSourceId() {
        return sourceId != null ? sourceId : "";
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId != null ? sourceId : "";
    }

    public String getContextKey() {
        return contextKey != null ? contextKey : "";
    }

    public void setContextKey(String contextKey) {
        this.contextKey = contextKey != null ? contextKey : "";
    }

    public String getRevision() {
        return revision != null ? revision : "";
    }

    public void setRevision(String revision) {
        this.revision = revision != null ? revision : "";
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = Math.max(0L, sequence);
    }

    public List<String> getValues() {
        return values != null ? values : List.of();
    }

    public void setValues(List<String> values) {
        this.values = values != null ? new ArrayList<>(values) : new ArrayList<>();
    }

    public List<OptionCatalogItem> getItems() {
        return items != null ? items : List.of();
    }

    public void setItems(List<OptionCatalogItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public String getStatus() {
        return status != null && !status.isBlank() ? status : "available";
    }

    public void setStatus(String status) {
        this.status = status != null && !status.isBlank() ? status : "available";
    }

    public String getDiagnostic() {
        return diagnostic != null ? diagnostic : "";
    }

    public void setDiagnostic(String diagnostic) {
        this.diagnostic = diagnostic != null ? diagnostic : "";
    }
}
