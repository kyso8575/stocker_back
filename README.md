# Stocker 

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
| GET | `/api/stocks/fetch/symbols` | 주식 심볼 데이터 수집 | - |
| GET | `/api/stocks/fetch/profiles` | 모든 주식 프로필 수집 | batchSize, delayMs |
| GET | `/api/stocks/fetch/profiles/null-country` | Country가 Null인 주식 프로필 수집 | batchSize, delayMs |
| GET | `/api/stocks/fetch/quotes/{symbol}` | 특정 주식 실시간 시세 조회 | symbol |
| GET | `/api/stocks/fetch/quotes/all` | 모든 주식 실시간 시세 조회 | batchSize, delayMs |
| **주식 정보 공유 API** |
| GET | `/api/stocks/detail/{ticker}` | 주식 상세 프로필 정보 조회 | ticker |
| GET | `/api/stocks/news` | 주식 관련 뉴스 조회 | symbol, from, to, count |
| GET | `/api/stocks/market_news` | 마켓 뉴스 조회 | from, to, count |
| GET | `/api/stocks/basic-financials/{symbol}` | 주식 기본 재무 지표 조회 | symbol |
| GET | `/api/stocks/top-movers` | 상승/하락 상위 종목(Top Movers) 조회 | type, limit, market |
