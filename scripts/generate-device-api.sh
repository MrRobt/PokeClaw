#!/bin/bash
# 生成设备API Kotlin客户端代码

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SPEC_FILE="/mnt/e/code/dyq/api-contracts/device.openapi.yaml"
OUTPUT_DIR="$PROJECT_DIR/app/src/main/java/io/agents/pokeclaw/cloud/api"

# 清理旧代码
echo "清理旧API代码..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# 使用OpenAPI Generator生成Kotlin代码
echo "正在从 $SPEC_FILE 生成Kotlin代码..."
openapi-generator-cli generate \
  -i "$SPEC_FILE" \
  -g kotlin \
  -o "$OUTPUT_DIR" \
  --library jvm-retrofit2 \
  --additional-properties=useCoroutines=true \
  --additional-properties=moshiCodeGen=true \
  --additional-properties=packageName=io.agents.pokeclaw.cloud.api \
  --skip-validate-spec

echo "生成完成，输出目录: $OUTPUT_DIR"
