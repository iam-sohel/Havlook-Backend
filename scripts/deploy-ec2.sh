
#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

# -----------------------------------------
# HAVLOOK DEPLOYMENT CONFIGURATION
# -----------------------------------------

AWS_REGION="ap-south-2"
AWS_ACCOUNT_ID="965825500661"
ECR_REPOSITORY="havlook/backend"

REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

CONTAINER_NAME="havlook-backend"
BACKUP_CONTAINER="havlook-backend-previous"

DEPLOY_DIR="/opt/havlook"
ENV_FILE="${DEPLOY_DIR}/runtime.env"

PARAMETER_NAME="/havlook/backend/runtime-env"

IMAGE_TAG="${1:?Image tag is required}"
PARAMETER_VERSION="${2:?Parameter version is required}"

if [[ ! "$IMAGE_TAG" =~ ^sha-[a-f0-9]{40}$ ]]; then
    echo "ERROR: Invalid image tag."
    exit 1
fi

if [[ ! "$PARAMETER_VERSION" =~ ^[0-9]+$ ]]; then
    echo "ERROR: Invalid parameter version."
    exit 1
fi

IMAGE="${REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"

echo "=== HAVLOOK DEPLOYMENT STARTED ==="
echo "Image: ${IMAGE}"

# -----------------------------------------
# 1. PROTECTED DEPLOYMENT DIRECTORY
# -----------------------------------------

install -d -m 700 -o root -g root "$DEPLOY_DIR"

exec 9>"${DEPLOY_DIR}/deploy.lock"

if ! flock -w 600 9; then
    echo "ERROR: Another deployment is running."
    exit 1
fi

TEMP_ENV="$(mktemp "${DEPLOY_DIR}/runtime.env.XXXXXX")"

ROLLBACK_REQUIRED=0
OLD_CONTAINER_RENAMED=0
NEW_CONTAINER_CREATED=0

cleanup() {
    STATUS=$?

    trap - EXIT
    set +e

    rm -f "$TEMP_ENV"

    if [[ "$STATUS" -ne 0 && "$ROLLBACK_REQUIRED" -eq 1 ]]; then
        echo "Deployment failed. Attempting rollback."

        if [[ "$NEW_CONTAINER_CREATED" -eq 1 ]]; then
            docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1
        fi

        if [[ "$OLD_CONTAINER_RENAMED" -eq 1 ]]; then
            docker rename "$BACKUP_CONTAINER" "$CONTAINER_NAME"

            if docker start "$CONTAINER_NAME" >/dev/null; then
                echo "Previous container restarted."
            else
                echo "ERROR: Previous container could not restart."
            fi
        fi
    fi

    exit "$STATUS"
}

trap cleanup EXIT

# -----------------------------------------
# 2. RETRIEVE ENCRYPTED RUNTIME SECRETS
# -----------------------------------------

echo "Retrieving encrypted runtime configuration..."

aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "${PARAMETER_NAME}:${PARAMETER_VERSION}" \
    --with-decryption \
    --query "Parameter.Value" \
    --output text > "$TEMP_ENV"

chmod 600 "$TEMP_ENV"

# Validate required environment variables.
# Never print secret values.

python3 - "$TEMP_ENV" <<'PY'
import sys
from pathlib import Path

required = {
    "JWT_SECRET",
    "DB_PASSWORD",
    "RAZORPAY_KEY_ID",
    "RAZORPAY_KEY_SECRET",
    "RAZORPAY_WEBHOOK_SECRET",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "MSG91_AUTH_KEY",
    "SUPABASE_SECRET_KEY",
    "SUPABASE_STORAGE_AUTH_KEY",
    "SANDBOX_API_KEY",
    "SANDBOX_API_SECRET",
    "NUMVERIFY_ACCESS_KEY",
    "HUNTER_API_KEY",
    "APP_CORS_ALLOWED_ORIGINS",
}

path = Path(sys.argv[1])
values = {}

for line in path.read_text().splitlines():
    if not line:
        continue

    key, separator, value = line.partition("=")

    if not separator or key not in required:
        raise SystemExit("ERROR: Invalid runtime configuration.")

    if key in values:
        raise SystemExit("ERROR: Duplicate environment variable.")

    values[key] = value

