# MicroSmart
Actividad de universidad desarrollo fullstack 2.2.2

MicroSmart/
├── docker-compose.yml             <-- Orquesta Postgres, MongoDB, RabbitMQ, Redis y los MS
├── api-gateway/
│   ├── pom.xml                    <-- Dependencias: Spring Cloud Gateway, Redis Reactive, OAuth2 Resource Server
│   ├── src/main/resources/application.yml
│   ├── src/main/java/com/microsmart/gateway/GatewayApplication.java <-- Proxy, JWT Security, Rate Limiter (Redis)
│   └── Dockerfile                 <-- Multi-stage build con Maven + JRE 17
├── ms-reservas/
│   ├── pom.xml                    <-- Dependencias: Spring Web, Data JPA, Postgres, AMQP
│   ├── src/main/resources/application.yml
│   ├── src/main/java/com/microsmart/reservas/ReservasApplication.java <-- @Lock(PESSIMISTIC_WRITE) y RabbitTemplate
│   └── Dockerfile
├── ms-solicitudes/
│   ├── pom.xml                    <-- Dependencias: Resilience4j, Spring AMQP, Data JPA
│   ├── src/main/resources/application.yml
│   ├── src/main/java/com/microsmart/solicitudes/SolicitudesApplication.java <-- @RabbitListener y @CircuitBreaker
│   └── Dockerfile
└── ms-personalizacion/
    ├── pom.xml                    <-- Dependencias: Spring Web, Data MongoDB
    ├── src/main/resources/application.yml
    ├── src/main/java/com/microsmart/personalizacion/PersonalizacionApplication.java <-- CQRS (2 Repositorios Mongo)
    └── Dockerfile
