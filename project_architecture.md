# System Design & Architecture Master Guide

This document presents a comprehensive, high-fidelity system design architecture for the Ticket Booking System, specifically tailored for a **System Design Interview**. It details how the platform addresses high concurrency, data consistency, eventual consistency, rate limiting, and real-time event broadcasting at scale.

---

## 🏗️ System Design Architecture Diagram

This component diagram models a highly scalable, multi-tier deployment of your system, illustrating how traffic flows through the API boundaries, distributed caches, transactional relational databases, and asynchronous event workers.

```mermaid
graph TD
    classDef client fill:#eceff1,stroke:#37474f,stroke-width:2px;
    classDef security fill:#ffe082,stroke:#ffb300,stroke-width:2px;
    classDef compute fill:#e1f5fe,stroke:#0288d1,stroke-width:2px;
    classDef cache fill:#ffebee,stroke:#c62828,stroke-width:2px;
    classDef db fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;
    classDef async fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef ops fill:#fbe9e7,stroke:#d84315,stroke-width:2px;

    %% 1. CLIENT TIER
    subgraph ClientTier ["1. Client Tier (Angular Single Page Application)"]
        direction LR
        Browser["User Browser"]:::client
        AngularComponents["Angular UI Components <br/> (Stadium Layout SVG, Matches)"]:::client
        JwtInterceptor["JwtInterceptor <br/> (Injects Bearer JWT into headers)"]:::client
        SSEListener["EventSource Listening Stream <br/> (Real-Time UI updates)"]:::client

        Browser --> AngularComponents
        AngularComponents --> JwtInterceptor
    end

    %% 2. SECURITY, GATEWAY & RESILIENCE LAYER
    subgraph GatewayTier ["2. Gateway & Resilience Layer (Spring Security Filters)"]
        direction TB
        CORSFilter["CorsFilter <br/> (Origins, Methods, & Credential Checks)"]:::security
        RateLimiterClass["RateLimiterService <br/> (ConcurrentHashMap Sliding Token Limiter)"]:::security
        JwtAuthFilter["JwtAuthFilter <br/> (Stateless Token Extraction & Signature Check)"]:::security
        JwtServiceClass["JwtService <br/> (Cryptographic HMAC-SHA256 Token Parser)"]:::security
        CircuitBreaker["Resilience4j Circuit Breakers <br/> (Protects Outgoing Cache Connections)"]:::security

        CORSFilter --> RateLimiterClass --> JwtAuthFilter
        JwtAuthFilter -->|"Delegates verification"| JwtServiceClass
    end

    %% 3. APPLICATION & COMPUTE POOL
    subgraph ComputePool ["3. Application Compute Tier (Spring Boot Nodes)"]
        direction TB
        TomcatPool["Tomcat Thread Pool <br/> (Thread-per-Request Execution Model)"]:::compute
        SeatController["SeatLockController <br/> (REST API & SSE stream)"]:::compute
        AuthController["AuthController <br/> (Registration & /me Profile endpoint)"]:::compute

        TomcatPool --> SeatController
        TomcatPool --> AuthController
    end

    %% 4. DISTRIBUTED CACHE LAYER (CONCURRENCY CONTROL)
    subgraph CacheTier ["4. Distributed Cache Tier (Redis Cluster)"]
        direction TB
        RedisLocks[("Redis TTL Locks <br/> Key: seat:lock:matchName:seatId <br/> Value: userId")]:::cache
    end

    %% 5. RELATIONAL DATA STORAGE (ACID BOUNDARY)
    subgraph DBTier ["5. Relational Persistence Tier (PostgreSQL Database)"]
        direction TB
        BookingService["BookingService <br/> (@Transactional DB Orchestrator)"]:::db
        PGUsers[("Users Table <br/> (UUID Primaries, BCrypt hashes)")]:::db
        PGTickets[("Tickets Table <br/> (Composite UNIQUE: match_name + seat_id)")]:::db
        PGOutbox[("OutboxEvent Table <br/> (Transaction Log for Dual-Write Safety)")]:::db

        BookingService --> PGUsers
        BookingService -->|"ACID Transaction Commit"| PGTickets
        BookingService -->|"ACID Transaction Commit"| PGOutbox
    end

    %% 6. ASYNC EVENTUAL CONSISTENCY TIER
    subgraph AsyncWorker ["6. Eventual Consistency Tier (Transactional Outbox Daemon)"]
        direction TB
        OutboxProcessor["OutboxProcessor <br/> (Background Scheduled Worker, 1s poll)"]:::async
        SSEBroadcast["SSE Broadcaster <br/> (Server-Sent Events Stream)"]:::async
    end

    %% 7. MONITORING & OBSERVABILITY TIER
    subgraph OpsTier ["7. Observability Tier"]
        direction TB
        SpringActuator["Spring Actuator <br/> (System health, CPU, memory)"]:::ops
        Micrometer["Micrometer Prometheus <br/> (Scrapes metrics)"]:::ops
        
        SpringActuator --> Micrometer
    end

    %% TIER-TO-TIER TRAFFIC WIRING
    JwtInterceptor -->|"Secure HTTP REST Requests"| CORSFilter
    JwtAuthFilter -->|"Resolved SecurityContext"| TomcatPool
    
    %% Compute Tier Interfaces
    SeatController -->|"1. Rate Limit Lock Action"| RateLimiterClass
    SeatController -->|"2. Read/Write TTL Locks"| CircuitBreaker
    CircuitBreaker --> RedisLocks
    SeatController -->|"3. Coordinate Purchase"| BookingService
    
    %% Async Engine Flow
    OutboxProcessor -->|"1. Reads unprocessed events"| PGOutbox
    OutboxProcessor -->|"2. Releases expired Locks"| RedisLocks
    OutboxProcessor -->|"3. Triggers realtime updates"| SSEBroadcast
    SSEBroadcast -.->|"Asynchronous SSE streams"| SSEListener

    %% Actuator scoping
    ComputePool --> SpringActuator

    class Browser,AngularComponents,JwtInterceptor,SSEListener client;
    class CORSFilter,RateLimiterClass,JwtAuthFilter,JwtServiceClass,CircuitBreaker security;
    class TomcatPool,SeatController,AuthController compute;
    class RedisLocks cache;
    class BookingService,PGUsers,PGTickets,PGOutbox db;
    class OutboxProcessor,SSEBroadcast async;
    class SpringActuator,Micrometer ops;
```

