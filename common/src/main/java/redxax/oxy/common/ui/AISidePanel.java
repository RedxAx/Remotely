package redxax.oxy.common.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static redxax.oxy.common.config.Config.*;
import static redxax.oxy.common.util.DevUtil.devPrint;
import static redxax.oxy.common.util.ImageUtil.loadResourceIcon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.common.Render;
import redxax.oxy.common.config.Config;
import redxax.oxy.common.explorer.ResponseManager;
import redxax.oxy.common.explorer.SyntaxHighlighter;

public class AISidePanel {

    private int panelY;
    private int panelWidth;
    private int panelX;

    public static class AIMessage {
        public String sender;
        public String text;
        public float animationProgress;
        public AIMessage(String sender, String text) {
            this.sender = sender;
            this.text = text;
            this.animationProgress = 0f;
        }
    }

    private List<AIMessage> messages;
    private StringBuilder inputBuffer;
    private int inputCursor;
    private int scrollOffset;
    private long lastBlinkTime;
    private boolean showCursor;
    private String extraContext;
    private MinecraftClient mc;
    private static final Path AI_CONFIG_PATH = Path.of(remotelyDir.toString(), "data", "ai.json");
    private int topBarHeight = 30;
    public boolean fieldFocused = false;
    boolean inputHovered = false;
    BufferedImage newChatIcon, deleteChatIcon, chatHistoryIcon;
    private float targetScrollOffset = 0;
    private float currentScrollOffset = 0;

    public AISidePanel() {
        this.messages = new ArrayList<>();
        this.inputBuffer = new StringBuilder();
        this.inputCursor = 0;
        this.scrollOffset = 0;
        this.lastBlinkTime = System.currentTimeMillis();
        this.showCursor = true;
        this.extraContext = "";
        this.mc = MinecraftClient.getInstance();
        try {
            newChatIcon = loadResourceIcon("/assets/remotely/icons/newchat.png");
            deleteChatIcon = loadResourceIcon("/assets/remotely/icons/deletechat.png");
            chatHistoryIcon = loadResourceIcon("/assets/remotely/icons/history.png");
        } catch (Exception e) {
            devPrint("Failed to load icons: " + e.getMessage());
        }
    }

    public void setExtraContext(String context) {
        this.extraContext = context;
    }

    public void addUserMessage(String msg) {
        messages.add(new AIMessage("user", msg));
    }

    public void addAIMessage(String msg) {
        messages.add(new AIMessage("ai", msg));
    }

    public void setErrorMessage(String errMsg) {
        messages.add(new AIMessage("error", errMsg));
    }

    public void deleteLastMessage() {
        if (!messages.isEmpty())
            messages.remove(messages.size() - 1);
    }

    public void copyLastMessage() {
        if (!messages.isEmpty()) {
            AIMessage last = messages.get(messages.size() - 1);
            mc.keyboard.setClipboard(last.text);
        }
    }

    public void newChat() {
        messages.clear();
        scrollOffset = 0;
    }

    private JsonObject readAIConfig() {
        JsonObject config = new JsonObject();
        try {
            if (Files.exists(AI_CONFIG_PATH)) {
                try (FileReader reader = new FileReader(AI_CONFIG_PATH.toFile())) {
                    config = JsonParser.parseReader(reader).getAsJsonObject();
                }
            } else {
                config.addProperty("entryPoint", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent");
                config.addProperty("apiToken", "your-api-token");
                try (FileWriter writer = new FileWriter(AI_CONFIG_PATH.toFile())) {
                    writer.write(config.toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public void sendMessage() {
        String userMsg = inputBuffer.toString().trim();
        if (userMsg.isEmpty())
            return;
        addUserMessage(userMsg);
        StringBuilder builder = new StringBuilder();
        if (!extraContext.isEmpty()) {
            builder.append("The Context Is: ").append(extraContext).append("\n");
            builder.append("The User Name Is: ").append(MinecraftClient.getInstance().getSession().getUsername()).append("\n");
            builder.append("The Current Date Is: ").append(new SimpleDateFormat("dd/MM/yyyy").format(new Date())).append("\n");
            builder.append("The Current Time Is: ").append(new SimpleDateFormat("HH:mm:ss").format(new Date())).append("\n");
            builder.append(extraContext).append("\n");
        }
        for (AIMessage m : messages) {
            builder.append(m.sender).append(": ").append(m.text).append("\n");
        }
        String contextData = builder.toString();
        JsonObject requestBodyJson = new JsonObject();
        JsonArray contentsArray = new JsonArray();
        JsonObject partObject1 = new JsonObject();
        partObject1.addProperty("text", contextData);
        JsonObject contentObject = new JsonObject();
        JsonArray partsArray = new JsonArray();
        partsArray.add(partObject1);
        contentObject.add("parts", partsArray);
        contentsArray.add(contentObject);
        requestBodyJson.add("contents", contentsArray);
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("response_mime_type", "text/plain");
        requestBodyJson.add("generation_config", generationConfig);
        JsonObject aiConfig = readAIConfig();
        String entryPoint = aiConfig.has("entryPoint") ? aiConfig.get("entryPoint").getAsString() : "";
        String apiToken = aiConfig.has("apiToken") ? aiConfig.get("apiToken").getAsString() : "";
        if (apiToken.isEmpty() || apiToken.contains("your-api-token")) {
            setErrorMessage("Missing valid API token in ai.json");
            inputBuffer.setLength(0);
            inputCursor = 0;
            return;
        }
        String requestBody = requestBodyJson.toString();
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(entryPoint + "?key=" + apiToken);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine.trim());
                    }
                    in.close();
                    String aiResponse = ResponseManager.parseAIResponse(response.toString());
                    mc.execute(() -> addAIMessage(aiResponse));
                } else {
                    mc.execute(() -> setErrorMessage("AI request failed with code: " + responseCode));
                }
            } catch (Exception e) {
                mc.execute(() -> setErrorMessage("AI request error: " + e.getMessage()));
            }
        });
        inputBuffer.setLength(0);
        inputCursor = 0;
    }

