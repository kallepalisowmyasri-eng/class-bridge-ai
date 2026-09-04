# AI Class Recovery System

**Tagline:**

> Missed the class? Catch up smarter, not longer.

**Project Type:** AI-powered full-stack web application
**Primary Backend:** Java + Spring Boot
**Frontend:** HTML, CSS, JavaScript
**Database:** PostgreSQL
**AI:** Gemini API
**Speech-to-Text:** Whisper / faster-whisper
**Media Processing:** FFmpeg

------------------------------------------------------------------------

# 1. Project Overview

The **AI Class Recovery System** is an education-focused web application
designed to help students recover knowledge from online classes they
missed or could not properly follow.

A student may join an important live class but be unable to actively
listen because of another important responsibility, distraction,
connectivity issue, or other circumstances.

After the class, instead of watching the entire recording again, the
student can access structured learning material generated from the
class.

The system focuses on:

> **MISSED CLASS → UNDERSTAND WHAT WAS MISSED → KNOW WHAT TO LEARN →
> CATCH UP QUICKLY**

This is not primarily an AI meeting summarizer. It is a **class recovery
and learning system**.

------------------------------------------------------------------------

# 2. Problem Statement

Students may miss important information during live online classes
because of other responsibilities, distractions, connectivity problems,
or inability to actively listen throughout the session.

Even when a recording is available, watching the entire recording again
can be time-consuming. Students may also find it difficult to identify:

-   What was taught
-   Which concepts were important
-   What the teacher emphasized
-   Which examples were discussed
-   What assignments were given
-   Which announcements were made
-   Which resources or links were shared
-   Which parts of the recording they actually need to watch
-   Which previous concepts are required to understand the current topic

When students miss multiple classes, catching up becomes even harder
because previous class knowledge may be required to understand later
topics.

Therefore, there is a need for an AI-powered class recovery system that
can analyze recorded classes, organize the important information,
identify what a student needs to learn, and provide a structured
learning path for catching up without requiring the student to watch the
entire recording.

------------------------------------------------------------------------

# 3. Project Goal

Build a web application that transforms recorded online classes into
structured, searchable, evidence-grounded learning material.

The primary goal is:

> **Help a student recover the knowledge of a missed class in less time
> while preserving the important information from the original class.**

------------------------------------------------------------------------

# 4. Core Product Flow

``` text
                    CLASS RECORDING
                           |
                           v
                Audio + Video Processing
                           |
             +-------------+-------------+
             |                           |
             v                           v
       Speech-to-Text              Visual Extraction
             |                           |
             +-------------+-------------+
                           |
                           v
                   AI CLASS ANALYSIS
                           |
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
   What Was Taught    Important Points    Teacher Information
        |                  |                  |
        v                  v                  v
   Assignments         Resources         Announcements
        |                  |                  |
        v                  v                  v
    Examples          Questions        Visual Content
        +------------------+------------------+
                           |
                           v
                 CLASS RECOVERY ENGINE
                           |
       +-------------------+-------------------+
       |                   |                   |
       v                   v                   v
 What You Missed       Must Learn       What to Watch
       |                   |                   |
       v                   v                   v
Quick Explanation    Prerequisites       Timestamps
       |                   |                   |
       +-------------------+-------------------+
                           |
                           v
                  CLASS KNOWLEDGE BASE
                           |
                           v
                  PREVIOUS CLASSES
                           |
                           v
                  LEARNING GAP ENGINE
                           |
       +-------------------+-------------------+
       |                   |                   |
       v                   v                   v
Missing Knowledge     Prerequisites      Difficult Topics
       +-------------------+-------------------+
                           |
                           v
              PERSONALIZED CATCH-UP PATH
                           |
       +-------------------+-------------------+
       |                   |                   |
       v                   v                   v
   Learn First         Practice          Quick Quiz
       +-------------------+-------------------+
                           |
                           v
                    RECOVERY PROGRESS
                           |
                           v
              CROSS-CLASS UNDERSTANDING
                           |
                           v
                  ASK YOUR CLASSES
```

------------------------------------------------------------------------

# 5. Core Features

## 5.1 Class Recording Upload

Students can upload recorded classes.

Supported formats:

-   MP4
-   MP3
-   WAV
-   M4A
-   Other common formats where practical

The system should:

-   Validate files
-   Show upload progress
-   Detect recording duration
-   Store class metadata
-   Show processing status
-   Handle invalid files
-   Allow recording deletion

------------------------------------------------------------------------

