# Stocker - 주식 데이터 수집 및 관리 시스템

Stocker는 Finnhub API를 활용하여 주식 데이터를 수집하고 관리하는 시스템입니다. 미국 주식 심볼 데이터를 가져오고, 개별 주식에 대한 상세 프로필 정보를 조회할 수 있습니다.

## 데이터 수집 프로세스

Stocker는 다음과 같은 단계로 주식 데이터를 수집합니다:

1. **주식 심볼 수집**: 미국 주식 시장의 모든 심볼(티커) 데이터를 Finnhub API에서 가져옵니다.
2. **주식 프로필 수집**: 각 심볼에 대한 상세 프로필 정보(국가, 시가총액, 발행주식수 등)를 수집합니다.
3. **실시간 시세 조회**: 개별 주식의 실시간 시세 정보(현재가, 변동폭, 고가, 저가 등)를 조회합니다.

## API 엔드포인트

### 인증 관련 엔드포인트

#### 회원가입
```
POST /api/auth/signup
```

**요청 본문**:
```json
{
  "username": "사용자이름",
  "email": "이메일@example.com",
  "password": "비밀번호"
}
```

**응답 예시** (성공):
```json
{
  "success": true,
  "username": "사용자이름",
  "message": "회원가입이 완료되었습니다."
}
```

**응답 예시** (실패):
```json
{
  "success": false,
  "message": "이미 사용 중인 사용자명입니다: 사용자이름"
}
```


#### RESTful API용 로그인
```
POST /api/auth/login-rest
```

이 엔드포인트는 RESTful API 클라이언트용 로그인을 위한 것입니다.

**요청 본문**:
```json
{
  "username": "사용자이름",
  "password": "비밀번호"
}
```

**응답 예시** (성공):
```json
{
  "success": true,
  "username": "사용자이름",
  "sessionId": "세션ID",
  "message": "로그인에 성공했습니다."
}
```

**응답 예시** (실패):
```json
{
  "success": false,
  "message": "로그인에 실패했습니다. 사용자명과 비밀번호를 확인해주세요."
}
```

#### 로그인 상태 확인
```
GET /api/auth/status
```

**응답 예시** (로그인 상태):
```json
{
  "success": true,
  "username": "사용자이름",
  "message": "로그인 상태입니다."
}
```

**응답 예시** (비로그인 상태):
```json
{
  "success": false,
  "message": "로그인되지 않은 상태입니다."
}
```

#### 로그아웃 상태 확인
```
GET /api/auth/status
```

#### RESTful API용 로그아웃
```
POST /api/auth/logout-rest
```

#### 사용자 정보 조회
```
GET /api/users/{username}
```

이 엔드포인트는 특정 사용자의 정보를 조회합니다. 인증된 사용자만 자신의 정보를 조회할 수 있습니다.

**응답 예시** (성공):
```json
{
  "success": true,
  "data": {
    "username": "사용자이름",
    "email": "이메일@example.com",
    "role": "USER",
    "createdAt": "2023-04-15T10:30:45",
    "lastLogin": "2023-04-16T08:15:22"
  },
  "message": "사용자 정보를 성공적으로 조회했습니다."
}
```

**응답 예시** (실패 - 사용자를 찾을 수 없음):
```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다: 존재하지않는사용자"
}
```

**응답 예시** (실패 - 권한 없음):
```json
{
  "success": false,
  "message": "다른 사용자의 정보를 조회할 권한이 없습니다."
}
```

#### 마지막 로그인 시간 업데이트
```
PUT /api/users/{username}/last-login
```

이 엔드포인트는 사용자의 마지막 로그인 시간을 현재 시간으로 업데이트합니다. 주로 내부적으로 사용되며, 로그인 프로세스의 일부로 자동 호출됩니다.

**응답 예시** (성공):
```json
{
  "success": true,
  "message": "마지막 로그인 시간이 업데이트되었습니다."
}
```

**응답 예시** (실패 - 사용자를 찾을 수 없음):
```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다: 존재하지않는사용자"
}
```

#### 비밀번호 변경
```
POST /api/users/{username}/change-password
```

이 엔드포인트는 사용자의 비밀번호를 변경합니다. 인증된 사용자만 자신의 비밀번호를 변경할 수 있습니다.

**요청 본문**:
```json
{
  "currentPassword": "현재비밀번호",
  "newPassword": "새비밀번호"
}
```

**응답 예시** (성공):
```json
{
  "success": true,
  "message": "비밀번호가 성공적으로 변경되었습니다."
}
```

**응답 예시** (실패 - 현재 비밀번호 불일치):
```json
{
  "success": false,
  "message": "현재 비밀번호가 일치하지 않습니다."
}
```

**응답 예시** (실패 - 사용자를 찾을 수 없음):
```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다: 존재하지않는사용자"
}
```

**응답 예시** (실패 - 권한 없음):
```json
{
  "success": false,
  "message": "다른 사용자의 비밀번호를 변경할 권한이 없습니다."
}
```

