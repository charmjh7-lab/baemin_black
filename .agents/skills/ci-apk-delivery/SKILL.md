---
name: ci-apk-delivery
description: Android CI·개발 서명·다운로드 가능한 APK 전달
---

# ci-apk-delivery

1. Gradle 소스가 없을 때에는 문서/설정 검증만 통과시키고 Android 빌드는 건너뛴다.
2. 소스가 준비되면 Wrapper, Java 17, SDK, applicationId와 실제 `:app:assembleDebug`를 확인한다.
3. repo-dev 고정 개발 서명은 최초 한 번 생성하고 이후 절대 교체하지 않는다.
4. push APK는 Actions Artifact, 사용자 전달은 수동 GitHub Release의 직접 `.apk` 자산으로 제공한다.
5. 빌드 SHA·versionCode·서명·다운로드 URL을 실제 증거로 보고한다.
