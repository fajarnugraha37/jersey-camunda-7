#!/bin/sh
set -eu

mc alias set sentinel "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc mb --ignore-existing "sentinel/${MINIO_EVIDENCE_BUCKET}"
