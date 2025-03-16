package redxax.oxy.common.servers;

import java.util.List;
import redxax.oxy.common.servers.ServerSettingsScreen.ServerSettingType;

public class ServerSetting {
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

    public ServerSetting(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description) {
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

    public ServerSetting(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description, List<String> options) {
        this(name, file, key, type, defaultValue, tab, description);
        this.options = options;
    }

    public ServerSetting(String name, String file, String key, ServerSettingType type, String defaultValue, String tab, String description, int min, int max) {
        this(name, file, key, type, defaultValue, tab, description);
        this.min = min;
        this.max = max;
    }

    public int getIntValue() {
        try {
            return Integer.parseInt(value);
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
