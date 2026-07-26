package redxax.oxy.remotely.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import restudio.rescreen.logging.LogConsole;
import restudio.rescreen.logging.LogEvent;
import restudio.rescreen.logging.LogSource;

import java.time.Instant;
import java.util.UUID;

public final class MinecraftLogConsole implements LogConsole {
    private static final String JEDI_EMULATOR_LOGGER = "com.jediterm.terminal.emulator.JediEmulator";
    private static final UUID NO_CORRELATION = new UUID(0, 0);
    private static final Logger LOGGER = LogManager.getLogger("Remotely");
    private static final MinecraftLogConsole INSTANCE = new MinecraftLogConsole();

    private MinecraftLogConsole() {
        Configurator.setLevel(JEDI_EMULATOR_LOGGER, Level.WARN);
    }

    public static MinecraftLogConsole get() {
        return INSTANCE;
    }

    @Override
    public void publish(LogEvent event) {
        StringBuilder message = prefix(event);
        message.append(' ').append(event.message());
        if (!event.correlationId().equals(NO_CORRELATION)) {
            message.append(" {").append(event.correlationId().toString(), 0, 8).append('}');
        }
        if (event.failure() != null) {
            message.append(System.lineSeparator()).append(event.failure().stackTrace());
        }
        LOGGER.log(level(event), message);
    }

    @Override
    public void publishRepeated(LogEvent event, int count, Instant timestamp) {
        LOGGER.info("{} Previous message repeated {} {}", prefix(event), count, count == 1 ? "time" : "times");
    }

    private StringBuilder prefix(LogEvent event) {
        StringBuilder prefix = new StringBuilder("[").append(event.type().name()).append(']');
        if (!event.source().equals(LogSource.GLOBAL)) {
            prefix.append(" [").append(event.source().displayName()).append(']');
        }
        return prefix;
    }

    private Level level(LogEvent event) {
        return switch (event.level()) {
            case TRACE -> Level.TRACE;
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
            case FATAL -> Level.FATAL;
        };
    }
}
