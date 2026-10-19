# 모델용 자동 초기화 체크리스트

**사람에게 이 체크리스트를 채우도록 요구하지 말 것.** 에이전트가 `START_HERE.md`부터 읽고 직접 수행한다.

- [ ] 저장소 HEAD/main, 코드·문서, 사용자 원문 요구, 기존 설계/서명/데이터 확인.
- [ ] 비어 있음 vs 이미 운영 중인 앱 구분. 기존 것은 위험 없는 범위에서 병합.
- [ ] 사용자 목적과 주 화면/핵심 경험을 `PRODUCT_BRIEF`에 근거와 함께 정의.
- [ ] **신규 UI라면** `DESIGN_SYSTEM`의 판단 순서에 따라 제품만의 시각·언어 방향(화면 위계/밀도, 타이포그래피, 의미 색, 컴포넌트·아이콘, 문구)을 스스로 결정하고 실제 앱 구현에 적용. 프레임워크 기본 테마·타 앱 템플릿 재사용 여부 확인. 기존 UI 국소 변경이면 이 단계로 무단 재설계하지 않음.
- [ ] 첫 이용·반복 사용·중단 복귀·실패/취소 4가지 사용자 여정을 선정.
- [ ] 올바른 앱 아키텍처/패키지명/영속 상태 모델·외부 호출 계약 마련.
- [ ] 앱 요구와 실제 기술 스택에 맞는 소스·Gradle wrapper/Node lockfiles 구성.
- [ ] `bootstrap.py --apply`로 설정, Android 개발용 키를 **신규 프로젝트에 한해** 생성.
- [ ] Android: 실제 Gradle signingConfig, 모듈명, versionCode, APK output 확인.
- [ ] `doctor.py`, `preflight.py`, 툴링 unit 실행. 앱 생성 후 최소 빌드/관련 단위 테스트.
- [ ] GitHub Actions workflow가 repository에서 인식되는지, permissions와 Release 가능 여부 확인.
- [ ] 외부 스킬 필요 판단 → 공식/커뮤니티 출처 및 라이선스 확인 → 필요한 것만 설치.
- [ ] 화면·팝업·버튼·데이터·다크·최근 앱·성능 등 **제품에 맞는** 짧은 회귀 여정 정리.
- [ ] 관련 없는 레이아웃·기존 동작 무단 변경 없는지 diff 검토.
- [ ] `IMPLEMENTATION_STATUS`에 실제 증거와 남은 사용자 조치만 기록.
- [ ] APK를 **명시적으로 요청받았을 때만** 단일 수동 릴리스 실행 후 `.apk` 파일 링크 제공.

**원칙:** 초기에 불필요한 테스트/스킬·모의 화면·사용자 승인 단계를 늘리지 않는다. 준비할 것과 사용자가 실제로 검토할 것을 명료하게 구분한다.


---

## 배민 블랙 실제 구현 항목


이 저장소에는 스타터 지침과 CI만 있는 상태에서 시작한다. 아래 순서대로 **앱 구현 작업**을 수행한다.

- [ ] Android Kotlin/Compose/Room 프로젝트 및 Gradle Wrapper 생성
- [ ] applicationId `com.charmjh7.baeminblack`, minSdk 26, targetSdk와 `project.json` 일치 확인
- [ ] Manifest에 배민(`com.sampleapp`) 대상 접근성 서비스 및 `ACTION_SEND text/plain` 진입 구성
- [ ] Room 모델: 가게·메모·사진 참조·설정·안전한 백업/복원
- [ ] 접근성 노드 탐색, 목록/상세 인식, shop ID·이름+지점·별칭 비교
- [ ] 목록 배지: 스크롤 즉시 숨김 → 마지막 이벤트 180ms 후 재분석·재표시
- [ ] 상세 경고/메모 진입, 비터치 배지와 관리 앱 CRUD UI
- [ ] 배민 외 앱 전환·잠금·서비스 해제 시 오버레이 정리
- [ ] 링크 파서·모호한 매칭·스크롤 전환·백업/복원 회귀 테스트
- [ ] 개발 전용 서명 최초 1회 생성: `python3 tools/bootstrap.py --apply --project android --signing repo-dev`
- [ ] `tools/android-signing-gradle.md` 기준 Gradle signingConfig 연결
- [ ] `python3 tools/preflight.py` 및 `python3 -m unittest discover -s tests -v` 실행
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 빌드
- [ ] GitHub Actions `Android debug APK` 결과 확인
- [ ] APK 요청 시 수동 `Development APK release` 실행 및 실제 APK URL 제공

**기술 결정을 다시 조사하는 체크리스트가 아니다.** 요구사항은 `docs/PRODUCT_BRIEF.md`와 `AGENTS.md`에 확정되어 있다.

실기기에서의 배민 UI 노드 노출과 스크롤 품질은 현재 APK 없는 CI에서 확인할 수 없다. 이 한계는 전체 개발을 중단할 사유가 아니며, 데이터·UI·서비스의 구현과 빌드 후 별도 확인한다.
