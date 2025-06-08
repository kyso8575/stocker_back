# 📈 Stocker Back - Real-time Stock Market Data Collection System

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-Real--time-red.svg)](https://finnhub.io/)

## 🚀 프로젝트 개요

**Stocker Back**은 S&P 500 주식의 실시간 거래 데이터를 수집하고 저장하는 Spring Boot 기반 백엔드 시스템입니다.

### ✨ 주요 특징

- 🕐 **시장 시간 기반 자동 관리**: 미국 시장 시간(9:30 AM - 4:00 PM ET)에만 데이터 수집
- 🔑 **멀티 API 키 지원**: 여러 Finnhub API 키로 더 많은 종목 모니터링 (최대 100개 종목)
- 📊 **고정된 알파벳 순서**: 일관된 심볼 구독으로 안정적인 데이터 수집
- ⚡ **설정 가능한 저장 간격**: 심볼별로 설정된 간격(기본 10초)으로 효율적 저장
- 🔄 **실시간 메모리 캐싱**: WebSocket 연결 유지하며 최신 데이터 메모리에 보관
- 🛠️ **설정 가능한 모니터링**: 연결 상태 모니터링 간격 조절 가능
- 💾 **효율적인 데이터 저장**: PostgreSQL 배치 처리로 성능 최적화
- 🔄 **자동 정리**: 7일 이상 된 데이터 자동 삭제

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Finnhub API   │    │  Spring Boot     │    │   PostgreSQL    │
│   (Multiple     │◄──►│  WebSocket       │◄──►│   Database      │
│   Keys)         │    │  Service         │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                       ┌──────▼──────┐
                       │ Scheduled   │
                       │ Management  │
                       │ + Memory    │
                       │ Cache       │
                       └─────────────┘
```

## 📋 목차

- [설치 및 실행](#-설치-및-실행)
- [환경 설정](#-환경-설정)
- [API 엔드포인트](#-api-엔드포인트)
- [시스템 구성 요소](#-시스템-구성-요소)
- [데이터베이스 스키마](#-데이터베이스-스키마)
- [모니터링](#-모니터링)
- [문제 해결](#-문제-해결)

## 🛠️ 설치 및 실행

### 사전 요구사항

- ☕ Java 17 이상
- 🐘 PostgreSQL 12 이상
- 🔑 Finnhub API 키 (최소 1개, 권장 2개)

### 1. 레포지토리 클론

```bash
git clone https://github.com/your-username/stocker_back.git
cd stocker_back
```

### 2. 데이터베이스 설정

```sql
-- PostgreSQL에서 실행
CREATE DATABASE stockerdb;
CREATE USER postgres WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE stockerdb TO postgres;
```

### 3. 환경 설정 파일 생성

`src/main/resources/application-secret.properties` 파일 생성:

```properties
# Database Configuration
spring.datasource.password=your_postgresql_password

# Finnhub API Keys (최소 1개 필수)
finnhub.api.key.1=your_first_finnhub_api_key
finnhub.api.key.2=your_second_finnhub_api_key
# finnhub.api.key.3=your_third_finnhub_api_key  # 선택사항
```

### 4. 애플리케이션 실행

```bash
# Gradle을 이용한 실행
./gradlew bootRun

# 또는 JAR 파일로 실행
./gradlew build
java -jar build/libs/stocker_back-0.0.1-SNAPSHOT.jar
```

## ⚙️ 환경 설정

### 주요 설정 값

```properties
# WebSocket 설정
finnhub.websocket.max-symbols=50              # API 키당 최대 구독 심볼 수

# 데이터 저장 설정
finnhub.websocket.save-interval-seconds=10    # 심볼별 저장 간격 (초)

# 스케줄링 설정
finnhub.scheduled.websocket.enabled=true      # 스케줄링된 서비스 활성화
finnhub.scheduled.websocket.monitor-interval-ms=10000  # 연결 모니터링 간격 (밀리초)

# 데이터베이스 최적화
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.show-sql=true                       # 개발시에만 true
```

### 📝 설정 커스터마이징

#### 저장 간격 조절
```properties
# 30초마다 저장
finnhub.websocket.save-interval-seconds=30

# 5초마다 저장 (더 빈번한 저장)
finnhub.websocket.save-interval-seconds=5
```

#### 모니터링 간격 조절
```properties
# 30초마다 연결 상태 체크
finnhub.scheduled.websocket.monitor-interval-ms=30000

# 5초마다 연결 상태 체크 (더 빈번한 모니터링)
finnhub.scheduled.websocket.monitor-interval-ms=5000
```

## 📡 API 엔드포인트

### 🔧 실시간 거래 데이터 및 WebSocket 관리

#### WebSocket 연결 관리
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/info/trades/websocket/status` | WebSocket 연결 상태 확인 |
| `GET` | `/api/stocks/info/trades/websocket/schedule_status` | 스케줄링된 WebSocket 상태 확인 |
| `POST` | `/api/stocks/info/trades/websocket/connect` | WebSocket 연결 시작 |
| `POST` | `/api/stocks/info/trades/websocket/disconnect` | WebSocket 연결 해제 |
| `POST` | `/api/stocks/info/trades/websocket/subscribe?symbol=AAPL` | 심볼 구독 (정보성) |
| `POST` | `/api/stocks/info/trades/websocket/unsubscribe?symbol=AAPL` | 심볼 구독 해제 (정보성) |

#### 거래 데이터 조회
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/info/trades/latest?symbol=All&limit=10` | 모든 심볼의 최신 거래 데이터 |
| `GET` | `/api/stocks/info/trades/latest?symbol=AAPL&limit=10` | 특정 심볼의 최신 거래 데이터 |
| `GET` | `/api/stocks/info/trades/range?startTime=2024-01-01T00:00:00&endTime=2024-01-02T00:00:00` | 시간 범위별 조회 |
| `GET` | `/api/stocks/info/trades/{symbol}/latest-price` | 특정 심볼의 최신 가격 |
| `GET` | `/api/stocks/info/trades/statistics` | 심볼별 거래 통계 |
| `GET` | `/api/stocks/info/trades/market-hours` | 미국 시장 시간 정보 |
| `POST` | `/api/stocks/info/trades/websocket/schedule-toggle?enabled=true` | 스케줄링 서비스 활성화/비활성화 |

#### 저장 상태 및 메모리 데이터 조회 ⭐ 새로운 기능
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/info/trades/websocket/save-status` | 심볼별 저장 상태 및 간격 정보 |
| `GET` | `/api/stocks/info/trades/websocket/latest-memory?symbol=All` | 메모리의 실시간 데이터 (모든 심볼) |
| `GET` | `/api/stocks/info/trades/websocket/latest-memory?symbol=AAPL` | 메모리의 실시간 데이터 (특정 심볼) |

### 📊 주식 데이터 관리

#### 데이터 수집 (fetch)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/stocks/symbols/batch?exchange=US` | 외부 API에서 모든 주식 심볼 배치 수집 |
| `POST` | `/api/stocks/symbols/{symbol}?exchange=US` | 외부 API에서 특정 주식 심볼 수집 |

#### 재무 지표 관리 ⭐ REST API 개선
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/stocks/financial-metrics/batch?batchSize=20&delayMs=500` | 모든 심볼의 재무 지표 수집 |
| `POST` | `/api/stocks/financial-metrics/{symbol}` | 특정 심볼의 재무 지표 수집 |
| `GET` | `/api/stocks/financial-metrics/{symbol}` | 특정 심볼의 최신 재무 지표 조회 |
| `GET` | `/api/stocks/financial-metrics/{symbol}/history?from=2024-01-01&to=2024-01-31` | 특정 심볼의 재무 지표 이력 조회 (날짜 범위) |

#### 회사 프로필 관리 ⭐ REST API 개선
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/stocks/company-profiles/batch?batchSize=20&delayMs=500` | 모든 심볼의 회사 프로필 수집 |
| `POST` | `/api/stocks/company-profiles/{symbol}` | 특정 심볼의 회사 프로필 수집 |
| `GET` | `/api/stocks/company-profiles/{symbol}` | 특정 심볼의 회사 프로필 조회 |

#### 데이터 조회 (info)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/info/basic_financials?symbol=AAPL` | 데이터베이스에서 재무 지표 조회 |

#### S&P 500 관리
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/stocks/update/sp500` | S&P 500 목록 웹스크래핑 업데이트 |
| `GET` | `/api/stocks/sp500` | S&P 500 심볼 목록 조회 |

### 📰 뉴스 데이터 ⭐ REST API 개선

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/news/companies/{symbol}?from=2024-01-01&to=2024-01-31&count=10` | 특정 회사 뉴스 조회 |
| `GET` | `/api/stocks/news/market?from=2024-01-01&to=2024-01-31&count=20` | 시장 전체 뉴스 조회 |

### 📝 API 응답 예시

#### WebSocket 연결 상태 확인
```json
{
  "connections": {
    "connection-1": true,
    "connection-2": true
  },
  "totalConnections": 2,
  "activeConnections": 2,
  "anyConnected": true,
  "description": "Multi-key WebSocket connection status for manual control",
  "timestamp": "2025-06-05T17:23:40.526983"
}
```

#### 스케줄링된 WebSocket 상태 확인
```json
{
  "enabled": true,
  "isMarketHours": false,
  "isConnected": false,
  "nextMarketEvent": "Market opens at: 2025-06-05T09:30-04:00[America/New_York]",
  "description": "Automated WebSocket management during US market hours (9:30 AM - 4:00 PM ET)",
  "monitoringInterval": "10 seconds",
  "timestamp": "2025-06-05T17:23:40.526983"
}
```

#### 최신 거래 데이터 조회 (모든 심볼)
```json
{
  "symbol": "All",
  "trades": [
    {
      "symbol": "AAPL",
      "latestTrade": {
        "id": 1234,
        "symbol": "AAPL",
        "price": 150.25,
        "volume": 100,
        "timestamp": 1705301400000,
        "receivedAt": "2024-01-15T10:30:00"
      },
      "tradeCount": 1500
    }
  ],
  "count": 50,
  "limit": 10,
  "description": "Latest trade data for all symbols",
  "timestamp": "2025-06-05T17:23:56.288587"
}
```

#### 재무 지표 수집 성공
```json
{
  "success": true,
  "symbol": "AAPL",
  "data": {
    "id": 1,
    "symbol": "AAPL",
    "peRatio": 28.5,
    "pbRatio": 6.2,
    "roeTTM": 0.875,
    "roaTTM": 0.275,
    "currentRatio": 1.07,
    "quickRatio": 0.98,
    "grossMarginTTM": 0.441,
    "operatingMarginTTM": 0.301,
    "createdAt": "2024-01-15T10:30:00"
  },
  "message": "Successfully fetched financial metrics for AAPL"
}
```

#### S&P 500 심볼 목록 조회
```json
{
  "success": true,
  "count": 503,
  "symbols": ["AAPL", "MSFT", "AMZN", "GOOGL", "..."],
  "message": "Found 503 S&P 500 symbols"
}
```

#### 회사 뉴스 조회
```json
{
  "success": true,
  "symbol": "AAPL",
  "from": "2024-01-01",
  "to": "2024-01-31",
  "data": [
    {
      "category": "technology",
      "datetime": 1705301400,
      "headline": "Apple Reports Q1 2024 Results",
      "id": "news_id_123",
      "image": "https://example.com/image.jpg",
      "related": "AAPL",
      "source": "Reuters",
      "summary": "Apple reported strong Q1 results...",
      "url": "https://example.com/news"
    }
  ],
  "count": 15,
  "message": "Successfully fetched 15 news items for AAPL"
}
```

#### 심볼별 저장 상태 조회 ⭐ 새로운 기능
```json
{
  "status": "success",
  "saveInterval": "10 seconds",
  "summary": {
    "totalSymbols": 95,
    "recentlySaved": 23,
    "pendingSave": 72,
    "saveIntervalSeconds": 10,
    "timestamp": "2024-01-15T14:30:25.123456"
  },
  "recentSaves": {
    "AAPL": "2024-01-15T14:30:20",
    "MSFT": "2024-01-15T14:30:18",
    "GOOGL": "2024-01-15T14:30:15"
  },
  "description": "Symbol-based save status with 10-second interval",
  "timestamp": "2024-01-15T14:30:25.123456"
}
```

#### 실시간 메모리 데이터 조회 ⭐ 새로운 기능
```json
{
  "status": "success",
  "symbol": "AAPL",
  "latestTrade": {
    "symbol": "AAPL",
    "price": 150.75,
    "volume": 1250,
    "conditions": ["12", "37"],
    "timestamp": 1705314600000
  },
  "source": "memory (real-time)",
  "description": "Latest trade data from WebSocket memory",
  "timestamp": "2024-01-15T14:30:25.123456"
}
```

#### 모든 심볼의 메모리 데이터 요약
```json
{
  "status": "success",
  "totalSymbols": 95,
  "samples": [
    {
      "symbol": "AAPL",
      "price": 150.75,
      "volume": 1250,
      "timestamp": 1705314600000
    },
    {
      "symbol": "MSFT",
      "price": 375.20,
      "volume": 2100,
      "timestamp": 1705314598000
    }
  ],
  "source": "memory (real-time)",
  "description": "Latest trade data from WebSocket memory (top 20 symbols)",
  "timestamp": "2024-01-15T14:30:25.123456"
}
```

### 📄 API 사용 팁

#### 배치 처리 최적화
```bash
# 작은 배치로 시작 (API 제한 고려)
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/batch?batchSize=5&delayMs=1000"

# 재무 지표 배치 수집 (모든 심볼)
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/batch?batchSize=20&delayMs=500"

# 특정 심볼의 재무 지표 수집
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/AAPL"

# 특정 심볼의 최신 재무 지표 조회
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL"

# 특정 심볼의 재무 지표 이력 조회
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL/history"

# 특정 기간의 재무 지표 이력 조회 (날짜만)
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL/history?from=2024-01-01&to=2024-01-31"

# 특정 기간의 재무 지표 이력 조회 (날짜+시간)
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL/history?from=2024-01-01T09:00:00&to=2024-01-31T18:00:00"

# 시작 날짜만 지정 (해당 날짜부터 현재까지)
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL/history?from=2024-01-01"

# 종료 날짜만 지정 (1년 전부터 해당 날짜까지)
curl "http://localhost:8080/api/stocks/financial-metrics/AAPL/history?to=2024-01-31"

# 재무 지표 통계 조회
curl "http://localhost:8080/api/stocks/financial-metrics/statistics"

# 회사 프로필 배치 수집 (모든 심볼)
curl -X POST "http://localhost:8080/api/stocks/company-profiles/batch?batchSize=5&delayMs=1000"

# 특정 심볼의 회사 프로필 수집
curl -X POST "http://localhost:8080/api/stocks/company-profiles/AAPL"

# 특정 심볼의 회사 프로필 조회
curl "http://localhost:8080/api/stocks/company-profiles/AAPL"

# 회사 프로필 통계 조회
curl "http://localhost:8080/api/stocks/company-profiles/statistics"

# 뉴스 데이터 조회
# 특정 회사 뉴스 조회
curl "http://localhost:8080/api/stocks/news/companies/AAPL?from=2024-01-01&to=2024-01-31&count=10"

# 시장 전체 뉴스 조회
curl "http://localhost:8080/api/stocks/news/market?from=2024-01-01&to=2024-01-31&count=20"

# 뉴스 통계 정보 조회
curl "http://localhost:8080/api/stocks/news/statistics?from=2024-01-01&to=2024-01-31"

# 최근 30일 회사 뉴스 (count 생략)
curl "http://localhost:8080/api/stocks/news/companies/MSFT?from=2024-01-01&to=2024-01-31"

#### 주식 심볼 관리 ⭐ REST API 개선
```bash
# 주식 심볼 데이터 배치 수집 (모든 심볼)
curl -X POST "http://localhost:8080/api/stocks/symbols/batch?exchange=US"

# 특정 심볼만 수집
curl -X POST "http://localhost:8080/api/stocks/symbols/AAPL?exchange=US"
```

#### WebSocket 모니터링
```bash
# WebSocket 연결 상태 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/status

# 스케줄링된 WebSocket 상태 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/schedule_status

# 시장 시간 확인
curl http://localhost:8080/api/stocks/info/trades/market-hours

# 거래 통계 확인
curl http://localhost:8080/api/stocks/info/trades/statistics

# S&P 500 심볼 목록 확인
curl http://localhost:8080/api/stocks/sp500
```

#### 저장 상태 및 실시간 데이터 모니터링 ⭐

```bash
# 심볼별 저장 상태 확인 (10초 간격 정보)
curl http://localhost:8080/api/stocks/info/trades/websocket/save-status

# 실시간 메모리 데이터 확인 (모든 심볼)
curl http://localhost:8080/api/stocks/info/trades/websocket/latest-memory

# 특정 심볼의 실시간 데이터
curl "http://localhost:8080/api/stocks/info/trades/websocket/latest-memory?symbol=AAPL"

# 저장 간격 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/save-status | jq '.saveInterval'
```

#### 오류 처리
- 모든 API는 `success` 필드로 성공/실패 표시
- 실패시 `error` 필드에 상세 오류 메시지 제공
- HTTP 상태 코드와 함께 적절한 오류 응답 반환

## 🔧 시스템 구성 요소

### 핵심 서비스

#### 1. `ScheduledWebSocketService`
- 🕐 **시장 시간 모니터링**: 매분마다 미국 시장 시간 체크
- 🔄 **자동 연결 관리**: 개장시 연결, 마감시 해제
- ⚡ **설정 가능한 모니터링**: 프로퍼티로 설정된 간격으로 연결 상태 확인

#### 2. `MultiKeyFinnhubWebSocketService`
- 🔑 **멀티 API 키 관리**: 여러 키로 더 많은 종목 커버
- 📊 **고정 구독**: 알파벳 순으로 일관된 심볼 할당
- 🔄 **자동 재연결**: 연결 끊김시 자동 복구
- 💾 **설정 가능한 저장 간격**: 심볼별로 프로퍼티 설정된 간격으로 저장
- 🧠 **실시간 메모리 캐싱**: 최신 거래 데이터를 메모리에 실시간 유지

#### 3. `TradeCleanupService`
- 🗑️ **자동 정리**: 7일 이상 된 데이터 삭제
- 📈 **통계 수집**: 시간당 거래 데이터 현황 리포트

### 데이터 플로우

```
Finnhub WebSocket → Message Handler → Memory Cache Update (실시간) → Interval Check → Database Save
                                           ↓
                               Real-time Memory Query (API)
                                           ↓
                               Trade Cleanup Service (Daily)
```

### 💾 메모리 vs 데이터베이스 저장 전략

```
📡 WebSocket 수신: 실시간 (항상)
🧠 메모리 업데이트: 실시간 (항상) → API 즉시 조회 가능
💾 DB 저장: 설정된 간격 (기본 10초) → 영구 보관용
🗑️ 데이터 정리: 매일 자동 (7일 이상)
```

## 🗄️ 데이터베이스 스키마

### `trades` 테이블
```sql
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    price DECIMAL(15,4) NOT NULL,
    volume BIGINT NOT NULL,
    timestamp BIGINT NOT NULL,
    trade_conditions TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 인덱스
    INDEX idx_trades_symbol (symbol),
    INDEX idx_trades_timestamp (timestamp),
    INDEX idx_trades_received_at (received_at)
);
```

### `stock_symbols` 테이블
```sql
CREATE TABLE stock_symbols (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) UNIQUE NOT NULL,
    description TEXT,
    display_symbol VARCHAR(20),
    type VARCHAR(50),
    is_sp_500 BOOLEAN DEFAULT FALSE,
    
    INDEX idx_stock_symbols_sp500 (is_sp_500)
);
```

## 📊 모니터링

### 시스템 상태 확인

```bash
# WebSocket 연결 상태 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/status

