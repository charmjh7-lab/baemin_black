import importlib.util
import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def load(name, filename):
    spec=importlib.util.spec_from_file_location(name,ROOT/"tools"/filename)
    obj=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj
PRE=load("preflight","preflight.py")
APK=load("verify_apk","verify_apk.py")

class TestStarter(unittest.TestCase):
    def test_preflight_allows_no_android_source(self):
        errors,_,notes=PRE.inspect(ROOT)
        self.assertEqual([],errors)
        self.assertTrue(notes)
    def test_release_requires_real_app_and_signing(self):
        errors,_,_=PRE.inspect(ROOT,"release")
        source_ready = (ROOT/"gradlew").is_file() and (
            (ROOT/"settings.gradle.kts").is_file() or (ROOT/"settings.gradle").is_file()
        ) and ((ROOT/"app/build.gradle.kts").is_file() or (ROOT/"app/build.gradle").is_file())
        signing_ready = (ROOT/"signing/dev-only.p12").is_file() and (
            ROOT/"signing/dev-signing.properties").is_file()
        cfg=json.loads((ROOT/"project.json").read_text())
        if source_ready and signing_ready and cfg["android"]["development_signing"]=="repo-dev":
            self.assertEqual([],errors)
        else:
            self.assertTrue(any("Release blocked" in x for x in errors))
    def test_identity(self):
        cfg=json.loads((ROOT/"project.json").read_text())
        self.assertEqual("android",cfg["platform"])
        self.assertEqual("com.charmjh7.baeminblack",cfg["android"]["application_id"])
    def test_apk_metadata(self):
        self.assertEqual(("com.charmjh7.baeminblack",7),APK.parse_aapt_badging("package: name='com.charmjh7.baeminblack' versionCode='7' versionName='1'"))
        self.assertEqual("abcd",APK.extract_apk_digest("Signer #1 certificate SHA-256 digest: ab:cd"))
    def test_update_compatibility(self):
        old={"application_id":"a.b","version_code":5,"certificate_sha256":"abc"}
        new={**old,"version_code":6}
        APK.compare_new_to_previous(new,old)
        with self.assertRaises(RuntimeError): APK.compare_new_to_previous(old,new)
        with self.assertRaises(RuntimeError): APK.compare_new_to_previous({**new,"certificate_sha256":"x"},old)

if __name__=="__main__":
    unittest.main()
