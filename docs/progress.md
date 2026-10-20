# 진행 기록

## 현재 상태 — 2026-10-20

- `origin/main`의 `5d63aa8`을 기준으로 `feature/v0.1-release-completion`에서 v0.1 잔여 기능을 구현했다. 현재 통합 코드 리뷰 대기 상태이며 `main`에는 병합하지 않았다.
- 댓글: Comment Entity·Repository·Service·Controller와 응답 객체를 추가했다. 공개 조회, 인증 사용자 작성, 작성자 PK 기반 수정·삭제, URL의 `postId`와 실제 댓글 소속 불일치 404, 내용 Validation을 구현했다.
- 게시글 삭제: 같은 transaction에서 연관 댓글을 bulk delete한 뒤 Post를 삭제한다. 성공 시 User와 공유 Tag는 유지하고, Post 삭제를 실패시킨 테스트에서는 먼저 삭제한 댓글도 rollback되는 것을 확인했다.
- 태그·유형·시각: Post에 `QUESTION`/`STUDY_LOG`, `Instant` 생성·수정 시각, Tag 다대다 관계를 추가했다. 태그 이름은 공백과 대소문자를 정규화하고 기존 태그를 재사용하며 글당 최대 3개로 제한한다. `post_tags` 조합과 Tag 이름에는 unique 제약을 둔다.
- 페이지 조회: `GET /posts`를 `content/page/size/totalPages/totalElements` 응답으로 바꾸고 `page=0`, `size=20`, `tag` filter를 추가했다. 정렬은 생성 시각 내림차순(null은 뒤), ID 내림차순이며 page는 0 이상, size는 1~50이다.
- 최소 UI: 같은 origin의 정적 HTML·CSS·JavaScript로 회원가입, 로그인·로그아웃, 게시글·댓글 CRUD, 태그별 조회와 페이지 이동을 연결했다. 로그인 User ID와 작성자 ID가 일치할 때만 편집 버튼을 표시하고, 서버 권한 검사는 그대로 유지한다.
- 상태 확인: `/health`는 process liveness로 유지하고, DB에 `select 1`을 실행하는 `/ready`를 readiness로 추가했다. `/auth/me`는 현재 User의 `id`와 `email`을 반환한다.
- 배포 준비: PostgreSQL driver와 env 기반 prod profile, `.env.example`, Dockerfile, `.dockerignore`, LF 고정 설정과 배포 절차를 추가했다. prod에서는 Open Session in View를 끄고 Secure·HttpOnly·SameSite=Lax Session cookie, 오류 상세 숨김과 graceful shutdown을 설정한다.
- 자동 검증: 최종 `clean test bootJar --no-daemon`에서 10개 test suite, 60개 테스트가 모두 성공했다. 실패·오류·건너뜀은 0개이며 boot JAR 생성도 완료했다. 2026-09-30의 40개 성공은 이전 Authorization 마일스톤 수치다.
- 브라우저 확인: 격리한 메모리 H2와 port 18080에서 화면·asset 200, 회원가입·로그인·`/auth/me`, type·쉼표 태그를 포함한 글 작성, 태그 filter, 댓글 작성·수정, 글과 연관 댓글 삭제, 로그아웃 뒤 `/auth/me` 401을 확인했다. 390×844 화면에서도 가로 overflow가 없었고 확인 서버는 종료했다.
- Docker CLI는 있지만 daemon이 실행되지 않아 image build와 container runtime은 검증하지 못했다. 실제 PostgreSQL의 schema 생성·쿼리·transaction과 외부 HTTPS의 Secure cookie는 배포 전 smoke test가 필요하다.
- 코드 감사에서 병합을 막는 결함은 확인되지 않았다. 후속 위험은 동시에 같은 새 태그를 처음 만드는 요청 중 하나가 unique 제약으로 500이 될 수 있는 점, 기존 DB의 `post_type/created_at/updated_at` migration·backfill, 공개 인증 endpoint의 rate limiting, memory Session의 재시작·다중 instance 한계다.
- 이번 기록은 실제 구현과 검증 상태만 나타낸다. 새 코드에 대한 이해도나 학습 완료 여부는 통합 코드 리뷰에서 별도로 확인한다.

## 현재 상태 — 2026-09-30

