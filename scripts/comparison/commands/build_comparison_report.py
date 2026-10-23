from pathlib import Path

from comparison.ports.chart_renderer import ChartRenderer
from comparison.ports.results_source import ResultsSource
from comparison.commands.no_results_error import NoResultsError
from comparison.report.markdown_report import MarkdownReport


class BuildComparisonReport:

    def __init__(self, source: ResultsSource, charts: ChartRenderer, output: Path):
        self._source = source
        self._charts = charts
        self._output = output

    def execute(self) -> Path:
        results = self._source.results()
        if not results:
            raise NoResultsError("No benchmark results found; run the benchmarks first")
        self._output.parent.mkdir(parents=True, exist_ok=True)
        self._output.write_text(MarkdownReport(results, self._source.origins(), self._charts).text(), encoding="utf-8")
        return self._output
