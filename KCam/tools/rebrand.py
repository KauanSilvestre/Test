#!/usr/bin/env python3
"""Re-apply KCam branding to string resources.

Idempotent: safe to run again after pulling a new upstream FadCam version.
  python3 tools/rebrand.py            # uses kcam.appName from gradle.properties

What it does:
  * removes fork-owned strings (app_name, fadsec_info, app_description) from the
    upstream files; app_name comes from Gradle resValue, the others from
    res/values/kcam_strings.xml
  * replaces the standalone word "FadCam" in user-visible strings with the fork name.
    Paths such as "Downloads/FadCam" are left alone because the on-disk folder name
    is defined in code, and feature names (FadRec, FadMic, Faditor...) are untouched.
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
props = (root / "gradle.properties").read_text(encoding="utf-8")
m = re.search(r"^kcam\.appName=(.+)$", props, re.M)
name = m.group(1).strip() if m else "KCam"

# Strings the fork owns: removed from upstream files (every locale) and redefined in
# res/values/kcam_strings.xml, so they can never collide with upstream copies.
FORK_OWNED = ["app_name", "fadsec_info", "app_description"]
owned = re.compile(r'^[ \t]*<string name="(?:%s)"[^>]*>.*?</string>[ \t]*\n' % "|".join(FORK_OWNED), re.M | re.S)
word = re.compile(r'(?<![/\w])FadCam(?![/\w])')

changed = 0
for f in sorted((root / "app/src/main/res").glob("values*/strings.xml")):
    if f.name == "kcam_strings.xml":
        continue
    s = f.read_text(encoding="utf-8")
    t = owned.sub("", s)
    # only touch text content, never attribute values / resource names
    t = re.sub(r'>([^<]*)<', lambda mm: ">" + word.sub(name, mm.group(1)) + "<", t)
    if t != s:
        f.write_text(t, encoding="utf-8")
        changed += 1
print(f"rebrand: {changed} file(s) updated with name '{name}'")
sys.exit(0)
