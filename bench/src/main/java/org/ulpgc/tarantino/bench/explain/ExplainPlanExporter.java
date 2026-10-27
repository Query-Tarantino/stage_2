package org.ulpgc.tarantino.bench.explain;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ExplainPlanExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainPlanExporter.class);
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();

    public static void exportPlans(String mongoUri, Path explainDir, int generation) throws IOException {
        Files.createDirectories(explainDir);
        try (MongoClient client = MongoClients.create(mongoUri)) {
            MongoDatabase db = client.getDatabase("tarantino");

            MongoCollection<Document> postings = db.getCollection("postings_g" + generation);
            Document postingsExplain = postings.find(Filters.in("term", List.of("love", "night")))
                    .explain(ExplainVerbosity.EXECUTION_STATS);
            Files.writeString(explainDir.resolve("postings_search.json"), postingsExplain.toJson(PRETTY));

            MongoCollection<Document> metadata = db.getCollection("metadata_g" + generation);
            Document metadataFiltersExplain = metadata.find(Filters.and(
                    Filters.all("author_terms", List.of("austen")),
                    Filters.eq("languages", "en"),
                    Filters.eq("year", 1998)))
                    .explain(ExplainVerbosity.EXECUTION_STATS);
            Files.writeString(explainDir.resolve("metadata_filters.json"), metadataFiltersExplain.toJson(PRETTY));

            Document metadataResultsExplain = metadata.find(Filters.in("_id", List.of(1, 2, 3)))
                    .explain(ExplainVerbosity.EXECUTION_STATS);
            Files.writeString(explainDir.resolve("metadata_results.json"), metadataResultsExplain.toJson(PRETTY));

            LOGGER.info("Exported explain plans to {}", explainDir);
        }
    }

    public static void main(String[] args) {
        try {
            Path explainDir = Paths.get(args.length > 0 ? args[0] : "benchmarks/runs/latest/explain");
            String mongoUri = args.length > 1 ? args[1] : System.getenv().getOrDefault("TARANTINO_MONGO_URI", "mongodb://localhost:27017");
            int gen = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            exportPlans(mongoUri, explainDir, gen);
        } catch (Exception e) {
            LOGGER.error("Failed to export explain plans: {}", e.getMessage());
        }
    }
}
