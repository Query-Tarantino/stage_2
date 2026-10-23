import tempfile
import unittest
from pathlib import Path

from comparison.adapters.no_chart_renderer import NoChartRenderer
from comparison.commands.build_comparison_report import BuildComparisonReport
from comparison.commands.no_results_error import NoResultsError
from tests.results import result


class FixedSource:

    def __init__(self, results):
        self._results = results

    def results(self):
        return self._results

    def origins(self):
        return ["java-crawler.csv"]


class BuildComparisonReportTest(unittest.TestCase):

    def test_writes_the_report_creating_its_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory, "report", "comparison.md")

            BuildComparisonReport(FixedSource([result("time")]), NoChartRenderer(), output).execute()

            self.assertTrue(output.read_text(encoding="utf-8").startswith("# Data structure comparison"))

    def test_fails_without_results(self):
        command = BuildComparisonReport(FixedSource([]), NoChartRenderer(), Path("unused.md"))

        self.assertRaises(NoResultsError, command.execute)
