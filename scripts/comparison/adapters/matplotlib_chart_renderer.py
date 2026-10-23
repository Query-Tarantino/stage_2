from pathlib import Path

from matplotlib.axes import Axes
from matplotlib.figure import Figure

from comparison.model.comparison import Comparison
from comparison.model.result import Result
from comparison.report.displayed_value import DisplayedValue


class MatplotlibChartRenderer:

    def __init__(self, directory: Path):
        self._directory = directory

    def render(self, comparison: Comparison, metric_results: list[Result]) -> str:
        languages = sorted({result.language for result in metric_results})
        figure = Figure(figsize=(5 * len(languages), 3.5), layout="tight")
        for axis, language in zip(figure.subplots(1, len(languages), squeeze=False)[0], languages):
            self._plot(axis, comparison, [result for result in metric_results if result.language == language])
        return self._saved(figure, f"{comparison.slug()}-{metric_results[0].metric}.png")

    def _saved(self, figure: Figure, name: str) -> str:
        figure.savefig(self._directory / name, dpi=120)
        return name

    @staticmethod
    def _plot(axis: Axes, comparison: Comparison, language_results: list[Result]) -> None:
        for structure in comparison.structures:
            points = sorted((result.books, result.value, result.margin())
                            for result in language_results if result.structure == structure)
            if points:
                books, values, margins = zip(*points)
                axis.errorbar(books, values, yerr=margins, marker="o", capsize=3, label=structure)
        MatplotlibChartRenderer._label(axis, language_results[0])

    @staticmethod
    def _label(axis: Axes, result: Result) -> None:
        axis.set_xscale("log")
        axis.set_title(f"{result.language}: {result.metric} ({DisplayedValue(result).unit()})")
        axis.set_xlabel("books")
        axis.legend()
