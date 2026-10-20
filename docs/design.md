# v0.1 설계

상태: User·Session 인증·Post CRUD·게시글 작성자 인가는 이전 단계에서 구현과 리뷰를 마쳤습니다. 댓글·태그·페이지네이션·최소 UI·배포 설정은 2026-10-20 현재 구현을 마치고 통합 코드 리뷰를 기다리고 있습니다. 외부 배포와 PostgreSQL runtime 검증은 아직 수행하지 않았습니다.

## 데이터 관계

| 데이터 | 주요 항목 | 관계와 생명주기 | 현재 상태 |
| --- | --- | --- | --- |
| User | `id`, `email`, password hash | Post·Comment의 작성자. 글·댓글 삭제가 User로 전파되지 않음 | 구현·기존 리뷰 완료 |
| Post | `id`, `title`, `content`, `author`, `type`, `createdAt`, `updatedAt` | User 한 명에 필수 다대일, author 변경 불가 | 구현·현재 리뷰 대기 |
| Comment | `id`, `content`, `post`, `author`, `createdAt`, `updatedAt` | Post 한 개와 User 한 명에 각각 필수 다대일. cascade remove 없음 | 구현·현재 리뷰 대기 |
| Tag | `id`, 고유 `name` | 여러 Post가 같은 Tag를 재사용 | 구현·현재 리뷰 대기 |
| `post_tags` | `post_id`, `tag_id` | Post–Tag 다대다 연결. 두 값 조합은 unique | 구현·현재 리뷰 대기 |

관계의 방향은 필요한 곳에서만 둡니다. User에 `posts`나 `comments` 컬렉션을 추가하지 않았고, Tag에도 Posts 역방향 컬렉션을 두지 않았습니다. 현재 기능에서 사용하지 않는 양방향 상태 동기화를 피하기 위한 선택입니다.

## 작성자와 권한

- Post와 Comment의 작성자는 클라이언트가 보낸 ID로 정하지 않습니다. Controller가 현재 `Authentication`의 email로 User를 조회해 Entity에 연결합니다.
- 수정·삭제는 저장된 작성자 PK와 현재 User PK를 비교합니다. JPA Entity가 같은 Java 객체인지에 의존하지 않습니다.
- 비로그인 쓰기 요청은 Security Filter가 401로 처리하고, 다른 로그인 사용자의 수정·삭제는 Service 결과를 403으로 변환합니다.
- 존재하지 않는 Post·Comment 또는 URL의 `postId`와 Comment의 실제 Post가 다른 경우 404입니다.
- CSRF token이 없거나 잘못된 unsafe 요청은 작성자 확인 전에 403으로 차단됩니다.
- HTTP 응답은 `PostResponse`, `CommentResponse`, 현재 사용자 map으로 제한하며 password hash를 노출하지 않습니다.

## Post 설계

### 값과 시각

- 유형은 `QUESTION`, `STUDY_LOG` 두 값이며 새 글의 기본값은 `STUDY_LOG`입니다.
- 생성 시 `@PrePersist`가 `createdAt`과 `updatedAt`에 같은 `Instant`를 기록합니다.
- 수정 시 Dirty Checking과 `@PreUpdate`로 `updatedAt`을 갱신합니다.
- 기존 파일형 H2 DB에 새 열을 `ddl-auto=update`로 추가할 수 있도록 Post의 `post_type`, `created_at`, `updated_at` DB 열은 현재 nullable입니다.
- 코드의 `getType()`은 이전 행의 null type을 `STUDY_LOG`로 읽지만, 시각은 자동으로 backfill하지 않습니다. 장기 보존 DB에는 배포 전 migration과 backfill이 필요합니다.

### 태그

- 태그 이름은 trim → 연속 공백 한 칸 → 소문자 변환 순서로 정규화합니다.
- 빈 이름은 연결 대상에서 제외하고, 이름은 최대 50자, 게시글당 서로 다른 이름은 최대 3개입니다.
- `tags.name` unique 제약과 `post_tags(post_id, tag_id)` unique 제약으로 저장소 수준 중복도 막습니다.
- 글 작성·수정 시 기존 Tag를 이름으로 조회해 재사용하고, 없을 때만 새 Tag를 만듭니다.
- 수정에서 tags를 생략하면 기존 관계를 유지하고, 빈 목록을 명시하면 연결을 모두 제거합니다.
- Post–Tag에는 cascade remove를 사용하지 않습니다. 게시글 삭제나 태그 교체가 다른 글에서 사용하는 Tag를 삭제하지 않게 합니다.

같은 이름의 새 태그를 여러 요청이 정확히 동시에 생성하면 두 요청 모두 조회 시점에 없다고 판단한 뒤 unique 제약에서 하나가 실패할 수 있습니다. 현재 구현은 중복 저장 자체는 막지만 이 충돌을 정상 응답으로 재시도하지 않습니다. 공개 트래픽 전에 충돌 처리 정책을 추가 검토합니다.

### 페이지 조회

`GET /posts`는 Spring Data `Pageable`을 사용하고 다음 순서를 고정합니다.

