package redxax.oxy.remotely.data.integrations.luckperms;

import com.google.gson.Gson;
import restudio.resync.permissions.LuckPermsManagementContract.Request;
import restudio.resync.permissions.LuckPermsManagementContract.Response;

import java.nio.charset.StandardCharsets;

public final class DesktopReSyncLuckPermsCodec implements ReSyncLuckPermsCodec {
    private final Gson gson = new Gson();

    @Override
    public byte[] encode(Request request) {
        return gson.toJson(request).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Response decode(byte[] payload) {
        return gson.fromJson(new String(payload, StandardCharsets.UTF_8), Response.class);
    }
}
