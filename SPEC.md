# Query Tarantino — Stage 1 Specification

This document is the contract shared by every language implementation (Java, Python, C++).
Two implementations are **conformant** when, given the same `../Query_Tarantino-Java/workload` and the same configuration,
they produce equivalent datalakes, datamarts, control files and search results, so that benchmark
differences come only from the language and the storage structure.

"Equivalent" means equal content, not byte-identical files: JSON whitespace, key order in documents
and SQLite page layout are free. Everything else in this document is normative.

**Java is the reference implementation.** Where a rule is stated through Java behaviour, Python and C++ use
the closest equivalent listed in §14. Conformance has two levels:

- **Equal results** (§1–§10): the same datalake, datamarts, control files and search results, checked by
  the cases of §13.
- **Comparable measurements** (§11): every implementation does the same work in each measured operation,
  with the same data structures and algorithms as far as its language allows, so that differences come
  from the language and not from a different design.

The command-line interface, the layout of the code and the tests every implementation has are in §15–§17.

## 1. General rules

- Book ids are positive integers.
- Every text file is read and written as **UTF-8 without BOM**, with **LF** (`\n`) line endings,
  on every operating system.
- Paths stored as data (metadata `path`) use `/` as separator and are relative to the working
  directory, exactly as built from the configured root (e.g. `datalake/20250925/14/1342.body.txt`).
- `strip` removes leading and trailing whitespace as Java `String.strip` defines it (`Character.isWhitespace`):
  U+0009–U+000D, U+001C–U+001F, U+0020, U+1680, U+2000–U+2006, U+2008–U+200A, U+2028, U+2029, U+205F and
  U+3000. It does **not** remove U+0085, U+00A0, U+2007 or U+202F. Python's bare `str.strip()` removes
  those four too, so Python passes the characters above explicitly (§14): with bare `str.strip()`, 1 of the
  2 800 books of the benchmark cache already gets a different body.
- A **line** ends only at `\n`. `\r`, U+0085, U+2028 and U+2029 are ordinary characters inside a line.
- Letters and case mapping follow the Unicode version of the runtime: 16.0 in Java 25 and ICU 76, 15.1 in
  Python 3.13. Each implementation records it (§11); §12 gives the effect of the difference.
- Every service runs from the project root; relative roots are resolved against it.

## 2. Configuration

| Variable                       | Default                     | Values                     |
|--------------------------------|-----------------------------|----------------------------|
| `TARANTINO_DATALAKE`           | `datalake`                  | path                       |
| `TARANTINO_DATAMARTS`          | `datamarts`                 | path                       |
| `TARANTINO_CONTROL`            | `control`                   | path                       |
| `TARANTINO_BENCHMARKS`         | `benchmarks`                | path                       |
| `TARANTINO_WORKLOAD`           | `workload`                  | path                       |
| `TARANTINO_DATALAKE_LAYOUT`    | `time`                      | `time`, `book`, `batch`    |
| `TARANTINO_INDEX`              | `json`                      | `json`, `folders`, `mongo` |
| `TARANTINO_METADATA`           | `sqlite`                    | `sqlite`, `mongo`          |
| `TARANTINO_MONGO_URI`          | `mongodb://localhost:27017` | connection string          |
| `TARANTINO_PARALLEL_DOWNLOADS` | `8`                         | positive integer           |
| `TARANTINO_INDEX_BATCH`        | `100`                       | positive integer           |
| `TARANTINO_MIRROR`             | (none)                      | path, or empty             |

An unknown value is a configuration error and must stop the service with a message naming the value.

## 3. Workload

All files live in `TARANTINO_WORKLOAD`, one entry per line; lines are stripped and empty lines ignored.

| File             | Content                                                               |
|------------------|-----------------------------------------------------------------------|
| `book_ids.txt`   | Candidate ids for the benchmark cache, in order (1 to 4000).          |
| `sample_ids.txt` | Small sample dataset; default candidates of the control service (§9). |
| `stopwords.txt`  | Stopwords; each entry is stripped and lowercased (see §7) on load.    |
| `queries.txt`    | Search benchmark workload, one `<category>: <query>` per line (§11).  |
| `conformance/`   | Conformance cases every implementation must pass (§13).               |

## 4. Download

- URL: `https://mirror.cs.odu.edu/gutenberg-epub/<id>/pg<id>.txt`. This is the official high-speed mirror
  of Project Gutenberg at Old Dominion University (listed in `https://www.gutenberg.org/MIRRORS.ALL`); it
  serves the same generated files as `www.gutenberg.org/cache/epub`. Books are downloaded only from official
  mirrors, never from `www.gutenberg.org`, whose robot policy forbids automated access to the website, so
  redirects are followed only within the mirror's host (at most 5): a redirect to any other host fails
  with `NETWORK_ERROR` before any request is sent there.
- The response body is decoded as UTF-8.
- HTTP 200 returns the text. HTTP 404 fails with `NOT_FOUND`. Any other status, timeout or I/O error
  fails with `NETWORK_ERROR`, except the two that mean the mirror is busy, below.
- Every request has a 30 second timeout, to connect and to receive the answer, and sends the header
  `User-Agent: query-tarantino/1.0 (ULPGC Big Data course project)`, so the mirror's operators can tell
  who is downloading.
- **Busy mirror.** HTTP 429 (Too Many Requests) and 503 (Service Unavailable) are retried, at most 5 times,
  after waiting what the `Retry-After` header asks, in seconds or as an HTTP date, or 1, 2, 4, 8 and 16
  seconds on successive answers without it. The wait applies to every download from the mirror, as
  several run at once (§9): none sends its next request before it ends. If the mirror asks to wait more
  than 5 minutes, or is still busy after 5 retries, the download fails with `NETWORK_ERROR`.
- A failed download stores nothing.
- **Local mirror.** When `TARANTINO_MIRROR` is set, the crawler reads `<mirror>/<id>/pg<id>.txt` from a
  local copy of the same generated collection instead of downloading it, decoded as UTF-8. A missing file
  fails with `NOT_FOUND` and any other I/O error with `NETWORK_ERROR`. The copy is made in bulk with
  rsync, Project Gutenberg's documented way to mirror the collection (the website itself is for human
  users only), for example
  `rsync -av --include='*/' --include='pg[0-9]*.txt' --exclude='*' rsync.ibiblio.org::gutenberg-epub/ <mirror>/`
  for the plain texts alone. One transfer instead of one request per book is what makes a collection of
  hundreds of thousands of books practical to ingest. The books of the mirror are its directories named by
  a book id that hold that `pg<id>.txt`; without a candidates file, the control layer takes them all (§9).

