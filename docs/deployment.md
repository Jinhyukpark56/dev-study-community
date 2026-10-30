# v0.1 배포 준비

상태: 운영 profile, 환경변수 예시, Dockerfile과 확인 절차를 준비했습니다. 유료 서비스나 외부 리소스는 생성하지 않았고, PostgreSQL runtime과 외부 HTTPS 배포는 아직 검증하지 않았습니다.

## 목표 구성

- 애플리케이션: Java 21 Spring Boot 실행 JAR 또는 같은 JAR을 담은 Docker image
- 화면: Spring Boot가 `src/main/resources/static`의 HTML·CSS·JavaScript를 API와 같은 origin에서 제공
- 데이터베이스: 외부 PostgreSQL
- 인증: 한 애플리케이션 process의 HTTP Session과 `JSESSIONID` cookie
- liveness: `GET /health` → `200 ok`
- DB readiness: `GET /ready` → DB `select 1` 성공 시 `200 ready`

화면과 API가 같은 origin을 사용하므로 현재 CORS 설정은 필요하지 않습니다. 브라우저는 같은 origin의 Session cookie를 보내며, 화면은 `/auth/csrf`에서 받은 token을 쓰기 요청에 포함합니다.

## 환경변수

실제 값은 배포 플랫폼의 secret 또는 environment 설정에 저장합니다. `.env` 파일과 비밀번호를 Git에 올리지 않습니다. 저장소의 `.env.example`에는 변수 이름과 자리표시자만 있습니다.

| 변수 | 용도 | 예시 또는 기본값 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영 설정 활성화 | `prod` |
| `PORT` | 애플리케이션 포트 | `8080` |
| `DB_HOST` | 애플리케이션에서 접근 가능한 PostgreSQL host | 플랫폼 내부 DNS 이름 |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | database 이름 | `dev_study_community` |
| `DB_USERNAME` | database 사용자 | 플랫폼이 제공한 사용자 |
| `DB_PASSWORD` | database 비밀번호 | secret 값, 기본값 없음 |
| `DB_POOL_SIZE` | 최대 DB connection 수 | `5` |
| `DB_SSL_MODE` | PostgreSQL JDBC SSL mode | 운영 기본 `require` |
| `SESSION_COOKIE_SECURE` | HTTPS에서만 cookie 전송 | 운영 `true` |
| `SESSION_TIMEOUT` | 로그인 Session 유지 시간 | `30m` |
| `JPA_DDL_AUTO` | Hibernate schema 처리 | 짧은 v0.1 preview `update` |

`DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`가 없으면 prod profile은 정상 시작할 수 없습니다. 운영 비밀번호에는 기본값을 두지 않습니다.

`DB_SSL_MODE=require`는 네트워크 구간 암호화를 요구하는 preview 기본값입니다. 사용하는 PostgreSQL 제공자가 CA 검증용 설정이나 `verify-full`을 요구하면 해당 제공자의 JDBC 안내에 맞춥니다. SSL을 지원하지 않는 로컬 DB를 확인할 때만 제한된 개발 환경에서 `disable`을 사용합니다.

## JAR 빌드와 실행

Linux 또는 macOS:

```bash
./gradlew clean test bootJar
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/dev-study-community-0.0.1-SNAPSHOT.jar
```

Windows에서 빌드:

```powershell
.\gradlew.bat clean test bootJar
```

운영 실행 전에 환경변수를 프로세스나 플랫폼 설정으로 주입합니다. prod profile은 `spring.jpa.open-in-view=false`이므로 HTTP 응답에 필요한 연관 데이터는 Service transaction 안에서 조회해야 합니다.

## Docker

Dockerfile의 build stage는 `clean test bootJar`를 실행합니다. 테스트가 실패하면 image도 만들어지지 않습니다.

```bash
docker build -t dev-study-community:0.1 .
docker run --rm -p 8080:8080 --env-file .env dev-study-community:0.1
```

