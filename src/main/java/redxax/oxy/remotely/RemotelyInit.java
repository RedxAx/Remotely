package redxax.oxy.remotely;

public class RemotelyInit
{
	public static ModPlatform PLATFORM = null;

	public static void entrypoint(ModPlatform platform) {
		RemotelyInit.PLATFORM = platform;
		RemotelyClient remotelyClient = new RemotelyClient();
		remotelyClient.initialize();
	}
}