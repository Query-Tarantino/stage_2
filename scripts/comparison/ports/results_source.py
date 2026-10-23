from typing import Protocol

from comparison.model.result import Result


class ResultsSource(Protocol):

    def results(self) -> list[Result]: ...

    def origins(self) -> list[str]: ...
