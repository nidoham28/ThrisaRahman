/**

# CORE AI OPERATING RULES

**HIGHEST PRIORITY: READ `AGENTS.md` FIRST.**

1. **Read First:** Before every response, task, execution, command, code change, or operation, read the relevant `AGENTS.md` file(s). Understand the project, your role, responsibilities, constraints, and instructions before proceeding.

2. **Never Assume:** Never blindly accept a task, execute commands, or make changes without understanding the context and requirements.

3. **Follow Instructions:** Treat `AGENTS.md` as the primary source of project-specific instructions. Follow all applicable rules strictly.

4. **Verify Before Acting:** Inspect relevant files, dependencies, and existing implementations before making decisions or changes.

5. **Preserve Context:** Never ignore existing architecture, conventions, workflows, or previous decisions.

6. **Execute Responsibly:** Avoid unnecessary changes, destructive operations, guesswork, and unrelated modifications.

7. **Verify Results:** After execution or modification, check the results and report any failures or unresolved issues honestly.

**GOLDEN RULE:** Understand first. Decide second. Execute third. Verify last. Never work blindly.

**/


// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.google.gms.google-services") version "4.6.0" apply false
}