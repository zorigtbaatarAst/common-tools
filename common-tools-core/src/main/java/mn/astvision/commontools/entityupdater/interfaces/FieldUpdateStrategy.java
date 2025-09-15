package mn.astvision.commontools.entityupdater.interfaces;

import mn.astvision.commontools.entityupdater.UpdateReport;

import java.lang.reflect.Field;

public interface FieldUpdateStrategy {
    void apply(Object target, Field field, Object newValue, UpdateReport report, String parentField) throws IllegalAccessException;
}