컨테이너의 `localhost`는 컨테이너 자신입니다. `DB_HOST`에는 다음 중 실제로 컨테이너에서 접근 가능한 값을 사용합니다.

- 같은 Docker network의 PostgreSQL service 이름
- 관리형 PostgreSQL이 제공한 내부 또는 외부 host
- Windows/macOS Docker Desktop에서 host DB를 시험할 때 `host.docker.internal`
- Linux에서 host DB를 시험할 때 실행 옵션 `--add-host=host.docker.internal:host-gateway`와 `DB_HOST=host.docker.internal`

로컬 HTTP에서 prod profile을 점검할 때만 `SESSION_COOKIE_SECURE=false`를 사용합니다. 외부 endpoint는 HTTPS를 적용하고 `true`를 유지합니다.

현재 작업 환경에서는 Docker CLI를 확인했지만 daemon이 실행되지 않아 `docker build`와 container runtime 검증을 완료하지 못했습니다. Dockerfile 존재만으로 배포 검증 완료로 처리하지 않습니다.

## liveness와 readiness

- `/health`는 Spring process가 요청에 응답하는지만 확인합니다. DB 장애 중에도 200일 수 있습니다.
- `/ready`는 `JdbcTemplate`으로 `select 1`을 실행합니다. 새 요청을 받을 수 있는지 판단하는 배포 플랫폼 health check에는 `/ready`를 사용합니다.
- process 생존 감시를 별도로 설정할 수 있는 환경에서는 `/health`를 liveness probe로 사용합니다.

플랫폼이 TLS를 종료하고 reverse proxy header를 전달하면 애플리케이션은 `server.forward-headers-strategy=framework`로 원래 요청 정보를 해석합니다.

## 데이터 영속성과 schema

기본 개발 profile의 파일형 H2는 로컬 학습용입니다. 컨테이너 로컬 파일은 재배포 때 사라질 수 있으므로 운영 데이터 저장소로 사용하지 않습니다. prod profile은 외부 PostgreSQL에 연결합니다.

짧은 preview에서 새 database를 시작하기 위해 현재 `JPA_DDL_AUTO=update`를 기본값으로 둡니다. 이 방식은 변경 이력, SQL 사전 검토, 되돌리기를 보장하지 않습니다. 장기간 데이터를 보존하기 전에 다음 작업이 필요합니다.

1. Flyway 같은 versioned migration 도구로 초기 schema와 변경 SQL 작성
2. 기존 Post 행의 `post_type`, `created_at`, `updated_at`을 backfill
3. backfill 검증 뒤 필요한 not-null 제약 추가
4. staging PostgreSQL에 migration과 애플리케이션 쿼리 적용
5. `JPA_DDL_AUTO=validate`로 변경
6. 자동 backup, 복구 연습과 보존 기간 확인

이번 구현은 Post의 새 type·시각 열을 기존 파일형 H2와 호환하려고 nullable로 추가했습니다. `getType()`은 null을 `STUDY_LOG`로 읽지만 DB 값을 갱신하지 않고, null 시각은 페이지 정렬에서 뒤로 보낼 뿐입니다. 기존 데이터가 있는 DB를 그대로 장기 운영 DB로 전환하지 말고 migration/backfill을 먼저 수행합니다. 가능하면 첫 preview는 빈 PostgreSQL로 검증합니다.

PostgreSQL driver와 연결 설정은 추가했지만 실제 PostgreSQL에서 schema 생성, 태그 쿼리, 페이지 정렬, 게시글 삭제 rollback을 아직 실행하지 않았습니다.

## Session, HTTPS와 보안

