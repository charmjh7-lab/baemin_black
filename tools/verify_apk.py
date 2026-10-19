#!/usr/bin/env python3
"""Validate APK app ID, versionCode and stable signing before user download."""
import argparse, hashlib, json, os, re, shutil, ssl, subprocess, sys
from pathlib import Path

def sdk_tool(name):
    direct = shutil.which(name)
    if direct: return direct
    for env in ("ANDROID_HOME","ANDROID_SDK_ROOT"):
        home=os.environ.get(env)
        if home:
            hits=sorted((Path(home)/"build-tools").glob("*/"+name),reverse=True)
            if hits: return str(hits[0])
    raise RuntimeError(name+" not found")

def run(args):
    p=subprocess.run(args,text=True,capture_output=True,timeout=60)
    if p.returncode: raise RuntimeError(Path(args[0]).name+" failed")
    return p.stdout

def parse_aapt_badging(raw):
    m=re.search(r"^package:\s+name='([^']+)'\s+versionCode='(\d+)'",raw,re.M)
    if not m:raise RuntimeError("APK metadata missing")
    return m.group(1),int(m.group(2))

def extract_apk_digest(raw):
    # Build Tools 37 uses 'V3.0 Signer:' while older tools use 'Signer #1'.
    matches=re.findall(r"(?:Signer #\d+|V\d+(?:\.\d+)? Signer:) certificate SHA-256 digest:\s*([A-Fa-f0-9:]+)",raw)
    digests={re.sub("[^0-9a-f]","",value.lower()) for value in matches}
    if not digests:raise RuntimeError("Signer digest missing")
    if len(digests)!=1:raise RuntimeError("Unexpected multiple signing certificates")
    return digests.pop()

def expected_digest(root):
    props={}
    for line in (root/"signing/dev-signing.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key,value=line.split("=",1);props[key.strip()]=value.strip()
    pem=run([shutil.which("keytool") or "keytool","-exportcert","-rfc",
             "-storetype","PKCS12","-keystore",str(root/props["storeFile"]),
             "-alias",props["keyAlias"],"-storepass",props["storePassword"]])
    m=re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",pem,re.S)
    if not m:raise RuntimeError("Cannot export expected cert")
    return hashlib.sha256(ssl.PEM_cert_to_DER_cert(m.group(0))).hexdigest()

def inspect(apk,root):
    cfg=json.loads((root/"project.json").read_text())
    run([sdk_tool("apksigner"),"verify","--verbose",str(apk)])
    digest=extract_apk_digest(run([sdk_tool("apksigner"),"verify","--print-certs",str(apk)]))
    package,code=parse_aapt_badging(run([sdk_tool("aapt"),"dump","badging",str(apk)]))
    if package != cfg["android"]["application_id"]:raise RuntimeError("Wrong applicationId")
    if cfg["android"]["development_signing"]!="repo-dev":raise RuntimeError("Development signing unconfigured")
    if digest!=expected_digest(root):raise RuntimeError("APK signer differs from development key")
    return {"application_id":package,"version_code":code,"certificate_sha256":digest,"commit":os.environ.get("GITHUB_SHA","")}

def compare_new_to_previous(current,previous):
    if current["application_id"] != previous["application_id"]:return
    if current["certificate_sha256"] != previous["certificate_sha256"]:raise RuntimeError("Signer changed")
    if current["version_code"] <= previous["version_code"]:raise RuntimeError("versionCode must increase")

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--apk",type=Path,required=True)
    p.add_argument("--root",type=Path,default=Path(__file__).resolve().parent.parent)
    p.add_argument("--previous",type=Path)
    p.add_argument("--output",type=Path)
    a=p.parse_args()
    try:
        data=inspect(a.apk.resolve(),a.root.resolve())
        if a.previous:compare_new_to_previous(data,json.loads(a.previous.read_text()))
        if a.output:
            a.output.parent.mkdir(parents=True,exist_ok=True)
            a.output.write_text(json.dumps(data,indent=2)+"\n")
        print(json.dumps(data,indent=2))
    except (OSError,RuntimeError,ValueError,KeyError,subprocess.TimeoutExpired) as exc:
        print("APK VERIFY ERROR: "+str(exc),file=sys.stderr);sys.exit(1)

