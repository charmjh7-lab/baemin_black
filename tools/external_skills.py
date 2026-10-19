#!/usr/bin/env python3
"""Recommend pinned Google Android skills; only install with explicit --apply.

No remote code is executed. Only skill files are copied from a fixed Google
Android Skills commit. Check licenses and task applicability first.
"""
from __future__ import annotations
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

OFFICIAL_GOOGLE = "https://github.com/android/skills.git"
PIN = "5bda39f154fb859a08d45e2392f3568e049f83fb"
SKILLS = {
    "android-cli": "devtools/android-cli",
    "testing-setup": "testing/testing-setup",
    "adaptive": "jetpack-compose/adaptive",
    "edge-to-edge": "system/edge-to-edge",
    "jetpack-compose-performance": "jetpack-compose/jetpack-compose-performance",
    "android-profiler": "profilers/android-profiler",
    "android-intent-security": "security/android-intent-security",
}
TASKS = {
    "bootstrap": ["android-cli", "testing-setup"],
    "android-ui": ["android-cli", "edge-to-edge"],
    "foldable": ["android-cli", "adaptive"],
    "performance": ["android-profiler", "jetpack-compose-performance"],
    "security": ["android-intent-security"],
    "android-testing": ["testing-setup"],
}

def detect(root: Path):
    compose = False
    for pattern in ("**/*.kt", "**/*.java"):
        for source in root.glob(pattern):
            if any(part in ("build", ".gradle", ".git", "node_modules") for part in source.parts):
                continue
            try:
                content = source.read_text(encoding="utf-8")
                if "androidx.compose" in content or "@Composable" in content:
                    compose = True
                    break
            except (UnicodeError, OSError):
                pass
        if compose:
            break
    return {
        "android": (root / "settings.gradle").is_file() or
                   (root / "settings.gradle.kts").is_file(),
        "compose": compose
    }

def select(task, stack):
    if not stack["android"]:
        return []
    names = TASKS[task][:]
    if not stack["compose"]:
        names = [s for s in names if s not in {
            "adaptive", "edge-to-edge", "jetpack-compose-performance"
        }]
    return names

def install(root: Path, names):
    if not names:
        return []
    if not shutil.which("git"):
        raise RuntimeError("git is needed to fetch pinned official skills")
    with tempfile.TemporaryDirectory(prefix="google-android-skills-") as tmp:
        work = Path(tmp)
        def check(args):
            subprocess.run(args, check=True, capture_output=True, text=True, timeout=120)
        check(["git", "init", "-q", str(work)])
        check(["git", "-C", str(work), "remote", "add", "origin", OFFICIAL_GOOGLE])
        check(["git", "-C", str(work), "fetch", "--depth", "1", "origin", PIN])
        check(["git", "-C", str(work), "checkout", "-q", "--detach", "FETCH_HEAD"])
        installed = []
        for name in names:
            source = work / SKILLS[name]
            dest = root / ".agents" / "skills" / ("vendor-google-" + name)
            if not (source / "SKILL.md").is_file():
                raise RuntimeError("Upstream skill changed: " + SKILLS[name])
            if dest.exists():
                print("PRESERVED existing", dest.relative_to(root))
                continue
            shutil.copytree(source, dest)
            license_file = work / "LICENSE.txt"
            if license_file.is_file():
                (dest / "UPSTREAM_LICENSE.txt").write_bytes(license_file.read_bytes())
            installed.append(dest.relative_to(root).as_posix())
        if installed:
            receipt = root / ".agents" / "VENDOR_SKILLS.md"
            with receipt.open("a", encoding="utf-8") as f:
                f.write("\nGoogle Android Skills from pinned commit " + PIN + "\n")
                for item in installed:
                    f.write("- " + item + " source: " + OFFICIAL_GOOGLE + "\n")
        return installed

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    ap.add_argument("--task", choices=sorted(TASKS), default="bootstrap")
    ap.add_argument("--recommend", action="store_true")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    root = args.root.resolve()
    stack = detect(root)
    names = select(args.task, stack)
    print(json.dumps({
        "stack": stack, "task": args.task, "official_source": OFFICIAL_GOOGLE,
        "pinned_commit": PIN, "recommended": names,
        "community_sources": "See docs/SKILLS_POLICY.md; never silently installed"
    }, ensure_ascii=False, indent=2))
    if not args.apply:
        print("No files modified. Pass --apply only after inspecting applicability.")
        return 0
    try:
        print("Installed:", ", ".join(install(root, names)) or "(nothing applicable)")
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        print("EXTERNAL SKILLS ERROR:", exc, file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
