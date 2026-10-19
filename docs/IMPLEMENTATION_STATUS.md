# 구현 및 검증 상태

| 구성 | 상태 | 근거·다음 단계 |
|---|---|---|
| GitHub 저장소 | 생성됨 | `charmjh7-lab/baemin_black` |
| 제품 범위·개발 지침 | 작성됨 | AGENTS.md, docs/PRODUCT_BRIEF.md |
| Python 사전점검/테스트 | 준비 | GitHub Actions 실행 결과는 별도 확인 |
| Android 앱 소스 | **미구현** | Kotlin/Compose/Room 생성 필요 |
| Gradle Wrapper | **미구현** | 앱 생성 시 포함 |
| 개발 서명 | **미생성** | 최초 Android 구현 시 고정 repo-dev 키 생성/연결 |
| APK 빌드/Release | **미실행** | 소스·서명 연결 후 수행 |
| 접근성 노드/배지 실제 동작 | **실기기 미검증** | 배민 설치 기기에서 확인 |
| 메모/백업 | **미구현** | 앱 개발 시 작성 |

검증 증거 없이 항목을 '완료'로 바꾸지 않는다.

## 스타터 패키지 품질 체계 복구 (2026-10-20)

- 원본 기반 상세 절차 복구: `AGENTS.md`, `START_HERE.md`, `docs/DESIGN_SYSTEM.md`, `docs/UX_ACCEPTANCE.md`, `docs/ENGINEERING_CONTRACT.md`, `docs/TEST_STRATEGY.md`, `docs/CI_RELEASE.md`, `docs/SKILLS_POLICY.md`.
- 재발 방지 사례 26개: `docs/PAST_FAILURE_ATLAS.md` 복구.
- 로컬 개발 스킬 10개를 원본 내용으로 복구하고 `project-bootstrap` 추가.
- 도구: `preflight.py` 검사 강화, `doctor.py` GitHub 상태 조회 복구, `external_skills.py` Google 공식 스킬 선택적 설치 복구, `bootstrap.py` 기존 키 보존.
- CI: 일반 push에는 빠른 검사만, Android APK 빌드·Release는 수동, Android 단위 테스트는 요청 시 실행.
- **검증 분리:** 원격 커밋 및 파일 존재를 확인함. GitHub Actions 실행 결과에 실패 기록이 있으나 로그 다운로드가 404여서 원인 미확정. 코드 테스트 통과로 주장하지 않음.
- Android 앱 소스/Gradle Wrapper와 실제 APK는 아직 구현되지 않았음.
