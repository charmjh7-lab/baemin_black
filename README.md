# 배민 블랙

배달의민족 원본 APK를 변경하지 않고 사용하는 **개인 가게 블랙리스트·주문 경험 메모 보조 앱** 개발 저장소입니다.

- 플랫폼: Android / Kotlin / Jetpack Compose / Room
- 동작: AccessibilityService로 현재 화면의 가게를 확인해 블랙리스트 배지와 메모 진입 UI를 접근성 오버레이로 표시
- 배지: 스크롤 중 숨기고, 멈춘 뒤 정확한 위치에 다시 표시
- 등록: 가게 공유 링크를 받거나 수동 입력
- 기록: 가게별 여러 메모, 주의·블랙리스트·즐겨찾기, 로컬 백업 및 복원
- 보안: 배민 APK 수정·비공개 API·HTTPS 가로채기·루팅 사용하지 않음

## 개발 시작

1. [START_HERE.md](START_HERE.md)와 [AGENTS.md](AGENTS.md)를 확인합니다.
2. [docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md)의 확정된 기능과 [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md)의 미구현 상태를 확인합니다.
3. 앱 코드를 생성한 뒤 `python3 tools/preflight.py`, `python3 -m unittest discover -s tests`, `./gradlew :app:assembleDebug`를 확인합니다.
4. GitHub Actions의 **Fast checks**, **Android debug APK**, **Development APK release** 워크플로를 이용합니다.

현재 단계는 **개발 환경과 프로젝트 지침을 설정한 초기 상태**입니다. Android 소스, Gradle Wrapper, 실제 앱 및 설치 가능한 APK는 이후 개발 작업에서 작성해야 합니다. 이 저장소의 설정 파일이나 워크플로가 존재한다는 사실은 기능 구현·실기기 검증 완료를 뜻하지 않습니다.

프로젝트 전용 규칙이 ZIP 스타터의 범용 지침보다 우선합니다.
