# OSEE Backend

Java 21, PostgreSQL, Flyway를 사용한다. `.env.example`을 로컬 `.env`로 복사하고 비밀값을 설정한다. 실제 `.env`는 커밋하지 않는다.

## 아바타용 신체정보와 JSON 입력

이 단계는 **신체정보 저장 → 버전별 입력 스냅샷 → 모델링용 중립 JSON 제공**까지 구현한다. MakeHuman 실행, 슬라이더 파라미터 추정, 프론트, 의류, 모델 파일 업로드·다운로드는 포함하지 않는다. 신규 Flyway `V2610261200`이 `body_profile`, `avatar_profile`, `avatar_generation_request`를 추가한다.

모든 경로는 본인의 Bearer Access Token이 필요하며 응답은 `Cache-Control: no-store`다. 요청에 다른 사용자 ID를 넣어도 소유자를 바꿀 수 없다.

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| PUT | `/api/users/me/body-profile` | 신체정보 등록·수정, 변경된 경우 PENDING 입력 요청 생성 |
| GET | `/api/users/me/body-profile` | 현재 신체정보·버전·요청 ID 조회 |
| DELETE | `/api/users/me/body-profile` | 본인 신체정보와 모든 과거 입력 스냅샷 삭제 |
| GET | `/api/users/me/avatar` | 최신 요청 상태 및 피부색 조회 |
| PATCH | `/api/users/me/avatar/appearance` | 피부색 저장·수정 |
| GET | `/api/users/me/avatar/generations/{id}/input.json` | 본인 요청의 입력 JSON 다운로드 |

신체정보 PUT 예시(설명용 가상 수치):

```json
{
  "gender": "MALE",
  "heightMm": 1750,
  "weightKg": 70,
  "chestCircumferenceMm": 950,
  "waistCircumferenceMm": 800,
  "hipCircumferenceMm": 980,
  "thighCircumferenceMm": 550,
  "inseamMm": 800
}
```

- 길이 항목은 필수 양의 정수 `mm`, 무게는 필수 양수 `kg`(정수 최대5자리, 소수 최대3자리)다. 길이는 기술적 저장 한도로 최대9자리만 허용한다.
- **기술적 형식 검증이지 실제 모델링 가능 범위나 체형 비율 검증이 아니다.** 모델러의 최소·최대 지원 치수, 비율 및 허용 오차는 생성기 계약 확정 후 추가해야 한다.
- `gender`는 선택 영문 코드(최대32자, 영문 시작, 영문/숫자/밑줄/하이픈)이며 대문자로 정규화한다. `MALE`은 예시일 뿐 지원 목록을 확정한 Enum이 아니다. 입력이 없으면 소셜 계정에서 추론하거나 기본 성별을 채우지 않는다. 모델러와의 코드 매핑은 별도 합의가 필요하다.
- 저장 응답은 `bodyVersion`, `gender`, `measurements`, `generationId`, `generationStatus`, `updatedAt`을 포함한다. 동일한 정규화 입력 재전송은 기존 버전/요청을 재사용한다. 실제 변경은 버전을 증가시키고 새 UUID 요청을 만든다.
- 사용자 행 잠금으로 동시 저장·삭제·탈퇴를 직렬화한다. 이전 요청의 신체정보는 불변 스냅샷으로 보존한다. 신체정보를 명시적으로 삭제하면 개인정보 보호를 위해 과거 스냅샷도 삭제한다. 이후 재등록의 버전은1부터 시작하지만 요청 UUID는 새로 생성하므로 이전 요청 ID를 재사용하지 않는다.
- 피부색 PATCH 본문은 `{"skinColorHex":"#00FF00"}` 형태다. 임의 RGB 색을 허용하고 대문자 `#RRGGBB`로 저장한다. 동일 색상 재전송은 appearanceVersion을 증가시키지 않으며 색상 변경으로 체형 요청을 다시 만들지 않는다. 지정 전에는 색상은 null, appearanceVersion은0이다.
- 신체정보 삭제 시 피부색 설정은 유지하고, 회원탈퇴 시에는 신체정보·과거 스냅샷·피부색 모두 삭제한다.

입력 JSON 구조:

```json
{
  "schemaVersion": 1,
  "generationId": "요청별 UUID",
  "bodyVersion": 1,
  "lengthUnit": "mm",
  "weightUnit": "kg",
  "gender": "MALE",
  "measurements": {
    "heightMm": 1750,
    "weightKg": 70,
    "chestCircumferenceMm": 950,
    "waistCircumferenceMm": 800,
    "hipCircumferenceMm": 980,
    "thighCircumferenceMm": 550,
    "inseamMm": 800
  }
}
```

JSON은 DB의 요청별 입력 스냅샷에서 응답 시 직렬화하며 서버 디스크에 별도 파일로 남기지 않는다. 사용자 ID/토큰은 포함하지 않는다. 피부색은 체형 입력과 별도인 appearance API로 조회한다. 이 JSON을 MakeHuman이 그대로 읽는 것은 아니며, 향후 생성 담당이 `schemaVersion`에 맞는 변환기를 구현해야 한다.

### 향후 모델 생성 결과 등록 계약 — 아직 쓰기 API 미구현

현재 생성기·결과 수신 엔드포인트는 연결하지 않았으므로 신규 요청은 **PENDING**을 유지한다. 모델 파일을 만들거나 생성 완료·실패·진행 중으로 자동 전환하지 않는다.

향후 신뢰된 생성 담당과 아래 항목 및 인증·저장소 정책을 합의한 후 결과 수신 API를 추가한다.

