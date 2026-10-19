---
name: ci-apk-delivery
description: 새 Android 프로젝트 설정, Actions 비용, 서명, 사용자 APK 전달.
---


# CI and APK delivery
1. 저장소 빌드 도구·모듈·signingConfig·app ID·version 확인 후 `ci.yml`/`release-apk.yml`을 실제 프로젝트에 맞춘다.
2. push는 저비용 `preflight`만. PR/수동은 필요한 단계, APK는 **명시 요청 또는 정해진 릴리스 시점**에만.
3. 새 개인 개발 앱은 사용자 정책에 따라 per-repo 개발 `repo-dev` 키 생성. 기존 앱 서명을 바꾸지 않음. Gradle 연결 확인.
4. APK를 요청받으면 GitHub manual release workflow 1회 또는 동일 커밋 산출물 재사용. 작업이 실패하면 로그 원인부터 해결.
5. Releases에 개별 `.apk` 업로드; checksum/SHA/버전/서명/설치 호환을 가능한 범위에서 확인. Actions ZIP으로 대신하지 말기.
6. 필요한 GitHub API 접근 권한이 없는 경우 우회/허위 완료 없이 남은 권한 조치 명시.
7. 캐시/required check/동시성/문서 변경 구분/비용 한도는 실제 프로젝트에 맞춰 설정.

