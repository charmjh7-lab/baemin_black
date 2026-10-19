#!/usr/bin/env python3
"""Check prepared Android project contracts; starter-only checks must pass."""
from pathlib import Path
import argparse
import json
import sys

REQUIRED = [
    "README.md", "AGENTS.md", "START_HERE.md", "project.json",
    "docs/PRODUCT_BRIEF.md", "docs/DECISIONS.md",
    "docs/IMPLEMENTATION_STATUS.md", "docs/DESIGN_SYSTEM.md",
    "docs/UX_ACCEPTANCE.md", "docs/ENGINEERING_CONTRACT.md",
    "docs/TEST_STRATEGY.md", "docs/CI_RELEASE.md", "docs/SKILLS_POLICY.md",
    ".github/workflows/ci.yml", ".github/workflows/android-debug.yml",
    ".github/workflows/release-apk.yml"
]

def inspect(root: Path, mode="normal"):
    errors, warnings, notes = [], [], []
    for path in REQUIRED:
        if not (root / path).is_file():
            errors.append("Missing: " + path)
    try:
        config = json.loads((root / "project.json").read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        errors.append("Invalid project.json: " + str(exc))
        config = {}
    android = config.get("android", {})
    if config and (config.get("platform") != "android" or config.get("schema_version") != 1):
        errors.append("Platform/schema must be android/1")
    if config and (android.get("module") != "app" or android.get("application_id") != "com.charmjh7.baeminblack"):
        errors.append("Android module/application ID mismatch")
    if not list((root / ".agents/skills").glob("*/SKILL.md")):
        errors.append("Missing selected local skills")
    has_source = (root / "gradlew").is_file() and (
        (root / "settings.gradle.kts").is_file() or (root / "settings.gradle").is_file()
    ) and (
        (root / "app/build.gradle.kts").is_file() or (root / "app/build.gradle").is_file()
    )
    if mode == "release":
        if not has_source:
            errors.append("Release blocked: Android source and Gradle Wrapper missing")
        if android.get("development_signing") != "repo-dev":
            errors.append("Release blocked: stable development signing not configured")
        if not (root / "signing/dev-only.p12").is_file() or not (root / "signing/dev-signing.properties").is_file():
            errors.append("Release blocked: development key missing")
    elif not has_source:
        notes.append("Starter-only repository; source/build pending")
    else:
        notes.append("Android source found; run compile/tests separately")
    return errors, warnings, notes

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--mode", choices=["normal", "release"], default="normal")
    args = parser.parse_args()
    errors, warnings, notes = inspect(args.root.resolve(), args.mode)
    for prefix, values in (("NOTE", notes), ("WARN", warnings), ("ERROR", errors)):
        for value in values:
            print(prefix + ": " + value)
    print("[preflight] " + ("FAILED" if errors else "PASSED"))
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main())
