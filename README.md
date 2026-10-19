# 배민 블랙

배민 원본 바이너리는 GitHub에 올리지 않고, 확인된 연동 단서와 실기기 검증 항목은 [배민 연동 참고 문서](docs/BAEMIN_INTEGRATION.md)에 별도로 기록했습니다.

배달의민족을 이용할 때 개인 블랙리스트와 주문 경험을 확인하는 Android 보조 앱입니다. 원본 배민 APK는 수정하지 않습니다.

## 기능
- 가게별 일반·주의·블랙리스트·즐겨찾기 분류, 다중 메모와 사진 기록
- 가게·지점·별칭·메모 검색, 분류 필터, 최근 수정순/이름순 정렬
- 배민 공유 링크 수신과 직접 등록, 가게 고유번호 중복 방지
- 접근성 노드로 읽을 수 있는 가게 카드에 비터치 금지 배지
- 스크롤 중 즉시 숨김 → 마지막 스크롤 후 180ms에 노드·좌표를 새로 얻어 표시
- 상세화면 개인 경고와 메모 바로가기
- 사진 포함 ZIP / 사진 제외 JSON 백업, 기존 데이터를 유지하는 병합 복원

## 설치와 사용
1. [Actions](https://github.com/charmjh7-lab/baemin_black/actions/workflows/android-debug.yml)의 성공한 실행에서 `BaeminBlack-debug-…` Artifact를 내려받아 압축을 풀고 `app-debug.apk`를 설치합니다. Android 8.0 이상을 지원합니다.
2. 앱의 **켜기** 또는 **설정 → 접근성 서비스 설정 열기**에서 배민 블랙을 활성화합니다. 외부에서 설치한 앱은 기기의 앱 정보 메뉴에서 제한된 설정 허용이 필요할 수 있습니다.
3. 배민 가게 상세의 **공유 → 배민 블랙**을 선택하거나 **가게 등록**으로 직접 등록합니다. 이름을 자동으로 추출하지 못하면 입력해 주세요.
4. 기록 화면에서 분류를 선택하고 **메모 추가**로 날짜·메뉴·제목·본문·만족도·사진을 기록합니다. 가게 정보에서 지점명과 별칭을 보완할 수 있습니다.
5. 목록에는 블랙리스트만 작은 빨강 배지를 표시합니다. 같은 이름의 다른 지점이나 좌표가 불확실하면 표시를 생략합니다. 상세의 경고/메모 버튼을 누르면 개인 기록이 열립니다.
6. **설정 → 데이터 백업·복원**에서 Android 문서 선택기로 저장/복원합니다. 가게 고유번호가 같으면 병합하고 기존 분류와 기록을 보존합니다. 기록 ID 충돌은 새 ID로 분리합니다. JSON에는 사진이 포함되지 않습니다.

접근성 권한 없이도 가게·메모·검색·사진·백업 기능을 사용할 수 있습니다. 모든 기록은 기기 내부에 저장합니다. 인터넷 권한은 사용자가 전달한 허용된 배민 HTTPS 공유 링크의 제한된 리디렉션 확인에만 사용합니다. 계정·쿠키·비공개 API·OCR·화면 캡처·외부 서버 전송은 사용하지 않습니다.

## 개발
Java 17, Android SDK API 37, Kotlin/Compose/Material 3, Room, Coroutines/Flow.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
python3 tools/preflight.py
python3 -m unittest discover -s tests -v
```

`main`의 앱 소스 push 및 수동 실행에서 테스트·디버그 APK를 빌드하고 Artifact로 업로드합니다. private 저장소 전용 고정 개발 키를 디버그 서명에 사용합니다. 별도 릴리스 키는 필요하지 않습니다. Room 스키마는 버전 1이며 이후 변경 시 명시적 마이그레이션을 추가해야 합니다(파괴적 자동 초기화 없음).

실제 배민의 접근성 노출, 카드 계층, 좌표, 상세 제목 및 공유 링크 리디렉션은 실기기 확인이 필요합니다. 가상의 노드 fixture 테스트와 빌드 성공을 실기기 성공으로 간주하지 않습니다. 대상 패키지는 요청에 따라 `com.sampleapp`입니다.

적용한 저장소 스킬: android-lifecycle, interaction-completeness, visual-quality, scope-and-provenance, risk-based-verification, ci-apk-delivery. 제품 계약과 품질 운영 문서는 `START_HERE.md` 및 `docs/`에 보존되어 있습니다.
