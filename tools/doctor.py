#!/usr/bin/env python3
"""Read-only development environment overview."""
import json
import shutil
from pathlib import Path
root=Path(__file__).resolve().parent.parent
exists=lambda path:(root/path).is_file()
state={
 "platform":"android",
 "gradle_wrapper":exists("gradlew"),
 "android_settings":exists("settings.gradle.kts") or exists("settings.gradle"),
 "app_module":exists("app/build.gradle.kts") or exists("app/build.gradle"),
 "development_signing":exists("signing/dev-only.p12") and exists("signing/dev-signing.properties"),
 "available_tools":{name:bool(shutil.which(name)) for name in ("git","gh","java","keytool","gradle","python3")}
}
print(json.dumps(state,ensure_ascii=False,indent=2))
