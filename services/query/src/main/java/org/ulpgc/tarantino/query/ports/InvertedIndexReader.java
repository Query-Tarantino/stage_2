package org.ulpgc.tarantino.query.ports;

import java.util.Set;

public interface InvertedIndexReader {

    Set<Integer> postings(String term);
}
