package redxax.oxy.remotely.ui.widgets.worldmap;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class RegionParser {

    public static ChunkData parseChunkData(Path file, int relX, int relZ) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            int offsetAddr = 4 * (relX + relZ * 32);
            raf.seek(offsetAddr);

            int offset = raf.readInt();
            int sectorId = offset >> 8;
            int sectorCount = offset & 0xFF;

            if (sectorId == 0 || sectorCount == 0) return null;

            raf.seek(sectorId * 4096L);
            int length = raf.readInt();
            byte compression = raf.readByte();

            if (length <= 0) return null;

            byte[] data = new byte[length - 1];
            raf.readFully(data);

            try (DataInputStream dis = getStream(compression, data)) {
                if (dis == null) return null;
                SimpleTag root = SimpleNbt.read(dis);
                return processChunkNbt(root);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static DataInputStream getStream(byte type, byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        if (type == 1) return new DataInputStream(new GZIPInputStream(bais));
        if (type == 2) return new DataInputStream(new InflaterInputStream(bais));
        return null;
    }

    private static ChunkData processChunkNbt(SimpleTag root) {
        ChunkData chunk = new ChunkData();

        SimpleTag sectionsTag = find(root, "sections");
        if (sectionsTag == null) sectionsTag = find(root, "Sections");

        if (sectionsTag == null) {
            SimpleTag level = find(root, "Level");
            if (level != null) sectionsTag = find(level, "Sections");
        }

        if (sectionsTag == null || !(sectionsTag.value instanceof List)) return chunk;

        List<SimpleTag> sections = (List<SimpleTag>) sectionsTag.value;

        sections.sort((a, b) -> {
            Number ya = (Number) find(a, "Y").value;
            Number yb = (Number) find(b, "Y").value;
            return Integer.compare(yb.intValue(), ya.intValue());
        });

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {

                boolean colDone = false;

                for (SimpleTag section : sections) {
                    if (colDone) break;

                    int yBase = ((Number) find(section, "Y").value).intValue() * 16;
                    SimpleTag blockStates = find(section, "block_states");
                    SimpleTag biomesTag = find(section, "biomes");

                    if (blockStates == null) continue;

                    PaletteData blockData = parsePalette(blockStates, false);
                    PaletteData biomeData = parsePalette(biomesTag, true);

                    if (blockData == null) continue;

                    for (int y = 15; y >= 0; y--) {
                        String block = getFromPalette(blockData, x, y, z);

                        if (block != null && !shouldIgnore(block)) {
                            String biome = (biomeData != null) ? getFromPalette(biomeData, x >> 2, y >> 2, z >> 2) : "minecraft:plains";
                            chunk.setBlock(x, z, block, yBase + y, biome);
                            colDone = true;
                            break;
                        }
                    }
                }
            }
        }
        return chunk;
    }

    static class PaletteData {
        String[] palette;
        long[] data;
        int bits;
        int perLong;
        int mask;
        boolean isBiome;
    }

    private static PaletteData parsePalette(SimpleTag containerTag, boolean isBiome) {
        if (containerTag == null) return null;

        SimpleTag paletteTag = find(containerTag, "palette");
        if (paletteTag == null || !(paletteTag.value instanceof List<?> rawList)) return null;

        if (rawList.isEmpty()) return null;

        PaletteData pd = new PaletteData();
        pd.isBiome = isBiome;
        pd.palette = new String[rawList.size()];

        for(int i=0; i<rawList.size(); i++) {
            Object entry = rawList.get(i);
            if (entry instanceof String) {
                pd.palette[i] = (String) entry;
            } else if (entry instanceof SimpleTag) {
                SimpleTag tag = (SimpleTag) entry;
                if (tag.value instanceof List) {
                    List<SimpleTag> fields = (List<SimpleTag>) tag.value;
                    for(SimpleTag field : fields) {
                        if ("Name".equals(field.name)) {
                            pd.palette[i] = (String) field.value;
                            break;
                        }
                    }
                }
            }
            if (pd.palette[i] == null) pd.palette[i] = "minecraft:air";
        }

        SimpleTag dataTag = find(containerTag, "data");
        if (dataTag != null && dataTag.value instanceof long[]) {
            pd.data = (long[]) dataTag.value;
            pd.bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(pd.palette.length - 1));
            pd.perLong = 64 / pd.bits;
            pd.mask = (1 << pd.bits) - 1;
        }

        return pd;
    }

    private static String getFromPalette(PaletteData pd, int x, int y, int z) {
        if (pd == null) return null;
        if (pd.data == null) return pd.palette[0];

        int index;
        if (pd.isBiome) index = (y * 4 + z) * 4 + x;
        else index = (y * 16 + z) * 16 + x;

        int lIndex = index / pd.perLong;
        int shift = (index % pd.perLong) * pd.bits;

        if (lIndex >= 0 && lIndex < pd.data.length) {
            int pIdx = (int) ((pd.data[lIndex] >>> shift) & pd.mask);
            if (pIdx >= 0 && pIdx < pd.palette.length) {
                return pd.palette[pIdx];
            }
        }
        return pd.palette[0];
    }

    private static boolean shouldIgnore(String id) {
        if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) return true;
        if (id.equals("minecraft:barrier") || id.equals("minecraft:structure_void") || id.equals("minecraft:light")) return true;

        if (id.equals("minecraft:short_grass")) return true;
        if (id.equals("minecraft:grass")) return true;
        if (id.equals("minecraft:tall_grass") || id.equals("minecraft:fern") || id.equals("minecraft:large_fern") || id.equals("bush")) return true;

        if (id.equals("minecraft:seagrass") || id.equals("minecraft:tall_seagrass")) return true;
        if (id.equals("minecraft:kelp") || id.equals("minecraft:kelp_plant")) return true;
        if (id.equals("minecraft:dead_bush") || id.equals("minecraft:sweet_berry_bush")) return true;

        if (id.equals("minecraft:crimson_roots") || id.equals("minecraft:warped_roots") || id.equals("minecraft:nether_sprouts")) return true;
        if (id.equals("minecraft:twisting_vines") || id.equals("minecraft:weeping_vines")) return true;
        if (id.equals("minecraft:twisting_vines_plant") || id.equals("minecraft:weeping_vines_plant")) return true;

        if (id.contains("flower") || id.contains("tulip") || id.contains("orchid") || id.contains("daisy") || id.contains("bluet") || id.contains("poppy")) return true;
        if (id.equals("minecraft:dandelion") || id.equals("minecraft:lilac") || id.equals("minecraft:peony") || id.equals("minecraft:rose_bush")) return true;

        if (id.contains("torch") || id.contains("lantern") || id.contains("tripwire")) return true;

        return false;
    }

    static class SimpleTag {
        String name; Object value;
        SimpleTag(String n, Object v) { name = n; value = v; }
    }
    static SimpleTag find(SimpleTag tag, String name) {
        if (tag.value instanceof List) {
            for (Object o : (List) tag.value) if (o instanceof SimpleTag && name.equals(((SimpleTag)o).name)) return (SimpleTag) o;
        }
        return null;
    }
    static class SimpleNbt {
        static SimpleTag read(DataInputStream dis) throws IOException {
            byte t = dis.readByte(); if (t==0) return null;
            String n = readString(dis); return readPayload(dis, t, n);
        }
        static SimpleTag readPayload(DataInputStream dis, byte t, String n) throws IOException {
            switch(t) {
                case 1: return new SimpleTag(n, dis.readByte());
                case 2: return new SimpleTag(n, dis.readShort());
                case 3: return new SimpleTag(n, dis.readInt());
                case 4: return new SimpleTag(n, dis.readLong());
                case 5: return new SimpleTag(n, dis.readFloat());
                case 6: return new SimpleTag(n, dis.readDouble());
                case 7: int l7=dis.readInt(); dis.skipBytes(l7); return new SimpleTag(n, new byte[0]);
                case 8: return new SimpleTag(n, readString(dis));
                case 9: byte lt=dis.readByte(); int ll=dis.readInt(); List<Object> l=new ArrayList<>(ll);
                    for(int i=0;i<ll;i++) l.add(lt==10?readPayload(dis,lt,""):readPayload(dis,lt,"").value);
                    return new SimpleTag(n, l);
                case 10: List<SimpleTag> c=new ArrayList<>(); while(true){byte tt=dis.readByte(); if(tt==0)break; c.add(readPayload(dis,tt,readString(dis)));} return new SimpleTag(n, c);
                case 11: int l11=dis.readInt(); dis.skipBytes(l11*4); return new SimpleTag(n, new int[0]);
                case 12: int l12=dis.readInt(); long[] la=new long[l12]; for(int i=0;i<l12;i++) la[i]=dis.readLong(); return new SimpleTag(n, la);
            }
            return new SimpleTag(n, null);
        }
        static String readString(DataInputStream d) throws IOException {
            int l = d.readUnsignedShort(); byte[] b = new byte[l]; d.readFully(b); return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
