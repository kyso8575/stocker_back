# 중복 로그인 관리 시스템

## 개요

이 시스템은 같은 아이디로 중복 로그인을 방지하고, 새로운 로그인 시 기존 세션을 자동으로 무효화하는 기능을 제공합니다.

## 주요 기능

1. **같은 아이디 중복 로그인 방지**: 동일한 사용자가 여러 곳에서 로그인할 수 없습니다.
2. **다른 아이디 동시 로그인 허용**: 서로 다른 사용자는 동시에 로그인할 수 있습니다.
3. **자동 세션 무효화**: 새로운 로그인 시 기존 세션이 자동으로 삭제됩니다.
4. **관리자 세션 관리**: 관리자가 모든 활성 세션을 조회하고 강제 로그아웃할 수 있습니다.

## 동작 과정

```
1. 사용자A (ID: user1)가 로그인
   - 세션 생성: SESSION_ABC123
   - 메모리에 저장: user1 -> SESSION_ABC123

2. 사용자A (ID: user1)가 다른 곳에서 로그인
   - 기존 세션 SESSION_ABC123을 데이터베이스에서 삭제
   - 새 세션 생성: SESSION_XYZ789
   - 메모리 업데이트: user1 -> SESSION_XYZ789

3. 첫 번째 로그인 세션은 무효화되어 API 호출 시 인증 실패
```

## API 엔드포인트

### 일반 사용자용

#### 1. 로그인
```http
POST /api/auth/login
Content-Type: application/json

{
    "usernameOrEmail": "user1",
    "password": "password123"
}
```

**응답:**
```json
{
    "success": true,
    "message": "Login successful",
    "user": {
        "id": 1,
        "username": "user1",
        "email": "user1@example.com",
        "fullName": "User One"
    }
}
```

#### 2. 로그아웃
```http
POST /api/auth/logout
```

**응답:**
```json
{
    "success": true,
    "message": "Logout successful"
}
```

### 관리자용

#### 1. 특정 사용자 강제 로그아웃
```http
POST /api/auth/admin/force-logout/1
```

**응답:**
```json
{
    "success": true,
    "message": "User 1 has been forcefully logged out"
}
```

## 기술적 구현

### 핵심 컴포넌트

1. **SessionManagerService**: 세션 관리의 핵심 로직
   - 사용자별 활성 세션 추적
   - 기존 세션 무효화
   - 메모리 기반 세션 캐시

2. **AuthController**: 인증 관련 API 엔드포인트
   - 로그인/로그아웃 시 세션 관리 호출
   - 관리자용 세션 관리 기능

3. **AuthenticationInterceptor**: 권한 검증
   - 관리자 세션 관리 엔드포인트 보호

### 데이터베이스 구조

Spring Session JDBC를 사용하여 세션 정보를 데이터베이스에 저장:
- `SPRING_SESSION`: 세션 메타데이터
- `SPRING_SESSION_ATTRIBUTES`: 세션 속성 (userId, username 등)

### 메모리 캐시

`ConcurrentHashMap<Long, String>`을 사용하여 사용자 ID와 세션 ID 매핑을 메모리에 캐시:
```java
// 사용자별 활성 세션 ID 캐시
private final Map<Long, String> userActiveSessionMap = new ConcurrentHashMap<>();
```

## 테스트 시나리오

### 시나리오 1: 중복 로그인 방지
1. 브라우저 A에서 user1으로 로그인
2. 브라우저 B에서 user1으로 로그인
3. 브라우저 A에서 API 호출 → 401 Unauthorized (세션 무효화됨)
4. 브라우저 B에서 API 호출 → 정상 동작

### 시나리오 2: 다른 사용자 동시 로그인
1. 브라우저 A에서 user1으로 로그인
2. 브라우저 B에서 user2로 로그인
3. 두 브라우저 모두 정상 동작 (서로 영향 없음)

### 시나리오 3: 관리자 세션 관리
1. 관리자로 로그인
2. `/api/auth/admin/force-logout/{userId}`로 특정 사용자 강제 로그아웃

## 로그 모니터링

다음과 같은 로그를 통해 세션 관리 상태를 모니터링할 수 있습니다:

```
# 새로운 세션 등록
INFO : Registering new session for user: 1, sessionId: ABC123

# 기존 세션 무효화
INFO : Invalidated old session for user: 1, oldSessionId: XYZ789

# 성공적인 로그인
INFO : Login successful with duplicate login management: userId=1, username=user1, sessionId=ABC123

# 세션 정리
INFO : Removed session for user: 1, sessionId: ABC123
```

## 주의사항

1. **메모리 캐시**: 애플리케이션 재시작 시 메모리 캐시가 초기화됩니다.
2. **데이터베이스 의존성**: 세션 무효화는 데이터베이스 접근이 필요합니다.
3. **성능**: 대량의 동시 사용자가 있는 경우 메모리 사용량을 모니터링해야 합니다.
4. **클러스터 환경**: 다중 서버 환경에서는 세션 동기화를 위한 추가 구현이 필요할 수 있습니다.

## 설정

`application.properties`에서 세션 관련 설정을 확인하고 조정할 수 있습니다:

```properties
# 세션 타임아웃 (초)
spring.session.timeout=3600
server.servlet.session.timeout=3600

# 세션 쿠키 설정
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=false
server.servlet.session.cookie.same-site=lax
``` 