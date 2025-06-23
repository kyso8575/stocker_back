# 📈 Stocker Back - 실시간 주식 데이터 API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)

## 🚀 프로젝트 개요

S&P 500 주식의 실시간 거래 데이터를 수집하고 RESTful API로 제공하는 시스템입니다.

### ✨ 주요 기능

- 🕐 **스마트한 시장 시간 관리**: 
  - **Pre-market (9:00 AM ET)**: WebSocket 연결 및 구독 완료
  - **Market Hours (9:30 AM - 4:00 PM ET)**: 실시간 데이터 저장
  - **After Hours**: 연결 해제로 리소스 절약
- 🤖 **완전 자동화**: 매일 9:00 AM ET에 S&P 500 재무 지표 자동 수집
- 🔑 **멀티 API 키 지원**: 여러 Finnhub API 키로 더 많은 종목 모니터링 (최대 100개 종목)
- ⚡ **최적화된 Rate Limiting**: 60 calls/minute으로 API 제한 준수
- 📊 **고정된 알파벳 순서**: 일관된 심볼 구독으로 안정적인 데이터 수집
- 💾 **지능적인 데이터 저장**: 
  - Pre-market: 연결 유지, 저장 안함
  - Market Hours: 심볼별 10초 간격 저장
- 🔄 **실시간 메모리 캐싱**: WebSocket 연결 유지하며 최신 데이터 메모리에 보관
- 🛠️ **설정 가능한 모니터링**: 연결 상태 모니터링 간격 조절 가능
- 💾 **효율적인 데이터 저장**: PostgreSQL 배치 처리로 성능 최적화
- 🔄 **자동 정리**: 7일 이상 된 데이터 자동 삭제
- 🌐 **완전한 REST API**: 25개의 RESTful 엔드포인트 제공
- 📡 **SSE 실시간 스트리밍**: Server-Sent Events로 브라우저 실시간 데이터 전송
- 📈 **포괄적인 데이터 관리**: 심볼, 재무지표, 회사프로필, 뉴스, 시세 데이터 통합 관리
- 🔒 **중복 방지**: 스마트한 중복 데이터 방지 및 명확한 응답 메시지

## 🆕 최신 업데이트 (v2.2.0)

### 🎯 자동 스케줄링 시스템 확장
- **Financial Metrics 자동 수집**: 매일 9:00 AM ET에 S&P 500 재무 지표 자동 수집
- **Quote Data 자동 수집**: 매일 4:30 PM ET에 S&P 500 종가 데이터 자동 수집 ⭐ **NEW**
- **WebSocket Pre-market Setup**: 시장 개장 30분 전 연결 및 구독 완료
- **데이터 손실 방지**: 시장 개장 즉시 데이터 저장 시작

### ⚡ Rate Limit 최적화
- **변경**: 25 requests/second → **60 requests/minute** (1000ms 간격)
- **API 클라이언트 자동 제어**: 모든 API 호출에 자동 적용
- **배치 작업 최적화**: delayMs 기본값 500ms → 0ms (API 클라이언트에서 처리)

### 🔧 새로운 스케줄러 관리 API
- **통합 상태 모니터링**: Financial Metrics + Quote Data + Monthly Data + WebSocket 스케줄러 통합 관리
- **실시간 상태 확인**: Pre-market, Market Hours, Data Saving, Monthly Collection 상태 실시간 조회
- **4개 스케줄러 통합**: 일일 재무지표, 일일 시세, 월간 데이터, WebSocket 관리를 하나의 API로 모니터링