- Post 작성자 Authorization의 ChatGPT 실제 코드 리뷰와 사용자 이해 확인을 완료했다. `Post → User` 관계와 FK, Authentication 기반 작성자 지정, 작성자 PK 비교, Authorization 선확인, PostResponse, 401·403·404 및 CSRF 403 구분을 실제 코드로 검토했다.
- 사용자는 Authentication email → User Entity 조회 → `Post.author` 연결 → JPA FK 저장 흐름과, 조회 → Authorization → Validation → 변경 → Dirty Checking 순서를 설명했다.
- 최종 `clean test bootJar`에서 전체 40개 성공, 실패·오류·건너뜀 0을 다시 확인했다. 파일형 H2 실제 기동에서 `/health` 200 `ok`, `/posts` 200 `[]`, `POST.AUTHOR_ID NOT NULL → USERS.ID` FK도 재확인했다.
- 다음 기능은 시작하지 않았다. JUnit 별도 학습은 출시 필수 기능 이후로 계속 보류한다.
- 최종 commit·PR·merge 식별자와 Tasks 상태는 Git history와 Notion 학습 기록에서 관리한다.

## 현재 상태 — 2026-09-29

- `feature/post-author-authorization`에서 게시글 작성자 Authorization 구현과 전체 테스트를 완료했고, ChatGPT 실제 코드 리뷰를 기다린다.
- `Post`에서 `User`로 향하는 필수 단방향 `ManyToOne` 관계를 추가했다. DB에는 null을 허용하지 않는 `post.author_id` 외래 키를 저장하며 author 변경 setter와 cascade는 두지 않았다.
- `POST /posts`는 클라이언트의 작성자 입력을 사용하지 않고, 현재 `Authentication`의 email로 조회한 `User`를 작성자로 지정한다.
- `GET /posts`, `GET /posts/{id}`는 비로그인 사용자에게 공개한다. 응답은 `id/title/content/authorId`만 포함해 User Entity의 password hash를 노출하지 않는다.
- `PATCH /posts/{id}`, `DELETE /posts/{id}`는 로그인한 게시글 작성자 본인에게만 허용한다. 비로그인 요청은 401, 다른 로그인 사용자는 403, 없는 게시글은 404로 구분한다. 입력 검증 실패는 400이며 CSRF token 누락·오류는 Security Filter에서 403으로 차단한다.
- 게시글 요청의 현재 입력 형식은 `application/x-www-form-urlencoded`이다. 작성·수정은 `title/content`를 사용하며, 수정은 두 값을 모두 받는다.
- 기존 제목·본문 Validation은 유지했다: null·공백 거부, 제목 최대 100자, 본문 최대 1000자. 저장되지 않은 author가 있는 게시글 등록도 거부한다.
- 수정 트랜잭션은 대상 조회 → 작성자 ID 비교 → 전체 입력 검증 → 값 변경 순서다. 성공 시 Dirty Checking으로 반영하고, 권한·입력 실패 시 Entity를 변경하기 전에 종료한다. 삭제는 대상 조회 → 작성자 ID 비교 → 삭제 순서다.
- 전체 테스트 40개 성공, 실패·오류·건너뜀 0. `PostServiceTests` 16개, `PostAuthorizationTests` 13개, `AuthenticationTests` 9개, context와 health 각 1개가 통과했다.
- 파일형 개발 H2로 실제 서버를 기동해 `/health` 200 `ok`, 공개 `/posts` 200 `[]`를 확인했다. DB metadata에서 `POST.AUTHOR_ID`가 NOT NULL이며 `USERS.ID`를 참조하는 Foreign Key도 확인했고, `bootJar` 생성에 성공했다.
- 당시 Git 상태: 구현과 검증은 완료했지만 코드 리뷰 전이어서 commit·push·PR·merge는 진행하지 않았다.
- 당시 다음 단계: ChatGPT와 `Post` 관계, 현재 사용자 작성자 지정, 401/403/404 분리, 트랜잭션 검증 순서, 통합 테스트를 리뷰한다. 이 리뷰와 이해 확인은 2026-09-30 완료했다.

## 현재 상태 — 2026-09-27

- Spring Security Session Authentication 실제 코드 리뷰와 사용자 이해 확인을 완료했다.
- `User` → `UserRepository` → `UserService` → `SecurityConfig` → `AuthController` → `AuthenticationTests` 순서와 `build.gradle` 의존성을 검토했다.
- `existsByEmail`과 `findByEmail`의 역할, DB unique 제약과 `DataIntegrityViolationException`, email 소문자 정규화 정책, Password hashing과 `matches` 흐름을 확인했다.
- form login Filter → `UserDetailsService` → Password 검증 → `Authentication` → `SecurityContext` → HTTP Session 흐름을 사용자가 직접 설명했다.
- logout의 인증 제거·Session 무효화와 CSRF 403의 의미, 같은 Session을 재사용하는 통합 테스트의 목적을 확인했다.
- 현재 학습 범위에서 즉시 수정해야 할 핵심 Authentication 버그는 확인되지 않았다.
- 전체 테스트를 다시 실행해 26개 성공, 실패·오류·건너뜀 0을 확인했다.
- 당시 다음 학습·구현 단계는 게시글 작성자 Authorization이었다. 2026-09-29 구현·테스트, 2026-09-30 실제 코드 리뷰·이해 확인을 완료했다. JUnit 별도 학습은 출시 필수 기능 이후로 계속 보류한다.

