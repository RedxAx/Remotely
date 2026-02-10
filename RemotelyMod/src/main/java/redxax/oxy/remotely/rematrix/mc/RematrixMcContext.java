package redxax.oxy.remotely.rematrix.mc;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
//#if MC >= 1.20.1
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.renderer.GameRenderer;
//#if MC >= 1.21.5
import net.minecraft.client.renderer.RenderPipelines;
//#endif
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
//#if MC >= 1.21.9
import net.minecraft.network.chat.FontDescription;
//#endif
//#if MC < 1.21.6
//$$ import net.minecraft.client.renderer.RenderType;
//#endif
//#if MC < 1.21.6
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#endif
//#if MC < 1.21.11
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
//#if MC < 1.20.1
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import net.minecraft.client.gui.GuiComponent;
//#endif
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import redxax.oxy.remotely.rematrix.RematrixContext;
import redxax.oxy.remotely.rematrix.RematrixMatrixStack;
import redxax.oxy.remotely.rematrix.RematrixScissorStack;
import redxax.oxy.remotely.rematrix.RematrixTextBridge;
import redxax.oxy.remotely.rematrix.RematrixTextureCache;
import redxax.oxy.remotely.rematrix.RematrixTextureHandle;
import restudio.rescreen.text.StyledText;

public final class RematrixMcContext implements RematrixContext {
    private static final Map<BufferedImage, RematrixTextureHandle> TEXTURE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int SELECTION_COLOR = 0xFF0000FF;

    //#if MC >= 1.20.1
    private final GuiGraphics graphics;
    //#endif
    //#if MC < 1.20.1
    //$$ private final PoseStack graphics;
    //#endif
    private final float scissorScale;
    private final RematrixMatrixStack matrices;
    private final RematrixScissorStack scissors;
    private final RematrixTextureCache textures;
    private final RematrixTextBridge textBridge;

    //#if MC >= 1.20.1
    public RematrixMcContext(@NotNull GuiGraphics graphics) {
        this(graphics, 1.0f);
    }
    //#endif
    //#if MC < 1.20.1
    //$$ public RematrixMcContext(@NotNull PoseStack graphics) {
    //$$     this(graphics, 1.0f);
    //$$ }
    //#endif

    //#if MC >= 1.20.1
    public RematrixMcContext(@NotNull GuiGraphics graphics, float scissorScale) {
        this.graphics = graphics;
        this.scissorScale = scissorScale > 0f ? scissorScale : 1f;
        this.matrices = new McMatrixStack(graphics.pose());
        this.scissors = new McScissorStack();
        this.textBridge = new McTextBridge();
        this.textures = new McTextureCache();
    }
    //#endif
    //#if MC < 1.20.1
    //$$ public RematrixMcContext(@NotNull PoseStack graphics, float scissorScale) {
    //$$     this.graphics = graphics;
    //$$     this.scissorScale = scissorScale > 0f ? scissorScale : 1f;
    //$$     this.matrices = new McMatrixStack(graphics);
    //$$     this.scissors = new McScissorStack();
    //$$     this.textBridge = new McTextBridge();
    //$$     this.textures = new McTextureCache();
    //$$ }
    //#endif

    @Override
    public RematrixMatrixStack matrices() {
        return matrices;
    }

    @Override
    public RematrixScissorStack scissors() {
        return scissors;
    }

    @Override
    public RematrixTextureCache textures() {
        return textures;
    }

    @Override
    public RematrixTextBridge text() {
        return textBridge;
    }

    @Override
    public Object graphics() {
        return graphics;
    }

    //#if MC >= 1.20.1
    public GuiGraphics getGraphics() {
        return graphics;
    }
    //#endif
    //#if MC < 1.20.1
    //$$ public PoseStack getGraphics() {
    //$$     return graphics;
    //$$ }
    //#endif

