package redxax.oxy.remotely.data.playerdata.sources;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.data.playerdata.PlayerData;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataParserTest {
    @Test
    void parsesModernItemsAndComponents() {
        PlayerData data = PlayerDataParser.parsePlayerDataFromSnbt("{Health:20.0f,foodLevel:18,XpLevel:7,Pos:[1.5d,64.0d,-3.25d],Inventory:[{Slot:0b,id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:custom_name\":\"Blade\"}}]}");

        assertEquals(20.0d, data.health());
        assertEquals(1, data.inventory().size());
        assertFalse(data.inventory().getFirst().tag().isEmpty());
        assertEquals(1.5d, data.location().x());
    }

    @Test
    void missingPositionRemainsUnavailable() {
        PlayerData data = PlayerDataParser.parsePlayerDataFromSnbt("{Health:10.0f,Inventory:[]}");

        assertNull(data.location());
    }

    @Test
    void malformedStatsReturnEmptyMap() {
        assertEquals(0, PlayerDataParser.parseStats("not-json").size());
    }

    @Test
    void normalizesLegacyCountAndTag() {
        PlayerData data = PlayerDataParser.parsePlayerDataFromSnbt("{Inventory:[{Slot:2b,id:\"minecraft:stone\",Count:12b,tag:{display:{Name:'{\"text\":\"Stone\"}'}}}]}");

        assertEquals(12, data.inventory().getFirst().count());
        assertFalse(data.inventory().getFirst().tag().isEmpty());
    }

    @Test
    void acceptsGzipZlibAndUncompressedNbt() throws Exception {
        byte[] nbt = binaryPlayerData();

        assertEquals(20.0d, PlayerDataParser.parsePlayerData(nbt).join().health());
        assertEquals(20.0d, PlayerDataParser.parsePlayerData(compress(nbt, true)).join().health());
        assertEquals(20.0d, PlayerDataParser.parsePlayerData(compress(nbt, false)).join().health());
    }

    @Test
    void truncatedNbtFailsWithoutInventedData() {
        PlayerData data = PlayerDataParser.parsePlayerData(new byte[]{10, 0, 0, 3, 0}).join();

        assertNull(data);
    }

    private byte[] binaryPlayerData() {
        return new byte[]{10, 0, 0, 5, 0, 6, 72, 101, 97, 108, 116, 104, 65, -96, 0, 0, 0};
    }

    private byte[] compress(byte[] input, boolean gzip) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var compressor = gzip ? new GZIPOutputStream(output) : new DeflaterOutputStream(output);
        compressor.write(input);
        compressor.close();
        assertTrue(output.size() > 0);
        return output.toByteArray();
    }
}