- 요청의 `generationId`, 원본 `bodyVersion`, 입력 `schemaVersion`
- 처리 상태 `PROCESSING` / `COMPLETED` / `FAILED`와 허용 전이·재시도·중복 결과 정책
- 성공 결과: 신뢰된 저장소의 에셋 키, 모델 버전, `GLB`/`GLTF` 형식, 양의 파일 크기
- 실패 결과: 개인정보나 내부 경로를 담지 않는 안전한 실패 코드
- 결과 전달 주체의 별도 인증, 오래된 결과가 최신 모델을 덮어쓰지 않는 버전 확인, 실제 파일 검증 및 삭제 정책

DB에는 향후 결과 메타데이터를 위한 nullable 필드를 두되, 결과 정보가 없는 `COMPLETED`와 실패 코드 없는 `FAILED`는 제약조건으로 거부한다. 임의 외부 URL을 다운로드하거나 사용자 Access Token만으로 생성 완료를 등록하는 API는 제공하지 않는다. 실제 에셋이 없는 현재 단계에는 외부 파일 삭제 작업도 만들지 않는다.

```powershell
docker compose up -d postgres
.\gradlew.bat bootRun
```

## 회원탈퇴

- `DELETE /api/users/me`: 본인의 Bearer Access Token과 허용된 정확한 `Origin`이 필요하다. 다른 사용자 ID는 입력받지 않는다.
- 성공 시 200과 `{ "withdrawn": true, "googleUnlinkRequired": true, "kakaoUnlinkPending": false }` 형태를 반환한다. 연결된 제공자에 따라 두 플래그는 달라진다.
- 사용자를 `WITHDRAWN`으로 변경하고 프로필/사진/소셜 연결을 삭제한다. 모든 Refresh Token을 무효화하고 HttpOnly 쿠키를 삭제한다. 기존 Access Token도 사용자 상태 검증으로 거부한다.
- 외부 해제 작업은 같은 DB 트랜잭션으로 `social_unlink_task`에 기록하며 외부 장애가 내부 탈퇴를 롤백하지 않는다. 새 Flyway `V2610202100`이 필요하다. 현재 구현된 개인정보 도메인은 인증/프로필뿐이며 향후 신체정보/옷장/착장 저장소 추가 시 삭제 범위도 확장해야 한다.
- Google은 탈퇴 성공 응답의 본인 `googleSubjects`를 브라우저 메모리에서만 사용해 공식 SDK `google.accounts.id.revoke`를 호출한다. 완료 후 메모리에서 식별값을 제거하고, 실패/시간 초과 시에만 [Google 계정 연결 설정](https://myaccount.google.com/connections) 수동 해제를 안내한다. Google 작업 테이블에는 제공자 식별값을 보존하지 않는다. 동일 Google 프로젝트에 부여한 권한도 철회될 수 있음을 확인창에서 안내한다. [Google 공식 안내](https://developers.google.com/identity/gsi/web/guides/revoke).
- `POST /api/auth/web/withdrawal/google-complete`는 브라우저 SDK 성공 결과의 완료 기록용이다. 유효한 서명·만료 범위의 기존 본인 Access Token과 정확한 Origin을 요구하고, 사용자 상태가 WITHDRAWN일 때만 Google 작업을 COMPLETED로 변경한다. 보호 API 접근이나 세션 재발급은 허용하지 않는다. 이 상태는 클라이언트가 확인한 SDK 결과 기록이며 Google 서버에서 독립 검증한 증명은 아니다.
- Kakao 자동 해제는 로컬 `.env`의 **서버 전용** `KAKAO_ADMIN_KEY`와 해당 키의 Unlink API 권한이 필요하다. 로그인 REST API 키와 다른 키이며 Flutter에 넣지 않는다. [Kakao 공식 API](https://developers.kakao.com/docs/ko/kakaologin/rest-api).
- 키 미설정/권한 오류는 `CONFIG_REQUIRED`로 기록하고 15분 간격 재확인한다. 외부 통신 실패는 지수 간격으로 최대 10회 재시도 후 `USER_ACTION_REQUIRED`로 남긴다. 성공 시 작업의 제공자 식별값을 삭제한다. 미완료 Kakao 작업이 재가입한 계정 연결을 나중에 끊지 않도록 처리 완료 전 같은 계정 재가입을 차단한다.
- 제공자 식별값은 미완료 Kakao 해제 작업에만 목적 한정으로 남는다. 운영자는 `USER_ACTION_REQUIRED`/`CONFIG_REQUIRED` 상태를 확인하고 처리해야 하며, 운영 전 보관 기간/수동 종료 절차를 별도로 확정해야 한다.

## 세션과 로그

- Refresh 회전은 최초 로그인부터 최대 48시간을 연장하지 않는다. 오래된 Refresh 요청은 204를 반환하되 새 쿠키를 지우지 않는다. 로그아웃/탈퇴만 명시적으로 쿠키를 삭제한다.
- 웹 클라이언트는 Web Locks로 탭 간 회전을 직렬화하고 BroadcastChannel로 종료 상태를 전파한다.
- 외부 SDK의 잘못된 쿠키 내용을 Tomcat이 INFO로 평문 기록하지 않도록 해당 쿠키 파서만 WARN으로 설정한다. 애플리케이션/보안 오류 로그를 전체 비활성화하지 않는다.
- `spring.mvc.log-resolved-exception=false`로 DevTools의 처리 완료 예외 중복 로그에 신체정보의 rejected value가 포함되는 것을 막는다. 검증 실패는 오류 코드·필드 개수, 아바타 요청 거부는 안전한 코드·상태만 애플리케이션 INFO 로그에 남긴다. 처리되지 않은 예외/서버 오류 로깅은 유지한다.

```powershell
.\gradlew.bat test
.\gradlew.bat build
```
