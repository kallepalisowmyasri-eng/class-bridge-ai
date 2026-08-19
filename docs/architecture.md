# Architecture — ClassRecover AI Backend

> Spring Boot layering + module structure (§21, §23 of `class.md`).

## Technology stack
- Java 21, Spring Boot 3.3.x, Maven
- Spring Web, Spring Data JPA (Hibernate), Spring Security + JWT
- PostgreSQL 18, pgvector (Phase 10, optional)
- FFmpeg (media/audio processing), Whisper / faster-whisper (Python service)
- Gemini API (AI class analysis + chat answers)

## High-level flow

```
FRONTEND (HTML/CSS/JS)
      | REST API (JSON) + JWT
      v
SPRING BOOT BACKEND
      |  Authentication | Class Management | User Management
      v
MEDIA PROCESSING
      +-- FFmpeg (audio extraction, duration, frames)
      +-- Whisper Service (timestamped transcript)
      +-- Gemini AI Service (class analysis)
      v
CLASS ANALYSIS ENGINE
      v
CLASS RECOVERY ENGINE
      v
KNOWLEDGE BASE / RAG  ->  LEARNING GAP ENGINE  ->  PERSONALIZED CATCH-UP
      v
   PostgreSQL
```

## Processing lifecycle

```
Upload → Validate → Save → Extract Audio (FFmpeg) → Transcribe (Whisper)
→ Analyze (Gemini) → Generate Recovery Material → Ready
```

Each step updates the class `processingStatus`:
`UPLOADED → AUDIO_READY → TRANSCRIBED → ANALYZED → READY`

## Module structure (backend/)

```
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/aiclassrecovery/
    │   │   ├── ClassRecoveryApplication.java
    │   │   ├── controller/        REST endpoints
    │   │   ├── service/           business logic (ClassRecoveryService,
    │   │   │                       MediaProcessingService, WhisperService,
    │   │   │                       GeminiService, RecoveryEngineService,
    │   │   │                       LearningGapService, ChatService)
    │   │   ├── repository/        Spring Data JPA interfaces
    │   │   ├── model/             JPA entities
    │   │   ├── dto/               request/response records
    │   │   ├── config/            security config, CORS, beans
    │   │   ├── security/          JWT filter, user details, token util
    │   │   ├── exception/         global exception handler + API errors
    │   │   └── util/              file validation, constants
    │   └── resources/
    │       ├── application.properties
    │       └── db/
    └── test/
```

### Frontend (frontend/)
```
frontend/
├── index.html       # landing page (marketing) + entry
├── dashboard.html   # My Classes
├── class.html       # class detail with tabs
├── css/landing.css  # landing styles (also base theme for app pages)
└── js/landing.js    # landing interactions; app.js/dashboard.js later
```

### External services
```
transcription-service/         # Python, faster-whisper
├── whisper_service.py         # POST /transcribe -> timestamped segments
└── requirements.txt
```

## Layer responsibilities
- **Controller**: HTTP validation, status codes, request/response mapping.
- **Service**: business logic, orchestration, calls Whisper/Gemini, transaction
  boundaries.
- **Repository**: Spring Data JPA (PagingAndSortingRepository etc.).
- **Model**: JPA entities with relationships matching the schema doc.
- **DTO**: immutable records; never expose entities directly.
- **Security**: JWT bearer auth; stateless; user-scoped queries.
- **Exception**: `@RestControllerAdvice` with a uniform error JSON.
- **Util**: file extension/size validation, constants.

## AI processing strategy (§25)
Long transcripts are chunked before calling Gemini; per-chunk structured
results are merged and deduplicated:

```
Long Transcript → Chunking → Chunk Analysis → Structured Results
→ Merge / Deduplicate → Final Class Analysis
```

## RAG strategy (§26)
```
Class Transcript → Chunking → Embeddings → Vector Storage (pgvector/FAISS)
→ Student Question → Similarity Search → Relevant Chunks → Gemini → Grounded Answer + Timestamp
```

## Security & privacy
- API keys in environment variables (`GEMINI_API_KEY`), never in code
- JWT for auth; all class/subject queries filtered by the authenticated user
- Upload validation: extension allow-list, size cap, storage outside web root
- Deletion endpoints remove files + generated data
- Avoid logging transcript/recording content
