package org.ulpgc.tarantino.crawler.benchmarking.support.files;

/**
 * What a directory tree takes: regular files, directories below the root, the sum of file sizes, and that
 * sum with each file rounded up to whole blocks of its file system (SPEC §11, disk_allocated).
 */
public record Footprint(long files, long directories, long bytes, long allocatedBytes) {

    public static final Footprint NONE = new Footprint(0, 0, 0, 0);
}
