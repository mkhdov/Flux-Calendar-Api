#!/bin/bash

echo "🔍 VERIFYING ENVIRONMENT VARIABLES"
echo "===================================="

# List all key variables
variables=(
    "CONFIG_URL" "CONFIG_USERNAME" 
    "CALENDAR_PGURL" "CALENDAR_PGUSER" "CALENDAR_PGPASSWORD"
    "USER_PGURL" "USER_PGUSER" "USER_PGPASSWORD"
    "REDIS_HOST" "REDIS_PORT"
    "JWT_SECRET" "APP_JWT_URL"
    "MAIL_HOST" "MAIL_USERNAME"
    "GOOGLE_CLIENT_ID" "GOOGLE_REDIRECT_URI"
    "MINIO_ENDPOINT" "MINIO_BUCKET"
    "EUREKA_SERVICE_URL"
    "FRONTEND_URL"
    "USER_MANAGEMENT_PORT" "CALENDARS_PORT" "GATEWAY_PORT"
    "ZOOM_CLIENT_ID" "ZOOM_CLIENT_REDIRECT_URL"
)

count=0
for var in "${variables[@]}"; do
    if [ -n "${!var}" ]; then
        echo "✅ $var is set"
        count=$((count + 1))
    else
        echo "❌ $var is NOT set"
    fi
done

echo "===================================="
echo "📊 Total variables verified: $count/${#variables[@]}"