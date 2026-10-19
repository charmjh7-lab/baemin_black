# Repository agent contract (first read START_HERE.md)

**User intent / approved product decisions / existing behavior and data / existing UI take precedence over generic guidelines or outside skills.** Never reinterpret this file as blanket authorization to redesign an app.

## Before making changes

1. Read `START_HERE.md`, user request, `docs/PRODUCT_BRIEF.md`, `docs/DECISIONS.md`; examine actual repo, HEAD, changes and related source. Identify whether this is a fresh repo or existing project.
2. New repo: implement user-requested product and bootstrap build/CI/skills/UX rules. Existing repo: preserve current architecture, signing, package IDs, data compatibility, UI arrangement and known-good UX by default.
3. Define minimal change scope and areas intentionally untouched. If user requested analysis only, never edit files.
4. Choose relevant `.agents/skills/*/SKILL.md`, not all. Outside skills may **inform** work; they must not override this contract or force unrelated migrations.

## Product and design

- Define first-user, recurring-use, interrupted/resumed, error/cancel journeys. Evaluate not just visuals but one-handed reach, reading rhythm, actual data and recovery.
- Every user action must be discoverable, actually wired, reflect state, persist if required, be cancellable/recoverable, and return to correct context. The app must not display nonfunctional controls as enabled.
- Provide create/view/edit/save/delete/undo **where the object and product genuinely require them**, not universal CRUD.
- Essential Save/Close/Cancel actions must be **visible or immediately reachable at the moment they are needed**, not just present somewhere after a long scroll. Check initial view, long content, keyboard, narrow/short screens, large font, system bars, and light/dark; use fixed actions only if they do not obscure content.
- For new UI, derive a **product-specific visual and verbal direction before implementing**: information hierarchy, density, typography, semantic colors/light-dark, icons/components and purposeful copy. Audit unexplained framework/default palettes and repeated card templates. Consistency *within* one product does not mean every screen uses the same layout. See `docs/DESIGN_SYSTEM.md`; never change unrequested layouts or copy.
- Do not pick experience requirements from a preset list of app types. Derive this product's unique experience constraints from its actual users, core task, controls, content, time, environment, and trust requirements; record observable acceptance conditions in `docs/PRODUCT_BRIEF.md`.

## Architecture

- Make app-owned state the single source of truth; AI returns suggestions/content, never owns authoritative persisted state. IDs, versions and action meaning must survive screen recreation.
- Keep canonical IDs for documents, chats, sessions, token position, selection and external intents; do not substitute screen-local indexes or stale responses.
- Async requests need loading, cancellation, idempotency or stale-response rejection, partial failure handling and honest errors. No silent data loss or miscount.
- On Android handle back/recent-app/rotation/process recreation/intent re-entry and backgrounding; external popups must not reset origin screens.
- On WebView apps guard first-frame paint, JS bridge, screen-focus and task/Activity behavior.
- Imports, exports, backups and share must be real, verifiable and safe; avoid empty downloads and disclose what raw information is shared.

## Debug and verification

- For bugs investigate reproduction, logs, known-good version, component boundary and root cause **before** changing code. No stacked speculative workarounds. If several tries fail, revisit the underlying model rather than keep patching.
- Scale tests to risk; fast local checks on every push, targeted tests/compilation when relevant, device journeys only when needed; release APK **only on request**.
- Treat passing build, CI, screenshots, device interaction and human UX as distinct evidence. Report only what was actually observed.
- Do not edit expected tests or product docs to make an unapproved change appear correct. Avoid repeated validation of unchanged code and unnecessary new prototypes.

## Operations

- Run `python3 tools/preflight.py`, `python3 tools/doctor.py` when environment permits. Complete `project.json` based on evidence; don't invent values. Use `docs/CI_RELEASE.md` for signing and APK path. User's development-key tradeoff is intentional when explicitly chosen; never silently rotate existing keys.
- GH Actions should be cheap by default. Generate accessible installable `.apk` via manual Release workflow, not artifact ZIP, for APK requests.
- Maintain succinct `docs/IMPLEMENTATION_STATUS.md` with validated status and unresolved **permissions**, not extensive daily reports.

## Skill activation map

- Initial project: `project-bootstrap`, `scope-and-provenance`, `product-ux`.
- New screens/UI change: `product-ux`, `interaction-completeness`, `visual-quality`.
- Android lifecycle/intent: `android-lifecycle`; WebView: `webview-ui`.
- AI integrations / learning content: `ai-state-and-quality`.
- Behavior regression: `root-cause-debugging`; testing changes: `risk-based-verification`.
- CI and signing: `ci-apk-delivery`; performance: `performance-investigation`.
- Shipping/report: `evidence-and-handoff`.