    public void drawText(String text, int x, int y, int color, boolean shadow) {
        //#if MC >= 1.20.1
        withScissor(() -> {
            if (shadow) {
                graphics.drawString(Minecraft.getInstance().font, text, x, y, color, true);
                return;
            }
            graphics.drawString(Minecraft.getInstance().font, text, x, y, color, false);
        });
        //#endif
        //#if MC < 1.20.1
        //$$ if (shadow) {
        //$$     Minecraft.getInstance().font.drawShadow(graphics, text, x, y, color);
        //$$     return;
        //$$ }
        //$$ Minecraft.getInstance().font.draw(graphics, text, x, y, color);
        //#endif
    }

    public void drawStyledText(Object text, int x, int y, int color, boolean shadow) {
        if (text instanceof Component component) {
            //#if MC >= 1.20.1
            withScissor(() -> {
                if (shadow) {
                    graphics.drawString(Minecraft.getInstance().font, component, x, y, color, true);
                    return;
                }
                graphics.drawString(Minecraft.getInstance().font, component, x, y, color, false);
            });
            //#endif
            //#if MC < 1.20.1
            //$$ if (shadow) {
            //$$     Minecraft.getInstance().font.drawShadow(graphics, component, x, y, color);
            //$$     return;
            //$$ }
            //$$ Minecraft.getInstance().font.draw(graphics, component, x, y, color);
            //#endif
            return;
        }
        if (text instanceof StyledText styledText) {
            if ((styledText.color >> 24 & 0xFF) == 0) {
                return;
            }
            MutableComponent renderText = Component.literal(styledText.text);
            //#if MC >= 1.21.11
            if (styledText.font instanceof Identifier rl) {
            //#endif
            //#if MC < 1.21.11
            //$$ if (styledText.font instanceof ResourceLocation rl) {
            //#endif
                //#if MC >= 1.21.9
                renderText.setStyle(Style.EMPTY.withFont(new FontDescription.Resource(rl)));
                //#endif
                //#if MC < 1.21.9
                //$$ renderText.setStyle(Style.EMPTY.withFont(rl));
                //#endif
            }
            //#if MC >= 1.20.1
            withScissor(() -> {
                if (shadow) {
                    graphics.drawString(Minecraft.getInstance().font, renderText, x, y, styledText.color, true);
                    return;
                }
                graphics.drawString(Minecraft.getInstance().font, renderText, x, y, styledText.color, false);
            });
            //#endif
            //#if MC < 1.20.1
            //$$ if (shadow) {
            //$$     Minecraft.getInstance().font.drawShadow(graphics, renderText, x, y, styledText.color);
            //$$     return;
            //$$ }
            //$$ Minecraft.getInstance().font.draw(graphics, renderText, x, y, styledText.color);
            //#endif
            return;
        }
        drawText(String.valueOf(text), x, y, color, shadow);
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        //#if MC >= 1.20.1
        withScissor(() -> graphics.fill(x1, y1, x2, y2, argb));
        //#endif
        //#if MC < 1.20.1
        //$$ GuiComponent.fill(graphics, x1, y1, x2, y2, argb);
        //#endif
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2, boolean horizontal) {
        if (!horizontal) {
            //#if MC >= 1.20.1
            withScissor(() -> graphics.fillGradient(x1, y1, x2, y2, color1, color2));
            //#endif
            //#if MC < 1.20.1
            //$$ GuiComponent.fillGradient(graphics, x1, y1, x2, y2, color1, color2);
            //#endif
            return;
        }
        int width = x2 - x1;
        if (width <= 0) return;
        float a1 = (float) (color1 >> 24 & 255);
        float r1 = (float) (color1 >> 16 & 255);
        float g1 = (float) (color1 >> 8 & 255);
        float b1 = (float) (color1 & 255);
        float a2 = (float) (color2 >> 24 & 255);
        float r2 = (float) (color2 >> 16 & 255);
        float g2 = (float) (color2 >> 8 & 255);
        float b2 = (float) (color2 & 255);
        withScissor(() -> {
            for (int i = 0; i < width; i++) {
                float t = (width == 1) ? 0.0f : (float) i / (float) (width - 1);
                int a = (int) (a1 * (1 - t) + a2 * t);
                int r = (int) (r1 * (1 - t) + r2 * t);
                int g = (int) (g1 * (1 - t) + g2 * t);
                int b = (int) (b1 * (1 - t) + b2 * t);
                int interpolatedColor = (a << 24) | (r << 16) | (g << 8) | b;
                graphics.fill(x1 + i, y1, x1 + i + 1, y2, interpolatedColor);
            }
        });
    }

