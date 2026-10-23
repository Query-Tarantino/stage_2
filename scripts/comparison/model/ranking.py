from comparison.model.result import Result

HIGHER_IS_BETTER = frozenset({"write_throughput", "recovery_ok"})
SAME_FOR_EVERY_STRUCTURE = frozenset({"term_count"})


def is_higher_better(metric: str) -> bool:
    return metric in HIGHER_IS_BETTER


def is_same_for_every_structure(metric: str) -> bool:
    return metric in SAME_FOR_EVERY_STRUCTURE


class Ranking:

    def __init__(self, candidates: list[Result]):
        self._candidates = candidates

    def best(self) -> Result:
        choose = max if is_higher_better(self._candidates[0].metric) else min
        return choose(self._candidates, key=lambda candidate: candidate.value)

    def winners(self) -> list[Result]:
        best = self.best()
        return [candidate for candidate in self._candidates if candidate.overlaps(best)]

    def winning_structures(self) -> list[str]:
        return list(dict.fromkeys(winner.structure for winner in self.winners()))

    def is_winner(self, result: Result) -> bool:
        return len(self._candidates) > 1 and result in self.winners()
