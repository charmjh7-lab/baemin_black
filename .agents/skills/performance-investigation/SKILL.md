---
name: performance-investigation
description: 실측된 지연, 화면 버벅임, 메모리·배터리, 초기에 흰 화면/렌더 지연.
---


# Performance: measure, localize, then change
- 사용자가 체감한 구체 상황(큰 문서 첫 열기/두 번째 열기, 탭 이동, WebView memory, Compose frame drop)을 **같은 조건에서 계측**.
- 첫 로드 vs cache hit 분리, cold/warm start 구분, debug/release 성능 특성 구분.
- 지연 코드 구간(파일 읽기/정규화/토큰화/layout/DB/web renderer)별 시간 기록. 무분별한 캐싱은 근본 병목을 가릴 수 있음.
- 수정을 병목에 국소 적용, 결과를 baseline과 비교. 기존 문맥/화질/제스처/데이터가 변하지 않았는지 확인.
- Google official Android Profiler/Compose Performance를 필요할 때 활용. CPU/memory trace는 데이터·개인정보 주의.
- 최적화로 UX 품질을 희생하지 않기. 프레임 깜빡임과 화면 점프도 측정/관찰 대상.

