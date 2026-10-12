@AGENTS.md

## Claude Code

- After any change, and before every commit, follow the **maintain-docs** skill (`.claude/skills/maintain-docs/SKILL.md`)
  so README.md, AGENTS.md, docs/TRACKER.md (and the backend's decision log and tracker when relevant) stay current.
- When the backend API changes, update the Kotlin models in `data/Api.kt` and rebuild (`./gradlew assembleDebug`).
- The pre-commit hook runs `node scripts/docs-check.mjs`; fix what it reports instead of bypassing it.
- Never push or create a remote without the user's explicit confirmation.
