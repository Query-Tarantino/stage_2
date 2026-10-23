import csv
from pathlib import Path

from comparison.model.result import Result


class CsvResultsSource:

    def __init__(self, directory: Path):
        self._directory = directory

    def results(self) -> list[Result]:
        return [result for file in self._files() for result in self._results_in(file)]

    def origins(self) -> list[str]:
        return [file.name for file in self._files()]

    def _files(self) -> list[Path]:
        return sorted(self._directory.glob("*.csv"))

    @staticmethod
    def _results_in(file: Path) -> list[Result]:
        with file.open(newline="", encoding="utf-8") as lines:
            return [CsvResultsSource._result(row) for row in csv.DictReader(lines)]

    @staticmethod
    def _result(row: dict[str, str]) -> Result:
        return Result(row["language"], row["structure"], row["metric"], int(row["n_books"]),
                      float(row["value"]), row["unit"], CsvResultsSource._error(row.get("error")))

    @staticmethod
    def _error(text: str | None) -> float | None:
        return float(text) if text else None
