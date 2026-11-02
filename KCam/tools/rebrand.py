#!/usr/bin/env python3
"""Re-apply the fork branding to string resources.

Idempotent: safe to run again after pulling a new upstream FadCam version.
  python3 tools/rebrand.py            # uses kcam.appName from gradle.properties

What it does:
  * removes fork-owned strings (app_name, fadsec_info, app_description) from the
    upstream files; app_name comes from Gradle resValue, the others from
    res/values/kcam_strings.xml
  * in the UPSTREAM string files, replaces the standalone product name
    ("FadCam" or the previous fork name "KCam") in user-visible text with the
    current fork name. Paths such as "Downloads/FadCam" are left alone (the
    on-disk folder name is defined in code), and feature names (FadRec, FadMic,
    Faditor...) are untouched.
  * in the fork-owned res/values/kcam_strings.xml, replaces only the previous
    fork name "KCam" with the current one. "FadCam" is kept there on purpose:
    that file carries the GPL attribution/credit to the upstream project, which
    must be preserved.
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
props = (root / "gradle.properties").read_text(encoding="utf-8")
m = re.search(r"^kcam\.appName=(.+)$", props, re.M)
name = m.group(1).strip() if m else "KCam"

# Strings the fork owns: removed from upstream files (every locale) and redefined
# in res/values/kcam_strings.xml, so they can never collide with upstream copies.
FORK_OWNED = ["app_name", "fadsec_info", "app_description"]
owned = re.compile(r'^[ \t]*<string name="(?:%s)"[^>]*>.*?</string>[ \t]*\n' % "|".join(FORK_OWNED), re.M | re.S)

# Standalone product name. Negative look-around on "/" and word chars keeps
# folder paths ("Downloads/FadCam") and compound names ("FadCamXYZ") intact.
both_names = re.compile(r'(?<![/\w])(?:FadCam|KCam)(?![/\w])')
prev_fork = re.compile(r'(?<![/\w])KCam(?![/\w])')


def replace_in_text(pattern, xml):
    # Only touch text content, never attribute values or resource names.
    return re.sub(r'>([^<]*)<', lambda mm: ">" + pattern.sub(name, mm.group(1)) + "<", xml)


changed = 0

# Upstream locale files: strip fork-owned strings and rebrand the product name.
for f in sorted((root / "app/src/main/res").glob("values*/strings.xml")):
    if f.name == "kcam_strings.xml":
        continue
    s = f.read_text(encoding="utf-8")
    t = replace_in_text(both_names, owned.sub("", s))
    if t != s:
        f.write_text(t, encoding="utf-8")
        changed += 1

# Fork-owned strings: only retire the previous fork name; keep FadCam attribution.
kcam_file = root / "app/src/main/res/values/kcam_strings.xml"
if kcam_file.exists():
    s = kcam_file.read_text(encoding="utf-8")
    t = replace_in_text(prev_fork, s)
    if t != s:
        kcam_file.write_text(t, encoding="utf-8")
        changed += 1

print(f"rebrand: {changed} file(s) updated with name '{name}'")
sys.exit(0)