### 주식 심볼 데이터 수집

미국 주식 시장의 모든 주식 심볼을 Finnhub API에서 가져와 데이터베이스에 저장합니다.

```
GET /api/stocks/fetch/symbols
```

**응답 예시**:
```
주식 심볼 데이터 가져오기 완료. 8742개의 새로운 주식 데이터가 저장되었습니다.
```

### 모든 주식 프로필 수집

모든 주식 심볼에 대한 상세 프로필 정보를 수집합니다.

```
GET /api/stocks/fetch/profiles
```

**파라미터**:
- `batchSize`: 한 번에 처리할 주식 수 (기본값: 20)
- `delayMs`: API 호출 사이의 지연 시간(밀리초) (기본값: 200)

**사용 예시**:
```
curl 'http://localhost:8080/api/stocks/fetch/profiles?batchSize=20&delayMs=200'
```

**응답 예시**:
```
주식 상세 정보 업데이트 완료. 1250개의 주식 상세 정보가 업데이트되었습니다.
```

### Country가 Null인 주식 프로필 수집

Country 필드가 null인 주식에 대해서만 상세 프로필 정보를 수집합니다.

```
GET /api/stocks/fetch/profiles/null-country
```

**파라미터**:
- `batchSize`: 한 번에 처리할 주식 수 (기본값: 20)
- `delayMs`: API 호출 사이의 지연 시간(밀리초) (기본값: 500)

**사용 예시**:
```
curl 'http://localhost:8080/api/stocks/fetch/profiles/null-country?batchSize=30&delayMs=300'
```

**응답 예시**:
```
Country가 null인 주식 상세 정보 업데이트 완료. 840개의 주식 상세 정보가 업데이트되었습니다.
```

### 주식 실시간 시세 조회

특정 주식 심볼의 실시간 시세 정보를 Finnhub API에서 가져와 데이터베이스에 저장하고 반환합니다.

```
GET /api/stocks/fetch/quotes/{symbol}
```

**파라미터**:
- `symbol`: 주식 심볼 (예: AAPL, MSFT, GOOGL)

**사용 예시**:
```
curl 'http://localhost:8080/api/stocks/fetch/quotes/AAPL'
```

**응답 예시**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "symbol": "AAPL",
    "currentPrice": 182.63,
    "change": 1.25,
    "percentChange": 0.69,
    "high": 183.12,
    "low": 180.44,
    "open": 181.27,
    "previousClose": 181.38,
    "timestamp": 1650384000
  },
  "message": "Successfully fetched quote for AAPL"
}
```

**오류 응답**:
- 주식을 찾을 수 없는 경우 (404 Not Found)
```json
{
  "success": false,
  "error": "Stock with ticker XYZ not found",
  "message": "Stock not found: XYZ"
}
```

- API 호출 제한에 도달한 경우 (500 Internal Server Error)
```json
{
  "success": false,
  "error": "API rate limit exceeded. Please try again later.",
  "message": "Error fetching quote for AAPL"
}
```

> **참고**: 기존 `/api/stocks/quote/{ticker}` 엔드포인트도 계속 지원되지만, 새 엔드포인트 사용을 권장합니다.

### 모든 주식 실시간 시세 조회

모든 주식의 실시간 시세 정보를 Finnhub API에서 가져와 데이터베이스에 저장하고 반환합니다.

```
GET /api/stocks/fetch/quotes/all
```

**파라미터**:
- `batchSize`: 한 번에 처리할 주식 수 (기본값: 20)
- `delayMs`: API 호출 사이의 지연 시간(밀리초) (기본값: 300)

**사용 예시**:
```
curl 'http://localhost:8080/api/stocks/fetch/quotes/all?batchSize=10&delayMs=500'
```

**응답 예시**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "symbol": "AAPL",
      "currentPrice": 182.63,
      "change": 1.25,
      "percentChange": 0.69,
      "high": 183.12,
      "low": 180.44,
      "open": 181.27,
      "previousClose": 181.38,
      "timestamp": 1650384000
    },
    {
      "id": 2,
      "symbol": "MSFT",
      "currentPrice": 327.81,
      "change": 2.33,
      "percentChange": 0.72,
      "high": 329.10,
      "low": 325.55,
      "open": 326.12,
      "previousClose": 325.48,
      "timestamp": 1650384000
    }
    // ... 더 많은 주식 데이터
  ],
  "totalCount": 1250,
  "message": "Successfully fetched quotes for 1250 stocks"
}
```

**주의사항**:
- 이 엔드포인트는 모든 주식 데이터를 처리하므로 실행 시간이 길고 API 호출 제한에 도달할 수 있습니다.
- 대량의 데이터를 처리할 때는 `batchSize`와 `delayMs` 값을 적절히 조정하세요.
- 여러 페이지에 걸쳐 데이터를 순차적으로 처리합니다.

### 주식 상세 프로필 정보 조회

특정 주식 심볼의 상세 프로필 정보를 조회합니다. 이 정보에는 회사명, 국가, 통화, 시가총액, 발행주식수 등의 기업 정보가 포함됩니다.

