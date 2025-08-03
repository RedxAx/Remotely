package redxax.oxy.remotely.terminal;

import com.jediterm.core.input.InputEvent;
import org.lwjgl.glfw.GLFW;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class KeyCodeConverter {
    private static final Map<Integer, Integer> AWT_KEY_MAP = new HashMap<>();

    static {
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_ENTER, KeyEvent.VK_ENTER);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_BACKSPACE, KeyEvent.VK_BACK_SPACE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_TAB, KeyEvent.VK_TAB);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_ESCAPE, KeyEvent.VK_ESCAPE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_SPACE, KeyEvent.VK_SPACE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_DELETE, KeyEvent.VK_DELETE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_INSERT, KeyEvent.VK_INSERT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_HOME, KeyEvent.VK_HOME);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_END, KeyEvent.VK_END);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_PAGE_UP, KeyEvent.VK_PAGE_UP);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_PAGE_DOWN, KeyEvent.VK_PAGE_DOWN);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_LEFT, KeyEvent.VK_LEFT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_UP, KeyEvent.VK_UP);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_RIGHT, KeyEvent.VK_RIGHT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_DOWN, KeyEvent.VK_DOWN);

        for (int i = 1; i <= 12; i++) {
            AWT_KEY_MAP.put(GLFW.GLFW_KEY_F1 + (i - 1), KeyEvent.VK_F1 + (i - 1));
        }

        for (int i = 0; i < 26; i++) {
            AWT_KEY_MAP.put(GLFW.GLFW_KEY_A + i, KeyEvent.VK_A + i);
        }

        for (int i = 0; i < 10; i++) {
            AWT_KEY_MAP.put(GLFW.GLFW_KEY_0 + i, KeyEvent.VK_0 + i);
        }

        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_0, KeyEvent.VK_NUMPAD0);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_1, KeyEvent.VK_NUMPAD1);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_2, KeyEvent.VK_NUMPAD2);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_3, KeyEvent.VK_NUMPAD3);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_4, KeyEvent.VK_NUMPAD4);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_5, KeyEvent.VK_NUMPAD5);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_6, KeyEvent.VK_NUMPAD6);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_7, KeyEvent.VK_NUMPAD7);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_8, KeyEvent.VK_NUMPAD8);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_9, KeyEvent.VK_NUMPAD9);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_ENTER, KeyEvent.VK_ENTER);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_ADD, KeyEvent.VK_PLUS);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_SUBTRACT, KeyEvent.VK_MINUS);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_MULTIPLY, KeyEvent.VK_MULTIPLY);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_DIVIDE, KeyEvent.VK_DIVIDE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_DECIMAL, KeyEvent.VK_DECIMAL);

        AWT_KEY_MAP.put(GLFW.GLFW_KEY_SEMICOLON, KeyEvent.VK_SEMICOLON);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_EQUAL, KeyEvent.VK_EQUALS);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_COMMA, KeyEvent.VK_COMMA);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_MINUS, KeyEvent.VK_MINUS);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_PERIOD, KeyEvent.VK_PERIOD);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_SLASH, KeyEvent.VK_SLASH);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_GRAVE_ACCENT, KeyEvent.VK_BACK_QUOTE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_LEFT_BRACKET, KeyEvent.VK_OPEN_BRACKET);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_BACKSLASH, KeyEvent.VK_BACK_SLASH);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_RIGHT_BRACKET, KeyEvent.VK_CLOSE_BRACKET);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_APOSTROPHE, KeyEvent.VK_QUOTE);
    }

    public static int toAwtKeyCode(int glfwKeyCode) {
        return AWT_KEY_MAP.getOrDefault(glfwKeyCode, KeyEvent.VK_UNDEFINED);
    }

    public static int toAwtModifiers(int glfwModifiers) {
        int awtModifiers = 0;
        if ((glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            awtModifiers |= InputEvent.SHIFT_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            awtModifiers |= InputEvent.CTRL_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_ALT) != 0) {
            awtModifiers |= InputEvent.ALT_MASK;
        }
        if ((glfwModifiers & GLFW.GLFW_MOD_SUPER) != 0) {
            awtModifiers |= InputEvent.META_MASK;
        }
        return awtModifiers;
    }
}