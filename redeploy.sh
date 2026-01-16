#!/bin/bash
set -e

echo "=== Rebuilding Komga Docker image ==="
docker build -t komga-local:latest .

echo ""
echo "=== Stopping and removing existing container ==="
docker stop komga 2>/dev/null || true
docker rm komga 2>/dev/null || true

echo ""
echo "=== Starting new container ==="
docker run -d \
  --name komga \
  -p 8090:25600 \
  -v /home/omar/komga/config:/config \
  -v /home/omar/komga/data:/data \
  -v "/home/omar/OneDrive/Documents/Books:/library" \
  -e PUID=1000 \
  -e PGID=1000 \
  --restart unless-stopped \
  komga-local:latest

echo ""
echo "=== Done! Komga is running at http://localhost:8090 ==="
docker logs -f komga
