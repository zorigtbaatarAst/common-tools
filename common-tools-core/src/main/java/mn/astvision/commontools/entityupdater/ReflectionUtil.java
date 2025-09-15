package mn.astvision.commontools.entityupdater;

import mn.astvision.commontools.entityupdater.annotations.FieldLabel;

import java.lang.reflect.Field;
import java.time.temporal.Temporal;
import java.util.*;

public class ReflectionUtil {
    private static final Set<Class<?>> WRAPPER_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Character.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Void.class,
            String.class
    );

    public static boolean isPrimitiveOrWrapperOrString(Class<?> clazz) {
        if (clazz == null) return false;
        return clazz.isPrimitive()
                || WRAPPER_TYPES.contains(clazz)
                || Date.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz); // includes LocalDate, LocalDateTime, etc.
    }

    public static boolean isSimpleValue(Object obj) {
        if (obj == null) return true;
        return isPrimitiveOrWrapperOrString(obj.getClass());
    }

    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    public static Map<String, String> createFieldMapper(Class<?> clazz) {
        Map<String, String> mapper = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            FieldLabel label = field.getAnnotation(FieldLabel.class);
            mapper.put(field.getName(), label != null ? label.value() : field.getName());
        }
        return mapper;
    }

    public static boolean isUserClass(Class<?> clazz) {
        // Only recurse into your own classes, not JDK classes
        String pkg = clazz.getPackageName();
        return !pkg.startsWith("java.") && !clazz.isEnum();
    }


    private Object copyNonNullFields(Object source, boolean skipNulls) {
        try {
            Object copy = source.getClass().getDeclaredConstructor().newInstance();
            for (Field field : getAllFields(source.getClass())) {
                field.setAccessible(true);
                Object value = field.get(source);
                if (skipNulls && value == null) continue;
                field.set(copy, value);
            }
            return copy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy nested object", e);
        }
    }

}