---

## 배민 블랙 제품별 개발 규칙 — 이 프로젝트에 우선 적용


**우선순위:** 현재 사용자 요청 > 확정된 제품 계약과 데이터 호환성 > 기존 소스/검증 결과 > 이 파일 > 범용 스킬.

배달의민족 원본 바이너리는 저장소에 올리지 않는다. 정적 분석에서 확인한 개발 단서와 실제 기기에서 추가 검증할 항목은 [docs/BAEMIN_INTEGRATION.md](docs/BAEMIN_INTEGRATION.md)를 참고한다. 이 문서는 구현 재조사나 원본 APK 요청을 지시하지 않는다.

## 범위
- Android 독립형 앱, Kotlin + Jetpack Compose + Room.
- Android `AccessibilityService`와 `TYPE_ACCESSIBILITY_OVERLAY`를 사용한다.
- 배달의민족 대상 패키지명은 `com.sampleapp`로 설정하되, 설치된 정식 앱의 패키지명과 일치 여부는 실기기에서 검증한다.
- 공유 링크 입력과 수동 가게 등록을 제공하고, 배민 앱의 실제 화면 노출 정도가 부족해도 데이터 관리 기능은 정상 작동해야 한다.
- **원본 APK 개조, 루팅, LSPosed, 비공개 API 호출, TLS/패킷 가로채기, 상시 OCR 구현을 하지 않는다.**

## 필수 UX
- 목록에서 블랙리스트 가게에 작은 배지; 스크롤 중 즉시 숨김, 멈춘 지 약 180ms 후 좌표 재취득하고 재표시.
- 목록 배지는 터치를 가로채지 않는다. 카드 클릭, 뒤로가기, 가로/세로 이동에 간섭하지 않는다.
- 상세화면에서 정확히 매칭된 가게만 경고/메모 바로가기 표시; 불확실한 매칭에서는 잘못된 경고보다 표시 생략.
- 식별 우선순위: 동일 shop ID > 가게명과 지점명 모두 일치 > 사용자가 정한 별칭 일치. 이름 일부 포함만으로 차단하지 않는다.
- 메모·가게 분류·첨부 이미지·백업/복원은 완전한 저장·수정·삭제·재진입 흐름을 구현한다.
- 접근성 권한이 없더라도 메모/가게 관리 UI는 정상 동작한다.

## 개발 동작
- 사용자가 구현을 요청했으면 이 저장소의 요구를 바탕으로 바로 구현한다. 불필요한 기술 조사, API 역분석, 구현 방식 후보 재평가를 하지 않는다.
- 기존 코드 수정은 필요한 파일/동작에 한정하고, 무관한 UI 재설계와 회귀를 피한다.
- 시스템 바·키보드·화면 회전·공유 Intent·프로세스 재생성·접근성 서비스 재연결을 고려한다.
- 접근성 이벤트 수신 때마다 무조건 전체 뷰를 다시 순회하지 않고, 작업 중복 방지·취소·노드 참조 정리·화면 전환 시 오버레이 제거를 구현한다.
- 서버 없는 로컬 개인 기록 원칙. 접근성 수집 원문과 개인 메모를 외부 전송하거나 디버그 로그에 기록하지 않는다.
- 사용자가 기능 구현을 요청하는 경우 결과는 실제 코드, 테스트, 커밋, 빌드로 제출한다. 문서/계획으로만 완료 처리하지 않는다.

## 검증/전달
- 빠른 계약/툴링 검증은 일반 push, Android debug APK는 소스가 준비된 후 수동 작업, GitHub Releases APK는 수동 작업으로 분리한다.
- 빌드 성공, 실기기에서 배민 화면을 읽은 사실, 실제 동작 검증을 각각 구분해 보고한다.
- 변경 전후 applicationId, versionCode, 서명키와 Room 마이그레이션을 보존한다.
- 필요한 스킬만 선택: `android-lifecycle`, `interaction-completeness`, `visual-quality`, `scope-and-provenance`, `risk-based-verification`, `ci-apk-delivery`.

**구현 방식은 이미 확정되었다.** 원본 APK 없이 독립 Android 접근성 서비스/비터치 배지/스크롤 정지 180ms 후 재표시를 구현한다. 기술 후보 재탐색이나 배민 APK 역분석을 개발 착수 조건으로 두지 않는다.