## 5.2 Student Notes

Students can write their own notes for a class.

Notes are:

-   Personal to the student (not visible to other students)
-   Attached to a specific class or topic
-   Free-form text (plain or markdown)
-   Editable and deletable by the student

Notes are stored separately from AI-generated material so the student's
own annotations are never confused with verified class content.

------------------------------------------------------------------------

# 6. Media Processing

Use **FFmpeg** for media processing.

Responsibilities:

-   Detect media information
-   Extract audio from video
-   Convert audio to a suitable format
-   Extract selected video frames later
-   Support long recordings

Processing lifecycle:

``` text
Upload
  ↓
Validate
  ↓
Save
  ↓
Extract Audio
  ↓
Transcribe
  ↓
Analyze
  ↓
Generate Recovery Material
  ↓
Ready
```

------------------------------------------------------------------------

# 7. Speech-to-Text

Use **Whisper / faster-whisper**.

Requirements:

-   Timestamped transcript
-   Long recording support
-   Audio chunking
-   CPU-friendly configuration
-   Error handling
-   No invented transcript content

Example:

``` text
00:04:12
Teacher introduces binary search.

00:18:40
Teacher explains the algorithm.

01:02:15
Teacher announces an assignment.
```

The timestamp must come from the transcription/media pipeline, not from
AI guessing.

------------------------------------------------------------------------

# 8. Visual Content Extraction

Speech is not the only source of information in a class.

Important information can appear in:

-   Slides
-   Diagrams
-   Code
-   Formulas
-   Tables
-   Charts
-   Whiteboards
-   Screen demonstrations

The system should eventually extract important frames and associate them
with timestamps.

Example:

``` text
Timestamp
    ↓
Screenshot
    ↓
Visual description
    ↓
Related topic
```

This is an advanced feature and should be implemented after the
transcript pipeline is stable.

------------------------------------------------------------------------

# 9. AI Class Analysis

Gemini should analyze the verified transcript and available visual
information.

The AI should identify:

## Topics

-   Main topics
-   Subtopics
-   Topic sequence

## Educational Content

-   Definitions
-   Important concepts
-   Examples
-   Explanations
-   Formulas
-   Procedures
-   Demonstrations

## Important Information

-   Assignments
-   Deadlines when explicitly mentioned
-   Announcements
-   Resources
-   URLs
-   Questions discussed
-   Exam-related statements only when explicitly mentioned

The AI should return structured data rather than only free-form text.

------------------------------------------------------------------------

# 10. Evidence Grounding

Every important extracted item should retain its source.

Example:

``` text
Assignment:
Implement Binary Search.

Source:
01:12:43 - 01:14:02
```

Example:

``` text
Important Concept:
Binary Search

Source:
00:32:10
```

The system must never invent:

-   Assignments
-   Deadlines
-   URLs
-   Timestamps
-   Teacher statements
-   Announcements
-   Exam information

If information cannot be verified:

> "This information was not found in the class content."

------------------------------------------------------------------------

# 11. Class Recovery Engine

The **Class Recovery Engine** is the main product feature.

It should generate the following.

## 11.1 What You Missed

A concise overview answering:

> What happened in this class?

------------------------------------------------------------------------

## 11.2 Must Learn

Rank the most important concepts.

Example:

``` text
1. Binary Search       - Critical
2. Time Complexity     - High
3. Linear Search       - Medium
```

Importance should be based on class evidence where possible.

------------------------------------------------------------------------

## 11.3 Catch-up Path

Create a logical sequence:

``` text
Prerequisite
    ↓
Basic Concept
    ↓
Main Concept
    ↓
Example
    ↓
Practice
    ↓
Quiz
```

------------------------------------------------------------------------

## 11.4 Prerequisites

Identify concepts required to understand later topics.

Clearly distinguish:

-   Prerequisite explicitly referenced in the class
-   AI-recommended prerequisite

Never falsely claim that a student does not know something.

------------------------------------------------------------------------

## 11.5 Quick Explanation

Explain difficult concepts in simple student-friendly language.

AI-generated explanations must not be presented as direct teacher
statements.

------------------------------------------------------------------------

## 11.6 Teacher Announcements

Separate announcements from teaching content.

Examples:

-   Exam announcements
-   Schedule changes
-   Important instructions

------------------------------------------------------------------------

## 11.7 Assignments

Extract:

-   Assignment description
-   Deadline
-   Submission instructions
-   Related topic

Only include information actually mentioned in the class.

------------------------------------------------------------------------

