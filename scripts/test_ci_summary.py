import os
import tempfile
import unittest

from ci_summary import parse_results, render


XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{name}" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">
</testsuite>
"""


class CiSummaryTest(unittest.TestCase):
    def write(self, directory, name, tests, failures=0, errors=0, skipped=0):
        with open(os.path.join(directory, f'TEST-{name}.xml'), 'w', encoding='utf-8') as handle:
            handle.write(XML.format(name=name, tests=tests, failures=failures,
                                    errors=errors, skipped=skipped))

    def test_totals_failures_and_errors_across_suites(self):
        with tempfile.TemporaryDirectory() as directory:
            self.write(directory, 'AlphaTest', 10, failures=1)
            self.write(directory, 'BetaTest', 5, errors=2, skipped=1)
            summary = parse_results(directory)
            self.assertEqual((summary.tests, summary.failed, summary.skipped), (15, 3, 1))
            self.assertEqual([entry[0] for entry in summary.suites], ['AlphaTest', 'BetaTest'])
            text = render(summary, directory)
            self.assertIn('15 tes, 3 gagal, 1 dilewati', text)
            self.assertIn('| BetaTest | 5 | 2 | 1 |', text)

    def test_missing_results_directory_reports_instead_of_crashing(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = os.path.join(directory, 'absent')
            summary = parse_results(missing)
            self.assertEqual(summary.tests, 0)
            self.assertIn('Tidak ada hasil tes', render(summary, missing))

    def test_clean_run_omits_failure_hint(self):
        with tempfile.TemporaryDirectory() as directory:
            self.write(directory, 'GreenTest', 7)
            text = render(parse_results(directory), directory)
            self.assertIn('7 tes, 0 gagal, 0 dilewati', text)
            self.assertNotIn('detail kegagalan', text)


if __name__ == '__main__':
    unittest.main()
