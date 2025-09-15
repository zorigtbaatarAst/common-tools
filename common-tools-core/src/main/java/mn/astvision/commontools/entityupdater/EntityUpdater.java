package mn.astvision.commontools.entityupdater;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;

import static mn.astvision.commontools.entityupdater.ReflectionUtil.*;


@Slf4j
public class EntityUpdater {

    private static final Set<String> SYSTEM_FIELDS = Set.of("id", "createdBy", "modifiedBy", "createdDate", "modifiedDate");

    public static <T> UpdateBuilder<T> builder(T target, T updates) {
        return new UpdateBuilder<>(target, updates);
    }

    // ======================
    // Update Builder
    // ======================

    @Data
    public static class UpdateBuilder<T> {
        private final T target;
        private final T updates;

        private boolean skipNulls = true;
        private int maxDepth = Integer.MAX_VALUE;

        private final Set<String> skipFields = new HashSet<>();
        private Set<String> includeFields = null;

        private Map<String, String> fieldMapper = null;
        private List<FieldConverter> converters = new ArrayList<>();
        private Map<String, Predicate<Object>> updateConditions = new HashMap<>();
        private Map<String, FieldUpdateStrategy> fieldStrategies = new HashMap<>();

        private UpdateBuilder(T target, T updates) {
            this.target = target;
            this.updates = updates;
        }

        public UpdateBuilder<T> skipNulls(boolean skipNulls) {
            this.skipNulls = skipNulls;
            return this;
        }

        public UpdateBuilder<T> maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public UpdateBuilder<T> skipFields(String... fields) {
            if (fields != null) this.skipFields.addAll(Arrays.asList(fields));
            return this;
        }

        public UpdateBuilder<T> includeFields(String... fields) {
            if (fields != null) this.includeFields = new HashSet<>(Arrays.asList(fields));
            return this;
        }

        public UpdateBuilder<T> fieldMapper(Map<String, String> mapper) {
            this.fieldMapper = mapper;
            return this;
        }

        public UpdateBuilder<T> resolveFieldMapper() {
            this.fieldMapper = createFieldMapper(target.getClass());
            return this;
        }

        public UpdateBuilder<T> converters(List<FieldConverter> converters) {
            this.converters = converters;
            return this;
        }

        public UpdateBuilder<T> updateConditions(Map<String, Predicate<Object>> conditions) {
            this.updateConditions = conditions;
            return this;
        }

        public UpdateBuilder<T> fieldStrategies(Map<String, FieldUpdateStrategy> strategies) {
            this.fieldStrategies = strategies;
            return this;
        }


        public UpdateBuilder<T> registerConverter(FieldConverter converter) {
            if (converter != null) converters.add(converter);
            return this;
        }

        public UpdateBuilder<T> updateCondition(String field, Predicate<Object> condition) {
            if (field != null && condition != null) updateConditions.put(field, condition);
            return this;
        }

        public UpdateBuilder<T> fieldStrategy(String field, FieldUpdateStrategy strategy) {
            if (field != null && strategy != null) fieldStrategies.put(field, strategy);
            return this;
        }

        // ======================
        // Core: update with report
        // ======================

        public UpdateReport updateWithReport() {
            UpdateReport report = new UpdateReport();
            if (target == null || updates == null) return report;
            updateObject(target, updates, report, "", 0);
            if (fieldMapper != null) report.setFieldMapper(fieldMapper);
            return report;
        }

        public void update() {
            UpdateReport dummyReport = new UpdateReport();
            updateObject(target, updates, dummyReport, "", 0);
        }

        // ======================
        // Recursive field update
        // ======================
        private void updateObject(Object targetObj, Object updatesObj, UpdateReport report, String parentField, int depth) {
            if (targetObj == null || updatesObj == null || depth > maxDepth) return;

            for (Field field : getAllFields(targetObj.getClass())) {
                field.setAccessible(true);

                if (shouldSkip(field, parentField)) continue;

                try {
                    Object currentValue = field.get(targetObj);
                    Object newValue = field.get(updatesObj);

                    newValue = applyConverters(field.getName(), newValue);

                    if (skipNulls && newValue == null) continue;
                    if (!passesUpdateCondition(field.getName(), newValue)) continue;
                    if (applyFieldStrategyIfExists(field, targetObj, newValue, report, parentField)) continue;

                    String fieldPath = buildFieldPath(parentField, field.getName());

                    // Dispatch to specialized update handlers
                    if (handleCollectionField(field, targetObj, currentValue, newValue, report, fieldPath)) continue;
                    if (handleMapField(field, targetObj, currentValue, newValue, report, fieldPath)) continue;
                    if (handlePrimitiveOrStringField(field, targetObj, currentValue, newValue, report, fieldPath))
                        continue;

                    // Nested POJO
                    updateNestedField(field, targetObj, currentValue, newValue, report, fieldPath, depth);

                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to update field: " + field.getName(), e);
                }
            }
        }


