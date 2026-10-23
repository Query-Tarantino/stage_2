import unittest

from comparison.model.comparison import Comparison
from comparison.report.tables.winners_table import WinnersTable
from tests.results import result

COMPARISON = Comparison("Datalake structures", ("time", "book", "batch"), ("lookup_time", "recovery_ok"))


class WinnersTableTest(unittest.TestCase):

    def test_names_the_best_structure_at_the_largest_size(self):
        results = [result("time", books=100, value=0.1), result("book", books=100, value=0.5),
                   result("time", books=1000, value=9.0), result("book", books=1000, value=0.6)]

        self.assertIn("| `lookup_time` | java | 1,000 | **book** |", WinnersTable(COMPARISON, results).lines())

    def test_reports_ties(self):
        results = [result(layout, "recovery_ok", value=1) for layout in ("time", "book", "batch")]

        self.assertIn("| `recovery_ok` | java | 100 | tie: time, book, batch |", WinnersTable(COMPARISON, results).lines())