    public void render(DrawContext context, int panelX, int panelY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        this.panelWidth = panelWidth;
        this.panelY = panelY;
        this.panelX = panelX;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Config.innerBackgroundColor);
        Render.drawInnerBorder(context, panelX, panelY, panelWidth, panelHeight, Config.innerBorderColor);
        Render.drawOuterBorder(context, panelX, panelY, panelWidth, panelHeight, Config.globalOuterBorder);
        renderTopBar(context, panelX, panelY, panelWidth, mouseX, mouseY);
        int msgAreaY = panelY + topBarHeight;
        int msgAreaHeight = panelHeight - topBarHeight - 35;
        int totalHeight = getTotalChatHeight(panelWidth - 10, mc.textRenderer);
        int maxScroll = Math.max(0, totalHeight - msgAreaHeight);
        int threshold = (mc.textRenderer.fontHeight + 2) * 2;
        if (targetScrollOffset >= maxScroll - threshold) {
            targetScrollOffset = maxScroll;
        }
        context.enableScissor(panelX, msgAreaY, panelX + panelWidth, msgAreaY + msgAreaHeight);

        currentScrollOffset += (targetScrollOffset - currentScrollOffset) * globalScrollSpeed * deltaTime;
        int msgY = msgAreaY + 5 - (int)currentScrollOffset;

