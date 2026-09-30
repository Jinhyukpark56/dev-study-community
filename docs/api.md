# 인증 및 게시글 API

## Session 인증 API — 2026-09-25 구현

인증 요청은 Spring Security의 표준 form login과 HTTP Session을 사용합니다. 로그인 body는 JSON이 아니라 `application/x-www-form-urlencoded`입니다. unsafe HTTP 요청에는 `/auth/csrf`에서 받은 CSRF token과 같은 Session cookie를 함께 전달합니다.
로그인과 로그아웃 성공 시 Spring Security가 기존 CSRF token을 제거하므로, 클라이언트는 각 성공 뒤 `/auth/csrf`를 다시 호출해 다음 unsafe 요청에 사용할 token을 받아야 합니다.

| 행동 | 요청 | 입력 | 성공 | 실패 |
| --- | --- | --- | --- | --- |
| CSRF token | `GET /auth/csrf` | 없음 | 200, token 정보 | - |
| 회원가입 | `POST /auth/register` | `email`, `password` | 201 | 잘못된 입력·중복 email 400 |
| 로그인 | `POST /auth/login` | `email`, `password` | 200, Session 생성 | 자격정보 오류 401 |
| 현재 사용자 | `GET /auth/me` | Session cookie | 200, email | 비로그인 401 |
| 로그아웃 | `POST /auth/logout` | Session cookie | 204, Session 무효화 | CSRF 오류 403 |

- email은 앞뒤 공백을 제거하고 소문자로 저장하며 최대 254자, `@` 한 개, 공백 없음 규칙을 적용합니다.
- password는 최소 15자, BCrypt가 처리할 수 있는 최대 72 UTF-8 byte이며 앞뒤 공백을 임의로 제거하지 않습니다.
- password 원문은 저장하지 않고 BCrypt 기반 `PasswordEncoder` 결과만 저장합니다.
- 존재하지 않는 email과 잘못된 password는 모두 같은 401로 응답합니다.

## 게시글 API — 2026-09-30 구현·코드 리뷰 완료

게시글 요청의 현재 계약도 `application/x-www-form-urlencoded`를 사용합니다. 조회는 공개하고, 작성·수정·삭제는 로그인한 Session과 유효한 CSRF token이 필요합니다.

| 행동 | 요청 | 현재 입력 | 성공 | 주요 실패 | 권한 |
| --- | --- | --- | --- | --- | --- |
| 목록 조회 | `GET /posts` | 없음 | 200, 게시글 배열 | - | 누구나 |
| 상세 조회 | `GET /posts/{id}` | 없음 | 200, 게시글 | 대상 없음 404 | 누구나 |
| 작성 | `POST /posts` | `title`, `content` | 201, `Location` header | 입력 오류 400, 비로그인 401, CSRF 오류 403 | 로그인 사용자 |
| 수정 | `PATCH /posts/{id}` | `title`, `content` | 200, 본문 없음 | 입력 오류 400, 비로그인 401, 타인 글 403, 대상 없음 404 | 작성자 |
| 삭제 | `DELETE /posts/{id}` | 없음 | 204, 본문 없음 | 비로그인 401, 타인 글 403, 대상 없음 404 | 작성자 |

조회 응답은 Entity를 직접 노출하지 않고 다음 필드만 반환합니다.

```json
{
  "id": 1,
  "title": "Java",
  "content": "Authorization 학습 기록",
  "authorId": 7
}
```

### 현재 구현 규칙

- 작성자는 클라이언트 입력으로 받지 않습니다. 서버가 `Authentication`의 email로 현재 `User`를 조회해 새 `Post`에 연결합니다. 요청에 임의의 `authorId`를 추가해도 작성자로 사용하지 않습니다.
- 제목과 본문은 null 또는 공백만인 값을 허용하지 않습니다. 제목은 최대 100자, 본문은 최대 1000자이며 `String.length()` 기준입니다.
- 현재 수정 요청은 `title`과 `content`를 모두 전달해야 합니다. 둘 중 하나라도 잘못되면 두 값 모두 바꾸지 않습니다.
- 게시글의 ID와 작성자는 수정할 수 없습니다. `Post`에는 author 변경 메서드가 없습니다.
- 수정 트랜잭션은 대상 조회 → 작성자 ID 비교 → 전체 입력 검증 → Entity 변경 순서입니다. 성공한 변경은 Dirty Checking으로 반영합니다.
- 삭제 트랜잭션은 대상 조회 → 작성자 ID 비교 → 삭제 순서입니다.
- 유효한 CSRF token을 보냈지만 로그인 Session이 없는 쓰기 요청은 401입니다. 로그인했더라도 다른 작성자의 글을 수정·삭제하면 403입니다. CSRF token이 없거나 잘못되면 요청은 작성자 검사 전에 Spring Security에서 403으로 차단됩니다.
- 인증된 사용자가 존재하지 않는 게시글을 수정·삭제하면 404입니다.

작성자 비교는 `User` 객체 참조가 아니라 DB에서 생성된 ID로 수행합니다. `Post`에서 `User`로 이어지는 필수 단방향 `ManyToOne` 관계를 사용하며, DB에는 `post.author_id` 외래 키가 저장됩니다.

## 남은 v0.1 API 초안

다음 항목은 확정된 v0.1 제품 범위를 보존한 후속 작업이며, 위의 현재 게시글 API 계약에는 아직 포함되지 않습니다.

- 질문/학습 기록 유형, 작성·수정 시각, 태그 선택
- 최신순 정렬, `page=0`, `size=20` 기본값과 태그 필터
- 생략된 필드를 유지하는 부분 수정 계약
- 댓글 CRUD와 게시글 삭제 시 댓글·글-태그 연결 정리
- 실제 배포 환경에 맞춘 오류 응답 body

화면에서 버튼을 숨기더라도 서버의 Authentication과 작성자 Authorization 검사는 계속 적용합니다. 게시글 삭제와 연결 데이터 처리는 댓글·태그 구현 단계에서 하나의 트랜잭션으로 구체화합니다.

## 상태 코드 기준

| 상태 코드 | 현재 의미 | 예시 |
| --- | --- | --- |
| 400 | 요청 입력이 잘못됨 | 빈 제목, 제목 100자 초과, 본문 1000자 초과, 필수 parameter 누락 |
| 401 | 로그인 필요 | 유효한 CSRF token과 함께 비로그인 사용자가 게시글 쓰기 요청 |
| 403 | 작업 권한 없음 또는 CSRF 방어 | 다른 사용자의 글 수정·삭제, CSRF token 누락·오류 |
| 404 | 대상 없음 | 존재하지 않거나 삭제된 게시글 상세·수정·삭제 |
| 500 | 예상하지 못한 서버 오류 | 내부 실패. 응답에 내부 예외나 비밀정보를 노출하지 않음 |

현재 구현과 40개 전체 테스트는 성공했습니다. ChatGPT 실제 코드 리뷰와 사용자 이해 확인을 마쳤으며, 현재 학습 범위에서 즉시 수정해야 할 핵심 Authorization 문제는 발견되지 않았습니다.
