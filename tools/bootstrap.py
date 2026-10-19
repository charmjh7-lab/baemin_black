#!/usr/bin/env python3
"""Create a stable development-only keystore AFTER Gradle source exists."""
from pathlib import Path
import argparse, json, re, secrets, shutil, subprocess, sys

def bootstrap(root, apply=False, signing="preserve"):
    cfg_path = root / "project.json"
    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    gradle = root / "app/build.gradle.kts"
    if not gradle.exists():
        gradle = root / "app/build.gradle"
    if not gradle.exists() or not (root / "gradlew").is_file():
        raise RuntimeError("Create Android project and Gradle Wrapper first")
    m = re.search(r'applicationId\s*(?:=\s*|\s+)["\x27]([^"\x27]+)["\x27]', gradle.read_text())
    if not m or m.group(1) != cfg["android"]["application_id"]:
        raise RuntimeError("Gradle applicationId does not match project.json")
    if not apply:
        print("DRY RUN: app ID matches; no files changed")
        return
    if signing == "preserve":
        print("Existing signing preserved")
        return
    base = root / "signing"
    key, props = base / "dev-only.p12", base / "dev-signing.properties"
    if key.exists() != props.exists():
        raise RuntimeError("Partial signing files found, refusing key rotation")
    if not key.exists():
        binary = shutil.which("keytool")
        if not binary:
            raise RuntimeError("JDK keytool required")
        base.mkdir(exist_ok=True)
        pwd = secrets.token_urlsafe(26)
        args = [binary,"-genkeypair","-alias","development_only","-keyalg","RSA","-keysize","3072","-validity","36500",
                "-storetype","PKCS12","-keystore",str(key),"-storepass",pwd,"-keypass",pwd,
                "-dname","CN=Development Only, OU=Prototype, O=Local App","-noprompt"]
        try:
            subprocess.run(args, capture_output=True, timeout=45, check=True)
        except Exception:
            if key.exists():
                key.unlink()
            raise
        props.write_text("storeFile=signing/dev-only.p12\nstorePassword="+pwd+
                         "\nkeyAlias=development_only\nkeyPassword="+pwd+"\n")
    cfg["android"]["development_signing"] = "repo-dev"
    cfg["status"] = "source_detected"
    cfg_path.write_text(json.dumps(cfg, ensure_ascii=False, indent=2)+"\n")
    print("Development signing prepared. Wire Gradle per tools/android-signing-gradle.md")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--root",type=Path,default=Path(__file__).resolve().parent.parent)
    p.add_argument("--project",choices=["android"],default="android")
    p.add_argument("--signing",choices=["preserve","repo-dev"],default="preserve")
    p.add_argument("--apply",action="store_true")
    a = p.parse_args()
    try: bootstrap(a.root.resolve(),a.apply,a.signing)
    except (RuntimeError,OSError,ValueError,subprocess.CalledProcessError) as e:
        print("BOOTSTRAP ERROR: "+str(e),file=sys.stderr)
        sys.exit(1)
