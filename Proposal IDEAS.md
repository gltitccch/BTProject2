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

## 3. Proposal Ideas Changelog & Expansion Log

| Date | Added By | Summary of Ideas Logged |
| :--- | :--- | :--- |
| **2026-09-25** | System Architecture Review | Initialized `Proposal IDEAS.md` with Proposals 01 through 10 (GEDCOM, ML Kit OCR, DNA cM Correlation, GIS Migration, Offline-First Room SQLite, Encrypted Vault, Regional Dialects, 3-Way Merge, Oral History, Reunion Planner). |

*(New proposals and suggestions requested in future interactions will be appended here automatically.)*

