# 개발 공부 커뮤니티

개발 공부 중 생긴 질문과 학습 기록을 공유하는 커뮤니티 서비스입니다.
Java와 백엔드 개발을 기능 구현에 연결해 학습하고, 코드 리뷰와 테스트를 통해 개선합니다.

## 현재 상태

Java 21 / Spring Boot 프로젝트에 Spring Data JPA 기반 게시글 CRUD, Spring Security 기반 Session 인증, 게시글 작성자 Authorization을 구현했습니다. 개발 실행은 파일형 H2 DB를 사용합니다. Authorization 구현과 40개 전체 테스트, ChatGPT 실제 코드 리뷰, 사용자 이해 확인을 2026-09-30에 완료했습니다.
저장소: https://github.com/Jinhyukpark56/dev-study-community

### JPA 게시글 구현 (2026-09-19)

- `Post`: `id/title/content/author`를 가진 JPA Entity입니다. ID는 `Long`이며 DB 저장 시 생성되고 외부에서 변경할 수 없습니다.
- `author`는 필수 `@ManyToOne` 관계이며 `post.author_id` 외래 키로 `User`와 연결합니다. `Post`에서 `User`로만 탐색하는 단방향 관계이고, author 변경 메서드와 cascade는 두지 않았습니다.
- `PostRepository`: `JpaRepository<Post, Long>`를 상속해 저장·ID 조회·목록·삭제를 담당합니다.
- `PostService`: Repository를 사용해 등록·ID 조회·목록·수정·삭제, 입력 검증, 작성자 확인을 처리합니다.
- 등록은 성공 여부를 `boolean`으로 반환합니다. 수정·삭제는 `SUCCESS`, `INVALID_INPUT`, `NOT_FOUND`, `FORBIDDEN`으로 결과를 구분하며, 없는 ID 조회는 `null`입니다.
- 등록은 null Post, 이미 저장된 Post, 제목/내용 null·공백, 제목 100자 초과, 내용 1000자 초과를 거부합니다.
- 수정도 같은 문자열 제한을 적용하고, 검증 실패 시 제목과 내용 모두 기존 값을 유지합니다. 길이는 `String.length()` 기준입니다.
- 수정은 대상 조회 → 작성자 확인 → 입력 검증 → 값 변경 순서로 처리합니다. Service의 `@Transactional` 범위에서 Entity 값을 바꾸며, 별도 `save` 호출 없이 Dirty Checking으로 반영됩니다.
- 개발 실행은 `data/` 아래 파일형 H2 DB를 사용합니다. 테스트는 별도 메모리 H2 DB를 사용해 개발 데이터를 건드리지 않습니다.
- 현재 전체 40개 테스트가 통과했습니다. 게시글 서비스, Session 인증, 게시글 HTTP API와 작성자 인가의 성공·실패 경로를 포함합니다.

현재 `findAllPosts()`는 정렬을 지정하지 않으므로 제품 요구사항의 최신순 목록은 아직 구현되지 않았습니다. Service는 내부에서 `Post` Entity를 사용하지만 HTTP 조회 응답은 `PostResponse`로 변환해 허용한 필드만 노출합니다. 파일형 H2의 `ddl-auto=update`는 학습·개발용 설정으로 스키마 변경 이력을 관리하지 않습니다.

```java
User currentUser = userService.findByEmail(authentication.getName()).orElseThrow();
Post post = new Post("Java", "JPA로 게시글 관리", currentUser);
postService.addPost(post);                  // true, ID와 작성자 연결 저장
Post found = postService.findPostById(post.getId());
postService.updatePost(post.getId(), currentUser, "JPA", "수정한 내용"); // SUCCESS
postService.updatePost(post.getId(), currentUser, " ", "내용");         // INVALID_INPUT
postService.deletePost(post.getId(), currentUser);                       // SUCCESS
```

위 예제의 `Post`, `PostService`는 `com.jinhyuk.community.post` 패키지에 있습니다.

### Session 인증 구현 (2026-09-25)

