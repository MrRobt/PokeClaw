#!/usr/bin/env bash
# 薄壳：把 P3-01 端云证据任务转给 Python runner
# 真实实现: scripts/pokeclaw_p3p1_runner.py
exec python3 "$(cd "$(dirname "$0")"/.. && pwd)/scripts/pokeclaw_p3p1_runner.py" "$@"
