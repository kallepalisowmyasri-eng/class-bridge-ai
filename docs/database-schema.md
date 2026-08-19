# Database Schema — ClassRecover AI (PostgreSQL)

> 17 tables from §24 of `class.md`. Naming: lowercase, snake_case.
> Primary keys are `BIGSERIAL`. Foreign keys use `ON DELETE CASCADE` where the
> child belongs to its parent. Timestamps default `now()`.

## Entity relationships

```
User (1) ──< Subject (1) ──< Class
User (1) ──< Notes, RecoveryProgress, CatchupPaths, LearningGaps
Class (1) ──< Transcript (1) ──< TranscriptSegment
Class (1) ──< Topic, Assignment, Announcement, Resource, Question,
               VisualContent, Quiz (1) ──< QuizQuestion
```

## Tables

### users
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | login identifier |
| password_hash | VARCHAR(255) NOT NULL | BCrypt/JWT |
| name | VARCHAR(120) | |
| created_at | TIMESTAMPTZ DEFAULT now() | |

### subjects
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | owner |
| name | VARCHAR(150) NOT NULL | |
| description | TEXT | |
| created_at | TIMESTAMPTZ DEFAULT now() | |

### classes
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| subject_id | BIGINT FK → subjects.id | |
| title | VARCHAR(200) NOT NULL | |
| class_date | DATE | |
| description | TEXT | |
| duration_seconds | INT | from ffprobe |
| recording_path | VARCHAR(500) | stored file |
| audio_path | VARCHAR(500) | extracted WAV |
| processing_status | VARCHAR(30) DEFAULT 'UPLOADED' | UPLOADED/AUDIO_READY/TRANSCRIBED/ANALYZED/READY/FAILED |
| created_at | TIMESTAMPTZ DEFAULT now() | |
| updated_at | TIMESTAMPTZ DEFAULT now() | |

### transcripts
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id UNIQUE | one transcript per class |
| language | VARCHAR(10) | e.g. en |
| full_text | TEXT | joined transcript |
| created_at | TIMESTAMPTZ DEFAULT now() | |

### transcript_segments
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| transcript_id | BIGINT FK → transcripts.id | |
| start_seconds | NUMERIC(10,2) | |
| end_seconds | NUMERIC(10,2) | |
| text | TEXT | |
| segment_order | INT | position in transcript |

### topics
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| name | VARCHAR(200) NOT NULL | |
| description | TEXT | |
| importance | VARCHAR(20) | CRITICAL/HIGH/MEDIUM |
| sequence | INT | order in class |
| source_start | NUMERIC(10,2) | evidence grounding |
| source_end | NUMERIC(10,2) | |

### assignments
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| title | VARCHAR(300) | |
| description | TEXT | |
| deadline | VARCHAR(200) | free text, only if mentioned |
| related_topic | VARCHAR(200) | |
| source_start | NUMERIC(10,2) | |
| source_end | NUMERIC(10,2) | |

### announcements
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| content | TEXT NOT NULL | |
| source_start | NUMERIC(10,2) | |
| source_end | NUMERIC(10,2) | |

### resources
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| title | VARCHAR(300) | |
| url | VARCHAR(500) | |
| resource_type | VARCHAR(30) | BOOK/ARTICLE/VIDEO/WEBSITE/FILE |
| source_start | NUMERIC(10,2) | |
| source_end | NUMERIC(10,2) | |

### questions
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| question | TEXT NOT NULL | discussed in class |
| answer | TEXT | |
| source_start | NUMERIC(10,2) | |
| source_end | NUMERIC(10,2) | |

### visual_contents
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| frame_path | VARCHAR(500) | extracted frame |
| timestamp | NUMERIC(10,2) | |
| description | TEXT | visual description |
| related_topic | VARCHAR(200) | |

### quizzes
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| class_id | BIGINT FK → classes.id | |
| title | VARCHAR(200) | |
| created_at | TIMESTAMPTZ DEFAULT now() | |

### quiz_questions
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| quiz_id | BIGINT FK → quizzes.id | |
| question_text | TEXT NOT NULL | |
| options_json | JSONB | array of options |
| correct_index | INT | only class-supported |
| explanation | TEXT | |

### recovery_progress
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| class_id | BIGINT FK → classes.id | |
| status | VARCHAR(20) DEFAULT 'IN_PROGRESS' | |
| percent | INT DEFAULT 0 | 0-100 |
| updated_at | TIMESTAMPTZ DEFAULT now() | |

### learning_gaps
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| class_id | BIGINT FK → classes.id | |
| concept | VARCHAR(200) | |
| recommendation | TEXT | review suggestion |
| gap_type | VARCHAR(20) | PREREQUISITE/RECOMMENDED |
| source_class_id | BIGINT | where it was taught |

### catchup_paths
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| class_id | BIGINT FK → classes.id | |
| step_order | INT | |
| title | VARCHAR(200) | |
| duration_minutes | INT | estimate |
| step_type | VARCHAR(20) | PREREQUISITE/CONCEPT/EXAMPLE/PRACTICE/QUIZ |
| completed | BOOLEAN DEFAULT false | |

### notes
| column | type | notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| class_id | BIGINT FK → classes.id | |
| content | TEXT | plain/markdown |
| created_at | TIMESTAMPTZ DEFAULT now() | |
| updated_at | TIMESTAMPTZ DEFAULT now() | |
