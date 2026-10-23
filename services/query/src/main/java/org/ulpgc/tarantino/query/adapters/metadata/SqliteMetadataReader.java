package org.ulpgc.tarantino.query.adapters.metadata;

import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.ports.MetadataReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SqliteMetadataReader implements MetadataReader {

    private static final String BOOK_BY_ID = "SELECT book_id, title, author, language, path FROM books WHERE book_id = ?";
    private static final String BOOKS_BY_AUTHOR =
            "SELECT book_id, title, author, language, path FROM books WHERE author LIKE ? ESCAPE '\\' ORDER BY book_id";

    private final Path database;
    private final Map<String, PreparedStatement> statements = new HashMap<>();
    private Connection connection;

    public SqliteMetadataReader(Path database) {
        this.database = database;
    }

    @Override
    public Optional<BookMetadata> book(int bookId) {
        return books(BOOK_BY_ID, statement -> statement.setInt(1, bookId)).stream().findFirst();
    }

    @Override
    public List<BookMetadata> booksBy(String author) {
        return books(BOOKS_BY_AUTHOR, statement -> statement.setString(1, "%" + literal(author) + "%"));
    }

    /** The author as a LIKE pattern that matches it literally: % and _ are not wildcards (SPEC §8.2). */
    private static String literal(String author) {
        return author.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<BookMetadata> books(String sql, Parameters parameters) {
        return connection != null || Files.exists(database) ? queriedBooks(sql, parameters) : List.of();
    }

    private List<BookMetadata> queriedBooks(String sql, Parameters parameters) {
        try {
            PreparedStatement statement = statement(sql);
            parameters.bindTo(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return booksIn(rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query " + database, e);
        }
    }

    private PreparedStatement statement(String sql) throws SQLException {
        PreparedStatement statement = statements.get(sql);
        if (statement == null) {
            statement = connection().prepareStatement(sql);
            statements.put(sql, statement);
        }
        return statement;
    }

    private Connection connection() throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        }
        return connection;
    }

    private static List<BookMetadata> booksIn(ResultSet rows) throws SQLException {
        List<BookMetadata> books = new ArrayList<>();
        while (rows.next()) {
            books.add(bookAt(rows));
        }
        return books;
    }

    private static BookMetadata bookAt(ResultSet row) throws SQLException {
        return new BookMetadata(row.getInt("book_id"), row.getString("title"), row.getString("author"),
                row.getString("language"), Path.of(row.getString("path")));
    }

    @FunctionalInterface
    private interface Parameters {
        void bindTo(PreparedStatement statement) throws SQLException;
    }
}
