# Spring Boot 4.1 and dependency updates

Upgrade the application to the latest stable Spring Boot 4.1 patch, 4.1.3. Refresh direct backend, build, and frontend dependencies while preserving the existing application behavior.

1. Update `gradle/libs.versions.toml` using published versions. Let the Spring Boot BOM select managed dependencies and check Springdoc compatibility with Spring Boot 4.1. Keep Java 25 and Node.js 24 LTS as the runtime baselines.
2. Refresh compatible package versions and regenerate `webapp/package-lock.json` with npm. Keep the existing application dependency majors. Update ESLint and its React plugins together because ESLint 9 has reached end of support.
3. Run the Gradle test and packaging pipeline and the frontend linter. Enable Node VM modules for Jest so it can load MSW's ESM dependency. Align the documented build tool versions and check the final diff for whitespace errors.

## Verification

- `CI=true ./gradlew build --console=plain` passed with 360 Java tests and 68 frontend tests.
- `npm run lint` passed with 25 warnings and no errors. `git diff --check` passed.
- The packaged JAR contained Spring Boot 4.1.3 and Springdoc 3.2.1. The web UI, Swagger UI, and OpenAPI document returned HTTP 200.
- npm audit reported 18 moderate entries in the Jest test dependency tree. Its suggested remediation would downgrade Jest to 29.
