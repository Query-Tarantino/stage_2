from comparison.model.result import Result


def result(structure: str, metric: str = "lookup_time", books: int = 100, value: float = 1.0,
           language: str = "java", unit: str = "ms", error: float | None = None) -> Result:
    return Result(language, structure, metric, books, value, unit, error)