### 📊 Quote Data API 추가
- **S&P 500 시세 일괄 수집**: `/api/quote/admin/sp500` (POST)
- **단일 주식 시세 수집**: `/api/quote/admin/symbol/{symbol}` (POST)
- **자동 스케줄링**: 매일 4:30 PM ET에 S&P 500 종가 데이터 자동 수집

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Finnhub API   │    │  Spring Boot     │    │   PostgreSQL    │
│   (60/min Rate  │◄──►│  WebSocket       │◄──►│   Database      │
│   Limited)      │    │  Service         │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                       ┌──────▼──────┐
                       │ Automated   │
                       │ Scheduler   │
                       │ (9:00AM ET) │
                       │ + Memory    │
                       │ Cache       │
                       └─────────────┘
```

## 📋 목차

- [설치 및 실행](#-설치-및-실행)
- [환경 설정](#-환경-설정)
- [API 엔드포인트](#-api-엔드포인트)
- [자동 스케줄링](#-자동-스케줄링)
- [시스템 구성 요소](#-시스템-구성-요소)
- [데이터베이스 스키마](#-데이터베이스-스키마)
- [모니터링](#-모니터링)
- [문제 해결](#-문제-해결)

## 🛠️ 설치 및 실행

### 사전 요구사항
- Java 17 이상
- PostgreSQL 12 이상  
- Finnhub API 키 (무료 계정 가능)

### 1. 데이터베이스 설정
```sql
CREATE DATABASE stockerdb;
CREATE USER postgres WITH ENCRYPTED PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE stockerdb TO postgres;
```

### 2. 환경 설정
`src/main/resources/application-secret.properties` 파일 생성:
```properties
spring.datasource.password=your_postgresql_password
finnhub.api.key.1=your_finnhub_api_key
```

### 3. 실행
```bash
./gradlew bootRun
```

## 📡 API 엔드포인트

### 🔧 실시간 거래 데이터
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/trades/latest/{symbol}` | 최신 거래 데이터 |
| `GET` | `/api/trades/{symbol}/price` | 최신 가격 |
| `GET` | `/api/trades/history` | 거래 이력 (시간 범위) |
| `GET` | `/api/trades/stream/{symbol}` | 실시간 SSE 스트리밍 |

### 🔌 WebSocket 관리
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/trades/websocket/status` | 연결 상태 확인 |
| `POST` | `/api/trades/websocket/admin/connect` | 연결 시작 |
| `POST` | `/api/trades/websocket/admin/disconnect` | 연결 해제 |

### 🤖 자동 스케줄러 관리 (NEW!)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/scheduler/status` | **완전 통합 상태 조회** (헬스 + 설정 + Financial Metrics + Quote Data + WebSocket) |

### 📊 주식 데이터 관리
| 메서드 | 엔드포인트 | 설명 | 변경사항 |
|--------|-----------|------|----------|
| `POST` | `/api/symbols/batch` | 모든 주식 심볼 수집 | |
| `POST` | `/api/symbols/{symbol}` | 특정 심볼 수집 | |
| `POST` | `/api/financial-metrics/admin/batch` | 모든 심볼 재무지표 배치 수집 | 🔄 delayMs 기본값: 500ms → 0ms |
| `POST` | `/api/financial-metrics/admin/sp500` | S&P 500 재무지표 수집 | 🔄 delayMs 기본값: 500ms → 0ms |
| `GET` | `/api/financial-metrics/{symbol}` | 재무지표 조회 | |
| `GET` | `/api/financial-metrics/sp500` | S&P 500 재무지표 조회 (오늘 또는 최근) | |
| `POST` | `/api/company-profiles/admin/batch` | 회사프로필 배치 수집 | 🔄 delayMs 기본값: 500ms → 0ms |
| `POST` | `/api/company-profiles/admin/sp500` | S&P 500 회사프로필 수집 | 🔄 delayMs 기본값: 500ms → 0ms |
| `POST` | `/api/company-profiles/admin/symbol/{symbol}` | 회사프로필 개별 수집 | |