        // ---------------------
// Helper methods
// ---------------------

        private Object applyConverters(String fieldName, Object value) {
            Object result = value;
            try {
                for (FieldConverter converter : converters) {
                    result = converter.convert(fieldName, result);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply converters for field: " + fieldName, e);
            }

            return result;
        }

        private boolean passesUpdateCondition(String fieldName, Object newValue) {
            Predicate<Object> condition = updateConditions.get(fieldName);
            return condition == null || condition.test(newValue);
        }

        private boolean applyFieldStrategyIfExists(Field field, Object targetObj, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException {
            FieldUpdateStrategy strategy = fieldStrategies.get(field.getName());
            if (strategy != null) {
                strategy.apply(targetObj, field, newValue, report, parentField);
                return true;
            }
            return false;
        }

        private String buildFieldPath(String parentField, String fieldName) {
            return parentField.isEmpty() ? fieldName : parentField + "." + fieldName;
        }

        private boolean handleCollectionField(Field field, Object targetObj, Object currentValue, Object newValue, UpdateReport report, String fieldPath) throws IllegalAccessException {
            if (newValue instanceof Collection<?> newCol) {
                Collection<?> oldCopy = currentValue instanceof Collection<?> ? new ArrayList<>((Collection<?>) currentValue) : null;
                Collection<?> newCopy = new ArrayList<>(newCol);
                field.set(targetObj, newCopy);
                report.addCollectionChange(fieldPath, oldCopy, newCopy);
                return true;
            }
            return false;
        }

        private boolean handleMapField(Field field, Object targetObj, Object currentValue, Object newValue, UpdateReport report, String fieldPath) throws IllegalAccessException {
            if (newValue instanceof Map<?, ?> newMap) {
                Map<?, ?> oldCopy = currentValue instanceof Map<?, ?> ? new LinkedHashMap<>((Map<?, ?>) currentValue) : null;
                Map<?, ?> newCopy = new LinkedHashMap<>(newMap);
                field.set(targetObj, newCopy);
                report.addMapChange(fieldPath, oldCopy, newCopy);
                return true;
            }
            return false;
        }

        private boolean handlePrimitiveOrStringField(Field field, Object targetObj, Object currentValue, Object newValue, UpdateReport report, String fieldPath) throws IllegalAccessException {
            Class<?> type = field.getType();
            if (isPrimitiveOrWrapperOrString(type)) {
                if (!Objects.equals(currentValue, newValue)) {
                    field.set(targetObj, newValue);
                    report.addChange(fieldPath, currentValue, newValue);
                }
                return true;
            }
            return false;
        }

        private void updateNestedField(Field field, Object parentObj, Object currentValue, Object newValue,
                                       UpdateReport report, String fieldPath, int depth) throws IllegalAccessException {
            if (newValue == null) return;

            if (currentValue == null) {
                // Create nested instance if null
                try {
                    currentValue = field.getType().getDeclaredConstructor().newInstance();
                    field.set(parentObj, currentValue);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create instance for nested field: " + fieldPath, e);
                }
            }

            // Recurse into nested object
            updateObject(currentValue, newValue, report, fieldPath, depth + 1);
        }


        private boolean shouldSkip(Field field, String parentPath) {
            // Fully qualified field path
            String fullPath = parentPath == null || parentPath.isEmpty() ? field.getName() : parentPath + "." + field.getName();

            // Skip transient, static final, system fields
            if (field.isAnnotationPresent(Transient.class)) return true;
            if (Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) return true;
            if (SYSTEM_FIELDS.contains(field.getName())) return true;
            if (skipFields.contains(field.getName())) return true;

            if (includeFields != null && !includeFields.isEmpty()) {
                boolean included = includeFields.stream().anyMatch(f -> f.equals(fullPath) || fullPath.startsWith(f + "."));
                return !included;
            }

            return false;
        }

    }

    // ======================
    // Helper interfaces
    // ======================

    public interface FieldConverter {
        Object convert(String fieldName, Object value);
    }

    public interface FieldUpdateStrategy {
        void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException;
    }
}
