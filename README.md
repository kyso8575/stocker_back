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
- ⚡ **10초 간격 모니터링**: 시장 시간 중 연결 상태 자동 체크 및 재연결
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
                       │ Service     │
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
finnhub.scheduled.websocket.enabled=true      # 스케줄링된 서비스 활성화

# 데이터베이스 최적화
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.show-sql=true                       # 개발시에만 true
```

## 📡 API 엔드포인트

### 🕐 스케줄링된 WebSocket 관리

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/trades/scheduled-websocket/status` | 시스템 상태 확인 |
| `POST` | `/api/trades/scheduled-websocket/toggle?enabled=true` | 서비스 활성화/비활성화 |
| `GET` | `/api/trades/market-hours` | 미국 시장 시간 정보 |

### 🔧 수동 WebSocket 관리 (테스트용)

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/trades/websocket/multi-status` | 연결 상태 확인 |
| `POST` | `/api/trades/websocket/multi-connect` | 수동 연결 시작 |
| `POST` | `/api/trades/websocket/multi-disconnect` | 수동 연결 해제 |

### 📊 거래 데이터 조회

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| `GET` | `/api/trades/{symbol}/latest?limit=10` | 특정 심볼의 최신 거래 데이터 |
| `GET` | `/api/trades/{symbol}/latest-price` | 특정 심볼의 최신 가격 |
| `GET` | `/api/trades/statistics` | 심볼별 거래 통계 |
| `GET` | `/api/trades?page=0&size=20` | 페이징된 전체 거래 데이터 |
| `GET` | `/api/trades/range?startTime=2024-01-01T00:00:00&endTime=2024-01-02T00:00:00` | 시간 범위별 조회 |

### 📝 API 응답 예시

#### 시스템 상태 확인
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

#### 거래 통계 조회
```json
{
  "statistics": [
    {"symbol": "AAPL", "tradeCount": 150},
    {"symbol": "MSFT", "tradeCount": 142}
  ],
  "symbolCount": 100,
  "totalTrades": 5240,
  "description": "Trade count by symbol",
  "timestamp": "2025-06-05T17:23:56.288587"
}
```

## 🔧 시스템 구성 요소

### 핵심 서비스

#### 1. `ScheduledWebSocketService`
- 🕐 **시장 시간 모니터링**: 매분마다 미국 시장 시간 체크
- 🔄 **자동 연결 관리**: 개장시 연결, 마감시 해제
- ⚡ **상태 모니터링**: 10초마다 연결 상태 확인

#### 2. `MultiKeyFinnhubWebSocketService`
- 🔑 **멀티 API 키 관리**: 여러 키로 더 많은 종목 커버
- 📊 **고정 구독**: 알파벳 순으로 일관된 심볼 할당
- 🔄 **자동 재연결**: 연결 끊김시 자동 복구

#### 3. `TradeCleanupService`
- 🗑️ **자동 정리**: 7일 이상 된 데이터 삭제
- 📈 **통계 수집**: 시간당 거래 데이터 현황 리포트

### 데이터 플로우

```
Finnhub WebSocket → Message Handler → Trade Converter → Database Batch Save
                                           ↓
                               Trade Cleanup Service (Daily)
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
# 전체 시스템 상태
curl http://localhost:8080/api/trades/scheduled-websocket/status

# 연결 상태 확인
curl http://localhost:8080/api/trades/websocket/multi-status

# 시장 시간 확인
curl http://localhost:8080/api/trades/market-hours
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

#### 3. 데이터베이스 연결 오류
```
❌ Failed to save trades from [connection-1]
```
**해결책**: PostgreSQL 서비스 상태 및 연결 정보 확인

### 디버깅 모드 활성화

```properties
# application.properties에 추가
logging.level.com.stocker_back.stocker_back.service=DEBUG
logging.level.org.java_websocket=DEBUG
```

## 🔮 향후 계획

- [ ] 📈 **실시간 대시보드**: WebSocket을 통한 프론트엔드 연동
- [ ] 🔔 **알림 시스템**: 가격 변동 알림 기능
- [ ] 📊 **기술적 지표**: 이동평균, RSI 등 계산 기능
- [ ] 🌐 **API 확장**: RESTful API 추가 기능
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


