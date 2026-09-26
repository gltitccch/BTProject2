# KinTrace: Future Roadmap & Proposal Ideas (Phase 2 / Post-Capstone)

> **Living Backlog & Standing Directive**:  
> This document serves as the permanent, living repository of advanced features, architectural proposals, and future-phase improvements for **KinTrace (Mobile-Based Genealogical Bloodline Tracing System)**.  
> **Rule of Baseline Integrity**: The core capstone scope, system architecture, data models, and Philippine legal compliance rules are 100% finalized and approved. All items documented here represent strictly optional, high-impact Phase 2 / post-capstone expansions. Whenever new suggestions or enhancements are requested, they will be systematically structured and logged into this document.

---

## 1. Summary Matrix of Proposal Ideas

| # | Proposal Concept | Category | Target Phase | Primary Architectural Impact |
| :-: | :--- | :--- | :-: | :--- |
| **01** | **GEDCOM 5.5.1 / 7.0 Interchange** | Interoperability | Phase 2.1 | Bidirectional translator (`GedcomParser`) with audit gatekeeper |
| **02** | **On-Device ML Kit OCR for PSA & Parish Records** | Intelligent Capture | Phase 2.2 | Google ML Kit text recognition with field mapping & confidence scores |
| **03** | **Autosomal DNA Correlation (cM to Civil Degrees)** | Genetic Genealogy | Phase 2.3 | Shared cM probability engine mapped to Roman Civil degrees ($1^\circ$ to $6^\circ$) |
| **04** | **GIS Ancestral Migration & Province Heatmaps** | Cultural & Spatial | Phase 2.4 | Philippine Standard Geographic Code (PSGC) coordinates & timeline map |
| **05** | **Offline-First Room SQLite & WorkManager Mesh** | Reliability & Access | Phase 2.5 | Local Room DAO persistence with auto-sync queue for remote areas |
| **06** | **Client-Side Encrypted Proof Vault (AES-GCM-256)** | Security & RA 10173 | Phase 2.6 | End-to-end PBKDF2 + AES-GCM encryption for court decrees & vital IDs |
| **07** | **Philippine Regional Dialect Localization** | Accessibility | Phase 2.7 | Multilingual narrative engine for Ilocano, Bisaya, Hiligaynon, Bikol, Waray |
| **08** | **Collaborative 3-Way Branch Merge Wizard** | Collaboration | Phase 2.8 | Visual side-by-side branch comparison with interactive conflict resolver |
| **09** | **Oral History & Voice Memories Archive** | Multimedia Heritage | Phase 2.9 | AAC audio recorder with waveforms attached to member timeline events |
| **10** | **Kinship-Aware Reunion & Milestone Planner** | Community & Family | Phase 2.10 | Calendar integration generating proximity-based invitations & reminder feeds |
| **11** | **Multi-Tree Anchor-Bridge Merger** | Cross-Tree Integration | Phase 2.11 | Select target & incoming trees; link via Spouse, Parent-Child, or Shared Ancestor bridge |
| **12** | **Non-Destructive Clan Synthesis** | Safety & Data Integrity | Phase 2.12 | Generates new combined Master Tree C while keeping original trees 100% intact |
| **13** | **Cross-Account Collaborative Tree Merge** | Social & Clan Federation | Phase 2.13 | Merge with external family trees via Invite Code handshake and dual-owner approval |
| **14** | **Automated Graph Overlap & 3-Way Diff Engine** | Graph Intelligence | Phase 2.14 | Bipartite duplicate detection, side-by-side field conflict picker, and cycle/legal pre-commit audit |
| **15** | **Safe Master Tree Synthesis (Zero-Risk Clan Fusion Engine)** | Non-Destructive Synthesis | **Immediate / Executable** | Deep-clones Tree A & Tree B (or via Invite Code) into new Master Tree C; original trees 100% untouched |
| **16** | **Dedicated "Merged Clan Space" & Separate Sandbox Viewer** | Architectural Isolation | **Immediate / Executable** | Houses merged trees exclusively inside the "Merge Branches" portal; user's "View Tree" stays 100% pure |
| **17** | **Multi-Generational Benchmark Tree Seeder (20-Member Clan Suite)** | Testing & Simulation | Developer Utility / Immediate | Topological 4-generation 20-member benchmark via FamilyRelationshipService & CentralTreeSynchronizer |

---

## 2. Detailed Proposal Specifications

### Proposal 01: International GEDCOM 5.5.1 / 7.0 Standard File Interchange
- **Concept & Purpose**: Enable KinTrace users to import and export family tree files using the universal **GEDCOM (GEnealogical Data COMmunication)** format, allowing seamless interoperability with global platforms such as FamilySearch, WikiTree, MyHeritage, and Gramps.
- **Architectural Fit**:
  - Implement a dedicated `GedcomParser` and `GedcomSerializer` module.
  - When importing a `.ged` file, the parser translates `INDI` (Individual) and `FAM` (Family) records into KinTrace `Person` entities.
  - **Mandatory Safety Guard**: All imported relationships must pass through `FamilyRelationshipService.auditFamilyTree()` to ensure imported records comply with Philippine marriage laws, minimum marriage age (RA 11596), and cycle prevention before saving to Firestore.
- **Value Proposition**: Eliminates platform lock-in, facilitates family data migration from existing desktop software, and establishes KinTrace as a globally compatible genealogical tool.

---

### Proposal 02: On-Device ML Kit OCR for Philippine Civil & Parish Records
- **Concept & Purpose**: Allow users to point their smartphone camera at physical Philippine vital documents—such as PSA/NSO Birth Certificates, Marriage Certificates, Local Civil Registry copies, and Catholic Parish baptismal certificates—and automatically populate member creation forms.
- **Architectural Fit**:
  - Integrate **Google ML Kit Text Recognition** for on-device processing without cloud latency or subscription fees.
  - Implement an extraction pattern matcher trained on standard Philippine civil registry forms (identifying Child Name, Father's Full Name, Mother's Maiden Name, Date of Birth, Place of Birth, Registry Number).
  - Pre-fill `AddMemberActivity` with editable text fields, accompanied by field confidence badges (`✓ High Confidence`, `⚠ Review Needed`).
- **Value Proposition**: Drastically lowers data entry barrier for non-tech-savvy users and elders, speeds up large family onboarding, and preserves aging paper records digitally.

---

### Proposal 03: Autosomal DNA Segment Correlation (cM to Civil Degrees)
- **Concept & Purpose**: Bridge biological civil consanguinity under Philippine law with empirical molecular genetics by correlating shared centiMorgan (cM) data from DNA testing services (e.g. 23andMe, AncestryDNA) with predicted civil degrees of consanguinity.
- **Architectural Fit**:
  - Extend `BloodlineTracer` and `RelationshipExplainer` with a `DnaConsanguinityCalculator`.
  - Ingest shared cM values and segment counts; cross-reference against the **Shared cM Project** probability distributions.
  - Display dual output:
    1. **Empirical Genetic Match**: Expected shared DNA percentage (e.g., 12.5% shared DNA $\approx$ 850 cM).
    2. **Civil Law Equivalent**: Predicted legal relationship degrees (e.g., 3rd Degree Collateral [Uncle/Niece] or 4th Degree Collateral [First Cousins]).
