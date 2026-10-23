import tempfile
import unittest
from pathlib import Path

from comparison.adapters.csv_results_source import CsvResultsSource
from comparison.model.result import Result

CSV = ("language,structure,metric,n_books,value,error,unit\n"
       "java,time,write_throughput,100,314.542,12.5,books/s\n"
       "java,time,lookup_time,100,0.43,,µs/op\n")
CSV_WITHOUT_ERRORS = "language,structure,metric,n_books,value,unit\njava,time,write_throughput,100,314.542,books/s\n"


class CsvResultsSourceTest(unittest.TestCase):

    def test_reads_every_result_file_of_the_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "java-crawler.csv").write_text(CSV, encoding="utf-8")
            source = CsvResultsSource(Path(directory))

            self.assertEqual([Result("java", "time", "write_throughput", 100, 314.542, "books/s", 12.5),
                              Result("java", "time", "lookup_time", 100, 0.43, "µs/op", None)], source.results())
            self.assertEqual(["java-crawler.csv"], source.origins())

    def test_reads_results_written_without_the_error_column(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "python-crawler.csv").write_text(CSV_WITHOUT_ERRORS, encoding="utf-8")

            self.assertIsNone(CsvResultsSource(Path(directory)).results()[0].error)
