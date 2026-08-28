import base64
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse

app = FastAPI(title="Safety Relay")
TOKEN = os.getenv("SAFETY_TOKEN", "CHANGE_ME")
DATA = Path(os.getenv("SAFETY_DATA", "data"))
RECORDINGS = DATA / "recordings"
LOCATIONS = DATA / "locations.jsonl"
STATUS = DATA / "status.json"
RECORDINGS.mkdir(parents=True, exist_ok=True)
DATA.mkdir(parents=True, exist_ok=True)


def auth(authorization: str | None):
    if authorization != f"Bearer {TOKEN}":
        raise HTTPException(status_code=401, detail="unauthorized")


def now_iso():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/api/v1/client/audio")
async def client_audio(
    request: Request,
    authorization: str | None = Header(default=None),
    x_meta: str | None = Header(default=None),
    x_device_id: str | None = Header(default=None),
):
    auth(authorization)
    if not x_meta:
        raise HTTPException(status_code=400, detail="missing metadata")
    try:
        metadata = json.loads(base64.b64decode(x_meta).decode("utf-8"))
        rid = metadata["id"]
    except Exception as exc:
        raise HTTPException(status_code=400, detail="bad metadata") from exc
    metadata["received_at"] = now_iso()
    metadata["device_id"] = x_device_id or "unknown"
    payload = await request.body()
    (RECORDINGS / f"{rid}.enc").write_bytes(payload)
    (RECORDINGS / f"{rid}.json").write_text(json.dumps(metadata), encoding="utf-8")
    return {"ok": True, "id": rid}


@app.post("/api/v1/client/location")
async def client_location(
    request: Request,
    authorization: str | None = Header(default=None),
    x_device_id: str | None = Header(default=None),
):
    auth(authorization)
    item = await request.json()
    item["device_id"] = x_device_id or "unknown"
    with LOCATIONS.open("a", encoding="utf-8") as f:
        f.write(json.dumps(item, separators=(",", ":")) + "\n")
    return {"ok": True}


@app.post("/api/v1/client/heartbeat")
async def client_heartbeat(
    request: Request,
    authorization: str | None = Header(default=None),
    x_device_id: str | None = Header(default=None),
):
    auth(authorization)
    item = await request.json()
    item["device_id"] = x_device_id or "unknown"
    item["last_seen"] = now_iso()
    STATUS.write_text(json.dumps(item), encoding="utf-8")
    return {"ok": True}


@app.get("/api/v1/viewer/recordings")
def viewer_recordings(authorization: str | None = Header(default=None)):
    auth(authorization)
    out = []
    for meta_file in RECORDINGS.glob("*.json"):
        try:
            meta = json.loads(meta_file.read_text(encoding="utf-8"))
            rid = meta.get("id") or meta_file.stem
            enc = RECORDINGS / f"{rid}.enc"
            if enc.exists():
                out.append({
                    "id": rid,
                    "started_at": meta.get("started_at", ""),
                    "ended_at": meta.get("ended_at", ""),
                    "encrypted_bytes": enc.stat().st_size,
                })
        except Exception:
            continue
    out.sort(key=lambda x: x.get("started_at", ""), reverse=True)
    return JSONResponse(out)


@app.get("/api/v1/viewer/audio/{rid}")
def viewer_audio(rid: str, authorization: str | None = Header(default=None)):
    auth(authorization)
    enc = RECORDINGS / f"{rid}.enc"
    meta = RECORDINGS / f"{rid}.json"
    if not enc.exists() or not meta.exists():
        raise HTTPException(status_code=404, detail="not found")
    meta_b64 = base64.b64encode(meta.read_bytes()).decode("ascii")
    return FileResponse(
        enc,
        media_type="application/octet-stream",
        filename=f"{rid}.enc",
        headers={"X-Meta": meta_b64, "Cache-Control": "no-store"},
    )


@app.get("/api/v1/viewer/locations")
def viewer_locations(
    after: str = "",
    limit: int = 200,
    authorization: str | None = Header(default=None),
):
    auth(authorization)
    if not LOCATIONS.exists():
        return JSONResponse([])
    items = []
    for line in LOCATIONS.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            item = json.loads(line)
            ts = item.get("ts", "")
            if after and ts <= after:
                continue
            items.append(item)
        except Exception:
            continue
    items.sort(key=lambda x: x.get("ts", ""))
    return JSONResponse(items[-max(1, min(limit, 1000)):])


@app.get("/api/v1/viewer/status")
def viewer_status(authorization: str | None = Header(default=None)):
    auth(authorization)
    if not STATUS.exists():
        return {"online": False, "last_seen": "never"}
    try:
        item = json.loads(STATUS.read_text(encoding="utf-8"))
    except Exception:
        return {"online": False, "last_seen": "unknown"}
    try:
        seen = datetime.fromisoformat(item.get("last_seen", "").replace("Z", "+00:00"))
        age = (datetime.now(timezone.utc) - seen).total_seconds()
        item["online"] = age <= 180
    except Exception:
        item["online"] = False
    return item
