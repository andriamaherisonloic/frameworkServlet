package utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public final class JsonSerializer {
    private JsonSerializer() {
    }

    public static String serialize(Object value) {
        return serialize(value, new IdentityHashMap<>());
    }

    private static String serialize(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            return quote(String.valueOf(value));
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cycle détecté pendant la sérialisation JSON");
        }

        try {
            if (value instanceof Map<?, ?>) {
                StringBuilder json = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("Les clés JSON doivent être des chaînes");
                    }
                    if (!first) {
                        json.append(',');
                    }
                    json.append(quote((String) entry.getKey())).append(':')
                            .append(serialize(entry.getValue(), visited));
                    first = false;
                }
                return json.append('}').toString();
            }
            if (value instanceof Collection<?>) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Object item : (Collection<?>) value) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(serialize(item, visited));
                    first = false;
                }
                return json.append(']').toString();
            }
            if (value.getClass().isArray()) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < Array.getLength(value); i++) {
                    if (i > 0) {
                        json.append(',');
                    }
                    json.append(serialize(Array.get(value, i), visited));
                }
                return json.append(']').toString();
            }

            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Field field : value.getClass().getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                if (!field.canAccess(value)) {
                    field.setAccessible(true);
                }
                if (!first) {
                    json.append(',');
                }
                json.append(quote(field.getName())).append(':')
                        .append(serialize(field.get(value), visited));
                first = false;
            }
            return json.append('}').toString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Impossible de sérialiser " + value.getClass().getName(), e);
        } finally {
            visited.remove(value);
        }
    }

    private static String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\b': json.append("\\b"); break;
                case '\f': json.append("\\f"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
            }
        }
        return json.append('"').toString();
    }
}