- **Value Proposition**: Empowers users solving unknown parentage cases, adoption searches, or disputed lineages with scientifically grounded genealogical analysis.

---

### Proposal 04: Geographic GIS Historical Migration & Province Heatmaps
- **Concept & Purpose**: Visualize the historical and geographical journey of a Filipino family clan over decades by plotting birthplaces and residences on an interactive map of the Philippine archipelago.
- **Architectural Fit**:
  - Map `birthPlace` strings to normalized **Philippine Standard Geographic Code (PSGC)** coordinates (Region, Province, Municipality/City, Barangay).
  - Integrate Android Google Maps SDK or OpenStreetMap with heatmaps and animated trajectory vectors.
  - Provide a generational timeline slider: stepping through Gen 1 $\rightarrow$ Gen 4 animates migration lines from ancestral origin provinces (e.g., Ilocos, Batangas, Iloilo, Leyte) to urban centers (Metro Manila, Cebu, Davao) and international overseas diaspora hubs.
- **Value Proposition**: Adds historical and cultural depth, transforming dry family data into a compelling visual narrative of family heritage and migration.

---

### Proposal 05: Offline-First Room SQLite Cache with WorkManager Mesh Sync
- **Concept & Purpose**: Provide uninterrupted, full-featured family tree viewing, adding, and editing even in rural Philippine areas with weak or absent cellular connectivity.
- **Architectural Fit**:
  - Introduce an embedded **Room SQLite database** mirroring Firestore entities locally (`LocalPersonEntity`, `LocalTreeEntity`).
  - Read operations serve from local SQLite with 0ms latency.
  - Write operations commit locally and register pending mutations in a sync queue.
  - Android `WorkManager` monitors network connectivity; upon detecting an active internet connection, it flushes queued operations to `CentralTreeSynchronizer` and Firestore via idempotent batch writes.
- **Value Proposition**: Guarantees zero downtime and complete usability during provincial family reunions, cemetery visits, and remote rural field research.

---

### Proposal 06: Client-Side Encrypted Proof Vault (AES-GCM-256)
- **Concept & Purpose**: Provide enterprise-grade security and confidentiality for sensitive civil documents (Court Adoption Decrees, Annulment / Divorce Judgments, Government IDs) attached to family members.
- **Architectural Fit**:
  - Implement client-side cryptographic storage in `DocumentHelper`.
  - When a user uploads a sensitive document classified as "Private Legal Proof", the file is encrypted on the device using **AES-GCM-256** with a key derived from the user's master passcode using **PBKDF2** with a unique salt.
  - The ciphertext is stored in Firebase Storage; only authorized tree members who hold the decryption key can view the decrypted document.
- **Value Proposition**: Strengthens compliance with the **Data Privacy Act of 2012 (RA 10173)**, assuring users that sensitive legal records remain strictly confidential even from backend administrators.

---

### Proposal 07: Philippine Regional Dialect Kinship Localization
- **Concept & Purpose**: Generate natural language kinship explanations and UI titles in major Philippine regional languages to celebrate regional Filipino heritage.
- **Architectural Fit**:
  - Extend `RelationshipExplainer` and `KinshipTitleHelper` with language-specific string bundles.
  - Supported regional languages:
    - **Ilocano** (*Manong*, *Manang*, *Apo*, *Kasinsin*, *Kayong*, *Ipat*)
    - **Cebuano / Bisaya** (*Manoy*, *Manay*, *Apohan*, *Paryente*, *Ugangan*)
    - **Hiligaynon / Ilonggo** (*Toto*, *Inday*, *Lolo*, *Lola*, *Pakaisa*)
    - **Kapampangan** (*Koya*, *Achi*, *Ingkung*, *Apu*, *Pisan*)
    - **Bikol** (*Manoy*, *Manay*, *Lolo*, *Lola*, *Pinsan*)
    - **Waray** (*Mano*, *Mana*, *Apohan*, *Patod*)
  - User can toggle between English, Filipino (Tagalog), and regional dialects in app settings.
- **Value Proposition**: Increases emotional resonance, inclusivity, and pride in indigenous regional roots across diverse Filipino households.

---

### Proposal 08: Collaborative 3-Way Branch Merge Wizard
- **Concept & Purpose**: Provide a guided visual diff and merge interface when two independent family tree owners discover they belong to the same overarching clan and wish to merge branches.
- **Architectural Fit**:
  - Enhance `MergeBranchesActivity` with a three-column interactive conflict resolver:
    - Left: *Branch A (Source)*
    - Right: *Branch B (Incoming)*
    - Center: *Merged Result*
  - Automatically identifies overlapping members using fuzzy name matching and birthdate proximity.
  - Highlights discrepancies (e.g. differing middle names, contradictory birthplaces) and lets the tree owner choose which attribute to retain on a field-by-field basis before committing.
- **Value Proposition**: Enables large clans to federate their decentralized trees into an authoritative master genealogy without data corruption or accidental duplicate creation.

---

### Proposal 09: Oral History & Voice Memories Archive
- **Concept & Purpose**: Preserve the spoken voices, stories, folk songs, and personal memories of elderly family members directly attached to their genealogical record.
- **Architectural Fit**:
  - Add an audio recording widget to `MemberDetailActivity` utilizing Android `MediaRecorder` (AAC/M4A format).
  - Generates waveform previews and allows tagging recordings by topic (*"World War II Memories"*, *"How Jose and Maria Met"*, *"Lola's Traditional Recipe Story"*).
  - Audio files upload to Firebase Storage with streaming playback support.
- **Value Proposition**: Elevates KinTrace from a static genealogical chart into an emotional living archive of family oral history.

---

### Proposal 10: Kinship-Aware Reunion & Milestone Planner
- **Concept & Purpose**: Transform genealogical relationships into actionable family connections by automatically calculating milestones, birth anniversaries, and generating kinship-aware reunion invitation lists.
- **Architectural Fit**:
  - Background notification worker calculates upcoming milestones (Golden Wedding Anniversaries, 80th Birthdays, Deceased Memorials / *Babang Luksa*).
  - "Plan a Reunion" tool allows an owner to select a patriarch/matriarch and automatically generates a complete guest list of all living descendants, grouped by branch and generation.
  - Exports PDF invitation rosters with RSVP tracking.
- **Value Proposition**: Directly strengthens Filipino family solidarity and clan reunions (*angkan* gatherings) using the structured tree data.

---

### Proposal 11: Multi-Tree Anchor-Bridge Merger (Target-Absorptive Model)

