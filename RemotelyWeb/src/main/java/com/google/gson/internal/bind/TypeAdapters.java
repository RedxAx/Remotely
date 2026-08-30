package com.google.gson.internal.bind;

import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import restudio.rescreen.util.JsonTreeParser;

import java.io.IOException;

public final class TypeAdapters {
    public static final TypeAdapter<JsonElement> JSON_ELEMENT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, JsonElement value) throws IOException {
            out.jsonValue(JsonTreeParser.write(value));
        }

        @Override
        public JsonElement read(JsonReader in) throws IOException {
            throw new UnsupportedOperationException("Browser JSON reader adapter is unavailable");
        }
    };

    private TypeAdapters() {
    }
}
