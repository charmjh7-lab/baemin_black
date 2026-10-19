# 최소 비용 CI·개발 서명·사용자 APK 전달

이 문서는 완성된 Android 앱과 초기 스타터를 구분한다. 앱 코드가 없으면 Android 빌드의 성공을 주장하지 않는다. 기존 앱에 적용할 때는 기존 applicationId·versionCode·서명·사용자 데이터·키 보존을 우선한다.

## 1. CI 작업을 분리한다

| 목적 | 실행 시점 | 실제 수행 |
|---|---|---|
| 가벼운 계약 검사 | 일반 push·PR | `preflight.py`, 툴링 테스트 |
| 필요 시 Android 단위 테스트 | PR 또는 명시적 수동 요청 | 관련 테스트, Gradle |
| APK 제작 | 사용자가 요청할 때만 | 수동 Release workflow |
| 실기기 검증 | 가능한 테스트 환경에서 | 설치, 업데이트, 주요 화면 조작 |

push마다 APK를 빌드하거나 전체 E2E를 돌리지 않는다. Android SDK 설치·Gradle 실행은 앱 소스와 구체적인 검증 요청이 있을 때 사용한다. 실패한 동일 단계의 로그를 읽고 원인 수정 후 다시 실행한다.

## 2. Android 개발 서명 보존

- 개인 개발용 신규 앱에 한해서 `repo-dev` 고정 개발 서명을 적용할 수 있다. 공개·운영 배포용 키는 별도로 취급한다.
- `tools/bootstrap.py --apply --project android --signing repo-dev`는 **처음 준비할 때 한 번** 적용하고, 기존 키가 있으면 절대 교체하지 않는다.
- `signing/dev-only.p12`와 `signing/dev-signing.properties`는 private repo 내 개인 개발 목적의 명시적 보존 정책에서만 추적할 수 있다. 개인 키·운영 키나 외부 서비스 인증 비밀은 저장소에 올리지 않는다.
- Android Gradle `signingConfigs`에 실제 keystore·alias·비밀번호를 연결하고 빌드한 APK의 인증서가 기대값과 동일한지 검증한다.
- 앱 ID나 서명 변경으로 기존 설치본을 업데이트할 수 없게 만들지 않는다. 새 versionCode는 설치된 이전 버전보다 커야 한다.

## 3. APK 전달

1. 기존 CI/릴리스 실패가 있으면 그 로그·커밋에서 재개한다.
2. `python3 tools/preflight.py --mode release`로 필수 파일·서명 준비를 확인한다.
3. Gradle 한 번 빌드한 APK를 Android SDK의 `apksigner`와 `aapt`로 검사한다.
4. `version-info.json`의 앱 ID·서명 지문·versionCode를 가능한 경우 이전 릴리스와 비교한다.
5. GitHub Actions의 **수동** `release-apk.yml`로 GitHub Prerelease에 개별 APK 자산·체크섬·메타데이터를 게시한다.
6. 사용자가 APK를 요청하면 Actions ZIP이 아니라 **직접 설치할 수 있는 .apk URL**을 제공한다.

설치·로그인·사용자가 실제로 수행한 동작 검증은 서명 검사/빌드와 별개로 보고한다. 테스트할 수 없었다면 미검증으로 남긴다.

## 4. 실패와 GitHub 권한

- Actions가 비활성화되었거나 permissions가 제한되었다면 `tools/doctor.py`와 GitHub 설정을 확인한다.
- 조직 정책 때문에 권한을 얻을 수 없는 경우 사용자에게 필요한 설정 조치만 전달한다. 권한을 무단 우회하지 않는다.
- GitHub Actions 비용을 아끼기 위해 무한 rerun·불필요한 Emulator 실행을 하지 않는다.
- 작업 중 불필요한 서명 교체·workflow 재설계·정상 앱 구조 전면 재작성은 피한다.

## 5. 시작 시 점검

| 확인 | 명령 | 의미 |
|---|---|---|
| 빠른 계약 | `python3 tools/preflight.py` | 구조·문서·시나리오 준비 |
| 환경 진단 | `python3 tools/doctor.py` | 로컬 및 GitHub 도구·권한 |
| 초기 개발 설정 | `python3 tools/bootstrap.py --apply --project android --signing repo-dev` | 프로젝트 소스 이후 필요할 때만 |
| Android 빌드 | `./gradlew :app:assembleDebug` | 실제 앱 소스가 있어야 가능 |
| APK 서명 검증 | `python3 tools/verify_apk.py --apk <path>` | APK 존재 및 서명 일치 |


---

## 배민 블랙 실제 워크플로 연결


- `ci.yml`: push/PR/수동에서 경량 preflight + Python 단위 테스트.
- `android-debug.yml`: main의 앱 소스 push 및 수동 실행에서 `:app:assembleDebug` 실행, APK를 Actions Artifact로 업로드. 파일이 없으면 준비 단계로 기록하고 성공 종료.
- `release-apk.yml`: 수동 실행에서만 빌드·검증 후 GitHub **Prerelease에 직접 APK 파일** 업로드. 원본 프로젝트와 서명 설정이 완성되기 전에는 명확히 중단.
- Java 17 / Android SDK / Gradle 캐시. 기존 Gradle Wrapper 사용.
- 고정 개발 키: 새 앱 최초 구현 시 `tools/bootstrap.py --apply --project android --signing repo-dev`로 저장소 전용 dev-only 키를 생성하고 Gradle `signingConfig`에 연결. 기존 생성 키는 절대 변경 금지.
- 이 개인 개발 전용 키는 별도로 검토한 프로젝트 정책에 따라 private repo에만 저장한다. **운영·공개 배포용 서명 키와 혼용하지 않는다.**
- 외부 Secrets/토큰과 실서비스 인증 정보를 저장소에 커밋하지 않는다.
- APK 전달 시 Release의 실제 다운로드 URL, 빌드 SHA, 서명/버전 확인 결과를 제시한다.
- 최초 소스 추가 이후 Actions의 빌드 결과를 한 번 확인한 뒤 상태 문서 업데이트.


현재 사용자 요청에 따라 `Android debug APK`는 main의 앱 소스 변경 push와 수동 실행에서 테스트·빌드·서명 확인·Artifact 업로드를 수행한다. 문서만 변경한 push에서는 경량 검사만 수행한다. 직접 APK는 이 대화에서도 제공하며, 별도 Release 워크플로는 수동으로 유지한다. 최초 APK의 signingConfig 연결과 앱 ID·버전·서명 검증은 Actions에서 통과했다.