## 11.8 Resources

Extract:

-   URLs
-   Books
-   Articles
-   Videos
-   Websites
-   References
-   Files/resources

------------------------------------------------------------------------

## 11.9 Important Timestamps

Create timestamp references for important moments.

Example:

``` text
00:12:30 - Introduction
00:25:10 - Important concept
00:41:20 - Example
01:05:32 - Assignment
01:20:10 - Announcement
```

------------------------------------------------------------------------

## 11.10 Quick Revision

Generate concise:

-   Definitions
-   Concepts
-   Formulas
-   Examples
-   Key takeaways

------------------------------------------------------------------------

## 11.11 Quiz

Generate a short quiz using only class-supported information.

Possible types:

-   Multiple choice
-   Short answer
-   Conceptual questions

------------------------------------------------------------------------

## 11.12 What to Watch

This feature directly supports the main problem.

Instead of requiring students to watch the complete recording, identify:

-   Must Watch
-   Recommended
-   Optional

Example:

``` text
MUST WATCH
00:32:10 - 00:45:20

RECOMMENDED
01:02:15 - 01:08:30

OPTIONAL
01:20:10 - 01:25:00
```

The system must explain the reason for recommendations where possible.

------------------------------------------------------------------------

# 12. Missed Class Mode

Provide a dedicated:

> **I Missed This Class**

experience.

It should answer:

-   What was taught?
-   What did I miss?
-   What is most important?
-   What should I learn first?
-   What prerequisites should I review?
-   What assignments were given?
-   What resources were shared?
-   What announcements were made?
-   What should I revise?
-   What should I watch?
-   Can I test my understanding?

------------------------------------------------------------------------

# 13. Class Knowledge Base

Do not treat every class as an isolated recording.

Organize classes by:

``` text
Subject
  |
  +-- Class 01
  +-- Class 02
  +-- Class 03
  +-- Class 04
```

Each class can contain:

-   Recording
-   Transcript
-   Topics
-   Summary
-   Assignments
-   Announcements
-   Resources
-   Visuals
-   Quiz
-   Recovery progress

------------------------------------------------------------------------

# 14. Previous Class Memory

The system should remember previous classes.

This enables students to ask:

> Which previous class explained this topic?

> What did we learn before today's topic?

> Show me the class where the teacher explained recursion.

------------------------------------------------------------------------

# 15. Cross-Class Understanding

The system should connect related information across classes.

Example:

``` text
Today's Class:
Backpropagation

Previous Classes:
Gradient Descent
Derivatives
Neural Networks
```

The system can recommend reviewing previous concepts before continuing.

------------------------------------------------------------------------

# 16. Learning Gap Engine

The Learning Gap Engine determines what the student may need to review.

Inputs:

-   Current class topics
-   Previous class topics
-   Recommended prerequisites
-   Student-marked understanding
-   Quiz performance
-   Recovery progress

Outputs:

``` text
Missing / Needs Review
        ↓
Prerequisite
        ↓
Recommended Review
        ↓
Current Topic
```

The system should use careful language:

> "This may be a prerequisite. We recommend reviewing it."

Not:

> "You don't know this."

unless the student has explicitly indicated it or there is sufficient
evidence.

------------------------------------------------------------------------

# 17. Personalized Catch-up Path

Create a learning path based on the student's available information.

Example:

``` text
Step 1
Review prerequisite
8 minutes

Step 2
Learn main concept
15 minutes

Step 3
Review teacher example
8 minutes

Step 4
Practice
10 minutes

Step 5
Quick Quiz
5 questions
```

The student should be able to:

-   Mark a topic as understood
-   Mark it for review
-   Skip known topics
-   Complete practice
-   Take quizzes
-   Track recovery progress

------------------------------------------------------------------------

# 18. Chat With Class

Students can ask questions about a specific class.

Example:

> "Explain backpropagation simply."

> "What assignment did the teacher give?"

> "When did the teacher explain this?"

The system should:

``` text
Question
   ↓
Retrieve relevant class evidence
   ↓
Gemini
   ↓
Answer
   ↓
Timestamp / source
```

If the answer is not available:

> "This information was not found in the class content."

------------------------------------------------------------------------

# 19. Student Dashboard

The dashboard should contain:

``` text
Dashboard
│
├── My Classes
│
├── Current Class
│   ├── Overview
│   ├── What You Missed
│   ├── Must Learn
│   ├── Catch-up Path
│   ├── Prerequisites
│   ├── Topics
│   ├── Notes
│   ├── Assignments
│   ├── Announcements
│   ├── Resources
│   ├── Visuals
│   ├── Timeline
│   ├── Revision
│   ├── Quiz
│   └── Chat
│
└── Previous Classes
```