## 5. Header and body split

1. Replace every `\r\n` with `\n`.
2. Find the first match of the start marker:
   `\*\*\* ?START OF (THE|THIS) PROJECT GUTENBERG EBOOK[^\n]*\n`
3. Find the first match of the end marker **after the end of the start marker**:
   `\*\*\* ?END OF (THE|THIS) PROJECT GUTENBERG EBOOK`
4. If either marker is missing, fail with `MISSING_MARKERS` and store nothing.
5. `header` = text before the start marker, stripped.
   `body` = text between the end of the start marker (its whole line included) and the start of the
   end marker, stripped. The footer is discarded.

Each marker is also the earliest occurrence of its literal forms: `***START OF THE`, `*** START OF THE`,
`***START OF THIS` and `*** START OF THIS`, each followed by ` PROJECT GUTENBERG EBOOK` (and the same with
`END`); the start marker then runs to the next `\n`, which it includes, and is missing if there is none.
C++ finds them that way (§14): `std::regex` is slow and, in libstdc++, can exhaust the stack on texts of
hundreds of kilobytes.

Failure reasons are exactly: `NOT_FOUND`, `NETWORK_ERROR`, `MISSING_MARKERS`, `STORAGE_ERROR`.

## 6. Datalake

Every layout stores exactly two files per book, `header` and `body`, with the content of §5.

| Layout  | Header file                              | Body file                              |
|---------|------------------------------------------|----------------------------------------|
| `time`  | `<root>/<YYYYMMDD>/<HH>/<id>.header.txt` | `<root>/<YYYYMMDD>/<HH>/<id>.body.txt` |
| `book`  | `<root>/<id>/header.txt`                 | `<root>/<id>/body.txt`                 |
| `batch` | `<root>/<id div 1000>/<id>.header.txt`   | `<root>/<id div 1000>/<id>.body.txt`   |

- `time`: date and hour of the moment the book is saved, in **UTC**, zero-padded
  (`20250925`, `14`). Books saved in different hours end up in different directories.
- `batch`: the directory is the integer division of the id by 1000, without padding
  (`1342` → `1/`, `64317` → `64/`).

Writing and lookup:

1. Each file is written to `<file>.tmp` and then renamed over the final name (atomic move, Java
   `Files.move` with `ATOMIC_MOVE`, §14). Files are not synced to disk (§12).
2. The header is written before the body. **A book exists in the datalake if and only if its body
   file exists**, so an interrupted write never produces a book without header.
3. Saving a book that is already stored does not create a second copy (see §9, ingestion).
4. Lookup by id: `book` and `batch` compute the path directly and check that the body file exists; `time`
   walks `<root>` depth first, up to depth 3, and stops at the first file with the body's name. A lookup
   keeps nothing in memory for the next one and does not read the files.
5. Before ingesting anything, the crawler and the control layer remove what an interrupted run left
   behind: every `.tmp` file, every header whose body does not exist next to it, and every directory
   under `<root>` left empty. Without it `time` would keep, in the old hour directory, the header and
   the `.tmp` body of a book that the resumed run stores again in a new one. It runs once at start,
   with no other process writing the datalake.

New books detection lists the ids of the books stored since an instant:

- `time`: every book in the hour directories from the hour that contains the instant (UTC) onwards,
  decided by the directory names alone.
- `book` and `batch`: every book whose body file was last modified at or after the instant, comparing
  times at the full precision the file system keeps (nanoseconds on APFS and ext4).

## 7. Metadata extraction and tokenization

**Header fields.** For each of `Title`, `Author` and `Language`, take the first match of
`^<Field>:[ \t]*(.+)$` in multiline mode over the header, where only `\n` ends a line (§1; Java adds
`Pattern.UNIX_LINES`), and strip it. Equivalently, without regular expressions: the first line that starts
with `<Field>:` and has at least one character after the colon; the value is the rest of that line,
stripped, so a value made only of blanks is the empty string, not `null`. A missing field is `null`.
The value is stored as written (e.g. `English`, not `en`).

**Tokenizer.** Applied to the body when indexing and to the query when searching:

1. Lowercase the whole text at once with the locale-independent full Unicode mapping, which is
   context-sensitive (a final `Σ` becomes `ς`) and may lengthen the text (`İ` becomes `i` + U+0307):
   Java `toLowerCase(Locale.ROOT)`, Python `str.lower`, C++ ICU `icu::UnicodeString::toLower` with the root
   locale (§14). Lowercasing character by character is not conformant.
2. A term is a maximal run of Unicode letters, i.e. characters of category `L`: Java scans code points with
   `Character.isLetter`, which is exactly category `L` (equivalent to `\p{L}+`); Python uses the `regex`
   package's `\p{L}+` (the standard `re` module has no `\p{L}`) or `unicodedata.category`; C++ ICU's
   `u_isalpha`.
   Digits, apostrophes, hyphens and any other character split terms:
   `don't` → `don`, `t`; `1984year` → `year`.
3. Discard terms shorter than **2 code points** and terms in the stopword list.
4. No stemming and no Unicode normalization.

Indexing counts the frequency of each term per book in a hash map; the storage structures below keep only
book ids.

On the 2 800 books of the benchmark cache, Java and Python (with the strip of §1, and either `regex` or
`unicodedata`) give exactly the same header, body and term frequencies for every book (checked).

## 8. Datamarts

### 8.1 Inverted index

Adding a book adds its id to the postings of each of its terms. Postings never contain duplicates,
so indexing the same book twice leaves the index unchanged. Postings are ordered ascending wherever
the structure has an order.

| Structure | Location                                         | Format |
|-----------|--------------------------------------------------|--------|
| `json`    | `<datamarts>/inverted_index.json`                | One JSON object: term → array of ids, e.g. `{"island": [5, 1342]}` |
| `folders` | `<datamarts>/inverted_index/<c>/<name>.txt`      | One id per line; `<name>` encodes the term and `<c>` its first code point (below) |
| `mongo`   | database `tarantino`*, collection `inverted_index` | One document per term: `{"term": "island", "postings": [5, 1342]}`, unique index on `term` |

- `json` is rewritten completely, and atomically (`.tmp` + rename), each time the index is flushed.
- `folders` rewrites, on each flush, every affected term file with the union of its stored ids and the
  new ones, sorted and atomically (`.tmp` + rename). Term files that gain no new id are not touched.
