---
name: android-lifecycle
description: Android 화면 복귀·공유 Intent·접근성 서비스와 오버레이 수명주기
---

# android-lifecycle

1. 접근성 서비스 활성화/중지·재연결, 잠금·화면 회전·다른 앱 전환 시 이전 배지를 제거한다.
2. 공유 Intent의 `onCreate`/`onNewIntent`와 중복 입력을 처리해 같은 가게가 중복 등록되지 않도록 한다.
3. Room에 저장된 메모와 설정은 프로세스 재생성 이후에도 유지한다.
4. 시스템바·IME·분할 화면에서 overlay 좌표계 및 inset을 확인한다.
5. 배민 내부 화면을 강제로 클릭하거나 주문·결제 조작에 관여하지 않는다.
