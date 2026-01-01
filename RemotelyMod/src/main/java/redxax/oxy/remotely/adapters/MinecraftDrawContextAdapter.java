package redxax.oxy.remotely.adapters;

import dev.deftu.omnicore.api.OmniResourceLocation;
import dev.deftu.omnicore.api.client.image.OmniImage;
import dev.deftu.omnicore.api.client.image.OmniImages;
import dev.deftu.omnicore.api.client.render.DefaultVertexFormats;
import dev.deftu.omnicore.api.client.render.DrawMode;
import dev.deftu.omnicore.api.client.render.OmniRenderingContext;
import dev.deftu.omnicore.api.client.render.OmniTextureUnit;
import dev.deftu.omnicore.api.client.render.OmniTextRenderer;
import dev.deftu.omnicore.api.client.render.ScissorBox;
import dev.deftu.omnicore.api.client.render.pipeline.OmniRenderPipeline;
import dev.deftu.omnicore.api.client.render.pipeline.OmniRenderPipelines;
import dev.deftu.omnicore.api.client.render.state.OmniBlendState;
import dev.deftu.omnicore.api.client.render.stack.OmniPoseStack;
import dev.deftu.omnicore.api.client.render.vertex.OmniBufferBuilder;
import dev.deftu.omnicore.api.client.render.vertex.OmniBufferBuilders;
import dev.deftu.omnicore.api.client.render.vertex.UV;
import dev.deftu.omnicore.api.client.textures.OmniTextureHandle;
import dev.deftu.omnicore.api.client.textures.OmniTextures;
import dev.deftu.omnicore.api.color.OmniColor;
import dev.deftu.omnicore.api.color.OmniColors;
import net.minecraft.network.chat.Component;
//#if MC >= 1.21.9
import net.minecraft.network.chat.FontDescription;
//#endif
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import restudio.rescreen.platform.IDrawContext;
import restudio.rescreen.platform.IMatrixStack;
import restudio.rescreen.text.StyledText;
import restudio.rescreen.util.Identifier;
import restudio.rescreen.util.ResourceManager;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class MinecraftDrawContextAdapter implements IDrawContext {
    private static final Map<BufferedImage, OmniTextureHandle> TEXTURE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private static final OmniRenderPipeline INVERTED_PIPELINE = OmniRenderPipelines.builderWithDefaultShader(
            OmniResourceLocation.createOrThrow("rescreen", "inverted_rect"),
            DefaultVertexFormats.POSITION_COLOR, DrawMode.QUADS).setColorLogic(OmniRenderPipeline.ColorLogic.OR_REVERSE)
        .setBlendState(OmniBlendState.DISABLED).build();

    private static final OmniColor SELECTION_COLOR = new OmniColor(0.0f, 0.0f, 1.0f, 1.0f);

    private final OmniRenderingContext ctx;
    private final OmniPoseStack matrices;
    public float renderScale;

    public MinecraftDrawContextAdapter(@NotNull OmniRenderingContext ctx, float renderScale) {
        this.ctx = ctx;
        this.matrices = ctx.pose();
        this.renderScale = renderScale;
    }

    public MinecraftDrawContextAdapter(@NotNull OmniRenderingContext ctx) {
        this(ctx, 1.0f);
    }

    public OmniRenderingContext getOmniContext() {
        return ctx;
    }

    private OmniColor fromArgb(int argb) {
        float a = (float) ((argb >> 24) & 255) / 255.0F;
        float r = (float) ((argb >> 16) & 255) / 255.0F;
        float g = (float) ((argb >> 8) & 255) / 255.0F;
        float b = (float) (argb & 255) / 255.0F;
        return new OmniColor(r, g, b, a);
    }

    private static OmniTextureHandle ensureTexture(BufferedImage image) {
        OmniTextureHandle existing = TEXTURE_CACHE.get(image);
        if (existing != null) return existing;
        synchronized (TEXTURE_CACHE) {
            OmniTextureHandle again = TEXTURE_CACHE.get(image);
            if (again != null) return again;
            try (OmniImage oi = OmniImages.from(image)) {
                OmniTextureHandle handle = OmniTextures.load(oi);
                OmniTextures.register(handle.getLocation(), handle);
                TEXTURE_CACHE.put(image, handle);
                return handle;
            } catch (Exception e) {
                System.out.println("Failed to load texture for buffered image");
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public IMatrixStack getMatrices() {
        return new IMatrixStack() {
            @Override public void push() { matrices.push(); }
            @Override public void pop() { matrices.pop(); }
            @Override public void translate(float x, float y, float z) { matrices.translate(x, y, z); }
            @Override public void scale(float x, float y, float z) { matrices.scale(x, y, z); }
            @Override public void rotate(float angle, float x, float y, float z) { matrices.rotate(angle, x, y, z, true); }
            @Override public void multiply(float angle) { matrices.rotate(angle, 0, 0, 1, true); }
        };
    }

    @Override public void pushScissorState() {}
    @Override public void popScissorState() {}
    @Override public void clearScissor() {}

    @Override
    public void enableScissor(float x1, float y1, float x2, float y2) {
        int sx = (int) (x1 * renderScale);
        int sy = (int) (y1 * renderScale);
        int sw = (int) ((x2 - x1) * renderScale);
        int sh = (int) ((y2 - y1) * renderScale);
        ctx.pushScissor(sx, sy, sw, sh);
    }

    @Override
    public boolean scissorsContains(int i, int i1) {
        return ctx.doesScissorContain((int) (i * renderScale), (int) (i1 * renderScale));
    }

    @Override
    public void disableScissor() {
        ctx.popScissor();
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        OmniColor color = fromArgb(argb);
        OmniBufferBuilder builder = OmniBufferBuilders.create(OmniRenderPipelines.POSITION_COLOR);
        builder.quad(ctx.pose(), x1, y1, x2 - x1, y2 - y1, color);
        builder.buildOrThrow().drawAndClose(OmniRenderPipelines.POSITION_COLOR, encoder -> {
            ScissorBox scissor = ctx.getCurrentScissor();
            if (scissor != null) {
                encoder.enableScissor(scissor);
            } else {
                encoder.disableScissor();
            }
        });
    }

    @Override
    public void fillGradient(int i, int i1, int i2, int i3, int i4, int i5) {
        fillGradient(i, i1, i2, i3, i4, i5, false);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2, boolean horizontal) {
        OmniBufferBuilder builder = OmniBufferBuilders.create(OmniRenderPipelines.POSITION_COLOR);
        OmniColor c1 = fromArgb(color1);
        OmniColor c2 = fromArgb(color2);

        if (!horizontal) {
            builder.vertex(ctx.pose(), x2, y1, 0).color(c1).next();
            builder.vertex(ctx.pose(), x1, y1, 0).color(c1).next();
            builder.vertex(ctx.pose(), x1, y2, 0).color(c2).next();
            builder.vertex(ctx.pose(), x2, y2, 0).color(c2).next();
        } else {
            float a1 = (float) (color1 >> 24 & 255);
            float r1 = (float) (color1 >> 16 & 255);
            float g1 = (float) (color1 >> 8 & 255);
            float b1 = (float) (color1 & 255);
            float a2 = (float) (color2 >> 24 & 255);
            float r2 = (float) (color2 >> 16 & 255);
            float g2 = (float) (color2 >> 8 & 255);
            float b2 = (float) (color2 & 255);
            int width = x2 - x1;
            if (width <= 0) return;
            for (int i = 0; i < width; i++) {
                float t = (width == 1) ? 0.0f : (float) i / (float) (width - 1);
                int a = (int) (a1 * (1 - t) + a2 * t);
                int r = (int) (r1 * (1 - t) + r2 * t);
                int g = (int) (g1 * (1 - t) + g2 * t);
                int b = (int) (b1 * (1 - t) + b2 * t);
                int interpolatedColor = (a << 24) | (r << 16) | (g << 8) | b;
                OmniColor omniColor = fromArgb(interpolatedColor);
                builder.quad(ctx.pose(), x1 + i, y1, 1, y2 - y1, omniColor);
            }
        }

        builder.buildOrThrow().drawAndClose(OmniRenderPipelines.POSITION_COLOR, encoder -> {
            ScissorBox scissor = ctx.getCurrentScissor();
            if (scissor != null) {
                encoder.enableScissor(scissor);
            } else {
                encoder.disableScissor();
            }
        });
    }

    @Override public void fillRoundedRectWithBorders(int i, int i1, int i2, int i3, float v, int i4, int i5, int i6) {}
    @Override public void drawAnimatedCornerGradient(float v, float v1, float v2, float v3, int i) {}

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {

        ScissorBox scissor = ctx.getCurrentScissor();
        boolean manualScissor = ctx.getGraphics() != null && scissor != null;

        if (manualScissor) {
            ctx.getGraphics().enableScissor(scissor.getX(), scissor.getY(), scissor.getX() + scissor.getWidth(), scissor.getY() + scissor.getHeight());
        }

        ctx.renderText(text, (float) x, (float) y, fromArgb(color), shadow);

        if (manualScissor) {
            ctx.getGraphics().disableScissor();
        }
    }

    @Override
    public void drawStyledText(Object text, int x, int y, int color, boolean shadow) {
        ScissorBox scissor = ctx.getCurrentScissor();
        boolean manualScissor = ctx.getGraphics() != null && scissor != null;

        if (manualScissor) {
            ctx.getGraphics().enableScissor(scissor.getX(), scissor.getY(), scissor.getX() + scissor.getWidth(), scissor.getY() + scissor.getHeight());
        }

        if (text instanceof Component mcText) {
            ctx.renderText(mcText, (float) x, (float) y, fromArgb(color == 0 ? 0xFFFFFFFF : color), shadow);
        } else if (text instanceof StyledText styledText) {
            if ((styledText.color >> 24 & 0xFF) == 0) {
                if (manualScissor) ctx.getGraphics().disableScissor();
                return;
            }
            MutableComponent renderText = Component.literal(styledText.text);
            if (styledText.font instanceof ResourceLocation rl) {
                //#if MC >= 1.21.9
                renderText.setStyle(Style.EMPTY.withFont(new FontDescription.Resource(rl)));
                //#endif
                //#if MC < 1.21.9
                //$$ renderText.setStyle(Style.EMPTY.withFont(rl));
                //#endif
            }
            ctx.renderText(renderText, (float) x, (float) y, fromArgb(styledText.color), shadow);
        } else {
            ctx.renderText(String.valueOf(text), (float) x, (float) y, fromArgb(color), shadow);
        }

        if (manualScissor) {
            ctx.getGraphics().disableScissor();
        }
    }

    @Override
    public void drawBufferedImage(BufferedImage image, float x, float y, float width, float height) {
        if (image == null) return;
        OmniTextureHandle handle = ensureTexture(image);
        if (handle == null) return;
        int dw = Math.max(1, (int) Math.ceil(width <= 0 ? handle.getWidth() : width));
        int dh = Math.max(1, (int) Math.ceil(height <= 0 ? handle.getHeight() : height));
        float du = 0.5f / Math.max(1, handle.getWidth());
        float dv = 0.5f / Math.max(1, handle.getHeight());

        OmniBufferBuilder builder = OmniBufferBuilders.create(OmniRenderPipelines.TEXTURED);
        builder.quad(ctx.pose(), x, y, dw, dh, OmniColors.WHITE, new UV(du, dv, 1f - du, 1f - dv));

        builder.buildOrThrow().drawAndClose(OmniRenderPipelines.TEXTURED, encoder -> {
            ScissorBox scissor = ctx.getCurrentScissor();
            if (scissor != null) {
                encoder.enableScissor(scissor);
            } else {
                encoder.disableScissor();
            }
            encoder.texture(OmniTextureUnit.TEXTURE0, handle.getId());
        });
    }

    @Override
    public void drawPixelArt(BufferedImage bufferedImage, float x, float y, float width, float height) {
        drawBufferedImage(bufferedImage, x, y, width, height);
    }

    @Override
    public void drawBufferedImage(Identifier identifier, float v, float v1, float v2, float v3) {
        drawBufferedImage(ResourceManager.getInstance().getImage(identifier), v, v1, v2, v3);
    }

    @Override
    public void drawPixelArt(Identifier identifier, float x, float y, float width, float height) {
        drawBufferedImage(identifier, x, y, width, height);
    }

    @Override
    public void drawInvertedRect(float x1, float y1, float x2, float y2) {
        if (x1 == x2 || y1 == y2) return;
        float minX = Math.min(x1, x2);
        float minY = Math.min(y1, y2);
        float w = Math.max(x1, x2) - minX;
        float h = Math.max(y1, y2) - minY;

        OmniBufferBuilder builder = OmniBufferBuilders.create(INVERTED_PIPELINE);
        builder.quad(ctx.pose(), minX, minY, w, h, SELECTION_COLOR);
        builder.buildOrThrow().drawAndClose(INVERTED_PIPELINE, encoder -> {
            ScissorBox scissor = ctx.getCurrentScissor();
            if (scissor != null) {
                encoder.enableScissor(scissor);
            } else {
                encoder.disableScissor();
            }
        });
    }
}
