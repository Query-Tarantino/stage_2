from dataclasses import dataclass

from comparison.model.result import Result

BYTES_PER_MEGABYTE = 1_000_000


@dataclass(frozen=True)
class DisplayedValue:
    result: Result

    def text(self) -> str:
        value = self._formatted(self.result.value)
        return f"{value} ± {self._formatted(self.result.error)}" if self.result.error else value

    def unit(self) -> str:
        return "MB" if self.result.unit == "bytes" else self.result.unit

    def _formatted(self, number: float) -> str:
        if self.result.unit == "bytes":
            return f"{number / BYTES_PER_MEGABYTE:,.1f}"
        return f"{number:,.0f}" if self.result.value >= 100 else f"{number:,.2f}"