- 운영 endpoint는 HTTPS를 사용합니다.
- `JSESSIONID`는 `HttpOnly`, `Secure`, `SameSite=Lax`로 설정합니다.
- 로그인과 로그아웃 성공 뒤 화면은 `/auth/csrf`를 다시 호출합니다.
- 화면과 API를 다른 origin으로 분리하지 않습니다. 분리하면 credential CORS, cookie domain과 `SameSite=None` 정책을 별도로 설계해야 합니다.
- 비밀번호, DB 접속정보, Session ID, CSRF token을 로그에 남기지 않습니다.
- 운영 오류 응답은 message, binding error, exception, stack trace를 포함하지 않게 설정했습니다.

Session은 현재 한 애플리케이션 process의 memory에 저장됩니다. process가 재시작되면 사용자가 로그아웃되며, 여러 instance로 늘리면 요청이 다른 instance로 갈 때 인증 상태가 이어지지 않습니다. v0.1 preview는 한 instance로 실행하고, 필요해질 때 shared Session store를 검토합니다.

회원가입과 로그인 endpoint에는 애플리케이션 수준 rate limiting이 없습니다. 소수 리뷰 사용자를 넘어 공개하기 전에 reverse proxy 또는 애플리케이션에서 요청 제한, 로그인 실패 관찰과 차단 기준을 정합니다.

## 플랫폼 후보

설정이 단순한 형태는 HTTPS와 환경변수를 지원하는 managed Java/container service 한 개와 managed PostgreSQL 한 개입니다. 요금과 무료 조건은 변경될 수 있으므로 생성 직전에 공식 문서를 다시 확인합니다.

- Render의 무료 web service는 [유휴 상태가 15분 이어지면 spin down](https://render.com/docs/free)될 수 있고, 다음 요청에서 다시 시작하는 데 시간이 걸립니다. process가 다시 만들어지면 memory Session이 유지되지 않을 수 있습니다. 같은 문서 기준 무료 PostgreSQL은 생성 후 30일에 만료되고 backup을 제공하지 않아 짧은 리뷰 배포에만 적합합니다.
- Railway의 [현재 plan 문서](https://docs.railway.com/pricing/plans)에 표시된 credit, memory와 최소 비용을 배포 직전에 확인합니다. 무료 credit을 넘는 사용이나 지속 실행에 비용이 생길 수 있습니다.

사용 기간, database 보존 기간, backup 필요성, sleep 허용 여부와 예상 비용을 확인한 뒤 플랫폼을 선택합니다. 이번 작업에서는 계정 연결, plan 선택, database 생성, 실제 배포를 수행하지 않습니다.

## 출시 전 확인

1. 최종 branch에서 전체 자동 테스트와 `bootJar` 성공
2. Git 추적 파일과 image layer에 실제 secret이 없는지 확인
3. Docker image를 빌드하고 non-root user로 시작되는지 확인
4. 새 PostgreSQL에서 애플리케이션 시작, `/health`와 `/ready` 200 확인
5. PostgreSQL schema, unique/FK 제약과 시간 열 확인
6. 별도 browser 또는 private window에서 사용자 두 명 준비
7. 회원가입 → 로그인 → 글 작성 → 태그 조회 → 댓글 작성 흐름 확인
8. 다른 사용자의 글·댓글 수정과 삭제가 403인지 확인
9. 다른 게시글 ID를 사용한 댓글 접근이 404인지 확인
10. 게시글 삭제 후 댓글과 `post_tags` 연결은 없어지고 User와 공유 Tag는 남는지 확인
11. 로그아웃 후 쓰기 요청이 401인지 확인
12. 애플리케이션 재시작 뒤 글·댓글·태그는 유지되고 Session은 종료되는지 확인
13. 400, 401, 403, 404, 500 화면 메시지에 내부 예외나 secret이 없는지 확인
14. HTTPS에서 Secure cookie와 CSRF 요청 흐름 확인

애플리케이션 배포가 실패하면 이전 image로 되돌립니다. `ddl-auto=update`로 schema가 바뀐 뒤에는 자동 schema rollback을 기대할 수 없습니다. 장기 운영 배포는 database backup과 versioned migration의 forward/rollback 절차를 먼저 준비해야 합니다.