- `User`: 생성 ID, 정규화한 email, PasswordEncoder로 만든 password hash만 저장합니다.
- `UserRepository`: email 조회와 중복 확인을 담당합니다. DB에도 email unique 제약을 둡니다.
- `UserService`: email/password 검증, 중복 확인, password hashing, 회원 저장을 담당합니다.
- `SecurityConfig`: Spring Security form login과 logout을 사용해 인증 정보를 HTTP Session에 저장하고 제거합니다.
- `POST /auth/register`: `application/x-www-form-urlencoded`의 `email`, `password`로 회원가입합니다.
- `POST /auth/login`: 같은 형식으로 로그인합니다. 성공은 200, 잘못된 email/password는 모두 401입니다.
- `POST /auth/logout`: Session과 인증 상태를 제거하고 204를 반환합니다.
- `GET /auth/me`: 로그인 상태이면 현재 email을 반환하고, 비로그인이면 401을 반환합니다.
- `GET /auth/csrf`: 회원가입·로그인·로그아웃 같은 POST 요청에 필요한 CSRF token을 제공합니다.
- 로그인과 로그아웃 성공 시 이전 CSRF token이 제거되므로, 브라우저는 각 성공 뒤 `/auth/csrf`를 다시 호출해야 합니다.
- 게시글·댓글 조회는 공개하고 쓰기 요청은 로그인 사용자에게만 허용합니다. 게시글 수정·삭제의 작성자 본인 확인은 `PostService`에서 처리합니다.
- 전체 테스트 26개가 통과했습니다. 기존 Post/JPA 테스트 17개와 인증 테스트 9개이며 실패·오류·건너뜀은 없습니다.

2026-09-27 ChatGPT 실제 코드 리뷰와 사용자 이해 확인을 완료했습니다. 현재 학습 범위에서 즉시 수정해야 할 핵심 Authentication 버그는 확인되지 않았습니다.

### 게시글 작성자 Authorization 구현·리뷰 완료 (2026-09-30)

- `GET /posts`, `GET /posts/{id}`는 로그인 없이 조회할 수 있습니다. 응답은 `id/title/content/authorId`만 포함해 User의 password hash를 노출하지 않습니다.
- `POST /posts`는 로그인 사용자가 보낸 `title/content`를 검증하고, 서버가 현재 `Authentication`의 email로 찾은 `User`를 작성자로 지정합니다. 클라이언트가 작성자 ID를 선택할 수 없습니다.
- `PATCH /posts/{id}`와 `DELETE /posts/{id}`는 로그인한 작성자 본인에게만 허용합니다. 다른 로그인 사용자는 403, 존재하지 않는 게시글은 404입니다.
- 입력 오류는 400, 유효한 CSRF token은 있지만 로그인하지 않은 쓰기 요청은 401입니다. CSRF token이 없거나 잘못된 unsafe 요청은 Spring Security가 403으로 차단합니다.
- 게시글 요청의 현재 입력 형식은 `application/x-www-form-urlencoded`이며, 수정 요청은 `title`과 `content`를 모두 전달합니다.
- 작성자 확인은 Java 객체 동일성이 아니라 저장된 `User` ID를 비교합니다. 권한이 없거나 입력이 잘못되면 Entity를 바꾸기 전에 종료해 기존 데이터가 유지됩니다.
- 전체 40개 테스트가 성공했고 실패·오류·건너뜀은 없습니다. ChatGPT 실제 코드 리뷰와 사용자 Authorization 흐름 이해 확인도 완료했습니다.

## 기획 문서

- [첫 버전 요구사항](docs/requirements.md): 사용자 흐름, 기능 규칙, 완료 기준
- [개발 작업 목록](docs/backlog.md): 저장소 생성 후 Issue로 옮길 작업 초안
- [설계 초안](docs/design.md): 데이터 관계와 구현 전 결정할 내용
- [게시글 API 초안](docs/api.md): 요청 경로, 권한과 성공·실패 응답
- [진행 기록](docs/progress.md): 현재 상태와 다음 시작점

문서의 세부 규칙은 구현을 시작하기 위한 제안이며 실제 사용 피드백과 리뷰에 따라 수정합니다.

## 첫 버전 범위

- 회원가입 및 로그인
- 질문·학습 기록 작성, 목록·상세 조회, 수정, 삭제
- 댓글 작성·조회·수정·삭제
- 태그별 목록과 페이지네이션
- 배포 후 실제 사용 및 피드백 반영

