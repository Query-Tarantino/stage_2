from comparison.model.comparison import COMPARISONS, Comparison
from comparison.model.ranking import is_higher_better, is_same_for_every_structure
from comparison.model.result import Result
from comparison.ports.chart_renderer import ChartRenderer
from comparison.report.displayed_value import DisplayedValue
from comparison.report.tables.metric_table import MetricTable
from comparison.report.tables.winners_table import WinnersTable


class MarkdownReport:

    def __init__(self, results: list[Result], origins: list[str], charts: ChartRenderer,
                 comparisons: tuple[Comparison, ...] = COMPARISONS):
        self._results = results
        self._origins = origins
        self._charts = charts
        self._comparisons = comparisons

    def text(self) -> str:
        sections = [line for comparison in self._comparisons for line in self._section(comparison)]
        return "\n".join(self._heading() + sections)

    def _heading(self) -> list[str]:
        origins = ", ".join(f"`{origin}`" for origin in self._origins)
        return ["# Data structure comparison", "", f"Generated from {origins}.",
                "Values are the mean ± the half-width of its 95% confidence interval. Per language and size, "
                "the best value and every value whose interval overlaps it are in **bold**: those structures "
                "are tied.", ""] + self._missing_errors_warning()

    def _missing_errors_warning(self) -> list[str]:
        if all(result.error is not None for result in self._results):
            return []
        return ["> **Warning:** some results have no error margin (a quick run, or a results file without the "
                "`error` column). Ties cannot be detected for them, so the best value in **bold** may win only by "
                "noise: trust only large differences.", ""]

    def _section(self, comparison: Comparison) -> list[str]:
        title = [f"## {comparison.title}: {', '.join(comparison.structures)}", "", "### Best structure", ""]
        metrics = [line for metric in comparison.metrics for line in self._metric_section(comparison, metric)]
        return title + WinnersTable(comparison, self._results).lines() + [""] + metrics

    def _metric_section(self, comparison: Comparison, metric: str) -> list[str]:
        results = comparison.results_of(self._results, metric)
        if not results:
            return []
        return self._metric_heading(results[0]) + MetricTable(results).lines() + self._chart(comparison, results) + [""]

    @staticmethod
    def _metric_heading(result: Result) -> list[str]:
        direction = "higher is better" if is_higher_better(result.metric) else "lower is better"
        if is_same_for_every_structure(result.metric):
            direction = "must be equal for every structure"
        return [f"### `{result.metric}` ({DisplayedValue(result).unit()}, {direction})", ""]

    def _chart(self, comparison: Comparison, results: list[Result]) -> list[str]:
        image = self._charts.render(comparison, results)
        return ["", f"![{results[0].metric}]({image})"] if image else []
