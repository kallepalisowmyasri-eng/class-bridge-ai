# ClassBridge-AI

> AI‑Powered Class Recovery Platform
---
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/yourrepo/yourproject/actions)
[![License](https://img.shields.io/github/license/yourrepo/yourproject)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.0-blue)](https://github.com/yourrepo/yourproject/releases)

ClassBridge‑AI is a full‑stack web application that automatically transforms recorded online lectures into structured, searchable, evidence‑grounded learning material and generates personalized catch‑up paths, quizzes, and AI‑driven insights for students who missed a class.
## Tech Stack

| Layer | Tech |
|-------|------|
| Backend | Java 17/21, Spring Boot, Spring Security, Maven |
| Frontend | HTML, CSS, JavaScript |
| Database | MongoDB |
| AI | Gemini API |
| Speech‑to‑Text | Whisper / faster‑whisper (Python) |
| Media Processing | FFmpeg |

## Documentation

- [Project specification](class.md) — full product requirements, features,
  architecture, database design, MVP phases, and next steps.

## Contributing

We welcome contributions! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for details on our development workflow, coding standards, and how to submit pull requests.

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.


## Getting Started

Follow these steps to run the project locally:

1. **Clone the repository**  
   ```bash
   git clone https://github.com/yourrepo/yourproject.git
   cd yourproject
   ```

2. **Set up the backend**  
   - Ensure **Java 21** (or 17) and **Maven** are installed.  
   - Create a PostgreSQL database named `classbridge` and update `backend/src/main/resources/application-dev.properties` with your credentials.  
   - Set the Gemini API key:
     ```bash
     export GEMINI_API_KEY=your_key   # Linux/macOS
     set GEMINI_API_KEY=your_key      # Windows PowerShell
     ```

3. **Run the backend**  
   ```bash
   cd backend
   mvn spring-boot:run
   ```

4. **Start the Whisper transcription service** (Python)  
   ```bash
   cd transcription-service
   python -m venv venv
   venv\\Scripts\\activate   # Windows
   pip install -r requirements.txt
   python whisper_service.py
   ```

5. **Launch the frontend**  
   Open `frontend/index.html` in a browser (no server required).

6. **Verify the setup**  
   ```bash
   curl http://localhost:8080/api/health
   # Expected output: {"status":"UP"}
   ```

You can now upload a class recording via the web UI and explore the API endpoints listed in the documentation.
## Screenshots

A preview of the ClassBridge‑AI dashboard UI:

![Dashboard mockup](file:///C:/Users/durga/.gemini/antigravity-ide/brain/a14e1e27-78af-4150-98f6-f438c598db32/dashboard_mockup_1792847846673.jpg)
