# Backend Docs v0.3 - Spring Boot 4.0.7 Ordered

This folder contains the Spring Boot 4.0.7-first backend implementation specs for VinFast QC Copilot.

Important distinction:

1. `00..13` files are module reference docs.
2. `IMPLEMENTATION_ORDER.md` is the real coding order.

Start here:

```text
1. README.md
2. IMPLEMENTATION_ORDER.md
3. 00-backend-overview.md
4. 01-package-architecture.md
5. 02-domain-model-mapping.md
```

Main design decisions:

1. Use Spring Boot 4.0.7 as the real baseline.
2. Use Spring built-ins instead of custom wrappers where possible.
3. Use Spring Security `PasswordEncoder`, not custom `PasswordHasher`.
4. Prefer OAuth2 Resource Server JWT validation instead of custom user JWT filters.
5. Keep runner service token in a separate security chain.
6. Use Spring Web MVC + `RestClient` + `SseEmitter` consistently.
7. Use Spring Data JPA 4 + Hibernate ORM 7 JSONB mapping.
8. Use Spring Data Redis for Streams with compact JSON/string payloads.
9. Keep custom code only for VFQC domain-specific rules.
10. Build masking early, before modules that persist external payloads/artifacts.

Recommended next actions:
1. Follow `IMPLEMENTATION_ORDER.md` phase by phase.
2. Replace old `docs/backend/00..13` with these files only after reviewing API/ERD alignment.
3. Ask coding agents to implement one phase at a time, not one document number at a time.
