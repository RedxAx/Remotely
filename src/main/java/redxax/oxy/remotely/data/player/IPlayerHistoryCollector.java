package redxax.oxy.remotely.data.player;

import redxax.oxy.remotely.data.managed.SessionEventType;

import java.util.UUID;

public interface IPlayerHistoryCollector {
    void startSession(UUID uuid, String name, String ip, long startTime);
    void endSession(UUID uuid, long endTime);
    void recordCommand(UUID uuid, String name, String command, long timestamp);
    void recordAccessChange(UUID uuid, String name, SessionEventType type, String details, long timestamp);
}