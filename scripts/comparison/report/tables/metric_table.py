from comparison.model.ranking import Ranking
from comparison.model.result import Result
from comparison.report.displayed_value import DisplayedValue

MISSING = "–"


class MetricTable:

    def __init__(self, results: list[Result]):
        self._results = results

    def lines(self) -> list[str]:
        return self._header() + [self._row(language, structure) for language, structure in self._keys()]

    def _sizes(self) -> list[int]:
        return sorted({result.books for result in self._results})

    def _keys(self) -> list[tuple[str, str]]:
        return sorted({(result.language, result.structure) for result in self._results})

    def _header(self) -> list[str]:
        sizes = " | ".join(f"N = {size:,}" for size in self._sizes())
        return [f"| Language | Structure | {sizes} |", "|---|---|" + "---:|" * len(self._sizes())]

    def _row(self, language: str, structure: str) -> str:
        cells = " | ".join(self._cell(language, structure, size) for size in self._sizes())
        return f"| {language} | {structure} | {cells} |"

    def _cell(self, language: str, structure: str, size: int) -> str:
        peers = [result for result in self._results if result.language == language and result.books == size]
        match = next((result for result in peers if result.structure == structure), None)
        if match is None:
            return MISSING
        return self._emphasized(match, Ranking(peers))

    @staticmethod
    def _emphasized(result: Result, ranking: Ranking) -> str:
        text = DisplayedValue(result).text()
        return f"**{text}**" if ranking.is_winner(result) else text
