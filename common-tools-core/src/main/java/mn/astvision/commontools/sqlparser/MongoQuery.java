package mn.astvision.commontools.sqlparser;

import lombok.Getter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MongoQuery {

    private final String collection;
    private final Document filter;
    private final Document projection;
    private final Document sort;
    private final Integer limit;
    private final Integer skip;
    private final Type type;
    private final List<Document> pipeline;

    private MongoQuery(Builder builder) {
        this.collection = builder.collection;
        this.filter = builder.filter;
        this.projection = builder.projection;
        this.sort = builder.sort;
        this.limit = builder.limit;
        this.skip = builder.skip;
        this.type = builder.type;
        this.pipeline = builder.pipeline;
    }

    public enum Type {FIND, AGGREGATE}

    // ✅ Builder
    public static class Builder {
        private String collection;
        private Document filter = new Document();
        private Document projection = new Document();
        private Document sort = new Document();
        private Integer limit;
        private Integer skip;
        private Type type = Type.FIND;
        private List<Document> pipeline = new ArrayList<>();

        public Builder collection(String collection) {
            this.collection = collection;
            return this;
        }

        public Builder filter(Document filter) {
            this.filter = filter;
            return this;
        }

        public Builder projection(Document projection) {
            this.projection = projection;
            return this;
        }

        public Builder sort(Document sort) {
            this.sort = sort;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder skip(Integer skip) {
            this.skip = skip;
            return this;
        }

        public Builder asFind() {
            this.type = Type.FIND;
            return this;
        }

        public Builder asAggregate() {
            this.type = Type.AGGREGATE;
            return this;
        }

        public Builder pipeline(List<Document> pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder addStage(Document stage) {
            this.pipeline.add(stage);
            return this;
        }

        public MongoQuery build() {
            if (collection == null || collection.isBlank()) {
                throw new IllegalStateException("Collection must be set");
            }
            return new MongoQuery(this);
        }
    }
}
