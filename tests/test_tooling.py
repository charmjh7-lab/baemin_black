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


class TestRestoredStarterBehaviors(unittest.TestCase):
    def test_design_and_ux_original_contracts_retained(self):
        design = (ROOT / "docs/DESIGN_SYSTEM.md").read_text(encoding="utf-8")
        ux = (ROOT / "docs/UX_ACCEPTANCE.md").read_text(encoding="utf-8")
        self.assertIn("디자인 결정의 출발점", design)
        self.assertIn("화면 문구는 디자인과 함께 결정", design)
        self.assertIn("기본 UI 라이브러리", design)
        self.assertIn("조작이 보이는지와 실제 닿는지 구별", ux)
        self.assertIn("추가 스크롤 없이", ux)
        self.assertIn("배민 블랙", design)

    def test_failure_atlas_and_bootstrap_skill_present(self):
        atlas = (ROOT / "docs/PAST_FAILURE_ATLAS.md").read_text(encoding="utf-8")
        self.assertIn("26.", atlas)
        skill = (ROOT / ".agents/skills/project-bootstrap/SKILL.md").read_text(encoding="utf-8")
        self.assertIn("Project bootstrap", skill)

    def test_release_and_android_build_are_opt_in(self):
        release = (ROOT / ".github/workflows/release-apk.yml").read_text()
        debug = (ROOT / ".github/workflows/android-debug.yml").read_text()
        self.assertIn("workflow_dispatch:", release)
        self.assertNotIn("\n  push:", release)
        self.assertIn("workflow_dispatch:", debug)
        self.assertIn("workflow_dispatch:", debug)

    def test_preflight_rejects_invalid_skill_metadata(self):
        import tempfile
        from shutil import copytree
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "project.json").write_text((ROOT / "project.json").read_text())
            target = directory / ".agents/skills/invalid/SKILL.md"
            target.parent.mkdir(parents=True)
            target.write_text("# Missing frontmatter", encoding="utf-8")
            errors, _, _ = PRE.inspect(directory)
            self.assertTrue(any("Invalid skill frontmatter" in err for err in errors))

    def test_official_skill_selection_does_not_install_by_default(self):
        sk = load("external_skills", "external_skills.py")
        self.assertEqual([], sk.select("bootstrap", {"android": False, "compose": False}))
        self.assertEqual(
            ["android-cli", "testing-setup"],
            sk.select("bootstrap", {"android": True, "compose": False})
        )
        self.assertNotIn("adaptive", sk.select("foldable", {"android": True, "compose": False}))

if __name__=="__main__":
    unittest.main()

