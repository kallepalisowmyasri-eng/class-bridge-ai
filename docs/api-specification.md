# API Specification — ClassRecover AI

> REST API consumed by the frontend. Base URL: `http://localhost:8080`.
> Auth: `Authorization: Bearer <JWT>` on all endpoints except `/api/auth/**`
> and `/api/health`. Errors return the shape:
> `{ "timestamp": "...", "status": 404, "error": "Not Found", "message": "...", "path": "/..." }`

## Health (Phase 1)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/health | — | `{"status":"UP"}` |

## Auth (Phase 1 / security)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/auth/signup | `{email, password, name}` | `201` `{token, user{id,email,name}}` |
| POST | /api/auth/login | `{email, password}` | `200` `{token, user{id,email,name}}` |
| GET | /api/auth/me | — | `200` current user |

## Subjects (Phase 2)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/subjects | `{name, description}` | `201` `SubjectDto` |
| GET | /api/subjects | — | `200` `[SubjectDto]` (own) |
| GET | /api/subjects/{id} | — | `200` `SubjectDto` |
| PUT | /api/subjects/{id} | `{name, description}` | `200` `SubjectDto` |
| DELETE | /api/subjects/{id} | — | `204` |

## Classes (Phase 2)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/classes | `{subjectId, title, date, description}` | `201` `ClassDto` |
| GET | /api/classes | — | `200` `[ClassDto]` |
| GET | /api/classes/{id} | — | `200` `ClassDto` (with status) |
| DELETE | /api/classes/{id} | — | `204` (class + files + data) |

## Recording upload (Phase 3)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/classes/{id}/upload | multipart `file` (mp4/mp3/wav/m4a, ≤2GB) | `200` `ClassDto` status=UPLOADED |
| GET | /api/classes/{id}/recording | — | `200` file stream |
| DELETE | /api/classes/{id}/recording | — | `204` remove file |

## Processing (Phases 4–6, triggered after upload)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/classes/{id}/process | — | `202` starts pipeline; class status moves UPLOADED→AUDIO_READY→TRANSCRIBED→ANALYZED→READY |
| GET | /api/classes/{id}/status | — | `200` `{processingStatus}` |
| GET | /api/classes/{id}/transcript | — | `200` `{language, segments:[{start,end,text}]}` |

## Class Recovery Engine (Phase 7)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/classes/{id}/missed | — | `200` `{summary}` — What happened |
| GET | /api/classes/{id}/must-learn | — | `200` `[{name, importance, source}]` |
| GET | /api/classes/{id}/catchup-path | — | `200` `[{step, title, type, minutes}]` |
| GET | /api/classes/{id}/prerequisites | — | `200` `{referenced:[], recommended:[]}` |
| GET | /api/classes/{id}/explanations | — | `200` `[{concept, explanation}]` |
| GET | /api/classes/{id}/announcements | — | `200` `[{content, source}]` |
| GET | /api/classes/{id}/assignments | — | `200` `[{title, description, deadline, source}]` |
| GET | /api/classes/{id}/resources | — | `200` `[{title, url, type, source}]` |
| GET | /api/classes/{id}/timestamps | — | `200` `[{time, label}]` |
| GET | /api/classes/{id}/revision | — | `200` `{definitions, concepts, formulas, examples}` |
| GET | /api/classes/{id}/quiz | — | `200` `{questions:[{text, options, correctIndex}]}` |
| GET | /api/classes/{id}/watch | — | `200` `{mustWatch:[], recommended:[], optional:[]}` |

## Topics & Visuals
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/classes/{id}/topics | — | `200` `[{name, importance, sequence, source}]` |
| GET | /api/classes/{id}/visuals | — | `200` `[{framePath, timestamp, description, relatedTopic}]` |

## Notes (Phase 8)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/classes/{id}/notes | — | `200` `[{id, content, updatedAt}]` |
| POST | /api/classes/{id}/notes | `{content}` | `201` note |
| PUT | /api/notes/{noteId} | `{content}` | `200` note |
| DELETE | /api/notes/{noteId} | — | `204` |

## Recovery progress (Phase 8/12)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/classes/{id}/progress | — | `200` `{percent, status, items:[{label, done}]}` |
| PUT | /api/classes/{id}/progress | `{percent, status}` | `200` updated |
| GET | /api/classes/{id}/catchup | — | `200` catch-up path with completion |
| PUT | /api/catchup/{stepId} | `{completed}` | `200` step |

## Previous Classes (Phase 9)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/subjects/{id}/classes | — | `200` `[ClassDto]` ordered by date |
| GET | /api/classes/search?q= | — | `200` search across classes |

## Chat With Class (Phase 10, RAG)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/classes/{id}/chat | `{question}` | `200` `{answer, source:[{classId, timestamp}]}` |
| POST | /api/chat | `{question}` | `200` cross-class answer + sources |

## Learning Gap Engine (Phase 11)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/classes/{id}/learning-gaps | — | `200` `[{concept, recommendation, gapType, sourceClassId}]` |
| GET | /api/classes/{id}/recommended-review | — | `200` `[{concept, fromClass, link}]` |

## Quiz answers (Phase 8/12)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| POST | /api/classes/{id}/quiz/submit | `{answers:[{questionId, index}]}` | `200` `{score, correct:[...], percent}` |

## JSON examples

### ClassDto
```json
{
  "id": 1, "subjectId": 1, "title": "Introduction to Binary Search",
  "date": "2026-08-10", "description": "...", "durationSeconds": 1860,
  "processingStatus": "READY"
}
```

### Whisper transcript segment (Phase 5)
```json
{ "start": 252.0, "end": 260.0, "text": "Teacher explains the algorithm." }
```

### What to Watch (Phase 7)
```json
{
  "mustWatch": [{"from": 1932, "to": 2720, "reason": "Binary search algorithm explanation"}],
  "recommended": [{"from": 3735, "to": 4110, "reason": "Assignment announcement"}],
  "optional": [{"from": 4810, "to": 5100, "reason": "Extended example"}]
}
```
