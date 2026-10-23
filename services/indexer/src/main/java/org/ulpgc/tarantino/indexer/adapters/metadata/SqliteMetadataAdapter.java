package org.ulpgc.tarantino.indexer.adapters.metadata;

import org.ulpgc.tarantino.indexer.model.book.Book;
import org.ulpgc.tarantino.indexer.ports.datamarts.MetadataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteMetadataAdapter implements MetadataStorage {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS books (
                book_id  INTEGER PRIMARY KEY,
                title    TEXT,
                author   TEXT,
                language TEXT,
                path     TEXT NOT NULL
            )""";
    private static final String UPSERT = "INSERT OR REPLACE INTO books (book_id, title, author, language, path) VALUES (?, ?, ?, ?, ?)";

    private final Path database;
    private PreparedStatement upsert;

    public SqliteMetadataAdapter(Path database) {
        this.database = database;
    }

    @Override
    public void save(Book book) {
        try {
            PreparedStatement statement = upsert();
            bind(statement, book);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save metadata of book " + book.bookId(), e);
        }
    }

    private PreparedStatement upsert() throws SQLException {
        if (upsert == null) {
            upsert = openConnection().prepareStatement(UPSERT);
        }
        return upsert;
    }

    private static void bind(PreparedStatement statement, Book book) throws SQLException {
        statement.setInt(1, book.bookId());
        statement.setString(2, book.title());
        statement.setString(3, book.author());
        statement.setString(4, book.language());
        statement.setString(5, PortablePaths.of(book.path()));
    }

    private Connection openConnection() throws SQLException {
        createParentDirectory();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        createTable(connection);
        return connection;
    }

    private void createParentDirectory() {
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        }
    }
}
