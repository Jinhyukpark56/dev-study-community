# v0.1 API

상태: 2026-10-20 구현 완료, 통합 코드 리뷰 대기. 현재 브랜치의 계약이며 `main` 병합 전입니다.

## 공통 요청 방식

- 인증은 Spring Security form login과 HTTP Session을 사용합니다.
- 회원가입·로그인·게시글·댓글 쓰기 입력은 `application/x-www-form-urlencoded`입니다.
- `POST`, `PATCH`, `DELETE` 요청에는 `/auth/csrf`에서 받은 token과 같은 Session cookie를 함께 보냅니다.
- 로그인과 로그아웃이 성공하면 이전 CSRF token이 제거될 수 있으므로 화면은 `/auth/csrf`를 다시 호출합니다.
- 조회 Entity를 그대로 직렬화하지 않고 응답 객체를 사용합니다. User의 password hash는 응답에 포함하지 않습니다.

## 상태 확인

| 목적 | 요청 | 성공 | 의미 |
| --- | --- | --- | --- |
| liveness | `GET /health` | `200`, 본문 `ok` | 애플리케이션 process가 HTTP 요청에 응답함 |
| readiness | `GET /ready` | `200`, 본문 `ready` | DB에 `select 1`을 실행할 수 있음 |

`/health`는 DB 상태를 확인하지 않습니다. `/ready`의 DB 조회가 실패하면 Spring의 일반 오류 처리로 실패 응답을 반환합니다.

## 인증 API

| 행동 | 요청 | 입력 | 성공 | 주요 실패 |
| --- | --- | --- | --- | --- |
| CSRF token | `GET /auth/csrf` | 없음 | 200, token 정보 | - |
| 회원가입 | `POST /auth/register` | `email`, `password` | 201 | 잘못된 입력·중복 email 400, CSRF 오류 403 |
| 로그인 | `POST /auth/login` | `email`, `password` | 200, Session 생성 | 자격정보 오류 401, CSRF 오류 403 |
| 현재 사용자 | `GET /auth/me` | Session cookie | 200, `id`와 `email` | 비로그인 401 |
| 로그아웃 | `POST /auth/logout` | Session cookie | 204, Session 무효화 | CSRF 오류 403 |

`GET /auth/me` 응답:

```json
{
  "id": 7,
  "email": "student@example.com"
}
```

- email은 앞뒤 공백을 제거하고 소문자로 저장합니다. 최대 254자이며 `@` 한 개가 필요하고 공백은 허용하지 않습니다.
- password는 최소 15자, BCrypt가 처리할 수 있는 최대 72 UTF-8 byte입니다. 앞뒤 공백을 임의로 제거하지 않습니다.
- password 원문은 저장하지 않고 BCrypt 기반 `PasswordEncoder` 결과만 저장합니다.
- 존재하지 않는 email과 잘못된 password는 모두 같은 401로 응답합니다.

## 게시글 API

게시글 조회는 공개합니다. 작성·수정·삭제는 로그인 Session과 유효한 CSRF token이 필요합니다.

| 행동 | 요청 | 입력 | 성공 | 주요 실패 | 권한 |
| --- | --- | --- | --- | --- | --- |
| 목록 조회 | `GET /posts` | `page`, `size`, 선택 `tag` | 200, 페이지 객체 | 잘못된 page·size·tag 400 | 누구나 |
| 상세 조회 | `GET /posts/{id}` | 없음 | 200, 게시글 | 대상 없음 404 | 누구나 |
| 작성 | `POST /posts` | `title`, `content`, 선택 `type`, 선택 `tags` | 201, `Location` header | 입력 오류 400, 비로그인 401, CSRF 오류 403 | 로그인 사용자 |
| 수정 | `PATCH /posts/{id}` | `title`, `content`, 선택 `type`, 선택 `tags` | 200, 본문 없음 | 입력 오류 400, 비로그인 401, 타인 글 403, 대상 없음 404 | 작성자 |
| 삭제 | `DELETE /posts/{id}` | 없음 | 204, 본문 없음 | 비로그인 401, 타인 글 403, 대상 없음 404 | 작성자 |

### 목록 조회

기본값은 `page=0`, `size=20`입니다. `page`는 0 이상, `size`는 1~50이어야 합니다. 정렬은 `createdAt DESC`, 같은 시각에는 `id DESC`이며 기존 데이터의 null 시각은 뒤로 보냅니다. `tag`를 보내면 정규화한 이름이 연결된 게시글만 조회합니다.

```http
GET /posts?page=0&size=20&tag=spring%20boot
```

```json
{
  "content": [
    {
      "id": 21,
      "title": "Session 인증 정리",
      "content": "SecurityContext와 Session의 관계",
      "authorId": 7,
      "type": "STUDY_LOG",
      "tags": ["spring boot", "security"],
      "createdAt": "2026-10-20T03:00:00Z",
      "updatedAt": "2026-10-20T03:05:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalPages": 1,
  "totalElements": 1
}
```

`GET /posts`의 응답은 이전의 게시글 배열에서 위 페이지 객체로 바뀌었습니다. 기존 클라이언트는 최상위 배열 대신 `content`를 읽어야 합니다.

### 작성과 수정 입력

