# 배민 블랙 개발 시작

1. 가장 최근 사용자 요청 → [AGENTS.md](AGENTS.md) → [제품 계약](docs/PRODUCT_BRIEF.md) → [확정 결정](docs/DECISIONS.md) 순서로 읽는다.
2. 앱 소스가 없으면 Kotlin/Compose/Room 기반 Android 프로젝트를 이 저장소에 생성한다. 사용자에게 APK 원본이나 별도 역분석을 요청하지 않는다.
3. Android applicationId는 `com.charmjh7.baeminblack`, 모듈은 `app`, minSdk 26, targetSdk는 설치된 안정 SDK 및 API 정책에 맞춰 `project.json`과 일치시킨다.
4. 사용자가 확정한 배지 동작·가게 매칭 규칙을 재탐색하거나 구조를 바꾸지 않는다. 실제 UI 노드 노출은 기기 검증 사항으로만 남긴다.
5. `python3 tools/preflight.py` → `python3 -m unittest discover -s tests -v` → 소스 생성 후 `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
6. 서명은 [CI/APK 계약](docs/CI_RELEASE.md)에 따라 **앱 소스가 준비될 때** 개발용 고정 서명으로 설정한다. 기존 키가 있으면 절대 교체하지 않는다.
7. 완성되지 않은 기능을 완료라고 표시하지 않고 [진행 상태](docs/IMPLEMENTATION_STATUS.md)를 업데이트한다.

공통 지침과 스킬은 코딩 품질을 지원할 뿐, 확정된 제품 요구를 다시 분석하게 하는 요청이 아니다.
