package org.ulpgc.tarantino.bench.explain;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
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

/** {@code bench explain <dir> <mongo uri> <generation>}: writes the {@code explain("executionStats")} of each Mongo query {@code query} runs to the run's {@code explain/} (SPEC §9). */
public class ExplainPlanExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainPlanExporter.class);
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();
    private static final List<String> SEARCH_TERMS = List.of("love", "night");
    private static final String AUTHOR_TERM = "austen";
    private static final String LANGUAGE = "en";
    private static final int YEAR = 1998;
    private static final List<Integer> RESULT_BOOK_IDS = List.of(1342, 84, 11);

    public static void exportPlans(String mongoUri, Path explainDir, int generation) throws IOException {
        Files.createDirectories(explainDir);
        MongoDatabase database = MongoDatabases.database(mongoUri);
        MongoCollection<Document> batches = database.getCollection(MongoCollections.batches(generation));
        MongoCollection<Document> metadata = database.getCollection(MongoCollections.metadata(generation));
        explain(explainDir, "batches_published.json", publishedBatches(batches));
        explain(explainDir, "postings_search.json", postingsSearch(database.getCollection(MongoCollections.postings(generation)), publishedBatchIds(batches)));
        explain(explainDir, "metadata_filter_author.json", idsOf(metadata, authorFilter()));
        explain(explainDir, "metadata_filter_language.json", idsOf(metadata, languageFilter()));
        explain(explainDir, "metadata_filter_language_year.json", idsOf(metadata, Filters.and(languageFilter(), yearFilter())));
        explain(explainDir, "metadata_filter_year.json", idsOf(metadata, yearFilter()));
        explain(explainDir, "metadata_filters.json", idsOf(metadata, Filters.and(authorFilter(), languageFilter(), yearFilter())));
        explain(explainDir, "metadata_results.json", metadataResults(metadata));
        LOGGER.info("Exported explain plans to {}", explainDir);
    }

    private static void explain(Path dir, String file, FindIterable<Document> query) throws IOException {
        Files.writeString(dir.resolve(file), query.explain(ExplainVerbosity.EXECUTION_STATS).toJson(PRETTY));
    }

    private static FindIterable<Document> publishedBatches(MongoCollection<Document> batches) {
        return batches.find(Filters.eq(MongoFields.STATUS, BatchStatus.PUBLISHED.name())).projection(Projections.include(MongoFields.ID));
    }

    private static List<ObjectId> publishedBatchIds(MongoCollection<Document> batches) {
        List<ObjectId> publishedBatchIds = publishedBatches(batches).map(batch -> batch.getObjectId(MongoFields.ID)).into(new ArrayList<>());
        return publishedBatchIds.isEmpty() ? List.of(new ObjectId()) : publishedBatchIds;
    }

    private static FindIterable<Document> postingsSearch(MongoCollection<Document> postings, List<ObjectId> publishedBatchIds) {
        return postings.find(Filters.and(Filters.in(MongoFields.TERM, SEARCH_TERMS), Filters.in(MongoFields.BATCH_ID, publishedBatchIds)))
                .projection(Projections.include(MongoFields.TERM, MongoFields.BOOK_IDS, MongoFields.TFS));
    }

    private static FindIterable<Document> idsOf(MongoCollection<Document> metadata, Bson filter) {
        return metadata.find(filter).projection(Projections.include(MongoFields.ID));
    }

    private static Bson authorFilter() {
        return Filters.all(MongoFields.AUTHOR_TERMS, List.of(AUTHOR_TERM));
    }

    private static Bson languageFilter() {
        return Filters.eq(MongoFields.LANGUAGES, LANGUAGE);
    }

    private static Bson yearFilter() {
        return Filters.eq(MongoFields.YEAR, YEAR);
    }

    private static FindIterable<Document> metadataResults(MongoCollection<Document> metadata) {
        return metadata.find(Filters.in(MongoFields.ID, RESULT_BOOK_IDS))
                .projection(Projections.include(MongoFields.TITLE, MongoFields.AUTHOR, MongoFields.LANGUAGES, MongoFields.YEAR));
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
