# 구현 및 검증 상태

- 1.1.0(4), 기존 `com.charmjh7.baeminblack`·Room v1·개발 서명·공유/메모/백업 경로 보존. 스키마 변경과 데이터 초기화 없음.
- 서비스 연결/대상/화면/매칭/표시를 분리하고 상태 버튼은 인식 실패에도 유지. 배지·상세·상태 오버레이 표시 결과를 별도 관리.
- 문맥 기반 제목·카드 추출, 구체적인 제외 사유/후보 통계, 동명이점 보류, 기존 ID/전체 이름·지점/별칭 매칭 유지.
- 같은 화면 이벤트 병합, 분석 간격 제한/고정 예약, generation·현재 루트·제목/좌표 재검증. 스크롤 중 숨김과 180ms 정지 후 새 좌표 사용.
- 빠른 블랙 등록/해제는 Room 트랜잭션. 실행취소는 가게와 메모 내용 모두 비교하여 후속 수정 보호. 미등록 가게 메모는 저장할 때에만 가게와 함께 커밋.
- 내부 JSONL 순환 로그: 7일/10MB, bounded 비동기 writer, 반복·유실 집계, trace 연결, 민감 원문 제외. 상세 진단은 배민 화면에 한해 5분, 원문은 명시 선택만 허용.
- 진단 화면: 단계별 상태·최근 시각·실패·보관 집계·trace·스냅샷 확인/삭제. ZIP은 manifest/events/summary/errors와 선택한 snapshots를 포함하고 실제 문서를 다시 열어 검사.
- 로컬 preflight 및 Python 11개 테스트 통과. 로컬 Android 실행은 Gradle 배포 호스트 연결 실패로 불가.
- 최종 APK 코드 커밋 `9f80785b6564d78981b73544e55b0a1bd13a83be`: GitHub Actions 실행 `39303451636`에서 Android 43개 테스트(실패/건너뜀 0), assembleDebug, versionCode 4, 기존 개발 인증서 검증 및 Releases 게시 통과. Python 11개 테스트와 preflight도 통과.
- APK: https://github.com/charmjh7-lab/baemin_black/releases/download/dev-1.1.0-9f80785/BaeminBlack-1.1.0.apk
- 요청 APK는 Android debug 워크플로의 명시적 `[release-apk]` 커밋에 한해, 검증한 APK를 재빌드 없이 Releases에 게시한다. 기존 수동 릴리스 경로도 유지.
- 실기기 미검증: 실제 배민 트리 노출/인식 정확도, 삼성 기기의 오버레이 위치·창 이벤트·잠금/권한 해제/재연결, 문서 제공자별 ZIP 쓰기/재열기, 장시간 배터리/체감 성능. 가상 노드·Robolectric 성공을 실기기 성공으로 간주하지 않는다.
