# 📈 Stocker Back - 실시간 주식 데이터 API

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)

## 🚀 프로젝트 개요

S&P 500 주식의 실시간 거래 데이터를 수집하고 RESTful API로 제공하는 시스템입니다.

### ✨ 주요 기능

- 🕐 **시장 시간 기반 자동 관리**: 미국 시장 시간(9:30 AM - 4:00 PM ET)에만 데이터 수집
- 🔑 **멀티 API 키 지원**: 여러 Finnhub API 키로 더 많은 종목 모니터링 (최대 100개 종목)
- 📊 **고정된 알파벳 순서**: 일관된 심볼 구독으로 안정적인 데이터 수집
- ⚡ **설정 가능한 저장 간격**: 심볼별로 설정된 간격(기본 10초)으로 효율적 저장
- 🔄 **실시간 메모리 캐싱**: WebSocket 연결 유지하며 최신 데이터 메모리에 보관
- 🛠️ **설정 가능한 모니터링**: 연결 상태 모니터링 간격 조절 가능
- 💾 **효율적인 데이터 저장**: PostgreSQL 배치 처리로 성능 최적화
- 🔄 **자동 정리**: 7일 이상 된 데이터 자동 삭제
- 🌐 **완전한 REST API**: 24개의 RESTful 엔드포인트 제공
- 📡 **SSE 실시간 스트리밍**: Server-Sent Events로 브라우저 실시간 데이터 전송
- 📈 **포괄적인 데이터 관리**: 심볼, 재무지표, 회사프로필, 뉴스 통합 관리
- 🔒 **중복 방지**: 스마트한 중복 데이터 방지 및 명확한 응답 메시지

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
| `GET` | `/api/stocks/trades/latest/{symbol}` | 최신 거래 데이터 |
| `GET` | `/api/stocks/trades/{symbol}/price` | 최신 가격 |
| `GET` | `/api/stocks/trades/history` | 거래 이력 (시간 범위) |
| `GET` | `/api/stocks/trades/stream/{symbol}` | 실시간 SSE 스트리밍 |

### 🔌 WebSocket 관리
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/trades/websocket/status` | 연결 상태 확인 |
| `POST` | `/api/stocks/trades/websocket/connect` | 연결 시작 |
| `POST` | `/api/stocks/trades/websocket/disconnect` | 연결 해제 |

### 📊 주식 데이터 관리  
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `POST` | `/api/stocks/symbols/batch` | 모든 주식 심볼 수집 |
| `POST` | `/api/stocks/symbols/{symbol}` | 특정 심볼 수집 |
| `POST` | `/api/stocks/financial-metrics/batch` | 재무지표 배치 수집 |
| `POST` | `/api/stocks/financial-metrics/sp500` | S&P 500 재무지표 수집 |
| `POST` | `/api/stocks/financial-metrics/{symbol}` | 재무지표 개별 수집 |
| `GET` | `/api/stocks/financial-metrics/{symbol}` | 재무지표 조회 |
| `POST` | `/api/stocks/company-profiles/batch` | 회사프로필 배치 수집 |
| `POST` | `/api/stocks/company-profiles/sp500` | S&P 500 회사프로필 수집 |
| `POST` | `/api/stocks/company-profiles/{symbol}` | 회사프로필 개별 수집 |
| `GET` | `/api/stocks/company-profiles/{symbol}` | 회사프로필 조회 |

### 📰 뉴스 & 기타
| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/stocks/news/companies/{symbol}` | 회사 뉴스 |
| `GET` | `/api/stocks/news/market` | 시장 뉴스 |
| `POST` | `/api/stocks/update/sp500` | S&P 500 목록 업데이트 |
| `GET` | `/api/stocks/sp500` | S&P 500 목록 조회 |

## 📝 사용 예시

### 실시간 데이터 조회
```bash
# 최신 가격 조회
curl "http://localhost:8080/api/stocks/trades/AAPL/price"

# 최신 거래 데이터 (10개)
curl "http://localhost:8080/api/stocks/trades/latest/AAPL?limit=10"

# 시간 범위별 이력
curl "http://localhost:8080/api/stocks/trades/history?from=2024-01-01T00:00:00&to=2024-01-02T00:00:00"
```

### SSE 실시간 스트리밍 (JavaScript)
```javascript
const eventSource = new EventSource('/api/stocks/trades/stream/AAPL?interval=5');

eventSource.addEventListener('trade_data', function(event) {
    const data = JSON.parse(event.data);
    console.log(`${data.symbol}: $${data.trade.price}`);
});
```

### 데이터 수집
```bash
# 심볼 데이터 수집
curl -X POST "http://localhost:8080/api/stocks/symbols/batch?exchange=US"

# 재무지표 수집 (배치)
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/batch?batchSize=20&delayMs=500"

# S&P 500 재무지표 수집 (S&P 500 종목만)
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/sp500?batchSize=20&delayMs=500"

# 특정 심볼 재무지표
curl -X POST "http://localhost:8080/api/stocks/financial-metrics/AAPL"

# 회사프로필 수집 (배치)
curl -X POST "http://localhost:8080/api/stocks/company-profiles/batch?batchSize=20&delayMs=500"

# S&P 500 회사프로필 수집 (S&P 500 종목만)
curl -X POST "http://localhost:8080/api/stocks/company-profiles/sp500?batchSize=20&delayMs=500"
```

### WebSocket 관리
```bash
# 연결 상태 확인
curl "http://localhost:8080/api/stocks/trades/websocket/status"

# 연결 시작
curl -X POST "http://localhost:8080/api/stocks/trades/websocket/connect"
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

---

<div align="center">

**⭐ 이 프로젝트가 유용하다면 스타를 눌러주세요! ⭐**

</div>