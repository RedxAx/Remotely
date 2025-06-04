package redxax.oxy.remotely.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redxax.oxy.remotely.render.MarkdownRenderer;
import org.lwjgl.glfw.GLFW;
import redxax.oxy.remotely.Render;
import redxax.oxy.remotely.config.Config;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import redxax.oxy.remotely.util.Sound;

import static redxax.oxy.remotely.Render.*;
import static redxax.oxy.remotely.config.Config.*;
import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.ImageUtil.drawPixelArt;
import static redxax.oxy.remotely.util.ImageUtil.loadResourceIcon;
import static redxax.oxy.remotely.util.SoundUtils.playSound;

public class AISidePanel {

    private int panelY;
    private int panelWidth;
    private int panelX;
    private int panelHeight;

    public static class AIMessage {
        public String sender;
        public String text;
        public float animationProgress;
        public MarkdownRenderer markdown;

        public AIMessage(String sender, String text) {
            this.sender = sender;
            this.text = text;
            this.animationProgress = 0f;
            if ("ai".equals(sender)) {
                try {
                    this.markdown = new MarkdownRenderer(text);
                } catch (Exception e) {
                    this.markdown = null;
                }
            } else {
                this.markdown = null;
            }
        }
    }

    private final List<AIMessage> messages;
    private final StringBuilder inputBuffer;
    private int inputCursor;
    private String extraContext;
    private final MinecraftClient mc;
    private static final Path AI_CONFIG_PATH = Path.of(remotelyDir.toString(), "data", "ai.json");
    private final int topBarHeight = 30;
    public boolean fieldFocused = false;
    boolean inputHovered = false;
    BufferedImage newChatIcon, deleteChatIcon, chatHistoryIcon, RemotelyAIcon;
    private float targetScrollOffset = 0;
    private float currentScrollOffset = 0;
    private static final Path CHAT_HISTORY_PATH = Path.of(remotelyDir.toString(), "data", "chat_history.json");
    private MarkdownRenderer markdownPanel;
    private String lastPanelMarkdownText;
    private boolean hasValidToken = true;

    public AISidePanel() {
        this.messages = new ArrayList<>();
        this.inputBuffer = new StringBuilder();
        this.inputCursor = 0;
        this.extraContext = "";
        this.mc = MinecraftClient.getInstance();
        try {
            newChatIcon = loadResourceIcon("/assets/remotely/icons/newchat.png");
            deleteChatIcon = loadResourceIcon("/assets/remotely/icons/delete.png");
            chatHistoryIcon = loadResourceIcon("/assets/remotely/icons/history.png");
            RemotelyAIcon = loadResourceIcon("/assets/remotely/icons/ReemotelyAI.png");
        } catch (Exception e) {
            devPrint("Failed to load icons: " + e.getMessage());
        }
        loadLatestChatHistory();
        checkTokenValidity();
    }

    private void checkTokenValidity() {
        JsonObject aiConfig = readAIConfig();
        String apiToken = aiConfig.has("apiToken") ? aiConfig.get("apiToken").getAsString() : "";
        hasValidToken = !(apiToken.isEmpty() || apiToken.contains("your-api-token"));
        if (!hasValidToken) {
            setErrorMessage("Please enter your Gemini API token in the input field below");
        }
    }

    private void updateApiToken(String token) {
        try {
            JsonObject config = readAIConfig();
            config.addProperty("apiToken", token);
            try (FileWriter writer = new FileWriter(AI_CONFIG_PATH.toFile())) {
                writer.write(config.toString());
            }
            hasValidToken = true;
            inputBuffer.setLength(0);
            inputCursor = 0;
            setErrorMessage("API token updated successfully! You can now chat with Remotely AI.");
        } catch (IOException e) {
            setErrorMessage("Failed to update API token: " + e.getMessage());
        }
    }

    public void setExtraContext(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hey Remotely, Here Is Some Handy Context: \n");
        if (mc.getSession().getUsername().equalsIgnoreCase("RedxAx")) sb.append("The User Is RedxAx. Your Creator And Programmer Of The Remotely Mod.");
        else sb.append("The User's Name is ").append(mc.getSession().getUsername()).append(". \n");
        sb.append("(Only For You To Know) The User Language (WHICH YOU MUST USE UNLESS THE USER ASKS NOT TO) is: ").append(mc.getLanguageManager().getLanguage()).append(". \n");
        sb.append("(Only For You To Know) The User's Operating System is: ").append(System.getProperty("os.name")).append(". \n");
        sb.append("The Current User's Context is: \n");
        for (String line : context.split("\n")) {
            sb.append(line).append("\n");
        }
        this.extraContext = sb.toString();
        updatePanelMarkdown();
    }

