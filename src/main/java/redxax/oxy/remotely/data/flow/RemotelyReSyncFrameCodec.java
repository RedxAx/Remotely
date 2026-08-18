package redxax.oxy.remotely.data.flow;

import restudio.resync.protocol.ReSyncCompressionNegotiation;
import restudio.resync.protocol.ReSyncFrame;
import restudio.resync.protocol.ReSyncFrameCodec;
import restudio.resync.protocol.ReSyncMessageType;
import restudio.resync.protocol.ReSyncProtocolContract;

import java.util.Set;

public final class RemotelyReSyncFrameCodec {
    private final ReSyncFrameCodec delegate;

    public RemotelyReSyncFrameCodec() {
        delegate = new ReSyncFrameCodec(
            ReSyncProtocolContract.MAX_ENCODED_FRAME_BYTES,
            ReSyncProtocolContract.MAX_DECOMPRESSED_PAYLOAD_BYTES,
            new ReSyncDeflateCompression(),
            ReSyncCompressionNegotiation.enabled("deflate", ReSyncProtocolContract.DEFAULT_COMPRESSION_THRESHOLD_BYTES));
    }

    public ReSyncDecodedFrame decode(byte[] data, Set<Short> validDataChannels) {
        ReSyncFrame frame = delegate.decode(data);
        short channel = (short) frame.channel();
        if (frame.messageType().value() == ReSyncProtocolContract.MESSAGE_DATA
                && validDataChannels != null && !validDataChannels.contains(channel)) {
            throw new IllegalArgumentException("Unknown channel: " + (channel & 0xFFFF));
        }
        return new ReSyncDecodedFrame(frame.messageType().value(), channel, frame.sequence(), frame.payload(), frame.compressed());
    }

    public byte[] encode(int messageType, byte[] payload, short channel, int sequence) {
        ReSyncFrame frame = new ReSyncFrame(ReSyncMessageType.fromValue((byte) messageType),
            Short.toUnsignedInt(channel), sequence, payload, false, false, false);
        return delegate.encode(frame, false);
    }
}