    public void drawBufferedImage(BufferedImage image, float x, float y, float width, float height) {
        if (image == null) return;
        RematrixTextureHandle handle = textures.getTexture(image);
        if (handle == null) return;
        int dw = Math.max(1, (int) Math.ceil(width <= 0 ? handle.getWidth() : width));
        int dh = Math.max(1, (int) Math.ceil(height <= 0 ? handle.getHeight() : height));
        //#if MC >= 1.21.9
        withScissor(() -> graphics.blit(RenderPipelines.GUI_TEXTURED, (Identifier) handle.getId(), (int) Math.round(x), (int) Math.round(y), 0f, 0f, dw, dh, handle.getWidth(), handle.getHeight(), handle.getWidth(), handle.getHeight()));
        //#endif
        //#if MC >= 1.21.6 && MC < 1.21.9
        //$$ withScissor(() -> graphics.blit(RenderPipelines.GUI_TEXTURED, (ResourceLocation) handle.getId(), (int) Math.round(x), (int) Math.round(y), 0f, 0f, dw, dh, handle.getWidth(), handle.getHeight(), handle.getWidth(), handle.getHeight()));
        //#endif
        //#if MC >= 1.21.5 && MC < 1.21.6
        //$$ withScissor(() -> graphics.blit(RenderType::guiTextured, (ResourceLocation) handle.getId(), (int) Math.round(x), (int) Math.round(y), 0f, 0f, dw, dh, handle.getWidth(), handle.getHeight(), handle.getWidth(), handle.getHeight()));
        //#endif
        //#if MC >= 1.21.4 && MC < 1.21.5
        //$$ withScissor(() -> graphics.blit(RenderType::guiTextured, (ResourceLocation) handle.getId(), (int) Math.round(x), (int) Math.round(y), 0f, 0f, dw, dh, handle.getWidth(), handle.getHeight(), handle.getWidth(), handle.getHeight()));
        //#endif
        //#if MC >= 1.20.1 && MC < 1.21.4
        //$$ withScissor(() -> graphics.blit((ResourceLocation) handle.getId(), (int) Math.round(x), (int) Math.round(y), dw, dh, 0f, 0f, handle.getWidth(), handle.getHeight(), handle.getWidth(), handle.getHeight()));
        //#endif
        //#if MC < 1.20.1
        //$$ RenderSystem.enableBlend();
        //$$ RenderSystem.defaultBlendFunc();
        //$$ RenderSystem.setShader(GameRenderer::getPositionTexShader);
        //$$ RenderSystem.setShaderTexture(0, (ResourceLocation) handle.getId());
        //$$ Matrix4f matrix = graphics.pose().last().pose();
        //$$ Tesselator tesselator = Tesselator.getInstance();
        //$$ BufferBuilder builder = tesselator.getBuilder();
        //$$ builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        //$$ float minU = 0.0f;
        //$$ float minV = 0.0f;
        //$$ float maxU = 1.0f;
        //$$ float maxV = 1.0f;
        //$$ builder.vertex(matrix, x, y + dh, 0).uv(minU, maxV).endVertex();
        //$$ builder.vertex(matrix, x + dw, y + dh, 0).uv(maxU, maxV).endVertex();
        //$$ builder.vertex(matrix, x + dw, y, 0).uv(maxU, minV).endVertex();
        //$$ builder.vertex(matrix, x, y, 0).uv(minU, minV).endVertex();
        //$$ tesselator.end();
        //$$ RenderSystem.disableBlend();
        //#endif
    }

