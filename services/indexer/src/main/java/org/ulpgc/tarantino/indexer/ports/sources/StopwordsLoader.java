package org.ulpgc.tarantino.indexer.ports.sources;

import java.util.Set;

public interface StopwordsLoader {

    Set<String> stopwords();
}