        TextRenderer tr = mc.textRenderer;
        for (AIMessage msg : messages) {
            if (msg.animationProgress < 1f) {
                msg.animationProgress += 0.05f;
                if (msg.animationProgress > 1f)
                    msg.animationProgress = 1f;
            }
            int msgColor = msg.sender.equals("user") ? 0xFFAAAAFF : msg.sender.equals("ai") ? 0xFFAAFFAA : 0xFFFFAAAA;
            List<String> wrapped = wrapText(msg.text, panelWidth - 10, tr);
            for (String line : wrapped) {
                if (line.startsWith("```")) {
                    continue;
                }
                if (line.startsWith("CODE:")) {
                    Text codeText = SyntaxHighlighter.highlight(line.substring(5), "");
                    context.drawText(tr, codeText, panelX + 5, msgY, msgColor, Config.shadow);
                } else {
                    context.drawText(tr, Text.literal(line), panelX + 5, msgY, msgColor, Config.shadow);
                }
                msgY += tr.fontHeight + 2;
            }
            msgY += 5;
        }
        context.disableScissor();
        inputHovered = (mouseX >= panelX + 5 && mouseX < panelX + panelWidth - 5 && mouseY >= panelY + panelHeight - 35 && mouseY < panelY + panelHeight - 10);
        context.fill(panelX, panelY + panelHeight - 35, panelX + panelWidth, panelY + panelHeight, Config.innerBackgroundColor);
        Render.drawInnerBorder(context, panelX, panelY + panelHeight - 35, panelWidth, 35, Config.innerBorderColor);
        Render.drawOuterBorder(context, panelX, panelY + panelHeight - 35, panelWidth, 35, Config.globalOuterBorder);
        Render.drawTextInput(context, mc, panelX + 5, panelY + panelHeight - 30, "AI Input", inputBuffer.toString(), fieldFocused, inputCursor, -1, -1, inputHovered, panelWidth - 10, 20);
    }

    private void renderTopBar(DrawContext context, int panelX, int panelY, int panelWidth, int mouseX, int mouseY) {
        int buttonSize = 20;
        int gap = 4;
        int barHeight = buttonSize + 2 * gap;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + barHeight, Config.innerBackgroundColor);
        Render.drawInnerBorder(context, panelX, panelY, panelWidth, barHeight, Config.innerBorderColor);
        Render.drawOuterBorder(context, panelX, panelY, panelWidth, barHeight, Config.globalOuterBorder);
        int xHistory = panelX + panelWidth - gap - buttonSize;
        int xDelete = xHistory - gap - buttonSize;
        int xNew = xDelete - gap - buttonSize;
        int yButton = panelY + gap;
        boolean hoverHistory = (mouseX >= xHistory && mouseX < xHistory + buttonSize && mouseY >= panelY && mouseY < panelY + barHeight);
        boolean hoverDelete = (mouseX >= xDelete && mouseX < xDelete + buttonSize && mouseY >= panelY && mouseY < panelY + barHeight);
        boolean hoverNew = (mouseX >= xNew && mouseX < xNew + buttonSize && mouseY >= panelY && mouseY < panelY + barHeight);
        Render.drawSquareButton(context, xNew, yButton, mc, hoverNew, mouseX, mouseY, "New Chat", newChatIcon);
        Render.drawSquareButton(context, xDelete, yButton, mc, hoverDelete, mouseX, mouseY, "Delete Chat", deleteChatIcon);
        Render.drawSquareButton(context, xHistory, yButton, mc, hoverHistory, mouseX, mouseY, "Chat History", chatHistoryIcon);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int buttonSize = 20;
        int gap = 4;
        int barHeight = buttonSize + 2 * gap;
        if (inputHovered) {
            fieldFocused = true;
            return true;
        } else {
            fieldFocused = false;
        }
        if (mouseY >= panelY && mouseY < panelY + barHeight) {
            int xHistory = panelX + panelWidth - gap - buttonSize;
            int xDelete = xHistory - gap - buttonSize;
            int xNew = xDelete - gap - buttonSize;
            if (mouseX >= xNew && mouseX < xNew + buttonSize) {
                newChat();
                return true;
            }
            if (mouseX >= xDelete && mouseX < xDelete + buttonSize) {
                deleteLastMessage();
                return true;
            }
            if (mouseX >= xHistory && mouseX < xHistory + buttonSize) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, int panelX, int panelY, int panelWidth, int panelHeight) {
        int msgAreaY = panelY + topBarHeight;
        int msgAreaHeight = panelHeight - topBarHeight - 35;
        if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= msgAreaY && mouseY < msgAreaY + msgAreaHeight) {
            int totalHeight = getTotalChatHeight(panelWidth - 10, mc.textRenderer);
            targetScrollOffset -= (int)(verticalAmount * mc.textRenderer.fontHeight * 3);
            if (targetScrollOffset < 0)
                targetScrollOffset = 0;
            if (targetScrollOffset > totalHeight - msgAreaHeight)
                targetScrollOffset = totalHeight - msgAreaHeight;
            return true;
        }
        return false;
    }

    private int getTotalChatHeight(int availableWidth, TextRenderer tr) {
        int total = 0;
        for (AIMessage msg : messages) {
            List<String> wrapped = wrapText(msg.text, availableWidth, tr);
            total += wrapped.size() * (tr.fontHeight + 2) + 5;
        }
        return total;
    }

    private List<String> wrapText(String text, int maxWidth, TextRenderer tr) {
        List<String> result = new ArrayList<>();
        String[] originalLines = text.split("\n");
        for (String orig : originalLines) {
            if (tr.getWidth(orig) <= maxWidth) {
                result.add(orig);
            } else {
                String[] words = orig.split(" ");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    if (tr.getWidth(line + word + " ") > maxWidth && !line.isEmpty()) {
                        result.add(line.toString());
                        line = new StringBuilder();
                    }
                    if (!line.isEmpty())
                        line.append(" ");
                    line.append(word);
                }
                if (!line.isEmpty())
                    result.add(line.toString());
            }
        }
        return result;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendMessage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (inputCursor > 0) {
                inputBuffer.deleteCharAt(inputCursor - 1);
                inputCursor--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (inputCursor > 0)
                inputCursor--;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (inputCursor < inputBuffer.length())
                inputCursor++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int keyCode) {
        if (chr >= 32 && chr != 127) {
            inputBuffer.insert(inputCursor, chr);
            inputCursor++;
        }
        return true;
    }
}

