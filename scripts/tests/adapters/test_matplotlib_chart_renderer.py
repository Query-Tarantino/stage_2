import importlib.util
import tempfile
import unittest
from pathlib import Path

from comparison.model.comparison import Comparison
from tests.results import result


@unittest.skipIf(importlib.util.find_spec("matplotlib") is None, "matplotlib is not installed")
class MatplotlibChartRendererTest(unittest.TestCase):

    def test_saves_one_chart_per_metric_named_after_the_comparison(self):
        from comparison.adapters.matplotlib_chart_renderer import MatplotlibChartRenderer
        comparison = Comparison("Datalake structures", ("time", "book"), ("lookup_time",))
        results = [result("time", books=100), result("time", books=1000), result("book", books=100)]

        with tempfile.TemporaryDirectory() as directory:
            name = MatplotlibChartRenderer(Path(directory)).render(comparison, results)

            self.assertEqual("datalake-structures-lookup_time.png", name)
            self.assertTrue(Path(directory, name).stat().st_size > 0)
