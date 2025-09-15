package mn.astvision.commontools.entityupdater;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Enhanced report for entity updates
 */
@Getter
@Slf4j
public class UpdateReport {

    private final Map<String, Object[]> changes = new LinkedHashMap<>();
    private final Map<String, Object[]> mappedChanges = new LinkedHashMap<>();
    private Map<String, String> fieldMapper = new HashMap<>();

    // ======================
    // Basic & nested change tracking
    // ======================

    public void addChange(String field, Object oldValue, Object newValue) {
        changes.put(field, new Object[]{oldValue, newValue});
        String mappedName = fieldMapper.getOrDefault(field, field);
        mappedChanges.put(mappedName, new Object[]{oldValue, newValue});
    }

    public void addNestedChange(String parentField, String childField, Object oldValue, Object newValue) {
        String fullField = parentField + "." + childField;
        addChange(fullField, oldValue, newValue);
    }

    // ======================
    // Collection diff tracking
    // ======================

    public void addCollectionChange(String field, Collection<?> oldValue, Collection<?> newValue) {
        Map<String, Object> diff = diffCollections(oldValue, newValue);
        if (!diff.isEmpty()) {
            addChange(field, oldValue, newValue);
            changes.put(field + "_diff", new Object[]{null, diff});
        }
    }

    private static Map<String, Object> diffCollections(Collection<?> oldValue, Collection<?> newValue) {
        Set<Object> oldSet = oldValue == null ? Collections.emptySet() : new LinkedHashSet<>(oldValue);
        Set<Object> newSet = newValue == null ? Collections.emptySet() : new LinkedHashSet<>(newValue);

        Set<Object> added = new LinkedHashSet<>(newSet);
        added.removeAll(oldSet);

        Set<Object> removed = new LinkedHashSet<>(oldSet);
        removed.removeAll(newSet);

        Map<String, Object> diff = new LinkedHashMap<>();
        if (!added.isEmpty()) diff.put("added", added);
        if (!removed.isEmpty()) diff.put("removed", removed);
        return diff;
    }

    // ======================
    // Map diff tracking
    // ======================

    public void addMapChange(String field, Map<?, ?> oldValue, Map<?, ?> newValue) {
        Map<String, Object> diff = diffMaps(oldValue, newValue);
        if (!diff.isEmpty()) {
            addChange(field, oldValue, newValue);
            changes.put(field + "_diff", new Object[]{null, diff});
        }
    }

    private static Map<String, Object> diffMaps(Map<?, ?> oldValue, Map<?, ?> newValue) {
        Map<Object, Object> oldMap = oldValue == null ? Collections.emptyMap() : new LinkedHashMap<>(oldValue);
        Map<Object, Object> newMap = newValue == null ? Collections.emptyMap() : new LinkedHashMap<>(newValue);

        Set<Object> allKeys = new LinkedHashSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());

        Set<Object> addedKeys = new LinkedHashSet<>();
        Set<Object> removedKeys = new LinkedHashSet<>();
        Map<Object, Object[]> changedValues = new LinkedHashMap<>();

        for (Object key : allKeys) {
            boolean inOld = oldMap.containsKey(key);
            boolean inNew = newMap.containsKey(key);

            if (!inOld && inNew) addedKeys.add(key);
            else if (inOld && !inNew) removedKeys.add(key);
            else {
                Object oldVal = oldMap.get(key);
                Object newVal = newMap.get(key);
                if (!Objects.equals(oldVal, newVal)) changedValues.put(key, new Object[]{oldVal, newVal});
            }
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        if (!addedKeys.isEmpty()) diff.put("addedKeys", addedKeys);
        if (!removedKeys.isEmpty()) diff.put("removedKeys", removedKeys);
        if (!changedValues.isEmpty()) diff.put("changedValues", changedValues);

        return diff;
    }

    // ======================
    // Field mapping support
    // ======================

    public void setFieldMapper(Map<String, String> mapper) {
        this.fieldMapper = mapper != null ? mapper : new HashMap<>();
        rebuildMappedChanges();
    }

    private void rebuildMappedChanges() {
        mappedChanges.clear();
        for (Map.Entry<String, Object[]> entry : changes.entrySet()) {
            String mappedName = fieldMapper.getOrDefault(entry.getKey(), entry.getKey());
            mappedChanges.put(mappedName, entry.getValue());
        }
    }

    // ======================
    // MongoDB integration
    // ======================

    public Update toMongoUpdate() {
        Update update = new Update();
        for (Map.Entry<String, Object[]> entry : changes.entrySet()) {
            String field = entry.getKey();
            if (field.endsWith("_diff")) continue;

            Object newValue = entry.getValue()[1];
            if (newValue == null) update.unset(field);
            else {
                if (newValue instanceof LocalDate)
                    newValue = Date.from(((LocalDate) newValue).atStartOfDay(ZoneId.systemDefault()).toInstant());
                else if (newValue instanceof LocalDateTime)
                    newValue = Date.from(((LocalDateTime) newValue).atZone(ZoneId.systemDefault()).toInstant());
                update.set(field, newValue);
            }
        }
        return update;
    }

    public String toMongoUpdateQueryJson() {
        try {
            if (changes.isEmpty()) return "{}";
            Document doc = toMongoUpdate().getUpdateObject();
            return doc.toJson();
        } catch (Exception e) {
            log.error("Failed to build Mongo Update JSON", e);
            return "{}";
        }
    }

    // ======================
    // String & HTML renderers
    // ======================

    public String toMappedString() {
        rebuildMappedChanges();
        if (mappedChanges.isEmpty()) return "Update Report: None";

        StringBuilder sb = new StringBuilder();
        mappedChanges.forEach((field, vals) -> {
            if (field.endsWith("_diff")) sb.append(field).append(": ").append(vals[1]).append("\n");
            else sb.append(field).append(": ").append(vals[0]).append(" -> ").append(vals[1]).append("\n");
        });
        return sb.toString();
    }

    public String toMappedHtmlFallback() {
        rebuildMappedChanges();
        if (mappedChanges.isEmpty()) return "<div>Update Report: <em>None</em></div>";

        StringBuilder sb = new StringBuilder("<div class=\"update-report\">");
        mappedChanges.forEach((field, vals) -> {
            String safeField = escapeHtml(field);
            String from = vals != null && vals.length > 0 ? escapeHtml(String.valueOf(vals[0])) : "";
            String to = vals != null && vals.length > 1 ? escapeHtml(String.valueOf(vals[1])) : "";
            sb.append("<div class=\"update-line\"><strong>").append(safeField).append("</strong>: ")
                    .append(from).append(" &rarr; ").append(to)
                    .append("</div>");
        });
        sb.append("</div>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                case '/' -> out.append("&#x2F;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        if (changes.isEmpty()) return "Update Report: None";
        StringBuilder sb = new StringBuilder();
        changes.forEach((field, vals) -> sb.append(field).append(": ").append(vals[0]).append(" -> ").append(vals[1]).append("\n"));
        return sb.toString();
    }
}
