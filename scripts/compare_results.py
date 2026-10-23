#!/usr/bin/env python3
import sys

from comparison.commands.no_results_error import NoResultsError
from comparison.factory import build_comparison_report_command


def main() -> None:
    try:
        print(f"Comparison written to {build_comparison_report_command().execute()}")
    except NoResultsError as error:
        sys.exit(str(error))


if __name__ == "__main__":
    main()
