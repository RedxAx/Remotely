package redxax.oxy.common;

import org.spongepowered.asm.mixin.Unique;

@Unique
public class Style1Button {
    public final String label;
    public final Runnable action;
    public int x;
    public int y;
    public final int size = 18;

    public Style1Button(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }
}