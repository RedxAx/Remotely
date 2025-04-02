package redxax.oxy.common.servers;

import java.util.List;
import java.util.Set;

import redxax.oxy.common.servers.SettingsScreen.ServerSettingType;

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
    public Settings(String name, String description, String tab, String file, String key, ServerSettingType type, String defaultValue) {
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
    }
    public Settings(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description, List<String> options) {
        this(name, description, tab, file, key, type, defaultValue);
        this.options = options;
    }
    public Settings(String name, String description, String tab, String file, String key, ServerSettingType type, String defaultValue, List<String> options) {
        this(name, description, tab, file, key, type, defaultValue);
        this.options = options;
    }
    public Settings(String name, String description, String tab, String file, String key, ServerSettingType type, String defaultValue, int min, int max) {
        this(name, description, tab, file, key, type, defaultValue);
        this.min = min;
        this.max = max;
    }
    public Settings(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description, int min, int max) {
        this(name, description, tab, file, key, type, defaultValue);
        this.min = min;
        this.max = max;
    }
    public Settings(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description, String dependencyKey, String dependencyValue) {
        this(name, description, tab, file, key, type, defaultValue);
        this.dependencyKey = dependencyKey;
        this.dependencyValue = dependencyValue;
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
        if (options == null || options.isEmpty()) return;
        if (newIndex < 0 || newIndex >= options.size()) return;
        value = options.get(newIndex);
    }
}
