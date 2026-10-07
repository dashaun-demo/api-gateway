api-gateway is the front door of the insurance platform demo: a Spring
Cloud Gateway that discovers every downstream service through eureka-server
and routes /api/** paths to them with load-balanced lb:// routes.

Part of the demo platform: Java 8, Spring Boot 2.7.0, Spring Cloud 2021.0.3.
This is the only service with a public route (api-gateway.dashaun.group);
everything else stays on internal routes.

## Landing page

GET / renders the platform dashboard: every service from gateway.services,
discovered through Eureka, with its status, Spring Boot version, Java version,
startup time, and uptime - all read live from each service's /actuator/info.
The page refreshes itself every 10 seconds, so redeploying a service shows up
as a new version and a fresh 'started at' time, in front of the audience.

## Routing

/actuator routes (lb://service-name) are configured in application.properties:
/api/customers/**, /api/quotes/**, /api/underwriting/**, /api/policies/**,
/api/claims/**, /api/payments/**, /api/documents/**, /api/notifications/**,
and /api/agents/**.

## Run

    ./mvnw spring-boot:run

Listens on port 8080, or the PORT environment variable when deployed to Cloud
Foundry. Point it at a local registry for development:

    EUREKA_URL=http://localhost:8090/eureka EUREKA_INSTANCE_HOSTNAME=localhost ./mvnw spring-boot:run

