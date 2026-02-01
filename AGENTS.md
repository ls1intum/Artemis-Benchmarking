# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot server code under `de.tum.cit.aet.*`.
- `src/main/webapp`: Angular client (`app/` for components/services, `content/` for assets + SCSS, `main.ts` bootstrap).
- `src/main/resources`: server configuration, templates, and Liquibase migrations in `config/liquibase` (add new changesets to `master.xml`).
- `src/test/java` and `src/test/resources`: server tests and test data.
- `docs/`: diagrams and documentation assets.
- `config/`: Docker compose env files; `build/` is generated output.

## Build, Test, and Development Commands
- `npm run start`: run the Angular dev server with HMR on port 9000 (proxy configured in `proxy.conf.mjs`).
- `./gradlew bootRun`: start the Spring Boot server locally.
- `npm run webapp:build`: development build of the client; `npm run build` for production client build.
- `./gradlew -Pprod clean bootJar` (or `bootWar`): build production artifacts into `build/libs`.
- `npm run services:up`: start local services via Docker (`src/main/docker/services.yml`).

## Coding Style & Naming Conventions
- Indentation follows `.editorconfig`: 4 spaces by default, 2 spaces for TS/JS/JSON/CSS/SCSS/HTML/YAML.
- Client formatting: Prettier (`npm run prettier:format`) with single quotes and 140 char print width; linting via `npm run lint`.
- Server formatting: Spotless (`./gradlew spotlessCheck` / `./gradlew spotlessApply -x webapp`) and Checkstyle (`./gradlew checkstyleMain -x webapp`).
- Naming: Java classes in `UpperCamelCase`, methods/fields in `lowerCamelCase`; Angular files follow `*.component.ts`, `*.service.ts`, etc.
- DTOs: Always use Java `record` types for DTOs and avoid classes.

## Testing Guidelines
- Server tests use JUnit 5 in `src/test/java`.
- Unit tests typically end with `*Test.java`; integration tests use `*IT.java` and are excluded by default in `gradle/test.gradle`.
- Run unit tests with `./gradlew test`; run integration tests via your IDE or by adjusting the exclusion rules as needed.
- Client tests (when enabled) run with `npm run test` (Angular CLI).

## Commit & Pull Request Guidelines
- Commit messages are short, sentence-case imperatives (e.g., “Update dependencies”, “fix check style”); include PR/issue numbers in parentheses when relevant.
- PRs should include a clear summary, testing notes (commands run), links to related issues, and screenshots for UI changes.
- Ensure GitHub Actions CI passes before requesting review.

## Configuration & Security Tips
- Use `src/main/resources/config/application-local.yml` for local secrets (gitignored). Do not commit credentials.
- Docker environment values live in `config/benchmarking.env` and `config/mysql.env`.
