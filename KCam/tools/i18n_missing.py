#!/usr/bin/env python3
"""List English strings that have no Portuguese (values-pt) translation yet.

Run after pulling upstream FadCam changes or adding KCam strings:
  python3 tools/i18n_missing.py            # names + English text
Translate them into app/src/main/res/values-pt/<same file name>.xml.
"""
import pathlib
import re

res = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
total = 0
for src in sorted((res / "values").glob("*.xml")):
    s = src.read_text(encoding="utf-8")
    names = {m.group(1): m.group(3) for m in re.finditer(
        r'<(?:string|string-array|plurals)\s+name="([^"]+)"([^>]*)>(.*?)</(?:string|string-array|plurals)>', s, re.S)
        if 'translatable="false"' not in m.group(2)
        and re.search(r"[A-Za-z]{2}", re.sub(r"<[^>]+>|%\d*\$?[sdf]", "", m.group(3)))}
    if src.name in ("fonts.xml",) or not names:
        continue
    have = set()
    for pt in (res / "values-pt").glob("*.xml"):
        have |= set(re.findall(r'<(?:string|string-array|plurals)\s+name="([^"]+)"', pt.read_text(encoding="utf-8")))
    missing = [n for n in names if n not in have]
    for n in missing:
        print(f"{src.name}\t{n}\t{names[n].strip()[:100]}")
    total += len(missing)
print(f"# {total} string(s) missing in values-pt")
