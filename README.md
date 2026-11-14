# DevPilot AI

An ML-powered developer intelligence platform with issue classification,
prediction history, and a read-only AI assistant for selected GitHub repositories.

## Project Structure

- `frontend/` — React + JavaScript user interface
- `backend/` — Java + Spring Boot application API
- `ml/` — Python machine learning service and experiments
- `datasets/` — Dataset setup and documentation
- `docs/` — Architecture and experiment documentation

## Development Status

The ML service classifies issue titles and descriptions with a TF-IDF and
balanced Logistic Regression model. The React frontend calls the Spring Boot
API, which stores signed-in users and prediction history in MySQL and forwards
classification requests to the Python service. Docker configuration is not
currently included in this repository.

## Run the ML Prediction API

From the repository root, first build the paired model/vectorizer artifact from
the processed title/body splits:

```bash
ml/.venv/bin/python ml/train_title_body.py
```

This writes `ml/artifacts/title_body_bundle.joblib`. The existing
`balanced_model.joblib` artifacts come from an earlier experiment and are not
used by this API.

Start the API:

```bash
PYTHONPATH=ml/src ml/.venv/bin/uvicorn devpilot_ml.api:app --host 127.0.0.1 --port 8000
```

Check `http://127.0.0.1:8000/health`, open `http://127.0.0.1:8000/docs`, or
submit a prediction:

```bash
curl -X POST http://127.0.0.1:8000/predict \
  -H 'Content-Type: application/json' \
  -d '{"title":"Application crashes on startup","body":"The CLI exits with an unexpected exception."}'
```

Install dependencies with `ml/.venv/bin/pip install -r ml/requirements.txt` if
needed. Run the API tests with:

```bash
PYTHONPATH=ml/src ml/.venv/bin/pytest ml/tests
```

Set `DEVPILOT_MODEL_PATH` to use the paired artifact at another path.

## Run the Spring Boot backend

The backend also provides a signed-in chat endpoint at `POST /api/chat`. It uses Groq's OpenAI-compatible Chat Completions API. Keep the Groq API key in the backend environment; never put it in frontend code:

```bash
export GROQ_API_KEY=your-groq-api-key
export GROQ_MODEL=openai/gpt-oss-20b
# Required by the MySQL datasource; configure it for your local database.
export DB_PASSWORD='your-local-database-password'
cd backend
mvn spring-boot:run
```

The default model can be changed with `GROQ_MODEL`, and the API base URL with `GROQ_BASE_URL`. The free tier has rate limits. General conversation (such as a greeting) is sent without repository context. For a repository question, the backend inspects only repositories the signed-in user explicitly enabled for agent access; it sends relevant search results or retrieved file contents to Groq. The tools are read-only and cannot change files or GitHub repositories.

Send `{ "message": "Explain this code..." }` to `/api/chat` with the signed-in session cookie.

The backend exposes `POST /api/predictions` and forwards the title and body to
the Python API at `/predict`. Start the Python API as described above, then in
a second terminal run (Java 17 and Maven required):

```bash
cd backend
mvn spring-boot:run
```

By default, the Java service calls `http://127.0.0.1:8000`. Keep the Python API
bound to loopback or behind a private service boundary; it does not implement
user authentication. To point the Java service to a
different Python service URL, set `DEVPILOT_ML_BASE_URL` before starting the
backend. For example:

```bash
DEVPILOT_ML_BASE_URL=http://localhost:8000 mvn spring-boot:run
```

## Configure the GitHub App integration

Create a GitHub App in **Settings → Developer settings → GitHub Apps**. Grant
only **Metadata: Read-only** and **Contents: Read-only** repository permissions;
do not enable write permissions. Set its Setup URL to
`https://<your-backend-host>/api/integrations/github/callback` (use the local
backend URL while developing), then install the app on the intended account or
organization. The callback validates a one-time session state and verifies the
installation against GitHub with a short-lived, server-generated App JWT before
storing its account and installation identifiers. The callback does not persist
GitHub tokens; repository APIs mint short-lived read-only installation tokens
when needed and keep them only in backend memory.

Provide these values to the backend process through your deployment secret
store or local untracked environment, never through frontend build variables:

```bash
export GITHUB_APP_ID='<App ID>'
export GITHUB_APP_SLUG='<app-slug>'
export GITHUB_APP_PRIVATE_KEY="$(cat /secure/path/devpilot-app.private-key.pem)"
```

The key may be a GitHub-generated RSA private key; the backend accepts the PEM
with literal newlines or `\\n` sequences. Keep the key file outside the source
tree. The signed-in browser starts setup with `POST /api/integrations/github/connect`;
enable **Redirect on update** so changing repositories on an existing
installation also returns to this callback. The callback returns to `/settings?github=connected`. The signed-in settings page
loads installed repositories with `GET /api/integrations/github/repositories`.
Each row shows the GitHub App's read-only access separately from the user's
**Agent access** opt-in; update that setting with
`PUT /api/integrations/github/repositories/{repositoryId}/access` and
`{"enabled":true|false}`. All opt-ins start disabled. Mutating requests require
the configured frontend `Origin` and existing `devpilotUserId` server session.

For chat, the backend creates a short-lived installation token in memory,
intersects enabled repository IDs with the current installation, and exposes
read-only `list_repository_files` and `read_repository_file` tools to the model.
Tokens are not saved. File contents are fetched only when a tool is called and
sent to the configured Groq-compatible provider as context. Repository files are
treated as untrusted input. The agent cannot write, commit, or push.

Send a prediction to the Java API on port 8080:

```bash
curl -X POST http://127.0.0.1:8080/api/predictions \
  -H 'Content-Type: application/json' \
  -d '{"title":"Application crashes on startup","body":"The CLI exits with an unexpected exception."}'
```

The response contains the predicted `label` and class `probabilities`. Hibernate
SQL logging is disabled by default; set `SPRING_JPA_SHOW_SQL=true` temporarily
for local query debugging. Run the
backend checks from `backend/` with `mvn test`; the mock HTTP test verifies the
request and response between Java and Python without starting the ML service.

## Planned Capabilities

- Software issue classification
- Similar issue retrieval
- Explainable predictions
- Model evaluation and experiment tracking
EOF
