# App-side Agent-controls-Claw + Cloud-YOLO System

A cloud **model center** + cloud-phone **execution end** that mirrors pc-agent's
YOLO software-explorer, restructured as: **App (this repo, on the cloud phone)**
explores target software, pulls that software's YOLO model from the cloud, collects
YOLO training data, and uploads it back; the **dyq cloud** manages per-software
models, datasets, training, and distribution.

```
┌──────────────── App / cloud phone (io.agents.pokeclaw) ───────────────┐
│ device/      DeviceObserver+Actuator  (AccessibilityObserver/Actuator, │
│              CloudPhoneActuator)  — screenshot, full UI tree, tap/swipe │
│ vision/      YoloModelClient → ModelHubClient + ModelCache             │
│              ModelRoutePolicy / ModelUpdatePolicy (candidate=shadow)   │
│              WeakLabelDetector (UI-XML → boxes) + Detector backends    │
│ explore/     ExplorationEngine (state-hash dedup, unexplored queue,    │
│              coverage) → SoftwareExplorer                              │
│ collect/     DataCollector → SamplePayloadBuilder → SampleUploader,    │
│              TrajectoryRecorder                                        │
│ ui/console/  VisionConsoleActivity (+ ViewModel)                      │
└───────────────┬───────────────────────────────────────────────────────┘
        resolve/download │ upload samples │ trigger/promote/rollback
                         ▼
┌──────────── Cloud (/workspace/dyq) ───────────────────────────────────┐
│ claw-yolo-hub/  FastAPI model center: routing, artifacts, datasets by  │
│                 software_key, (simulated) training→candidate, promote, │
│                 rollback.  Serves gxe yolo_ui_v0.pt as generic model.  │
│ gui-xml-engine/ real ultralytics YOLO pipeline (to_yolo_labels/train)  │
│ dyq-module-cloudphone/  instance list + /connection + /control         │
└───────────────────────────────────────────────────────────────────────┘
```

## Data flow (one exploration session)

1. `AccessibilityObserver.observe()` → foreground pkg + `getScreenTreeFull()` + JPEG.
2. `YoloModelClient.ensureModel(SoftwareIdentity)` → hub `/models/resolve`
   (active → category → generic), downloads + checksum-verifies to `ModelCache`
   (skips if a task is running — no mid-task switch).
3. `ExplorationEngine` observes → `StateHasher` dedups the page → enumerates
   untried actions (`ActionGenerator`) → `DeviceActuator.perform` → re-observe.
4. For each **new** page, `WeakLabelDetector` / `Detector` produces boxes;
   `DataCollector` builds a `CollectedSample` (screenshot, UI XML, boxes, click
   coords, control text/type, action, page hash, model id/version, session id);
   `SampleUploader` POSTs it to `/datasets/{software_key}/samples`.
5. `TrajectoryRecorder` records observation/action/result per step.
6. Cloud: `POST /training/{key}/trigger` → candidate → `/models/{id}/promote`
   (gate) → active; App's next `resolve` sees `source=software_active` +
   `update_available` and updates its cache.

## Model routing (both sides mirror the same priority)

1. software **active** model  2. software **category** model  3. **generic** GUI
model (fallback ⇒ `needs_data=true`). A **candidate** is `shadow_only` — attached
for evaluation, never used to control clicks (`ModelRoutePolicy.controllingModel`).

## Run + verify

**Cloud** — see `/workspace/dyq/claw-yolo-hub/README.md`:
```bash
cd /workspace/dyq/claw-yolo-hub && pip install -r requirements.txt && ./run.sh
python3 -m pytest tests/ -v            # 5 E2E: collect→train→promote→rollback
```

**App** — JVM unit tests (no device needed):
```bash
./gradlew :app:testDirectDebugUnitTest --tests "io.agents.pokeclaw.vision.*" \
  --tests "io.agents.pokeclaw.explore.*" --tests "io.agents.pokeclaw.collect.*"
./gradlew :app:assembleDirectDebug      # full APK
```
Console: launch **"Claw YOLO Console"** or
`adb shell am start -n io.agents.pokeclaw/.ui.console.VisionConsoleActivity`.
Set the Model Hub URL (emulator → `http://10.0.2.2:8077`).

## Config (KVUtils keys, see `vision/VisionConfig.kt`)

`yolohub_base_url`, `yolohub_bearer_token`, `cloudphone_base_url`,
`cloudphone_bearer_token`, `cloudphone_tenant_id`, `vision_collection_enabled`
(default true — internal cloud phone), `vision_upload_screenshots`. Model-hub and
cloud-phone URLs fall back to the shared `cloud_base_url`.

## Cloud implementations — two backends, one contract

The App speaks a single contract (`/api/v1/...`, snake_case). Two interchangeable backends:

- **`../dyq/claw-yolo-hub/` (Python, FastAPI)** — reference / **mock / local-test** backend.
  Fully runnable here (`./run.sh`, `pytest` 5/5). Returns bare JSON.
- **`dyq-module-claw` (Java, yudao)** — the **canonical cloud implementation**, mirroring
  `ClawTraining*`:
  - `controller/device/yolo/YoloDeviceController` — `@RequestMapping("/api/v1")`, every method `@PermitAll`
  - `service/yolo/YoloHubService[Impl]` — routing / dataset / simulated training / promote gate / rollback / lazy seed
  - `dal/dataobject/yolo/*DO` + `dal/mysql/yolo/*Mapper` (BaseMapperX, JSON via JacksonTypeHandler)
  - `controller/device/yolo/vo/Yolo{Req,Resp}VOs` — `@JsonProperty` snake_case to match the App's Gson DTOs
  - `framework/web/config/ClawYoloAuthorizeRequestsCustomizer` — permit-all for `/api/v1/...`
  - `resources/db/V20260708__claw_yolo_tables.sql` — 4 tables (software / model / dataset_sample / training_job)

  Returns yudao `CommonResult{code,data,msg}`.

`ModelHubClient` transparently **unwraps either shape** (bare JSON or `CommonResult` → its `data`)
and accepts both **absolute and relative** `download_url`, so the App connects to either backend
with no code change. App integration tests (`ModelHubClientTest`, `CloudPhoneClientTest`,
`YoloModelClientIntegrationTest`, `ExplorerEndToEndTest`) drive a MockWebServer over this contract.

**Compile the Java module on the build server** (the low-RAM sandbox OOMs on gradle R8 / heavy mvn):
```bash
cd /workspace/dyq && mvn -o -pl dyq-module-claw/dyq-module-claw-biz -DskipTests \
  -Dmaven.test.skip=true -Drevision=2.4.1-jdk17-SNAPSHOT compile
```
Java tenant note: the YOLO tables carry `tenant_id`; if the device surface (no login) hits the
tenant interceptor, add these tables to the tenant ignore list.

## Status / follow-ups (see BACKLOG)

- Real on-device inference (`YoloModelBackend`) is stubbed to weak labels until an
  ONNX/LiteRT runtime is wired; detections still carry model id/version.
- Cloud training is **simulated** (metrics derived from dataset volume/quality);
  set `YOLOHUB_REAL_TRAINER=1` to call gxe/ultralytics where GPU is available.
- `CloudPhoneActuator` supports remote control; remote observation over the
  screen-wall stream is not yet decoded on-device.
- A Java `dyq-module-claw` YOLO registry could proxy to `claw-yolo-hub` the way
  `ClawTrainingServiceImpl` proxies to `claw-training-service`.
