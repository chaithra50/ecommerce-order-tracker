# 🛒 Real-Time E-Commerce Order Tracking System

A **production-grade microservices backend** built with Java 17 + Spring Boot 3.  
Features real-time order tracking, JWT authentication, Kafka event streaming, Redis caching, and full observability with Prometheus + Grafana.

---

## 📐 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (Browser / App)                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP / WebSocket
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway  :8080                         │
│           Spring Cloud Gateway — routing, rate limiting         │
└──────────┬────────────────────────────────────┬─────────────────┘
           │ REST                               │ WS
           ▼                                    ▼
┌──────────────────────┐           ┌──────────────────────────────┐
│   Order Service      │           │   Notification Service       │
│   :8081              │           │   :8082                      │
│                      │           │                              │
│  ┌────────────────┐  │           │  ┌────────────────────────┐  │
│  │ Auth/JWT       │  │           │  │  Kafka Consumer        │  │
│  │ REST APIs      │  │  Kafka    │  │  WebSocket/STOMP Push  │  │
│  │ Business Logic │──┼──────────►│  │                        │  │
│  │ Redis Cache    │  │ Events    │  └────────────────────────┘  │
│  │ Kafka Producer │  │           └──────────────────────────────┘
│  └────────────────┘  │
│          │           │
│   ┌──────┴───────┐   │
│   │  PostgreSQL  │   │
│   │  Redis       │   │
│   └──────────────┘   │
└──────────────────────┘

Monitoring: Prometheus :9090 → Grafana :3000
Kafka UI:   :9093
```

---

## 🚀 Tech Stack

| Layer            | Technology                        | Purpose                              |
|------------------|-----------------------------------|--------------------------------------|
| Language         | Java 17                           | LTS, records, sealed classes         |
| Framework        | Spring Boot 3.2                   | Auto-config, dependency injection    |
| Security         | Spring Security + JWT (JJWT)      | Stateless auth, role-based access    |
| Database         | PostgreSQL 16 + Spring Data JPA   | Persistent storage, ACID compliance  |
| Cache            | Redis 7 + Spring Cache            | Sub-millisecond order status reads   |
| Messaging        | Apache Kafka 7.5                  | Async event streaming                |
| Real-time Push   | WebSocket + STOMP                 | Live order status updates            |
| API Gateway      | Spring Cloud Gateway              | Routing, rate limiting, circuit breaker |
| Observability    | Micrometer + Prometheus + Grafana | Metrics, dashboards                  |
| Containerization | Docker + Docker Compose           | Full local stack in one command      |
| Testing          | JUnit 5 + Mockito + Testcontainers| Unit & integration tests             |
| Build            | Maven (multi-module)              | Dependency management                |

---

## 📁 Project Structure

```
ecommerce-order-tracker/
├── api-gateway/                  # Spring Cloud Gateway
│   └── src/main/
│       ├── java/com/apigateway/
│       │   └── config/FallbackController.java
│       └── resources/application.yml
│
├── order-service/                # Core business service
│   └── src/main/java/com/ordertracker/
│       ├── config/               # Security, Redis, Kafka configs
│       ├── controller/           # REST endpoints
│       ├── dto/                  # Request/Response objects
│       ├── entity/               # JPA entities (User, Order, OrderItem)
│       ├── enums/                # OrderStatus, Role
│       ├── exception/            # Custom exceptions + GlobalExceptionHandler
│       ├── kafka/                # OrderEvent model + Producer
│       ├── repository/           # Spring Data JPA repositories
│       ├── security/             # JwtService + JwtAuthFilter
│       └── service/              # Business logic
│
├── notification-service/         # Real-time notification service
│   └── src/main/java/com/notificationservice/
│       ├── config/               # WebSocket + Kafka consumer config
│       ├── kafka/                # OrderEventConsumer
│       ├── model/                # OrderEvent model
│       └── websocket/            # NotificationPayload
│
├── docker/
│   ├── init.sql                  # PostgreSQL schema + seed data
│   ├── prometheus.yml            # Prometheus scrape config
│   └── grafana/provisioning/     # Auto-configured Grafana dashboards
│
├── docker-compose.yml            # Full stack: all services + infra
├── .env.example                  # Environment variable template
└── README.md
```

---

## ⚡ Getting Started

### Prerequisites
- Docker Desktop 4.x+
- Java 17+ (for local dev without Docker)
- Maven 3.9+

### Run the full stack (recommended)

```bash
# 1. Clone the repository
git clone https://github.com/yourusername/ecommerce-order-tracker.git
cd ecommerce-order-tracker

# 2. Copy and configure env (defaults work out of the box)
cp .env.example .env

# 3. Start everything with one command
docker-compose up --build

# Services will be available at:
# API Gateway:       http://localhost:8080
# Order Service:     http://localhost:8081
# Notification WS:   ws://localhost:8082/ws
# Kafka UI:          http://localhost:9093
# Prometheus:        http://localhost:9090
# Grafana:           http://localhost:3000  (admin/admin)
```

### Run locally (without Docker)

```bash
# Start infrastructure only
docker-compose up postgres redis kafka zookeeper -d

