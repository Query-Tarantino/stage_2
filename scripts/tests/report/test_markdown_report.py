import unittest

from comparison.adapters.no_chart_renderer import NoChartRenderer
from comparison.model.comparison import Comparison
from comparison.report.markdown_report import MarkdownReport
from tests.results import result

COMPARISON = Comparison("Metadata backends", ("sqlite", "mongo"), ("book_by_id_time", "books_by_author_time"))
RESULTS = [result("sqlite", "book_by_id_time", value=57), result("mongo", "book_by_id_time", value=77)]


class FixedChart:

    def render(self, comparison, metric_results):
        return "chart.png"


class MarkdownReportTest(unittest.TestCase):

    def test_includes_winners_and_one_section_per_metric_with_results(self):
        text = MarkdownReport(RESULTS, ["java-query.csv"], NoChartRenderer(), (COMPARISON,)).text()

        self.assertIn("Generated from `java-query.csv`.", text)
        self.assertIn("| `book_by_id_time` | java | 100 | **sqlite** |", text)
        self.assertIn("### `book_by_id_time` (ms, lower is better)", text)
        self.assertNotIn("### `books_by_author_time`", text)

    def test_links_the_chart_when_one_is_rendered(self):
        text = MarkdownReport(RESULTS, [], FixedChart(), (COMPARISON,)).text()

        self.assertIn("![book_by_id_time](chart.png)", text)

    def test_warns_when_some_results_have_no_error_margin(self):
        text = MarkdownReport(RESULTS, [], NoChartRenderer(), (COMPARISON,)).text()

        self.assertIn("> **Warning:** some results have no error margin", text)

    def test_does_not_warn_when_every_result_has_an_error_margin(self):
        results = [result("sqlite", "book_by_id_time", value=57, error=1.2),
                   result("mongo", "book_by_id_time", value=77, error=0)]

        self.assertNotIn("Warning", MarkdownReport(results, [], NoChartRenderer(), (COMPARISON,)).text())

    def test_term_count_is_not_ranked_as_lower_is_better(self):
        comparison = Comparison("Inverted index structures", ("json", "folders"), ("term_count",))
        results = [result("json", "term_count", value=5_000, unit="terms"),
                   result("folders", "term_count", value=5_000, unit="terms")]

        text = MarkdownReport(results, [], NoChartRenderer(), (comparison,)).text()

        self.assertIn("### `term_count` (terms, must be equal for every structure)", text)
