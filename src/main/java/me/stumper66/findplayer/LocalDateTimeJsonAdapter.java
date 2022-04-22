package me.stumper66.findplayer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LocalDateTimeJsonAdapter implements JsonSerializer<LocalDateTime>,
    JsonDeserializer<LocalDateTime> {

    public static final LocalDateTimeJsonAdapter INSTANCE = new LocalDateTimeJsonAdapter();

    private LocalDateTimeJsonAdapter() {
    }

    @Override
    public LocalDateTime deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) throws JsonParseException {
        if(!json.isJsonNull()) {
            return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return null;
    }

    @Override
    public JsonElement serialize(final LocalDateTime src, final Type typeOfSrc,
        final JsonSerializationContext context) {
        if(src != null) {
            return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return JsonNull.INSTANCE;
    }
}