# 스케줄링된 WebSocket 상태 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/schedule_status

# 시장 시간 확인
curl http://localhost:8080/api/stocks/info/trades/market-hours

# 거래 통계 확인
curl http://localhost:8080/api/stocks/info/trades/statistics

# S&P 500 심볼 목록 확인
curl http://localhost:8080/api/stocks/sp500
```

### 로그 모니터링

```bash
# 애플리케이션 로그 확인
tail -f logs/spring.log

# WebSocket 연결 상태
grep "WebSocket" logs/spring.log | tail -20

# 거래 데이터 저장 상태
grep "Saved.*trades" logs/spring.log | tail -10
```

### 저장 상태 및 실시간 데이터 모니터링 ⭐

```bash
# 심볼별 저장 상태 확인 (10초 간격 정보)
curl http://localhost:8080/api/stocks/info/trades/websocket/save-status

# 실시간 메모리 데이터 확인 (모든 심볼)
curl http://localhost:8080/api/stocks/info/trades/websocket/latest-memory

# 특정 심볼의 실시간 데이터
curl "http://localhost:8080/api/stocks/info/trades/websocket/latest-memory?symbol=AAPL"

# 저장 간격 확인
curl http://localhost:8080/api/stocks/info/trades/websocket/save-status | jq '.saveInterval'
```

## 🐛 문제 해결

### 자주 발생하는 문제

#### 1. WebSocket 연결 실패
```
❌ Failed to connect WebSocket [connection-1]
```
**해결책**: API 키 확인 및 네트워크 연결 상태 점검

#### 2. 시장 시간 외 데이터 없음
```
isMarketHours: false
```
**해결책**: 정상 동작. 미국 시장 시간(9:30 AM - 4:00 PM ET)에만 데이터 수집

#### 3. 저장 간격이 예상과 다름
```
💾 Saving trade for symbol: AAPL (interval: 30s)
```
**해결책**: `application.properties`에서 `finnhub.websocket.save-interval-seconds` 설정 확인

#### 4. 메모리 데이터는 있지만 DB에 저장되지 않음
```
⏭️ Skipping save for symbol: AAPL (last saved: 5s ago, need: 10s)
```
**해결책**: 정상 동작. 설정된 간격이 지나야 DB에 저장됨

#### 5. 데이터베이스 연결 오류
```
❌ Failed to save trades from [connection-1]
```
**해결책**: PostgreSQL 서비스 상태 및 연결 정보 확인

### 디버깅 모드 활성화

```properties
# application.properties에 추가
logging.level.com.stocker_back.stocker_back.service=DEBUG
logging.level.org.java_websocket=DEBUG