---

## 🧠 System Design Trade-Offs & Key Architectures

Use this technical reference to showcase your deep system design knowledge during your interview:

### 1. High Concurrency & Preventing Double-Booking (Multi-Tier Defense)
When thousands of users try to book the same stadium seat simultaneously, the system uses a **multi-tier concurrency defense** to guarantee data consistency:
* **Tier 1 (Redis Distributed Locks)**: Before checkout, a temporary lock is set in Redis (`seat:lock:<matchName>:<seatId>`) with a TTL. Since Redis is single-threaded and executes commands sequentially, only *one* request succeeds in setting the key, preventing immediate write conflicts.
* **Tier 2 (Lock Ownership Verification)**: Prior to committing a ticket purchase, the backend verifies `userId.equals(lockOwner)` in Redis to ensure a user cannot purchase a seat reserved by someone else (preventing booking hijacking).
* **Tier 3 (ACID Relational Constraints)**: The ultimate safety net is a composite `UNIQUE(match_name, seat_id)` index in PostgreSQL. Under high load, if two requests bypass cache layers, the database blocks the double-insert, rolls back the transaction, and throws a `DataIntegrityViolationException` (cleanly mapped to HTTP 409 Conflict).

### 2. Solving the "Dual-Write" Problem via the Transactional Outbox Pattern
* **The Problem**: A naive booking flow writes a ticket to PostgreSQL, deletes the seat lock from Redis, and broadcasts a real-time Server-Sent Event (SSE). However, databases and caches cannot share an atomic transaction boundary. If the database commit succeeds but the network fails while deleting the Redis lock, the system becomes inconsistent.
* **The Solution**: The system implements the **Transactional Outbox Pattern**:
  * The ticket details and an `OutboxEvent` log are committed atomically inside the same PostgreSQL transaction (`@Transactional`).
  * An asynchronous background worker (`OutboxProcessor`) polls the outbox table every second.
  * The worker safely clears the Redis lock, updates state caches, broadcasts the SSE update, and marks the event as processed.
  * This guarantees **at-least-once delivery** and eventual consistency, shielding core transactions from caching or streaming network failures.

