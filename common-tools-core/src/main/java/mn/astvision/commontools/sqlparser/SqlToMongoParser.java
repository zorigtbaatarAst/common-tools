package mn.astvision.commontools.sqlparser;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlToMongoParser {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+(?<fields>.+?)\\s+FROM\\s+(?<collection>[a-zA-Z0-9_]+)" +
                    "(?:\\s+WHERE\\s+(?<where>.+?))?" +
                    "(?:\\s+GROUP\\s+BY\\s+(?<group>.+?))?" +
                    "(?:\\s+ORDER\\s+BY\\s+(?<order>.+?))?" +
                    "(?:\\s+LIMIT\\s+(?<limit>\\d+))?" +
                    "(?:\\s+OFFSET\\s+(?<offset>\\d+))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "(?<field>[a-zA-Z0-9_]+)\\s*" +
                    "(=|!=|>=|<=|>|<|IN|NOT IN|LIKE|IS NULL|IS NOT NULL)\\s*" +
                    "(?<value>.+)?",
            Pattern.CASE_INSENSITIVE
    );

    public static String toMongoShell(String sql) {
        MongoQuery query = parse(sql);
        return MongoQueryToJson.toMongoShell(query);
    }

    public static MongoQuery parse(String sql) {
        Matcher matcher = SELECT_PATTERN.matcher(sql.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid SQL: " + sql);

        String collection = matcher.group("collection");
        String fieldsPart = matcher.group("fields").trim();
        String wherePart = matcher.group("where");
        String orderPart = matcher.group("order");
        String groupPart = matcher.group("group");
        String limitPart = matcher.group("limit");
        String offsetPart = matcher.group("offset");

        Document filter = (wherePart != null) ? parseWhere(wherePart) : new Document();

        boolean isCount = fieldsPart.equalsIgnoreCase("COUNT(*)");
        boolean isDistinct = fieldsPart.toUpperCase(Locale.ROOT).startsWith("DISTINCT ")
                || fieldsPart.toUpperCase(Locale.ROOT).startsWith("UNIQUE ");

        // ----- COUNT(*)
        if (isCount) {
            List<Document> pipeline = new ArrayList<>();
            if (!filter.isEmpty()) pipeline.add(new Document("$match", filter));
            pipeline.add(new Document("$count", "count"));
            return new MongoQuery.Builder()
                    .collection(collection)
                    .asAggregate()
                    .pipeline(pipeline)
                    .build();
        }

        // ----- DISTINCT field
        if (isDistinct) {
            String distinctField = fieldsPart.replaceAll("(?i)(DISTINCT|UNIQUE)\\s+", "").trim();
            List<Document> pipeline = new ArrayList<>();
            if (!filter.isEmpty()) pipeline.add(new Document("$match", filter));
            pipeline.add(new Document("$group", new Document("_id", "$" + distinctField)));
            pipeline.add(new Document("$project", new Document(distinctField, "$_id").append("_id", 0)));
            return new MongoQuery.Builder()
                    .collection(collection)
                    .asAggregate()
                    .pipeline(pipeline)
                    .build();
        }

        // ----- GROUP BY
        if (groupPart != null) {
            List<Document> pipeline = new ArrayList<>();
            if (!filter.isEmpty()) pipeline.add(new Document("$match", filter));
            String[] groupFields = groupPart.split(",");
            Document groupId = new Document();
            for (String f : groupFields) groupId.put(f.trim(), "$" + f.trim());
            pipeline.add(new Document("$group", new Document("_id", groupId)));
            return new MongoQuery.Builder()
                    .collection(collection)
                    .asAggregate()
                    .pipeline(pipeline)
                    .build();
        }

        // ----- Normal SELECT
        Document projection = new Document();
        if (!fieldsPart.equals("*")) {
            for (String f : fieldsPart.split(",")) projection.put(f.trim(), 1);
        }

        // ----- ORDER BY
        Document sort = new Document();
        if (orderPart != null) {
            for (String clause : orderPart.split(",")) {
                String[] parts = clause.trim().split("\\s+");
                String field = parts[0];
                int direction = (parts.length > 1 && parts[1].equalsIgnoreCase("DESC")) ? -1 : 1;
                sort.put(field, direction);
            }
        }

        Integer limit = (limitPart != null) ? Integer.parseInt(limitPart) : null;
        Integer offset = (offsetPart != null) ? Integer.parseInt(offsetPart) : null;

        return new MongoQuery.Builder()
                .collection(collection)
                .asFind()
                .filter(filter)
                .projection(projection)
                .sort(sort)
                .limit(limit)
                .skip(offset)
                .build();
    }

    private static Document parseWhere(String where) {
        where = stripOuterParentheses(where);

        // ----- BETWEEN (must be first)
        Pattern betweenPattern = Pattern.compile(
                "(\\w+)\\s+BETWEEN\\s+(.+?)\\s+AND\\s+(.+)",  // non-greedy first value
                Pattern.CASE_INSENSITIVE
        );

        Matcher mBetween = betweenPattern.matcher(where);
        if (mBetween.matches()) {  // <-- matches() ensures the entire clause fits
            String field = mBetween.group(1);
            Object start = parseValue(mBetween.group(2));
            Object end = parseValue(mBetween.group(3));
            return new Document(field, new Document("$gte", start).append("$lte", end));
        }

        // Split top-level AND
        List<String> andParts = splitTopLevel(where, " AND ");
        if (andParts.size() > 1) {
            List<Document> docs = new ArrayList<>();
            for (String part : andParts) docs.add(parseWhere(part));
            return new Document("$and", docs);
        }

        // Split top-level OR
        List<String> orParts = splitTopLevel(where, " OR ");
        if (orParts.size() > 1) {
            List<Document> docs = new ArrayList<>();
            for (String part : orParts) docs.add(parseWhere(part));
            return new Document("$or", docs);
        }

        // ----- General condition
        Matcher m = CONDITION_PATTERN.matcher(where);
        if (!m.matches()) throw new IllegalArgumentException("Unsupported WHERE clause: " + where);

        String field = m.group("field");
        String op = m.group(2).toUpperCase(Locale.ROOT);
        String rawValue = m.group("value");

        System.out.println(field);
        System.out.println(op);
        System.out.println(rawValue);

        return switch (op) {
            case "=" -> new Document(field, parseValue(rawValue));
            case "!=" -> new Document(field, new Document("$ne", parseValue(rawValue)));
            case ">" -> new Document(field, new Document("$gt", parseValue(rawValue)));
            case "<" -> new Document(field, new Document("$lt", parseValue(rawValue)));
            case ">=" -> new Document(field, new Document("$gte", parseValue(rawValue)));
            case "<=" -> new Document(field, new Document("$lte", parseValue(rawValue)));
            case "IN" -> new Document(field, new Document("$in", parseArray(rawValue)));
            case "NOT IN" -> new Document(field, new Document("$nin", parseArray(rawValue)));
            case "LIKE" -> new Document(field,
                    new Document("$regex", convertLikeToRegex(rawValue)).append("$options", "i"));
            case "IS NULL" -> new Document(field, null);
            case "IS NOT NULL" -> new Document(field, new Document("$ne", null));
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }


    private static String stripOuterParentheses(String expr) {
        expr = expr.trim();
        while (expr.startsWith("(") && expr.endsWith(")")) {
            int level = 0;
            boolean canStrip = true;
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (c == '(') level++;
                else if (c == ')') level--;
                if (level == 0 && i < expr.length() - 1) {
                    canStrip = false;
                    break;
                }
            }
            if (canStrip) expr = expr.substring(1, expr.length() - 1).trim();
            else break;
        }
        return expr;
    }

    private static List<String> splitTopLevel(String expr, String delimiter) {
        List<String> parts = new ArrayList<>();
        int level = 0, last = 0;
        String upper = expr.toUpperCase(Locale.ROOT);

        for (int i = 0; i <= expr.length() - delimiter.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') level++;
            else if (c == ')') level--;
            if (level == 0 && upper.startsWith(delimiter, i)) {
                parts.add(expr.substring(last, i).trim());
                last = i + delimiter.length();
                i += delimiter.length() - 1;
            }
        }
        parts.add(expr.substring(last).trim());
        return parts;
    }

    private static Object parseValue(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.startsWith("'") && raw.endsWith("'")) return raw.substring(1, raw.length() - 1);
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) return Boolean.parseBoolean(raw);
        if (raw.matches("-?\\d+")) return Integer.parseInt(raw);
        if (raw.matches("-?\\d+\\.\\d+")) return Double.parseDouble(raw);
        return raw;
    }

    private static List<Object> parseArray(String raw) {
        raw = raw.trim();
        if (raw.startsWith("(") && raw.endsWith(")")) raw = raw.substring(1, raw.length() - 1);
        List<Object> list = new ArrayList<>();
        for (String part : raw.split(",")) list.add(parseValue(part.trim()));
        return list;
    }

    private static String convertLikeToRegex(String value) {
        value = value.trim();
        if (value.startsWith("'") && value.endsWith("'")) value = value.substring(1, value.length() - 1);
        return "^" + value.replace("%", ".*").replace("_", ".") + "$";
    }
}