#### 11.1 Concept & Genealogical Motivation
- **Primary Function**: Transforms the "Merge Branches" tool from an intra-tree duplicate finder into a true **Inter-Tree Merger** that merges two distinct family trees in the app.
- **The Core Problem**: A user frequently builds two separate family trees—for example, *Tree A: Paternal Dela Cruz Tree* (15 members) and *Tree B: Maternal Santos Tree* (12 members), or one spouse built their side and the other built theirs. The user now wants to merge them into a single, comprehensive family tree without manually re-typing 12 people.
- **The Model**: Tree A is chosen as the **Target (Recipient) Tree**, and Tree B is designated as the **Incoming Tree**. Tree B is absorbed into Tree A, resulting in a single expanded tree with 27 members.

```
[ Tree A: Dela Cruz Tree (15 members) ] ──┐
                                          ├─► [ Unified Tree A (27 members) ]
[ Tree B: Santos Tree (12 members)    ] ──┘   (Linked via Genealogical Anchor Bridge)
```

---

#### 11.2 The Three Genealogical "Anchor Bridge" Mechanisms
In graph theory, two disconnected acyclic directed graphs (DAGs) cannot be unified without establishing at least one connecting edge. Approach 1 introduces **3 Anchor Bridge Types**:

##### A. 💍 Spousal Union Bridge (Horizontal Link)
- **User Story**: *"My father is in Tree A, and my mother is in Tree B. I want to connect the two trees by marrying them."*
- **Mechanism**:
  1. User selects `Person A` from Tree A (e.g., Father) and `Person B` from Tree B (e.g., Mother).
  2. The system sets `Person A.spouseId = Person B.id` and `Person B.spouseId = Person A.id`.
  3. All of Tree B's maternal ancestors (maternal grandparents, great-grandparents, aunts/uncles) are imported into Tree A, perfectly rooted through Mother's horizontal marriage link to Father.
- **Graph Transformation**: Paternal branch and maternal branch align side-by-side on the generation canvas with a connecting marriage ring badge.

##### B. 👶 Parent-Child Bridge (Vertical Link)
- **User Story**: *"My uncle created a separate tree containing his children and grandchildren. I want to attach his branch under his profile in our main family tree."*
- **Mechanism**:
  1. User selects `Person A` in Tree A (Uncle) and `Person B` in Tree B (his eldest Child, or the root of the sub-branch).
  2. User specifies the direction: *"Person A is Parent of Person B"* (or *"Person B is Parent of Person A"*).
  3. The system assigns `Person B.fatherId` (or `motherId`) to `Person A.id`.
  4. Tree B's descendants are attached vertically underneath Person A.

##### C. 👥 Shared Ancestor Bridge (Anchor Node Fusion)
- **User Story**: *"Both my tree and my cousin's tree already contain our common grandfather Don Ramon Dela Cruz. I want to fuse his two records into one so the branches join together."*
- **Mechanism**:
  1. User selects `Person A` in Tree A and `Person B` in Tree B as representing the **exact same individual**.
  2. The system fuses the two records into a single canonical record in Tree A.
  3. All children, parents, and siblings from Tree B that pointed to `Person B` are re-wired to point to canonical `Person A`.
  4. Both lineages converge cleanly at that common ancestor.

---

#### 11.3 Handling Overlapping & Duplicate Members (Field-by-Field Conflict Resolution)
When two trees are merged, several people might exist in both trees (e.g., common children, cousins, or in-laws).
- **Automated Overlap Detection**:
  - The engine runs a bipartite similarity scan comparing all nodes in Tree A against Tree B using Jaro-Winkler full name scoring and birthdate equality.
  - Matches with $>85\%$ confidence are flagged as candidate duplicate pairs.
- **Field-by-Field Conflict Resolution UI**:
  - If attributes differ (e.g., Tree A says birthdate is *May 10, 1965* while Tree B says *May 12, 1965*), the user is presented with an interactive conflict card:

```
┌────────────────────────────────────────────────────────┐
│  ⚠️ Duplicate Candidate: Jose Dela Cruz               │
│  Similarity Score: 92% Match                           │
├────────────────────────────────────────────────────────┤
│  Birth Date:                                           │
│  (●) May 10, 1965  [Tree A: Dela Cruz Tree]            │
│  (○) May 12, 1965  [Tree B: Santos Tree]               │
│                                                        │
│  Birth Place:                                          │
│  (○) Manila        [Tree A: Dela Cruz Tree]            │
│  (●) Quezon City   [Tree B: Santos Tree]               │
│                                                        │
│  Profile Photo:                                        │
│  (●) Keep Tree A Photo     (○) Use Tree B Photo        │
└────────────────────────────────────────────────────────┘
```
- The user taps their preferred value for each conflicting field before the merge executes.

---

#### 11.4 Original Tree B Retention Strategies
What happens to Tree B after its records are absorbed into Tree A? Three distinct strategies are proposed:

1. **Option 1: Archived Snapshot (Recommended for Safety)**:
   - Tree B remains in the database and user account, but its metadata is updated: `isArchived = true` and `mergedIntoTreeId = TreeA.id`.
   - In the tree selector, it appears with a subtle badge: `📁 Santos Tree (Merged into Dela Cruz Tree)`.
   - **Advantage**: 100% fail-safe. The user can still open Tree B as a historical standalone snapshot or manually delete it later when satisfied.
2. **Option 2: Auto-Clean / Complete Deletion**:
   - Once the merge transaction commits, the system deletes Tree B and its member records from Firestore.
   - **Advantage**: Tree switcher remains clean with zero duplicate tree entries.
   - **Disadvantage**: Irreversible; cannot be undone if the user regrets the merge.
3. **Option 3: Automated Pre-Merge JSON/GEDCOM Backup**:
   - Before executing the merge, the app automatically generates and exports an offline JSON/GEDCOM backup snapshot of Tree B to device storage (`Downloads/KinTrace_Backups/TreeB_backup.json`).
   - Ensures user data is never permanently lost.

---

#### 11.5 Detailed 5-Step UI/UX Wizard Walkthrough (`MergeBranchesActivity`)

```
Step 1: Select Trees
├── Primary / Target Tree Dropdown: [ Dela Cruz Family Tree (15 members) ▼ ]
└── Secondary / Incoming Tree Dropdown: [ Santos Family Tree (12 members) ▼ ]
      [ Button: "Scan & Proceed to Bridge Connection ➔" ]

Step 2: Choose Anchor Bridge Mode
├── (●) 💍 Spousal Union Bridge ("Member in Tree A married to Member in Tree B")
├── (○) 👶 Parent-Child Bridge ("Member in Tree A is parent/child of Member in Tree B")
└── (○) 👥 Shared Ancestor Bridge ("Same person exists in both trees")

Step 3: Select Anchor Members
├── Select Person from Dela Cruz Tree: [ Juan Dela Cruz (Father) ▼ ]
└── Select Person from Santos Tree:   [ Maria Santos (Mother) ▼ ]
      [ Visual Link Preview Card showing relationship badge: "Spouse Link" ]

Step 4: Review Overlaps & Conflicts
├── Duplicate Scanner: "2 duplicate children detected across trees"
├── Conflict Resolver: Interactive radio cards to pick winning birthdate/photos
└── Affected Sub-branches: "10 new relatives will be attached to Maria Santos"

Step 5: Safety Audit & Final Merge
├── System Validation Check:
│     ✓ Cycle Check: 0 circular parentage detected
│     ✓ Legal Consanguinity: Philippine Family Code Arts. 37 & 38 satisfied
│     ✓ Permissions: User is verified Owner of both trees
├── Retention Choice: [●] Keep Tree B as Archived Snapshot   [○] Delete Tree B
└── [ Big Action Button: "Confirm & Execute Inter-Tree Merge" ]
      └── Progress indicator ──► CentralTreeSynchronizer updates app in real time
```

