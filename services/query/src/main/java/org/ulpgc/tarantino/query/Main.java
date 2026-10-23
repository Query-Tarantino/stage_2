package org.ulpgc.tarantino.query;

import org.ulpgc.tarantino.query.commands.SearchCommand;
import org.ulpgc.tarantino.query.model.BookMetadata;
import org.ulpgc.tarantino.query.model.SearchResult;

public class Main {

    public static void main(String[] args) {
        SearchCommand search = QueryFactory.searchCommand(QueryConfig.fromEnvironment());
        print(search.execute(String.join(" ", args)));
    }

    private static void print(SearchResult result) {
        System.out.printf("%d result(s) for \"%s\"%n", result.books().size(), result.query());
        result.books().stream().map(Main::line).forEach(System.out::println);
    }

    private static String line(BookMetadata book) {
        return "  [%d] %s — %s (%s) %s".formatted(book.bookId(), book.title(), book.author(), book.language(), book.path());
    }
}
