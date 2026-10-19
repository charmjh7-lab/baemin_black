---
name: android-lifecycle
description: Android 작업에서 최근 앱 복귀·회전·외부 Activity·잠금·앱 전환·TTS·타이머 상태 문제.
---


# Android lifecycle and entry paths
1. 기존 `Activity` manifest launchMode, taskAffinity, Intent flags, `onCreate`/`onNewIntent`, Fragment/Compose navigation을 실제로 확인한다.
2. 사용자 진입: 앱 아이콘, 최근 앱, 공유 intent, 알림, 외부 사전 팝업, OAuth 복귀 등 **각기 별도 경로**를 기록.
3. 독서 재생/타이머는 화면 떠남·잠금·프로세스 종료에 제품 의도대로 pause/resume/continue를 결정. 화면 밖 코루틴만 계속 돌려 저장 상태를 어긋나게 하지 않는다.
4. 화면과 관련된 document/chat/session/purpose ID는 화면 인스턴스가 파괴되어도 유지. 진입 시 새 문서/대화/세션을 무조건 생성 금지.
5. WebView 흰 화면, 뒤로 포커스, overlay/Window 실패는 원인 조사 후 가장 좁은 변경으로 해결. 버전/플랫폼 실제 동작 검증 필요 시 명시.
6. 시스템 상태바/IME/폴더블의 안전 영역. 기존 하단 고정 제어가 가려지거나 두 번 padding되지 않도록.
7. Google 공식 platform skill을 필요할 때 보고, 스택을 강제로 Compose/Navigation3로 마이그레이션하지 않는다.