```
GET /api/stocks/detail/{ticker}
```

**파라미터**:
- `ticker`: 주식 심볼 (예: AAPL, MSFT, GOOGL)

**사용 예시**:
```
curl 'http://localhost:8080/api/stocks/detail/AAPL'
```

**응답 예시**:
```json
{
  "success": true,
  "data": {
    "symbol": "AAPL",
    "name": "Apple Inc",
    "country": "US",
    "currency": "USD",
    "exchange": "NASDAQ NMS - GLOBAL MARKET",
    "ipo": "1980-12-12",
    "marketCapitalization": 2851357.2,
    "shareOutstanding": 15634.2,
    "industry": "Consumer Electronics",
    "sector": "Technology",
    "phone": "14089961010",
    "weburl": "https://www.apple.com/",
    "logo": "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL.svg",
    "finnhubIndustry": "Technology"
  },
  "message": "Successfully fetched details for AAPL"
}
```

**오류 응답**:
- 주식을 찾을 수 없는 경우 (404 Not Found)
```json
{
  "success": false,
  "error": "Stock with ticker XYZ not found",
  "message": "Stock not found: XYZ"
}
```

- 해당 주식의 상세 정보가 없는 경우 (404 Not Found)
```json
{
  "success": false,
  "error": "No profile data available for ticker ABC",
  "message": "Profile data not found: ABC"
}
```

## 데이터 수집 순서와 최적 사용법

데이터를 효율적으로 수집하기 위한 권장 순서:

1. 먼저 `/api/stocks/fetch/symbols`로 기본 주식 심볼 데이터를 수집합니다.
2. 다음으로 `/api/stocks/fetch/profiles`로 모든 주식에 대한 상세 정보를 수집합니다.
   - 대량의 데이터가 있을 경우 `batchSize`를 적절히 조정합니다.
   - API 제한을 고려하여 `delayMs`를 충분히 설정합니다(최소 200ms 권장).
3. 필요에 따라 `/api/stocks/fetch/profiles/null-country`를 통해 누락된 country 정보를 보완합니다.
4. 주식의 실시간 시세 정보가 필요한 경우 `/api/stocks/fetch/quotes/{symbol}`를 통해 개별 주식의 시세를 조회합니다.

## 인증 및 권한

Stocker는 세션 기반 인증 시스템을 제공합니다. 인증 세션은 데이터베이스에 저장되어 관리됩니다.

### 웹 브라우저 인증

1. 사용자는 `/signup` 페이지를 통해 회원가입할 수 있습니다.
2. 회원가입 후 `/login` 페이지에서 로그인합니다.
3. 로그인에 성공하면 세션이 생성되고, 이후 인증이 필요한 페이지에 접근할 수 있습니다.
4. 로그아웃은 `/api/auth/logout` 엔드포인트를 호출하여 처리됩니다.

### RESTful API 인증

API 클라이언트의 경우 다음과 같은 방식으로 인증할 수 있습니다:

1. `/api/auth/signup` 엔드포인트를 통해 회원가입합니다.
2. `/api/auth/login-rest` 엔드포인트를 통해 로그인하여 세션 ID를 발급받습니다.
3. 발급받은 세션 ID를 쿠키에 저장하고 이후 API 요청시 쿠키와 함께 요청합니다.
4. `/api/auth/status` 엔드포인트를 통해 로그인 상태를 확인할 수 있습니다.
5. 로그아웃은 `/api/auth/logout-rest` 엔드포인트를 호출하여 처리합니다.

### 세션 사용 예시 (curl)

```
# 로그인 
curl -X POST -H "Content-Type: application/json" -d '{"username":"사용자명","password":"비밀번호"}' http://localhost:8080/api/auth/login-rest -c cookies.txt

# 세션 쿠키를 사용하여 인증이 필요한 API 호출
curl -b cookies.txt http://localhost:8080/api/stocks/fetch/symbols

# 로그아웃
curl -X POST -b cookies.txt http://localhost:8080/api/auth/logout-rest
```

## 주의사항

- Finnhub API는 호출 제한이 있으므로, 대량의 데이터를 수집할 때는 `delayMs` 값을 충분히 크게 설정하세요.
- 데이터 수집은 ID 순서대로 이루어지며, 로그를 통해 진행 상황을 확인할 수 있습니다.
- API 호출 제한에 도달했을 경우(HTTP 429), 시스템은 자동으로 1.2초 대기 후 최대 3회까지 재시도합니다.
- 실시간 시세 조회는 존재하는 주식 심볼에 대해서만 가능합니다. 따라서 심볼 데이터를 먼저 수집해야 합니다.
- 일부 API 엔드포인트는 인증된 사용자만 접근할 수 있으므로, 먼저 회원가입 및 로그인을 통해 세션을 생성해야 합니다.
- 세션은 기본적으로 1시간 후 만료되며, 서버 재시작 시에도 세션 정보는 데이터베이스에서 유지됩니다.
