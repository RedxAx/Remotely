package redxax.oxy.remotely.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;
import static redxax.oxy.remotely.util.Sound.*;

public class SoundUtils {
    private static final Map<String, Integer> BUFFER_CACHE = new HashMap<>();

    public static void playSound(Sound sound) {
        if (sound.isEnabled())
            playSound(sound, (float) soundVolume / 100, (float) ((Math.random() * Sound.pitchVariation + 100) / 100));
    }

    public static void playSound(Sound sound, float vol, float pitch) {
        play(sound.getPath(), vol, pitch);
    }

    private static int getBuffer(String path) {
        if (BUFFER_CACHE.containsKey(path)) {
            return BUFFER_CACHE.get(path);
        }
        int bufferId = loadSound(path);
        BUFFER_CACHE.put(path, bufferId);
        return bufferId;
    }

    private static int loadSound(String path) {
        try (InputStream is = SoundUtils.class.getResourceAsStream(path)) {
            if (is == null) {
                new Notification("Sound file not found: " + path, Notification.Type.ERROR);
                return -1;
            }
            byte[] data = readAllBytes(is);
            ByteBuffer vorbis = MemoryUtil.memAlloc(data.length);
            vorbis.put(data).flip();
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            IntBuffer rate = BufferUtils.createIntBuffer(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(vorbis, channels, rate);
            MemoryUtil.memFree(vorbis);
            int fmt = channels.get(0) > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            int bufferId = AL10.alGenBuffers();
            AL10.alBufferData(bufferId, fmt, pcm, rate.get(0));
            MemoryUtil.memFree(pcm);
            return bufferId;
        } catch (IOException e) {
            throw new RuntimeException("Error loading sound: " + path, e);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static void play(String path, float vol, float pitch) {
        int buf = getBuffer(path);
        int src = AL10.alGenSources();
        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
        AL10.alSourcef(src, AL10.AL_GAIN, vol);
        AL10.alSourcef(src, AL10.AL_PITCH, pitch);
        AL10.alSourcePlay(src);
        new Thread(() -> {
            int state;
            do {
                state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
            } while (state == AL10.AL_PLAYING);
            AL10.alDeleteSources(src);
        }).start();
    }
}