    public void drawInvertedRect(float x1, float y1, float x2, float y2) {
        if (x1 == x2 || y1 == y2) return;
        float minX = Math.min(x1, x2);
        float minY = Math.min(y1, y2);
        float maxX = Math.max(x1, x2);
        float maxY = Math.max(y1, y2);
        //#if MC >= 1.21.11
        withScissor(() -> graphics.textHighlight((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY), true));
        //#endif
        //#if MC >= 1.21.8 && MC < 1.21.11
        //$$ withScissor(() -> graphics.textHighlight((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY)));
        //#endif
        //#if MC >= 1.21.6 && MC < 1.21.8
        //$$ withScissor(() -> graphics.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, (int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY), SELECTION_COLOR));
        //#endif
        //#if MC < 1.21.6
        //$$ withScissor(() -> graphics.fill(RenderType.guiTextHighlight(), (int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY), SELECTION_COLOR));
        //#endif
        //#if MC < 1.20.1
        //$$ float alpha = ((SELECTION_COLOR >> 24) & 0xFF) / 255.0f;
        //$$ float red = 0.0f;
        //$$ float green = 0.0f;
        //$$ float blue = (SELECTION_COLOR & 0xFF) / 255.0f;
        //$$ RenderSystem.enableColorLogicOp();
        //$$ RenderSystem.logicOp(GL11.GL_OR_REVERSE);
        //$$ RenderSystem.disableBlend();
        //$$ RenderSystem.setShaderColor(red, green, blue, alpha);
        //$$ RenderSystem.setShader(GameRenderer::getPositionColorShader);
        //$$ Matrix4f matrix = graphics.pose().last().pose();
        //$$ Tesselator tesselator = Tesselator.getInstance();
        //$$ BufferBuilder builder = tesselator.getBuilder();
        //$$ builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        //$$ builder.vertex(matrix, minX, minY, 0).color(red, green, blue, alpha).endVertex();
        //$$ builder.vertex(matrix, maxX, minY, 0).color(red, green, blue, alpha).endVertex();
        //$$ builder.vertex(matrix, maxX, maxY, 0).color(red, green, blue, alpha).endVertex();
        //$$ builder.vertex(matrix, minX, maxY, 0).color(red, green, blue, alpha).endVertex();
        //$$ tesselator.end();
        //$$ RenderSystem.disableColorLogicOp();
        //$$ RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //#endif
    }

    private static final class McMatrixStack implements RematrixMatrixStack {
        //#if MC >= 1.21.6
        private final org.joml.Matrix3x2fStack pose;

        private McMatrixStack(org.joml.Matrix3x2fStack pose) {
            this.pose = pose;
        }
        //#endif

        //#if MC < 1.21.6
        //$$ private final PoseStack pose;
        //$$
        //$$ private McMatrixStack(PoseStack pose) {
        //$$     this.pose = pose;
        //$$ }
        //#endif

        @Override
        public void push() {
            //#if MC >= 1.21.6
            pose.pushMatrix();
            //#endif
            //#if MC < 1.21.6
            //$$ pose.pushPose();
            //#endif
        }

        @Override
        public void pop() {
            //#if MC >= 1.21.6
            pose.popMatrix();
            //#endif
            //#if MC < 1.21.6
            //$$ pose.popPose();
            //#endif
        }

        @Override
        public void translate(float x, float y, float z) {
            //#if MC >= 1.21.6
            pose.translate(x, y);
            //#endif
            //#if MC < 1.21.6
            //$$ pose.translate(x, y, z);
            //#endif
        }

        @Override
        public void scale(float x, float y, float z) {
            //#if MC >= 1.21.6
            pose.scale(x, y);
            //#endif
            //#if MC < 1.21.6
            //$$ pose.scale(x, y, z);
            //#endif
        }

        @Override
        public void rotate(float angle, float x, float y, float z) {
            //#if MC >= 1.21.6
            pose.rotate(angle);
            //#endif
            //#if MC < 1.21.6
            //$$ pose.mulPose(new Quaternionf().fromAxisAngleDeg(x, y, z, angle));
            //#endif
        }

        @Override
        public void multiply(float angle) {
            //#if MC >= 1.21.6
            pose.rotate(angle);
            //#endif
            //#if MC < 1.21.6
            //$$ pose.mulPose(new Quaternionf().fromAxisAngleDeg(0f, 0f, 1f, angle));
            //#endif
        }
    }