# 저장 관련 디버깅 (더 자세한 로그)
logging.level.com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService=TRACE
```

### 📊 성능 최적화 설정

```properties
# 메모리 사용량 최적화
finnhub.websocket.save-interval-seconds=30    # 저장 빈도 줄이기

# 모니터링 부하 줄이기
finnhub.scheduled.websocket.monitor-interval-ms=30000  # 모니터링 간격 늘리기

# 배치 크기 조정
spring.jpa.properties.hibernate.jdbc.batch_size=100
```

## 🔮 향후 계획

- [ ] 📈 **실시간 대시보드**: WebSocket을 통한 프론트엔드 연동
- [ ] 🔔 **알림 시스템**: 가격 변동 알림 기능
- [ ] 📊 **기술적 지표**: 이동평균, RSI 등 계산 기능
- [ ] 🌐 **API 확장**: RESTful API 추가 기능
- [ ] 🧠 **메모리 캐싱 최적화**: Redis 기반 분산 캐싱
- [ ] 📊 **실시간 분석**: 메모리 데이터 기반 실시간 통계
- [ ] 🐳 **Docker 지원**: 컨테이너화 배포

## 🤝 기여

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 📞 문의

프로젝트 관련 문의사항이나 버그 리포트는 [Issues](https://github.com/your-username/stocker_back/issues)를 통해 제보해주세요.

---

<div align="center">

**⭐ 이 프로젝트가 유용하다면 스타를 눌러주세요! ⭐**

</div>


