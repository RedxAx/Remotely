package redxax.oxy.remotely;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemotelyInit
{
	public static final Logger LOGGER = LoggerFactory.getLogger("remotely");
	public static ModPlatform PLATFORM = null;

	public static void entrypoint(ModPlatform platform) {
		RemotelyInit.PLATFORM = platform;
		RemotelyClient remotelyClient = new RemotelyClient();
		remotelyClient.initialize();
		LOGGER.info("Started mod in %s loader".formatted(RemotelyInit.PLATFORM.getModloader()));
	}
}