- `type`: `QUESTION` 또는 `STUDY_LOG`. 작성할 때 생략하면 `STUDY_LOG`입니다.
- `tags`: `tags=java,spring`처럼 쉼표로 구분하거나 같은 parameter를 반복할 수 있습니다.
- 태그는 앞뒤 공백 제거, 연속 공백 한 칸 축소, 소문자 변환 후 중복을 제거합니다.
- 서로 다른 태그는 게시글당 최대 3개, 각 이름은 최대 50자입니다.
- 이미 존재하는 이름은 같은 Tag를 재사용하고, 없는 이름은 새 Tag로 저장합니다.
- 수정에서 `type`이나 `tags`를 생략하면 기존 값을 유지합니다. 빈 `tags` 값을 명시하면 태그 연결을 모두 제거합니다.
- 제목과 본문은 null 또는 공백만인 값을 허용하지 않습니다. 제목은 최대 100자, 본문은 최대 1000자이며 `String.length()` 기준입니다.
- 수정은 현재 계약상 `title`과 `content`를 모두 전달해야 합니다. 둘 중 하나라도 잘못되면 게시글 값을 바꾸지 않습니다.
- 작성자는 요청으로 받지 않습니다. 서버가 현재 `Authentication`의 email로 찾은 User를 사용합니다.

상세 응답은 목록의 `content` 안 게시글과 같은 필드를 반환합니다. 이번 변경 전부터 존재한 개발 DB 행은 migration과 backfill 전까지 `createdAt` 또는 `updatedAt`이 null일 수 있습니다.

### 게시글 삭제

작성자 확인 뒤 같은 트랜잭션에서 댓글을 먼저 삭제하고 게시글을 삭제합니다. JPA가 `post_tags`의 해당 연결을 제거하며 공유 Tag Entity는 삭제하지 않습니다. 댓글 삭제 뒤 게시글 삭제가 실패하는 경우 전체 트랜잭션이 rollback되는 경로를 테스트합니다.

## 댓글 API

댓글 조회는 공개합니다. 쓰기 요청에는 로그인 Session과 유효한 CSRF token이 필요합니다.

| 행동 | 요청 | 입력 | 성공 | 주요 실패 | 권한 |
| --- | --- | --- | --- | --- | --- |
| 목록 조회 | `GET /posts/{postId}/comments` | 없음 | 200, 댓글 배열 | 게시글 없음 404 | 누구나 |
| 상세 조회 | `GET /posts/{postId}/comments/{commentId}` | 없음 | 200, 댓글 | 게시글 경로와 댓글 불일치·대상 없음 404 | 누구나 |
| 작성 | `POST /posts/{postId}/comments` | `content` | 201, `Location` header | 입력 오류 400, 비로그인 401, 게시글 없음 404, CSRF 오류 403 | 로그인 사용자 |
| 수정 | `PATCH /posts/{postId}/comments/{commentId}` | `content` | 200, 본문 없음 | 입력 오류 400, 비로그인 401, 타인 댓글 403, 대상 없음 404 | 작성자 |
| 삭제 | `DELETE /posts/{postId}/comments/{commentId}` | 없음 | 204, 본문 없음 | 비로그인 401, 타인 댓글 403, 대상 없음 404 | 작성자 |

댓글 응답:

```json
{
  "id": 31,
  "content": "이 부분은 예제로 다시 확인해 보세요.",
  "postId": 21,
  "authorId": 8,
  "createdAt": "2026-10-20T03:10:00Z",
  "updatedAt": "2026-10-20T03:10:00Z"
}
```

- 댓글 내용은 null·공백만인 값을 허용하지 않고 최대 1000자입니다.
- 목록은 생성 시각 오름차순, 같은 시각에는 ID 오름차순입니다. 현재 댓글 목록에는 별도 페이지네이션을 적용하지 않습니다.
- URL의 `postId`와 실제 Comment의 게시글이 다르면 다른 게시글의 댓글을 노출하거나 변경하지 않고 404를 반환합니다.
- 작성자는 현재 로그인 User로 정하며, 수정·삭제 시 Comment의 작성자 PK와 현재 사용자 PK를 비교합니다.
- Comment에서 Post와 User로 향하는 관계에는 cascade remove를 두지 않아 댓글 삭제가 게시글이나 사용자를 삭제하지 않습니다.

## 상태 코드 기준

| 상태 코드 | 의미 | 예시 |
| --- | --- | --- |
| 200 | 조회 또는 수정 성공 | 목록·상세 조회, 게시글·댓글 수정 |
| 201 | 생성 성공 | 회원가입, 게시글·댓글 작성 |
| 204 | 본문 없는 성공 | 로그아웃, 게시글·댓글 삭제 |
| 400 | 요청 입력 오류 | 빈 내용, 길이 초과, 잘못된 type·page·size·tag |
| 401 | 로그인 필요 또는 로그인 실패 | 비로그인 쓰기 요청, 잘못된 로그인 정보 |
| 403 | 작성자 권한 없음 또는 CSRF 방어 | 다른 사용자의 수정·삭제, CSRF token 누락·오류 |
| 404 | 경로에 해당하는 대상 없음 | 없는 게시글·댓글, 다른 게시글에 속한 댓글 경로 |
| 500 | 예상하지 못한 서버 오류 | 내부 실패. 운영 응답에는 예외·stack trace·secret을 포함하지 않음 |

현재 구현과 API 계약은 통합 코드 리뷰 전입니다. 외부 PostgreSQL과 배포 환경에서의 동작은 아직 검증하지 않았습니다.