missing = sorted(
    key for key in required
    if not values.get(key)
)

if missing:
    raise SystemExit(
        "ERROR: Missing environment variables: "
        + ", ".join(missing)
    )

print("Runtime configuration validated successfully.")
PY

# -----------------------------------------
# 3. LOGIN TO ECR AND PULL NEW IMAGE
# -----------------------------------------

echo "Authenticating to Amazon ECR..."

aws ecr get-login-password \
    --region "$AWS_REGION" \
    | docker login \
        --username AWS \
        --password-stdin "$REGISTRY"

echo "Pulling new ARM64 image..."

docker pull "$IMAGE"

ARCH="$(docker image inspect \
    --format '{{.Architecture}}' "$IMAGE")"

if [[ "$ARCH" != "arm64" ]]; then
    echo "ERROR: Unexpected image architecture."
    exit 1
fi

echo "ARM64 image verified."

# -----------------------------------------
# 4. PRESERVE PREVIOUS CONTAINER
# -----------------------------------------

if docker container inspect "$BACKUP_CONTAINER" \
    >/dev/null 2>&1; then

    docker rm -f "$BACKUP_CONTAINER" >/dev/null
fi

ROLLBACK_REQUIRED=1

if docker container inspect "$CONTAINER_NAME" \
    >/dev/null 2>&1; then

    echo "Preserving previous container..."

    docker rename "$CONTAINER_NAME" "$BACKUP_CONTAINER"

    OLD_CONTAINER_RENAMED=1

    docker stop -t 30 "$BACKUP_CONTAINER" >/dev/null
fi

# -----------------------------------------
# 5. START NEW CONTAINER
# -----------------------------------------

echo "Starting new HavLook backend..."

docker create \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    --env-file "$TEMP_ENV" \
    -p 127.0.0.1:8080:8080 \
    "$IMAGE" >/dev/null

NEW_CONTAINER_CREATED=1

docker start "$CONTAINER_NAME" >/dev/null

# -----------------------------------------
# 6. VERIFY APPLICATION STARTUP
# -----------------------------------------

echo "Waiting for application startup..."

READY=0

for attempt in $(seq 1 60); do

    STATE="$(docker inspect \
        --format '{{.State.Status}}' \
        "$CONTAINER_NAME")"

    RESTARTS="$(docker inspect \
        --format '{{.RestartCount}}' \
        "$CONTAINER_NAME")"

    if [[ "$STATE" == "exited" || "$STATE" == "dead" ]]; then
        echo "ERROR: Container stopped unexpectedly."
        exit 1
    fi

    if [[ "$RESTARTS" -gt 0 ]]; then
        echo "ERROR: Container restarted during startup."
        exit 1
    fi

    HTTP_CODE="$(curl \
        --silent \
        --output /dev/null \
        --write-out '%{http_code}' \
        --max-time 3 \
        http://127.0.0.1:8080/ || true)"

    if [[ "$HTTP_CODE" =~ ^[234][0-9]{2}$ ]]; then
        READY=1
        echo "Backend HTTP listener is responding."
        break
    fi

    sleep 5
done

if [[ "$READY" -ne 1 ]]; then
    echo "ERROR: Backend startup timed out."
    exit 1
fi

# Verify container remains stable.
sleep 30

STATE="$(docker inspect \
    --format '{{.State.Status}}' \
    "$CONTAINER_NAME")"

RESTARTS="$(docker inspect \
    --format '{{.RestartCount}}' \
    "$CONTAINER_NAME")"

if [[ "$STATE" != "running" || "$RESTARTS" -ne 0 ]]; then
    echo "ERROR: Container stability check failed."
    exit 1
fi

# -----------------------------------------
# 7. FINALIZE SUCCESSFUL DEPLOYMENT
# -----------------------------------------

mv -f "$TEMP_ENV" "$ENV_FILE"

chmod 600 "$ENV_FILE"

ROLLBACK_REQUIRED=0

echo "=== HAVLOOK DEPLOYMENT SUCCESSFUL ==="

docker ps \
    --filter "name=^/${CONTAINER_NAME}$" \
    --format "Container: {{.Names}} | Status: {{.Status}}"
