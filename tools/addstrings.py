#!/usr/bin/env python3
"""Add UI strings to both locales.

usage: tools/addstrings.py strings.tsv
Each line: key<TAB>English<TAB>Tamil. Existing keys are updated in place, new keys appended.
"""
import re, sys, html
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
FILES = {"en": RES / "values/strings_ui.xml", "ta": RES / "values-ta/strings_ui.xml"}

def esc(s: str) -> str:
    s = html.escape(s, quote=False)
    return s.replace("'", "\\'").replace('"', '\\"')

def upsert(path: Path, key: str, value: str):
    text = path.read_text(encoding="utf-8")
    line = f'    <string name="{key}">{esc(value)}</string>'
    pat = re.compile(rf'^\s*<string name="{re.escape(key)}"[^>]*>.*?</string>$', re.M)
    text = pat.sub(line, text, count=1) if pat.search(text) else text.replace("</resources>", line + "\n</resources>")
    path.write_text(text, encoding="utf-8")

for raw in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if not raw.strip() or raw.startswith("#"): continue
    key, en, ta = raw.split("\t")
    upsert(FILES["en"], key, en); upsert(FILES["ta"], key, ta)
print("ok")
