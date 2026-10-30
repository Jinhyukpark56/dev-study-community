# 개발 공부 커뮤니티

개발 공부 중 생긴 질문과 학습 기록을 게시글과 댓글로 공유하는 Spring Boot 애플리케이션입니다. Java와 백엔드 개념을 실제 기능에 연결하고, 자동 테스트와 코드 리뷰로 구현을 확인합니다.

저장소: https://github.com/Jinhyukpark56/dev-study-community

## 현재 상태 — 2026-10-20

`feature/v0.1-release-completion`에서 v0.1 잔여 기능을 구현했습니다. 현재 변경은 **통합 코드 리뷰 대기 상태**이며 아직 `main`에 병합하거나 외부 환경에 배포하지 않았습니다.

- Spring Security form login과 HTTP Session 기반 회원가입·로그인·로그아웃
- 게시글·댓글 작성자 ID를 확인하는 수정·삭제 권한 검사
- 질문(`QUESTION`)·학습 기록(`STUDY_LOG`) 게시글 CRUD
- 댓글 CRUD와 게시글 삭제 시 댓글을 먼저 지우는 단일 트랜잭션
- 재사용 가능한 태그와 `post_tags` 다대다 연결
- 게시글 최신순 페이지 조회와 태그 필터
- 회원가입부터 게시글·댓글·태그·페이지 이동까지 확인할 수 있는 최소 웹 화면
- PostgreSQL 운영 profile, 환경변수 예시, Dockerfile과 배포 절차 초안
- `/health` liveness와 `/ready` DB readiness 분리

최종 `clean test bootJar --no-daemon`에서 10개 test suite, 60개 테스트가 모두 성공했고 실패·오류·건너뜀은 0개였습니다. 실행 가능한 boot JAR도 생성했습니다. 2026-09-30에 기록한 40개 성공은 이전 게시글 작성자 인가 마일스톤의 수치입니다.

## v0.1 기능

### 인증과 권한

- `POST /auth/register`: email/password 회원가입, BCrypt hash 저장
- `POST /auth/login`: Spring Security form login, Session 생성
- `POST /auth/logout`: Session과 인증 상태 제거
- `GET /auth/me`: 로그인 사용자의 `id`, `email` 반환
- 조회 요청은 공개하고, 게시글·댓글 쓰기 요청은 로그인 사용자에게만 허용
- 게시글·댓글 수정과 삭제는 저장된 작성자 PK와 현재 사용자 PK가 같을 때만 허용
- unsafe 요청은 CSRF token과 같은 Session cookie가 필요

### 게시글·댓글·태그

- 게시글은 `id/title/content/author/type/tags/createdAt/updatedAt`을 가집니다.
- 게시글 목록은 `createdAt DESC`, 같은 시각에는 `id DESC`로 정렬합니다.
- `GET /posts?page=0&size=20&tag=java`는 목록과 현재 페이지, 전체 페이지 수, 전체 게시글 수를 함께 반환합니다.
- 태그 이름은 앞뒤 공백 제거, 내부 연속 공백 축소, 소문자 변환 후 재사용합니다. 게시글당 서로 다른 태그는 최대 3개입니다.
- 댓글은 게시글과 작성자에 각각 다대일로 연결하고, 생성 시각·ID 오름차순으로 조회합니다.
- 게시글을 삭제하면 같은 트랜잭션에서 댓글과 글-태그 연결을 제거합니다. 사용자와 다른 글에서 공유하는 태그는 유지합니다.

요청 형식과 응답 예시는 [API 문서](docs/api.md)에 있습니다.

### 최소 웹 화면

Spring Boot가 `src/main/resources/static`의 HTML, CSS, JavaScript를 API와 같은 origin에서 제공합니다.

- 회원가입, 로그인, 로그아웃과 현재 로그인 상태 표시
- 게시글 목록·상세·작성·수정·삭제
- 댓글 작성·조회·수정·삭제
- 태그 표시·태그별 조회와 게시글 페이지 이동
- 400, 401, 403, 404, 500 응답에 대한 사용자 메시지

