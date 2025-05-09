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
| **데이터 수집 API** |
| GET | `/api/stocks/fetch/symbols` | Finnhub API에서 주식 심볼 데이터 수집 및 DB 저장 | exchange (기본값: "US"), symbol (선택) |
| GET | `/api/stocks/fetch/company_profiles` | Finnhub API에서 회사 프로필 데이터 수집 및 DB 저장 | batchSize (기본값: 20), delayMs (기본값: 500), symbol (선택) |
| GET | `/api/stocks/fetch/quotes` | Finnhub API에서 주식 시세 데이터 수집 및 DB 저장 | symbol (선택), batchSize (기본값: 20), delayMs (기본값: 500) |
| GET | `/api/stocks/fetch/basic_financials` | Finnhub API에서 주식 기본 재무 지표 데이터 수집 및 DB 저장 | symbol (선택), batchSize (기본값: 20), delayMs (기본값: 500) |
| **정보 관련 API** |
| GET | `/api/stocks/info/company_news` | Finnhub API에서 회사 뉴스 데이터 가져오기 | symbol (필수), from (필수), to (필수), count (선택) |
| GET | `/api/stocks/info/market_news` | Finnhub API에서 일반 시장 뉴스 데이터 가져오기 | from (필수), to (필수), count (선택) |
| GET | `/api/stocks/info/basic_financials` | 저장된 주식 기본 재무 지표 데이터 조회 | symbol (필수) |

### 회사 프로필 데이터 수집 API

#### `/api/stocks/fetch/company_profiles`

이 엔드포인트는 Finnhub API에서 주식 심볼에 대한 회사 프로필 정보를 가져와 데이터베이스에 저장합니다. 모든 심볼에 대한 회사 프로필을 일괄 처리하거나 특정 심볼 하나에 대한 프로필만 가져올 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `batchSize` (선택, 기본값: 20): 한 번에 처리할 주식 심볼 수
- `delayMs` (선택, 기본값: 500): API 호출 사이의 지연 시간(밀리초)
- `symbol` (선택): 특정 심볼 코드(예: "AAPL"). 입력 시 해당 심볼의 프로필만 가져옴

**사용 예시**:
- 모든 회사 프로필 가져오기: `/api/stocks/fetch/company_profiles`
- 처리 배치 크기 및 지연 시간 지정: `/api/stocks/fetch/company_profiles?batchSize=50&delayMs=1000`
- 특정 심볼의 회사 프로필만 가져오기: `/api/stocks/fetch/company_profiles?symbol=AAPL`

**응답 형식**: JSON
```json
// 모든 회사 프로필 처리한 경우
{
  "success": true,
  "totalProcessed": 200,
  "validProfiles": 180,
  "emptyProfiles": 20,
  "message": "Successfully processed 200 company profiles (valid: 180, empty: 20)"
}

// 특정 심볼의 프로필만 가져온 경우
{
  "success": true,
  "data": {
    "symbol": "AAPL",
    "name": "Apple Inc",
    "country": "US",
    "currency": "USD",
    "exchange": "NASDAQ",
    "ipo": "1980-12-12",
    "marketCapitalization": 2994003.0,
    "shareOutstanding": 15821.5,
    "logo": "https://static.finnhub.io/logo/87cb30d8-80df-11ea-8951-00000000092a.png",
    "phone": "14089961010",
    "weburl": "https://www.apple.com/",
    "finnhubIndustry": "Technology",
    "profileEmpty": false
  },
  "message": "Successfully updated company profile for AAPL"
}
```

**오류 응답**:
```json
{
  "success": false,
  "symbol": "AAPL", // symbol 파라미터 사용 시
  "error": "오류 메시지"
}
```

**주요 특징**:
- 배치 처리: 지정된 `batchSize`에 따라 회사 프로필을 효율적으로 수집합니다.
- API 호출 제한 관리: `delayMs` 파라미터로 API 호출 간격을 조절하여 API 사용량 제한을 준수합니다.
- 유효성 검증: 빈 프로필과 유효한 프로필을 구분하여 저장합니다.
- 개별 처리: 특정 심볼에 대한 회사 프로필 정보만 선택적으로 처리할 수 있습니다.