---

#### 11.6 Technical Data Flow & Backend Transaction Mechanics
1. **ID Translation & Relational Pointer Mapping**:
   - When Tree B members are ingested into Tree A:
     - Each person in Tree B can retain their original ID or receive a mapped UUID.
     - A translation dictionary `idMap: Map<String, String>` maps every Tree B person ID to their new Tree A person ID.
     - For every incoming person:
       - `treeId` is updated to `treeA.id`.
       - `fatherId = idMap[fatherId] ?: fatherId`
       - `motherId = idMap[motherId] ?: motherId`
       - `spouseId = idMap[spouseId] ?: spouseId`
2. **Anchor Edge Application**:
   - If Spousal Bridge: `personA.spouseId = personB.id` and `personB.spouseId = personA.id`.
   - If Parent-Child Bridge: `personB.fatherId` (or `motherId`) = `personA.id`.
   - If Shared Ancestor Bridge: Canonical record created with merged attributes; duplicate record deleted.
3. **Atomic Firestore Batch Transaction**:
   - Written using Firestore `WriteBatch` (up to 500 operations per batch) ensuring that either all members and relational pointers are committed successfully, or no change is made at all.
4. **Activity Logging & Notification**:
   - Creates an entry in `activity_logs`: `"Tree Merger: Santos Family Tree absorbed into Dela Cruz Family Tree (12 members merged)"`.
   - Emits an in-app notification confirming successful tree union.
5. **Real-Time Reactive Notification**:
   - Calls `CentralTreeSynchronizer.notifyTreeDataChanged(treeA.id)`.
   - Active tree canvas (`InteractiveTreeActivity`), records list (`FamilyRecordsActivity`), and dashboard counters (`HomeActivity`) immediately reload with the new 27-member tree graph.

---

#### 11.7 Legal & Topological Safety Guardrails
1. **Cycle Prevention Engine**:
   - Executes topological cycle detection (`FamilyRelationshipService.detectCycles()`) on the unified graph before the database write is committed.
   - Prevents paradoxes where a person could become their own ancestor.
2. **Philippine Family Code Consanguinity Compliance**:
   - If a Spousal Bridge is selected, `MarriageValidationEngine.validateMarriage()` verifies that the couple does not violate **Executive Order No. 209 (Family Code of the Philippines)**:
     - **Article 37 (Incestuous Marriages)**: No marriages between ascendants and descendants of any degree, or between brothers and sisters (full or half blood).
     - **Article 38 (Against Public Policy)**: No marriages between collateral relatives within the fourth civil degree (first cousins, uncle/niece, aunt/nephew).
   - If an invalid marriage is attempted, the merge is blocked with an explicit legal alert.
3. **Owner Authorization Gate**:
   - Verifies that the current user has `Owner` role on both Tree A and Tree B, ensuring no unauthorized tree takeovers can occur.

---

### Proposal 12: Non-Destructive Clan Synthesis (Master Tree Generator)
- **Concept & Purpose**: Eliminates the danger of data loss or tree corruption. Instead of modifying Tree A or Tree B directly, this approach synthesizes data from both trees into a **brand-new, independent Master Tree C** (e.g., *"Dela Cruz – Santos Master Clan Tree"*).
- **Architectural Fit**:
  - Creates a new document in the `trees` collection (`masterClanTreeId`).
  - Deep-clones all `Person` records from Tree A and Tree B into Tree C with new UUIDs, utilizing an in-memory translation lookup map (`oldId -> newId`) to preserve all existing marital and parent-child edges.
  - Applies the selected Anchor Bridge (Spousal, Parent-Child, or Shared Ancestor) within the new Tree C.
  - **100% Fail-Safe & Reversible**: Both original trees (Tree A and Tree B) remain completely untouched and fully functional. If the user makes an error or is dissatisfied with the merged layout, they can simply delete Tree C with zero consequence to their original trees.
- **Value Proposition**: Provides users with maximum confidence and psychological safety when executing complex family mergers.

---

### Proposal 13: Cross-Account Collaborative Tree Merge via Invite Code & Handshake
- **Concept & Purpose**: Extends tree merging across different user accounts. Allows two different KinTrace users (e.g., distant cousins, or a husband and wife who each built their own family tree on separate phones) to merge their family trees collaboratively.
- **Architectural Fit**:
  - **Step 1: Code Verification**: Initiator enters the 6-character Invite Code of the external tree. `FirestoreHelper.getInviteCodeRecord()` validates the code and fetches the target tree metadata.
  - **Step 2: Merge Proposal Draft**: Initiator selects the Anchor Bridge (e.g., "Person X in my tree is married to Person Y in your tree") and previews potential duplicates.
  - **Step 3: Handshake Protocol**: Rather than merging unilaterally, the app creates a document in `tree_merge_proposals` with status `"PENDING"`.
  - **Step 4: Dual-Owner Authorization**: The owner of the external tree receives an in-app notification (*"Juan Dela Cruz has requested to merge his tree with yours"*). They can open an interactive review modal displaying the bridge proposal and duplicate preview.
  - **Step 5: Mutual Approval & Federation**: Upon approval, the merge executes (either as Target Absorption or Master Tree C), and both users are granted Co-Owner / Editor roles in `tree_members`.
- **Value Proposition**: Enables decentralized clan federation across large extended families without requiring password sharing or manual double-entry.

---

### Proposal 14: Automated Graph Overlap & 3-Way Topological Diff Engine
- **Concept & Purpose**: When merging two established trees containing dozens or hundreds of relatives, manually searching for duplicate entries is impractical. This engine executes automated bipartite matching across both trees and presents an interactive 3-way reconciliation interface.
- **Architectural Fit**:
  - **Bipartite Detection**: Compares all nodes of Tree A against Tree B using multi-attribute similarity (Jaro-Winkler full name scoring, birth/death date matching, and parent/spouse topological context).
  - **3-Way Visual Reconciliation Interface**:
    - **Identical Nodes**: Automatically grouped and merged (marked green).
    - **Conflicting Attributes**: For records with matching identities but divergent data (e.g., Birthdate: "1965-03-12" vs "1965-03-15", or different middle names/birthplaces), the UI renders side-by-side radio chips allowing the user to select which field value wins on a field-by-field basis.
    - **Unique Lineages**: Visualized as incoming branches that will attach to the anchor node.
  - **Mandatory Pre-Commit Legal & Graph Audit**:
    - Feeds the combined proposed graph into `FamilyRelationshipService.auditFamilyTree()`.
    - Enforces cycle prevention (ensures no node becomes their own ancestor).
    - Enforces Philippine Family Code marriage rules (Articles 37 & 38: no direct ascendant/descendant marriages, no sibling marriages, no collateral marriages within the 4th civil degree).
    - If any legal or topological violation is detected, merge execution is strictly blocked with clear error badges explaining the issue.
