# Working on sbt-idea-plugin

## Build and validation

- Use JDK 17. Published artifacts target Java 11 bytecode; this is not the development JDK requirement.
- Run sbt from the repository root. See [development notes](README.md#development-notes).
- During editing, start with the affected suite, for example:
  `sbt 'ideaSupport/testOnly org.jetbrains.sbtidea.runIdea.IdeaRunnerTest'`.
- Use `sbt testFast` for all tests except consumer integration suites, and `sbt testIntegration`
  for those suites. The aliases do not change the default `test` task or persist test filters.
- Before landing, validate the final changes with `sbt test`, or both aliases. CI uses `sbt test`.
- Keep consumer integration suites in `org.jetbrains.sbtidea.integrationTests` so the commands
  classify them correctly. These tests launch nested sbt processes and may download SDKs/JBRs/plugins.
- Tests run serially because some use global state. Preserve fresh sbt processes in tests whose
  assertions concern startup, reload, locking or concurrency.

## Repository map and test fixtures

- `core/`: declarative project structure; `packaging/`: artifact construction;
  `ideaSupport/`: IntelliJ SDKs, runtime directories, IDE launches and plugin tasks.
- Shared consumer-process helpers are in `testUtils/src/test/scala/org/jetbrains/sbtidea/testUtils/`.
- Consumer projects are in `ideaSupport/testData/projects/`. Their copy helper uses `git ls-files`;
  newly added fixture files must be staged before running a test that copies them.
- JUnit XML reports are in each module's `target/test-reports/`. Old report files may remain after
  test classes are removed, so do not sum existing XML files as proof of the current run's test count.
- Use temporary fixtures when testing destructive behavior. Integration caches live under
  `tempIntellijSdks/` and `tempIntellijArtifactsDownloads/` in this checkout.

## Changes and documentation

- Prefer explicitly named tests with shared private assertion helpers over loops declaring tests.
- Keep consequential design decisions and tradeoffs in [Markdown ADRs](docs/adr/README.md).

## Releases

For releases of sbt-idea-plugin itself, read
[Development notes](README.md#development-notes) and follow the canonical release
guide linked there. Maintain the procedure in that guide; link to it from
instructions or skills instead of duplicating it.
