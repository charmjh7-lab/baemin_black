#!/usr/bin/env python3
"""No-dependency preflight for Baemin Black. Cheap on every push, safe for an empty app."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REQUIRED = [
    "README.md", "START_HERE.md", "AGENTS.md", "project.json",
    "docs/PRODUCT_BRIEF.md", "docs/DECISIONS.md", "docs/DESIGN_SYSTEM.md",
    "docs/ENGINEERING_CONTRACT.md", "docs/IMPLEMENTATION_STATUS.md",
    "docs/UX_ACCEPTANCE.md", "docs/TEST_STRATEGY.md", "docs/CI_RELEASE.md",
    "docs/SKILLS_POLICY.md", "docs/PAST_FAILURE_ATLAS.md",
    ".github/workflows/ci.yml", ".github/workflows/android-debug.yml",
    ".github/workflows/release-apk.yml",
]

def inspect(root: Path, mode="normal"):
    errors, warnings, notes = [], [], []
    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append("Missing package contract: " + name)

    try:
        cfg = json.loads((root / "project.json").read_text(encoding="utf-8"))
    except (ValueError, UnicodeError, OSError) as exc:
        cfg = {}
        errors.append("Invalid project.json: " + str(exc))

    android = cfg.get("android", {})
    if cfg:
        if cfg.get("schema_version") != 1 or cfg.get("platform") != "android":
            errors.append("project.json must use schema_version=1, platform=android")
        if android.get("module") != "app":
            errors.append("Android module must be 'app'")
        if android.get("application_id") != "com.charmjh7.baeminblack":
            errors.append("Android application ID differs from the agreed ID")
        if cfg.get("automation", {}).get("android_apk") != "manual_release_only":
            warnings.append("Release policy differs from manual_release_only")

    gradle_settings = (root / "settings.gradle.kts").is_file() or (root / "settings.gradle").is_file()
    wrapper = (root / "gradlew").is_file()
    module = (root / "app/build.gradle.kts").is_file() or (root / "app/build.gradle").is_file()
    if gradle_settings and not wrapper:
        warnings.append("Android settings present but Gradle Wrapper missing")

    skills = sorted((root / ".agents/skills").glob("*/SKILL.md"))
    if not skills:
        errors.append("No local skills installed")
    for skill in skills:
        try:
            raw = skill.read_text(encoding="utf-8")
            if (not raw.startswith("---\n") or
                "name:" not in raw[:600] or "description:" not in raw[:800]):
                errors.append("Invalid skill frontmatter: " + str(skill.relative_to(root)))
        except (UnicodeError, OSError):
            errors.append("Cannot read skill: " + str(skill.relative_to(root)))

    ci = root / ".github/workflows/ci.yml"
    if ci.is_file():
        raw = ci.read_text(encoding="utf-8")
        if "preflight.py" not in raw or "unittest" not in raw:
            errors.append("Fast CI must run preflight and tooling tests")
    release = root / ".github/workflows/release-apk.yml"
    if release.is_file():
        raw = release.read_text(encoding="utf-8")
        if "workflow_dispatch:" not in raw or re.search(r"(?m)^  push:", raw):
            errors.append("APK release must be manual only")
        if "gh release create" not in raw:
            errors.append("Release must publish a direct installable APK")
    debug = root / ".github/workflows/android-debug.yml"
    if debug.is_file():
        raw = debug.read_text(encoding="utf-8")
        if re.search(r"(?m)^  push:", raw):
            errors.append("Debug APK must not rebuild on every push")

    if mode == "release":
        if not gradle_settings:
            errors.append("Release blocked: Android settings absent")
        if not wrapper:
            errors.append("Release blocked: Gradle Wrapper absent")
        if not module:
            errors.append("Release blocked: app Gradle module absent")
        if android.get("development_signing") != "repo-dev":
            errors.append("Release blocked: stable development signing not configured")
        if not (root / "signing/dev-only.p12").is_file() or not (
            root / "signing/dev-signing.properties"
        ).is_file():
            errors.append("Release blocked: fixed repo-dev key/properties missing")
    elif not (gradle_settings and module and wrapper):
        notes.append("Starter-only repository; Android source/build pending")
    else:
        notes.append("Android source present; compilation and journeys are separate evidence")

    return errors, warnings, notes

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    p.add_argument("--mode", choices=("normal", "release"), default="normal")
    args = p.parse_args()
    errors, warnings, notes = inspect(args.root.resolve(), args.mode)
    for label, values in (("NOTE", notes), ("WARN", warnings), ("ERROR", errors)):
        for line in values:
            print(label + ": " + line)
    print("[preflight] " + ("FAILED" if errors else "PASSED"))
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main())
