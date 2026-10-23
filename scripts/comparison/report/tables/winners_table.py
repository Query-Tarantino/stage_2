from comparison.model.comparison import Comparison
from comparison.model.ranking import Ranking
from comparison.model.result import Result

HEADER = ["| Metric | Language | Largest N | Best structure |", "|---|---|---:|---|"]


class WinnersTable:

    def __init__(self, comparison: Comparison, results: list[Result]):
        self._comparison = comparison
        self._results = results

    def lines(self) -> list[str]:
        return HEADER + [self._row(metric, language)
                         for metric in self._comparison.metrics
                         for language in self._languages(metric)]

    def _languages(self, metric: str) -> list[str]:
        return sorted({result.language for result in self._comparison.results_of(self._results, metric)})

    def _row(self, metric: str, language: str) -> str:
        own = [result for result in self._comparison.results_of(self._results, metric) if result.language == language]
        largest = max(result.books for result in own)
        ranking = Ranking([result for result in own if result.books == largest])
        return f"| `{metric}` | {language} | {largest:,} | {self._verdict(ranking.winning_structures())} |"

    @staticmethod
    def _verdict(structures: list[str]) -> str:
        return f"tie: {', '.join(structures)}" if len(structures) > 1 else f"**{structures[0]}**"
