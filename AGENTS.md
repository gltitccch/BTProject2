# KinTrace Project Rules & Workflow Standards

## Mandatory Pre-Implementation Architectural Scope Confirmation

Before implementing or proposing code for any new feature, rule, or bug fix on the KinTrace project, you must always ask the user to confirm its architectural scope first.

### 1. Clarify Scope First
Explicitly ask whether the requested change should be applied **Globally** (affecting the entire app and all interconnected modules) or **Locally** (restricted only to that specific screen or component).

### 2. Map Connected Modules
Briefly list all connected tools and layers that will be impacted if the change is made global, specifically:
- **Cloud Firestore persistence & Central Repository** (`FirestoreHelper.kt`)
- **Real-Time Tree Synchronizer** (`CentralTreeSynchronizer.kt`, coordinators, event bus)
- **Interactive Family Tree** (Canvas, custom node rendering, Tree Legend badges & colors in `FamilyTreeView` / `InteractiveTreeActivity.kt`)
- **Ancestor Pedigree View & Fan Chart** (Ahnentafel tree traversal, pedigree rendering)
- **Populated Records List & Member Details Screen** (`FamilyRecordsActivity.kt`, `MemberDetailActivity.kt`, `MemberAdapter.kt`)
- **Add/Edit Member Forms & Branch Merging** (`AddMemberActivity.kt`, `EditMemberActivity.kt`, `MergeBranchesActivity.kt`)
- **Central Relationship Validation Pipeline** (`FamilyRelationshipService.kt`, `FamilyLinkValidator.kt`, `MarriageValidationEngine.kt`)
- **Dashboard Insights & Counters** (`InsightsActivity.kt`, `HomeActivity.kt`, `ExportTreeActivity.kt`)

### 3. Enforce Global Integration Standard
When a global scope is confirmed:
- Architect the solution as a **single source of truth**.
- Ensure the change routes through the central repository (`FirestoreHelper`) and reactive synchronizer (`CentralTreeSynchronizer`).
- Guarantee that every connected screen, validation check, and visual component updates automatically in real time without requiring app restarts or manual reloads.
- **Never write isolated, single-screen solutions** unless the user explicitly confirms that the change is strictly local.

---

## GitHub Push Workflow Standard (`/push` Signal Condition)
- Code must **never be pushed to remote GitHub automatically** without explicit user instruction.
- The slash command `/push` serves as the **exclusive signal/condition** authorizing and triggering a git push to the official remote repository: `https://github.com/gltitccch/BTProject2.git`.
- When the user sends `/push`:
  1. Verify that all unit tests pass (`.\gradlew.bat testDebugUnitTest`) and the debug build compiles cleanly.
  2. Check local git status and commit any outstanding verified changes.
  3. Push to `origin master` (`https://github.com/gltitccch/BTProject2.git`).
  4. Report the push status and remote commit hash to the user.

---

## Mandatory Post-Change Re-Run & Regression Verification Standard

For every single change, addition, or feature increment made in the codebase, you must always strictly execute a complete re-run and verification check across everything involved in that update before concluding the step:
1. **Full Test Suite Execution**: Immediately run all unit tests (`.\gradlew.bat testDebugUnitTest`) to verify that no existing functions, validations, models, or sync listeners were accidentally deleted, altered, or broken.
2. **Build & Compilation Sanity**: Confirm that the Android debug build compiles cleanly without syntax errors, missing resource IDs, or unresolved symbols.
3. **Involved Module Audit**: Explicitly inspect and verify all touched and interconnected screens/components (e.g., `HomeActivity`, layout XML files, view IDs, click listeners, Firestore queries, and `CentralTreeSynchronizer` events) to guarantee that:
   - No existing button IDs, XML elements, or click listeners were deleted, renamed, or overridden.
   - All existing user journeys (tree creation/selection, member viewing/editing, theme toggle, logout, bottom navigation) remain 100% intact.
4. **Transparent Verification Reporting**: Always report the test execution results, the build status, and the exact list of verified components to the user after every change.


