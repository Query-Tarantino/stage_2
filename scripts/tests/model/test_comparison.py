import unittest

from comparison.model.comparison import Comparison
from tests.results import result


class ComparisonTest(unittest.TestCase):

    comparison = Comparison("Metadata backends", ("sqlite", "mongo"), ("book_by_id_time",))

    def test_selects_results_of_its_structures_and_metric(self):
        sqlite = result("sqlite", "book_by_id_time")
        results = [sqlite, result("json", "book_by_id_time"), result("sqlite", "query_time")]

        self.assertEqual([sqlite], self.comparison.results_of(results, "book_by_id_time"))

    def test_slug_is_a_lowercase_file_name(self):
        self.assertEqual("metadata-backends", self.comparison.slug())
