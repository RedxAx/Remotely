package redxax.oxy.remotely.data.playerdata.sources;

import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.NumberTag;
import net.querz.nbt.tag.ArrayTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

import java.util.ArrayList;
import java.util.List;

final class QuerzTagConverter {
    private QuerzTagConverter() {
    }

    static Nbt.Tag convert(String name, Tag<?> source) {
        if (source == null) return new Nbt.Tag(name, null);
        if (source instanceof CompoundTag compound) {
            List<Nbt.Tag> children = new ArrayList<>();
            compound.forEach((key, value) -> children.add(convert(key, value)));
            return new Nbt.Tag(name, children);
        }
        if (source instanceof ListTag<?> list) {
            List<Object> values = new ArrayList<>();
            for (Tag<?> value : list) {
                Nbt.Tag converted = convert("", value);
                values.add(value instanceof CompoundTag ? converted : converted.value);
            }
            return new Nbt.Tag(name, values);
        }
        if (source instanceof NumberTag<?> number) return new Nbt.Tag(name, number.asDouble());
        if (source instanceof StringTag string) return new Nbt.Tag(name, string.getValue());
        if (source instanceof ArrayTag<?> array) return new Nbt.Tag(name, array.getValue());
        return new Nbt.Tag(name, source.valueToString());
    }
}
