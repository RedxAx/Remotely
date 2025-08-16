package redxax.oxy.remotely.api;

import redxax.oxy.remotely.servers.ServerInfo;

public class RemotelyApiFactory {
    public static RemotelyAPI get(ServerInfo serverInfo) {
        if (serverInfo != null && serverInfo.isRemote) {
            return new RemoteAPI(serverInfo.remoteHost);
        } else {
            return new LocalAPI();
        }
    }
}
