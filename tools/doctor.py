#!/usr/bin/env python3
"""Read-only local/GitHub environment inventory. Does not modify or print credentials."""
from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

def run(args, timeout=12, cwd=None):
    try:
        p = subprocess.run(args, capture_output=True, text=True, timeout=timeout,
                           check=False, cwd=cwd)
        return {"ok": p.returncode == 0, "stdout": p.stdout.strip()[:4000],
                "stderr": p.stderr.strip()[:300] if p.returncode else ""}
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"ok": False, "error": type(exc).__name__}

def main():
    root = Path(__file__).resolve().parent.parent
    tools = {name: bool(shutil.which(name)) for name in (
        "git", "gh", "python3", "java", "keytool", "gradle", "node", "npm"
    )}
    report = {
        "root": str(root),
        "tools": tools,
        "android_settings": (root / "settings.gradle.kts").is_file() or
                            (root / "settings.gradle").is_file(),
        "gradle_wrapper": (root / "gradlew").is_file(),
        "app_module": (root / "app/build.gradle.kts").is_file() or
                      (root / "app/build.gradle").is_file(),
        "development_signing": (root / "signing/dev-only.p12").is_file() and
                               (root / "signing/dev-signing.properties").is_file()
    }
    if tools["git"]:
        remote = run(["git", "-C", str(root), "remote", "get-url", "origin"])
        report["git_origin_configured"] = remote["ok"]
        head = run(["git", "-C", str(root), "rev-parse", "--short", "HEAD"])
        report["git_head"] = head["stdout"] if head["ok"] else None
    if tools["gh"]:
        auth = run(["gh", "auth", "status"], cwd=root)
        report["github_authenticated"] = auth["ok"]
        if auth["ok"] and (root / ".git").exists():
            repo = run(["gh", "repo", "view", "--json",
                        "nameWithOwner,isPrivate,defaultBranchRef"], timeout=15, cwd=root)
            if repo["ok"]:
                try:
                    meta = json.loads(repo["stdout"])
                    name = meta.get("nameWithOwner")
                    report["github_repository"] = {
                        "name": name, "private": meta.get("isPrivate"),
                        "default_branch": (meta.get("defaultBranchRef") or {}).get("name")
                    }
                    if name:
                        permissions = run(["gh", "api", "repos/" + name + "/actions/permissions"],
                                          timeout=15, cwd=root)
                        report["actions_permissions"] = (
                            {k: json.loads(permissions["stdout"]).get(k)
                             for k in ("enabled", "allowed_actions")}
                            if permissions["ok"] else "unavailable to this token"
                        )
                        workflow = run(["gh", "api", "repos/" + name +
                                        "/actions/permissions/workflow"], timeout=15, cwd=root)
                        if workflow["ok"]:
                            data = json.loads(workflow["stdout"])
                            report["workflow_permissions"] = data.get("default_workflow_permissions")
                except ValueError:
                    report["github_repository"] = "invalid API response"
            else:
                report["github_repository"] = "inaccessible or not yet pushed"
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
