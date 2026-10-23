import unittest

from comparison.report.displayed_value import DisplayedValue
from tests.results import result


class DisplayedValueTest(unittest.TestCase):

    def test_shows_bytes_as_megabytes(self):
        value = DisplayedValue(result("json", "disk_usage", value=822_357, unit="bytes"))

        self.assertEqual(("0.8", "MB"), (value.text(), value.unit()))

    def test_rounds_large_values_and_keeps_two_decimals_for_small_ones(self):
        self.assertEqual("27,992", DisplayedValue(result("folders", value=27_992.357)).text())
        self.assertEqual("0.45", DisplayedValue(result("book", value=0.449)).text())

    def test_shows_the_error_with_the_same_precision_as_the_value(self):
        self.assertEqual("1,649 ± 247", DisplayedValue(result("time", value=1_649.27, error=246.63)).text())
        self.assertEqual("0.75 ± 0.32", DisplayedValue(result("book", value=0.75, error=0.32)).text())
        self.assertEqual("831.5 ± 2.0", DisplayedValue(result("book", "disk_usage", value=831_500_000,
                                                              unit="bytes", error=2_000_000)).text())
