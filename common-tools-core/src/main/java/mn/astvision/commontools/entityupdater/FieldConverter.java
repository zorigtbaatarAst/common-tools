package mn.astvision.commontools.entityupdater;

public interface FieldConverter {
    boolean supports(Class<?> sourceType, Class<?> targetType);

    Object convert(Object source);
}