- **Value Proposition**: Guarantees that large, complex tree mergers result in clean, non-duplicated, legally compliant genealogical data.

---

### Comparative Analysis of Tree-Merging Architectural Paradigms

| Paradigm | Target Tree Impact | Reversibility | Data Duplication | Complexity | Best Suited For |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Approach 1: Target-Absorptive (Tree B $\rightarrow$ Tree A)** | Tree A grows; Tree B absorbed | Low (requires manual rollback or backup restore) | None (single expanded tree) | Moderate | Consolidating personal sub-branches into one definitive tree |
| **Approach 2: Clan Synthesis (Tree A + B $\rightarrow$ Tree C)** | None (Tree A & B untouched) | High (simply delete Tree C if not satisfied) | Moderate (nodes cloned into Tree C) | Moderate | Risk-free experimentation, major multi-family weddings/unions |
| **Approach 3: Cross-Account Handshake** | Dependent on chosen model (1 or 2) | High (requires dual consent before write) | None to Moderate | High | Distant relatives, cousins, or in-laws collaborating across accounts |
| **Approach 4: Automated 3-Way Overlap Diff** | Applied atop Approach 1, 2, or 3 | High (explicit user confirmation per conflict) | N/A | High | Large family trees with multiple overlapping generations |

---

### Proposed 5-Step UI/UX Wizard for `MergeBranchesActivity`

```
[ Step 1: Select Trees ]
   ├── Pick Primary Tree (Tree A: e.g. "Dela Cruz Family Tree")
   └── Pick Second Tree (Tree B: From "My Trees" OR "Enter 6-Char Invite Code")
             │
             ▼
[ Step 2: Choose Anchor Bridge Mode ]
   ├── 💍 Spousal Union Bridge ("Member in Tree A married to Member in Tree B")
   ├── 👶 Parent-Child Bridge ("Member in Tree A is parent/child of Member in Tree B")
   └── 👥 Shared Ancestor Bridge ("Same person exists in both trees")
             │
             ▼
[ Step 3: Select Anchor Members ]
   ├── Tree A Member Picker (Searchable autocomplete dropdown)
   └── Tree B Member Picker (Searchable autocomplete dropdown)
             │
             ▼
[ Step 4: Overlap & Conflict Review ]
   ├── Automated Duplicate Detection Scan (Bipartite Jaro-Winkler)
   ├── Side-by-side Field Conflict Resolver (Choose Name, Birthdate, Photos)
   └── Affected Children / Relatives Preview
             │
             ▼
[ Step 5: Pre-Commit Audit & Execution ]
   ├── Choose Output Mode: ( ) Absorb into Tree A   ( ) Create New Master Tree C
   ├── System Validation: Philippine Family Code & Cycle Check (FamilyRelationshipService)
   └── [ Confirm & Execute Merge ] ──► CentralTreeSynchronizer triggers real-time refresh
```

---

### Architectural Scope & Connected Modules Mapping (KinTrace Standard)

When implementing the multi-tree merge feature, the following core architectural layers will be integrated:
1. **Cloud Firestore Persistence & Repository (`FirestoreHelper.kt`)**:
   - New batch transaction for cross-tree cloning, person ID re-mapping, and anchor edge creation.
   - Support for `tree_merge_proposals` collection for cross-account handshake requests.
2. **Real-Time Tree Synchronizer (`CentralTreeSynchronizer.kt`)**:
   - Emits `TreeSyncEvent.TreeDataChanged` or `TreeSyncEvent.TreeCreated` to notify active screens.
3. **Central Relationship Validation Pipeline (`FamilyRelationshipService.kt`, `MarriageValidationEngine.kt`)**:
   - Pre-commit audit ensures that connecting the two trees creates no graph cycles and violates no Philippine civil consanguinity laws (Articles 37 & 38).
4. **Interactive Family Tree Canvas (`FamilyTreeView.kt`, `InteractiveTreeActivity.kt`)**:
   - Redraws the new unified hierarchical graph with proper generational tiering and spouse bridges.
5. **Dashboard, Records & Insights (`HomeActivity.kt`, `FamilyRecordsActivity.kt`, `InsightsActivity.kt`)**:
   - Member counters, generation depths, and surname distributions update dynamically without requiring manual reload or app restart.

---

### Proposal 15: Safe Master Tree Synthesis (Zero-Risk Clan Fusion Engine)

#### 15.1 Executive Summary & Why It Is Immediately Executable
This proposal synthesizes the best strengths of **Proposal 12 (Non-Destructive Master Tree Generator)** and **Proposal 13 (Invite Code External Tree Connection)** into a **workable, production-ready, zero-risk solution** that can be built and run right now without requiring complex backend migrations.

- **The Core Innovation**: Instead of modifying, overwriting, or deleting either family tree (which risks corrupting existing genealogies), the system leaves Tree A and Tree B **100% untouched and strictly read-only**. It synthesizes both trees into a **brand-new Master Tree C** (e.g., *"Dela Cruz – Santos Master Clan Tree"*).
- **Zero Risk / Effortless Rollback**: Because original trees are never written to, there is literally zero possibility of data loss or corrupted lineages. If the user dislikes the merged outcome or made a mistake, they simply delete Tree C. Both Tree A and Tree B remain completely intact.
- **Immediate Workability**: It builds entirely upon KinTrace's existing and proven architecture:
  - Fetches existing trees via `FirestoreHelper.getUserTrees()`.
  - Fetches external trees via `FirestoreHelper.getInviteCodeRecord()` / `getTreeByInviteCode()`.
  - Validates marriage consanguinity via `MarriageValidationEngine.validateMarriage()`.
  - Prevents graph cycles via `FamilyRelationshipService.auditFamilyTree()`.
  - Broadcasts immediate UI canvas updates via `CentralTreeSynchronizer.notifyTreeDataChanged()`.

---

#### 15.2 Dual Input Sources: Merging Own Trees or via 6-Digit Invite Code
Users can synthesize Master Tree C from two flexible sources:

```
[ Tree 1: Source A ] ───► Pick from "My Family Trees" (e.g. Dela Cruz Tree)
                                 │
                                 ├──► [ Master Tree C: "Dela Cruz – Santos Clan" ]
                                 │    (Original Trees Remain 100% Untouched!)
[ Tree 2: Source B ] ───► Toggle:
                          ├── Option 1: Pick from "My Trees" (e.g. Santos Tree)
                          └── Option 2: Enter 6-Char Invite Code (e.g. "5SRQJU" from a relative)
```