------------------------------------------------------------------------

# 20. Recovery Progress

Track:

``` text
Class Recovery: 65%

✓ Overview
✓ Topic 1
✓ Topic 2
○ Topic 3
○ Practice
○ Quiz
```

Students can mark concepts:

-   Understood
-   Need Review
-   Completed

------------------------------------------------------------------------

# 21. Suggested Java Architecture

``` text
                    FRONTEND
             HTML + CSS + JavaScript
                         |
                         | REST API
                         v
                SPRING BOOT BACKEND
                         |
      +------------------+------------------+
      |                  |                  |
      v                  v                  v
 Authentication     Class Management    User Management
      |                  |                  |
      +------------------+------------------+
                         |
                         v
                 MEDIA PROCESSING
                         |
              +----------+----------+
              |                     |
              v                     v
            FFmpeg             Video Frames
              |                     |
              v                     v
       Audio Processing      Visual Extraction
              |
              v
        Whisper Service
              |
              v
      Timestamped Transcript
              |
              v
        Gemini AI Service
              |
              v
      CLASS ANALYSIS ENGINE
              |
              v
      CLASS RECOVERY ENGINE
              |
              v
       KNOWLEDGE BASE / RAG
              |
              v
        LEARNING GAP ENGINE
              |
              v
      PERSONALIZED CATCH-UP
              |
              v
           PostgreSQL
```

------------------------------------------------------------------------

# 22. Technology Stack

## Backend

-   Java 17 or Java 21
-   Spring Boot
-   Spring Web
-   Spring Data JPA
-   Hibernate
-   Spring Security
-   PostgreSQL JDBC driver
-   Spring WebClient (HTTP calls to Whisper and Gemini)
-   Maven

## Frontend

Initial version:

-   HTML
-   CSS
-   JavaScript

Optional future upgrade:

-   React

## Database

-   PostgreSQL

## AI

-   Gemini API

## Speech-to-Text

-   Whisper / faster-whisper (Python)
-   Python 3.11+
-   pip / virtual environment for the transcription service

## Media

-   FFmpeg

## Retrieval

Future:

-   pgvector
-   FAISS
-   Vector database

## Authentication

-   Spring Security
-   JWT

## Development

-   IntelliJ IDEA / VS Code
-   Git
-   GitHub
-   Postman

------------------------------------------------------------------------

# 23. Java Backend Folder Structure

``` text
classbridge-ai/
│
├── backend/
│   ├── pom.xml
│   │
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/aiclassrecovery/
│       │   │       │
│       │   │       ├── controller/
│       │   │       ├── service/
│       │   │       ├── repository/
│       │   │       ├── model/
│       │   │       ├── dto/
│       │   │       ├── config/
│       │   │       ├── security/
│       │   │       ├── exception/
│       │   │       └── util/
│       │   │
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/
│       │
│       └── test/
│
├── frontend/
│   ├── index.html
│   ├── dashboard.html
│   ├── class.html
│   ├── css/
│   │   └── style.css
│   └── js/
│       ├── app.js
│       └── dashboard.js
│
├── transcription-service/
│   ├── whisper_service.py
│   └── requirements.txt
│
├── uploads/
│   ├── recordings/
│   └── audio/
│
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── database-schema.md
│   └── api-specification.md
│
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

------------------------------------------------------------------------

# 24. Database Design

Initial tables:

``` text
users
subjects
classes
transcripts
transcript_segments
topics
assignments
announcements
resources
questions
visual_contents
quizzes
quiz_questions
recovery_progress
learning_gaps
catchup_paths
notes
```

Important relationships:

``` text
User
  |
  +-- Recovery Progress
  +-- Catch-up Paths
  +-- Notes
  |
  +-- Classes

Subject
  |
  +-- Classes

Class
  |
  +-- Transcript
  |     +-- Transcript Segments
  +-- Topics
  +-- Assignments
  +-- Announcements
  +-- Resources
  +-- Questions
  +-- Visual Content
  +-- Quiz
  |     +-- Quiz Questions
  +-- Recovery Progress
  +-- Learning Gaps
  +-- Catch-up Paths
  +-- Notes
```

------------------------------------------------------------------------

# 25. AI Processing Strategy

Long transcripts should not be sent to Gemini as one huge request.

Use:

``` text
Long Transcript
      ↓
