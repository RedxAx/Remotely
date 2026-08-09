package redxax.oxy.remotely.quickserver;

//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$$ import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class QuickServerJoinScreen extends Screen {
    QuickServerJoinScreen() {
        super(Component.literal("Joining Quick Server"));
    }

    //#if MC >= 26.1
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, height / 2 - 10, 0xFFFFFF);
    }
    //#else
    //$$ @Override
    //$$ public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    //$$     super.render(graphics, mouseX, mouseY, delta);
    //$$     graphics.drawCenteredString(font, title, width / 2, height / 2 - 10, 0xFFFFFF);
    //$$ }
    //#endif

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