## 현재 상태 — 2026-09-25

- 사용자 확인 학습 완료: Authentication/Authorization 차이, 401/403, Password hashing과 matches, Session/Cookie, Spring Security·SecurityFilterChain·Authentication·SecurityContext·PasswordEncoder 기본 역할.
- JUnit 별도 학습은 출시 필수 기능 이후로 미루고 `feature/session-authentication`에서 인증 구현을 진행했다.
- 인증 구현 당시 `User` Entity에는 생성 ID, 정규화한 email, password hash만 저장하고 Role·JWT·Refresh Token·OAuth·작성자 인가는 추가하지 않았다. 작성자 인가는 이후 2026-09-29 별도 단계에서 구현했다.
- 회원가입은 email/password 검증 → email 중복 확인 → PasswordEncoder hashing → User 저장 순서다. email은 앞뒤 공백 제거와 소문자 정규화를 적용하고 DB unique 제약도 둔다. password 원문은 변형하거나 저장하지 않는다.
- Spring Security의 표준 form login을 `/auth/login`에 연결했다. 성공한 Authentication은 HTTP Session에 보관되며, `/auth/me`를 다음 요청에서 호출해 로그인 상태 유지를 확인했다.
- `/auth/logout`은 Session과 인증 상태를 제거한다. 이후 보호된 `/auth/me` 요청이 401이 되는 것을 확인했다.
- CSRF 보호는 유지한다. `/auth/csrf`에서 token을 얻고 회원가입·로그인·로그아웃 POST에 포함한다.
- 당시 접근 정책: `/health`, 인증 진입점, 게시글·댓글 GET은 공개하고 쓰기 요청에는 Authentication을 요구했다. 작성자 본인 여부 판단은 다음 단계로 남겼고 2026-09-29 게시글에 구현했다.
- 테스트: 전체 26개 성공, 실패·오류·건너뜀 0. 기존 Post/JPA 15개, context 1개, health 1개가 계속 통과했고 인증 통합 테스트 9개를 추가했다.
- Git: 인증 구현은 commit·push·PR·merge 전이다. 실제 코드 리뷰와 사용자 인증 흐름 설명·이해 확인을 기다린다.
- 인증 구현 당시 다음 순서: ChatGPT 실제 코드 리뷰 → 필요한 수정 → 전체 재테스트 → 사용자 승인 후 Git 작업 마무리. 작성자 인가는 이 인증 작업과 분리해 이후 별도 단계에서 구현했다.

## 현재 상태 — 2026-09-12

- 사용자 확인 학습 완료: 메서드 복습, OOP, 캡슐화, static/instance 기본, List/ArrayList, 반복문, 메모리 CRUD, null/isBlank/short-circuit, 기본 Validation, Post/PostService 역할 분리.
- `feature/in-memory-post-crud`에서 Post/PostService와 CRUD·Validation 테스트 구현. 사용자 지시에 따라 검증·변경 검토 후 commit/push/PR/main merge까지 진행한다.
- 기준 커밋: `ca2b4a6`. 로컬 main과 저장된 origin/main이 일치한다. 2026-09-12 git fetch origin 성공으로 원격 최신 main과 일치함을 확인했다.
- 구현: id/title/content만 사용, 제목 100자·내용 1000자 제한, 잘못된 입력 거부, ID 조회·수정·삭제, 중복 ID 거부.
- 기존 requirements.md의 본문 최대 10,000자와 제목 trim 규칙은 v0.1 초안이다. 이번 학습 구현에는 사용자가 지정한 본문 최대 1000자 및 원문 길이 기준을 적용했으며, 기존 제품 범위 초안은 수정하지 않았다.
- `/health` 유지. JPA, Entity, Repository, DB 연결, 게시글 Controller/API는 이번 범위에 포함하지 않는다.
- 테스트: JUnit Platform 직접 실행으로 15개 발견·실행·성공, 실패/건너뜀 0개. PostService 13개, MockMvc GET /health의 HTTP 200 및 ok 1개, 기존 Spring contextLoads 1개.
- 추가 검증: 일반 Gradle Wrapper test도 BUILD SUCCESSFUL. 15개 테스트, 실패·오류 0개를 XML 보고서로 확인했다. 이전 환경의 접근 오류는 현재 실행에서 재현되지 않았고 빌드 설정은 변경하지 않았다.
- 다음: ChatGPT 학습 채팅에서 DB/JPA 기초 학습 후 실제 구현 범위를 결정한다.
- Git 운영: 앞으로는 테스트·변경 검토·병합 가능 여부를 확인한 뒤 별도 검토 대기 없이 커밋·push·PR·merge까지 진행한다. 충돌·테스트 실패·범위 밖 설계가 있으면 해결하거나 필요한 사항을 알린다.
- 역할: ChatGPT 학습 채팅은 개념 설명·문제·복습·이해도 확인·학습 진도 판단을 담당한다. Codex는 저장소 구현·테스트·Git·검토·Notion 기록 및 Tasks 갱신을 담당한다. 배우지 않은 기술은 선행 구현하지 않는다.

