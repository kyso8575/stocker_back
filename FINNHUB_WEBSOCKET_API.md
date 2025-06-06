# Finnhub WebSocket Real-time Trade Data API

이 프로젝트는 Finnhub 웹소켓을 사용하여 S&P 500 종목들의 실시간 거래 데이터를 수집하고 저장하는 Spring Boot 애플리케이션입니다.

## 주요 기능

### 1. 자동 WebSocket 연결
- 애플리케이션 시작 시 자동으로 Finnhub 웹소켓에 연결
- 연결 실패 시 10초 후 자동 재연결 시도
- API 호출 제한 준수 (25 calls/sec)

### 2. S&P 500 종목 실시간 구독
- 데이터베이스에 저장된 S&P 500 종목들을 자동으로 구독
- 실시간 거래 데이터 및 가격 업데이트 수신
- Volume이 0인 경우는 가격 업데이트로 처리

### 3. 데이터 저장 및 관리
- PostgreSQL 데이터베이스에 거래 데이터 저장
- 인덱스 최적화 (symbol, timestamp 기준)
- 7일 이후 오래된 데이터 자동 삭제

## API 엔드포인트

### WebSocket 관리

#### 웹소켓 연결 시작
```http
POST /api/trades/websocket/connect
```

응답 예시:
```json
{
  "status": "success",
  "message": "WebSocket connection initiated"
}
```

#### 웹소켓 연결 상태 확인
```http
GET /api/trades/websocket/status
```

응답 예시:
```json
{
  "connected": true,
  "timestamp": "2024-01-15T10:30:00"
}
```

#### 특정 심볼 구독
```http
POST /api/trades/websocket/subscribe/{symbol}
```

예시:
```http
POST /api/trades/websocket/subscribe/AAPL
```

응답 예시:
```json
{
  "status": "success",
  "message": "Subscribed to AAPL"
}
```

#### 특정 심볼 구독 해제
```http
POST /api/trades/websocket/unsubscribe/{symbol}
```

### 거래 데이터 조회

#### 특정 심볼의 최신 거래 데이터 조회
```http
GET /api/trades/{symbol}/latest?limit=10
```

예시:
```http
GET /api/trades/AAPL/latest?limit=5
```

응답 예시:
```json
[
  {
    "id": 1,
    "symbol": "AAPL",
    "price": 150.25,
    "volume": 100,
    "timestamp": 1705301400000,
    "receivedAt": "2024-01-15T10:30:00",
    "tradeConditions": "12,37"
  }
]
```

#### 특정 심볼의 최신 가격 조회
```http
GET /api/trades/{symbol}/latest-price
```

예시:
```http
GET /api/trades/AAPL/latest-price
```

응답 예시:
```json
{
  "symbol": "AAPL",
  "price": 150.25,
  "timestamp": "2024-01-15T10:30:00"
}
```

#### 시간 범위별 거래 데이터 조회
```http
GET /api/trades/range?startTime=2024-01-15T09:00:00&endTime=2024-01-15T10:00:00
```

#### 심볼별 거래 통계 조회
```http
GET /api/trades/statistics
```

응답 예시:
```json
[
  {
    "symbol": "AAPL",
    "tradeCount": 1250
  },
  {
    "symbol": "MSFT",
    "tradeCount": 987
  }
]
```

#### 모든 거래 데이터 페이징 조회
```http
GET /api/trades?page=0&size=20&sort=timestamp,desc
```

## 데이터베이스 스키마

### trades 테이블
```sql
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    price DECIMAL(19,6) NOT NULL,
    volume BIGINT NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP NOT NULL,
    trade_conditions TEXT
);

-- 인덱스
CREATE INDEX idx_symbol ON trades (symbol);
CREATE INDEX idx_timestamp ON trades (timestamp);
CREATE INDEX idx_symbol_timestamp ON trades (symbol, timestamp);
```

## 설정

### application.properties
```properties
# Finnhub WebSocket Configuration
finnhub.websocket.url=wss://ws.finnhub.io
finnhub.websocket.auto-connect=true
finnhub.websocket.reconnect-delay=10000
```

### application-secret.properties
```properties
# Finnhub API 키
finnhub.api.key=your_finnhub_api_key_here
```

## 자동 스케줄링

### 데이터 정리
- **매일 새벽 2시**: 7일 이전의 거래 데이터 자동 삭제
- **매시간**: 거래 데이터 통계 로깅

### API 호출 제한
- Finnhub API 제한에 맞춰 **25 calls/sec** 준수
- 초당 호출 수 카운터 자동 리셋

## Finnhub 웹소켓 메시지 형식

### 구독 메시지
```json
{
  "type": "subscribe",
  "symbol": "AAPL"
}
```

### 거래 데이터 수신
```json
{
  "type": "trade",
  "data": [
    {
      "s": "AAPL",      // 심볼
      "p": 150.25,      // 가격
      "v": 100,         // 거래량 (0이면 가격 업데이트)
      "c": ["12", "37"], // 거래 조건
      "t": 1705301400000 // 타임스탬프 (밀리초)
    }
  ]
}
```

## 실행 방법

1. PostgreSQL 데이터베이스 설정
2. `application-secret.properties`에 Finnhub API 키 설정
3. S&P 500 종목 데이터가 `stock_info` 테이블에 있어야 함 (`is_sp_500 = true`)
4. 애플리케이션 실행:
   ```bash
   ./gradlew bootRun
   ```

## 모니터링

애플리케이션 로그를 통해 다음 정보를 확인할 수 있습니다:
- 웹소켓 연결 상태
- 구독한 S&P 500 종목 수
- 받은 거래 데이터 수
- 데이터 저장 성공/실패
- 자동 정리 작업 결과

## 주의사항

1. **API 키 보안**: `application-secret.properties` 파일을 Git에 커밋하지 마세요
2. **데이터베이스 용량**: 거래 데이터가 많이 쌓일 수 있으므로 정기적인 모니터링 필요
3. **네트워크 연결**: 웹소켓 연결이 끊어질 경우 자동 재연결됨
4. **S&P 500 데이터**: `stock_info` 테이블에 S&P 500 종목이 미리 설정되어 있어야 함 