### 📈 시세 데이터 관리 (NEW!)
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/quote/admin/sp500` | **S&P 500 시세 일괄 수집** ⭐ |
| `POST` | `/api/quote/admin/symbol/{symbol}` | **단일 주식 시세 수집** ⭐ |

### 📰 뉴스 & 기타
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/news/companies/{symbol}` | 회사 뉴스 |
| `GET` | `/api/news/market` | 시장 뉴스 |
| `POST` | `/api/sp500/update` | S&P 500 목록 업데이트 |
| `GET` | `/api/sp500` | S&P 500 목록 조회 |

### Financial Metrics (5 endpoints)
- **Collect All Financials**: `/api/financial-metrics/admin/batch` (POST) - Batch collect financial data
- **Collect Single Financial**: `/api/financial-metrics/{symbol}` (POST) - Fetch specific financial metrics
- **Get Financial Metrics**: `/api/financial-metrics/{symbol}` (GET) - Retrieve stored financial metrics
- **Get Financial History**: `/api/financial-metrics/{symbol}/history` (GET) - Get financial metrics history
- **Get S&P 500 Financials**: `/api/financial-metrics/sp500` (GET) - Get S&P 500 financial metrics (today or most recent)
- **Collect S&P 500 Financials**: `/api/financial-metrics/admin/sp500` (POST) - Batch collect S&P 500 financial data

### Quote Data (2 endpoints)
- **Collect S&P 500 Quotes**: `/api/quote/admin/sp500` (POST) - Batch collect S&P 500 quote data
- **Collect Single Quote**: `/api/quote/admin/symbol/{symbol}` (POST) - Fetch specific stock quote

## 🤖 자동 스케줄링

### 📅 Daily Financial Metrics Collection
```
🕘 매일 9:00 AM ET: S&P 500 재무 지표 자동 수집
- 대상: 503개 S&P 500 종목
- 소요시간: 약 8.4분 (60 requests/minute)
- 배치 크기: 20개씩 처리
- 에러 처리: Rate limit 재시도 로직 포함
```

### 📈 Daily Quote Data Collection (NEW!)
```
🕟 매일 4:30 PM ET: S&P 500 종가 데이터 자동 수집 ⭐
- 대상: 503개 S&P 500 종목
- 소요시간: 약 8.4분 (60 requests/minute)
- 배치 크기: 20개씩 처리
- 수집 전략: 평일(종가), 주말(금요일 종가 유지)
- 글로벌 시장 영향 확인을 위해 주말에도 데이터 수집
```

### 📆 Monthly Data Collection
```
🗓️ 매월 1일 & 15일 8:00 AM ET: S&P 500 목록 & 회사 프로필 자동 수집
- S&P 500 목록 업데이트 (웹 스크래핑)
- S&P 500 회사 프로필 수집 (503개 종목)
- 소요시간: 약 10-15분 (목록 업데이트 + 프로필 수집)
- 배치 크기: 20개씩 처리
- Rate Limit: 60 requests/minute 자동 적용
```

### 📡 WebSocket Lifecycle Management
```
🟡 09:00 AM ET - PRE-MARKET SETUP
├─ WebSocket 연결 시작
├─ S&P 500 종목 구독 완료  
└─ 데이터 수신 시작 (저장 안함)

🟢 09:30 AM ET - MARKET OPEN
├─ 데이터 저장 활성화
├─ 실시간 거래 데이터 DB 저장 시작
└─ 연결은 이미 준비 완료 상태

🔴 04:00 PM ET - MARKET CLOSE
├─ 데이터 저장 중단
├─ WebSocket 연결 해제
└─ 시장 종료

🕟 04:30 PM ET - DAILY QUOTE COLLECTION
├─ S&P 500 종가 데이터 수집 시작
├─ 배치 처리로 안정적인 데이터 수집
└─ 주말에도 금요일 종가 유지 데이터 수집
```

## 📝 사용 예시

### Monitor Automated System
```bash
# Get complete scheduler status (includes health, config, and all schedulers)
curl http://localhost:8080/api/scheduler/status
```

