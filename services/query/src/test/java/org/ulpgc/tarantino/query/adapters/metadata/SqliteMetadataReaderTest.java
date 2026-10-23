package org.ulpgc.tarantino.query.adapters.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.query.model.BookMetadata;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteMetadataReaderTest {

    @TempDir
    Path directory;

    private Path database;

    @BeforeEach
    void createDatabase() throws SQLException {
        database = directory.resolve("metadata.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE books (book_id INTEGER PRIMARY KEY, title TEXT, author TEXT, language TEXT, path TEXT NOT NULL)");
            statement.execute("INSERT INTO books VALUES (76, 'Huckleberry Finn', 'Mark Twain', 'English', 'datalake/76/body.txt')");
            statement.execute("INSERT INTO books VALUES (74, 'Tom Sawyer', 'Mark Twain', 'English', 'datalake/74/body.txt')");
            statement.execute("INSERT INTO books VALUES (84, 'Frankenstein', 'Mary Shelley', 'English', 'datalake/84/body.txt')");
        }
    }

    @Test
    void findsABookById() {
        assertEquals(Optional.of(new BookMetadata(84, "Frankenstein", "Mary Shelley", "English", Path.of("datalake/84/body.txt"))),
                new SqliteMetadataReader(database).book(84));
        assertEquals(Optional.empty(), new SqliteMetadataReader(database).book(1));
    }

    @Test
    void findsBooksByCaseInsensitiveAuthorSubstringOrderedById() {
        assertEquals(List.of(74, 76), new SqliteMetadataReader(database).booksBy("twain").stream().map(BookMetadata::bookId).toList());
    }

    @Test
    void matchesPercentUnderscoreAndBackslashLiterally() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO books VALUES (1, 'One', 'A_B\\C', 'English', 'datalake/1/body.txt')");
            statement.execute("INSERT INTO books VALUES (2, 'Two', 'AxB', 'English', 'datalake/2/body.txt')");
        }
        SqliteMetadataReader reader = new SqliteMetadataReader(database);

        assertEquals(List.of(1), reader.booksBy("a_b\\c").stream().map(BookMetadata::bookId).toList());
        assertEquals(List.of(), reader.booksBy("a_b").stream().filter(book -> book.bookId() == 2).toList());
        assertEquals(List.of(), reader.booksBy("%"));
    }

    @Test
    void keepsItsConnectionAndSeesBooksSavedAfterItsFirstQuery() throws SQLException {
        SqliteMetadataReader reader = new SqliteMetadataReader(database);
        assertEquals(Optional.empty(), reader.book(1342));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO books VALUES (1342, 'Pride and Prejudice', 'Jane Austen', 'English', 'datalake/1342/body.txt')");
        }

        assertEquals(Optional.of("Pride and Prejudice"), reader.book(1342).map(BookMetadata::title));
    }

    @Test
    void treatsAMissingDatabaseAsEmpty() {
        assertEquals(Optional.empty(), new SqliteMetadataReader(directory.resolve("missing.db")).book(84));
    }
}
