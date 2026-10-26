# myCapstoneProject: E-commerce Microservices Backend

A Spring Boot microservices backend for an e-commerce website. It implements the PRD and HLD in
*PRD & HLD of Ecommerce Website*: user management, product catalog, cart, checkout, orders, payments and
notifications. Kafka connects the services, Redis provides caching, and Elasticsearch powers search.

```
                         ┌───────────────────── Eureka Service Discovery (8761) ─────────────────────┐
 Client ─► (AWS ELB) ─► API Gateway (8080) ──► User/Auth (8081) ── MySQL
                         │  auth, roles,      ├► Product Catalog (8082) ── MySQL + Elasticsearch
                         │  rate limit (Redis)├► Cart (8083) ── MongoDB + Redis
                         │                    ├► Order (8084) ── MySQL
                         │                    └► Payment (8085) ── MySQL + Stripe / Razorpay / Mock
                         │
             Kafka topics: signup, password-reset, session-revoked, cart-events, order-events, payment-events
                         └──────────────► Notification (8086) ── SMTP / Amazon SES
```

| Service | Folder | Port | Storage | Responsibilities |
|---|---|---|---|---|
| Service Discovery | `ServiceDiscovery_MyCapstoneproject` | 8761 | – | Eureka registry |
| API Gateway | `ApiGateway_MyCapstoneProject` | 8080 | Redis | Routing, token validation (cached), USER/ADMIN access, header anti-spoofing, rate limiting |
| User / Auth | `UserAuthService_MyCapStoneProject` | 8081 | MySQL | Signup, login (JWT + session), logout, profile, password reset, roles |
| Product Catalog | `ProductCatalogService_MyCapstone` | 8082 | MySQL, Elasticsearch | Products, categories, browse, fuzzy full-text search |
| Cart | `CartService_MyCapstoneProject` | 8083 | MongoDB, Redis | Cart CRUD, cache-aside reads, cart events |
| Order | `OrderService_MyCapstoneProject` | 8084 | MySQL | Checkout, history, tracking, status state machine |
| Payment | `PaymentService_MyCapstoneProject` | 8085 | MySQL | Payment links (Stripe/Razorpay/Mock), signed webhooks, receipts |
| Notification | `NotificationService_MyCapStoneProject` | 8086 | – | Welcome, reset, order, payment and receipt emails |

## Run it

Prerequisites: JDK 17, Maven 3.9 and Docker.

```bash
./build-all.sh                 # build + test every service (add -DskipTests to skip tests)
docker compose up -d --build   # MySQL, MongoDB, Redis, Kafka, Elasticsearch + all services
./scripts/e2e-smoke.sh         # 58 end-to-end checks through the gateway
```

The services take about a minute to register with Eureka (dashboard: http://localhost:8761).
Signing up with `admin@capstone-shop.local` (configurable with `ADMIN_EMAILS`) gives a user the ADMIN role.

Configuration comes from environment variables. Defaults are in each service's `application.properties`
and in `docker-compose.yml`. The main ones are:

| Variable | Used by | Default |
|---|---|---|
| `JWT_SECRET` | user service | dev-only key (**override in any shared environment**) |
| `PAYMENT_GATEWAY` | payment | `MOCK` (`STRIPE` / `RAZORPAY` need the keys below) |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | payment | empty |
| `EMAIL_ENABLED`, `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | notification | disabled; emails are logged |
| `ELASTICSEARCH_ENABLED` | product | `true` in compose, `false` standalone (falls back to MySQL `LIKE`) |
| `RATE_LIMIT_PER_SECOND`, `RATE_LIMIT_BURST`, `AUTH_CACHE_TTL_SECONDS` | gateway | 20, 40, 60 |

## API (through the gateway on :8080)

Send `Authorization: Bearer <token>` (returned by `/auth/login`) or the `auth_session_id` cookie.

| Method & path | Access | PRD |
|---|---|---|
| `POST /auth/signup`, `POST /auth/login`, `POST /auth/logout` | public | 1.1, 1.2, 6.2 |
| `POST /auth/forgot-password`, `POST /auth/reset-password` | public | 1.4 |
| `GET /users/me`, `PATCH /users/me` | user | 1.3 |
| `GET /products?page&size`, `GET /products/{id}`, `GET /categories`, `GET /categories/{id}/products` | public | 2.1, 2.2 |
| `GET /search?q=&page=&size=&sortBy=` or `POST /search` | public | 2.3 |
| `POST/PUT/DELETE /products…`, `POST /categories`, `POST /search/reindex` | admin | – |
| `GET /cart`, `POST /cart/items`, `PUT/DELETE /cart/items/{productId}`, `DELETE /cart` | user | 3.1, 3.2 |
| `POST /orders/checkout` | user | 3.3 |
| `GET /orders`, `GET /orders/{id}`, `GET /orders/{id}/tracking`, `POST /orders/{id}/cancel` | user | 4.1–4.3 |
| `PATCH /orders/{id}/status` | admin | 4.3 |
| `GET /payment/order/{orderId}`, `GET /payment/order/{orderId}/receipt` | user | 5.2, 5.3 |
| `POST /stripeWebhook`, `POST /payment/webhook/razorpay` | signed by the gateway | 5.1, 5.2 |

### Checkout flow

1. `POST /orders/checkout`: the Order service reads the cart, re-prices each item from the catalog, saves
   the order as `PAYMENT_PENDING`, empties the cart and publishes `ORDER_PLACED` to `order-events`.
2. The Payment service consumes `ORDER_PLACED`, creates a payment link and publishes `PAYMENT_LINK_CREATED`.
   The Order service stores the link on the order, and the Notification service emails it.
3. The customer pays. A signed gateway webhook (or the mock gateway page) marks the payment `SUCCEEDED`, and
   `PAYMENT_SUCCEEDED` moves the order to `PAID` and triggers the receipt email.
4. Admins advance the order to `SHIPPED`, then `OUT_FOR_DELIVERY`, then `DELIVERED`. Each step is recorded
   and shown by `/orders/{id}/tracking`.

## Tests and benchmarks

- `mvn package` runs 44 unit/integration tests (H2 for MySQL and mocked Kafka; no infrastructure needed).
- `scripts/e2e-smoke.sh` runs 58 checks against the full Docker stack. The latest output is in
  [`docs/results/e2e-smoke-output.txt`](docs/results/e2e-smoke-output.txt).
- `scripts/seed_products.py`, `scripts/seed_orders.py` and `scripts/bench.py` reproduce the benchmarks below.
  Raw results are in [`docs/results`](docs/results).

Measured on the Docker Compose stack (4 vCPU, 16 GB RAM, everything on one machine), with 10 concurrent clients:

| Optimisation | Before (p50 / throughput) | After (p50 / throughput) |
|---|---|---|
| Product search, 200k products: MySQL `LIKE` → Elasticsearch | 1,624 ms / 5.9 req/s | 40.7 ms / 212 req/s |
| Order history, 300k orders: composite index `(user_id, created_at)` | 828 ms / 11.6 req/s | 21.4 ms / 446 req/s |
| Order history: N+1 item loading → `@BatchSize` (12 → 3 queries) | 21.4 ms / 446 req/s | 13.9 ms / 669 req/s |
| Cart read: MongoDB → Redis cache | 5.2 ms / 1,752 req/s | 3.6 ms / 2,405 req/s |
| Gateway auth: validate every request → Redis-cached validation | 21.5 ms / 406 req/s | 11.1 ms / 839 req/s |

## Deployment on AWS

See [`docs/aws-deployment.md`](docs/aws-deployment.md).