### 실시간 데이터 조회
```bash
# 최신 가격 조회
curl "http://localhost:8080/api/trades/AAPL/price"

# 최신 거래 데이터 (10개)
curl "http://localhost:8080/api/trades/latest/AAPL?limit=10"

# 시간 범위별 이력
curl "http://localhost:8080/api/trades/history?from=2024-01-01T00:00:00&to=2024-01-02T00:00:00"

# S&P 500 재무지표 조회 (오늘 또는 최근)
curl "http://localhost:8080/api/financial-metrics/sp500"
```

### SSE 실시간 스트리밍 (JavaScript)
```javascript
const eventSource = new EventSource('/api/trades/stream/AAPL?interval=5');

eventSource.addEventListener('trade_data', function(event) {
    const data = JSON.parse(event.data);
    console.log(`${data.symbol}: $${data.trade.price}`);
});
```

### 데이터 수집 (최적화된 Rate Limit)
```bash
# 심볼 데이터 수집
curl -X POST "http://localhost:8080/api/symbols/batch?exchange=US"

# 재무지표 수집 (60 requests/minute 자동 적용)
curl -X POST "http://localhost:8080/api/financial-metrics/admin/batch?batchSize=20"

# S&P 500 재무지표 수집
curl -X POST "http://localhost:8080/api/financial-metrics/admin/sp500?batchSize=20"

# 특정 심볼 재무지표
curl -X POST "http://localhost:8080/api/financial-metrics/admin/symbol/AAPL"

# 회사프로필 수집 (자동 rate limiting)
curl -X POST "http://localhost:8080/api/company-profiles/admin/batch?batchSize=20"

# S&P 500 회사프로필 수집
curl -X POST "http://localhost:8080/api/company-profiles/admin/sp500?batchSize=20"

# S&P 500 시세 데이터 수집 (NEW!)
curl -X POST "http://localhost:8080/api/quote/admin/sp500?batchSize=20&delayMs=1000"

# 단일 주식 시세 데이터 수집 (NEW!)
curl -X POST "http://localhost:8080/api/quote/admin/symbol/AAPL"
```

### WebSocket 관리
```bash
# 연결 상태 확인
curl "http://localhost:8080/api/trades/websocket/status"

# 연결 시작
curl -X POST "http://localhost:8080/api/trades/websocket/admin/connect"
```

