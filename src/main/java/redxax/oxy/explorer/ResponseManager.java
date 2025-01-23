package redxax.oxy.explorer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.config.Config;

import java.util.ArrayList;
import java.util.List;

import static redxax.oxy.Render.*;
import static redxax.oxy.config.Config.*;

public class ResponseManager {

    public static String parseAIResponse(String jsonResponse) {
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (jsonObject.has("candidates") && jsonObject.getAsJsonArray("candidates").size() > 0) {
                JsonObject firstCandidate = jsonObject.getAsJsonArray("candidates").get(0).getAsJsonObject();
                if (firstCandidate.has("content") && firstCandidate.getAsJsonObject("content").has("parts")) {
                    JsonObject content = firstCandidate.getAsJsonObject("content");
                    if (content.getAsJsonArray("parts").size() > 0) {
                        String generatedText = content.getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();
                        return generatedText.replace("\\n", "\n").replace("\\\"", "\"");
                    }
                }
            }
            return "AI response parsing failed: Invalid JSON structure";
        } catch (Exception e) {
            return "AI response parsing failed: " + e.getMessage();
        }
    }
    static class ResponseWindow {

        int x;
        int y;
        int width;
        int height;
        String text;
        boolean dragging;
        int dragOffsetX;
        int dragOffsetY;
        boolean closed;

        ResponseWindow(int x, int y, String text, int w) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.width = w;
            this.height = 50;
        }

        void render(DrawContext context, int mouseX, int mouseY, float delta, MinecraftClient minecraftClient) {
            int wrapWidth = width - 10;
            List<String> lines = wrapText(text, wrapWidth, minecraftClient);
            int lineHeight = minecraftClient.textRenderer.fontHeight + 2;
            height = Math.max(30, (lines.size() * lineHeight) + 10);
            context.fill(x, y, x + width, y + height, airBarBackgroundColor);
            drawInnerBorder(context, x, y, width, height, airBarBorderColor);
            drawOuterBorder(context, x, y, width, height, globalBottomBorder);
            int drawY = y + 5;
            for (String l : lines) {
                context.drawText(minecraftClient.textRenderer, Text.literal(l), x + 5, drawY, 0xFFFFFF, Config.shadow);
                drawY += lineHeight;
            }
            int closeX = x + width - 10;
            int closeY = y;
            context.drawText(minecraftClient.textRenderer, Text.literal("x"), closeX, closeY, buttonTextDeleteHoverColor, false);
        }

        boolean mouseClicked(double mx, double my, int button) {
            if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
                if (mx >= x + width - 10 && mx <= x + width && my >= y && my <= y + 10) {
                    closed = true;
                    return true;
                }
                dragging = true;
                dragOffsetX = (int) (mx - x);
                dragOffsetY = (int) (my - y);
                return true;
            }
            return false;
        }

        boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return false;
        }

        boolean mouseDragged(double mx, double my, int button) {
            if (dragging) {
                x = (int) mx - dragOffsetX;
                y = (int) my - dragOffsetY;
                return true;
            }
            return false;
        }

        private List<String> wrapText(String text, int wrapWidth, MinecraftClient minecraftClient) {
            List<String> result = new ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                int lineWidth = minecraftClient.textRenderer.getWidth(currentLine + word + " ");
                if (lineWidth > wrapWidth) {
                    result.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                currentLine.append(word).append(" ");
            }
            if (currentLine.length() > 0) {
                result.add(currentLine.toString());
            }
            return result;
        }
    }

    static class Position {
        int line;
        int start;
        int end;
        Position(int line, int start, int end) {
            this.line = line;
            this.start = start;
            this.end = end;
        }
    }
}