**데이터 저장 위치**: `stock_info` 테이블에 회사 프로필 정보 필드들이 업데이트됩니다.

### 주식 시세(Quotes) 데이터 수집 API

#### `/api/stocks/fetch/quotes`

이 엔드포인트는 Finnhub API에서 주식 시세 데이터를 가져와 데이터베이스에 저장합니다. 특정 심볼 하나에 대한 시세만 가져오거나, 모든 심볼에 대한 시세를 일괄적으로 가져올 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `symbol` (선택): 특정 심볼 코드(예: "AAPL"). 입력 시 해당 심볼의 시세만 가져옴
- `batchSize` (선택, 기본값: 20): 한 번에 처리할 주식 심볼 수 (symbol이 제공되지 않을 때)
- `delayMs` (선택, 기본값: 500): API 호출 사이의 지연 시간(밀리초) (symbol이 제공되지 않을 때)

**사용 예시**:
- 특정 심볼의 시세만 가져오기: `/api/stocks/fetch/quotes?symbol=AAPL`
- 모든 심볼의 시세 가져오기: `/api/stocks/fetch/quotes`
- 처리 배치 크기 및 지연 시간 지정: `/api/stocks/fetch/quotes?batchSize=50&delayMs=1000`

**응답 형식**: JSON
```json
// 특정 심볼의 시세를 가져온 경우
{
  "success": true,
  "symbol": "AAPL",
  "data": {
    "id": 1,
    "symbol": "AAPL",
    "currentPrice": 213.32,
    "change": 0.82,
    "percentChange": 0.3859,
    "highPrice": 214.56,
    "lowPrice": 208.9,
    "openPrice": 209.08,
    "previousClosePrice": 212.5,
    "timestamp": 1746129600,
    "createdAt": "2024-05-02T10:15:30.123456"
  },
  "message": "Successfully fetched and saved quote data for AAPL"
}

// 모든 심볼의 시세를 처리한 경우
{
  "success": true,
  "savedCount": 150,
  "message": "Successfully fetched and saved quotes for 150 symbols"
}
```

**오류 응답**:
```json
{
  "success": false,
  "symbol": "INVALID", // symbol 파라미터 사용 시
  "error": "No quote data found for INVALID"
}
```

**주요 특징**:
- 선택적 처리: 특정 심볼 또는 모든 심볼에 대한 시세 데이터를 수집할 수 있습니다.
- 배치 처리: 지정된 `batchSize`에 따라 시세 데이터를 효율적으로 수집합니다.
- 트랜잭션 관리: 각 배치마다 별도의 트랜잭션으로 처리하여 데이터 일관성을 유지합니다.
- API 호출 제한 관리: `delayMs` 파라미터로 API 호출 간격을 조절하여 API 사용량 제한을 준수합니다.

**데이터 저장 위치**: `stock_quotes` 테이블

**시세 데이터 필드 설명**:
- `currentPrice` (c): 현재 가격
- `change` (d): 당일 변동 금액
- `percentChange` (dp): 당일 변동 비율(%)
- `highPrice` (h): 당일 최고가
- `lowPrice` (l): 당일 최저가
- `openPrice` (o): 당일 시가
- `previousClosePrice` (pc): 전일 종가
- `timestamp` (t): 데이터 타임스탬프(초 단위 epoch time)

### 회사 뉴스(Company News) 데이터 API

#### `/api/stocks/info/company_news`

이 엔드포인트는 Finnhub API에서 특정 주식 심볼에 대한 회사 뉴스 데이터를 가져옵니다. 지정된 기간 내의 뉴스 기사를 검색하며, 선택적으로 반환할 뉴스 항목 수를 제한할 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `symbol` (필수): 주식 심볼 코드(예: "AAPL")
- `from` (필수): 시작 날짜 (YYYY-MM-DD 형식)
- `to` (필수): 종료 날짜 (YYYY-MM-DD 형식)
- `count` (선택): 반환할 최대 뉴스 항목 수

