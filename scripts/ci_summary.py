"""Ringkas hasil unit test JUnit ke halaman run GitHub Actions.

Artefak laporan bisa gagal diunggah ketika kuota penyimpanan artifact akun
penuh. Ringkasan ini ditulis ke $GITHUB_STEP_SUMMARY sehingga angka tes tetap
terbaca dari halaman run tanpa memakai penyimpanan artifact sama sekali.
"""

from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field


@dataclass
class Summary:
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    suites: list[tuple[str, int, int, int]] = field(default_factory=list)

    @property
    def failed(self) -> int:
        return self.failures + self.errors


def parse_results(directory: str) -> Summary:
    summary = Summary()
    for path in sorted(glob.glob(os.path.join(directory, 'TEST-*.xml'))):
        root = ElementTree.parse(path).getroot()
        suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
        for suite in suites:
            tests = int(suite.get('tests', 0))
            failures = int(suite.get('failures', 0))
            errors = int(suite.get('errors', 0))
            skipped = int(suite.get('skipped', 0))
            summary.tests += tests
            summary.failures += failures
            summary.errors += errors
            summary.skipped += skipped
            summary.suites.append((suite.get('name', os.path.basename(path)), tests, failures + errors, skipped))
    summary.suites.sort(key=lambda entry: entry[0])
    return summary


def render(summary: Summary, directory: str) -> str:
    if not summary.suites:
        return f'## Kotlin unit tests\n\nTidak ada hasil tes di `{directory}`.\n'
    lines = [
        '## Kotlin unit tests',
        '',
        f'**{summary.tests} tes, {summary.failed} gagal, {summary.skipped} dilewati.**',
        '',
        '| Suite | Tes | Gagal | Dilewati |',
        '| --- | ---: | ---: | ---: |',
    ]
    lines += [f'| {name} | {tests} | {failed} | {skipped} |'
              for name, tests, failed, skipped in summary.suites]
    if summary.failed:
        lines += ['', 'Lihat log langkah Gradle untuk detail kegagalan.']
    return '\n'.join(lines) + '\n'


def main(argv: list[str]) -> int:
    directory = argv[1] if len(argv) > 1 else 'app/build/test-results/testDebugUnitTest'
    summary = parse_results(directory)
    text = render(summary, directory)
    destination = os.environ.get('GITHUB_STEP_SUMMARY')
    if destination:
        with open(destination, 'a', encoding='utf-8') as handle:
            handle.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
