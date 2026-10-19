# 로컬 스킬과 공식·커뮤니티 스킬 사용 정책

## 왜 복붙된 '대형 프롬프트'가 아닌가

공통 규칙은 `AGENTS.md`, 제품 고유 목표는 `PRODUCT_BRIEF.md`/`DESIGN_SYSTEM.md`, 구체 방법은 `.agents/skills`에 따로 둔다. 바깥 스킬은 기술 전문성을 강화할 **참고/실행 도구**이지, 사용자 결정과 변경 범위를 바꾸는 상위 권한이 아니다.

## 외부 스킬 후보와 호출 조건

| 출처/스킬 | 언제 사용 | 사용하지 않을 때 |
|---|---|---|
| Google `android/skills` 공식 `android-cli` | Android 기기·UI·레이아웃 검사 준비 | 웹만 개발 중 |
| Google `testing-setup` | 새 Android 앱의 테스트 기반을 처음 구성할 때 | 버튼 한 개 수정·이미 검증 체계 있을 때 |
| Google `jetpack-compose/adaptive` | 실제 Compose 앱에서 폴더블·화면 크기 문제 해결 | WebView, View 기반 화면/Navigation3 미사용·마이그레이션 미승인 |
| Google `system/edge-to-edge` | Compose 앱의 IME·status/nav bar overlap | 사용하지 않는 Compose migration 강요 |
| Google `jetpack-compose-performance` | Compose 재구성/지연이 **측정**된 경우 | 단순 UI 디자인 요청 |
| Google `profilers/android-profiler` | 메모리·ANR·프레임·시작 지연 원인 측정 | 모든 소규모 패치 |
| Google `security/android-intent-security` | external Activity·Intent·PendingIntent와 권한 | 관계없는 로컬 화면 |
| Vercel `web-design-guidelines` | Web/WebView UI 구조·상태·접근성 리뷰 | Android native 전체 스타일 변경 |
| Anthropic `frontend-design` | 새 웹 디자인 방향, AI 슬롭 억제 | 기존 승인 디자인 무단 재작업 |
| Community `ui-ux-pro-max` | 새 디자인 시스템 선택/제품별 품질 참고 | 스킬 기본값으로 기존 UI 덮어쓰기 |
| Community `obra/superpowers` | `systematic-debugging`/`verification-before-completion` 원칙만 참고 | 전체 프로세스 설치로 모든 수정에 다단계 승인/전체 테스트 강제 |
| Maestro | 실제 사용자 여정 UI 자동화가 충분히 가치 있을 때 | 기기 없는 상태에서 허위 검증, 매 커밋의 비싼 E2E |

## 자동 선택

`python3 tools/external_skills.py --recommend --task bootstrap`는 스택을 기반으로 설치할 후보를 표시한다. `--apply --task bootstrap`을 실행하면 **허용 목록에 있는 Google 스킬만** `.agents/skills/vendor-google-*`로 복사한다. 외부 Git 소스의 코드·라이선스를 검토한 뒤 에이전트가 실행한다. 모든 후보를 한 번에 설치하지 않는다.

외부 스킬의 `SKILL.md`에는 강한 명령문이 포함될 수 있다. 사용 목적에 부적절한 명령(예: 전체 Compose/Navigation 마이그레이션, 반드시 모든 화면 스냅샷 생성, 모든 작업에 TDD 강제)은 요청 범위 밖에서는 실행하지 않는다.

Vercel/Anthropic/커뮤니티는 문서 참조를 기본값으로 한다. 필요시 스킬 담당 에이전트가 설치방법을 조사해 호환성과 라이선스를 확인한 뒤 설치한다. 외부 원격 코드 실행을 자동화하지 않는다.

## 신뢰와 출처

- 공식 Google: https://github.com/android/skills
- Vercel: https://github.com/vercel-labs/agent-skills
- Anthropic: https://github.com/anthropics/skills/tree/main/skills/frontend-design
- UI UX Pro Max (커뮤니티): https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- Superpowers (커뮤니티): https://github.com/obra/superpowers
- Maestro 도구: https://github.com/mobile-dev-inc/Maestro

패키지 안의 로컬 스킬은 이 자료들을 그대로 복사하지 않고, 실제 개발 실패에서 추출한 독자적인 한국어 계약으로 구성했다. 외부 소스 라이선스를 우회해 번들에 내장하지 않는다.


---

## 배민 블랙에서 활성화할 범위


ZIP 범용 스타터의 스킬 가운데 다음 **Android 개인 앱에 직접 필요한 범위**만 포함한다.

- `android-lifecycle`: 화면 전환, 공유 Intent, 잠금, 서비스 생명주기
- `interaction-completeness`: CRUD, 닫기·취소·저장·재진입
- `product-ux`: 기존 요구에서 UX 성공 조건 도출
- `visual-quality`: 오버레이·관리 앱 디자인과 문구
- `scope-and-provenance`: 확정 사양 존중, 불필요한 재조사/전면 변경 방지
- `risk-based-verification`: 영향 범위에 맞는 테스트
- `ci-apk-delivery`: Android 빌드, 고정 개발 서명, APK 전달
- `root-cause-debugging`: 회귀 발생 시 사용
- `performance-investigation`: 실제 배지 지연/이벤트 폭주가 측정된 경우에만
- `evidence-and-handoff`: 구현/검증/미검증 구분

웹/노드/범용 AI 답변/WebView 전용 스킬과 프로젝트에 관련 없는 범용 템플릿은 제외한다. 외부 스킬을 자동 설치하지 않는다.

배민 블랙에서는 Android/Compose·접근성 서비스·Intent·빌드 관련 스킬만 실제 과제에 해당할 때 사용한다. AI/WebView 전용 스킬은 현재 설치하지 않는다.
