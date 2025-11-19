package redxax.oxy.remotely.data.integrations.luckperms;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class LuckPermsSseClient {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread thread;

    public LuckPermsSseClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public void subscribe(String endpointPath, Consumer<String> onMessage) {
        subscriptions.add(new Subscription(endpointPath, onMessage));
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::runLoop);
        thread.setDaemon(true);
        thread.setName("LuckPerms-SSE");
        thread.start();
    }

    public void close() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void runLoop() {
        while (running) {
            List<Connection> conns = new ArrayList<>();
            try {
                for (Subscription s : subscriptions) {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + s.path)).header("Accept", "text/event-stream").header("Authorization", "Bearer " + apiKey).timeout(Duration.ofSeconds(30)).GET().build();
                    HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        Connection c = new Connection(new BufferedReader(new InputStreamReader(resp.body())), s.onMessage);
                        conns.add(c);
                    }
                }
                if (conns.isEmpty()) {
                    sleepQuiet(3000);
                }
                while (running && !conns.isEmpty()) {
                    conns.removeIf(c -> !c.readOnce());
                    if (conns.isEmpty()) break;
                    sleepQuiet(50);
                }
            } catch (Exception ignored) {
            }
            sleepQuiet(1500);
        }
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static class Subscription {
        final String path;
        final Consumer<String> onMessage;

        Subscription(String path, Consumer<String> onMessage) {
            this.path = path;
            this.onMessage = onMessage;
        }
    }

    private static class Connection {
        private final BufferedReader br;
        private final Consumer<String> onMessage;
        private StringBuilder dataBuf = new StringBuilder();
        private boolean open = true;

        Connection(BufferedReader br, Consumer<String> onMessage) {
            this.br = br;
            this.onMessage = onMessage;
        }

        boolean readOnce() {
            if (!open) return false;
            try {
                if (!br.ready()) return true;
                String line = br.readLine();
                if (line == null) {
                    open = false;
                    return false;
                }
                if (line.startsWith("data:")) {
                    if (!dataBuf.isEmpty()) dataBuf.append("\n");
                    dataBuf.append(line.substring(5).trim());
                } else if (line.isEmpty()) {
                    if (!dataBuf.isEmpty()) {
                        String payload = dataBuf.toString();
                        dataBuf = new StringBuilder();
                        try {
                            onMessage.accept(payload);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                open = false;
                return false;
            }
        }
    }
}