## 📊 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": {...},
  "message": "Successfully processed",
  "timestamp": "2024-01-15T10:30:00"
}
```

### 오류 응답
```json
{
  "success": false,
  "error": "Error message",
  "timestamp": "2024-01-15T10:30:00"
}
```

## 🔮 향후 계획

- [ ] 📈 실시간 대시보드 구현
- [ ] 🔔 가격 변동 알림 시스템
- [ ] 📊 기술적 지표 계산 기능
- [ ] 🐳 Docker 컨테이너화
- [ ] 🔑 Multi-API Key Rotation 시스템

---

<div align="center">

**⭐ 이 프로젝트가 유용하다면 스타를 눌러주세요! ⭐**

</div>

## API Endpoints Overview

**Total: 25 endpoints** (originally 28 → 25 after complete scheduler consolidation)

### Symbol Management (2 endpoints)
- **Add Symbols Batch**: `/api/symbols/batch` (POST) - Batch fetch stock symbols from exchange
- **Add Single Symbol**: `/api/symbols/{symbol}` (POST) - Fetch specific symbol data

### S&P 500 Management (2 endpoints)
- **Update S&P 500 List**: `/api/sp500/update` (POST) - Web scrape and update S&P 500 symbols
- **Get S&P 500 Symbols**: `/api/sp500` (GET) - Retrieve all S&P 500 symbols

### Company Information (4 endpoints)
- **Collect All Profiles**: `/api/company-profiles/admin/batch` (POST) - Batch collect company profiles
- **Collect Single Profile**: `/api/company-profiles/admin/symbol/{symbol}` (POST) - Fetch specific company profile
- **Get Company Profile**: `/api/company-profiles/{symbol}` (GET) - Retrieve stored company profile
- **Collect S&P 500 Profiles**: `/api/company-profiles/admin/sp500` (POST) - Batch collect S&P 500 company profiles

### Financial Metrics (5 endpoints)
- **Collect All Financials**: `/api/financial-metrics/admin/batch` (POST) - Batch collect financial data
- **Collect Single Financial**: `/api/financial-metrics/{symbol}` (POST) - Fetch specific financial metrics
- **Get Financial Metrics**: `/api/financial-metrics/{symbol}` (GET) - Retrieve stored financial metrics
- **Get Financial History**: `/api/financial-metrics/{symbol}/history` (GET) - Get financial metrics history
- **Get S&P 500 Financials**: `/api/financial-metrics/sp500` (GET) - Get S&P 500 financial metrics (today or most recent)
- **Collect S&P 500 Financials**: `/api/financial-metrics/admin/sp500` (POST) - Batch collect S&P 500 financial data

### Quote Data (2 endpoints)
- **Collect S&P 500 Quotes**: `/api/quote/admin/sp500` (POST) - Batch collect S&P 500 quote data
- **Collect Single Quote**: `/api/quote/admin/symbol/{symbol}` (POST) - Fetch specific stock quote

### Real-time Trade Data (6 endpoints)
- **Latest Trades by Symbol**: `/api/trades/latest/{symbol}` (GET) - Get latest trades for symbol
- **Current Price**: `/api/trades/{symbol}/price` (GET) - Get current price for symbol
- **Trade History**: `/api/trades/history` (GET) - Get trade history with filters
- **WebSocket Status**: `/api/trades/websocket/status` (GET) - Check WebSocket connection status
- **Connect WebSocket**: `/api/trades/websocket/admin/connect` (POST) - Start real-time data collection
- **Disconnect WebSocket**: `/api/trades/websocket/admin/disconnect` (POST) - Stop real-time data collection

### News & Market Data (2 endpoints)
- **Company News**: `/api/news/companies/{symbol}` (GET) - Get company-specific news
- **Market News**: `/api/news/market` (GET) - Get general market news

### Real-time Streaming (1 endpoint)  
- **SSE Stream**: `/api/sse/{symbol}` (GET) - Server-Sent Events real-time price stream

### Scheduler Management (1 endpoint)
**Monitor automated scheduling system:**

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/api/scheduler/status` | GET | Complete system status (Health + Financial Metrics + Quote Data + WebSocket + Config) |

**Note**: This is a fully automated system with a single comprehensive monitoring endpoint.