1. **Local Merge**: User owns both Tree A (paternal) and Tree B (maternal) under their own account.
2. **Collaborative / Cross-Account Merge**: User enters the 6-character Invite Code of a relative's tree. The app reads the public/shared tree records of Tree B and merges them with Tree A to produce Tree C in the user's account.

---

#### 15.3 The Three Anchor Link Types
When synthesizing Master Tree C, the user specifies how the two families connect:

1. **💍 Marriage Link (Spousal Union Bridge)**
   - *Example*: Juan Dela Cruz (Tree A) is married to Maria Santos (Tree B).
   - In Tree C, the cloned Juan and cloned Maria are linked as spouses (`spouseId`). Their respective ancestral trees branch out horizontally beside each other.
2. **👶 Parent-Child Link (Ancestral Bridge)**
   - *Example*: Jose Dela Cruz (Tree A) is the father of Pedro Dela Cruz (Tree B, a sub-branch created separately).
   - In Tree C, cloned Jose is assigned as the `fatherId` of cloned Pedro.
3. **👥 Shared Duplicate Ancestor Fusion**
   - *Example*: Both trees contain the common grandfather *Ramon Dela Cruz*.
   - In Tree C, *Ramon* is created as a single canonical person. Relatives from both sides point to this single node.

---

#### 15.4 The 3-Step Instant Execution Wizard (`MergeBranchesActivity`)

A clean, distraction-free 3-step flow designed for speed and clarity:

```
┌────────────────────────────────────────────────────────┐
│ STEP 1: Select Trees to Combine                        │
├────────────────────────────────────────────────────────┤
│ Primary Tree:                                          │
│ [ Dela Cruz Family Tree (15 members)                ▼ ]│
│                                                        │
│ Secondary Tree:                                        │
│ (●) From My Trees      (○) Enter 6-Char Invite Code    │
│ [ Santos Family Tree (12 members)                   ▼ ]│
│                                                        │
│ Master Tree Name:                                      │
│ [ Dela Cruz - Santos Master Clan Tree                 ]│
│                                   [ Next: Link Trees ➔ ]
└────────────────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ STEP 2: Establish the Anchor Connection                │
├────────────────────────────────────────────────────────┤
│ How are these two families connected?                  │
│ [●] 💍 Married Couple (Spouse Link)                    │
│ [ ] 👶 Parent & Child                                  │
│ [ ] 👥 Shared Same Relative (Duplicate Ancestor)       │
│                                                        │
│ Member from Dela Cruz Tree:                            │
│ [ Juan Dela Cruz (Father)                           ▼ ]│
│                                                        │
│ Member from Santos Tree:                               │
│ [ Maria Santos (Mother)                             ▼ ]│
│                                   [ Next: Review ➔ ]   │
└────────────────────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ STEP 3: Zero-Risk Safety Review & Create               │
├────────────────────────────────────────────────────────┤
│ 🛡️ Zero-Risk Guarantee:                                │
│ Dela Cruz Tree and Santos Tree will NOT be modified.   │
│ All records are safely copied into Master Tree C.     │
│                                                        │
│ 📊 Synthesis Summary:                                  │
│ • Dela Cruz Tree: 15 members                           │
│ • Santos Tree: 12 members                              │
│ • Master Tree C: 27 total members                      │
│ • Bridge: Juan Dela Cruz 💍 Maria Santos               │
│ • Validation: ✓ 0 Cycles  ✓ Philippine Law Compliant  │
│                                                        │
│ [  GENERATE MASTER TREE C (ZERO RISK)  ]               │
└────────────────────────────────────────────────────────┘
```

---

#### 15.5 Technical Algorithm & Execution Blueprint

```kotlin
// Step 1: Read source records (STRICTLY READ-ONLY)
val membersA = firestoreHelper.getPersonsByTree(treeAId)
val membersB = firestoreHelper.getPersonsByTree(treeBId)

// Step 2: Build UUID Remapping Dictionary (prevents ID collisions)
val idMap = mutableMapOf<String, String>()
for (person in membersA + membersB) {
    idMap[person.id] = UUID.randomUUID().toString()
}

// Step 3: Create Master Tree C record
val masterTreeId = UUID.randomUUID().toString()
val masterTree = FamilyTree(
    id = masterTreeId,
    name = masterTreeName,
    ownerId = currentUserId,
    ownerName = currentUserName,
    memberCount = membersA.size + membersB.size,
    createdAt = System.currentTimeMillis()
)

// Step 4: Deep-clone members into Tree C with re-mapped relational pointers
val clonedMembersC = mutableListOf<Person>()

fun clonePerson(p: Person): Person {
    return p.copy(
        id = idMap[p.id]!!,
        treeId = masterTreeId,
        fatherId = idMap[p.fatherId] ?: p.fatherId,
        motherId = idMap[p.motherId] ?: p.motherId,
        spouseId = idMap[p.spouseId] ?: p.spouseId,
        createdBy = currentUserId,
        createdAt = System.currentTimeMillis()
    )
}

for (p in membersA) clonedMembersC.add(clonePerson(p))
for (p in membersB) clonedMembersC.add(clonePerson(p))

// Step 5: Apply the Anchor Bridge on the cloned records in Tree C
val clonedAnchorA = clonedMembersC.first { it.id == idMap[anchorPersonAId] }
val clonedAnchorB = clonedMembersC.first { it.id == idMap[anchorPersonBId] }

when (bridgeMode) {
    BridgeMode.SPOUSE -> {
        // Enforce Philippine Family Code consanguinity validation
        val validation = MarriageValidationEngine.validateMarriage(clonedAnchorA, clonedAnchorB, clonedMembersC)
        if (!validation.isValid) throw IllegalStateException(validation.errorMessage)

        clonedAnchorA.spouseId = clonedAnchorB.id
        clonedAnchorB.spouseId = clonedAnchorA.id
    }
    BridgeMode.PARENT_CHILD -> {
        clonedAnchorB.fatherId = clonedAnchorA.id // or motherId based on gender
    }
    BridgeMode.SHARED_ANCESTOR -> {
        // Fuse records: retain Anchor A's record, re-point Anchor B's children to Anchor A, remove duplicate Anchor B
        clonedMembersC.filter { it.fatherId == clonedAnchorB.id }.forEach { it.fatherId = clonedAnchorA.id }
        clonedMembersC.filter { it.motherId == clonedAnchorB.id }.forEach { it.motherId = clonedAnchorA.id }
        clonedMembersC.remove(clonedAnchorB)
    }
}

// Step 6: Verify topological graph integrity
val cycleDetected = FamilyRelationshipService.detectCycles(clonedMembersC)
if (cycleDetected) throw IllegalStateException("Cycle detected in merged graph")

// Step 7: Atomic Firestore Commit
firestoreHelper.saveMasterTreeAndMembers(masterTree, clonedMembersC, onSuccess = {
    // Notify reactive synchronizer
    CentralTreeSynchronizer.notifyTreeDataChanged(masterTreeId)
    TreePreferences.setActiveTreeId(context, masterTreeId)
    // Open the new master tree on canvas!
})
```