**사용 예시**:
- 특정 기간의 뉴스 가져오기: `/api/stocks/info/company_news?symbol=AAPL&from=2024-05-01&to=2024-05-07`
- 특정 기간의 뉴스를 최대 3개까지 가져오기: `/api/stocks/info/company_news?symbol=AAPL&from=2024-05-01&to=2024-05-07&count=3`

**응답 형식**: JSON
```json
{
  "success": true,
  "symbol": "AAPL",
  "from": "2024-05-01",
  "to": "2024-05-07",
  "count": 3,
  "data": [
    {
      "category": "technology",
      "datetime": 1715234400,
      "headline": "Apple Reports Q2 2024 Results",
      "id": 12345,
      "image": "https://example.com/news-image.jpg",
      "related": "AAPL",
      "source": "MarketWatch",
      "summary": "Apple reported better than expected earnings for Q2 2024...",
      "url": "https://example.com/apple-q2-results"
    },
    {
      "category": "business",
      "datetime": 1715148000,
      "headline": "Apple to Launch New Product Line",
      "id": 12346,
      "image": "https://example.com/news-image2.jpg",
      "related": "AAPL",
      "source": "CNBC",
      "summary": "Apple announced plans to launch a new product line...",
      "url": "https://example.com/apple-new-product"
    },
    {
      "category": "company",
      "datetime": 1715061600,
      "headline": "Apple Expands Operations in Asia",
      "id": 12347,
      "image": "https://example.com/news-image3.jpg",
      "related": "AAPL",
      "source": "Bloomberg",
      "summary": "Apple announced expansion of operations in several Asian markets...",
      "url": "https://example.com/apple-asia-expansion"
    }
  ],
  "message": "Successfully fetched 3 news items for AAPL"
}
```

**오류 응답**:
```json
{
  "success": false,
  "symbol": "AAPL",
  "from": "2024-05-01",
  "to": "2024-05-07",
  "error": "오류 메시지"
}
```

**주요 특징**:
- 날짜 범위 지정: 특정 기간 내의 뉴스만 필터링하여 가져올 수 있습니다.
- 항목 수 제한: `count` 파라미터를 사용하여 반환할 뉴스 항목 수를 제한할 수 있습니다.
- 오류 처리: API 호출 중 발생할 수 있는 다양한 오류(API 레이트 제한 등)를 적절히 처리합니다.

**뉴스 데이터 필드 설명**:
- `category`: 뉴스 카테고리 (예: technology, business)
- `datetime`: 뉴스 발행 시간(초 단위 epoch time)
- `headline`: 뉴스 헤드라인
- `id`: 뉴스 항목 ID
- `image`: 뉴스 이미지 URL
- `related`: 관련 주식 심볼
- `source`: 뉴스 소스(출처)
- `summary`: 뉴스 요약
- `url`: 뉴스 원문 URL

### 일반 시장 뉴스 데이터 API

#### `/api/stocks/info/market_news`

이 엔드포인트는 Finnhub API에서 일반 시장 뉴스 데이터를 가져옵니다. 지정된 기간 내의 뉴스 기사를 검색하며, 선택적으로 반환할 뉴스 항목 수를 제한할 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `from` (필수): 시작 날짜 (YYYY-MM-DD 형식)
- `to` (필수): 종료 날짜 (YYYY-MM-DD 형식)
- `count` (선택): 반환할 최대 뉴스 항목 수

**사용 예시**:
- 특정 기간의 시장 뉴스 가져오기: `/api/stocks/info/market_news?from=2024-05-01&to=2024-05-07`
- 특정 기간의 시장 뉴스를 최대 3개까지 가져오기: `/api/stocks/info/market_news?from=2024-05-01&to=2024-05-07&count=3`

