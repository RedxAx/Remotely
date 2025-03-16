package redxax.oxy.common;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Remotely implements ModInitializer {
    public static final String MOD_ID = "remotely";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Remotely mod initialized on the server.");
    }
}
