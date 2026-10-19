# 구현 상태

## 소스 구현
- Android 독립 앱 / Compose 관리 UI / Room 가게·다중 메모 / 사진 첨부·정리
- 정확한 식별·별칭·중복 방지 / ACTION_SEND 및 제한된 HTTPS 리디렉션
- 접근성 서비스 / 제한된 노드 탐색 / 이벤트 병합·취소 / 비터치 배지·상세 진입
- 스크롤 숨김 및 180ms 재취득 / 앱 이탈·잠금·회전·해제 시 정리
- ZIP·JSON 백업 / 파일 경로·용량·버전 검증 / 기존 기록 보존 병합
- 설정 저장 / 접근성 활성화 조회 / 오류 표시·삭제 확인·편집 취소
- 링크·매칭·스크롤·노드 fixture·Room 저장·사진 포함 복원 단위 테스트

## 검증
- Python 계약·툴링 검사 11건 통과.
- Android 자동 테스트 17건 통과(실패·건너뜀 0), assembleDebug 성공.
- Robolectric에서 권한 없는 관리 화면 실행 → 가게 등록 → 블랙리스트 분류 → 메모 저장 → Activity 재생성 후 기록 유지 확인.
- 앱 ID `com.charmjh7.baeminblack`, 버전 1.0.0(1), 고정 개발 서명 검증 성공.
- 빌드 소스 커밋 `2b3c5e510b83ce7a2acfcd38729e803739079048`.
- [성공한 GitHub Actions 실행](https://github.com/charmjh7-lab/baemin_black/actions/runs/39294205714) / [APK Artifact](https://github.com/charmjh7-lab/baemin_black/actions/runs/39294205714/artifacts/12207201707).
- 내려받은 APK도 로컬에서 앱 ID·버전·서명이 일치함을 재확인.
- 실기기 설치, 배민 화면별 노드 노출·좌표·터치 통과·스크롤·상세 진입: 미검증.

## 권한
- 접근성 서비스는 사용자가 Android 설정에서 직접 활성화해야 합니다.
- 원본 APK나 추가 서버 권한은 필요하지 않습니다.

## 미구현 항목
요청된 기능의 코드 미구현 항목은 없습니다. 배민 실제 화면에서의 인식 범위와 오버레이 동작은 위 실기기 검증 항목으로 남아 있습니다.
