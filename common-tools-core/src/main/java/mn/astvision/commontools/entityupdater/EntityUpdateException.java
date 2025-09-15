package mn.astvision.commontools.entityupdater;

public class EntityUpdateException extends Exception {
    public EntityUpdateException(String message) {
        super(message);
    }

    public EntityUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}