package org.ulpgc.tarantino.indexer.adapters.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.indexer.model.book.Book;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteMetadataAdapterTest {

    @TempDir
    Path directory;

    @Test
    void createsTheDatabaseAndUpsertsBooks() throws SQLException {
        Path database = directory.resolve("datamarts/metadata.db");
        SqliteMetadataAdapter metadata = new SqliteMetadataAdapter(database);
        metadata.save(new Book(5, "Old title", null, "English", Path.of("datalake", "5", "body.txt")));
        metadata.save(new Book(5, "Robinson Crusoe", "Daniel Defoe", "English", Path.of("datalake", "5", "body.txt")));

        assertEquals(List.of(List.of("5", "Robinson Crusoe", "Daniel Defoe", "English", "datalake/5/body.txt")), rows(database));
    }

    @Test
    void storesMissingFieldsAsNull() throws SQLException {
        Path database = directory.resolve("metadata.db");
        new SqliteMetadataAdapter(database).save(new Book(7, null, null, null, Path.of("7.body.txt")));

        assertEquals(Arrays.asList("7", null, null, null, "7.body.txt"), rows(database).getFirst());
    }

    private static List<List<String>> rows(Path database) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM books ORDER BY book_id")) {
            List<List<String>> result = new ArrayList<>();
            while (rows.next()) {
                result.add(Arrays.asList(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5)));
            }
            return result;
        }
    }
}
