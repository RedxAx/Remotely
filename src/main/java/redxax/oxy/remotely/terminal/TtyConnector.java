package redxax.oxy.remotely.terminal;

import com.jcraft.jsch.Channel;
import com.jediterm.core.util.TermSize;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TtyConnector implements com.jediterm.terminal.TtyConnector {
    private final Channel myChannel;
    private final InputStream myInputStream;
    private final OutputStream myOutputStream;
    private final InputStreamReader myReader;

    public TtyConnector(@NotNull Channel channel) throws IOException {
        myChannel = channel;
        myInputStream = channel.getInputStream();
        myOutputStream = channel.getOutputStream();
        myReader = new InputStreamReader(myInputStream, StandardCharsets.UTF_8);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return myReader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        myOutputStream.write(bytes);
        myOutputStream.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return myChannel.isConnected();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        if (myChannel instanceof com.jcraft.jsch.ChannelShell) {
            ((com.jcraft.jsch.ChannelShell) myChannel).setPtySize(termSize.getColumns(), termSize.getRows(), termSize.getColumns() * 8, termSize.getRows() * 8);
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (isConnected()) {
            Thread.sleep(100);
        }
        return myChannel.getExitStatus();
    }

    @Override
    public boolean ready() throws IOException {
        return myReader.ready();
    }

    @Override
    public String getName() {
        return "SSH";
    }

    @Override
    public void close() {
        if (myChannel != null && myChannel.isConnected()) {
            myChannel.disconnect();
        }
        try {
            myOutputStream.close();
        } catch (IOException ignored) {
        }
        try {
            myInputStream.close();
        } catch (IOException ignored) {
        }
    }
}