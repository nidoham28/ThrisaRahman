/** MODULE ACCESS RULE (STRICT)

- EDITABLE: only the `app/` module. Every new file, fix, refactor, or any
  change must happen inside `app/` and nowhere else.
- READ-ONLY: every other module — `:ai`, `:common`, `:speech`, `:terminal`,
  and any module added in the future. You may read their code and call
  their public APIs, but NEVER create, edit, rename, or delete any file
  inside them, including build.gradle.kts, manifests, resources, or configs.
- If a task seems to require changing a read-only module, do NOT touch it.
  Solve it inside `app/` only, or stop and ask for explicit permission.
- Before finishing, verify every changed file path starts with `app/`.
  Revert anything outside it.

**/