**응답 형식**: JSON
```json
{
  "success": true,
  "from": "2024-05-01",
  "to": "2024-05-07",
  "count": 3,
  "data": [
    {
      "category": "general",
      "datetime": 1715234400,
      "headline": "Federal Reserve Holds Interest Rates Steady",
      "id": 12345,
      "image": "https://example.com/news-image.jpg",
      "related": "SPY,QQQ",
      "source": "Reuters",
      "summary": "The Federal Reserve announced it would maintain current interest rates...",
      "url": "https://example.com/fed-holds-rates"
    },
    {
      "category": "general",
      "datetime": 1715148000,
      "headline": "Global Markets React to Economic Data",
      "id": 12346,
      "image": "https://example.com/news-image2.jpg",
      "related": "DJI,SPX",
      "source": "Bloomberg",
      "summary": "Global markets showed mixed reaction to the latest economic indicators...",
      "url": "https://example.com/global-markets"
    },
    {
      "category": "general",
      "datetime": 1715061600,
      "headline": "Oil Prices Surge Amid Middle East Tensions",
      "id": 12347,
      "image": "https://example.com/news-image3.jpg",
      "related": "USO,XLE",
      "source": "Financial Times",
      "summary": "Crude oil prices increased by 3% as geopolitical tensions escalated...",
      "url": "https://example.com/oil-prices"
    }
  ],
  "message": "Successfully fetched 3 market news items"
}
```

**오류 응답**:
```json
{
  "success": false,
  "from": "2024-05-01",
  "to": "2024-05-07",
  "error": "오류 메시지"
}
```

**주요 특징**:
- 날짜 범위 지정: 특정 기간 내의 뉴스만 필터링하여 가져올 수 있습니다.
- 항목 수 제한: `count` 파라미터를 사용하여 반환할 뉴스 항목 수를 제한할 수 있습니다.
- 오류 처리: API 호출 중 발생할 수 있는 다양한 오류(API 레이트 제한 등)를 적절히 처리합니다.

**뉴스 데이터 필드 설명**:
- `category`: 뉴스 카테고리 (항상 "general")
- `datetime`: 뉴스 발행 시간(초 단위 epoch time)
- `headline`: 뉴스 헤드라인
- `id`: 뉴스 항목 ID
- `image`: 뉴스 이미지 URL
- `related`: 관련 주식 심볼 (여러 심볼이 쉼표로 구분됨)
- `source`: 뉴스 소스(출처)
- `summary`: 뉴스 요약
- `url`: 뉴스 원문 URL

### 주식 기본 재무 지표(Basic Financials) 데이터 수집 API

#### `/api/stocks/fetch/basic_financials`

이 엔드포인트는 Finnhub API에서 주식의 기본 재무 지표 데이터를 가져와 데이터베이스에 저장합니다. 특정 심볼 하나에 대한 재무 지표만 가져오거나, 모든 심볼에 대한 재무 지표를 일괄적으로 가져올 수 있습니다.

**메서드**: GET

**요청 파라미터**:
- `symbol` (선택): 특정 심볼 코드(예: "AAPL"). 입력 시 해당 심볼의 재무 지표만 가져옴
- `batchSize` (선택, 기본값: 20): 한 번에 처리할 주식 심볼 수 (symbol이 제공되지 않을 때)
- `delayMs` (선택, 기본값: 500): API 호출 사이의 지연 시간(밀리초) (symbol이 제공되지 않을 때)

**사용 예시**:
- 특정 심볼의 재무 지표만 가져오기: `/api/stocks/fetch/basic_financials?symbol=AAPL`
- 모든 심볼의 재무 지표 가져오기: `/api/stocks/fetch/basic_financials`
- 처리 배치 크기 및 지연 시간 지정: `/api/stocks/fetch/basic_financials?batchSize=10&delayMs=1000`

