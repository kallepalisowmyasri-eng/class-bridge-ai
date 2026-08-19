# Requirements — ClassRecover AI (AI Class Recovery System)

> Source of truth: `class.md` (§5–§20). This document consolidates the product
> requirements that the backend must implement.

## Product positioning

AI Class Recovery System transforms recorded online classes into structured,
searchable, evidence-grounded learning material and personalized catch-up
paths, helping students recover missed knowledge **without watching the entire
recording**.

> MISSED CLASS → UNDERSTAND WHAT WAS MISSED → KNOW WHAT TO LEARN → CATCH UP QUICKLY

## Core features

### 1. Class Recording Upload (§5.1)
- Upload recorded classes: MP4, MP3, WAV, M4A and other common formats
- Validate files (extension, size limit, e.g. max 2 GB)
- Show upload progress; store class metadata
- Detect recording duration (ffprobe)
- Show processing status; handle invalid files
- Allow recording deletion

### 2. Student Notes (§5.2)
- Personal free-form notes attached to a class/topic (plain or markdown)
- Editable and deletable; stored separately from AI-generated content

### 3. Media Processing (§6)
- FFmpeg: detect media info, extract audio, convert to 16 kHz mono WAV
- Extract selected video frames (later phase)
- Support long recordings; expose the processing lifecycle:
  Upload → Validate → Save → Extract Audio → Transcribe → Analyze →
  Generate Recovery Material → Ready

### 4. Speech-to-Text (§7)
- Whisper / faster-whisper (Python service)
- Timestamped transcript with segments; long-recording chunking
- CPU-friendly configuration; error handling
- No invented transcript content

### 5. Visual Content Extraction (§8)
- Capture frames for slides, diagrams, code, formulas, tables, charts,
  whiteboards, screen demonstrations
- Associate frames with timestamps + visual description + related topic
- Implement after the transcript pipeline is stable (Phase 13)

### 6. AI Class Analysis (§9)
- Gemini analyzes the verified transcript (+ visual info when available)
- Identify: main topics + subtopics + sequence; definitions, concepts,
  examples, formulas, procedures, demonstrations; assignments, deadlines,
  announcements, resources, URLs, discussed questions, exam statements
  (only when explicitly mentioned)
- Return **structured data**, not only free-form text

### 7. Evidence Grounding (§10)
- Every extracted item retains its source (timestamp range)
- Never invent: assignments, deadlines, URLs, timestamps, teacher statements,
  announcements, exam information
- Unverifiable → respond: "This information was not found in the class content."

### 8. Class Recovery Engine (§11) — core value
1. **What You Missed** — concise overview ("What happened in this class?")
2. **Must Learn** — ranked concepts with importance (Critical/High/Medium)
3. **Catch-up Path** — Prerequisite → Basic Concept → Main Concept → Example →
   Practice → Quiz
4. **Prerequisites** — explicitly referenced vs AI-recommended; never falsely
   claim the student doesn't know something
5. **Quick Explanation** — simple-language explanations (not teacher quotes)
6. **Teacher Announcements** — separated from teaching content
7. **Assignments** — description, deadline, submission instructions, topic
   (only what was mentioned)
8. **Resources** — URLs, books, articles, videos, websites, references
9. **Important Timestamps** — timestamp index of important moments
10. **Quick Revision** — definitions, concepts, formulas, examples, takeaways
11. **Quiz** — short quiz from class-supported content only (MCQ/short/conceptual)
12. **What to Watch** — Must Watch / Recommended / Optional with reasons

### 9. Missed Class Mode (§12)
Dedicated "I Missed This Class" experience answering: what was taught, what was
missed, what matters most, what to learn first, prerequisites to review,
assignments, resources, announcements, what to revise, what to watch, and a
self-test.

### 10. Class Knowledge Base (§13)
Organize classes by subject: Subject → Class 01..N. Each class contains
recording, transcript, topics, summary, assignments, announcements, resources,
visuals, quiz, recovery progress.

### 11. Previous Class Memory (§14)
Remember previous classes so students can ask:
- "Which previous class explained this topic?"
- "What did we learn before today's topic?"
- "Show me the class where the teacher explained recursion."

### 12. Cross-Class Understanding (§15)
Connect today's topic with previous classes (e.g., Backpropagation → Gradient
Descent, Derivatives, Neural Networks). Recommend reviewing earlier concepts.

### 13. Learning Gap Engine (§16)
- Inputs: current topics, previous topics, recommended prerequisites,
  student-marked understanding, quiz performance, recovery progress
- Outputs: Missing/Needs Review → Prerequisite → Recommended Review →
  Current Topic
- Careful language: "This may be a prerequisite. We recommend reviewing it."
  NOT "You don't know this."

### 14. Personalized Catch-up Path (§17)
- Step-by-step path with estimated durations (e.g., Review prerequisite 8 min →
  Learn main concept 15 min → Teacher example 8 min → Practice 10 min → Quiz)
- Student can mark understood / needs review, skip known topics, complete
  practice, take quizzes, track recovery progress

### 15. Chat With Class (§18)
- RAG: Question → retrieve relevant evidence → Gemini → grounded answer →
  timestamp source
- Class-specific and cross-class questions
- If not available: "This information was not found in the class content."

### 16. Student Dashboard (§19)
- My Classes; Current Class with tabs (Overview, What You Missed, Must Learn,
  Catch-up Path, Prerequisites, Topics, Notes, Assignments, Announcements,
  Resources, Visuals, Timeline, Revision, Quiz, Chat); Previous Classes

### 17. Recovery Progress (§20)
- Per-class progress %, e.g., "Class Recovery: 65%", with checklist of topics,
  practice, quiz
- Concepts can be marked Understood / Need Review / Completed

## Non-functional requirements
- Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Spring Security + JWT
- Gemini API (keys in environment variables, never in code)
- Whisper/faster-whisper Python service; FFmpeg media processing
- Distinguish **Verified Class Content** vs **AI Learning Assistance** in the UI
- Secure: protect accounts, prevent unauthorized class access, allow deletion
  of recordings and generated data, validate uploads, avoid unnecessary
  sensitive logging, handle API failures safely
