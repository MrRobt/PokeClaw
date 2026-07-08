# PokeClaw AI Index

This is the repo map for coding agents. Keep canonical information in existing files; do not create new root docs when one of these files already owns the topic.

## Canonical Root Docs

| File | Owns |
|------|------|
| `README.md` | Public product direction, roadmap, platform constraints, benchmark claims, changelog |
| `CLAUDE.md` | Agent/project working rules |
| `QA_CHECKLIST.md` | QA methodology, release gate, test cases, debug changelog |
| `RELEASING.md` | Release signing, tag workflow, stable APK publishing |
| `BACKLOG.md` | Prioritized bugs, features, QA gaps, ideas |
| `ARCHITECTURE_RECONSTRUCTION.md` | Historical architecture reconstruction plan and refactor guardrails |
| `CLOUD_SUBSYSTEM_BOUNDARY.md` | Boundary/quarantine + invariants for the on-device cloud/端云 subsystem (off-by-default, unverified) |
| `CLA.md` | Contributor license agreement |
| `AI_INDEX.md` | This repo map |

## Directory Map

| Path | Purpose |
|------|---------|
| `app/src/main/java/io/agents/pokeclaw/` | Android app source |
| `app/src/main/java/io/agents/pokeclaw/vision/` | Cloud-YOLO client: model routing/cache/update, weak-label detector, descriptors |
| `app/src/main/java/io/agents/pokeclaw/explore/` | Auto software-explorer: state-hash dedup, action frontier, coverage, `SoftwareExplorer` |
| `app/src/main/java/io/agents/pokeclaw/collect/` | YOLO data collection: `CollectedSample`, payload builder, uploader, trajectory |
| `app/src/main/java/io/agents/pokeclaw/device/` | Device actuator/observer (local Accessibility + remote CloudPhone) |
| `app/src/main/java/io/agents/pokeclaw/cloud/modelhub/` | Model Hub Retrofit/OkHttp client + DTOs |
| `app/src/main/java/io/agents/pokeclaw/cloud/cloudphone/` | dyq cloud-phone instance client (list/connect/control) |
| `app/src/main/java/io/agents/pokeclaw/ui/console/VisionConsoleActivity.kt` | Cloud-YOLO management console |
| `docs/AGENT_CLAW_YOLO_SYSTEM.md` | App↔cloud Agent-controls-Claw + per-software YOLO model center (design/run/verify) |
| `../dyq/claw-yolo-hub/` | Cloud YOLO model center (FastAPI): routing, datasets, training, publish/rollback |
| `app/src/main/assets/playbooks/` | Built-in playbooks used by the agent harness |
| `app/src/test/` | JVM/unit regression tests |
| `scripts/` | QA and automation scripts |
| `.github/workflows/` | CI, CLA check, signed release workflow |
| `docs/` | GitHub Pages site and public site assets |
| `docs/screenshots/` | Screenshot assets used by the landing page |
| `demo/` | Legacy demo GIF assets |
| `Screenshots/` | Legacy screenshot assets |
| `prototype/` | Historical UI prototypes |
| `mockup/` | Early interactive mockups |
| `signatures/` | CLA signature state |

## Direction Rules

- PokeClaw is a generic Android mobile-agent harness with a product shell on top.
- Prefer fixing deterministic harness/runtime/device problems before tuning one stochastic task.
- Keep prompts, tools, skills, and playbooks generic.
- Treat Cloud/Local exploratory task success as a repeated-trial metric, not a single-run truth.
- For GPU/local runtime reports, collect logs and keep CPU fallback truthful before changing backend selection.
