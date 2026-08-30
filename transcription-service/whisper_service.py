import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from faster_whisper import WhisperModel

app = FastAPI(title="ClassBridge Transcription Service")

model_size = os.getenv("WHISPER_MODEL", "base")
UPLOAD_DIR = os.getenv("AUDIO_DIR", os.path.expanduser("~/classbridge/uploads/audio"))

try:
    model = WhisperModel(model_size, device="cpu", compute_type="int8")
except Exception as e:
    raise RuntimeError(f"Failed to load whisper model '{model_size}': {e}")


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

    if not str(resolved).startswith(str(upload_dir)):
        raise HTTPException(status_code=400, detail="Path not within allowed directory")

    if not resolved.exists():
        raise HTTPException(status_code=404, detail="Audio file not found")

    allowed_suffixes = {".wav", ".mp3", ".mp4", ".m4a", ".flac", ".ogg"}
    if resolved.suffix.lower() not in allowed_suffixes:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {resolved.suffix}")

    return resolved


@app.post("/transcribe", response_model=TranscribeResponse)
async def transcribe(request: TranscribeRequest):
    resolved_path = validate_audio_path(request.audio_path)

    try:
        segments_iter, info = model.transcribe(
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
    return {"status": "UP"}