Chunking
      ↓
Chunk Analysis
      ↓
Structured Results
      ↓
Merge / Deduplicate
      ↓
Final Class Analysis
```

For example:

``` text
Chunk 1 → Topics
Chunk 2 → Topics
Chunk 3 → Assignments
Chunk 4 → Announcements
Chunk 5 → Examples
```

Then combine the structured outputs.

------------------------------------------------------------------------

# 26. RAG Strategy

For class chat:

``` text
Class Transcript
       ↓
Chunking
       ↓
Embeddings
       ↓
Vector Storage
       ↓
Student Question
       ↓
Similarity Search
       ↓
Relevant Chunks
       ↓
Gemini
       ↓
Grounded Answer
       ↓
Timestamp
```

For cross-class questions:

``` text
All Previous Classes
       ↓
Knowledge Base
       ↓
Semantic Retrieval
       ↓
Relevant Classes
       ↓
Gemini
       ↓
Answer
```

------------------------------------------------------------------------

# 27. Security and Privacy

The system must:

-   Keep API keys in environment variables
-   Never hard-code secrets
-   Protect user accounts
-   Prevent unauthorized class access
-   Allow recording deletion
-   Allow generated-data deletion
-   Avoid unnecessary logging of sensitive content
-   Validate uploaded files
-   Restrict dangerous file types
-   Handle API failures safely

------------------------------------------------------------------------

# 28. AI Quality Rules

The system must distinguish between:

### Verified Class Content

Information directly supported by:

-   Transcript
-   Recording
-   Visual content
-   Shared resources

### AI Learning Assistance

Information generated to explain or organize verified content.

The UI should make this distinction clear.

------------------------------------------------------------------------

# 29. MVP Development Phases

## Phase 1 --- Project Foundation

Build:

-   Spring Boot project
-   Maven configuration
-   PostgreSQL connection
-   Basic REST API
-   Basic frontend
-   Environment configuration

### Output

A running Java web application connected to PostgreSQL.

------------------------------------------------------------------------

## Phase 2 --- Class Management

Build:

-   Subject creation
-   Class creation
-   Class metadata
-   Class listing
-   Class details

### Output

Students can create and view classes.

------------------------------------------------------------------------

## Phase 3 --- Recording Upload

Build:

-   Upload endpoint
-   File validation
-   File storage
-   Duration detection
-   Processing status

### Output

A class can have an uploaded recording.

------------------------------------------------------------------------

## Phase 4 --- Audio Processing

Build:

-   FFmpeg integration
-   Audio extraction
-   Audio conversion
-   Processing error handling

### Output

Video recording becomes transcription-ready audio.

------------------------------------------------------------------------

## Phase 5 --- Transcription

Build:

-   Whisper service
-   Chunking
-   Timestamped transcript
-   Transcript storage

### Output

A recording produces a reliable timestamped transcript.

------------------------------------------------------------------------

## Phase 6 --- AI Class Analysis

Build:

-   Gemini integration
-   Structured JSON output
-   Summary
-   Topics
-   Important points
-   Assignments
-   Announcements
-   Resources
-   Questions

### Output

The system understands the class.

------------------------------------------------------------------------

## Phase 7 --- Class Recovery Engine

Build:

-   What You Missed
-   Must Learn
-   Catch-up Path
-   Prerequisites
-   Quick Explanation
-   What to Watch
-   Important Timestamps
-   Quick Revision
-   Quiz

### Output

A student can recover the missed class.

------------------------------------------------------------------------

## Phase 8 --- Student Dashboard

Build the complete learning UI.

### Output

A usable end-to-end MVP.

------------------------------------------------------------------------

## Phase 9 --- Previous Classes

Build:

-   Class history
-   Subject organization
-   Search
-   Previous class retrieval

------------------------------------------------------------------------

## Phase 10 --- Chat With Class

Build:

-   RAG
-   Semantic search
-   Class-specific chat
-   Timestamp citations

------------------------------------------------------------------------

## Phase 11 --- Learning Gap Engine

Build:

-   Prerequisite identification
-   Previous-class relationships
-   Recommended review topics

------------------------------------------------------------------------

## Phase 12 --- Personalized Catch-up

Build:

-   Student learning state
-   Practice
-   Quiz results
-   Recovery progress
-   Personalized learning path

------------------------------------------------------------------------

## Phase 13 --- Visual Understanding

Build:

-   Frame extraction
-   Slide detection
-   Screenshot storage
-   Visual analysis

------------------------------------------------------------------------

## Phase 14 --- Future Live Integration

Only after the complete recorded-class system works:

-   Google Meet integration
-   Zoom integration
-   Live capture
-   Real-time processing

------------------------------------------------------------------------

# 30. MVP Boundary

The first MVP should include only:

``` text
✓ Java Spring Boot
✓ HTML/CSS/JavaScript
✓ PostgreSQL
✓ Class creation
✓ Recording upload
✓ FFmpeg
✓ Whisper
✓ Timestamped transcript
✓ Gemini
✓ Summary
✓ Topics
✓ Important points
✓ Assignments
✓ Announcements
✓ Resources
✓ What You Missed
✓ Must Learn
✓ Catch-up Path
✓ Prerequisites
✓ Quick Explanation
✓ What to Watch
✓ Important timestamps
✓ Quick Revision
✓ Quiz
✓ Basic student dashboard
```

Do NOT initially implement:

``` text
✗ Google Meet integration
✗ Zoom integration
✗ Real-time AI
✗ Complex multi-agent architecture
✗ Large local LLM
✗ Advanced visual AI
✗ Full personalization
✗ Production-scale infrastructure
✗ Previous Classes / Cross-class knowledge base (Phase 9)
✗ Chat With Class / RAG (Phase 10)
✗ Learning Gap Engine (Phase 11)
✗ Personalized Catch-up Path (Phase 12)
✗ Visual Understanding / Frame extraction (Phase 13)
✗ Live integration (Phase 14)
```

------------------------------------------------------------------------

# 31. First Demo Scenario

Use one 20--30 minute recorded class.

Example:

``` text
Student missed:
"Introduction to Binary Search"