### 3. Edge-Layer Resilience (Rate Limiting & Circuit Breakers)
* **Bot & DDoS Mitigation**: The `RateLimiterService` tracks seat-locking requests in an in-memory sliding window using thread-safe `AtomicInteger` counters within a `ConcurrentHashMap`. Any user exceeding **20 requests per minute** is immediately blocked at the filter level (`429 Too Many Requests`), saving app server and database resources.
* **Database & Cache Isolation**: Backend services utilize **Resilience4j Circuit Breakers** to wrap calls to the Redis cache. If the Redis instance becomes unresponsive, the circuit breaker trips, causing the application to fail gracefully (returning a conflict) rather than exhausting Tomcat thread pools and crashing the server.

### 4. Real-Time Data Streaming via Server-Sent Events (SSE)
* **Why SSE over WebSockets?**: Stadium booking is primarily **one-way real-time streaming** (the server broadcasting seat updates to all connected browsers). SSE runs over standard HTTP, supports automatic reconnection out of the box, and bypasses corporate firewalls easily without the complexity or overhead of full-duplex WebSockets.
* **Stream Performance**: The `SeatLockService` holds a collection of active `SseEmitter` clients grouped by `matchName`. This keeps browser screens in sync with the actual seat inventory in real-time, reducing stale HTTP requests from clients repeatedly polling the server.

---

## 🎤 System Design Talking Points: Scaling to 1,000,000 Users

If the interviewer asks: **"How would you scale this architecture to support a high-volume concert booking with 1,000,000 concurrent users?"**

1. **Redis Clustering & Partitioning**:
   * *Talking Point*: *"Currently, we use a single Redis instance. To scale to a million users, we would partition our distributed locks across a **Redis Cluster** using the `{matchName}` as a hash tag (e.g. `{match-a}:seat:lock`). This ensures all locks for a single match reside on the same Redis shard, keeping operations atomic while scaling out memory and throughput across shards."*
2. **PostgreSQL Read Replicas & Connection Pooling**:
   * *Talking Point*: *"For database scaling, our writes are protected by ACID boundaries, but our read volume (fetching seat statuses) is extremely high. We would offload read traffic by deploying **PostgreSQL Read Replicas** and using a pool manager like **PgBouncer** to handle thousands of open database connections efficiently without exhausting database process memory."*
3. **Horizontal Scaling of Compute Nodes**:
   * *Talking Point*: *"Since our application nodes are fully stateless (session states are stored in JWTs), we can horizontally scale out our Spring Boot nodes behind an **Application Load Balancer (ALB)**. If one node fails, the ALB routes traffic to healthy nodes seamlessly without dropping active user logins."*
4. **Message Broker Integration for the Outbox Pattern**:
   * *Talking Point*: *"Currently, the `OutboxProcessor` polls the database every second. Under extreme scale, database polling creates overhead. We would transition this to a log-tailed outbox processor using tools like **Debezium** to stream PostgreSQL transaction logs (WAL) directly into a high-throughput message broker like **Apache Kafka**. Kafka would then trigger our cache invalidation and SSE broadcast services asynchronously."*
