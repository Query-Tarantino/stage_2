package org.ulpgc.tarantino.bench.corpus;

import org.ulpgc.tarantino.common.terms.Tokenizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** The distinct terms of the whole corpus, with the tokenizer and stopwords that indexing uses (SPEC §6.3). */
public class CorpusTerms {

    private final Tokenizer tokenizer;

    public CorpusTerms(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public int distinct(CorpusCache cache) {
        Set<String> terms = new HashSet<>();
        cache.texts().values().forEach(text -> terms.addAll(tokenizer.distinctTerms(read(text))));
        return terms.size();
    }

    private static String read(Path text) {
        try {
            return new String(Files.readAllBytes(text), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
