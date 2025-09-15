package mn.astvision.commontools.entityupdater;

import java.lang.reflect.Field;
import java.util.*;

public enum BuiltInFieldUpdateStrategy implements EntityUpdater.FieldUpdateStrategy {
    REPLACE {
        @Override
        public void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException {
            field.setAccessible(true);
            Object oldValue = field.get(target);
            if (!Objects.equals(oldValue, newValue)) {
                field.set(target, newValue);
                if (report != null) {
                    String path = parentField.isEmpty() ? field.getName() : parentField + "." + field.getName();
                    report.addChange(path, oldValue, newValue);
                }
            }
        }
    }, MERGE {
        @Override
        public void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException {
            field.setAccessible(true);
            Object oldValue = field.get(target);
            String name = parentField.isEmpty() ? field.getName() : parentField + "." + field.getName();
            if (oldValue instanceof Map && newValue instanceof Map) {
                Map<Object, Object> merged = new LinkedHashMap<>((Map<?, ?>) oldValue);
                merged.putAll((Map<?, ?>) newValue);
                field.set(target, merged);
                if (report != null) {
                    report.addMapChange(name, (Map<?, ?>) oldValue, merged);
                }
            } else if (oldValue instanceof Collection && newValue instanceof Collection) {
                Collection<Object> merged = new ArrayList<>((Collection<?>) oldValue);
                merged.addAll((Collection<?>) newValue);
                field.set(target, merged);
                if (report != null) {
                    report.addCollectionChange(name, (Collection<?>) oldValue, merged);
                }
            } else {
                REPLACE.apply(target, field, newValue, report, parentField);
            }
        }
    }, APPEND {
        @Override
        public void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException {
            field.setAccessible(true);
            Object oldValue = field.get(target);
            if (oldValue instanceof Collection && newValue instanceof Collection) {
                Collection<Object> appended = new ArrayList<>((Collection<?>) oldValue);
                appended.addAll((Collection<?>) newValue);
                field.set(target, appended);
                if (report != null) {
                    String path = parentField.isEmpty() ? field.getName() : parentField + "." + field.getName();
                    report.addCollectionChange(path, (Collection<?>) oldValue, appended);
                }
            } else {
                throw new IllegalArgumentException("APPEND strategy is only for collections");
            }
        }
    }, IGNORE {
        @Override
        public void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) {
            // do nothing
        }
    };
}
