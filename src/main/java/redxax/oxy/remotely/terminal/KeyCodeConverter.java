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
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F1, KeyEvent.VK_F1);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F2, KeyEvent.VK_F2);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F3, KeyEvent.VK_F3);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F4, KeyEvent.VK_F4);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F5, KeyEvent.VK_F5);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F6, KeyEvent.VK_F6);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F7, KeyEvent.VK_F7);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F8, KeyEvent.VK_F8);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F9, KeyEvent.VK_F9);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F10, KeyEvent.VK_F10);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F11, KeyEvent.VK_F11);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_F12, KeyEvent.VK_F12);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_INSERT, KeyEvent.VK_INSERT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_DELETE, KeyEvent.VK_DELETE);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_PAGE_UP, KeyEvent.VK_PAGE_UP);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_PAGE_DOWN, KeyEvent.VK_PAGE_DOWN);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_END, KeyEvent.VK_END);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_HOME, KeyEvent.VK_HOME);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_LEFT, KeyEvent.VK_LEFT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_UP, KeyEvent.VK_UP);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_RIGHT, KeyEvent.VK_RIGHT);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_DOWN, KeyEvent.VK_DOWN);
        AWT_KEY_MAP.put(GLFW.GLFW_KEY_KP_ENTER, KeyEvent.VK_ENTER);
        for (int i = 32; i < 127; i++) {
            if (i >= GLFW.GLFW_KEY_A && i <= GLFW.GLFW_KEY_Z) {
                AWT_KEY_MAP.put(i, i);
            }
        }
    }

    public static int toAwtKeyCode(int glfwKeyCode) {
        return AWT_KEY_MAP.getOrDefault(glfwKeyCode, glfwKeyCode);
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