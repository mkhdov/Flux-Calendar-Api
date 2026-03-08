# Deploy Flux-cal to Render (Free Tier)

This guide is for deploying this repository on Render using mostly free services.

As of February 15, 2026, Render free web services can spin down on inactivity, and free web services cannot receive private network traffic. For this project, that means microservices must call each other via public `onrender.com` URLs unless you move to paid plans.

## 1. What You Are Deploying

Backend services in this repo:

1. `flux-config-server` (Spring Cloud Config server)
2. `discovery-service` (Eureka server)
3. `user-management-service`
4. `calendar-service`
5. `gateway` (public API entry point)

Frontend:

1. `flux-vue` (static site)

## 2. Important Free-Tier Constraints

1. Free web services sleep after inactivity. First request can be slow.
2. Free web services cannot receive private network traffic.
3. SMTP ports `25`, `465`, `587` are blocked on Render outbound traffic.
4. Free PostgreSQL is limited (small storage and expires if inactive).

Impact on your app:

1. Use public URLs for Config and Eureka.
2. Do not use Gmail SMTP (`587`) on Render free. Use SMTP provider on `2525` or disable mail-dependent flows.
3. Expect cold starts.

## 3. Before You Deploy (Security Cleanup)

Your config files currently contain real secrets (DB, mail, Google, Zoom, JWT). Do this first:

1. Rotate all exposed secrets.
2. Stop storing secrets in repo config files.
3. Keep only placeholders in Git.
4. Put real values into Render environment variables.

Files that currently include sensitive values:

1. `services/flux-config-server/src/main/resources/configurations/user-management-service.yml`
2. `services/flux-config-server/src/main/resources/configurations/calendars-service.yml`
3. `services/flux-config-server/src/main/resources/configurations/gateway-service.yml`

## 4. External Dependencies You Need

Minimum practical setup:

1. PostgreSQL: Render managed Postgres (free).
2. Redis: Render Key Value (free).
3. Kafka: external free provider (or paid provider). Render has no managed free Kafka equivalent.
4. S3-compatible object storage for attachments (Cloudflare R2 / MinIO external / any S3-compatible).
5. SMTP provider with port `2525` (Mailtrap, SendGrid SMTP relay, etc.).

## 5. Prepare Database Schemas

Because free Postgres is limited, use one database with two schemas.

Connect once and run:

```sql
CREATE SCHEMA IF NOT EXISTS user_mgmt;
CREATE SCHEMA IF NOT EXISTS calendar;
```

Use schema-specific JDBC URLs:

1. User service URL suffix: `&currentSchema=user_mgmt`
2. Calendar service URL suffix: `&currentSchema=calendar`

Also set Flyway schema env vars per service (listed below).

## 6. Create Render Services (Order Matters)

Create these as **Web Service** from your GitHub repo, each with Docker runtime:

1. `flux-config-server`
2. `discovery-service`
3. `user-management-service`
4. `calendar-service`
5. `gateway` (public API)

Create frontend as **Static Site**:

1. `flux-vue`

## 7. Service Setup Details

For each backend service:

1. Runtime: `Docker`
2. Root Directory: service folder (for example `services/gateway`)
3. Dockerfile Path: `./Dockerfile`
4. Health Check Path: `/actuator/health` (or service-specific health endpoint if different)
5. Environment: add `SERVER_PORT=${PORT}`

Note: your Dockerfiles expose fixed ports, but Render routes traffic using `PORT`; Spring must read `SERVER_PORT`.

## 8. Required Environment Variables

Use Render Dashboard -> service -> Environment.

### 8.1 `flux-config-server`

Set:

1. `SERVER_PORT=${PORT}`
2. `SPRING_PROFILES_ACTIVE=native`
3. `SPRING_SECURITY_USER_NAME=<config_user>`
4. `SPRING_SECURITY_USER_PASSWORD=<config_password>`

### 8.2 `discovery-service`

Set:

1. `SERVER_PORT=${PORT}`
2. `SPRING_CONFIG_IMPORT=optional:configserver:https://<config-server>.onrender.com`
3. `SPRING_CLOUD_CONFIG_USERNAME=<config_user>`
4. `SPRING_CLOUD_CONFIG_PASSWORD=<config_password>`
5. `EUREKA_INSTANCE_HOSTNAME=<discovery-service>.onrender.com`
6. `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://<discovery-service>.onrender.com/eureka`

### 8.3 `user-management-service`

Set:

1. `SERVER_PORT=${PORT}`
2. `SPRING_CONFIG_IMPORT=optional:configserver:https://<config-server>.onrender.com`
3. `SPRING_CLOUD_CONFIG_USERNAME=<config_user>`
4. `SPRING_CLOUD_CONFIG_PASSWORD=<config_password>`
5. `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://<discovery-service>.onrender.com/eureka`
6. `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require&currentSchema=user_mgmt`
7. `SPRING_DATASOURCE_USERNAME=<db_user>`
8. `SPRING_DATASOURCE_PASSWORD=<db_pass>`
9. `SPRING_FLYWAY_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require&currentSchema=user_mgmt`
10. `SPRING_FLYWAY_USER=<db_user>`
11. `SPRING_FLYWAY_PASSWORD=<db_pass>`
12. `SPRING_FLYWAY_DEFAULT_SCHEMA=user_mgmt`
13. `SPRING_FLYWAY_SCHEMAS=user_mgmt`
14. `SPRING_DATA_REDIS_HOST=<redis_host>`
15. `SPRING_DATA_REDIS_PORT=<redis_port>`
16. `SPRING_KAFKA_BOOTSTRAP_SERVERS=<kafka_bootstrap>`
17. `APP_JWT_SECRET=<base64_hs512_secret>`
18. `APP_JWT_EXPIRATION=15552000000`
19. `APP_FRONTEND_URL=https://<frontend>.onrender.com`
20. `SPRING_MAIL_HOST=<smtp_host>`
21. `SPRING_MAIL_PORT=2525`
22. `SPRING_MAIL_USERNAME=<smtp_user>`
23. `SPRING_MAIL_PASSWORD=<smtp_pass>`

