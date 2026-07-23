# Contributing to NetSimX

## Before you start

Run the test suite and make sure it passes on a clean checkout before
you change anything — that way you know any failures later are actually
yours:

```bash
mvn test
```

## Making a change

1. Fork and branch off `main`.
2. Keep changes focused — one logical change per pull request is much
   easier to review than a bundle of unrelated fixes.
3. If you're touching `simulation`, `routing`, `ai`, or `persistence`,
   add or update a test in the matching `src/test/java` package. This
   project has already found two real bugs (see `CHANGELOG.md`, 0.4.0)
   purely because a test exercised a code path manual testing never hit
   — it's worth the extra few minutes.
4. If you're touching the `gui` package, actually launch the app and
   click through the thing you changed. Compiling isn't the same as
   working.
5. Run `mvn test` again before opening the PR.

## Code style

- Match the surrounding code rather than introducing a new style in one
  file.
- Comments should explain *why*, not *what* — if a comment just restates
  the line under it, it's probably not needed.
- Section-divider comments (`// ---- Lifecycle ----`) are used
  throughout to break up longer classes; feel free to follow that
  pattern in new files of similar size.

## Reporting a bug

Include:
- What you did (steps to reproduce)
- What you expected
- What actually happened
- Whether it's reproducible from a fresh `mvn test` / `mvn javafx:run`,
  or something specific to your environment

## Adding a routing algorithm, traffic type, or topology template

These are the most self-contained things to add — see the "How to
Extend This Project" chapter in `docs/PROJECT_DOCUMENTATION.md` (or
Chapter 9 of the PDF book in `docs/book/`) for exactly where each of
these plugs in.