- `folders` and `mongo` write the affected terms of a flush in ascending ordinal string order (UTF-16
  code units in Java, code points in Python, UTF-8 bytes in C++, which order like code points; they differ
  from Java only for supplementary characters), so
  the files of each `<c>` directory are written together and MongoDB's `term` index receives its keys
  in order. The order is not observable in the result, but it changes the cost of a flush.
- `folders` file names: in the UTF-8 bytes of the term, ASCII `a`–`z` are kept and every other byte is
  written as `%` and two uppercase hexadecimal digits (`island` → `island`, `écume` → `%C3%A9cume`). If
  the result is longer than 200 characters, the name is `#` followed by the lowercase hexadecimal SHA-256
  of the term's UTF-8 bytes. `<c>` is the first code point of the term encoded the same way
  (`%C3%A9/%C3%A9cume.txt`). Case-insensitive and normalization-insensitive file systems (APFS, NTFS)
  would otherwise store distinct terms such as `shape` and `ſhape`, or `café` in NFC and NFD, in the same
  file, and names would exceed the 255-byte limit for long terms.
- `mongo` upserts one document per affected term, adding the ids with `$addToSet`; readers must treat
  `postings` as a set.

**Writing.** What is added since the last flush is kept in memory and written by the flush:

- `json` loads the whole stored index when it is opened, or on the first add, and keeps it in memory as a
  sorted map of sorted sets (Java `TreeMap<String, TreeSet<Integer>>`); every flush serializes all of it
  with the language's usual JSON library (§14).
- `folders` and `mongo` keep only the pending postings, a hash map from term to the sorted set of its new
  ids, sorted by term when flushed. On a flush, `folders` reads each affected term file, writes it only if
  it gained an id, and creates each `<c>` directory at most once; `mongo` sends **one unordered bulk write**
  (`ordered: false`) holding one `updateOne` per affected term, `{"term": t}` with
  `{"$addToSet": {"postings": {"$each": [ids]}}}` and `upsert: true`, never one request per term (the
  driver splits it into batches of the server's limit). The unique index on `term` is created when the
  structure is opened.

**Reading** (the query service). No structure caches postings between lookups, other than `json` holding
its file:

- `json` loads the whole file the first time a term is looked up and keeps it as a hash map from term to a
  hash set of ids (Java `HashMap<String, HashSet<Integer>>`, which Jackson builds).
- `folders` reads the term's file on every lookup; a missing file is an empty list.
- `mongo` sends one `find` by `term` on every lookup and reads the whole document; a missing document is an
  empty list.

**MongoDB clients.** Each process opens one client per connection string the first time it needs it and
reuses it for every database and collection, with the driver's default pool, timeouts and write concern.

\* Or the database named in the path of `TARANTINO_MONGO_URI` (`mongodb://host:27017/<database>`).

### 8.2 Metadata

Fields: `book_id`, `title`, `author`, `language`, `path` (the body file of §6, see §1 for the format).
Saving a book that already exists replaces its row or document. Each backend opens its connection once
and reuses it for every save and query; each save is committed before it returns, because §9 marks a
book as indexed right after.

| Backend  | Location                            | Schema |
|----------|-------------------------------------|--------|
| `sqlite` | `<datamarts>/metadata.db`, table `books` | `book_id INTEGER PRIMARY KEY, title TEXT, author TEXT, language TEXT, path TEXT NOT NULL` |
| `mongo`  | database `tarantino`*, collection `books` | Same fields, unique index on `book_id`, missing fields stored as `null` |

- `sqlite` sets no `PRAGMA`, so it keeps SQLite's defaults: rollback journal (`journal_mode=DELETE`),
  `synchronous=FULL` and autocommit, which makes each save its own transaction. The table is created with
  `CREATE TABLE IF NOT EXISTS` when the connection opens, and every save runs the same prepared
  `INSERT OR REPLACE`. Python's `sqlite3` module does not autocommit by default (it opens a transaction and
  leaves it uncommitted), so it connects in autocommit mode (§14).
- `mongo` saves a book with one `replaceOne` by `book_id` with `upsert: true`; the unique index is created
  when the backend is opened.
