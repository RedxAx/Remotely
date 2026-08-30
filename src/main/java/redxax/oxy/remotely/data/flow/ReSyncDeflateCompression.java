package redxax.oxy.remotely.data.flow;

import restudio.resync.protocol.ReSyncCompression;

public final class ReSyncDeflateCompression implements ReSyncCompression {
    private static final ReSyncCompression DELEGATE = ReSyncCompression.deflate();

    @Override
    public String algorithm() {
        return DELEGATE.algorithm();
    }

    @Override
    public byte[] compress(byte[] payload) {
        return DELEGATE.compress(payload);
    }

    @Override
    public byte[] decompress(byte[] payload, int maximumOutputBytes) {
        return DELEGATE.decompress(payload, maximumOutputBytes);
    }
}
