import importlib.util
import os
from pathlib import Path

from comparison.adapters.csv_results_source import CsvResultsSource
from comparison.adapters.no_chart_renderer import NoChartRenderer
from comparison.commands.build_comparison_report import BuildComparisonReport
from comparison.ports.chart_renderer import ChartRenderer

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def build_comparison_report_command() -> BuildComparisonReport:
    benchmarks = PROJECT_ROOT / os.environ.get("TARANTINO_BENCHMARKS", "benchmarks")
    report = benchmarks / "report"
    return BuildComparisonReport(CsvResultsSource(benchmarks / "results"), chart_renderer(report), report / "comparison.md")


def chart_renderer(directory: Path) -> ChartRenderer:
    if importlib.util.find_spec("matplotlib") is None:
        return NoChartRenderer()
    from comparison.adapters.matplotlib_chart_renderer import MatplotlibChartRenderer
    return MatplotlibChartRenderer(directory)
