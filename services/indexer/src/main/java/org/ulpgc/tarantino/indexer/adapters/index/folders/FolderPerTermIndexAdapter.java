package org.ulpgc.tarantino.indexer.adapters.index.folders;

import org.ulpgc.tarantino.indexer.adapters.index.PendingPostings;
import org.ulpgc.tarantino.indexer.model.terms.TermOccurrences;
import org.ulpgc.tarantino.indexer.ports.datamarts.InvertedIndexStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class FolderPerTermIndexAdapter implements InvertedIndexStorage {

    private final Path root;
    private final PendingPostings pending = new PendingPostings();

    public FolderPerTermIndexAdapter(Path root) {
        this.root = root;
    }

    @Override
    public void add(TermOccurrences occurrences) {
        pending.add(occurrences);
    }

    /** Terms come in order, so the files of each folder are written together; each folder is created once. */
    @Override
    public void flush() {
        Set<Path> folders = new HashSet<>();
        pending.drain().forEach((term, ids) -> merge(TermFiles.file(root, term), ids, folders));
    }

    private static void merge(Path file, Set<Integer> ids, Set<Path> folders) {
        TreeSet<Integer> postings = storedPostings(file);
        if (postings.addAll(ids)) {
            if (folders.add(file.getParent())) {
                createDirectories(file.getParent());
            }
            writeAtomically(file, postings);
        }
    }

    private static TreeSet<Integer> storedPostings(Path file) {
        return lines(file).stream().map(Integer::valueOf).collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.exists(file) ? Files.readAllLines(file) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectories(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeAtomically(Path file, TreeSet<Integer> postings) {
        try {
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, content(postings));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String content(TreeSet<Integer> postings) {
        return postings.stream().map(id -> id + "\n").collect(Collectors.joining());
    }
}