    private final class McScissorStack implements RematrixScissorStack {
        private final Deque<float[]> stack = new ArrayDeque<>();
        private final Deque<List<float[]>> stateStack = new ArrayDeque<>();

        @Override
        public void pushState() {
            List<float[]> snapshot = new ArrayList<>(stack.size());
            for (float[] entry : stack) {
                snapshot.add(new float[]{entry[0], entry[1], entry[2], entry[3]});
            }
            stateStack.push(snapshot);
        }

        @Override
        public void popState() {
            if (stateStack.isEmpty()) return;
            List<float[]> snapshot = stateStack.pop();
            clear();
            for (float[] entry : snapshot) {
                stack.addLast(new float[]{entry[0], entry[1], entry[2], entry[3]});
            }
        }

        @Override
        public void clear() {
            stack.clear();
        }

        @Override
        public void enable(float x1, float y1, float x2, float y2) {
            float reqX = x1;
            float reqY = y1;
            float reqW = x2 - x1;
            float reqH = y2 - y1;

            if (!stack.isEmpty()) {
                float[] parent = stack.peek();
                float parentX = parent[0];
                float parentY = parent[1];
                float parentW = parent[2];
                float parentH = parent[3];

                float intersectX1 = Math.max(reqX, parentX);
                float intersectY1 = Math.max(reqY, parentY);
                float intersectX2 = Math.min(reqX + reqW, parentX + parentW);
                float intersectY2 = Math.min(reqY + reqH, parentY + parentH);

                reqX = intersectX1;
                reqY = intersectY1;
                reqW = Math.max(0, intersectX2 - intersectX1);
                reqH = Math.max(0, intersectY2 - intersectY1);
            }

            stack.push(new float[]{reqX, reqY, reqW, reqH});
        }

        @Override
        public void disable() {
            if (stack.isEmpty()) return;
            stack.pop();
        }

        @Override
        public boolean contains(int x, int y) {
            if (stack.isEmpty()) return true;
            float[] entry = stack.peek();
            return x >= entry[0] && x < entry[0] + entry[2] && y >= entry[1] && y < entry[1] + entry[3];
        }

        @Override
        public ScissorBox getCurrent() {
            if (stack.isEmpty()) return null;
            float[] entry = stack.peek();
            return new ScissorBox((int) entry[0], (int) entry[1], (int) entry[2], (int) entry[3]);
        }

        private float[] getCurrentRaw() {
            if (stack.isEmpty()) return null;
            return stack.peek();
        }
    }

