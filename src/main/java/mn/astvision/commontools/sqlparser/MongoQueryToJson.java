package mn.astvision.commontools.sqlparser;

import org.bson.Document;

public class MongoQueryToJson {
    public static String toMongoShell(MongoQuery q) {
        if (q.getType() == MongoQuery.Type.AGGREGATE) {
            return "db." + q.getCollection() + ".aggregate(" + q.getPipeline() + ")";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("db.").append(q.getCollection()).append(".find(");
        sb.append(q.getFilter().toJson());
        if (!q.getProjection().isEmpty()) {
            sb.append(", ").append(q.getProjection().toJson());
        }
        sb.append(")");
        if (!q.getSort().isEmpty()) {
            sb.append(".sort(").append(q.getSort().toJson()).append(")");
        }
        if (q.getSkip() != null) {
            sb.append(".skip(").append(q.getSkip()).append(")");
        }
        if (q.getLimit() != null) {
            sb.append(".limit(").append(q.getLimit()).append(")");
        }
        return sb.toString();
    }


    private static String toJson(Document doc) {
        return doc.toJson();
    }
}
