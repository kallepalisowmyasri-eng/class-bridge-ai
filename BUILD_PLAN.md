# ClassBridge-AI — Step-by-Step Implementation Plan

> Follow this plan in order. Do not skip ahead to later phases before the
> previous phase is verified. Each phase has: **Goal**, **Tasks**,
> **Commands**, and **Verify** (how to know it worked).

---

## Prerequisites — Install these first (once)

| Tool | Version | Why |
|------|---------|-----|
| Java JDK | 21 (Temurin/Adoptium) | Backend runtime |
| Maven | 3.9+ | Build tool |
| IntelliJ IDEA | Latest | Java IDE |
| PostgreSQL | 18 (already installed) | Database |
| Postman | Latest | API testing |
| FFmpeg | Latest | Audio/video processing (Phase 4) |
| Python | 3.11+ | Whisper service (Phase 5) |
| Git | Latest | Version control |

**Verify installs (run each):**
```bash
java -version
mvn -version
psql --version
ffmpeg -version
python --version
```

---

## Step 0 — Project Docs (before any code)

Create the `docs/` folder and design documents.

### 0.1 Create folder
```bash
mkdir docs
```

### 0.2 Create these documents
1. `docs/requirements.md` — copy feature list from `class.md` §5-§20
2. `docs/architecture.md` — Spring Boot layering + module structure (§21, §23)
3. `docs/database-schema.md` — all 17 tables from §24 with columns
4. `docs/api-specification.md` — every REST endpoint with method, path, request, response

### 0.3 Create the database
```bash
psql -U postgres -c "CREATE DATABASE classbridge;"
```

**Verify:** `psql -U postgres -d classbridge -c "\l"` shows `classbridge`.

---

## Phase 1 — Project Foundation

**Goal:** A running Java 21 + Spring Boot app connected to PostgreSQL.

### 1.1 Scaffold backend
- Open IntelliJ → New Project → Spring Initializr:
  - Java 21, Maven, Packaging: JAR
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, PostgreSQL Driver, Validation

Or create files manually:

```
backend/
├── pom.xml
└── src/main/
    ├── java/com/aiclassrecovery/
    │   ├── ClassRecoveryApplication.java
    │   ├── controller/HealthController.java
    │   ├── config/SecurityConfig.java
    │   └── service/...
    ├── java/com/aiclassrecovery/repository/...
    ├── java/com/aiclassrecovery/model/...
    └── resources/application.properties
```

### 1.2 `pom.xml` essentials
- Parent: `spring-boot-starter-parent` 3.3.x
- `<java.version>21</java.version>`
- Dependencies from above

### 1.3 Configure database (`application.properties`)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/classbridge
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=update
server.port=8080
```

### 1.4 Health endpoint
```java
@RestController
@RequestMapping("/api")
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

**Run:**
```bash
cd backend
mvn spring-boot:run
```

**Verify:**
- App starts, no connection errors
- `curl http://localhost:8080/api/health` → `{"status":"UP"}`

---

## Phase 2 — Class Management

**Goal:** Create and view subjects and classes via API.

### 2.1 Models (entities)
- `User` — id, email, password (hashed), name
- `Subject` — id, name, description, userId
- `Class` — id, subjectId, title, date, description, duration, processingStatus

### 2.2 Repositories
- `UserRepository`, `SubjectRepository`, `ClassRepository` (Spring Data JPA interfaces)

### 2.3 DTOs
- `SubjectDto`, `ClassDto` — for API request/response

### 2.4 Controllers + Services
- `POST /api/subjects` — create subject
- `GET /api/subjects` — list subjects
- `POST /api/classes` — create class
- `GET /api/classes` — list classes
- `GET /api/classes/{id}` — class details

**Verify (Postman or curl):**
```bash
curl -X POST http://localhost:8080/api/subjects -H "Content-Type: application/json" -d '{"name":"Data Structures","description":"CS course"}'
curl http://localhost:8080/api/subjects
```

---

## Phase 3 — Recording Upload

**Goal:** A class can have an uploaded recording with processing status.

### 3.1 File storage
- Add to `application.properties`:
```properties
file.upload-dir=uploads/recordings
```

### 3.2 Upload endpoint
- `POST /api/classes/{id}/upload` — multipart file upload
- Accept: MP4, MP3, WAV, M4A
- Validate extension + size (e.g., max 2GB)
- Save file to `uploads/recordings/`
- Set class `processingStatus = UPLOADED`

### 3.3 Download/delete
- `GET /api/classes/{id}/recording` — stream file
- `DELETE /api/classes/{id}` — delete class + file

**Verify:** upload an `.mp4`, check file appears in `uploads/recordings/`, class status shows `UPLOADED`.

---

## Phase 4 — Audio Processing

**Goal:** Extract audio from video using FFmpeg.

### 4.1 FFmpeg dependency
- Add to `pom.xml`:
```xml
<dependency>
    <groupId>net.bramp.ffmpeg</groupId>
    <artifactId>ffmpeg</artifactId>
    <version>0.8.0</version>
</dependency>
```
- Or call FFmpeg from Java `ProcessBuilder` (no library needed)

### 4.2 Audio extraction
- Extract audio → 16kHz mono WAV (Whisper-friendly)
```bash
ffmpeg -i input.mp4 -ar 16000 -ac 1 -vn output.wav
```
- Detect duration with `ffprobe`
- Store in `uploads/audio/`
- Update status: `AUDIO_READY`

