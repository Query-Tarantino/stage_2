import unittest

from comparison.model.ranking import Ranking
from tests.results import result


class RankingTest(unittest.TestCase):

    def test_lower_values_win_by_default(self):
        ranking = Ranking([result("time", value=74.0), result("book", value=0.4)])

        self.assertEqual(["book"], ranking.winning_structures())

    def test_higher_values_win_for_throughput(self):
        ranking = Ranking([result("time", "write_throughput", value=300), result("batch", "write_throughput", value=330)])

        self.assertEqual(["batch"], ranking.winning_structures())

    def test_ties_keep_every_winning_structure_once(self):
        ranking = Ranking([result("time", value=1), result("book", value=1), result("time", value=1)])

        self.assertEqual(["time", "book"], ranking.winning_structures())

    def test_structures_whose_interval_overlaps_the_best_one_are_tied(self):
        ranking = Ranking([result("book", value=0.75, error=0.32), result("batch", value=1.05, error=2.84),
                           result("time", value=3291, error=500)])

        self.assertEqual(["book", "batch"], ranking.winning_structures())

    def test_a_single_candidate_is_not_highlighted_as_winner(self):
        alone = result("time")

        self.assertFalse(Ranking([alone]).is_winner(alone))
