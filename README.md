# Stocker 

## Docker 환경 설정 및 실행 방법

### 필수 요구사항
- Docker 및 Docker Compose가 설치되어 있어야 합니다.
- Docker Desktop(Mac/Windows) 또는 Docker Engine + Docker Compose(Linux)

### 도커 이미지 빌드
프로젝트 루트 디렉토리에서 다음 명령어를 실행하여 Docker 이미지를 빌드합니다:
```bash
docker-compose build
```

### 도커 컨테이너 실행
다음 명령어를 실행하여 Docker 컨테이너를 시작합니다:
```bash
docker-compose up -d
```
이 명령은 다음 두 개의 컨테이너를 백그라운드에서 실행합니다:
- **stocker-postgres**: PostgreSQL 데이터베이스 서버 (포트 5432)
- **stocker-backend**: Spring Boot 백엔드 애플리케이션 (포트 8080)

### 컨테이너 로그 확인
백엔드 애플리케이션의 로그를 확인하려면:
```bash
docker logs stocker-backend
```

데이터베이스 서버의 로그를 확인하려면:
```bash
docker logs stocker-postgres
```

### 컨테이너 중지
다음 명령어로 모든 컨테이너를 중지할 수 있습니다:
```bash
docker-compose down
```

컨테이너와 함께 볼륨(데이터베이스 데이터)도 삭제하려면:
```bash
docker-compose down -v
```

### 도커 구성 설명
- **Dockerfile**: Spring Boot 애플리케이션을 위한 도커 이미지 빌드 스크립트
- **docker-compose.yml**: 멀티 컨테이너 구성 (백엔드 + 데이터베이스)
- 데이터베이스 설정(기본값):
  - 사용자: postgres
  - 비밀번호: postgres
  - 데이터베이스 이름: stockerdb
  - 포트: 5432

## 주요 API 상세 설명

### 주식 심볼 데이터 수집 API

#### `/api/stocks/fetch/symbols`

이 엔드포인트는 Finnhub API에서 주식 심볼 데이터를 가져와 데이터베이스에 저장합니다. 모든 심볼을 가져오거나 특정 심볼 하나만 선택적으로 가져올 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `exchange` (선택, 기본값: "US"): 데이터를 가져올 거래소 코드(예: "US", "KO" 등)
- `symbol` (선택): 특정 심볼 코드(예: "AAPL", "MSFT"). 입력 시 해당 심볼만 저장, 미입력 시 모든 심볼 저장

**사용 예시**:
- 모든 심볼 가져오기: `/api/stocks/fetch/symbols?exchange=US`
- 특정 심볼만 가져오기: `/api/stocks/fetch/symbols?symbol=AAPL&exchange=US`

**응답 형식**: JSON
```json
// 모든 심볼 가져온 경우
{
  "success": true,
  "exchange": "US",
  "savedCount": 8000,
  "message": "Successfully saved 8000 stock symbols for exchange US"
}

// 특정 심볼만 가져온 경우
{
  "success": true,
  "exchange": "US",
  "symbol": "AAPL",
  "savedCount": 1,
  "message": "Symbol AAPL successfully saved for exchange US"
}
```

**오류 응답**:
```json
{
  "success": false,
  "exchange": "US",
  "symbol": "AAPL", // symbol 파라미터 사용 시
  "error": "오류 메시지"
}
```

**주요 특징**:
- 배치 처리: 설정된 배치 크기(기본값: 200)에 따라 대량의 데이터를 효율적으로 저장합니다.
- 로깅: 데이터 저장 진행률을 퍼센트로 로그에 기록합니다.
- 중복 방지: 심볼의 고유성이 보장됩니다(UK_SYMBOL 제약 조건).
- 선택적 필터링: 특정 심볼만 선택적으로 저장할 수 있습니다.
- 병렬 처리: 다중 스레드를 활용한 병렬 처리로 데이터 처리 속도를 향상시킵니다.

**데이터 저장 위치**: `stock_info` 테이블

**구현 관련**:
- 데이터는 Finnhub API에서 가져오며, API 키는 `application-secret.properties`에서 관리됩니다.
- 심볼 데이터는 `stock_info` 테이블에 저장되며, 심볼 이름으로 유니크 제약조건이 적용됩니다.
- 배치 크기는 `application.properties`의 `spring.jpa.properties.hibernate.jdbc.batch_size` 속성으로 설정됩니다.

## 성능 최적화

### 주식 심볼 데이터 처리 최적화
주식 심볼 데이터를 대량으로 처리할 때 성능을 개선하기 위해 다음과 같은 최적화를 구현했습니다:

#### 중복 체크 최적화
- **이전 방식**: 각 심볼마다 데이터베이스 쿼리(`existsBySymbol`)를 실행하여 중복 체크
- **개선 방식**: 한 번에 모든 심볼을 메모리로 로드하여 Set 자료구조에서 O(1) 시간에 중복 체크
- **효과**: 데이터베이스 쿼리 수를 대폭 감소시켜 성능 향상

#### 배치 처리 최적화
- **배치 크기 증가**: 50 → 200으로 증가
- **효과**: 데이터베이스 커밋 횟수 감소 및 처리량 향상

#### 병렬 처리 최적화
- **청크 크기 증가**: 500 → 1000으로 증가
- **스레드 수 증가**: 4 → 8로 증가
- **효과**: 다중 코어 활용도 향상 및 처리 속도 개선

#### 메모리 내 중복 방지 추가
- 병렬 처리 중 발생할 수 있는 중복 저장을 방지하기 위해 공유된 Set 사용
- 새로 추가되는 심볼도 즉시 Set에 등록하여 다른 스레드에서 중복 저장되지 않도록 함

이러한 최적화를 통해 대량의 주식 심볼 데이터(수천 개)를 처리할 때 성능이 크게 향상되었습니다.

## API 명세서

### API 엔드포인트 요약

| 메서드 | 엔드포인트 | 설명 | 주요 파라미터 |
|--------|------------|------|--------------|
| **인증 관련 API** |
| POST | `/api/auth/signup` | 회원가입 | username, email, password |
| POST | `/api/auth/login` | RESTful API용 로그인 | username, password |
| GET | `/api/auth/status` | 로그인 상태 확인 | - |
| POST | `/api/auth/logout` | RESTful API용 로그아웃 | - |
| GET | `/api/users/{username}` | 사용자 정보 조회 | username |
| **주식 데이터 수집 API** |
| GET | `/api/stocks/fetch/symbols` | Finnhub API에서 주식 심볼 데이터 수집 및 DB 저장 | exchange (기본값: "US"), symbol (선택) |