**Verify:** upload `.mp4` → `output.wav` created in `uploads/audio/`; duration shown in class metadata.

---

## Phase 5 — Transcription (Whisper)

**Goal:** Produce a timestamped transcript.

### 5.1 Create Python service
```
transcription-service/
├── whisper_service.py
└── requirements.txt   # faster-whisper
```

### 5.2 Service API
- `POST /transcribe` — accepts audio file path, returns segments
```json
[
  {"start": 0.0, "end": 4.2, "text": "Teacher introduces binary search."},
  {"start": 252.0, "end": 260.0, "text": "Teacher explains the algorithm."}
]
```

### 5.3 Java calls Python service
- Spring `WebClient` posts audio path to `http://localhost:5000/transcribe`
- Save full transcript + segments to DB tables (`transcripts`, `transcript_segments`)
- Status: `TRANSCRIBED`

**Verify:** uploaded class now has a transcript with timestamps in DB/API.

---

## Phase 6 — AI Class Analysis (Gemini)

**Goal:** The system understands the class.

### 6.1 Gemini key
- Get API key from Google AI Studio
- Store in environment variable `GEMINI_API_KEY` (never in code)

### 6.2 Analysis service
- Send transcript chunks to Gemini with a structured JSON prompt (see §25 chunking strategy)
- Ask for: summary, topics, important points, assignments, announcements, resources, questions

### 6.3 Save results
- Store in DB tables: `topics`, `assignments`, `announcements`, `resources`, `questions`
- Every item keeps its source timestamp (§10 evidence grounding)
- Status: `ANALYZED`

**Verify:** API returns topics/assignments with timestamps for the uploaded class.

---

## Phase 7 — Class Recovery Engine (core value)

**Goal:** Student can recover the missed class.

Build these features (from §11):

### 7.1 What You Missed
- `GET /api/classes/{id}/missed` — concise overview

### 7.2 Must Learn
- `GET /api/classes/{id}/must-learn` — ranked concepts with importance

### 7.3 Catch-up Path
- `GET /api/classes/{id}/catchup-path` — prerequisite → concept → example → practice

### 7.4 Prerequisites
- `GET /api/classes/{id}/prerequisites` — explicit vs AI-recommended

### 7.5 Quick Explanation
- `GET /api/classes/{id}/explanations` — simple-language explanations

### 7.6 Announcements
- `GET /api/classes/{id}/announcements`

### 7.7 Assignments
- `GET /api/classes/{id}/assignments`

### 7.8 Resources
- `GET /api/classes/{id}/resources`

### 7.9 Important Timestamps
- `GET /api/classes/{id}/timestamps`

### 7.10 Quick Revision
- `GET /api/classes/{id}/revision`

### 7.11 Quiz
- `GET /api/classes/{id}/quiz` — questions from class content only

### 7.12 What to Watch
- `GET /api/classes/{id}/watch` — Must Watch / Recommended / Optional with reasons

**Verify:** all endpoints return meaningful data for a processed class.

---

## Phase 8 — Student Dashboard (MVP complete)

**Goal:** Usable end-to-end MVP.

### 8.1 Frontend files (per §23)
```
frontend/
├── index.html      # login / upload
├── dashboard.html  # My Classes
├── class.html      # class detail with tabs
├── css/style.css
└── js/app.js
```

### 8.2 Pages
- **index** — upload recording, list subjects
- **dashboard** — class list, recovery progress bar
- **class** — tabs: Overview, What You Missed, Must Learn, Catch-up Path, Prerequisites, Topics, Notes, Assignments, Announcements, Resources, Visuals, Timeline, Revision, Quiz

### 8.3 Connect to API
- JavaScript `fetch()` calls the Spring Boot REST API (Phase 7 endpoints)

**Verify:** full flow in browser: upload class → wait for processing → see recovery material + take quiz.

---

## MVP COMPLETE ✅ — Demo scenario from §31 works end-to-end.

---

## Phase 9 — Previous Classes (after MVP)

- Class history, subject organization, search
- `GET /api/subjects/{id}/classes` ordered by date

## Phase 10 — Chat With Class (RAG)

- Chunk transcripts → embeddings → vector storage (pgvector) → similarity search → Gemini answers with timestamps

## Phase 11 — Learning Gap Engine

- Compare current vs previous class topics → identify prerequisites to review

## Phase 12 — Personalized Catch-up

- Student learning state: mark understood / needs review / completed
- Recovery progress %, practice, quiz results

## Phase 13 — Visual Understanding

- Extract frames with FFmpeg, slide detection, visual analysis

## Phase 14 — Future Live Integration

- Google Meet / Zoom integration (only after recorded-class MVP works)

---

## Quick reference — DB tables to create (§24)

`users`, `subjects`, `classes`, `transcripts`, `transcript_segments`,
`topics`, `assignments`, `announcements`, `resources`, `questions`,
`visual_contents`, `quizzes`, `quiz_questions`, `recovery_progress`,
`learning_gaps`, `catchup_paths`, `notes`

---

## Golden rules while building

1. **Never hard-code secrets** — use environment variables
2. **Every extracted item keeps a timestamp** — AI never invents facts (§10)
3. **Distinguish verified content vs AI help** in the UI (§28)
4. **Build one phase, verify it, then move on**
5. **Commit to git after each working phase:**
```bash
git add -A
git commit -m "Phase N: <summary>"
git push origin main
```
