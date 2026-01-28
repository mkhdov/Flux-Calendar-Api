#!/bin/bash

SERVICES=("calendar-service" "discovery-service" "flux-config-server" "gateway" "user-management-service")
BASE_DIR="/home/maxvpire/Desktop/Flux-cal/services"

echo "Starting tests for all services..."

for service in "${SERVICES[@]}"; do
    echo "--------------------------------------------------"
    echo "Running tests for $service..."
    cd "$BASE_DIR/$service"
    ./mvnw test > test_output.log 2>&1
    if [ $? -eq 0 ]; then
        echo "$service: PASSED"
    else
        echo "$service: FAILED"
        # Extract failure summary if possible
        grep "Tests run:" test_output.log | tail -n 1
    fi
done

echo "--------------------------------------------------"
echo "Test run complete."
