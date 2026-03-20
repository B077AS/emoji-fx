package io.github.b077as.emojifx;

import com.gluonhq.connect.converter.InputStreamIterableInputConverter;
import com.gluonhq.connect.converter.JsonConverter;

import javax.json.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class EmojiIterableInputConverter extends InputStreamIterableInputConverter<EmojiCsv> implements Iterator<EmojiCsv> {

    private JsonArray jsonArray;
    private int index;
    private final JsonConverter<EmojiCsv> converter;
    private final Map<String, Method> settersMappedByPropertyName = new HashMap<>();

    public EmojiIterableInputConverter(Class<EmojiCsv> targetClass) {
        Method[] allMethods = targetClass.getMethods();
        for (Method method : allMethods) {
            if (method.getName().startsWith("set")) {
                settersMappedByPropertyName.put(method.getName().toLowerCase(Locale.ROOT).substring(3), method);
            }
        }
        converter = new JsonConverter<>(targetClass) {

            @Override
            public EmojiCsv readFromJson(JsonObject json) {
                EmojiCsv t = new EmojiCsv();
                for (String property : settersMappedByPropertyName.keySet()) {
                    if (!json.containsKey(property)) {
                        continue;
                    }
                    Method setter = settersMappedByPropertyName.get(property);
                    Object[] args = new Object[1];
                    JsonValue jsonValue = json.get(property);
                    switch (jsonValue.getValueType()) {
                        case NULL:
                            args[0] = null;
                            break;
                        case FALSE:
                            args[0] = Boolean.FALSE;
                            break;
                        case TRUE:
                            args[0] = Boolean.TRUE;
                            break;
                        case STRING:
                            args[0] = ((JsonString) jsonValue).getString();
                            break;
                        case NUMBER:
                            args[0] = ((JsonNumber) jsonValue).intValue();
                            break;
                        case ARRAY:
                            JsonArray arrayProperty = (JsonArray) jsonValue;
                            List<Object> values = new ArrayList<>();
                            args[0] = values;
                            for (JsonValue arrayValue : arrayProperty) {
                                JsonString stringArrayValue = (JsonString) arrayValue;
                                values.add(stringArrayValue.getString());
                            }
                            break;
                        case OBJECT:
                            List<EmojiCsv> list = new ArrayList<>();
                            for (String key : ((JsonObject) jsonValue).keySet()) {
                                JsonObject jsonObject = ((JsonObject) jsonValue).getJsonObject(key);
                                if (jsonObject != null) {
                                    JsonConverter<EmojiCsv> jsonConverter = new JsonConverter<>(EmojiCsv.class);
                                    EmojiCsv emojiSkin = jsonConverter.readFromJson(jsonObject);
                                    emojiSkin.setName(key);
                                    list.add(emojiSkin);
                                }
                            }
                            args[0] = list;
                            break;
                        default:
                            break;
                    }

                    try {
                        setter.invoke(t, args);
                    } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException ex) {
                        System.out.println("Failed to call setter " + setter + " with value " + property + " " + ex);
                    }
                }
                return t;
            }
        };
    }

    @Override
    public boolean hasNext() {
        return index < jsonArray.size();
    }

    @Override
    public EmojiCsv next() {
        JsonObject jsonObject = jsonArray.getJsonObject(index++);
        return converter.readFromJson(jsonObject);
    }

    @Override
    public Iterator<EmojiCsv> iterator() {
        index = 0;
        try (JsonReader reader = Json.createReader(getInputStream())) {
            jsonArray = reader.readArray();
        }
        return this;
    }
}