- Reading a book by id is one `SELECT … WHERE book_id = ?`, or one `find` by `book_id`.
- Reading the books of an author (§10) is `SELECT … WHERE author LIKE ? ESCAPE '\' ORDER BY book_id`, with
  the author between `%` signs and its `\`, `%` and `_` escaped with `\`, so they match literally; or a
  `find` with `{"author": {"$regex": <the author with every regular expression metacharacter escaped>,
  "$options": "i"}}` sorted by `book_id` ascending.
- If `metadata.db` does not exist, readers return no books instead of creating it.

## 9. Control layer

**State files** in `TARANTINO_CONTROL`: `downloaded_books.txt` and `indexed_books.txt`, one id per line.
They are append-only. On read, lines that are not a whole number after stripping are ignored,
so a partially written last line is harmless. File order is preserved. They are read once when a run
starts; during the run the control layer keeps the state in memory and only appends to the files, so a
step costs the same however many books are already done.

**Ingestion** of a book is idempotent: if the datalake already contains it (§6), it succeeds with the
existing paths without downloading again.

**Downloads.** Up to D books are downloaded at once, D = `TARANTINO_PARALLEL_DOWNLOADS` (8 by default).
Whenever fewer than D are running, the next candidates, in candidates-file order, that are not in
`downloaded_books.txt`, have not failed during this run and are not being downloaded start downloading;
they finish in any order. Only the download (§4) and the split (§5) run in parallel: looking a book up in
the datalake before downloading it, storing it (§6), the state files and the datamarts are used by one
thread at a time, the control layer's own, so the datalake keeps a single writer and `time` lookups never
walk a directory that another thread is changing.

**Books to index** are the ids of `downloaded_books.txt`, in file order (the order in which their
downloads finished), that are not in `indexed_books.txt` and have not failed during this run. They are
indexed in batches of K books, K = `TARANTINO_INDEX_BATCH` (100 by default).

**Next step**, evaluated before every step, once the downloads above are started:

1. `INDEX` the first K books to index, if there are at least K, or all of them if there is at least one
   and no download is running.
2. Otherwise, if a download is running, `DOWNLOAD`: wait for the first one to finish, whichever it is,
   and store its book.
3. Otherwise `IDLE`: the run ends.

Downloads go on while a batch is indexed. With D = 1 books are downloaded one at a time, in
candidates-file order. Each download runs in a thread of its own (§14): Java starts a virtual thread per
download; Python uses a `ThreadPoolExecutor` of D threads, which suits downloads because waiting on the
network releases the GIL; C++ a pool of D threads.

The control layer prints one line per step (§15).

**After each step**, each id is appended to the matching state file **only if it succeeded**.
A failed id is remembered in memory for the current run and retried on the next run.
Indexing a batch writes the metadata of each book, adds every book to the inverted index and flushes
it **once**; only then are the books that succeeded marked as indexed. An interrupted batch marks none
of its books, so the next run indexes it again, which is harmless because indexing a book twice leaves
the index unchanged (§8.1) and metadata is replaced (§8.2).

Batching only changes when the index is written, never its content: after the same books, the index is
the same for any K, and so are search results. A book becomes searchable when its batch is flushed.
Flushing once per batch instead of once per book is what makes it cheaper: `json` rewrites the whole
file on every flush and `folders` every term file of the flushed books (see `incremental_update_time`
and `batch_update_time`, §11). K = 1 indexes book by book.

Candidates come from `TARANTINO_WORKLOAD/<file>`, where `<file>` is the first argument of the control
service. Without an argument they are every book of the local mirror (§4) in ascending id order when
`TARANTINO_MIRROR` is set, and the ids of `sample_ids.txt` otherwise.

## 10. Search

1. Turn the query into terms with the tokenizer of §7, removing duplicates and keeping the order in
   which they first appear.
2. If no term remains, the result is empty.
3. The result is the set of books whose postings contain **every** term (AND), computed as Java does: for
   each term, in query order, look its postings up (§8.1) and copy them into a sorted set (Java `TreeSet`,
   C++ `std::set`); keep in the first set only the ids present in each following one (Java `retainAll`).
   Every term is looked up even once the result is empty, and postings are not reordered by length; a
   better plan (shortest list first, stop when empty) is a Stage 2 change for every language. Python has
   no sorted set in its standard library: it builds a `set` per term, intersects with `&=` and sorts the
   result once (§12).
4. Each id, in ascending order, is resolved through the metadata backend with one lookup by id (§8.2), not
   one query for all of them; ids without metadata are dropped.
5. Results are ordered by `book_id` ascending.

Author lookup is a case-insensitive substring match (§8.2): SQLite's `LIKE` folds ASCII letters only and
MongoDB's `i` option folds every letter (§12).

## 11. Benchmarks

**Dataset.** `../Query_Tarantino-Java/scripts/fill_cache.sh` copies raw texts once to `<benchmarks>/cache/<id>.txt`, unchanged,
following `book_ids.txt`, until the cache holds 2 800 books. By default it copies the candidates in one
rsync transfer from `rsync.ibiblio.org::gutenberg-epub` (§4, local mirror) to `<benchmarks>/mirror/`;
with `TARANTINO_CACHE_SOURCE=http`, for networks that block the rsync port, it downloads them over HTTP
(§4), 16 at a time. Both give the same files. Only books with both markers (§5) are cached; missing ids
(404 or absent from the mirror) and ids without markers are listed in `<benchmarks>/cache/skipped.txt` and
never retried, while network errors are retried on the next run.
The cached ids, in `book_ids.txt` order, are the **cache order**. Every implementation reads from the
same cache, so the network is never measured.

**Sizes.** N ∈ {100, 300, 1000}, evenly spread on a logarithmic scale. The dataset of size N is the first
N ids in cache order. The
**new books** are always the same 100: positions 1001 to 1100 in cache order. They never belong to a
dataset and are the same for every N, so results at different N differ only by N.

**Simulated download time.** The datalake benchmarks save the books of a dataset as a crawl
downloading 100 books per hour would: the book at position i (0-based) is saved at T₀ + ⌊i / 100⌋ hours,
so `time` spreads N books over ⌈N / 100⌉ hour directories. In new books detection that instant is also
set as the modification time of each body file, which `book` and `batch` read (§6); the other
benchmarks leave it unchanged, so writing costs the same for every layout.

**Execution.**

- Benchmarks target any Unix system: Linux is the reference platform and macOS is compatible. Same
  machine for all languages, with nothing else running.
- The files that benchmarks write go under `<benchmarks>/tmp.noindex/` and are kept out of desktop file
  indexers: macOS Spotlight skips directories whose name ends in `.noindex`; on a Linux desktop whose
  indexer covers the project (KDE Baloo indexes the whole home directory by default), exclude `<benchmarks>`
  in its settings. Servers have no indexer.
- The machine stays awake, on mains power and out of any power-saving mode for the whole run, for example
  `systemd-inhibit --what=idle:sleep <command>` on Linux or `caffeinate -i <command>` on macOS. A laptop
  that sleeps stops the run, and one that slows down under sustained load, as fanless laptops do,
  penalizes whatever is measured later.
- Same MongoDB for all languages: **MongoDB 7.0 on the benchmark machine itself, not inside a virtual
  machine**, with the WiredTiger cache fixed at 1 GB (`storage.wiredTiger.engineConfig.cacheSizeGB: 1`, or
  `--wiredTigerCacheSizeGB 1`), using one database per benchmark. On Linux, a native install or a Docker
  container with host networking both qualify, since containers share the host kernel. On macOS (and
  Windows), Docker runs containers inside a virtual machine that reserves its own memory and adds a
  network round trip to every operation, so MongoDB must be installed natively there.
- Record CPU, RAM, OS and the MongoDB version, and the versions of the runtime or compiler, of Unicode
  (§1) and of every library of §14, SQLite's included (Java's `sqlite-jdbc` bundles its own SQLite, 3.53.4
  in the reference run).
- **Runtimes.** Java 25 (HotSpot) runs each measured process with a fixed 4 GB heap (`-Xms4g -Xmx4g`) and
  the default G1 collector. Python runs on CPython 3.13 or later, without flags. C++ is C++20 built in
  release mode (`-O2 -DNDEBUG`) with Clang or GCC.
- **Harness.** Java uses JMH. Python and C++ follow the same scheme with a runner of their own that starts
  one operating-system process per benchmark, structure, size and pass (Python `subprocess`, C++
  `fork`/`exec`) and times with a monotonic clock (Java `System.nanoTime`, Python `time.perf_counter_ns`,
  C++ `std::chrono::steady_clock`). For one operation, the runner repeats it for one second and the sample
  is that second divided by the operations completed (JMH `AverageTime`); queries are also timed one by one
  (JMH `SampleTime`). C++ keeps every result observable, as JMH's `Blackhole` does, so that the compiler
  cannot drop the work (a `volatile` sink or `benchmark::DoNotOptimize`).
- **Random choices** (a book, a query, an author) are uniform and drawn inside the timed operation, as in
  Java: Java `ThreadLocalRandom`, Python `random.randrange`, C++ `std::mt19937_64` with
  `std::uniform_int_distribution`.
- Each benchmark runs in **2 separate processes** for every structure and size, one in each of two
  passes over all the benchmarks of a service. The second pass takes the structures and the sizes in
  reverse order, so whatever drifts during a run, such as the temperature of the machine or the writes
  left by the benchmark before, weighs alike on every structure instead of always on the last ones.
  Each process runs warm-up iterations, which are discarded, and then measured iterations. Every
  measured iteration is one **sample**:

| Kind of metric                                            | Warm-up per process                                 | Measured per process | Samples |
|-----------------------------------------------------------|-----------------------------------------------------|----------------------|--------:|
| Full build                                                | building 100 books                                  | 3 runs               |       6 |
| Incremental and batch update                              | the first 10 new books into an empty index, one by one and then together | 3 runs |  6 |
| Other whole runs: write, insertion, index open            | 1 run                                               | 3 runs               |       6 |
| One operation: lookup, detection, query, metadata queries | 3 × 1 second                                        | 5 × 1 second         |      10 |

  Warming up only compiles the code, so full builds and updates warm up on a small input with the same code
  instead of a whole run of N books, which for `folders` alone would cost minutes per process.
  A sample of the first kind is the time of one run. A sample of the second kind is the mean time per
  operation during one second.
- Before every run of the first kind, warm-up included, storage is reset without timing it: emptied,
  except for index open, which only reads, and the incremental and batch updates, which start every run
  from exactly the index of the dataset of size N. Putting back right before each run only the terms of
  the new books is enough, and costs a fraction of copying the whole index.
- The **value** of a metric is the mean of its samples. Its **error** is the half-width of the 95%
  confidence interval of that mean, t₀.₉₇₅,ₙ₋₁ · s / √n over the n samples, computed from the samples
  (JMH's own `Score Error` is fixed at 99.9%). Two structures are **tied** when their intervals overlap:
  |a − b| ≤ error(a) + error(b).
- 95% is the usual confidence level in statistics; JMH's 99.9% is far stricter. With it, 2 processes give
  narrower intervals than 3 processes at 99.9% (t₀.₉₇₅,₅ / √6 = 1.05 against t₀.₉₉₉₅,₈ / √9 = 1.68 for 6
  and 9 samples) with a third fewer runs. What 2 processes capture less is the variation between
  processes, and samples of one process are not fully independent, so intervals may be slightly
  optimistic.

**Validation.** Before measuring, each benchmark checks that the structure gives the correct result,
and the benchmark run fails without writing results otherwise:

- Lookup: every book of the dataset is found, and both of its files exist.
- New books detection: exactly the ids of the 100 new books.
- Query: for every query of `queries.txt`, the same ids as a reference computed in memory from the
  tokenized books of the dataset (§7, §10).
- Full index build: after the last build, every structure holds exactly as many terms as the distinct
  terms of the tokenized books of the dataset (`term_count`).
- Metadata queries: every id returns the book with that id, and every author returns at least every
  book of the dataset with exactly that author.

**Results.** Each implementation writes `<benchmarks>/results/<language>-<service>.csv`, `<language>` being
`java`, `python` or `cpp`, with the header
`language,structure,metric,n_books,value,error,unit`, e.g. `java,time,write_throughput,1000,812.4,35.2,books/s`.
`error` is in the unit of the metric. It is `0` for exact metrics (counts, sizes and `recovery_ok`) and
empty when it cannot be computed (fewer than 2 samples). For a value derived from a time, such as
`write_throughput` = N / time, the error is the value times the relative error of the time.
With the results of every language in that directory, `../Query_Tarantino-Java/scripts/compare_results.py` builds the
comparison report in `<benchmarks>/report/`, where tied structures share the first place.

| Group    | Structures                   | Metric                     | Unit    |
|----------|------------------------------|----------------------------|---------|
| Datalake | `time`, `book`, `batch`      | `write_throughput`         | books/s |
|          |                              | `lookup_time`              | µs/op   |
|          |                              | `new_books_detection_time` | ms      |
|          |                              | `recovery_ok`              | 0 or 1  |
|          |                              | `recovery_leftover_files`  | files   |
|          |                              | `file_count`               | files   |
|          |                              | `directory_count`          | dirs    |
|          |                              | `disk_usage`               | bytes   |
|          |                              | `disk_allocated`           | bytes   |
| Index    | `json`, `folders`, `mongo`   | `full_build_time`          | ms      |
|          |                              | `incremental_update_time`  | ms/book |
|          |                              | `batch_update_time`        | ms/book |
|          |                              | `index_open_time`          | ms      |
|          |                              | `query_time`               | µs/query|
|          |                              | `query_time_p99`           | µs/query|
|          |                              | `query_time_<category>`    | µs/query|
|          |                              | `build_memory`             | bytes   |
|          |                              | `index_memory`             | bytes   |
|          |                              | `memory_allocated`         | bytes   |
|          |                              | `term_count`               | terms   |
|          |                              | `disk_usage`               | bytes   |
|          |                              | `disk_allocated`           | bytes   |
| Metadata | `sqlite`, `mongo`            | `bulk_insertion_time`      | ms      |
|          |                              | `book_by_id_time`          | µs/op   |
|          |                              | `books_by_author_time`     | µs/op   |

- `write_throughput`: N divided by the time to ingest the N cached books as the crawler does (§9,
  ingestion): look the book up in the datalake (§6, lookup), then read, split (§5) and store it. The
  lookup is part of real ingestion, and for `time` it searches the whole datalake. Each run starts from an
  empty datalake.
- `lookup_time`: time to find the header and body paths of a random book of the dataset (§6, lookup, which
  checks that the body exists without reading it).
- `new_books_detection_time`: time to list the 100 new books (§6, new books detection). The N books of
  the dataset are saved first, ending one day before the new ones, which are then saved at the current time.
- `recovery_ok`: measured with 100 books. Half of them are ingested; the next one is interrupted after
  its header is written, leaving its body as `.tmp`; one hour later a new run removes incomplete writes
  (§6) and ingests all 100 again. It is 1 if every book ends with exactly one body file, 0 otherwise.
- `recovery_leftover_files`: after the same scenario, the number of files in the datalake that are
  neither the header nor the body of a stored book (`.tmp` files and orphaned headers).
- `file_count`, `directory_count`, `disk_usage`, `disk_allocated`: the datalake after writing N books;
  directories do not count the root, and `disk_usage` is the sum of file sizes in bytes.
- `disk_allocated`, for the datalake and the file-based indexes: the same files, each size rounded up to
  whole blocks of the file system that holds them (an empty file takes none). The block size is the one
  the file system reports: Java `FileStore.getBlockSize()`, Python `os.statvfs(path).f_frsize` and C++
  `statvfs(path, &stats)`'s `f_frsize` (not `f_bsize`, the preferred I/O size), 4096 bytes on default APFS,
  ext4 and NTFS. It is close to what the
  files really take: the `folders` index of 2000 books has 587 545 term files and about 55 MB of
  `disk_usage`, but at one 4096-byte block per file at least it takes about 2.4 GB. All the footprint
  metrics of a tree are measured in one walk.
- `full_build_time` and `memory_allocated`: time and bytes allocated to read, split, tokenize and index
  N books into an empty index, flushing once at the end. `memory_allocated` is counted by every thread
  around the build alone, never around emptying the storage, one sample per measured run. It counts
  garbage too: it measures the pressure on the garbage collector, not the memory required. Before its
  first run each process reads the N texts once, untimed, so that every run finds them in the operating
  system cache. A build saves no metadata.
- `build_memory`: memory retained while building, i.e. heap in use after a full garbage collection once
  the N books are added and before the flush, minus the same measure before the build.
- `incremental_update_time`: mean time per book to index the first 10 new books into an index of exactly
  the N books of the dataset book by book, as the control layer does with K = 1 (§9): each book is read
  from the cache, split, tokenized, added and flushed. Only 10 books, because flushing after each one is
  far slower (`folders` rewrites every term file of every book).
- `batch_update_time`: mean time per book to index the 100 new books into an index of exactly the N
  books of the dataset with a single flush at the end, as the control layer does with the default
  K = 100 (§9): the 100 books are read, split, tokenized and added, then flushed once. No update saves
  metadata. Compared with `incremental_update_time`, it shows what batching saves.
- Both updates open the index before timing, as a running control layer has it open: loading it (all of
  `json`) is measured by `index_open_time`, and timing it here would spread it over 10 books in one
  metric and over 100 in the other.
- `index_open_time`: time to open an index of N books from storage with a new reader, holding nothing
  in memory from previous runs, as a new process would, and answer the first query of `queries.txt`.
  The operating system cache and an open MongoDB connection may be reused, so it measures loading the
  index, not starting a process. The query goes through the search of §10 with the metadata reader of
  `query_time`.
- `query_time`: time of a random query of `queries.txt` against an open index of N books, through the
  search of §10 with a metadata reader that returns a fixed record for every id without any I/O (Java
  `QueryWorkload`): metadata is not read, so only the index is measured. Every query is timed; a sample is
  the mean of one measured second.
- `query_time_p99`: the 99th percentile of the query times of each measured second, over the queries
  of `query_time`; its samples are those 10 percentiles. A search engine is judged by its slowest
  queries as much as by its mean.
- `query_time_<category>`: `query_time` restricted to the queries of one category of `queries.txt`,
  measured in the same run: each query is also timed on its own, and a sample is the mean time of the
  queries of the category during one measured second (10 samples). Timing each query adds a few tens of
  nanoseconds, which only matters for the fastest queries. The cost of a query depends mostly on how long
  the postings it reads and intersects are, and terms follow a Zipf distribution, so the workload has 5
  queries of each category:

  | Category   | Queries                                                | Postings read            |
  |------------|--------------------------------------------------------|--------------------------|
  | `frequent` | one term in nearly every book (`love`, `time`)         | one very long list       |
  | `rare`     | one term in 2 or 3 of the first 2000 books, all within the first 100 | one tiny list |
  | `mixed`    | a frequent term and a rare one                         | long list ∩ tiny list    |
  | `long`     | four common terms (`ship captain sea voyage`)          | four long lists          |
  | `empty`    | two rare terms that no book has together               | two tiny lists, no result |
  | `nonascii` | one term with non-ASCII letters (`cæsar`, `façade`)    | encoded file names in `folders` |
- `index_memory`: memory retained by an index of N books open for querying, i.e. heap in use after a
  full garbage collection with the index open and every query of `queries.txt` answered once, minus the
  same measure before opening it. Each process opens the index once without measuring it and then
  measures 5 openings, so there are 10 samples. For `mongo` only the client side is measured, with the
  client already connected.
- `term_count`: distinct terms in the index of N books.
- `bulk_insertion_time`: time to save the metadata of N books one by one, through a single open backend,
  into an empty backend. The headers are parsed before timing; the timed run creates the backend, opens
  it (its connection, and the table or the index if missing; the process's MongoDB client may already
  exist, §8.1) and saves the N books.
- `book_by_id_time`: a random id of the dataset.
- `books_by_author_time`: a random author among the **distinct** authors of the dataset, leaving out books
  without one (at N = 1000, 426 authors for the 974 books that have one). It is not the author of a random
  book, which would favour prolific authors (Stevenson, with 43 books, would come up 4.4% of the time
  instead of 0.23%).
- For `mongo`, `disk_usage` is the `storageSize` + `totalIndexSize` of its collections after an `fsync`;
  that is already allocated storage, so `disk_allocated` equals it.
- `build_memory` is measured 3 times per process, so it has 6 samples; `term_count` is exact.
- **Memory in each language.** `build_memory` and `index_memory` are the memory retained, measured outside
  every timed run:
  - Java: heap in use (`MemoryMXBean`) after three `System.gc()` calls.
  - Python: memory traced by `tracemalloc` after `gc.collect()`. Tracing slows Python down several times,
    so it is started right before these measurements and stopped after them. Like the Java heap, it leaves
    out what native libraries allocate on their own.
  - C++: bytes in use by the allocator, with no collection needed: `malloc_zone_statistics(nullptr, &s)`'s
    `size_in_use` on macOS, `mallinfo2().uordblks` on Linux.

  `memory_allocated` is every byte allocated during the build, garbage included:
  - Java: `ThreadMXBean.getTotalThreadAllocatedBytes()`, every thread.
  - C++: a counter of the sizes requested from a replaced global `operator new`, read before and after.
  - Python has no counter of total allocations (`tracemalloc` gives current and peak memory and
    `sys.getallocatedblocks()` counts live blocks), so it writes no `memory_allocated` rows and the report
    has no Python row for that metric.

## 12. Known limitations

- Terms are used as file names by `folders`. On Windows the reserved names (`con`, `nul`, `aux`, `prn`…)
  cannot be created, so benchmarks for that structure run on macOS or Linux.
- Python 3.13 implements Unicode 15.1, older than the 16.0 of Java 25 and ICU 76: a letter added in 16.0
  is part of a term in Java and C++ and a separator in Python 3.13. No book of the benchmark cache has one
  (§7).
- Python intersects hash sets and sorts the result once (§10), which is cheaper than the sorted trees of
  Java and C++: the algorithm is the same, the standard library is not.
- Python writes no `memory_allocated`, and its memory metrics count only what `tracemalloc` traces (§11).
- For non-ASCII authors, the books found by author differ between SQLite, whose `LIKE` folds ASCII letters
  only, and MongoDB, whose `i` option folds every letter; each backend finds the same books in every
  language.
- `disk_allocated` is an estimate: it leaves out directories and file system metadata, and file systems
  that compress data or store very small files inside their metadata take less than it reports.
- The datalake is not synced to disk after each write, so `write_throughput` measures writes to the
  operating system cache.
- Durability differs between metadata backends: SQLite commits every save to disk before it returns,
  while MongoDB with its default write concern acknowledges a save before flushing its journal (within
  100 ms). `bulk_insertion_time` favours MongoDB on that account.
- MongoDB compresses collections and indexes on disk (WiredTiger), so its `disk_usage` is compressed while
  that of `json` and `folders` is not.
- Memory metrics count the memory of the measured process (§11): for `mongo` the index lives in the
  MongoDB server, whose memory (its 1 GB cache) is not counted.

## 13. Conformance cases

`TARANTINO_WORKLOAD/conformance/` holds language-neutral cases of the rules above, as JSON files with a
`spec` description, an optional `stopwords` list and a `cases` array. Every implementation runs them in its
tests, so it can show it is conformant without comparing datalakes or datamarts by hand. The datalake paths
are checked on both sides: the crawler stores each book at them and the indexer finds it there.

| File                  | Rule | Each case                                                                  |
|-----------------------|------|----------------------------------------------------------------------------|
| `split.json`          | §5   | `raw` text and the expected `header` and `body`, or the expected `failure` |
| `datalake_paths.json` | §6   | `layout`, `id` and `saved_at` instant, and the `header` and `body` paths relative to the root |
| `header_fields.json`  | §7   | `header` and the expected `title`, `author` and `language` (`null` if missing) |
| `terms.json`          | §7   | `text` and its expected term frequencies, with the file's `stopwords`      |
| `folders_names.json`  | §8.1 | `term` and the path of its `folders` file relative to the index root       |
| `query_terms.json`    | §10  | `query` and its expected terms in order, with the file's `stopwords`       |
| `search.json`         | §10  | `query` and the expected ids over the file's `books` and `stopwords`       |

The expected values follow this document, not any implementation. When a rule changes, its cases change
with it, and an implementation that fails a case is not conformant.

## 14. Language equivalents

Java is the reference implementation (see the introduction). Each row gives what Python and C++ use for what Java does; an
implementation uses the listed equivalent unless it shows, with the cases of §13 and the same work per
measured operation, that another one is equivalent.

| Concept                    | Java (reference)                                  | Python                                                   | C++                                                       |
|----------------------------|---------------------------------------------------|----------------------------------------------------------|-----------------------------------------------------------|
| Text                       | `String` (UTF-16)                                 | `str`                                                    | `std::string` in UTF-8; ICU `icu::UnicodeString` for Unicode rules |
| Strip (§1)                 | `String.strip`                                    | `s.strip(JAVA_WHITESPACE)` with the characters of §1, never bare `strip()` | loop over code points with the characters of §1 |
| Lowercase (§7)             | `toLowerCase(Locale.ROOT)`                        | `str.lower`                                              | `icu::UnicodeString::toLower(icu::Locale::getRoot())`; not `std::tolower` or `towlower`, which map one character at a time |
| Letter (§7)                | `Character.isLetter`                              | `regex` package `\p{L}+`, or `unicodedata.category(c)[0] == "L"` | ICU `u_isalpha`                              |
| Markers and header fields  | `java.util.regex` (`UNIX_LINES` for fields)       | `re`                                                     | literal search (§5) and line scan (§7)                    |
| Term frequencies           | `HashMap<String, Integer>`                        | `dict` or `collections.Counter`                          | `std::unordered_map<std::string, int>`                    |
| Sorted map and set         | `TreeMap`, `TreeSet`                              | `dict` and `set`, sorted with `sorted()` when written    | `std::map`, `std::set`                                    |
| Hash map and set           | `HashMap`, `HashSet`                              | `dict`, `set`                                            | `std::unordered_map`, `std::unordered_set`                |
| Atomic replace (§6)        | `Files.move` with `ATOMIC_MOVE`                   | `os.replace`                                             | `std::filesystem::rename`                                 |
| Directory walk (§6)        | `Files.find`, `Files.walk`, `Files.list`          | `os.scandir`                                             | `std::filesystem::directory_iterator`, `recursive_directory_iterator` |
| Modification time (§6, §11) | `Files.getLastModifiedTime`, `setLastModifiedTime` | `os.stat(p).st_mtime_ns`, `os.utime(p, ns=…)`         | `stat`'s `st_mtimespec` (macOS) or `st_mtim` (Linux), `utimensat` |
| Block size (§11)           | `FileStore.getBlockSize()`                        | `os.statvfs(p).f_frsize`                                 | `statvfs(p, &s)`, `s.f_frsize`                            |
| JSON (§8.1)                | Jackson `ObjectMapper`                            | `json` (standard library)                                | nlohmann/json                                             |
| SQLite (§8.2)              | `sqlite-jdbc`, autocommit, one `PreparedStatement` per query | `sqlite3.connect(path, autocommit=True)` (Python 3.12+; `isolation_level=None` before) | C API: `sqlite3_prepare_v2` once, then `bind`, `step`, `reset` per call |
| MongoDB (§8)               | `mongodb-driver-sync`                             | `pymongo`                                                | `mongocxx`, with one `mongocxx::instance` per process     |
| Regular expression escape (§8.2) | `Pattern.quote`                             | `re.escape`                                              | a backslash before each of `\^$.\|?*+()[]{}`             |
| HTTP (§4)                  | `java.net.http.HttpClient`, redirects followed by hand | `urllib.request` with a redirect handler that refuses other hosts | libcurl, `CURLOPT_FOLLOWLOCATION` off, redirects followed by hand |
| Parallel downloads (§9)    | a virtual thread per download                     | `concurrent.futures.ThreadPoolExecutor(max_workers=D)`   | a pool of D `std::thread`s                                |
| Clock (§11)                | `System.nanoTime`                                 | `time.perf_counter_ns`                                   | `std::chrono::steady_clock`                               |
| Random choice (§11)        | `ThreadLocalRandom`                               | `random.randrange`                                       | `std::mt19937_64` and `std::uniform_int_distribution`     |
| Benchmark harness (§11)    | JMH                                               | own runner                                               | own runner (or Google Benchmark, one process per configuration) |
| Retained memory (§11)      | `MemoryMXBean` after `System.gc()`                | `tracemalloc` after `gc.collect()`                       | `malloc_zone_statistics` (macOS), `mallinfo2` (Linux)     |
| Allocated memory (§11)     | `ThreadMXBean.getTotalThreadAllocatedBytes()`     | none                                                     | counter in a replaced `operator new`                      |
| Tests (§17)                | JUnit 5, Testcontainers                           | `unittest` or `pytest`, `testcontainers`                 | GoogleTest                                                |
| Build                      | Maven                                             | `pyproject.toml` (pip or uv)                             | CMake                                                     |

## 15. Command-line interface

Every service is a program run from the project root, configured by §2, that writes UTF-8 lines to
standard output. An argument that is not what the service expects (a book id that is not an integer) or an
invalid configuration (§2) stops it before any work, with a message on standard error and exit status 1.
Otherwise the exit status is 0, even when some books fail: their lines say so.

| Service   | Arguments                                         | What it does                                                    |
|-----------|---------------------------------------------------|-----------------------------------------------------------------|
| `crawler` | book ids                                          | removes incomplete writes (§6), then ingests each id in order, one at a time (§9, ingestion), from the mirror or `TARANTINO_MIRROR` (§4) |
| `indexer` | book ids                                          | indexes them as one batch with one flush (§9), reading the datalake |
| `query`   | the words of the query                            | joins them with single spaces and searches (§10)                |
| `control` | optionally, a candidates file in `TARANTINO_WORKLOAD` | runs the control layer (§9) until it is idle                |

Only `control` reads or writes the control files.

Output, where `<dir>` is the directory of the body file, as built from the datalake root (§1), and
`<REASON>` a failure reason of §5:

| Service   | Lines                                                                                       |
|-----------|---------------------------------------------------------------------------------------------|
| `crawler` | one per id: `[CRAWLER] <id>: stored in <dir>` or `[CRAWLER] <id>: skipped, <REASON>`        |
| `indexer` | one per id: `[INDEXER] <id>: <n> unique terms indexed` or `[INDEXER] <id>: skipped, not found in the datalake` |
| `query`   | `<n> result(s) for "<query>"`, then one per book: two spaces and `[<id>] <title> — <author> (<language>) <path>`, a missing field written `null` |
| `control` | one per step, `[CONTROL] <ACTION> <books>: <detail>`, and finally `[CONTROL] Nothing left to do` |

In `control` lines, `<ACTION>` is `DOWNLOAD` or `INDEX` and `<books>` is the book id, or
`<n> books (<first>…<last>)` for a batch of more than one. `<detail>` is that of the crawler or indexer
line for one book; for a batch, `<n> indexed` or `<k> indexed, <m> skipped`. `—` is U+2014 and `…` U+2026.

## 16. Project structure

Every implementation has the same top level: `../Query_Tarantino-Java/services` with one directory per service (`crawler`,
`indexer`, `query`, `control`); `scripts/` and `workload/`, copied unchanged from the Java repository; and
the runtime directories `datalake/`, `datamarts/`, `control/` and `benchmarks/`, which are not versioned.

Each service has the same layers, with the class names of Java:

| Layer       | Content                                                                                     |
|-------------|---------------------------------------------------------------------------------------------|
| `model/`    | records and pure domain logic, grouped by concept (`model/book`, `model/terms`, `model/failure`) |
| `ports/`    | interfaces the service depends on                                                           |
| `adapters/` | implementations of the ports, one subpackage per functionality and one per compared structure: `datalake/time`, `datalake/book`, `datalake/batch`, `index/json`, `index/folders`, `index/mongo`, `metadata`, `mongo`, `stopwords`, `gutenberg` |
| `commands/` | use cases: `IngestBookCommand`, `IndexBookCommand`, `SearchCommand`, `ControlPipeline`      |
| top level   | `Main`, `<Service>Config` (reads §2) and `<Service>Factory` (builds the adapters from it)   |

Packages hold about three classes each, as a guideline. A helper used by a single structure stays private
to its package; only helpers shared by several structures are public. Tests mirror the packages of what
they test, and benchmarks live in the test tree under `benchmarking/`, by comparison, with their shared
code in `benchmarking/support/`.

| Concept            | Java                                                  | Python                                      | C++                                              |
|--------------------|-------------------------------------------------------|---------------------------------------------|--------------------------------------------------|
| Service sources    | `services/<s>/src/main/java/org/ulpgc/tarantino/<s>/` | `services/<s>/src/tarantino_<s>/`           | `services/<s>/src/` and `services/<s>/include/tarantino/<s>/` |
| Tests              | `services/<s>/src/test/java/…`                        | `services/<s>/tests/`                       | `services/<s>/tests/`                            |
| Package            | package                                               | package (directory with `__init__.py`)      | directory and namespace `tarantino::<s>::<layer>` |
| Port               | `interface`                                           | `typing.Protocol`                           | abstract class with pure virtual functions       |
| Record             | `record`                                              | `@dataclass(frozen=True)`                   | `struct`                                         |
| Entry point        | `Main.main`                                           | `__main__.py`, run as `python -m tarantino_<s>` | `main.cpp`, one executable per service       |
| File names         | `IngestBookCommand.java`                              | `ingest_book_command.py`                    | `IngestBookCommand.hpp` and `.cpp`               |
| Control uses the crawler and indexer | their jars                          | their packages                              | their libraries, linked into its executable      |

## 17. Tests

Every implementation has:

- **Conformance tests** that run every case of §13, each reported by name.
- **Unit tests** for each class with logic, in the test tree, in the package of the class they test.
- **An end-to-end test** that runs the control layer over a temporary local mirror (§4) of three small
  books, with no candidates file, D = 3 and K = 2, once per datalake layout and once per index structure:
  every book reaches the datalake, and a search finds each one with its title and author.
- **MongoDB tests** (the adapters, and the end-to-end run on `mongo`) that use the server at
  `TARANTINO_MONGO_URI` when it answers, in a temporary database of their own dropped after each test;
  otherwise a disposable `mongo:7.0` container when Docker runs (Testcontainers in Java and Python; C++ has
  none and skips); otherwise they are skipped, never failed.

The tests of the comparison report, in `../Query_Tarantino-Java/scripts/tests`, are shared by every implementation.
