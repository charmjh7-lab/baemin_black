---
name: project-bootstrap
description: 새 저장소 또는 기존 저장소에 개발 표준을 처음 적용할 때 사용. 사용자에게 수동 CI 설정을 떠넘기지 않고 실제 환경을 완성.
---


# Project bootstrap
1. `START_HERE.md`, 현재 HEAD/브랜치/파일을 읽어 새 프로젝트와 기존 앱을 먼저 구분.
2. 사용자 요구에서 제품 목적·플랫폼·핵심 경험과 금지할 변경을 추출. 이미 제공된 정보를 다시 질문하지 말 것.
3. 빈 저장소면 앱에 필요한 **최소한의 실제 코드**와 빌드/상태 모델을 생성. 왜 Compose/Views/WebView/Node를 선택했는지 1문장 근거. 실체 없는 framework 추정 금지.
4. `python3 tools/bootstrap.py --apply --project <...> --signing repo-dev`로 설정(기존 서명 재생성 금지), Android Gradle 실제 서명 연결.
5. `preflight`→툴링 unit→해당 앱 compile 및 표적 테스트. CI job/GH permission을 실제로 확인.
6. 제품별 4개 사용자 여정, 핵심 경험, 기술 계약을 작성한다. 신규 화면 설계 시 `visual-quality`와 `docs/DESIGN_SYSTEM.md`를 이용해 사용자 목표에서 시각·문구 방향을 먼저 도출하고 실제 화면에 적용한다. 구현 증거와 미검증 항목은 상태 파일에 기록한다.
7. 사용자가 요청한 기능은 가능한 범위에서 한 번에 구현. APK를 요청받은 경우에만 manual release까지 수행.
8. 불필요한 새 문서/빈 템플릿/여러 디자인 시안 금지. 사용자 의도·코드 상태가 명백할 때 질문 생략.
