#!/bin/bash

# ============ CONFIG SERVER ============
export CONFIG_URL="http://localhost:8888"
export CONFIG_USERNAME="maxvpire"
export CONFIG_PASSWORD="h(wuP}}&R8Jk1RT/0KBT"

# ============ DATABASE CONFIGURATIONS ============
# Calendar Service Database
export CALENDAR_PGURL="jdbc:postgresql://ep-silent-art-a1qhy578-pooler.ap-southeast-1.aws.neon.tech/fluxdb?sslmode=require"
export CALENDAR_PGUSER="neondb_owner"
export CALENDAR_PGPASSWORD="npg_B2bnm7dvKlXF"

# User Service Database
export USER_PGURL="jdbc:postgresql://ep-silent-art-a1qhy578-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"
export USER_PGUSER="neondb_owner"
export USER_PGPASSWORD="npg_B2bnm7dvKlXF"

# ============ REDIS CONFIGURATION ============
export REDIS_HOST="localhost"
export REDIS_PORT="6379"

# ============ KEYCLOAK/JWT CONFIGURATION ============
export ISSUER_URL="http://localhost:8080/realms/flux"
export JWT_SET_URL="http://keycloak:8080/realms/flux/protocol/openid-connect/certs"
export JWT_SECRET="Uul0GYg0bLCvJQFj8OzDnqtQb4UJB14zvxkpt75eCVWwIBgPDI3fSMNHiMN6IX0hgCBr8B8pb5R1/RaFEi5IFA=="
export APP_JWT_URL="http://localhost:8083"
export JWT_EXPIRATION="15552000000"

# ============ EMAIL CONFIGURATION ============
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="maxvpire@gmail.com"
export MAIL_PASSWORD="zdxx wlvc fhsd imdp"

# ============ KAFKA CONFIGURATION ============
export KAFKA_BOOTSTRAP_SERVERS="localhost:9093"

# ============ GOOGLE OAUTH CONFIGURATION ============
export GOOGLE_CLIENT_ID="916357634266-l9qe40n10k4e5vanvp0n3n70edi51khp.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="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"
export GOOGLE_REDIRECT_URI="http://localhost:8082/auth/google/callback"

# ============ MINIO CONFIGURATION ============
export MINIO_ENDPOINT="http://localhost:9000"
export MINIO_ACCESS_KEY="maxvpirea"
export MINIO_SECRET_KEY="Max26156@vpire"
export MINIO_BUCKET="attachments"

# ============ EUREKA CONFIGURATION ============
export EUREKA_SERVICE_URL="http://localhost:8761/eureka"
export EUREKA_HOSTNAME="localhost"

# ============ GITHUB CONFIGURATION ============
export GITHUB_PUSH_TOKEN="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"

# ============ FRONTEND CONFIGURATION ============
export FRONTEND_URL="http://localhost:3000"

# ============ SERVICE PORTS ============
export USER_MANAGEMENT_PORT="8081"
export CALENDARS_PORT="8082"
export GATEWAY_PORT="8083"
export DISCOVERY_PORT="8761"
export CONFIG_PORT="8888"

# ============ GITLAB CONFIGURATION ============
export GITLAB_PASSWORD="a@N8@KZShAJ%8TZ"

# ============ ZOOM CONFIGURATION ============
export ZOOM_CLIENT_ID="mVwo1Ug2SNOh1yLQ6IGMXw"
export ZOOM_CLIENT_SECRET="f4MlanvBI3vsoi9dprGSeDcflctWw9hu"
export ZOOM_CLIENT_REDIRECT_URL="http://localhost:8083/zoom/callback"

# ============ SPRING PROFILES ============
export SPRING_PROFILES_ACTIVE="dev"

# ============ JAVA OPTIONS ============
export JAVA_OPTS="-Xmx512m -Xms256m -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"

# ============ LOGGING ============
export LOGGING_LEVEL_ROOT="INFO"
export LOGGING_LEVEL_COM_YOURCOMPANY="DEBUG"

echo "✅ All environment variables have been exported!"
echo "🔑 Total variables set: 37"