1. `createdAt DESC NULLS LAST`
2. `id DESC`

페이지 번호는 0 이상, 크기는 1~50입니다. 태그 filter는 정규화한 정확한 이름으로 조회합니다. `PostPageResponse`는 `content/page/size/totalPages/totalElements`를 반환합니다.

PostRepository는 페이지 조회 시 author를 EntityGraph로 함께 읽습니다. 상세 조회는 author와 tags를 함께 읽고, 목록의 tags는 `@BatchSize(50)`와 transaction 안 초기화로 처리합니다. 운영 데이터 규모가 커지면 실제 SQL 수와 응답 시간을 측정한 뒤 조회 projection이나 별도 쿼리를 검토합니다.

## Comment 설계

- Comment는 Post와 User를 각각 필수 외래 키로 참조합니다.
- 생성·수정 시각은 `Instant`로 기록하며 DB 열도 null을 허용하지 않습니다.
- 목록은 `createdAt ASC`, 같은 시각에는 `id ASC`로 정렬합니다.
- 내용은 공백만인 값을 거부하고 최대 1000자입니다.
- Comment에서 Post·User로 향하는 관계에 cascade를 두지 않습니다. 댓글 하나를 삭제해도 글과 사용자는 유지됩니다.

게시글 삭제는 하나의 `@Transactional` 메서드에서 다음 순서로 처리합니다.

1. Post 존재 여부와 작성자 확인
2. Comment bulk delete
3. Post 삭제와 `post_tags` 연결 정리
4. 어느 단계든 실패하면 전체 rollback

테스트는 정상 삭제 시 댓글과 게시글만 없어지고 User는 남는지, Post 삭제 실패를 유도했을 때 먼저 삭제한 댓글이 rollback되는지 확인합니다.

## API와 최소 UI

- 게시글·댓글 GET은 비로그인 사용자도 허용합니다.
- 게시글·댓글 쓰기와 `/auth/me`는 Authentication이 필요합니다.
- 인증 진입점, 정적 파일, `/health`, `/ready`는 공개합니다.
- 화면과 API를 같은 Spring Boot origin에서 제공하므로 현재 CORS 설정은 필요하지 않습니다.
- 화면은 `/auth/me`의 `id`로 현재 작성자와 비교해 수정·삭제 버튼을 표시합니다. 이 처리는 UI 편의용이며 서버 검사를 대신하지 않습니다.
- JavaScript는 사용자 입력을 `textContent`와 DOM API로 표시해 입력 문자열을 HTML로 실행하지 않습니다.
- `GET /posts` 응답은 기존 최상위 배열에서 페이지 객체로 바뀌었습니다. 최소 UI와 테스트는 새 계약의 `content`를 사용합니다.

## DB와 배포 방향

| 환경 | DB | schema 정책 | 검증 상태 |
| --- | --- | --- | --- |
| 개발 | 파일형 H2 | `ddl-auto=update` | 기존 개발 흐름 사용 |
| 테스트 | 메모리 H2 | `create-drop` | 자동 테스트에서 사용 |
| v0.1 preview 후보 | 외부 PostgreSQL | 임시 `ddl-auto=update` | 설정만 준비, runtime 미검증 |
| 장기 운영 | PostgreSQL | versioned migration + `validate` | 아직 미구현 |

운영 profile은 Open Session in View를 끄고, DB 연결정보·pool·cookie·Session 시간을 환경변수로 받습니다. `/health`는 process liveness, `/ready`는 DB `select 1` readiness입니다. 한 process의 memory Session을 사용하므로 재시작 시 로그인이 풀리고 여러 instance 사이에서 Session을 공유하지 못합니다.

## 현재 위험과 후속 결정

1. 기존 H2/PostgreSQL 데이터가 있다면 Post type·시각 열을 migration으로 추가하고 backfill한 뒤 not-null 제약을 검토해야 합니다.
2. PostgreSQL에서 schema 생성, 쿼리, transaction rollback을 아직 직접 확인하지 않았습니다.
3. Dockerfile은 준비했지만 현재 작업 환경에서 Docker daemon이 실행되지 않아 image build·runtime 검증이 남았습니다.
4. 같은 새 태그를 동시에 만드는 요청의 unique 충돌을 사용자에게 안정적인 응답으로 바꾸는 처리가 남았습니다.
5. 회원가입·로그인 endpoint에 애플리케이션 수준 rate limiting이 없습니다. 공개 범위를 넓히기 전에 플랫폼 또는 애플리케이션 요청 제한을 정해야 합니다.
6. memory Session을 유지할 한 instance 배포인지, shared Session store를 도입할지 실제 운영 요구에 따라 결정해야 합니다.

현재 구현은 최소 v0.1 기능 검증 범위입니다. 측정된 문제나 실제 운영 요구가 생기기 전에 캐시, 메시지 큐, JWT, 복잡한 권한 계층을 추가하지 않습니다.

## 결정 기록 양식

날짜 / 해결하려는 문제 / 검토한 선택지 / 결정 / 이유 / 단점 / 확인 결과를 남깁니다.
