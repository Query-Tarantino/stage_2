from typing import Protocol

from comparison.model.comparison import Comparison
from comparison.model.result import Result


class ChartRenderer(Protocol):

    def render(self, comparison: Comparison, metric_results: list[Result]) -> str | None: ...