## 개발환경 구성 당시 기록

- 프로젝트 방향: 개발 공부 질문·학습 기록 커뮤니티
- 운영 방식: 현재 대화에서 기획·학습·구현, 문서로 결정과 진도 기록, GitHub에서 코드와 리뷰 관리
- 완료: README, 요구사항 v0.1, 설계 초안, 개발 작업 목록, PR 양식, .gitignore 준비
- 완료: 공개 GitHub 저장소 Jinhyukpark56/dev-study-community 생성
- 완료: 로컬 Git 연결, setup/development-environment 작업 브랜치 생성, Java 21·Spring Boot 4.1.1·Gradle 9.7.1 최소 서버 구성
- 친구 devleejb 초대 발송 완료. 수락 여부는 아직 재확인하지 않음
- 검증: Gradle test 및 bootJar 성공, 실행한 JAR의 GET /health가 HTTP 200과 ok 반환. 검증 서버 종료
- 미진행: 게시글·회원·댓글 구현, 환경 구성 변경의 커밋·원격 업로드
- 문서의 세부 입력 제한·기술 후보는 제안이며 사용자가 확정한 결정이 아님
- 현재 진행: 로컬 저장소 연결 및 개발환경 준비 완료. 설정 파일과 최소 서버의 역할을 학습자에게 설명할 차례

## 초기 기획·학습 기록

사용자가 본인이 작성한 글·댓글만 수정·삭제할 수 있다는 원칙을 자신의 말로 설명했습니다. 비로그인 조회 허용도 확인했습니다. 댓글 수정 기능을 첫 버전에 반영했습니다.
사용자가 게시글 삭제 시 본문과 댓글을 모두 삭제하는 정책을 선택했습니다. 삭제된 글의 자리는 남기지 않고 글-태그 연결도 제거합니다. 공유 태그 자체는 유지합니다.
사용자가 질문과 학습 기록을 한 목록에 모아 보여주는 방식을 선택했습니다. 단순한 첫 버전을 원하므로 별도 게시판과 글 유형 필터는 추가하지 않습니다.
사용자가 게시글 기본 항목(글 ID, 작성자, 유형, 제목, 본문, 작성 시각, 수정 시각)에 동의했습니다. 설계 문서에 항목별 목적과 사용자·댓글·태그 연결 예시를 추가했습니다. 수정 시각 초기값 등 세부 구현 규칙은 초안입니다.
사용자가 제목으로 삭제하면 원하지 않는 글이 삭제될 수 있다고 설명해 글 ID의 필요성을 확인했습니다.
게시글 API(GET 목록·상세, POST 작성, PATCH 수정, DELETE 삭제)와 서버의 작성자 권한 검사를 설명했고 사용자가 해당 방향에 동의했습니다. API 개념 자체의 독립적인 이해 확인은 아직 하지 않았습니다.
API 문서에 성공·실패 응답 초안을 추가했습니다. 사용자가 비로그인 시 인증 필요, 로그인한 다른 작성자는 권한 없음으로 거부된다고 설명해 401·403의 차이를 확인했습니다. 세부 UI 선택 질문은 최소화하고 일반적인 기본값으로 진행합니다.
확인 후 첫 기능에 필요한 return과 반환형부터 학습을 이어갑니다.
최신 Java 지원 버전과 Spring Boot 호환성은 실제 개발환경 구성 시점에 공식 자료로 확인합니다.

## 학습 운영

- 설명 → 자신의 말로 설명 → 짧은 문제 → 직접 코딩으로 진행
- 틀리면 완성 답안보다 힌트를 먼저 제공
- 이해가 확인된 내용은 불필요하게 반복하지 않고 다음 기능으로 진행
- 이전 배열·조건문·반복문을 새 기능 안에서 누적 복습
- 새 개념은 설명을 들은 것과 이해가 확인된 것을 구분해서 기록
- 실제 코딩은 문법과 코드 위치까지 정확히 확인
- 학습 단위가 끝나면 진행상황과 다음 복습 문제를 문서로 갱신

학습자의 상세 개인 상황과 기존 상담 원문은 협업 저장소 문서에 포함하지 않습니다.
