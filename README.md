# common-tools
`commontools` is a lightweight Java utility library providing common tools for entity updates, performance monitoring, memory tracking, and more. 

## **EntityUpdater**

`EntityUpdater` helps you **update Java objects (entities) in a controlled way**, handling nested objects, collections, maps, and providing detailed update reports.

---

### **Features**

- Apply updates from one object to another safely.
- Update nested objects recursively.
- Handle collections (`List`, `Set`) and maps automatically.
- Skip nulls, specific fields, or system-managed fields (`id`, `createdDate`, etc.).
- Transform values during update using **FieldConverters**.
- Apply custom update logic per field using **FieldUpdateStrategy**.
- Get detailed update reports with `UpdateReport`.

---

### **Usage Example**

```java
// Define a field converter to transform values
EntityUpdater.FieldConverter converter = (fieldName, value) -> {
    if ("passportNumber".equals(fieldName) && value instanceof String) {
        return ((String) value).replace(" ", "");
    }
    return value;
};

// Build and execute the updater
UpdateReport report = EntityUpdater.builder(foreignCitizenOpt.get(), update)
        .converters(List.of(converter))     // Apply converters
        .skipNulls(true)                    // Skip null values
        .updateWithReport();                // Perform update and get report


// Access update report
System.out.println("Changed fields: " + report.getChanges());
```
# FieldConverter
#Transform field values before they are applied to the target object.
```java
EntityUpdater.FieldConverter converter = (fieldName, value) -> {
    if ("phoneNumber".equals(fieldName) && value instanceof String) {
        return ((String) value).replaceAll("[^0-9]", "");
    }
    return value;
};
```

# FieldUpdateStrategy
# Apply custom logic per field during update.
```java
EntityUpdater.FieldUpdateStrategy strategy = (target, field, newValue, report, parentField) -> {
    if (field.getName().equals("score")) {
        Integer current = (Integer) field.get(target);
        field.set(target, Math.max(current, (Integer) newValue)); // only update if higher
        report.addChange(parentField + "." + field.getName(), current, newValue);
    }
};
```

#Advanced Usage
```java
UpdateReport report = EntityUpdater.builder(targetObject, updates)
        .skipNulls(true)
        .maxDepth(3)                         // limit nested updates
        .includeFields("name", "address.*")  // update only certain fields
        .fieldStrategies(Map.of("score", strategy))
        .converters(List.of(converter))
        .updateWithReport();
```

### Installation
implementation 'com.yourorg:commontools:1.0.0'
