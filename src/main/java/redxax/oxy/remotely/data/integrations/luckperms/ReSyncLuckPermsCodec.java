package redxax.oxy.remotely.data.integrations.luckperms;

import restudio.resync.permissions.LuckPermsManagementContract.Request;
import restudio.resync.permissions.LuckPermsManagementContract.Response;

public interface ReSyncLuckPermsCodec {
    byte[] encode(Request request);

    Response decode(byte[] payload);

    static ReSyncLuckPermsCodec unavailable() {
        return new ReSyncLuckPermsCodec() {
            @Override
            public byte[] encode(Request request) {
                throw new IllegalStateException("LuckPerms Codec Is Unavailable");
            }

            @Override
            public Response decode(byte[] payload) {
                throw new IllegalStateException("LuckPerms Codec Is Unavailable");
            }
        };
    }
}
