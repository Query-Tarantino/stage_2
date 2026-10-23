from dataclasses import dataclass

from comparison.model.result import Result


@dataclass(frozen=True)
class Comparison:
    title: str
    structures: tuple[str, ...]
    metrics: tuple[str, ...]

    def slug(self) -> str:
        return self.title.lower().replace(" ", "-")

    def results_of(self, results: list[Result], metric: str) -> list[Result]:
        return [result for result in results if result.metric == metric and result.structure in self.structures]


COMPARISONS = (
    Comparison("Datalake structures", ("time", "book", "batch"),
               ("write_throughput", "lookup_time", "new_books_detection_time", "recovery_ok",
                "recovery_leftover_files", "file_count", "directory_count", "disk_usage", "disk_allocated")),
    Comparison("Inverted index structures", ("json", "folders", "mongo"),
               ("full_build_time", "incremental_update_time", "batch_update_time", "index_open_time",
                "query_time", "query_time_p99", "query_time_frequent", "query_time_rare", "query_time_mixed",
                "query_time_long", "query_time_empty", "query_time_nonascii",
                "build_memory", "index_memory", "memory_allocated", "term_count", "disk_usage", "disk_allocated")),
    Comparison("Metadata backends", ("sqlite", "mongo"),
               ("bulk_insertion_time", "book_by_id_time", "books_by_author_time")),
)
