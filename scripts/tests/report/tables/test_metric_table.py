import unittest

from comparison.report.tables.metric_table import MISSING, MetricTable
from tests.results import result


class MetricTableTest(unittest.TestCase):

    def test_has_one_column_per_size_and_bolds_the_best_value(self):
        lines = MetricTable([result("time", books=100, value=74), result("book", books=100, value=0.45),
                             result("book", books=1000, value=0.8)]).lines()

        self.assertEqual("| Language | Structure | N = 100 | N = 1,000 |", lines[0])
        self.assertIn("| java | book | **0.45** | 0.80 |", lines)
        self.assertIn(f"| java | time | 74.00 | {MISSING} |", lines)

    def test_bolds_every_value_tied_with_the_best_one(self):
        lines = MetricTable([result("book", value=0.75, error=0.32), result("batch", value=1.05, error=2.84),
                             result("time", value=3291, error=500)]).lines()

        self.assertIn("| java | batch | **1.05 ± 2.84** |", lines)
        self.assertIn("| java | book | **0.75 ± 0.32** |", lines)
        self.assertIn("| java | time | 3,291 ± 500 |", lines)