---

#### 15.6 Comparison: Why Proposal 15 is Superior for Immediate Execution

| Dimension | Approach 1 (Absorb into Tree A) | Proposal 15 (Safe Master Tree Synthesis) |
| :--- | :--- | :--- |
| **Risk to Original Data** | ⚠️ Moderate risk: Tree A and B are mutated | 🛡️ **Zero Risk**: Tree A and B are strictly read-only |
| **Reversibility** | Difficult; requires complex rollback logic | **Effortless**: User simply deletes Tree C |
| **Invite Code Compatibility** | Difficult: Cannot easily mutate someone else's tree | **Seamless**: Reads external tree data and clones it locally |
| **User Psychological Safety** | High anxiety about breaking existing records | **Complete Confidence**: Original trees are 100% guaranteed safe |
| **Implementation Complexity** | High (must handle mutations, soft-deletes, rollbacks) | **Clean & Modular**: Read $\rightarrow$ Clone $\rightarrow$ Write new tree |

---

### Proposal 16: Dedicated "Merged Clan Space" & Separate Sandbox Viewer (Preserving Original Family Tree)

#### 16.1 Concept & User Motivation: Preserving the Pure Original Family Tree
- **The Core User Insight**: A user's personal family tree is sacred. When a user explores connecting their paternal tree with their maternal tree, or linking their tree with their spouse’s or cousin’s tree, they **do not want their main personal tree polluted, distorted, or crowded out** by dozens of in-laws and collateral branches.
- **The Pitfall of Traditional Genealogy Merges**: In conventional applications, merging forces all incoming members into the primary tree view. As a result, the primary tree canvas becomes overwhelming, difficult to navigate, and the user's direct line of ancestors gets buried among new relatives.
- **The KinTrace Solution (Separate Space Architecture)**:
  - **"View Tree" on Home Dashboard**: Always opens the user's pure, direct **Personal Family Tree** in `InteractiveTreeActivity`. It remains 100% untouched, clean, and unpolluted.
  - **"Merge Branches" Action Card on Home Dashboard**: Acts as the dedicated entrance to an **isolated "Merged Clan Space"**.
  - Merged clan trees (synthesized Master Trees) exist and are explored **exclusively inside this separate space**, completely quarantined from the user's primary tree.

---

#### 16.2 Navigation & Architectural Flow

```
                                  [ Home Dashboard ]
                                    │             │
              ┌─────────────────────┘             └─────────────────────┐
              ▼                                                         ▼
     [ "View Tree" Button ]                               [ "Merge Branches" Action Card ]
              │                                                         │
              ▼                                                         ▼
 [ Pure Personal Family Tree ]                            [ Dedicated "Merged Clan Space" ]
 • Screen: InteractiveTreeActivity                        • Screen: MergeBranchesActivity (Clan Hub)
 • Displays only user's primary bloodline                 • Tab 1: "My Merged Clan Trees"
 • 100% Preserved & Pristine                                └── View synthesized clan trees
 • Zero in-law or cross-family clutter                      └── [ 👁️ Open Merged Clan Canvas ]
 • Active tree ID stays personal                          • Tab 2: "Synthesize New Clan Tree"
                                                            └── 3-Step Zero-Risk Fusion Wizard
```

---

#### 16.3 The "Merged Clan Space" Screen Experience
When the user taps the **Merge Branches** action card from the Home dashboard:

1. **Header & Context**:
   - Title: *"Merged Clan Space"*
   - Subtitle: *"Multi-Family Clan Unions & Cross-Tree Synergies"*
   - Info Pill: *"🛡️ Separate Sandbox: Viewing or creating clan trees here never alters your personal family tree."*

2. **State A: Empty State (No Merged Trees Yet)**:
   - Illustrative welcome card: *"Connect two family trees (e.g. Paternal & Maternal, or a Spouse's tree) into a unified clan view without touching your original family trees."*
   - Action Button: `[ + Synthesize New Clan Tree ]` $\rightarrow$ Launches the 3-step synthesis wizard (Proposal 15).

3. **State B: Active Merged Clan Trees List**:
   - Displays cards for all synthesized clan trees in the user's account:
     ```
     ┌────────────────────────────────────────────────────────────┐
     │  🏛️ Dela Cruz & Santos Master Clan Tree                    │
     │  27 Total Members • 4 Generations                          │
     │  Bridge: Juan Dela Cruz 💍 Maria Santos (Spouse Union)     │
     │  Created: Sep 2026 • Status: Active Clan Sandbox           │
     ├────────────────────────────────────────────────────────────┤
     │  [ 👁️ View Clan Canvas ]       [ 🗑️ Delete Clan Tree ]     │
     └────────────────────────────────────────────────────────────┘
     ```
   - **`[ 👁️ View Clan Canvas ]`**: Launches the dedicated **Merged Clan Sandbox Viewer**.
   - **`[ 🗑️ Delete Clan Tree ]`**: Effortlessly deletes the synthesized Master Tree C. Both original trees remain 100% intact.
   - **Floating Action Button**: `[ + Merge Another Tree ]`.

---

#### 16.4 Dedicated "Merged Clan Sandbox Viewer"
When the user opens a clan tree from the Merged Clan Space:
- **Visual Family Lineage Differentiation**:
  - **Family A Nodes**: Rendered with subtle Slate Blue borders and badges (*"Dela Cruz Line"*).
  - **Family B Nodes**: Rendered with subtle Emerald Green borders and badges (*"Santos Line"*).
  - **Anchor Bridge Node**: Highlighted with an illuminated Golden Ring badge (indicating the marital or ancestral union point).
- **Session Independence**:
  - Navigating and interacting with the Merged Clan Canvas does **not overwrite the user's active personal tree preference** (`TreePreferences.getActiveTreeId()`).
  - Pressing "Back" returns the user cleanly to the Merged Clan Space, and tapping Home returns to their personal tree without any session contamination.

---

#### 16.5 Data Segregation Standard
- In `FamilyTree` model and Firestore documents:
  - Add attribute: `treeType: String = "PERSONAL"` (default for standard family trees) or `"MERGED_CLAN"` (for synthesized trees).
  - `HomeActivity` and `FamilyRecordsActivity`: Query `treeType == "PERSONAL"`, ensuring merged trees never appear in the primary personal tree dropdowns.
  - `MergeBranchesActivity`: Queries `treeType == "MERGED_CLAN"` for its list, maintaining complete architectural separation.

---

### Proposal 17: Multi-Generational Benchmark Tree Seeder (20-Member Clan Suite)

