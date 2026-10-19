# 처음 접속한 개발 에이전트의 시작 명령

이 저장소에는 완성 앱이 아니라 **제품 개발과 품질 운영을 위한 올인원 스타터 계약**이 있을 수 있다. 사용자에게 GitHub Actions·서명·스킬을 일일이 설정하라고 되돌리지 말고, 접근 가능한 도구와 권한으로 **직접 완료**하라. 중간 승인은 치명적인 제품 결정·필요한 서비스 권한에만 요청한다. 모든 작은 결정에 사용자 확인을 요구하지 않는다.

## 0. 우선순위

1. **현재 사용자의 명시적 요청·금지 조건·의도적으로 선택한 방식**
2. 기존에 승인된 제품 계약/디자인, 실제 저장 데이터·호환성·서명
3. `AGENTS.md`, `docs/DECISIONS.md`, `docs/PRODUCT_BRIEF.md` 및 현재 코드
4. 로컬 스킬과 공통 품질 문서
5. 외부 스킬의 제안·일반적인 베스트 프랙티스

사용자가 오류 **분석만** 요청하면 파일을 수정하지 않는다. 기존 작업 진행 중이면 중단 지점과 이미 성공한 테스트를 확인해 이어간다. 코드·테스트·문서를 한꺼번에 변경해 승인되지 않은 제품 규칙을 정당화하지 않는다.

## 1. 저장소/요구를 읽고 시작

- Git repository 실제 HEAD/main, uncommitted diff, 현재 파일과 CI 파일, `README`, `.agents/skills`, `project.json`, 제품 문서를 확인한다.
- `python3 tools/doctor.py` → `python3 tools/preflight.py`를 실행한다. Python 사용 불가 시 파일을 직접 점검하고 사실대로 남긴다.
- 기존 앱이면 아키텍처·UI·데이터·빌드 경로를 먼저 파악한다. **스타터의 기본값으로 기존 앱을 교체하지 않는다.**
- 빈 앱이면 현재 요청이 명시한 플랫폼·제품 목적을 기반으로 현실적인 스택을 선택한다. 제품이 Android 앱이면 기존 이유 없이 웹으로 바꾸거나, WebView가 적합한데 Compose로 재작성하지 않는다. 미정 사항은 문서에 `미결정`으로 기록한다.
- 사용자가 이미 충분히 설명한 내용은 다시 질문하지 않는다.

## 2. 비어 있는 저장소를 실제 개발 가능 상태로 전환

- 실제 필요할 때 앱 소스/Gradle Wrapper 또는 Node project/package lock/필수 디렉터리를 생성한다.
- Android 개발이면 applicationId(고유·안정), 버전, 최소 OS, 빌드 타입, 사용자 데이터 보존, 개발 서명, 주요 화면 복귀를 고려한다. 목적에 맞는 Compose 또는 기존 Views/WebView를 선택한다.
- `python3 tools/bootstrap.py --apply --project <android|web|hybrid|custom> --signing repo-dev`를 실행한다(Android만 signing 적용). `--signing`은 개발용 프로젝트에서만 사용한다. 기존 서명을 찾으면 보존한다.
- Android Gradle 서명 설정을 `tools/android-signing-gradle.md`에 맞춰 실제로 연결한다. 다른 서명 구조를 사용한다면 문서와 release workflow를 그 구조에 연결한다.
- 사용자가 처음부터 운영 배포를 요구한 경우 `repo-dev`를 운영 서명으로 쓰지 말고 운영 배포 계약을 별도로 구성한다.
- 앱 **종류를 정형 목록에서 선택하지 말고** 사용자 요구의 핵심 행동·사용 상황·성공 기준을 분석해 `docs/PRODUCT_BRIEF.md`에 이 앱만의 경험 계약을 작성한다. 신규 UI에서는 `docs/DESIGN_SYSTEM.md`의 절차로 실제 제품에 맞는 색·글꼴·아이콘·밀도·화면별 구조·문구의 근거를 **구현 전에** 판단한다. 특정 앱/기술 스택의 기본 테마나 과거 디자인을 무의식적으로 복사하지 않으며, 여러 시안 제작·색상 선택을 사용자에게 떠넘기지 않는다. 공통 UX/디자인/테스트 문서는 필요한 내용만 연결하고 중복 서술을 늘리지 않는다.
- 각 주요 작업에 (진입 → 행동 → 피드백 → 결과 → 취소/복귀 → 재방문) 흐름을 연결한다. 조작이 코드상 존재하더라도 처음 진입한 화면·긴 콘텐츠·키보드 노출 조건에서 **발견·도달이 어려우면 미완료**다. 구현되지 않은 버튼은 활성 표시하지 않는다.

## 3. 전문 스킬 활성화 — 무조건 많이 설치하지 말 것

