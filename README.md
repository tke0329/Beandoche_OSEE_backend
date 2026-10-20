# OSEE Backend

Java 21, PostgreSQL, Flyway를 사용한다. `.env.example`을 로컬 `.env`로 복사하고 비밀값을 설정한다. 실제 `.env`는 커밋하지 않는다.

```powershell
docker compose up -d postgres
.\gradlew.bat bootRun
```

## 회원탈퇴

- `DELETE /api/users/me`: 본인의 Bearer Access Token과 허용된 정확한 `Origin`이 필요하다. 다른 사용자 ID는 입력받지 않는다.
- 성공 시 200과 `{ "withdrawn": true, "googleUnlinkRequired": true, "kakaoUnlinkPending": false }` 형태를 반환한다. 연결된 제공자에 따라 두 플래그는 달라진다.
- 사용자를 `WITHDRAWN`으로 변경하고 프로필/사진/소셜 연결을 삭제한다. 모든 Refresh Token을 무효화하고 HttpOnly 쿠키를 삭제한다. 기존 Access Token도 사용자 상태 검증으로 거부한다.
- 외부 해제 작업은 같은 DB 트랜잭션으로 `social_unlink_task`에 기록하며 외부 장애가 내부 탈퇴를 롤백하지 않는다. 새 Flyway `V2610202100`이 필요하다. 현재 구현된 개인정보 도메인은 인증/프로필뿐이며 향후 신체정보/옷장/착장 저장소 추가 시 삭제 범위도 확장해야 한다.
- Google은 사용자가 [Google 계정 연결 설정](https://myaccount.google.com/connections)에서 직접 해제하도록 안내한다. Google 작업에는 제공자 식별값을 보존하지 않는다. [Google 공식 안내](https://developers.google.com/identity/gsi/web/guides/revoke).
- Kakao 자동 해제는 로컬 `.env`의 **서버 전용** `KAKAO_ADMIN_KEY`와 해당 키의 Unlink API 권한이 필요하다. 로그인 REST API 키와 다른 키이며 Flutter에 넣지 않는다. [Kakao 공식 API](https://developers.kakao.com/docs/ko/kakaologin/rest-api).
- 키 미설정/권한 오류는 `CONFIG_REQUIRED`로 기록하고 15분 간격 재확인한다. 외부 통신 실패는 지수 간격으로 최대 10회 재시도 후 `USER_ACTION_REQUIRED`로 남긴다. 성공 시 작업의 제공자 식별값을 삭제한다. 미완료 Kakao 작업이 재가입한 계정 연결을 나중에 끊지 않도록 처리 완료 전 같은 계정 재가입을 차단한다.
- 제공자 식별값은 미완료 Kakao 해제 작업에만 목적 한정으로 남는다. 운영자는 `USER_ACTION_REQUIRED`/`CONFIG_REQUIRED` 상태를 확인하고 처리해야 하며, 운영 전 보관 기간/수동 종료 절차를 별도로 확정해야 한다.

## 세션과 로그

- Refresh 회전은 최초 로그인부터 최대 48시간을 연장하지 않는다. 오래된 Refresh 요청은 204를 반환하되 새 쿠키를 지우지 않는다. 로그아웃/탈퇴만 명시적으로 쿠키를 삭제한다.
- 웹 클라이언트는 Web Locks로 탭 간 회전을 직렬화하고 BroadcastChannel로 종료 상태를 전파한다.
- 외부 SDK의 잘못된 쿠키 내용을 Tomcat이 INFO로 평문 기록하지 않도록 해당 쿠키 파서만 WARN으로 설정한다. 애플리케이션/보안 오류 로그를 전체 비활성화하지 않는다.

```powershell
.\gradlew.bat test
.\gradlew.bat build
```