**Example Response:**
```json
{
  "success": true,
  "health": {
    "status": "healthy",
    "financialMetricsService": "active",
    "monthlyDataService": "active",
    "quoteService": "active",
    "webSocketService": "active",
    "schedulerEnabled": true,
    "automationLevel": "FULL",
    "totalSchedulers": 4
  },
  "financialMetricsScheduler": {
    "currentEasternTime": "2024-01-15 08:45:30 EST",
    "nextScheduleInfo": "Next execution: Jan 16, 2024 at 9:00 AM EST (in 15 minutes)",
    "schedule": "Daily at 9:00 AM ET (Mon-Fri)",
    "purpose": "S&P 500 financial metrics collection",
    "mode": "FULLY_AUTOMATED",
    "config": {
      "cronExpression": "0 0 9 * * MON-FRI",
      "timezone": "America/New_York",
      "description": "Every weekday at 9:00 AM Eastern Time",
      "targetSymbols": "S&P 500 stocks (503 symbols)",
      "batchSize": 20,
      "rateLimit": "60 requests/minute per API key",
      "estimatedDuration": "~8.4 minutes (503 symbols × 1 second)",
      "automation": "No manual intervention required"
    }
  },
  "quoteScheduler": {
    "currentEasternTime": "2024-01-15 08:45:30 EST",
    "nextScheduleInfo": "Next execution: Jan 15, 2024 at 4:30 PM EST (in 7 hours)",
    "schedule": "Daily at 4:30 PM ET (Mon-Fri)",
    "purpose": "S&P 500 daily closing quote collection",
    "mode": "FULLY_AUTOMATED",
    "config": {
      "cronExpression": "0 30 16 * * MON-FRI",
      "timezone": "America/New_York",
      "description": "Every weekday at 4:30 PM Eastern Time (30 minutes after market close)",
      "targetSymbols": "S&P 500 stocks (503 symbols)",
      "dataType": "Daily closing quotes",
      "batchSize": 20,
      "rateLimit": "60 requests/minute per API key",
      "estimatedDuration": "~8.4 minutes (503 symbols × 1 second)",
      "automation": "No manual intervention required"
    }
  },
  "monthlyDataScheduler": {
    "currentEasternTime": "2024-01-15 08:45:30 EST",
    "nextScheduleInfo": "Next execution: Feb 1, 2024 at 8:00 AM EST",
    "schedule": "Twice monthly: 1st & 15th at 8:00 AM ET",
    "purpose": "S&P 500 list update & company profiles collection",
    "mode": "FULLY_AUTOMATED",
    "config": {
      "cronExpression": "0 0 8 1,15 * ?",
      "timezone": "America/New_York",
      "description": "Every 1st and 15th day of month at 8:00 AM Eastern Time",
      "targetActions": "S&P 500 list scraping + Company profiles (503 symbols)",
      "batchSize": 20,
      "rateLimit": "60 requests/minute per API key",
      "estimatedDuration": "~10-15 minutes (list update + 503 profiles)",
      "automation": "No manual intervention required"
    }
  },
  "webSocketScheduler": {
    "isPreMarketSetup": false,
    "isMarketHours": false,
    "isDataSavingActive": false,
    "isWebSocketConnected": false,
    "nextMarketEvent": "Pre-market setup: Jan 16, 2024 at 9:00 AM EST",
    "schedule": "Pre-market: 9:00 AM, Market: 9:30 AM - 4:00 PM ET (Mon-Fri)",
    "purpose": "Real-time trade data collection",
    "mode": "FULLY_AUTOMATED"
  },
  "currentTime": "2024-01-15 08:45:30 EST",
  "message": "All automated scheduler services are running normally",
  "note": "This is a fully automated system with 4 schedulers - no manual intervention required"
}
```

## Usage Examples

### Monitor Automated System
```bash
# Get complete scheduler status (includes health, config, and all schedulers)
curl http://localhost:8080/api/scheduler/status
```

### Version 2.2.0 - Enhanced Scheduling & Quote Data Collection (Latest)

**🔄 Automated Scheduling System:**
- **Daily Financial Metrics Collection**: Automatic S&P 500 data collection at 9:00 AM ET
- **Daily Quote Data Collection**: Automatic S&P 500 closing quotes collection at 4:30 PM ET ⭐ **NEW**
- **Monthly Data Collection**: S&P 500 list & company profiles update twice monthly (1st & 15th at 8:00 AM ET)
- **Smart WebSocket Lifecycle**: Pre-market setup (9:00 AM) → Market data saving (9:30 AM - 4:00 PM)
- **No Data Loss Protection**: Connections established before market open for instant readiness
- **Full Automation**: Zero manual intervention required across all 4 schedulers

**📊 Enhanced API Management:**
- **Scheduler Monitoring**: 1 endpoint for complete automated system status monitoring
- **4-Scheduler Integration**: Financial Metrics + Quote Data + Monthly Data + WebSocket unified management
- **Consolidated Status API**: Single endpoint provides comprehensive system information
- **Health Monitoring**: Real-time service health and configuration details

**⚡ Optimized Rate Limiting:**
- **Finnhub API Compliance**: Proper 60 requests/minute implementation
- **API Client Enhancement**: Built-in 1000ms intervals between requests
- **Controller Optimization**: Removed redundant delays (0ms defaults)

