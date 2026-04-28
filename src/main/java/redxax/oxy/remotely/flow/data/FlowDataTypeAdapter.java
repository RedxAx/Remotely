package redxax.oxy.remotely.flow.data;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class FlowDataTypeAdapter extends TypeAdapter<FlowDataType> {
    @Override
    public void write(JsonWriter out, FlowDataType value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.getId());
        }
    }

    @Override
    public FlowDataType read(JsonReader in) throws IOException {
        String id = in.nextString();
        return FlowDataType.fromString(id);
    }
}