    public void addUserMessage(String msg) {
        messages.add(new AIMessage("user", msg));
        playSound(Sound.SEND);
        updateCurrentChatHistory();
    }

    public void setErrorMessage(String errMsg) {
        messages.add(new AIMessage("error", errMsg));
        playSound(Sound.RECEIVEERROR);
        updateCurrentChatHistory();
    }
    public void newChat() {
        updateCurrentChatHistory();
        try {
            JsonArray history;
            if (Files.exists(CHAT_HISTORY_PATH)) {
                String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
                history = JsonParser.parseString(content).getAsJsonArray();
            } else {
                history = new JsonArray();
            }
            history.add(new JsonArray());
            Files.writeString(CHAT_HISTORY_PATH, history.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            devPrint("Failed to create new chat: " + e.getMessage());
        }
        messages.clear();
        updatePanelMarkdown();
        targetScrollOffset = 0;
        currentScrollOffset = 0;
    }

    private JsonObject readAIConfig() {
        JsonObject config = new JsonObject();
        try {
            if (Files.exists(AI_CONFIG_PATH)) {
                try (FileReader reader = new FileReader(AI_CONFIG_PATH.toFile())) {
                    config = JsonParser.parseReader(reader).getAsJsonObject();
                }
            } else {
                config.addProperty("entryPoint", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse");
                config.addProperty("apiToken", "your-api-token");
                try (FileWriter writer = new FileWriter(AI_CONFIG_PATH.toFile())) {
                    writer.write(config.toString());
                }
            }
        } catch (IOException e) {
            devPrint("Failed to read AI config: " + e.getMessage());
        }
        return config;
    }

    public void sendMessage() {
        String userMsg = inputBuffer.toString().trim();
        if (userMsg.isEmpty())
            return;

        if (!hasValidToken && userMsg.length() > 20) {
            updateApiToken(userMsg);
            return;
        }

        addUserMessage(userMsg);
        JsonObject requestBodyJson = new JsonObject();
        JsonObject systemInstruction = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysPart = new JsonObject();
        String systemPrompt = "You're Remotely AI. A Chat Bot That Helps Minecraft Server Admins With Their Terminal / Files That May Relate To Minecraft Development. You Provide Short And To The Point Answers In a Human Friendly / Non-Robot Way. You're a Part Of a Minecraft Mod (Called Remotely) That Contains An In-Game MultiTerminal, File Explorer, File Editor, Server Manager, Etc. You Can And Should Use Markdown Rendering, And You Can Also Use Plain Text As HTML Rendering.";
        if (extraContext != null && !extraContext.isEmpty()) {
            systemPrompt += "\n\nAdditional context: " + extraContext;
        }
        sysPart.addProperty("text", systemPrompt);
        sysParts.add(sysPart);
        systemInstruction.add("parts", sysParts);
        requestBodyJson.add("system_instruction", systemInstruction);
        JsonArray contentsArray = new JsonArray();
        for (AIMessage m : messages) {
            if ("error".equals(m.sender))
                continue;
            JsonObject messageObj = new JsonObject();
            if ("user".equals(m.sender)) {
                messageObj.addProperty("role", "user");
            } else if ("ai".equals(m.sender)) {
                messageObj.addProperty("role", "model");
            }
            JsonArray partsArray = new JsonArray();
            JsonObject partObj = new JsonObject();
            partObj.addProperty("text", m.text);
            partsArray.add(partObj);
            messageObj.add("parts", partsArray);
            contentsArray.add(messageObj);
        }
        requestBodyJson.add("contents", contentsArray);
        JsonArray toolsArray = new JsonArray();
        JsonObject toolObj = new JsonObject();
        toolObj.add("google_search", new JsonObject());
        toolsArray.add(toolObj);
        requestBodyJson.add("tools", toolsArray);
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("response_mime_type", "text/plain");
        requestBodyJson.add("generation_config", generationConfig);
        JsonObject aiConfig = readAIConfig();
        String apiToken = aiConfig.has("apiToken") ? aiConfig.get("apiToken").getAsString() : "";
        if (apiToken.isEmpty() || apiToken.contains("your-api-token")) {
            hasValidToken = false;
            setErrorMessage("Please enter your Gemini API token in the input field below");
            inputBuffer.setLength(0);
            inputCursor = 0;
            return;
        }
        hasValidToken = true;
        String entryPoint = aiConfig.has("entryPoint") ? aiConfig.get("entryPoint").getAsString() : "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse";
        String urlWithKey = entryPoint + (entryPoint.contains("?") ? "&" : "?") + "key=" + apiToken;
        String requestBody = requestBodyJson.toString();
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(urlWithKey);
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
                    playSound(Sound.RECEIVE);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    AIMessage aiMessage = new AIMessage("ai", "");
                    mc.execute(() -> {
                        messages.add(aiMessage);
                        updateCurrentChatHistory();
                    });
                    String line;
                    StringBuilder accumulatedResponse = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String dataPart = line.substring(5).trim();
                            if ("[DONE]".equals(dataPart)) {
                                break;
                            }
                            JsonObject jsonChunk = JsonParser.parseString(dataPart).getAsJsonObject();
                            if (jsonChunk.has("candidates")) {
                                JsonArray candidates = jsonChunk.getAsJsonArray("candidates");
                                if (!candidates.isEmpty()) {
                                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                                    if (candidate.has("content")) {
                                        JsonObject content = candidate.getAsJsonObject("content");
                                        JsonArray parts = content.getAsJsonArray("parts");
                                        if (!parts.isEmpty()) {
                                            JsonObject textPart = parts.get(0).getAsJsonObject();
                                            String chunkText = textPart.get("text").getAsString();
                                            accumulatedResponse.append(chunkText);
                                            mc.execute(() -> {
                                                aiMessage.text = accumulatedResponse.toString();
                                                updateCurrentChatHistory();
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    }
                    reader.close();
                } else {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine.trim());
                    }
                    errorReader.close();
                    mc.execute(() -> setErrorMessage("AI request failed with code: " + responseCode + " response: " + errorResponse));
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
        this.panelHeight = panelHeight;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, Config.innerBackgroundColor);
        drawInnerBorder(context, panelX, panelY, panelWidth, panelHeight, Config.innerBorderColor);
        Render.drawOuterBorder(context, panelX, panelY, panelWidth, panelHeight, innerBackgroundColor);
        renderTopBar(context, panelX, panelY, panelWidth, mouseX, mouseY);
        int msgAreaY = panelY + topBarHeight;
        int msgAreaHeight = panelHeight - topBarHeight - 28;
        context.enableScissor(panelX, msgAreaY + 1, panelX + panelWidth, msgAreaY + msgAreaHeight + 4);
        currentScrollOffset += (targetScrollOffset - currentScrollOffset) * globalScrollSpeed * deltaTime;
        if (!messages.isEmpty() && markdownPanel != null) {
            int yStart = msgAreaY + 5 - (int) currentScrollOffset;
            if (panelWidth - 10 > 0)
                markdownPanel.render(panelX + 5, yStart, panelWidth - 10, context);
        } else if (messages.isEmpty()) {
            TextRenderer tr = mc.textRenderer;
            int iconRect = 100;
            int iconCenterX = panelX + panelWidth / 2 - iconRect / 2;
            int verticalCenter = msgAreaY + msgAreaHeight / 2;
            int totalContentHeight = iconRect + tr.fontHeight + 5;
            int iconY = verticalCenter - totalContentHeight / 2;
            drawPixelArt(context, iconCenterX, iconY, iconRect, iconRect, RemotelyAIcon);
            drawInnerBorder(context, iconCenterX, iconY, iconRect, iconRect, Config.innerBorderColor);
            Render.drawOuterBorder(context, iconCenterX, iconY, iconRect, iconRect, innerBorderColor);
            String greeting = Render.trimTextToWidthWithEllipsis("Welcome, " + mc.getSession().getUsername() + "!", panelWidth - 10);
            String greeting2 = "I'm Remotely AI.";
            int greetingCenterX = panelX + panelWidth / 2 - tr.getWidth(Text.literal(greeting)) / 2;
            int greeting2CenterX = panelX + panelWidth / 2 - tr.getWidth(Text.literal(greeting2)) / 2;
            int textY = iconY + iconRect + 5;
            context.drawText(tr, Text.literal(greeting), greetingCenterX, textY, Config.globalDarkTextColor, Config.shadow);
            textY += tr.fontHeight + 2;
            context.drawText(tr, Text.literal(greeting2), greeting2CenterX, textY, Config.globalDarkTextColor, Config.shadow);
        }
        context.disableScissor();
        inputHovered = mouseX >= panelX + 5 && mouseX < panelX + panelWidth - 5 && mouseY >= panelY + panelHeight - 24 && mouseY < panelY + panelHeight - 10;
        drawInnerBorder(context, panelX, panelY + panelHeight - 24, panelWidth, 35, Config.innerBorderColor);
        if (panelX + 5 <= panelX + panelWidth - 10) {
            String placeholder = hasValidToken ? "Ask Remotely..." : "Enter Gemini Token";
            Render.drawTextInput(context, mc, panelX + 5, panelY + panelHeight - 20, "AI Input", inputBuffer.toString(), fieldFocused, inputCursor, -1, -1, inputHovered, panelWidth - 10, 14, placeholder);
        }
        Render.ContextMenu.renderMenu(context, mc, mouseX, mouseY);
    }

    private void renderTopBar(DrawContext context, int panelX, int panelY, int panelWidth, int mouseX, int mouseY) {
        int buttonSize = 20;
        int gap = 4;
        int barHeight = buttonSize + 2 * gap;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + barHeight, Config.innerBackgroundColor);
        drawInnerBorder(context, panelX, panelY, panelWidth, barHeight, Config.innerBorderColor);
        Render.drawOuterBorder(context, panelX, panelY, panelWidth, barHeight, Config.innerBackgroundColor);
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
        if (Render.ContextMenu.isOpen()) {
            if (Render.ContextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        Render.ContextMenu.hide();
        if (markdownPanel != null) {
            if (markdownPanel.handleClick(panelX + 5, panelY + 5 - (int)currentScrollOffset, panelWidth - 10, (float)mouseX, (float)mouseY)) {
                return true;
            }
        }
        int buttonSize = 20;
        int gap = 4;
        int barHeight = buttonSize + 2 * gap;
        if (inputHovered) {
            playSound(Sound.SELECT);
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
                playSound(Sound.CREATE);
                newChat();
                return true;
            }
            if (mouseX >= xDelete && mouseX < xDelete + buttonSize) {
                playSound(Sound.DELETE);
                deleteCurrentChat();
                return true;
            }
            if (mouseX >= xHistory && mouseX < xHistory + buttonSize) {
                playSound(Sound.CLICK);
                Render.ContextMenu.show((int) mouseX, (int) mouseY + 15, 80, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
                showChatHistoryContextMenu();
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
            int maxScroll = Math.max(0, totalHeight - msgAreaHeight);
            float scrollSpeed = Math.max(1, mc.textRenderer.fontHeight * 3);
            targetScrollOffset -= (int) (verticalAmount * scrollSpeed);
            if (targetScrollOffset < 0) targetScrollOffset = 0;
            else if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
            return true;
        }
        return false;
    }

    private int getTotalChatHeight(int availableWidth, TextRenderer tr) {
        if (markdownPanel != null) {
            return (int) markdownPanel.getHeight();
        }
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
                    if (!line.isEmpty()) line.append(" ");
                    line.append(word);
                }
                if (!line.isEmpty()) result.add(line.toString());
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
            if (inputCursor > 0) inputCursor--;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (inputCursor < inputBuffer.length()) inputCursor++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboardText = mc.keyboard.getClipboard();
            if (clipboardText != null) {
                inputBuffer.insert(inputCursor, clipboardText);
                inputCursor += clipboardText.length();
            }
            return true;
        }
        return keyCode == GLFW.GLFW_KEY_SPACE;
    }

    public boolean charTyped(char chr, int keyCode) {
        if (chr >= 32 && chr != 127) {
            inputBuffer.insert(inputCursor, chr);
            inputCursor++;
        }
        return true;
    }

    private void updateCurrentChatHistory() {
        try {
            JsonArray history;
            if (Files.exists(CHAT_HISTORY_PATH)) {
                String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
                history = JsonParser.parseString(content).getAsJsonArray();
            } else {
                history = new JsonArray();
            }
            JsonArray currentChat = new JsonArray();
            for (AIMessage msg : messages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("sender", msg.sender);
                obj.addProperty("text", msg.text);
                currentChat.add(obj);
            }
            if (history.isEmpty()) {
                history.add(currentChat);
            } else {
                history.set(history.size() - 1, currentChat);
            }
            Files.writeString(CHAT_HISTORY_PATH, history.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            devPrint("Failed to update chat history: " + e.getMessage());
        }
        updatePanelMarkdown();
    }

    private void loadLatestChatHistory() {
        try {
            JsonArray history;
            if (Files.exists(CHAT_HISTORY_PATH)) {
                String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
                history = JsonParser.parseString(content).getAsJsonArray();
                if (!history.isEmpty()) {
                    JsonArray conversation = history.get(history.size() - 1).getAsJsonArray();
                    messages.clear();
                    for (int i = 0; i < conversation.size(); i++) {
                        JsonObject obj = conversation.get(i).getAsJsonObject();
                        String sender = obj.has("sender") ? obj.get("sender").getAsString() : "";
                        String text = obj.has("text") ? obj.get("text").getAsString() : "";
                        messages.add(new AIMessage(sender, text));
                    }
                } else {
                    history.add(new JsonArray());
                    Files.writeString(CHAT_HISTORY_PATH, history.toString(), StandardCharsets.UTF_8);
                }
            } else {
                history = new JsonArray();
                history.add(new JsonArray());
                Files.writeString(CHAT_HISTORY_PATH, history.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            devPrint("Failed to load latest chat history: " + e.getMessage());
        }
        updatePanelMarkdown();
    }

    private void showChatHistoryContextMenu() {
        try {
            if (!Files.exists(CHAT_HISTORY_PATH)) {
                return;
            }
            String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
            JsonArray history = JsonParser.parseString(content).getAsJsonArray();
            for (int i = 0; i < history.size(); i++) {
                String label = "History " + (i + 1);
                final int index = i;
                Render.ContextMenu.addItem(label, () -> {
                    playSound(Sound.CLICK);
                    loadChatHistory(index);
                }, false, false, false, "");
            }
        } catch (IOException e) {
            devPrint("Failed to load chat history: " + e.getMessage());
        }
    }

    private void loadChatHistory(int index) {
        try {
            if (!Files.exists(CHAT_HISTORY_PATH)) {
                return;
            }
            String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
            JsonArray history = JsonParser.parseString(content).getAsJsonArray();
            if (index < history.size()) {
                JsonArray conversation = history.get(index).getAsJsonArray();
                messages.clear();
                for (int i = 0; i < conversation.size(); i++) {
                    JsonObject obj = conversation.get(i).getAsJsonObject();
                    String sender = obj.has("sender") ? obj.get("sender").getAsString() : "";
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    messages.add(new AIMessage(sender, text));
                    devPrint("Loaded message: " + sender + ": " + text);
                }
            }
        } catch (IOException e) {
            devPrint("Failed to load chat history: " + e.getMessage());
        }
        updatePanelMarkdown();
    }

    private void deleteCurrentChat() {
        try {
            JsonArray history;
            if (Files.exists(CHAT_HISTORY_PATH)) {
                String content = Files.readString(CHAT_HISTORY_PATH, StandardCharsets.UTF_8);
                history = JsonParser.parseString(content).getAsJsonArray();
            } else {
                history = new JsonArray();
            }
            if (!history.isEmpty()) {
                history.remove(history.size() - 1);
            }
            if (!history.isEmpty()) {
                JsonArray conversation = history.get(history.size() - 1).getAsJsonArray();
                messages.clear();
                for (int i = 0; i < conversation.size(); i++) {
                    JsonObject obj = conversation.get(i).getAsJsonObject();
                    String sender = obj.has("sender") ? obj.get("sender").getAsString() : "";
                    String text = obj.has("text") ? obj.get("text").getAsString() : "";
                    messages.add(new AIMessage(sender, text));
                }
            } else {
                history.add(new JsonArray());
                messages.clear();
            }
            Files.writeString(CHAT_HISTORY_PATH, history.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            devPrint("Failed to delete current chat: " + e.getMessage());
        }
        updatePanelMarkdown();
        if (messages.isEmpty()) {
            targetScrollOffset = 0;
            currentScrollOffset = 0;
        }
    }

    private void updatePanelMarkdown() {
        StringBuilder sb = new StringBuilder();
        for (AIMessage msg : messages) {
            if ("user".equals(msg.sender)) {
                sb.append("<span style=\"color:#ffd94f\">").append(msg.text).append("</span>\n\n");
            } else if ("ai".equals(msg.sender)) {
                sb.append(msg.text).append("\n\n\n");
            } else {
                sb.append(msg.text).append("\n\n");
            }
        }
        String fullText = sb.toString();
        if (markdownPanel == null || lastPanelMarkdownText == null || !lastPanelMarkdownText.equals(fullText)) {
            try {
                markdownPanel = new MarkdownRenderer(fullText);
                lastPanelMarkdownText = fullText;
            } catch (Exception e) {
                markdownPanel = null;
            }
        }
    }
}
