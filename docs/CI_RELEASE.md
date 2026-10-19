# CI 및 APK 전달

- `ci.yml`: push/PR/수동에서 경량 preflight + Python 단위 테스트.
- `android-debug.yml`: main push/수동에서 Android 프로젝트가 실제 존재할 때만 `:app:assembleDebug` 실행, APK를 Actions Artifact로 업로드. 파일이 없으면 준비 단계로 기록하고 성공 종료.
- `release-apk.yml`: 수동 실행에서만 빌드·검증 후 GitHub **Prerelease에 직접 APK 파일** 업로드. 원본 프로젝트와 서명 설정이 완성되기 전에는 명확히 중단.
- Java 17 / Android SDK / Gradle 캐시. 기존 Gradle Wrapper 사용.
- 고정 개발 키: 새 앱 최초 구현 시 `tools/bootstrap.py --apply --project android --signing repo-dev`로 저장소 전용 dev-only 키를 생성하고 Gradle `signingConfig`에 연결. 기존 생성 키는 절대 변경 금지.
- 이 개인 개발 전용 키는 별도로 검토한 프로젝트 정책에 따라 private repo에만 저장한다. **운영·공개 배포용 서명 키와 혼용하지 않는다.**
- 외부 Secrets/토큰과 실서비스 인증 정보를 저장소에 커밋하지 않는다.
- APK 전달 시 Release의 실제 다운로드 URL, 빌드 SHA, 서명/버전 확인 결과를 제시한다.
- 최초 소스 추가 이후 Actions의 빌드 결과를 한 번 확인한 뒤 상태 문서 업데이트.
