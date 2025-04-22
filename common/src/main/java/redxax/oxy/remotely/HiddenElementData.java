package redxax.oxy.remotely;

import net.minecraft.client.gui.Element;

public class HiddenElementData {
    public final Element element;
    public final int origX;
    public final int origY;
    public final int origWidth;
    public final int origHeight;

    public HiddenElementData(Element element, int origX, int origY, int origWidth, int origHeight) {
        this.element = element;
        this.origX = origX;
        this.origY = origY;
        this.origWidth = origWidth;
        this.origHeight = origHeight;
    }
}