### 8.4 `calendar-service`

Set:

1. `SERVER_PORT=${PORT}`
2. `SPRING_CONFIG_IMPORT=optional:configserver:https://<config-server>.onrender.com`
3. `SPRING_CLOUD_CONFIG_USERNAME=<config_user>`
4. `SPRING_CLOUD_CONFIG_PASSWORD=<config_password>`
5. `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://<discovery-service>.onrender.com/eureka`
6. `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require&currentSchema=calendar`
7. `SPRING_DATASOURCE_USERNAME=<db_user>`
8. `SPRING_DATASOURCE_PASSWORD=<db_pass>`
9. `SPRING_FLYWAY_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require&currentSchema=calendar`
10. `SPRING_FLYWAY_USER=<db_user>`
11. `SPRING_FLYWAY_PASSWORD=<db_pass>`
12. `SPRING_FLYWAY_DEFAULT_SCHEMA=calendar`
13. `SPRING_FLYWAY_SCHEMAS=calendar`
14. `SPRING_DATA_REDIS_HOST=<redis_host>`
15. `SPRING_DATA_REDIS_PORT=<redis_port>`
16. `SPRING_KAFKA_BOOTSTRAP_SERVERS=<kafka_bootstrap>`
17. `APP_JWT_SECRET=<same_base64_hs512_secret_as_user_and_gateway>`
18. `MINIO_ENDPOINT=<s3_compatible_endpoint>`
19. `MINIO_ACCESS_KEY=<access_key>`
20. `MINIO_SECRET_KEY=<secret_key>`
21. `MINIO_BUCKET=attachments`
22. `GOOGLE_CALENDAR_CLIENT_ID=<google_client_id>`
23. `GOOGLE_CALENDAR_CLIENT_SECRET=<google_client_secret>`
24. `GOOGLE_CALENDAR_REDIRECT_URI=https://<gateway>.onrender.com/auth/google/callback`
25. `ZOOM_CLIENT_ID=<zoom_client_id>`
26. `ZOOM_CLIENT_SECRET=<zoom_client_secret>`
27. `ZOOM_CLIENT_REDIRECT_URI=https://<gateway>.onrender.com/zoom/callback`

### 8.5 `gateway`

Set:

1. `SERVER_PORT=${PORT}`
2. `SPRING_CONFIG_IMPORT=optional:configserver:https://<config-server>.onrender.com`
3. `SPRING_CLOUD_CONFIG_USERNAME=<config_user>`
4. `SPRING_CLOUD_CONFIG_PASSWORD=<config_password>`
5. `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=https://<discovery-service>.onrender.com/eureka`
6. `EUREKA_INSTANCE_HOSTNAME=<gateway>.onrender.com`
7. `APP_JWT_SECRET=<same_base64_hs512_secret>`

### 8.6 `flux-vue` static site

Build settings:

1. Root Directory: `flux-vue`
2. Build Command: `npm ci && npm run build`
3. Publish Directory: `dist`

Set frontend env var(s) as needed for API base URL (for example Vite env var if your code uses one).

## 9. Required Code Adjustments for Render

These are strongly recommended for reliable deploy:

1. Replace hardcoded localhost CORS in gateway:
   - Current hardcoded value in `services/gateway/src/main/java/com/maxvpire/gateway/config/SecurityConfig.java:74`
   - Make it configurable via env/property (for example `app.frontend.url`).
2. Ensure all services can run if Config Server is briefly unavailable on startup (retry/backoff).
3. Keep config values out of config files and use env overrides only.

## 10. Deploy Sequence

1. Deploy `flux-config-server` and wait for healthy.
2. Deploy `discovery-service` and wait for healthy.
3. Deploy `user-management-service` and `calendar-service`.
4. Deploy `gateway`.
5. Deploy `flux-vue`.
6. Update frontend API base URL to gateway public URL.

## 11. Smoke Tests

Run after deploy:

1. `GET https://<gateway>.onrender.com/actuator/health`
2. `POST https://<gateway>.onrender.com/api/auth/signup`
3. `POST https://<gateway>.onrender.com/api/auth/login`
4. `GET https://<gateway>.onrender.com/api/auth/me` with `Authorization: Bearer <token>`
5. `GET https://<gateway>.onrender.com/calendars/v3/api-docs`

## 12. Known Free-Tier Failure Modes

1. Cold starts cause gateway timeouts to sleeping upstream services.
2. Kafka outages break user-sync flow between services.
3. SMTP on blocked ports fails signup/verification/login emails.
4. Free Postgres expiration can wipe test data if inactive too long.

## 13. If You Want This Stable in Production

Use paid plan for at least:

1. `gateway`, `user-management-service`, `calendar-service` (always on)
2. Managed Redis
3. Managed/hosted Kafka
4. Persistent object storage
5. Optional: remove Eureka/Config Server and use direct env-based configuration to simplify.

## References

1. Render free instances and limits: https://render.com/docs/free
2. Render networking limits (private network note): https://render.com/docs/private-network
3. Render outbound SMTP restrictions: https://render.com/docs/outbound-traffic#blocked-ports
4. Render PostgreSQL free plan limits: https://render.com/docs/postgresql-creating-connecting#free-plan-limitations
