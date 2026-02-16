package redxax.oxy.remotely.data.playerdata.sources;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class RconClient implements AutoCloseable {
    private static final int SERVERDATA_RESPONSE_VALUE = 0;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_AUTH_RESPONSE = 2;

    private final Socket socket;
    private final PacketReader reader;
    private final PacketWriter writer;
    private final int baseTimeoutMillis;
    private int requestId = 1;

    RconClient(String host, int port, int timeoutMillis) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        this.socket.setSoTimeout(timeoutMillis);
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
        this.reader = new PacketReader(socket, new PacketCodec(StandardCharsets.US_ASCII), 4110);
        this.writer = new PacketWriter(socket, new PacketCodec(StandardCharsets.US_ASCII), 1460);
        this.baseTimeoutMillis = timeoutMillis;
    }

    boolean authenticate(String password) throws IOException {
        int authId = nextId();
        writePacket(new Packet(authId, SERVERDATA_AUTH, password != null ? password : ""));
        boolean sawAuthResponse = false;
        for (int i = 0; i < 8; i++) {
            Packet p = readPacket();
            if (p == null) return false;
            if (!p.isValid()) return false;
            if (p.requestId != authId) continue;
            if (p.type == SERVERDATA_AUTH_RESPONSE || p.type == SERVERDATA_RESPONSE_VALUE) {
                sawAuthResponse = true;
                if (p.type == SERVERDATA_AUTH_RESPONSE) break;
            }
        }
        return sawAuthResponse;
    }

    String execute(String command, boolean allowMultiPacket) throws IOException {
        int cmdId = nextId();
        writePacket(new Packet(cmdId, SERVERDATA_EXECCOMMAND, command));

        StringBuilder sb = new StringBuilder();
        boolean sawCmdResponse = false;

        while (true) {
            Packet packet = readPacket();
            if (packet == null) break;
            if (!packet.isValid()) break;
            if (packet.requestId != cmdId) continue;
            if (packet.payload == null || packet.payload.isEmpty()) break;
            sawCmdResponse = true;
            sb.append(packet.payload);
            if (!allowMultiPacket) break;
            if (packet.payload.length() < 4096) break;
        }

        if (!sawCmdResponse) return "";
        return sb.toString();
    }

    String execute(String command) throws IOException {
        return execute(command, true);
    }

    String executeWithDebug(String command, boolean allowMultiPacket, String tag) throws IOException {
        int cmdId = nextId();
        writePacket(new Packet(cmdId, SERVERDATA_EXECCOMMAND, command));

        StringBuilder sb = new StringBuilder();
        boolean sawCmdResponse = false;
        boolean sawEmpty = false;
        int packets = 0;
        int bytes = 0;

        while (true) {
            Packet packet = readPacket();
            if (packet == null) break;
            if (!packet.isValid()) break;
            if (packet.requestId != cmdId) continue;
            packets++;
            if (packet.payload == null || packet.payload.isEmpty()) {
                sawEmpty = true;
                break;
            }
            sawCmdResponse = true;
            sb.append(packet.payload);
            bytes += packet.payload.length();
            if (!allowMultiPacket) break;
            if (packet.payload.length() < 4096) break;
        }

        DebugLogger.log(tag, command, packets, bytes, sawEmpty, sawCmdResponse, sb);
        if (!sawCmdResponse) return "";
        return sb.toString();
    }

    private int nextId() {
        return requestId++;
    }

    private Packet readPacket() throws IOException {
        socket.setSoTimeout(baseTimeoutMillis);
        return reader.read();
    }

    private void writePacket(Packet packet) throws IOException {
        writer.write(packet);
    }

    private static final class DebugLogger {
        private static void log(String tag, String command, int packets, int bytes, boolean sawEmpty, boolean sawResponse, StringBuilder payload) {
            String label = tag == null || tag.isBlank() ? "RconClient" : tag;
            StringBuilder sb = new StringBuilder();
            sb.append("RCON debug: cmd=").append(command);
            sb.append(" packets=").append(packets);
            sb.append(" bytes=").append(bytes);
            sb.append(" sawEmpty=").append(sawEmpty);
            sb.append(" sawResponse=").append(sawResponse);
            String data = payload != null ? payload.toString() : "";
            if (!data.isBlank()) {
                String preview = data.replace('\n', ' ').replace('\r', ' ');
                sb.append(" preview=").append(preview);
            }
            restudio.rescreen.debug.DebugManager.getInstance().log(label, sb.toString());
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private record Packet(int requestId, int type, String payload) {
        boolean isValid() {
            return requestId != -1;
        }
    }

    private static final class PacketCodec {
        private final Charset charset;

        private PacketCodec(Charset charset) {
            this.charset = charset;
        }

        private void encode(Packet packet, ByteBuffer destination) {
            destination.putInt(packet.requestId);
            destination.putInt(packet.type);
            destination.put(charset.encode(packet.payload != null ? packet.payload : ""));
            destination.put((byte) 0);
            destination.put((byte) 0);
        }

        private Packet decode(ByteBuffer source, int length) {
            int requestId = source.getInt();
            int packetType = source.getInt();

            int limit = source.limit();
            source.limit(source.position() + length - 10);
            String payload = charset.decode(source).toString();
            source.limit(limit);

            source.get();
            source.get();

            return new Packet(requestId, packetType, payload);
        }
    }

    private static final class PacketReader {
        private final DataInputStream in;
        private final PacketCodec codec;
        private final ByteBuffer buffer;

        private PacketReader(Socket socket, PacketCodec codec, int bufferCapacity) throws IOException {
            this.in = new DataInputStream(socket.getInputStream());
            this.codec = codec;
            this.buffer = ByteBuffer.allocate(bufferCapacity).order(ByteOrder.LITTLE_ENDIAN);
        }

        private Packet read() throws IOException {
            readUntilAvailable(Integer.BYTES);
            buffer.flip();
            int length = buffer.getInt();
            buffer.compact();

            readUntilAvailable(length);
            buffer.flip();
            Packet packet = codec.decode(buffer, length);
            buffer.compact();
            return packet;
        }

        private void readUntilAvailable(int bytesAvailable) throws IOException {
            while (buffer.position() < bytesAvailable) {
                int read = in.read(buffer.array(), buffer.position(), buffer.remaining());
                if (read == -1) {
                    throw new EOFException();
                }
                buffer.position(buffer.position() + read);
            }
        }
    }

    private static final class PacketWriter {
        private final DataOutputStream out;
        private final PacketCodec codec;
        private final ByteBuffer buffer;

        private PacketWriter(Socket socket, PacketCodec codec, int bufferCapacity) throws IOException {
            this.out = new DataOutputStream(socket.getOutputStream());
            this.codec = codec;
            this.buffer = ByteBuffer.allocate(bufferCapacity).order(ByteOrder.LITTLE_ENDIAN);
        }

        private int write(Packet packet) throws IOException {
            if (packet.payload != null && packet.payload.length() > 1446) {
                throw new IllegalArgumentException("Packet payload too big");
            }

            buffer.clear();
            buffer.position(Integer.BYTES);
            codec.encode(packet, buffer);
            buffer.putInt(0, buffer.position() - Integer.BYTES);
            buffer.flip();
            out.write(buffer.array(), 0, buffer.limit());
            out.flush();
            return buffer.limit();
        }
    }
}