## 기본 규칙

- 글 조회는 비로그인 사용자도 가능합니다.
- 글과 댓글 작성은 로그인한 사용자만 가능합니다.
- 글과 댓글은 작성자만 수정·삭제할 수 있습니다.
- 첫 버전에서는 게시글 삭제 시 연결된 댓글도 함께 삭제합니다.
- 제목과 본문은 공백만으로 저장할 수 없습니다. 길이 제한은 요구사항 문서의 초안을 따릅니다.
- 목록은 작성 시각 내림차순, 동일 시각에서는 게시글 ID 내림차순으로 정렬합니다.

## 기술 방향

Java, Spring Boot, 관계형 데이터베이스를 사용합니다.
Java/Spring Boot 버전, 빌드 도구, 데이터베이스, 로그인 방식은 개발환경 구성 전에 결정하고 선택 이유를 기록합니다.
프론트엔드는 핵심 API가 작동한 뒤 연결합니다.

## 진행 순서

1. 요구사항과 첫 버전 범위 검토
2. 개발환경 구성 및 실행 가능한 최소 서버 만들기
3. 게시글 기능을 작은 단위로 구현하며 Java·객체지향·HTTP 학습
4. 데이터베이스 저장, 회원 기능, 작성자 권한 구현
5. 댓글·태그·페이지네이션 구현
6. 주요 성공·실패 사례 테스트 및 배포
7. 사용자 피드백 반영, 후속 기능 선정

## 후속 개선 후보

- 좋아요의 중복 요청 및 동시 요청 처리
- 검색 기능과 데이터 증가에 따른 조회 성능 측정·개선
- 신고 및 관리자 기능

채팅, AI 요약, 실시간 알림은 첫 버전에 포함하지 않습니다.
성능 수치는 직접 측정한 환경·데이터·조건과 함께 기록합니다.

## 협업 방식

- 학습자: 요구사항을 이해하고 코드를 작성하며 구현 이유를 설명합니다.
- AI: 개념 설명, 설계 선택지, 디버깅과 코드 검토를 돕습니다. 학습 중 막힌 문제에는 힌트를 우선 제공합니다.
- 리뷰어: 가능한 범위에서 설계와 PR을 검토합니다. 리뷰 일정과 담당 범위는 별도로 협의합니다.
- 기능별 이슈 → 작업 브랜치 → 작은 PR → 리뷰 반영 → 병합 순서로 진행합니다.
- 각 PR에는 변경 이유, 확인 방법, 아직 이해가 부족한 부분을 적습니다.
- 기능의 완료 기준은 동작 확인, 중요한 실패 사례 확인, 구현 원리 설명입니다.
- 비밀번호, API 키, 실제 사용자 정보는 저장소에 올리지 않습니다.

## 첫 리뷰에서 논의할 내용

- 첫 버전의 범위가 적절한가?
- 게시글부터 구현하고 회원 권한을 붙이는 순서가 적절한가?
- 기술 선택과 첫 이슈의 크기는 적절한가?

## 개발환경과 실행

- JDK 21
- Spring Boot 4.1.1
- Gradle 9.7.1 (Wrapper 포함, 별도 Gradle 설치 불필요)
- VSCode Java 확장 또는 Java IDE

프로젝트 폴더에서 PowerShell로 실행합니다.

```powershell
.\gradlew.bat bootRun
```

브라우저에서 http://localhost:8080/health 를 열면 `ok`가 표시됩니다. 종료는 실행한 터미널에서 Ctrl+C입니다.
현재 `/health`는 비로그인 상태에서도 사용할 수 있는 서버 실행 확인용입니다. 게시글 조회 API도 비로그인 상태에서 사용할 수 있으며, 없는 게시글 상세 조회는 404를 반환합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

처음 실행할 때 Gradle과 의존성을 다운로드하므로 인터넷 연결이 필요합니다.
별도 DB 설치는 필요하지 않습니다. 개발 실행 시 파일형 H2 DB가 `data/` 아래에 생성되고, 테스트는 메모리 H2 DB를 사용합니다.
