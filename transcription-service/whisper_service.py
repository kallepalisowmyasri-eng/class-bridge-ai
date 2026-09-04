import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from faster_whisper import WhisperModel

model_size = os.getenv("WHISPER_MODEL", "base")
UPLOAD_DIR = os.getenv("AUDIO_DIR", os.path.expanduser("~/classbridge/uploads/audio"))

# Global model reference — populated during startup
_whisper_model: WhisperModel | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load Whisper model on startup; release on shutdown."""
    global _whisper_model
    print(f"[ClassBridge] Loading Whisper model '{model_size}'...")
    try:
        _whisper_model = WhisperModel(model_size, device="cpu", compute_type="int8")
        print(f"[ClassBridge] Whisper model '{model_size}' loaded successfully.")
    except Exception as e:
        # Log clearly so the user sees why startup failed
        print(f"[ClassBridge] ERROR: Failed to load Whisper model '{model_size}': {e}")
        raise RuntimeError(
            f"Could not load Whisper model '{model_size}'. "
            f"Ensure faster-whisper is installed and the model name is valid.\n"
            f"Original error: {e}"
        ) from e
    yield
    # Cleanup on shutdown (model has no explicit close, but signal we're done)
    _whisper_model = None
    print("[ClassBridge] Whisper model unloaded.")

app = FastAPI(title="ClassBridge Transcription Service", lifespan=lifespan)


class TranscribeRequest(BaseModel):
    audio_path: str = Field(..., min_length=1)


class TranscriptSegment(BaseModel):
    start: float
    end: float
    text: str


class TranscribeResponse(BaseModel):
    segments: list[TranscriptSegment]
    full_text: str


def validate_audio_path(audio_path: str) -> Path:
    resolved = Path(audio_path).resolve()
    upload_dir = Path(UPLOAD_DIR).resolve()
    home_classbridge = (Path.home() / "classbridge").resolve()
    project_root = Path(__file__).resolve().parent.parent

    norm_resolved = os.path.normcase(str(resolved))
    norm_upload = os.path.normcase(str(upload_dir))
    norm_home = os.path.normcase(str(home_classbridge))
    norm_project = os.path.normcase(str(project_root))

    if not (norm_resolved.startswith(norm_upload) or
            norm_resolved.startswith(norm_home) or
            norm_resolved.startswith(norm_project)):
        raise HTTPException(status_code=400, detail="Path not within allowed directory")

    if not resolved.exists():
        raise HTTPException(status_code=404, detail="Audio file not found")

    allowed_suffixes = {".wav", ".mp3", ".mp4", ".m4a", ".flac", ".ogg"}
    if resolved.suffix.lower() not in allowed_suffixes:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {resolved.suffix}")

    return resolved


@app.post("/transcribe", response_model=TranscribeResponse)
async def transcribe(request: TranscribeRequest):
    if _whisper_model is None:
        raise HTTPException(status_code=503, detail="Whisper model not loaded. Service is starting up.")

    resolved_path = validate_audio_path(request.audio_path)

    try:
        segments_iter, info = _whisper_model.transcribe(
            str(resolved_path),
            beam_size=5,
            language="en",
            vad_filter=True,
        )

        segments = []
        full_text_parts = []
        max_segments = 10000

        for i, seg in enumerate(segments_iter):
            if i >= max_segments:
                break
            segments.append(TranscriptSegment(
                start=round(seg.start, 2),
                end=round(seg.end, 2),
                text=seg.text.strip(),
            ))
            full_text_parts.append(seg.text.strip())

        return TranscribeResponse(
            segments=segments,
            full_text=" ".join(full_text_parts),
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")


@app.get("/health")
def health():
    return {"status": "UP", "model": model_size, "model_loaded": _whisper_model is not None}
