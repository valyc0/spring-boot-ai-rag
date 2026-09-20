#!/bin/bash
set -euo pipefail

curl -s -G "http://localhost:8080/api/es/query" \
  --data-urlencode "question=quali sono i dispositi di protezione" \
  --data-urlencode "topK=2" \
  --data-urlencode "department=operations" \
  --data-urlencode "langId=it-IT"