화면에서 버튼을 숨기는 것은 편의를 위한 처리입니다. 실제 인증과 작성자 권한 검사는 서버에서 다시 수행합니다.

## 입력과 응답의 핵심 규칙

- email: 앞뒤 공백 제거와 소문자 정규화, 최대 254자, 공백 금지, 중복 금지
- password: 최소 15자, BCrypt 입력 기준 최대 72 UTF-8 byte, 원문 저장 금지
- 제목: null·공백만인 값 금지, 최대 100자
- 게시글 본문: null·공백만인 값 금지, 최대 1000자
- 댓글: null·공백만인 값 금지, 최대 1000자
- 페이지 번호: 0 이상, 크기: 1~50
- 400은 입력 오류, 401은 로그인 필요, 403은 작성자 권한 없음 또는 CSRF 오류, 404는 대상 없음입니다.

## 프로젝트 구조

```text
src/main/java/com/jinhyuk/community
├─ auth        회원가입·CSRF·현재 사용자 API
├─ security    Session 인증과 접근 정책
├─ user        User Entity·Repository·Service
├─ post        게시글·페이지 조회·작성자 권한
├─ comment     댓글 CRUD·작성자 권한
├─ tag         Tag Entity·Repository
├─ HealthController.java
└─ ReadinessController.java

src/main/resources
├─ static      최소 웹 화면
├─ application.properties
└─ application-prod.properties
```

## 개발환경과 실행

- JDK 21
- Spring Boot 4.1.1
- Gradle 9.7.1 Wrapper
- 개발: 파일형 H2
- 테스트: 메모리 H2
- 배포 profile: PostgreSQL

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

실행 후 http://localhost:8080/ 에서 화면을 열 수 있습니다.

- `GET /health`: 애플리케이션 process가 응답하는지 확인하는 liveness, `200 ok`
- `GET /ready`: DB에 `select 1`을 실행하는 readiness, 성공 시 `200 ready`

테스트와 실행 JAR 생성:

```powershell
.\gradlew.bat clean test bootJar
```

개발 실행 시 파일형 H2 DB가 `data/` 아래에 생성됩니다. 테스트는 별도 메모리 H2를 사용하므로 개발 데이터를 변경하지 않습니다.

## 배포 준비 상태

운영 profile과 Dockerfile은 준비했지만 실제 PostgreSQL 기동, Docker image 실행, 외부 HTTPS 환경의 Session cookie, 외부 배포 시나리오는 아직 확인하지 않았습니다. 기존 H2 DB에는 이번에 추가한 유형·시각 열의 backfill이 필요할 수 있으며, 장기 운영 전에는 `ddl-auto=update` 대신 versioned migration을 도입해야 합니다. 자세한 절차와 제한은 [배포 문서](docs/deployment.md)에 있습니다.

## 문서

- [첫 버전 요구사항](docs/requirements.md): v0.1 범위와 완료 기준
- [API 문서](docs/api.md): 인증·게시글·댓글 요청과 상태 코드
- [설계](docs/design.md): Entity 관계, 트랜잭션, 조회 구조와 현재 위험 요소
- [진행 기록](docs/progress.md): 날짜별 구현·검증 상태
- [배포 준비](docs/deployment.md): 운영 환경변수, Docker, PostgreSQL과 출시 전 확인
- [개발 작업 목록](docs/backlog.md): 초기 작업 초안

## 통합 코드 리뷰 우선순위

1. `Comment → Post/User` 관계와 게시글 삭제 트랜잭션
2. 공유 Tag를 유지하면서 `post_tags` 연결만 바꾸는 방식
3. `GET /posts`가 배열에서 페이지 응답 객체로 바뀐 계약
4. Session·CSRF와 최소 화면의 실제 요청 흐름
5. 기존 DB의 새 열 backfill 및 PostgreSQL migration 계획
6. 동시에 같은 새 태그를 만드는 요청과 공개 인증 endpoint의 요청 제한 정책

ChatGPT 통합 코드 리뷰와 필요한 수정을 마친 뒤 PR을 병합합니다.