**응답 형식**: JSON
```json
// 특정 심볼의 재무 지표를 가져온 경우
{
  "success": true,
  "symbol": "AAPL",
  "data": {
    "symbol": "AAPL",
    "tenDayAverageTradingVolume": 54.66907,
    "threeMonthAverageTradingVolume": 58.01531,
    "thirteenWeekPriceReturnDaily": -15.7246,
    "twentySixWeekPriceReturnDaily": -14.053,
    "fiftyTwoWeekPriceReturnDaily": 14.9454,
    "fiveDayPriceReturnDaily": -5.8331,
    "yearToDatePriceReturnDaily": -20.5774,
    "monthToDatePriceReturnDaily": -6.4047,
    "fiftyTwoWeekHigh": 260.1,
    "fiftyTwoWeekHighDate": "2024-12-26",
    "fiftyTwoWeekLow": 169.2101,
    "fiftyTwoWeekLowDate": "2025-04-08",
    "beta": 1.2650821,
    "threeMonthADReturnStd": 50.260803,
    "priceToEarningsRatio": 30.7115,
    "priceToBookRatio": 50.0084,
    "priceToSalesRatio": 7.4633,
    "priceToCashFlowRatio": 27.2741,
    "returnOnEquity": 151.31,
    "returnOnAssets": 28.37,
    "returnOnInvestment": 58.95,
    "grossMarginTTM": 46.63,
    "operatingMarginTTM": 31.81,
    "netProfitMarginTTM": 24.3,
    "totalDebtToEquityQuarterly": 1.4699,
    "longTermDebtToEquityQuarterly": 1.1762,
    "dividendPerShareAnnual": 0.9935,
    "dividendYieldIndicatedAnnual": 0.5064524,
    "dividendGrowthRate5Y": 5.3,
    "revenueGrowth3Y": 2.25,
    "revenueGrowth5Y": 8.49,
    "epsGrowth3Y": 2.71,
    "epsGrowth5Y": 15.41,
    "bookValuePerShareAnnual": 3.7673,
    "cashPerSharePerShareAnnual": 4.3112,
    "currentRatioAnnual": 0.8673,
    "quickRatioAnnual": 0.826
  },
  "message": "Successfully fetched financial metrics for AAPL"
}

// 모든 심볼의 재무 지표를 처리한 경우
{
  "success": true,
  "processedCount": 71,
  "message": "Successfully processed financial metrics for 71 symbols"
}
```

**오류 응답**:
```json
{
  "success": false,
  "symbol": "AAPL", // symbol 파라미터 사용 시
  "error": "오류 메시지"
}
```

**주요 특징**:
- 배치 처리: 지정된 `batchSize`에 따라 재무 지표를 효율적으로 수집합니다.
- 트랜잭션 최적화: 각 배치마다 별도의 트랜잭션을 사용하여 데이터를 즉시 커밋합니다.
- API 호출 제한 관리: `delayMs` 파라미터로 API 호출 간격을 조절하여 API 사용량 제한을 준수합니다.
- 타임아웃 처리: API 응답이 늦어지는 경우 건너뛰고 다음 심볼로 진행합니다.
- 유효 데이터 검증: 빈 응답이나 유효하지 않은 데이터를 처리하지 않습니다.

**데이터 저장 위치**: `financial_metrics` 테이블

#### `/api/stocks/info/basic_financials`

이 엔드포인트는 데이터베이스에 저장된 특정 주식 심볼의 가장 최근 재무 지표 데이터를 조회합니다.

**메서드**: GET

**요청 파라미터**:
- `symbol` (필수): 조회할 주식 심볼 코드(예: "AAPL")

**사용 예시**:
- 특정 심볼의 저장된 재무 지표 조회: `/api/stocks/info/basic_financials?symbol=AAPL`

**응답 형식**: JSON
```json
{
  "success": true,
  "symbol": "AAPL",
  "data": {
    // 재무 지표 데이터 (위 예시와 동일)
  },
  "message": "Successfully retrieved financial metrics for AAPL"
}
```

**오류 응답**:
```json
{
  "success": false,
  "symbol": "AAPL",
  "message": "No financial metrics found for AAPL"
}
```