# Run order-service
cd order-service
mvn spring-boot:run

# In another terminal, run notification-service
cd notification-service
mvn spring-boot:run
```

---

## 📡 API Reference

All requests go through the API Gateway at `http://localhost:8080`.

### Authentication

#### Register
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "fullName": "Ravi Kumar",
  "email": "ravi@example.com",
  "password": "SecurePass@123",
  "phone": "+919876543210"
}
```

#### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "ravi@example.com",
  "password": "SecurePass@123"
}
```
**Response includes** `accessToken` — use as `Authorization: Bearer <token>` on all subsequent requests.

---

### Orders

#### Create Order
```http
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "shippingAddress": "123 MG Road, Bangalore, KA 560001",
  "items": [
    {
      "productName": "Mechanical Keyboard",
      "productSku": "KB-MECH-001",
      "quantity": 1,
      "unitPrice": 2499.00
    },
    {
      "productName": "USB-C Hub",
      "productSku": "HUB-USB-004",
      "quantity": 2,
      "unitPrice": 999.00
    }
  ]
}
```

#### Get Order
```http
GET /api/v1/orders/{orderNumber}
Authorization: Bearer <token>
```

#### My Orders (paginated)
```http
GET /api/v1/orders/my?page=0&size=10
Authorization: Bearer <token>
```

#### Update Order Status (Admin only)
```http
PATCH /api/v1/orders/{orderNumber}/status
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "status": "SHIPPED",
  "trackingNumber": "TRK-987654",
  "statusNote": "Dispatched via BlueDart"
}
```

#### Cancel Order
```http
DELETE /api/v1/orders/{orderNumber}/cancel
Authorization: Bearer <token>
```

#### All Orders — Admin
```http
GET /api/v1/admin/orders?page=0&size=20
Authorization: Bearer <admin-token>
```

---

## 🔄 Order Status Flow

```
PENDING → CONFIRMED → PAYMENT_PROCESSING → PREPARING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED → REFUNDED
    │           │              │                │
    └──────────►└──────────────┴────────────────┴──► CANCELLED
                       PAYMENT_FAILED ──────────────► PAYMENT_PROCESSING (retry)
```

Each status transition publishes a **Kafka event** → Notification Service consumes it → **WebSocket push** to the client.

---

## 📡 WebSocket — Real-Time Updates

Connect using SockJS + STOMP:

```javascript
const socket = new SockJS('http://localhost:8082/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  // Subscribe to a specific order's updates
  stompClient.subscribe('/topic/orders/ORD-ABC12345', (msg) => {
    const update = JSON.parse(msg.body);
    console.log('Order update:', update);
    // { orderNumber, eventType, oldStatus, newStatus, trackingNumber, message, timestamp }
  });

  // Subscribe to all updates for the logged-in user
  stompClient.subscribe('/queue/users/42', (msg) => {
    const update = JSON.parse(msg.body);
    console.log('User notification:', update);
  });
});
```

---

## 📊 Monitoring

| Dashboard | URL |
|-----------|-----|
| Grafana (metrics) | http://localhost:3000 (admin / admin) |
| Prometheus | http://localhost:9090 |
| Kafka UI | http://localhost:9093 |
| Order Service Health | http://localhost:8081/actuator/health |
| Order Service Metrics | http://localhost:8081/actuator/prometheus |

### Key Metrics Tracked
- `orders_created_total` — total orders placed
- `orders_cancelled_total` — total cancellations
- `orders_creation_time_seconds` — histogram of order creation latency
- `notification_events_processed_total` — Kafka events consumed
- `http_server_requests_seconds` — per-endpoint API latency
- JVM heap, GC pause times, thread counts

---

## 🧪 Running Tests

```bash
# Run all unit tests
mvn test -pl order-service

# Run with coverage report
mvn test jacoco:report -pl order-service
# Report: order-service/target/site/jacoco/index.html

# Run a specific test class
mvn test -pl order-service -Dtest=OrderServiceTest
```

---

## 🔐 Default Credentials

| Role     | Email                    | Password       |
|----------|--------------------------|----------------|
| Admin    | admin@ordertracker.com   | Admin@1234     |
| Customer | customer@test.com        | Customer@1234  |

> **Security Note:** Change all credentials before any deployment. Generate a strong JWT secret with `openssl rand -hex 64`.

---

## 🌱 Future Enhancements

- **Flyway** database migrations (replace `ddl-auto`)
- **Resilience4j** circuit breaker on service-to-service calls
- **OpenTelemetry** distributed tracing with Jaeger
- **Stripe / Razorpay** payment gateway integration
- **Email notifications** via JavaMailSender + SendGrid
- **Kubernetes** deployment manifests (Helm charts)
- **GitHub Actions** CI/CD pipeline

---

## 📝 License

MIT License — free to use, modify, and distribute.