Student uploads recording.

System:
1. Extracts audio
2. Transcribes class
3. Identifies topics
4. Extracts important concepts
5. Finds assignment
6. Finds resources
7. Generates summary
8. Creates catch-up path
9. Generates quick revision
10. Generates quiz
```

Student sees:

``` text
WHAT YOU MISSED

The teacher introduced binary search,
explained its requirements, demonstrated
the algorithm using an example, and gave
an implementation assignment.

MUST LEARN
1. Sorted arrays
2. Binary search
3. Time complexity

ASSIGNMENT
Implement binary search.

QUICK REVISION
...

QUIZ
...
```

This proves the core value of the system.

------------------------------------------------------------------------

# 32. Project Differentiation

Do not claim that Google Meet or Zoom provide no AI features.

Instead:

> Existing meeting platforms provide meeting, recording, transcription,
> and AI assistance features. The AI Class Recovery System is
> specifically designed around the student's post-class learning
> journey.

The system focuses on:

-   Missed-class recovery
-   Structured learning material
-   What You Missed
-   What to Watch
-   Must Learn
-   Previous class memory
-   Cross-class understanding
-   Learning gap identification
-   Personalized catch-up
-   Revision and quiz

Therefore:

> **The system is not primarily a meeting recorder or meeting
> summarizer. It is an AI-powered class recovery and learning system.**

------------------------------------------------------------------------

# 33. Core Product Promise

``` text
        MISSED CLASS
              ↓
       WHAT HAPPENED?
              ↓
       WHAT DID I MISS?
              ↓
       WHAT IS IMPORTANT?
              ↓
      WHAT SHOULD I LEARN?
              ↓
       HOW DO I CATCH UP?
              ↓
       PRACTICE + QUIZ
              ↓
       RECOVERY COMPLETE
```

### Final positioning

> **AI Class Recovery System transforms recorded online classes into
> structured, searchable, evidence-grounded learning material and
> personalized catch-up paths, helping students recover missed knowledge
> without watching the entire recording.**

------------------------------------------------------------------------

# 34. Immediate Next Step

Do not start implementing every feature.

The next task is to create the **technical architecture document**
(under `docs/`) containing:

1.  Spring Boot architecture
2.  Module structure
3.  Database ER design
4.  PostgreSQL schema
5.  API endpoints
6.  Recording-processing pipeline
7.  Whisper integration design
8.  Gemini integration design
9.  JSON response schemas
10. RAG architecture
11. Security design
12. Error-handling design
13. MVP implementation order

The `docs/` folder (see section 23) does not exist yet. The architecture
document should be created as `docs/architecture.md`, with supporting
documents as `docs/database-schema.md`, `docs/api-specification.md`, and
`docs/requirements.md`.

After that, begin **Phase 1: Spring Boot + PostgreSQL project setup**.