- 작업에 해당하는 `.agents/skills/<name>/SKILL.md`를 필요할 때 읽는다. 프로젝트 전체 재설계가 아닌 버그 수리에는 `root-cause-debugging`, `scope-and-provenance`, 필요한 전문 스킬만 사용한다.
- `python3 tools/external_skills.py --recommend --task bootstrap`로 공식·커뮤니티 추천을 확인한다. `--apply`는 에이전트가 허용된 네트워크·Git 접근과 사용 중 에이전트의 경로 호환성을 확인한 뒤 실행한다.
- Android에서는 Google 공식 `android-cli`/`testing-setup`를 우선 후보로, Compose 실제 작업에서는 Google `adaptive`, `edge-to-edge`, `jetpack-compose-performance`를 필요할 때 검토한다.
- 웹 UI에서는 Vercel Web Design Guidelines, 새 디자인 방향 탐색 때 UI UX Pro Max/Anthropic Frontend Design을 후보로 본다. 외부 스킬의 프레임워크 전환·과한 테스트 지시는 자동으로 승인되지 않는다.
- README에 **실제로 설치·사용한 스킬만** 기록한다.

## 4. GitHub 설정·워크플로

- `ci.yml`이 경량으로 실행되고 source-empty 상태에서는 실패하지 않는지 검토한다. 일반 push는 프리플라이트만 수행한다. PR/수동 요청 시에는 현재 스택이 지원하면 단위테스트를 수행하도록 확장한다.
- Android 소스가 실제로 있으면 `release-apk.yml`의 Gradle 태스크, 파일 경로, development signing, 패키지명, versionCode가 일치하는지 확인한다. 한 번만 발행하는 수동 workflow가 GitHub Release에 `.apk` 직접 첨부하도록 한다.
- `gh` 또는 연결된 GitHub API 권한이 있으면 CI·Actions 권한·Workflow 활성화 및 릴리스 가능성을 직접 확인한다. `tools/doctor.py` 보고를 참조한다.
- GitHub 조직 정책·계정 승인·외부 인증 때문에 작업이 막히면 자동으로 권한을 우회하지 않는다. 접근 가능 부분은 끝내고 필요한 사용자 조치 **하나**를 명확히 설명한다.
- 프로젝트 보안 설정/branch protection/원격 Secrets를 허락 없이 느슨하게 하지 않는다.

## 5. 테스트 비용과 성공 판정

- 초기화 단계에서는 `preflight`와 로컬 툴링 유닛 테스트 정도부터 시작한다. 앱 코드를 추가한 뒤 **변경된 핵심 흐름에 맞는 단위테스트·빌드 1회**로 확인한다.
- 사소한 수정에 전체 E2E·에뮬레이터·APK 릴리스를 반복하지 않는다. 사용자의 과거 반복 실패를 막는 짧은 회귀 시나리오를 우선한다.
- 위험 높은 권한/데이터 손실/서명/Android Lifecycle/API 연동은 해당 위험에 맞는 검증을 추가한다.
- 실행 불가능한 실기기 조작은 완료라고 주장하지 않는다. 코드/CI/에뮬레이터/실기기/사람 평가를 분리해 보고한다.
- 요청받은 기능·화면과 변경 범위·파일·검증 증거·미확인 사항을 간결히 보고한다. **APK 요청 시 실제 설치 가능한 APK Releases 링크를 반환**한다.

## 6. 그 이후

문서·스킬의 원칙을 기계적으로 나열하는 대신 **제품에서 재발한 실제 문제를 회귀 계약으로 한 번만 추가**한다. 스킬 텍스트를 계속 누더기처럼 붙이지 않는다. 이미 해결했던 조사·테스트를 처음부터 반복하지 않는다. 제품 경험을 유지하는 선에서 작업을 한 번에 끝낸다.


---

## 배민 블랙 시작 시 추가 지침


1. 가장 최근 사용자 요청 → [AGENTS.md](AGENTS.md) → [제품 계약](docs/PRODUCT_BRIEF.md) → [확정 결정](docs/DECISIONS.md) 순서로 읽는다.
2. 앱 소스가 없으면 Kotlin/Compose/Room 기반 Android 프로젝트를 이 저장소에 생성한다. 사용자에게 APK 원본이나 별도 역분석을 요청하지 않는다.
3. Android applicationId는 `com.charmjh7.baeminblack`, 모듈은 `app`, minSdk 26, targetSdk는 설치된 안정 SDK 및 API 정책에 맞춰 `project.json`과 일치시킨다.
4. 사용자가 확정한 배지 동작·가게 매칭 규칙을 재탐색하거나 구조를 바꾸지 않는다. 실제 UI 노드 노출은 기기 검증 사항으로만 남긴다.
5. `python3 tools/preflight.py` → `python3 -m unittest discover -s tests -v` → 소스 생성 후 `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
6. 서명은 [CI/APK 계약](docs/CI_RELEASE.md)에 따라 **앱 소스가 준비될 때** 개발용 고정 서명으로 설정한다. 기존 키가 있으면 절대 교체하지 않는다.
7. 완성되지 않은 기능을 완료라고 표시하지 않고 [진행 상태](docs/IMPLEMENTATION_STATUS.md)를 업데이트한다.

공통 지침과 스킬은 코딩 품질을 지원할 뿐, 확정된 제품 요구를 다시 분석하게 하는 요청이 아니다.

배민 블랙은 기술 선택·목록 배지 동작·데이터 구조가 `AGENTS.md`와 `docs/PRODUCT_BRIEF.md`에 확정되어 있다. 해당 결정을 새로 탐색하지 않고 앱 소스 구현을 진행한다.
