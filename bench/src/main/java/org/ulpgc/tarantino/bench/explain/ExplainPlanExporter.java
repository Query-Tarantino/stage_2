package org.ulpgc.tarantino.bench.explain;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ulpgc.tarantino.common.model.BatchStatus;
import org.ulpgc.tarantino.common.mongo.MongoCollections;
import org.ulpgc.tarantino.common.mongo.MongoDatabases;
import org.ulpgc.tarantino.common.mongo.MongoFields;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExplainPlanExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainPlanExporter.class);
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();

    public static void exportPlans(String mongoUri, Path explainDir, int generation) throws IOException {
        Files.createDirectories(explainDir);
        MongoDatabase database = MongoDatabases.database(mongoUri);

        MongoCollection<Document> batches = database.getCollection(MongoCollections.batches(generation));
        Document batchesExplain = batches.find(Filters.eq(MongoFields.STATUS, BatchStatus.PUBLISHED.name()))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("batches_published.json"), batchesExplain.toJson(PRETTY));

        List<ObjectId> publishedBatchIds = batches.find(Filters.eq(MongoFields.STATUS, BatchStatus.PUBLISHED.name()))
                .projection(Projections.include(MongoFields.ID))
                .map(batch -> batch.getObjectId(MongoFields.ID))
                .into(new ArrayList<>());
        if (publishedBatchIds.isEmpty()) {
            publishedBatchIds.add(new ObjectId());
        }

        MongoCollection<Document> postings = database.getCollection(MongoCollections.postings(generation));
        Document postingsExplain = postings.find(Filters.and(
                Filters.in(MongoFields.TERM, List.of("love", "night")),
                Filters.in(MongoFields.BATCH_ID, publishedBatchIds)))
                .projection(Projections.include(MongoFields.TERM, MongoFields.BOOK_IDS, MongoFields.TFS))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("postings_search.json"), postingsExplain.toJson(PRETTY));

        MongoCollection<Document> metadata = database.getCollection(MongoCollections.metadata(generation));
        Document authorFilterExplain = metadata.find(Filters.all(MongoFields.AUTHOR_TERMS, List.of("austen")))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_filter_author.json"), authorFilterExplain.toJson(PRETTY));

        Document langFilterExplain = metadata.find(Filters.eq(MongoFields.LANGUAGES, "en"))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_filter_language.json"), langFilterExplain.toJson(PRETTY));

        Document langYearFilterExplain = metadata.find(Filters.and(
                Filters.eq(MongoFields.LANGUAGES, "en"),
                Filters.eq(MongoFields.YEAR, 1998)))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_filter_language_year.json"), langYearFilterExplain.toJson(PRETTY));

        Document yearFilterExplain = metadata.find(Filters.eq(MongoFields.YEAR, 1998))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_filter_year.json"), yearFilterExplain.toJson(PRETTY));

        Document metadataFiltersExplain = metadata.find(Filters.and(
                Filters.all(MongoFields.AUTHOR_TERMS, List.of("austen")),
                Filters.eq(MongoFields.LANGUAGES, "en"),
                Filters.eq(MongoFields.YEAR, 1998)))
                .projection(Projections.include(MongoFields.ID))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_filters.json"), metadataFiltersExplain.toJson(PRETTY));

        Document metadataResultsExplain = metadata.find(Filters.in(MongoFields.ID, List.of(1342, 84, 11)))
                .projection(Projections.include(MongoFields.TITLE, MongoFields.AUTHOR, MongoFields.LANGUAGES, MongoFields.YEAR))
                .explain(ExplainVerbosity.EXECUTION_STATS);
        Files.writeString(explainDir.resolve("metadata_results.json"), metadataResultsExplain.toJson(PRETTY));

        LOGGER.info("Exported explain plans to {}", explainDir);
    }

    public static void main(String[] args) {
        try {
            Path explainDir = Paths.get(args.length > 0 ? args[0] : "benchmarks/runs/latest/explain");
            String mongoUri = args.length > 1 ? args[1] : System.getenv().getOrDefault("TARANTINO_MONGO_URI", "mongodb://localhost:27017");
            int generation = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            exportPlans(mongoUri, explainDir, generation);
        } catch (Exception exception) {
            LOGGER.error("Failed to export explain plans: {}", exception.getMessage(), exception);
            System.exit(1);
        }
    }
}
