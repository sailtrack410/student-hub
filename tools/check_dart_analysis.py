#!/usr/bin/env python3
"""Reject analyzer failures and diagnostics that are absent from the reviewed baseline."""
import argparse
from collections import Counter
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument('log', type=Path)
parser.add_argument('--exit-code', type=int, default=2)
args = parser.parse_args()
if args.exit_code not in (0, 1, 2):
    sys.exit(f'Analyzer failed: exit {args.exit_code}')
baseline = json.loads(Path(__file__).with_name('dart-analysis-baseline.json').read_text())
def key(row):
    return (row['severity'], row['code'], row['file'], row['message'])
allowed = Counter(map(key, baseline))
actual = Counter()
for line in args.log.read_text().splitlines():
    parts = line.split('|', 7)
    if len(parts) == 8 and parts[0] in ('ERROR', 'WARNING', 'INFO'):
        severity, _, code, path, _, _, _, message = parts
        normalized = path.replace('\\', '/').split('/app/', 1)[-1]
        actual[(severity, code, normalized, message)] += 1
if args.exit_code and not actual:
    sys.exit('Analyzer exited without usable diagnostics')
new = actual - allowed
if new:
    for diagnostic, count in new.items():
        print(f'{count} new: {diagnostic[0]} {diagnostic[1]} {diagnostic[2]}: {diagnostic[3]}')
    sys.exit(1)
print('Analysis baseline passed')
