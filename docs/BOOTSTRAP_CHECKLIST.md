# 배민 블랙 Android 최초 구현 체크리스트

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
