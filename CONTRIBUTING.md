# Contributing

## Branching Model

- `main`: stable/demo-ready branch, pull requests only.
- `dev`: integration branch, pull requests preferred with lighter review rules.
- Create feature branches from `dev` (or from `main` if `dev` does not exist).
- Do not push directly to `main`.
- Use Squash merge (recommended) to keep history clean.

## Branch Naming Convention

Use:

- `<type>/<short-title>`

Allowed types:

- `feat`
- `fix`
- `chore`
- `docs`
- `refactor`
- `test`

Examples:

- `feat/project-crud`
- `fix/login-redirect`
- `docs/error-handling-contract`
- `chore/update-gitignore`

## Commit Message Convention

Use:

- `feat: ...`
- `fix: ...`
- `chore: ...`
- `docs: ...`
- `refactor: ...`
- `test: ...`

Keep messages short and meaningful.

## Local Verification

- Before PR, run `./mvnw test`.
- Always use Maven Wrapper.
  - macOS/Linux: `./mvnw test`
  - Windows: `mvnw.cmd test`

## Artifacts Never to Commit

- `target/`
- `.env` (use `.env.example` when needed)
- IDE files:
  - `.idea/`
  - `.vscode/`
  - `.classpath`
  - `.project`
  - `.settings/`

## PR Expectations

- Keep pull requests small and focused (single purpose).
- Require at least 1 approval before merge (aligned with branch protection settings).
- Clearly describe what changed and how it was tested locally.
- If DB schema changed, add/update Flyway migrations and describe them in the PR.
