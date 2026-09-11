from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = """    if count != 1:\n        raise SystemExit(f'expected one match, got {count}: {old[:100]!r}')\n    text = text.replace(old, new, 1)\n"""
new = """    if old.startswith('        LazyColumn(') and count == 2:\n        text = text.replace(old, new, 1)\n        return\n    if count != 1:\n        raise SystemExit(f'expected one match, got {count}: {old[:100]!r}')\n    text = text.replace(old, new, 1)\n"""
if text.count(old) != 1:
    raise SystemExit('patch guard preflight marker mismatch')
path.write_text(text.replace(old, new, 1))
