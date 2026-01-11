package redxax.oxy.remotely.data.player.model;

public record BanInfo(
    String uuid,
    String name,
    String created,
    String source,
    String expires,
    String reason
) {}
