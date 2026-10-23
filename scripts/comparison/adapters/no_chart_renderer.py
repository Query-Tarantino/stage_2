from comparison.model.comparison import Comparison
from comparison.model.result import Result


class NoChartRenderer:

    def render(self, comparison: Comparison, metric_results: list[Result]) -> None:
        return None
