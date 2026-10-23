from dataclasses import dataclass


@dataclass(frozen=True)
class Result:
    language: str
    structure: str
    metric: str
    books: int
    value: float
    unit: str
    error: float | None = None

    def margin(self) -> float:
        return self.error or 0.0

    def overlaps(self, other: "Result") -> bool:
        return abs(self.value - other.value) <= self.margin() + other.margin()
