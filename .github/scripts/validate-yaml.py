#!/usr/bin/env python3
"""Validates all YAML files under .github/ and checks that every `uses:` is SHA-pinned."""
import pathlib
import re
import sys

import yaml

root = pathlib.Path(__file__).resolve().parent.parent
failed = False
for path in sorted([*root.rglob("*.yml"), *root.rglob("*.yaml")]):
    text = path.read_text()
    try:
        doc = yaml.safe_load(text)
        assert isinstance(doc, dict), "top level must be a mapping"
        print(f"OK   {path.relative_to(root.parent)}")
    except Exception as exc:  # noqa: BLE001
        failed = True
        print(f"FAIL {path.relative_to(root.parent)}: {exc}")
    for line in text.splitlines():
        m = re.search(r"uses:\s*(\S+)", line)
        if m and not re.search(r"@[0-9a-f]{40}$", m.group(1)):
            failed = True
            print(f"FAIL {path.name}: not SHA-pinned: {m.group(1)}")
sys.exit(1 if failed else 0)
