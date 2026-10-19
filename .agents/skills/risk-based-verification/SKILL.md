---
name: risk-based-verification
description: 테스트 설계 및 완료 판정. 빌드/CI 사용량·에뮬레이터 시간 절약.
---


# Risk-based verification
- 영향 범위·데이터 위험·Android 권한·생명주기·네트워크/외부 연동에 따라 검증 단계를 고른다.
- 작은 수정: 관련 테스트 및 UI 경로 1개+인접 회귀 1개. 중요한 구조 변경: 추가 회귀/실기기.
- 기존 같은 조건으로 통과한 테스트를 매 단계 무작정 다시 실행하지 않는다.
- GitHub push마다 APK 생성, 수십 회 Workflow rerun, E2E 전체 실행 금지.
- 자동 UI 검사와 사람이 느끼는 독서 리듬·한손 조작·미학적 위계를 분리.
- `unittest`/compile success != physical device UX. 최종 보고는 측정한 범위만 쓰기.
- 테스트 실패를 피하려고 프로덕션 코드를 우회하거나 assertion/snapshot을 임의 수정 금지.