    private final class McTextureCache implements RematrixTextureCache {
        @Override
        public RematrixTextureHandle getTexture(BufferedImage image) {
            RematrixTextureHandle existing = TEXTURE_CACHE.get(image);
            if (existing != null) return existing;
            synchronized (TEXTURE_CACHE) {
                RematrixTextureHandle again = TEXTURE_CACHE.get(image);
                if (again != null) return again;
                int width = image.getWidth();
                int height = image.getHeight();
                NativeImage nativeImage = new NativeImage(width, height, true);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = image.getRGB(x, y);
                        int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
                        //#if MC >= 1.21.5
                        nativeImage.setPixelABGR(x, y, abgr);
                        //#endif
                        //#if MC >= 1.21.4 && MC < 1.21.5
                        //$$ nativeImage.setPixel(x, y, argb);
                        //#endif
                        //#if MC < 1.21.4
                        //$$ nativeImage.setPixelRGBA(x, y, abgr);
                        //#endif
                    }
                }
                //#if MC >= 1.21.5
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "rematrix", nativeImage);
                //#endif
                //#if MC < 1.21.5
                //$$ DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                //#endif
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                //#if MC >= 1.21.11
                Identifier id = Identifier.fromNamespaceAndPath("rematrix", "img_" + System.identityHashCode(image));
                textureManager.register(id, dynamicTexture);
                //#endif
                //#if MC < 1.21.11 && MC >= 1.21.1
                //$$ ResourceLocation id = ResourceLocation.fromNamespaceAndPath("rematrix", "img_" + System.identityHashCode(image));
                //$$ textureManager.register(id, dynamicTexture);
                //#endif
                //#if MC < 1.21.1
                //$$ ResourceLocation id = new ResourceLocation("rematrix", "img_" + System.identityHashCode(image));
                //$$ textureManager.register(id, dynamicTexture);
                //#endif
                RematrixTextureHandle handle = new RematrixTextureHandle(id, width, height);
                TEXTURE_CACHE.put(image, handle);
                return handle;
            }
        }

        @Override
        public void clear() {
            TEXTURE_CACHE.clear();
        }
    }

    private final class McTextBridge implements RematrixTextBridge {
        @Override
        public int getWidth(String text) {
            return Minecraft.getInstance().font.width(text);
        }

        @Override
        public int getWidth(String text, Object font) {
            //#if MC >= 1.21.11
            if (font instanceof Identifier rl) {
            //#endif
            //#if MC < 1.21.11
            //$$ if (font instanceof ResourceLocation rl) {
            //#endif
                MutableComponent component = Component.literal(text);
                //#if MC >= 1.21.9
                component.setStyle(Style.EMPTY.withFont(new FontDescription.Resource(rl)));
                //#endif
                //#if MC < 1.21.9
                //$$ component.setStyle(Style.EMPTY.withFont(rl));
                //#endif
                return Minecraft.getInstance().font.width(component);
            }
            return getWidth(text);
        }

        @Override
        public String trimToWidth(String text, int maxWidth) {
            int textWidth = getWidth(text);
            if (textWidth <= maxWidth) {
                return text;
            }
            StringBuilder trimmed = new StringBuilder();
            for (char c : text.toCharArray()) {
                trimmed.append(c);
                if (getWidth(trimmed.toString()) > maxWidth) {
                    trimmed.deleteCharAt(trimmed.length() - 1);
                    break;
                }
            }
            return trimmed.toString();
        }
    }

    private void withScissor(Runnable draw) {
        float[] scissor = ((McScissorStack) scissors).getCurrentRaw();
        if (scissor == null) {
            draw.run();
            return;
        }
        if (applyScissor(scissor)) {
            draw.run();
            graphics.disableScissor();
            return;
        }
        draw.run();
    }

    private boolean applyScissor(float[] scissor) {
        //#if MC >= 1.20.1
        float x1 = scissor[0] * scissorScale;
        float y1 = scissor[1] * scissorScale;
        float x2 = (scissor[0] + scissor[2]) * scissorScale;
        float y2 = (scissor[1] + scissor[3]) * scissorScale;
        int ix = (int) Math.floor(x1);
        int iy = (int) Math.floor(y1);
        int iw = Math.max(0, (int) Math.ceil(x2 - x1));
        int ih = Math.max(0, (int) Math.ceil(y2 - y1));
        //#if MC >= 1.21.6
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.identity();
        graphics.enableScissor(ix, iy, ix + iw, iy + ih);
        pose.popMatrix();
        //#endif
        //#if MC < 1.21.6
        //$$ graphics.pose().pushPose();
        //$$ graphics.pose().last().pose().identity();
        //$$ graphics.enableScissor(ix, iy, ix + iw, iy + ih);
        //$$ graphics.pose().popPose();
        //#endif
        return true;
        //#endif
        //#if MC < 1.20.1
        //$$ return false;
        //#endif
    }


    //#if MC < 1.20.1
    //$$ private void applyScissor(int x, int y, int width, int height) {
    //$$     int windowHeight = Minecraft.getInstance().getWindow().getHeight();
    //$$     int scaledX = (int) (x * scissorScale);
    //$$     int scaledY = (int) (y * scissorScale);
    //$$     int scaledWidth = (int) (width * scissorScale);
    //$$     int scaledHeight = (int) (height * scissorScale);
    //$$     RenderSystem.enableScissor(scaledX, windowHeight - (scaledY + scaledHeight), scaledWidth, scaledHeight);
    //$$ }
    //#endif
}
