package redxax.oxy.remotely.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import redxax.oxy.remotely.config.SettingsScreen.ServerSettingType;

public class Settings {
    public String name;
    public String file;
    public String key;
    public ServerSettingType type;
    public String value;
    public String tab;
    public String description;
    public boolean focused;
    public int cursorPos;
    public List<String> options;
    public int min;
    public int max;
    public int index;
    public String dependencyKey;
    public String dependencyValue;
    public Map<String, List<String>> dependencies;

    private Settings(String name, String description, String tab, String file, String key, ServerSettingType type, String defaultValue) {
        this.name = name;
        this.file = file;
        this.key = key;
        this.type = type;
        this.value = defaultValue;
        this.tab = tab;
        this.description = description;
        this.focused = false;
        this.cursorPos = this.value.length();
        this.index = 0;
        this.dependencies = new HashMap<>();
    }

    public int getIntValue() {
        try {
            return Math.round(Float.parseFloat(value));
        } catch (Exception e) {
            return min;
        }
    }

    public int getSelectedIndex() {
        if (options == null || options.isEmpty()) return 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) {
                return i;
            }
        }
        return 0;
    }

    public void setOption(int newIndex) {
        if (options == null || options.isEmpty())
            return;
        if (newIndex < 0 || newIndex >= options.size())
            return;
        value = options.get(newIndex);
    }

    public static class Builder {
        private String name;
        private String description;
        private String tab;
        private String file = "none";
        private String key;
        private ServerSettingType type;
        private String defaultValue;
        private List<String> options;
        private int min = 0;
        private int max = 100;
        private String dependencyKey;
        private String dependencyValue;
        private Map<String, List<String>> dependencies = new HashMap<>();

        public Builder(String name, String description, String tab, String key, ServerSettingType type, String defaultValue) {
            this.name = name;
            this.description = description;
            this.tab = tab;
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public Builder file(String file) {
            this.file = file;
            return this;
        }

        public Builder options(List<String> options) {
            this.options = options;
            return this;
        }

        public Builder range(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Builder dependency(String dependencyKey, String... dependencyValues) {
            this.dependencies.computeIfAbsent(dependencyKey, k -> new ArrayList<>());
            for (String v : dependencyValues) {
                if (!this.dependencies.get(dependencyKey).contains(v)) {
                    this.dependencies.get(dependencyKey).add(v);
                }
            }
            return this;
        }

        public Settings build() {
            Settings s = new Settings(this.name, this.description, this.tab, this.file, this.key, this.type, this.defaultValue);
            s.options = this.options;
            s.min = this.min;
            s.max = this.max;
            s.dependencyKey = this.dependencyKey;
            s.dependencyValue = this.dependencyValue;
            s.dependencies = this.dependencies;
            return s;
        }
    }
}