**📈 Quote Data Management:**
- **S&P 500 Quote Collection**: Daily closing quotes for all 503 S&P 500 stocks
- **Weekend Strategy**: Friday closing prices maintained on weekends for global market tracking
- **Batch Processing**: Individual transactions for data integrity
- **Error Handling**: Graceful failure handling with detailed logging

## API 엔드포인트 구조

### 일반 사용자 접근 가능한 엔드포인트 (GET)

#### 재무 지표 API
- `GET /api/financial-metrics/{symbol}` - 특정 주식의 최신 재무 지표 조회
- `GET /api/financial-metrics/{symbol}/history` - 특정 주식의 재무 지표 기록 조회 (선택적 날짜 범위 필터링)
- `GET /api/financial-metrics/sp500` - S&P 500 재무 지표 조회 (오늘 또는 최근)

#### 회사 프로필 API
- `GET /api/company-profiles/{symbol}` - 특정 주식의 회사 프로필 정보 조회

#### 주식 심볼 API
- `GET /api/symbols/{symbol}` - 특정 주식 심볼 정보 조회

#### S&P 500 API
- `GET /api/sp500` - S&P 500 목록 조회

#### 실시간 거래 데이터 API
- `GET /api/trades/latest/{symbol}` - 최신 거래 데이터
- `GET /api/trades/{symbol}/price` - 최신 가격
- `GET /api/trades/history` - 거래 이력 (시간 범위)
- `GET /api/trades/stream/{symbol}` - 실시간 SSE 스트리밍

#### 뉴스 API
- `GET /api/news/companies/{symbol}` - 회사 뉴스
- `GET /api/news/market` - 시장 뉴스

### 관리자 전용 엔드포인트 (POST)

#### 재무 지표 관리 API
- `POST /api/financial-metrics/admin/symbol/{symbol}` - 특정 주식의 재무 지표 수집
- `POST /api/financial-metrics/admin/batch` - 여러 주식의 재무 지표 일괄 수집
- `POST /api/financial-metrics/admin/sp500` - S&P 500 종목들의 재무 지표 일괄 수집

#### 회사 프로필 관리 API
- `POST /api/company-profiles/admin/symbol/{symbol}` - 특정 주식의 회사 프로필 수집
- `POST /api/company-profiles/admin/batch` - 여러 주식의 회사 프로필 일괄 수집

#### 주식 심볼 관리 API
- `POST /api/symbols/{symbol}` - 새로운 주식 심볼 추가
- `POST /api/symbols/batch` - 여러 주식 심볼 일괄 추가

#### S&P 500 관리 API
- `POST /api/sp500/update` - S&P 500 목록 업데이트

#### WebSocket 관리 API
- `POST /api/trades/websocket/admin/connect` - WebSocket 연결 시작
- `POST /api/trades/websocket/admin/disconnect` - WebSocket 연결 해제

### 인증 관련 API
- `POST /api/auth/register` - 회원가입
- `POST /api/auth/login` - 로그인
- `POST /api/auth/logout` - 로그아웃
- `GET /api/auth/check-username` - 사용자명 중복 확인
- `GET /api/auth/check-email` - 이메일 중복 확인
- `GET /api/auth/me` - 현재 로그인한 사용자 정보 조회

### 관심 종목 API
- `GET /api/watchlist` - 관심 종목 목록 조회
- `POST /api/watchlist` - 관심 종목 추가
- `DELETE /api/watchlist/{symbol}` - 관심 종목 삭제
- `GET /api/watchlist/count` - 관심 종목 개수 조회

### 관리자 전용 시스템 API
- `GET /api/admin/system/status` - 시스템 상태 조회
- `POST /api/admin/auth/force-logout/{targetUserId}` - 특정 사용자 강제 로그아웃

## 기술 스택
