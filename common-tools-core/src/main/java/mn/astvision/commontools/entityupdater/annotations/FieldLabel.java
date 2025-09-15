package mn.astvision.commontools.entityupdater.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface FieldLabel {
    String value();
}