#### 17.1 Concept & Genealogical Motivation
- **Primary Function**: Provides an automated, topologically valid mechanism to generate a comprehensive 20-member, 4-generation family tree benchmark suite directly into a designated tree.
- **The Core Problem Solved**: Validating high-level architectural features—specifically the **Merged Clan Space (Proposal 16)**, **Master Tree Synthesis (Proposal 15)**, Ahnentafel pedigree traversals, Fan Charts, and Dashboard Insights—requires a rich, interconnected multi-generational tree. Manually creating 20 members through the mobile UI requires 15–20 minutes of repetitive typing and dropdown selections.
- **Genealogical Benchmark Standard**: The dataset is deliberately structured to include all major genealogical relationships:
  - Direct ancestral lineage (Great-Grandparents $\rightarrow$ Grandparents $\rightarrow$ Parents $\rightarrow$ Children)
  - Horizontal spousal unions with reciprocal partner links
  - Collateral branches (Aunts, Uncles, Nieces, Nephews, First Cousins)
  - In-laws and marriage ties across family lines

---

#### 17.2 The 20-Member 4-Generation Benchmark Blueprint

```mermaid
graph TD
    subgraph Gen 1: Patriarch & Matriarch (Great-Grandparents)
        G1["1. Mateo Guaniso (b. 1945)"] ---|Married| G2["2. Elena Ramos (b. 1948)"]
    end

    subgraph Gen 2: Parents, Aunts/Uncles & In-Laws
        G1 & G2 --> P1["3. Roberto Guaniso (b. 1970)"]
        G1 & G2 --> P2["4. Maria Guaniso (b. 1973)"]
        G1 & G2 --> P3["5. Antonio Guaniso (b. 1978)"]
        P1 ---|Married| P4["6. Carmen Santos (b. 1972)"]
        P2 ---|Married| P5["7. Danilo Cruz (b. 1971)"]
    end

    subgraph Gen 3: Target Generation, Siblings, Cousins & Spouses
        P1 & P4 --> C1["8. Gabriel Guaniso (b. 1995)"]
        P1 & P4 --> C2["9. Sophia Guaniso (b. 1998)"]
        P1 & P4 --> C3["10. Lucas Guaniso (b. 2002)"]
        P2 & P5 --> C4["11. Andrea Cruz (b. 1999)"]
        P2 & P5 --> C5["12. Marco Cruz (b. 2004)"]
        P3 --> C6["13. Juan Guaniso (b. 2008)"]
        C1 ---|Married| C7["14. Isabella Reyes (b. 1996)"]
        C2 ---|Married| C8["15. Miguel Torres (b. 1997)"]
    end

    subgraph Gen 4: Descendants (Great-Grandchildren of Gen 1)
        C1 & C7 --> GG1["16. Liam Guaniso (b. 2020)"]
        C1 & C7 --> GG2["17. Maya Guaniso (b. 2022)"]
        C2 & C8 --> GG3["18. Noah Torres (b. 2021)"]
        C2 & C8 --> GG4["19. Emma Torres (b. 2023)"]
        C2 & C8 --> GG5["20. Leo Torres (b. 2025)"]
    end
```

---

#### 17.3 Architectural Compliance Pipeline & Technical Workflow
The seeder enforces the KinTrace Global Integration Standard at every layer:

1. **Topological Generation Sequencing**:
   - In graph theory, child nodes cannot reference nonexistent parent keys.
   - Generation executes strictly top-down: Generation 1 roots $\rightarrow$ Generation 2 $\rightarrow$ Generation 3 $\rightarrow$ Generation 4.
2. **Central Relationship Validation (`FamilyRelationshipService.kt`)**:
   - Each proposed relationship is validated through `FamilyRelationshipService` before persistence.
   - Enforces Philippine Family Code (Arts. 37 & 38) incest prohibitions.
   - Prevents cyclical ancestry graphs (nobody can be their own ancestor).
   - Enforces 2-biological-parent maximum cap and bi-directional spousal links (`spouseId` reciprocity).
3. **Central Cloud Firestore Persistence (`FirestoreHelper.kt`)**:
   - Records are committed to `trees/{treeId}/members/{memberId}` with complete metadata:
     - Full names, gender (`MALE` / `FEMALE`), birth dates, vital status (`isLiving = true/false`, death dates where applicable).
     - Relational pointers (`fatherId`, `motherId`, `spouseId`).
4. **Reactive Synchronization (`CentralTreeSynchronizer.kt`)**:
   - Emits `TreeEvent.TreeGraphMutated` upon batch completion.
   - Automatically updates all connected views without restarting the app:
     - **Interactive Tree Canvas**: Renders the complete 4-generation canvas with connection vectors and status badges.
     - **Pedigree & Fan Chart Views**: Populates full Ahnentafel generational tiers.
     - **Family Records Screen**: Lists all 20 searchable and filterable profiles.
     - **Dashboard & Insights**: Instantly updates member counts, generation depths, and demographic ratios.

---

#### 17.4 Permission Gate & Execution Safety
- **Strict Authorization Rule**: The benchmark seeder is strictly gated; it never creates, modifies, or deletes database records without explicit user confirmation and architectural scope approval.
- **Target Selection**: Requires explicit designation of the destination tree (e.g. active `"Guaniso Family"` tree or a newly created test tree).
- **Zero-Pollution Guarantee**: Can be safely tested in isolation or paired with the **Merged Clan Space** to verify inter-clan synthesis against an incoming second tree.

---

## 3. Proposal Ideas Changelog & Expansion Log

| Date | Added By | Summary of Ideas Logged |
| :--- | :--- | :--- |
| **2026-09-25** | System Architecture Review | Initialized `Proposal IDEAS.md` with Proposals 01 through 10 (GEDCOM, ML Kit OCR, DNA cM Correlation, GIS Migration, Offline-First Room SQLite, Encrypted Vault, Regional Dialects, 3-Way Merge, Oral History, Reunion Planner). |
| **2026-09-26** | System Architecture Review | Added Proposals 11 through 14: Comprehensive Multi-Tree Merging Architecture (Anchor-Bridge Merger, Non-Destructive Clan Synthesis, Cross-Account Handshake, Automated 3-Way Graph Diff Engine, Architectural Comparison Matrix, 5-Step UI/UX Wizard, & Module Impact Mapping). |
| **2026-09-26** | System Architecture Review | Added Proposal 15: Safe Master Tree Synthesis (Zero-Risk Clan Fusion Engine) — combines non-destructive Master Tree C generation with Invite Code support; 100% read-only on source trees, 3-step wizard, and instant execution blueprint. |
| **2026-09-26** | System Architecture Review | Added Proposal 16: Dedicated "Merged Clan Space" & Separate Sandbox Viewer — isolates merged trees exclusively inside the "Merge Branches" portal; guarantees the user's primary "View Tree" remains 100% pure, unpolluted, and preserved. |
| **2026-09-26** | System Architecture Review | Added Proposal 17: Multi-Generational Benchmark Tree Seeder (20-Member Clan Suite) — topologically sequenced 4-generation family tree generator passing through FamilyRelationshipService & CentralTreeSynchronizer for testing tree mergers and canvas rendering. |

*(New proposals and suggestions requested in future interactions will be appended here automatically.)*

