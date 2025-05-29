package redxax.oxy.remotely.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.*;

import static redxax.oxy.remotely.util.DevUtil.devPrint;
import static redxax.oxy.remotely.util.Sound.*;

public class SoundUtils {
    private static final Map<String, byte[]> AUDIO_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "Audio-Thread");
                t.setDaemon(true);
                return t;
            });

    public static void playSound(Sound sound) {
        if (sound.isEnabled()) {
            playSound(sound, (float) soundVolume / 100, (float) ((Math.random() * Sound.pitchVariation + 100) / 100));
        }
    }
    public static void playSound(Sound sound, float volume, float pitch) {
        AUDIO_EXECUTOR.submit(() -> play(sound.getPath(), volume, pitch));
    }
    private static byte[] getAudioData(String path) {
        return AUDIO_CACHE.computeIfAbsent(path, SoundUtils::loadAudioData);
    }

    private static byte[] loadAudioData(String path) {
        try (InputStream is = SoundUtils.class.getResourceAsStream(path)) {
            if (is == null) {
                new Notification("Sound file not found: " + path, Notification.Type.ERROR);
                return new byte[0];
            }
            return readAllBytes(is);
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

    private static void play(String path, float volume, float pitch) {
        try {
            byte[] audioData = getAudioData(path);
            if (audioData.length == 0) return;

            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(bais));
            AudioFormat originalFormat = audioStream.getFormat();
            AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, originalFormat.getSampleRate(), 16, originalFormat.getChannels(), originalFormat.getChannels() * 2, originalFormat.getSampleRate(), false);
            if (!originalFormat.equals(pcmFormat)) {
                audioStream = AudioSystem.getAudioInputStream(pcmFormat, audioStream);
            }

            AudioFormat playbackFormat = pcmFormat;
            if (Math.abs(pitch - 1.0f) > 0.01f) {
                playbackFormat = new AudioFormat(pcmFormat.getEncoding(), pcmFormat.getSampleRate() * pitch, pcmFormat.getSampleSizeInBits(), pcmFormat.getChannels(), pcmFormat.getFrameSize(), pcmFormat.getFrameRate() * pitch, pcmFormat.isBigEndian());
            }
            DataLine.Info info = new DataLine.Info(Clip.class, playbackFormat);

            if (!AudioSystem.isLineSupported(info)) {
                playbackFormat = pcmFormat;
                info = new DataLine.Info(Clip.class, playbackFormat);
            }

            if (AudioSystem.isLineSupported(info)) {
                Clip clip = (Clip) AudioSystem.getLine(info);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float gain = 20f * (float) Math.log10(Math.max(0.01f, volume));
                    gain = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gain));
                    gainControl.setValue(gain);
                }
                clip.open(audioStream);
                clip.start();

                AudioInputStream finalAudioStream = audioStream;
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        try {
                            finalAudioStream.close();
                            bais.close();
                        } catch (IOException ignored) {}
                    }
                });
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            devPrint("Error playing sound " + path + ": " + e.getMessage());
        }
    }

    public static void shutdown() {
        AUDIO_EXECUTOR.shutdown();
        AUDIO_CACHE.clear();
    }
}