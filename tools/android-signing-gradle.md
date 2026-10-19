# Android 개발 서명 연결

Android 소스 및 Gradle Wrapper 생성 후 최초 한 번 실행:

`python3 tools/bootstrap.py --apply --project android --signing repo-dev`

키가 있으면 재생성하지 않는다. `project.json`을 바꾼 것만으로 Gradle 설정이 연결되지는 않는다.

`app/build.gradle.kts`에서 `signing/dev-signing.properties`를 읽어 `android.signingConfigs.create("repoDev")`에 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`를 설정하고, `buildTypes.getByName("debug").signingConfig`에 할당한다.

Gradle DSL 구조에 맞춰 통합하고 실제 `:app:assembleDebug` APK의 인증서를 `tools/verify_apk.py`로 검증한다.

**주의:** private repository 전용 개발 키만 저장소에 보존하며, 운영/공개 배포 키·서비스 비밀번호는 절대 넣지 않는다. 기존 설치 APK의 인증서를 바꾸면 업데이트 설치가 실패한다.
