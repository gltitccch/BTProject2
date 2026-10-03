package com.example.btproject2.education

/**
 * EducationalContentRepository: The centralized, single source of truth for all
 * educational content, pro tips, legal guardrails, and genealogical reference materials
 * across KinTrace (Mobile-Based Genealogical Bloodline Tracing System).
 *
 * Designed under Proposal 25 (The Unified KinTrace 360° Educational Suite):
 * - Pure Kotlin in-memory repository (0% Firestore write risk, 0% latency).
 * - Serves both the universal in-situ QuickAssist Bottom Sheet and the dedicated KinAcademy screen.
 * - Comprehensive 360° coverage: Covers all 10 app modules from bloodline tracing to export and privacy.
 */
object EducationalContentRepository {

    enum class QuickAssistContext {
        HOME_DASHBOARD,
        CANVAS,
        PEDIGREE_FAN_CHART,
        TRACE,
        RELATIONSHIP_REPORT,
        ADD_MEMBER,
        EDIT_MEMBER,
        RECORDS,
        MERGE_BRANCHES,
        PRIVACY_CONTROLS,
        TREE_AUDIT,
        EXPORT_TREE
    }

    data class QuickAssistPayload(
        val context: QuickAssistContext,
        val title: String,
        val subtitle: String,
        val proTips: List<String>,
        val legalGuardrails: List<String>?,
        val targetAcademyModuleId: Int,
        val actionCtaText: String
    )

    data class KinAcademyModule(
        val id: Int,
        val title: String,
        val category: String,
        val tagline: String,
        val iconEmoji: String,
        val summary: String,
        val detailedSections: List<ModuleSection>,
        val deepLinkTarget: String? = null,
        val deepLinkActionText: String? = null
    )

    data class ModuleSection(
        val heading: String,
        val paragraphs: List<String>,
        val bulletPoints: List<String> = emptyList()
    )

    data class KamagAnakTerm(
        val tagalogTerm: String,
        val englishTranslation: String,
        val civilDegree: String,
        val legalClassification: String,
        val definition: String,
        val practicalExample: String
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 1. CONTEXTUAL QUICKASSIST PAYLOADS (For In-Situ Bottom Sheet)
    // ─────────────────────────────────────────────────────────────────────────

    fun getQuickAssistPayload(context: QuickAssistContext): QuickAssistPayload {
        return when (context) {
            QuickAssistContext.HOME_DASHBOARD -> QuickAssistPayload(
                context = context,
                title = "Dashboard & Clan Quest",
                subtitle = "Mastering the KinTrace Hub & Onboarding Milestones",
                proTips = listOf(
                    "Phase 1 vs Phase 2: Fresh accounts focus strictly on planting a tree or joining via code. Once active, Phase 2 unlocks full clan exploration tools.",
                    "Clan Builder Quest HUD: Complete all 4 onboarding milestones (Plant Tree → Add 2 Members → Explore Canvas → Share Code) to activate your clan.",
                    "Multi-Tree Switching: Maintain distinct paternal and maternal family trees. Tap your tree title anytime to switch clans."
                ),
                legalGuardrails = listOf(
                    "All account onboarding states are strictly isolated per user and will never cross-contaminate between different accounts on the same device."
                ),
                targetAcademyModuleId = 1,
                actionCtaText = "Open Getting Started Masterclass →"
            )

            QuickAssistContext.CANVAS -> QuickAssistPayload(
                context = context,
                title = "Canvas & Pedigree Mastery",
                subtitle = "Touch Gestures, Node Badges & Ahnentafel Numbering",
                proTips = listOf(
                    "Pinch to Zoom: Smoothly scale between bird's-eye 5-generation overview and detailed individual member cards.",
                    "Two-Finger Pan: Drag horizontally or vertically to traverse expansive ancestral branches.",
                    "Double-Tap to Recenter: Instantly snaps the camera back to focus on your tree root ancestor.",
                    "Long-Press Context Menu: Long-press any member card to directly add a spouse, child, or parent without opening full forms."
                ),
                legalGuardrails = listOf(
                    "Node Badge Colors: Primary Blue = Direct Lineage, Emerald Green = Spouses, Amber = Collateral Relatives, Purple = In-Laws, Teal = Bridge Anchors."
                ),
                targetAcademyModuleId = 2,
                actionCtaText = "Open Canvas & Pedigree Masterclass →"
            )

            QuickAssistContext.PEDIGREE_FAN_CHART -> QuickAssistPayload(
                context = context,
                title = "Ahnentafel & Fan Chart Guide",
                subtitle = "Decoding Direct Ancestor Indices & Concentric Generation Rings",
                proTips = listOf(
                    "Ahnentafel Math: Direct ancestors are indexed mathematically: Self = 1, Father = 2, Mother = 3. A person's father is 2n and mother is 2n+1.",
                    "Concentric Fan Rings: Ring layers display generations 1 through 5, highlighting missing ancestral branches at a single glance.",
                    "Tap to Center: Tap any ancestor in the fan chart to view their full generational lineage."
                ),
                legalGuardrails = null,
                targetAcademyModuleId = 2,
                actionCtaText = "Explore Ahnentafel Principles →"
            )

            QuickAssistContext.TRACE -> QuickAssistPayload(
                context = context,
                title = "Bloodline Tracing Engine",
                subtitle = "MRCA Calculation, Consanguinity Degrees & Bloodline Paths",
                proTips = listOf(
                    "Pick Any Two Members: Select Person A and Person B from the dropdowns to trace their biological and legal relationship.",
                    "MRCA (Common Ancestor): KinTrace automatically calculates the Most Recent Common Ancestor connecting both individuals.",
                    "Lineal vs Collateral: Lineal = direct ascendants/descendants (parent/child). Collateral = shared forebears (siblings, cousins, aunts/uncles).",
                    "Official Report & Path: Tap 'View Full Report' to generate a shareable PDF certificate or view the animated visual connection path."
                ),
                legalGuardrails = listOf(
                    "Civil Degrees: Calculated strictly under Philippine Civil Code Articles 963–966. 1st Cousin = 4th Degree Collateral; 2nd Cousin = 6th Degree Collateral."
                ),
                targetAcademyModuleId = 3,
                actionCtaText = "Open Bloodline Tracing Masterclass →"
            )

            QuickAssistContext.RELATIONSHIP_REPORT -> QuickAssistPayload(
                context = context,
                title = "Relationship Reports & Path",
                subtitle = "Official Bloodline Certification & Visual Genealogy Trails",
                proTips = listOf(
                    "Visual Trail: The visual path illustrates each generational step from Person A up to the common ancestor and down to Person B.",
                    "Export to PDF: Generate a formal, high-resolution bloodline certificate with timestamps and tree metadata.",
                    "Copy Summary Text: Share a formatted text explanation directly to family group chats via WhatsApp, Viber, or Messenger."
                ),
                legalGuardrails = listOf(
                    "All generated relationship certificates comply with Philippine Civil Code consanguinity definitions and Family Code marriage criteria."
                ),
                targetAcademyModuleId = 3,
                actionCtaText = "Read Relationship Report Guide →"
            )

            QuickAssistContext.ADD_MEMBER, QuickAssistContext.EDIT_MEMBER -> QuickAssistPayload(
                context = context,
                title = "Member Data Quality & Rules",
                subtitle = "Philippine Naming Customs, Dates & Legal Guardrails",
                proTips = listOf(
                    "Philippine Surnames: Use the mother's maiden name for middle names where applicable, and enter maiden names for married females.",
                    "Date Conventions: Standard format is YYYY-MM-DD. For historical records, approximate birth years are supported.",
                    "Biological vs Affinal: Specify whether parentage is biological or legal/adoptive to preserve accurate bloodline calculations."
                ),
                legalGuardrails = listOf(
                    "Age Gap Guardrail: The system enforces a minimum 12-year biological age gap between parent and child.",
                    "RA 11596 Compliance: Under the Prohibition of Child Marriage Act, marriages involving anyone under 18 years of age are strictly prohibited."
                ),
                targetAcademyModuleId = 4,
                actionCtaText = "Open Member Records Masterclass →"
            )

            QuickAssistContext.RECORDS -> QuickAssistPayload(
                context = context,
                title = "Family Records Directory",
                subtitle = "Search, Multi-Criteria Filtering & Biographical Heritage",
                proTips = listOf(
                    "Instant Search: Search by first name, surname, or maiden name across the entire clan database.",
                    "Filter by Generation & Branch: Narrow down records to specific ancestral generations or family branches.",
                    "Sort Options: Sort members alphabetically, by birth year, or by recently updated profiles.",
                    "Member Profile: Tap any card to view their complete biographical timeline, vital stats, and direct relatives."
                ),
                legalGuardrails = null,
                targetAcademyModuleId = 4,
                actionCtaText = "Explore Records Management →"
            )

            QuickAssistContext.MERGE_BRANCHES -> QuickAssistPayload(
                context = context,
                title = "Branch Merging Wizard",
                subtitle = "The 3 Bridge Types & Zero-Risk Master Tree C Synthesis",
                proTips = listOf(
                    "3-Step Merge Wizard: Select target tree → choose anchor members from both clans → pick bridge type and synthesize.",
                    "Spouse Bridge: Unifies two separate clans connected by a marriage between their respective members.",
                    "Parent-Child Bridge: Connects an offshoot branch to a discovered ancestor in the target tree.",
                    "Shared Ancestor Bridge: Unifies two branches that trace back to the same historical forebear."
                ),
                legalGuardrails = listOf(
                    "Zero-Risk Master Tree C Guarantee: Your original source trees (Tree A and Tree B) remain 100% read-only and preserved. A new combined Master Tree C is generated exclusively inside the Merged Clan Space."
                ),
                targetAcademyModuleId = 5,
                actionCtaText = "Open Branch Merging Masterclass →"
            )

            QuickAssistContext.PRIVACY_CONTROLS -> QuickAssistPayload(
                context = context,
                title = "Privacy & RA 10173 Compliance",
                subtitle = "Protecting Living Relatives & Managing Clan Access",
                proTips = listOf(
                    "RA 10173 (Data Privacy Act of 2012): Living relatives' personal data is legally protected, whereas historical records of deceased ancestors are accessible for genealogical research.",
                    "Mask Living Birthdates: Automatically hides exact birth dates for living members, showing only birth years or 'Living' badges to prevent identity theft.",
                    "Hide Notes & Causes of Death: Restrict sensitive personal research notes, court decrees, and medical records from external viewers.",
                    "Collaborator Roles: Owner has full administrative control; Editor can modify members; Viewer has read-only access."
                ),
                legalGuardrails = listOf(
                    "All privacy settings update reactively in real time and are enforced across export files, records lists, and canvas cards."
                ),
                targetAcademyModuleId = 6,
                actionCtaText = "Open Data Privacy Masterclass →"
            )

            QuickAssistContext.TREE_AUDIT -> QuickAssistPayload(
                context = context,
                title = "Tree Legal & Health Audit",
                subtitle = "Detecting Legal Violations & Structural Graph Anomalies",
                proTips = listOf(
                    "Real-Time Clan Scan: Audits your entire family tree against Philippine legal codes and topological graph rules.",
                    "Severity Badges: Red = Critical Legal Error (must be resolved); Amber = Data Warning (review recommended).",
                    "One-Tap Correction: Tap any audit issue in the list to jump directly to the affected member and resolve the issue.",
                    "Cycle Loop Detection: Prevents impossible ancestry loops where a person becomes their own ancestor."
                ),
                legalGuardrails = listOf(
                    "Article 37 Family Code: Prohibits marriages between ascendants and descendants, and full or half siblings (incestuous).",
                    "Article 38 Family Code: Prohibits marriages between collateral blood relatives up to the 4th civil degree (first cousins / pinsan buo).",
                    "RA 11596: Mandates that all marriage partners must be at least 18 years old."
                ),
                targetAcademyModuleId = 7,
                actionCtaText = "Open Legal Audit Masterclass →"
            )

            QuickAssistContext.EXPORT_TREE -> QuickAssistPayload(
                context = context,
                title = "Tree Export & Archival Heritage",
                subtitle = "High-Res PDF, HD PNG, CSV Roster & Privacy Masking",
                proTips = listOf(
                    "High-Resolution PDF: Formatted as a multi-page vector document ready for large-format physical printing and family reunions.",
                    "HD Image (PNG): Generates a high-definition visual tree chart for digital presentations, slides, and social sharing.",
                    "CSV Spreadsheet: Exports a complete tabular roster of members, birthdates, and relationship metadata for spreadsheet analysis.",
                    "Generation Depth: Limit exports to 3 or 4 generations to fit comfortably on standard poster paper."
                ),
                legalGuardrails = listOf(
                    "Living Member Privacy Mask: When enabled, exported files automatically redact birthdates and sensitive details of living relatives in accordance with RA 10173."
                ),
                targetAcademyModuleId = 8,
                actionCtaText = "Open Tree Export Guide →"
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. KINACADEMY 10 COMPREHENSIVE CURRICULUM MODULES
    // ─────────────────────────────────────────────────────────────────────────

    fun getAllModules(): List<KinAcademyModule> {
        return listOf(
            KinAcademyModule(
                id = 1,
                title = "Getting Started & Clan Initiation",
                category = "Foundations",
                tagline = "Planting your tree, joining via invite code, and the 2 phases of KinTrace",
                iconEmoji = "🌱",
                summary = "Master the fundamentals of KinTrace: understand the two-phase onboarding model, create or join family trees, complete Clan Builder Quests, and manage multiple clans.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "The Two Phases of KinTrace",
                        paragraphs = listOf(
                            "KinTrace operates on an intelligent two-phase architecture designed to prevent user confusion:",
                            "Phase 1 (Initiation): When you create a brand-new account, you do not yet have an active family tree. KinTrace presents a focused welcome experience guiding you to either Plant a New Family Tree or Join an Existing Clan using a 6-character Invite Code.",
                            "Phase 2 (Active Clan Hub): The instant your family tree is established or joined, the application automatically transitions into Phase 2, unlocking the entire suite of tools: Interactive Family Canvas, Bloodline Tracing Engine, Member Directory, Pedigree Fan Chart, and Clan Analytics."
                        ),
                        bulletPoints = listOf(
                            "Phase 1: Zero distraction onboarding focused on tree creation.",
                            "Phase 2: Comprehensive clan management hub unlocked permanently."
                        )
                    ),
                    ModuleSection(
                        heading = "The 'Clan Builder Quest' Milestone HUD",
                        paragraphs = listOf(
                            "To guide beginners step-by-step, KinTrace features a persistent Clan Builder Quest HUD docked at the top of your dashboard. It tracks 4 vital milestones (0/4):",
                            "1. Establish Family Roots: Plant your family tree or join an existing clan.",
                            "2. Expand Your Lineage: Add your first 2 family members to establish branches.",
                            "3. Explore Interactive Canvas: Open the visual tree view and explore touch gestures.",
                            "4. Invite Your Clan: Share your 6-character clan invite code with family members."
                        ),
                        bulletPoints = listOf(
                            "Each milestone updates in real time as you interact with the app.",
                            "When all 4 are complete, the progress bar reaches 100% gold and displays a celebration banner.",
                            "You can dismiss the HUD anytime once completed, and re-enable it in your profile settings."
                        )
                    ),
                    ModuleSection(
                        heading = "Multi-Tree Management",
                        paragraphs = listOf(
                            "KinTrace allows you to maintain multiple distinct family trees under a single account without cross-contaminating records. For example, you can create a 'Santos Paternal Lineage' and a 'Reyes Maternal Lineage'.",
                            "To switch active trees, simply tap the tree title on your Home dashboard or select 'Switch Tree' from the navigation drawer."
                        )
                    )
                ),
                deepLinkTarget = "CreateTreeActivity",
                deepLinkActionText = "Plant a New Family Tree"
            ),

            KinAcademyModule(
                id = 2,
                title = "Interactive Canvas & Pedigree Fan Chart",
                category = "Visual Navigation",
                tagline = "Touch gestures, color-coded node badges, Ahnentafel math, and concentric fan charts",
                iconEmoji = "🌳",
                summary = "Explore family branches visually using KinTrace's custom canvas rendering engine. Learn pinch-to-zoom gestures, decode color badges, and understand Ahnentafel ancestor numbering.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Mastering Canvas Gestures",
                        paragraphs = listOf(
                            "The Interactive Family Tree Canvas is built with hardware-accelerated custom rendering, giving you fluid control over massive multi-generational trees:"
                        ),
                        bulletPoints = listOf(
                            "Pinch-to-Zoom: Pinch inward or outward to scale between a bird's-eye 5-generation overview and close-up member cards.",
                            "Two-Finger Pan / Drag: Slide across the screen to traverse wide horizontal generational tiers.",
                            "Double-Tap Recenter: Double-tap anywhere on the canvas to instantly center the camera back to your root progenitor ancestor.",
                            "Long-Press Context Menu: Press and hold any member node to pop up quick action shortcuts (Add Spouse, Add Child, Add Parent, View Full Profile)."
                        )
                    ),
                    ModuleSection(
                        heading = "Decoding Node Badge Colors & Lineages",
                        paragraphs = listOf(
                            "Every node on the canvas is color-coded to communicate relationship types at a glance:"
                        ),
                        bulletPoints = listOf(
                            "Primary Blue: Direct Lineal Bloodline (progenitors, parents, children, direct ascendants/descendants).",
                            "Emerald Green: Affinal Spouses & Legal Marriage Partners.",
                            "Amber / Warm Gold: Collateral Blood Relatives (siblings, uncles, aunts, cousins).",
                            "Purple: In-Law Extensions (spouses of siblings, bilas, balae).",
                            "Teal Accent: Cross-Branch Bridge Anchors (points where two merged trees connect)."
                        )
                    ),
                    ModuleSection(
                        heading = "Ahnentafel Pedigree System & Concentric Fan Chart",
                        paragraphs = listOf(
                            "The Pedigree View and Fan Chart utilize the international Ahnentafel genealogical numbering system:",
                            "Subject = 1 (Self).",
                            "Father = 2 (2n). Mother = 3 (2n + 1).",
                            "Paternal Grandparents = 4 (Father's Father) & 5 (Father's Mother).",
                            "Maternal Grandparents = 6 (Mother's Father) & 7 (Mother's Mother).",
                            "The Concentric Fan Chart displays these ancestors in outward rings representing generations 1 through 5, making it effortless to identify missing historical branches."
                        )
                    )
                ),
                deepLinkTarget = "FamilyTreeActivity",
                deepLinkActionText = "Open Interactive Family Tree"
            ),

            KinAcademyModule(
                id = 3,
                title = "The Bloodline Tracing Engine",
                category = "Kinship Engine",
                tagline = "MRCA calculation, civil degrees (Art. 963-966), and official relationship reports",
                iconEmoji = "🩸",
                summary = "The crown jewel of KinTrace: select any two individuals in your clan to calculate their Most Recent Common Ancestor (MRCA), Roman civil degrees of consanguinity, and generate official relationship certificates.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "How the Bloodline Tracer Works",
                        paragraphs = listOf(
                            "When you navigate to Bloodline Tracing and select Person A and Person B, KinTrace executes a lowest-common-ancestor graph traversal algorithm:",
                            "1. Ascends through Person A's direct parental lines, mapping all ancestral nodes and generational distances.",
                            "2. Ascends through Person B's parental lines to identify the first shared progenitor node—the Most Recent Common Ancestor (MRCA).",
                            "3. Traces the downward path from the MRCA to both individuals, verifying whether the connection is Lineal, Collateral, or Affinal."
                        )
                    ),
                    ModuleSection(
                        heading = "Roman Civil Consanguinity (Articles 963–966)",
                        paragraphs = listOf(
                            "Philippine civil law computes consanguinity using the Roman Civil Degree method:",
                            "Direct Line (Lineal): Each generation equals one degree (Parent = 1°, Grandparent = 2°, Great-Grandparent = 3°).",
                            "Collateral Line: Count up from Person A to the common ancestor, then down to Person B:"
                        ),
                        bulletPoints = listOf(
                            "2nd Degree: Full or Half Siblings (1 step up to Parent + 1 step down to Sibling).",
                            "3rd Degree: Uncle / Aunt to Nephew / Niece (2 steps up to Grandparent + 1 step down).",
                            "4th Degree: First Cousins / Pinsan Buo (2 steps up to Grandparent + 2 steps down).",
                            "5th Degree: First Cousin Once Removed (2 steps up + 3 steps down).",
                            "6th Degree: Second Cousins / Pinsan Makalawa (3 steps up to Great-Grandparent + 3 steps down)."
                        )
                    ),
                    ModuleSection(
                        heading = "Visual Paths & Certified PDF Reports",
                        paragraphs = listOf(
                            "Once a trace is computed, tap 'View Full Report' to access two specialized tools:",
                            "Relationship Path Activity: An interactive visual connection path highlighting each ancestor between Person A and Person B.",
                            "Relationship Report Activity: An official genealogical summary certificate with verified civil degrees, timestamps, and tree metadata, exportable directly to PDF or shareable as text."
                        )
                    )
                ),
                deepLinkTarget = "TraceActivity",
                deepLinkActionText = "Launch Bloodline Tracer"
            ),

            KinAcademyModule(
                id = 4,
                title = "Member Records & Biographical Archival",
                category = "Records & Archival",
                tagline = "Philippine naming customs, date formatting, biological links, and search filters",
                iconEmoji = "📋",
                summary = "Capture rich family records while adhering to Philippine civil registry standards. Learn naming customs, date formats, biological age gap rules, and multi-criteria record search.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Philippine Naming Customs in Genealogy",
                        paragraphs = listOf(
                            "Philippine genealogical records follow distinctive legal and cultural naming patterns:",
                            "Maternal Middle Name: In Philippine civil law, a person's middle name is typically their mother's maiden surname. Entering accurate middle names provides crucial clues when bridging separate branches.",
                            "Maiden Names for Married Females: Always record female relatives by their maiden surname (birth surname) rather than their married surname. This preserves unbroken patriarchal lineage lines in ancestral trees."
                        )
                    ),
                    ModuleSection(
                        heading = "Vital Dates & Biological Guardrails",
                        paragraphs = listOf(
                            "Standard Date Format: Enter dates in YYYY-MM-DD format. If exact dates are unknown, circa dates (e.g. 'c. 1925') or approximate birth years are supported.",
                            "Biological Age Gap Guardrail: KinTrace validates that a biological parent must be at least 12 years older than their biological child. If a date entry violates this rule, the system alerts you to prevent data entry errors.",
                            "Biological vs. Adoptive / Affinal Links: Specify whether a relationship is biological or legal/adoptive. Biological parentage powers the consanguinity engine, while legal parentage preserves family bonds."
                        )
                    ),
                    ModuleSection(
                        heading = "Searching, Filtering & Profile Heritage",
                        paragraphs = listOf(
                            "In the Family Records directory, use the real-time search bar to find members by first name, surname, or maiden name. Use generation chips to filter members by branch.",
                            "Tap any member card to view their Member Detail profile, featuring their complete biographical narrative, life milestones, photos, and direct relatives."
                        )
                    )
                ),
                deepLinkTarget = "FamilyRecordsActivity",
                deepLinkActionText = "Open Family Records Directory"
            ),

            KinAcademyModule(
                id = 5,
                title = "Multi-Tree Merging & Clan Federation",
                category = "Collaboration",
                tagline = "The 3-step merge wizard, bridge types, and zero-risk Master Tree C synthesis",
                iconEmoji = "🔗",
                summary = "Combine separate family trees with distant relatives without fear of data loss. Learn how the 3 bridge types work and how Master Tree C preserves your original records with zero risk.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "The 3-Step Merge Wizard",
                        paragraphs = listOf(
                            "Merging branches in KinTrace is safe and structured through a dedicated 3-step wizard:",
                            "Step 1: Select Active Tree (Tree A) and Incoming Tree (Tree B from your account or an external tree joined via Invite Code).",
                            "Step 2: Choose Anchor Members—the connecting individuals from Tree A and Tree B.",
                            "Step 3: Choose the Bridge Type and synthesize the unified clan view."
                        )
                    ),
                    ModuleSection(
                        heading = "The 3 Bridge Types Explained",
                        paragraphs = listOf(
                            "1. Spouse Bridge: Connects two unrelated family clans united by a marriage between their respective members (e.g. John Santos marries Maria Reyes).",
                            "2. Parent-Child Bridge: Connects an offshoot branch to a discovered historical ancestor in the main tree.",
                            "3. Shared Ancestor Bridge: Unifies two branches that independently trace back to the same forebear, reconciling duplicate descendant records."
                        )
                    ),
                    ModuleSection(
                        heading = "The Zero-Risk Master Tree C Guarantee",
                        paragraphs = listOf(
                            "Traditional genealogy software often corrupts existing trees during merges by directly overwriting files. KinTrace solves this with Non-Destructive Clan Synthesis:",
                            "Tree A and Tree B are 100% read-only throughout the merge process.",
                            "KinTrace deep-clones both trees into a brand-new combined Master Tree C.",
                            "Master Tree C is housed exclusively inside the 'Merged Clan Space', leaving your primary 'View Tree' completely pure and untouched."
                        )
                    )
                ),
                deepLinkTarget = "MergeBranchesActivity",
                deepLinkActionText = "Open Branch Merging Portal"
            ),

            KinAcademyModule(
                id = 6,
                title = "Privacy Controls & RA 10173 Compliance",
                category = "Security & Legal",
                tagline = "Protecting living relatives under the Philippine Data Privacy Act of 2012",
                iconEmoji = "🛡️",
                summary = "Protect sensitive family information in full compliance with Republic Act 10173. Learn how to mask living members' birthdates, hide sensitive notes, and manage collaborator roles.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "RA 10173 (Philippine Data Privacy Act of 2012)",
                        paragraphs = listOf(
                            "Genealogical research balances open ancestral heritage with the fundamental right to privacy of living individuals:",
                            "Deceased Ancestor Records: Historical records (vital dates, marriages, and lineage) of deceased forebears are generally open for family history documentation.",
                            "Living Relatives Protection: Exact birthdates, contact info, and personal notes of living individuals are legally classified as sensitive personal information and require statutory safeguards."
                        )
                    ),
                    ModuleSection(
                        heading = "Granular Privacy Toggles in KinTrace",
                        paragraphs = listOf(
                            "In Privacy Controls, tree owners can configure four reactive privacy safeguards:",
                            "1. Private Tree Switch: When enabled, your tree is completely hidden from public directories and accessible only via direct invite approval.",
                            "2. Mask Living Relatives' Birthdates: Automatically displays 'Living' or only the birth year for living individuals, preventing identity theft and phishing risks.",
                            "3. Hide Sensitive Notes: Restricts personal research notes, court adoption decrees, and private documents from external viewers.",
                            "4. Hide Deceased Status & Cause of Death: Protects sensitive medical history and causes of death."
                        )
                    ),
                    ModuleSection(
                        heading = "Collaborator Roles & Access Permissions",
                        paragraphs = listOf(
                            "Owner: Full administrative authority, privacy management, and tree deletion rights.",
                            "Editor: Authorized to add, edit, and link family members within the tree.",
                            "Viewer: Read-only access for family members to explore without making changes.",
                            "Pending Requests: Tree owners can review, approve, or deny incoming join requests directly from the privacy dashboard."
                        )
                    )
                ),
                deepLinkTarget = "PrivacyControlsActivity",
                deepLinkActionText = "Configure Privacy Controls"
            ),

            KinAcademyModule(
                id = 7,
                title = "Tree Health & Legal Audit Engine",
                category = "Validation & Law",
                tagline = "Family Code Articles 37/38, RA 11596 child marriage law, and cycle loop detection",
                iconEmoji = "⚖️",
                summary = "Ensure your family tree is legally sound and topologically valid. Learn how KinTrace scans for incestuous marriages, public policy prohibitions, child marriage violations, and circular loops.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Running the Automated Tree Health Audit",
                        paragraphs = listOf(
                            "KinTrace features an automated audit engine that scans your entire family tree in real time. It evaluates both topological graph integrity and strict Philippine legal codes:",
                            "Red Badges (Critical Errors): Direct legal violations or graph corruptions that must be corrected.",
                            "Amber Badges (Data Warnings): Incomplete birth dates, missing maiden surnames, or single-parent notes that warrant review."
                        )
                    ),
                    ModuleSection(
                        heading = "Philippine Family Code Marriage Impediments",
                        paragraphs = listOf(
                            "Article 37 (Incestuous Marriages): Marriages between ascendants and descendants of any degree, and between brothers and sisters (whether full or half blood), are void from the beginning.",
                            "Article 38 (Public Policy Prohibitions): Marriages between collateral blood relatives up to the 4th civil degree (first cousins / pinsan buo) are void for reasons of public policy.",
                            "KinTrace's validation engine actively flags any relationship attempt that violates these statutory provisions."
                        )
                    ),
                    ModuleSection(
                        heading = "Republic Act 11596 & Structural Graph Safeguards",
                        paragraphs = listOf(
                            "Republic Act 11596 (Prohibition of Child Marriage Act): KinTrace strictly requires both partners in a marriage relationship to be at least 18 years of age at the time of marriage.",
                            "Cycle / Loop Prevention: The audit engine scans for circular ancestry loops (where an individual is mistakenly saved as their own ancestor or descendant), ensuring clean topological layout."
                        )
                    )
                ),
                deepLinkTarget = "TreeAuditActivity",
                deepLinkActionText = "Run Tree Health Audit"
            ),

            KinAcademyModule(
                id = 8,
                title = "Tree Export & Reunion Heritage",
                category = "Export & Archival",
                tagline = "Generating high-res PDFs, HD PNG charts, CSV rosters, and printing for reunions",
                iconEmoji = "📤",
                summary = "Transform your digital family tree into tangible physical documents. Learn how to export high-resolution vector PDFs, high-definition PNGs, and CSV spreadsheets for family reunions and research archives.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Supported Export Formats",
                        paragraphs = listOf(
                            "KinTrace offers 4 versatile export formats tailored for different use cases:",
                            "1. High-Resolution Vector PDF: Multi-page vector document formatted for large-format physical printing, poster framing, and archival binders.",
                            "2. High-Definition Image (PNG): Beautiful visual tree chart formatted for digital presentations, tablet viewing, and sharing to family group chats.",
                            "3. CSV Data Spreadsheet: Complete tabular roster containing all member vitals, dates, middle names, and relationship metadata for Excel or Google Sheets.",
                            "4. GEDCOM 5.5.1 / 7.0: Standard international genealogical data format for interoperability with global research platforms."
                        )
                    ),
                    ModuleSection(
                        heading = "Export Customization & Sizing Options",
                        paragraphs = listOf(
                            "Generation Depth Filter: You can restrict exports to 3, 4, or full clan generations to optimize layout size for standard poster paper dimensions.",
                            "Living Member Privacy Masking: When activated, all living relatives' exact birthdates are automatically redacted in the exported document, ensuring family privacy during reunions.",
                            "Secure Sharing: Directly export files to your device storage or share seamlessly via Android system share to WhatsApp, Viber, Messenger, or Gmail."
                        )
                    )
                ),
                deepLinkTarget = "ExportTreeActivity",
                deepLinkActionText = "Export Family Tree"
            ),

            KinAcademyModule(
                id = 9,
                title = "Clan Analytics & Dashboard Insights",
                category = "Analytics",
                tagline = "Generational depth metrics, gender distribution, longevity, and activity logs",
                iconEmoji = "📊",
                summary = "Discover deep insights into your family's demographic history. Track generational depth, gender distribution ratios, ancestral longevity, and review the chronological audit trail.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Clan Generational Metrics",
                        paragraphs = listOf(
                            "Generational Depth: Measures the longest unbroken direct ancestral line currently documented in your tree.",
                            "Clan Population Growth: Tracks generation-by-generation expansion, highlighting peak family growth eras.",
                            "Gender Balance: Visualizes the proportion of male and female relatives documented across all branches."
                        )
                    ),
                    ModuleSection(
                        heading = "Demographic Longevity & Activity Audit",
                        paragraphs = listOf(
                            "Ancestral Longevity: Computes average lifespan across documented generations, identifying long-lived ancestral lineages.",
                            "Recent Activities Log: A transparent chronological audit trail recording who created, updated, or merged members, ensuring accountability in collaborative clans.",
                            "In-App Notifications: Centralized alerts for collaborator join requests, merge status updates, and tree audit reminders."
                        )
                    )
                ),
                deepLinkTarget = "InsightsActivity",
                deepLinkActionText = "View Clan Insights"
            ),

            KinAcademyModule(
                id = 10,
                title = "Philippine 'Kamag-anak' Kinship Lexicon",
                category = "Culture & Glossary",
                tagline = "Traditional Filipino kinship terminology mapped to Roman civil degrees and legal standing",
                iconEmoji = "🇵🇭",
                summary = "A comprehensive, searchable genealogical dictionary mapping traditional Philippine kinship terms (pinsan buo, bilas, balae, hipag, bayaw, etc.) to their exact civil degree and legal standing.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Traditional Philippine Kinship Structure",
                        paragraphs = listOf(
                            "Philippine kinship is bilateral and deeply relational, extending beyond biological ties into affinal and spiritual (compadrazgo) dimensions.",
                            "KinTrace bridges this rich cultural vocabulary with formal Roman civil law, giving family historians precise definitions for every traditional term."
                        )
                    )
                ),
                deepLinkTarget = null,
                deepLinkActionText = null
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PHILIPPINE KINSHIP LEXICON ("KAMAG-ANAK DICTIONARY")
    // ─────────────────────────────────────────────────────────────────────────

    fun getKamagAnakDictionary(): List<KamagAnakTerm> {
        return listOf(
            KamagAnakTerm(
                tagalogTerm = "Pinsan Buo",
                englishTranslation = "First Cousin",
                civilDegree = "4th Degree Collateral",
                legalClassification = "Prohibited to marry under Art. 38(1) Family Code",
                definition = "The child of one's aunt or uncle. Shares a set of common grandparents.",
                practicalExample = "Your mother's brother's daughter is your Pinsan Buo (4th civil degree)."
            ),
            KamagAnakTerm(
                tagalogTerm = "Pinsan Makalawa",
                englishTranslation = "Second Cousin",
                civilDegree = "6th Degree Collateral",
                legalClassification = "Legally eligible to marry under Philippine civil law",
                definition = "The grandchild of one's great-aunt or great-uncle. Shares a set of common great-grandparents.",
                practicalExample = "The child of your parent's first cousin is your Pinsan Makalawa (6th civil degree)."
            ),
            KamagAnakTerm(
                tagalogTerm = "Pinsan Makaitlo",
                englishTranslation = "Third Cousin",
                civilDegree = "8th Degree Collateral",
                legalClassification = "Legally eligible to marry under Philippine civil law",
                definition = "The great-grandchild of one's great-great-aunt or great-great-uncle. Shares common great-great-grandparents.",
                practicalExample = "Distant cousins sharing the same great-great-grandparent lineage."
            ),
            KamagAnakTerm(
                tagalogTerm = "Bilas",
                englishTranslation = "Co-Sibling-in-Law",
                civilDegree = "Affinal (Non-Consanguineous)",
                legalClassification = "No biological impediment to marriage",
                definition = "The spouse of one's sibling-in-law; two people whose spouses are siblings.",
                practicalExample = "If two brothers marry two women, the two women are 'Bilas' to each other."
            ),
            KamagAnakTerm(
                tagalogTerm = "Balae",
                englishTranslation = "Co-Parent-in-Law",
                civilDegree = "Affinal (Non-Consanguineous)",
                legalClassification = "No legal impediment",
                definition = "The relationship between the parents of two married individuals.",
                practicalExample = "Your parents and your spouse's parents are 'Balae' to one another."
            ),
            KamagAnakTerm(
                tagalogTerm = "Hipag",
                englishTranslation = "Sister-in-Law",
                civilDegree = "Affinal Collateral",
                legalClassification = "Affinal relationship through marriage",
                definition = "The sister of one's spouse, or the wife of one's brother.",
                practicalExample = "Your wife's sister is your Hipag."
            ),
            KamagAnakTerm(
                tagalogTerm = "Bayaw",
                englishTranslation = "Brother-in-Law",
                civilDegree = "Affinal Collateral",
                legalClassification = "Affinal relationship through marriage",
                definition = "The brother of one's spouse, or the husband of one's sister.",
                practicalExample = "Your husband's brother is your Bayaw."
            ),
            KamagAnakTerm(
                tagalogTerm = "Amain",
                englishTranslation = "Uncle (Paternal or Maternal)",
                civilDegree = "3rd Degree Collateral",
                legalClassification = "Prohibited to marry under Art. 38 Family Code",
                definition = "A brother of one's father or mother.",
                practicalExample = "Your father's brother is your Amain (3rd civil degree)."
            ),
            KamagAnakTerm(
                tagalogTerm = "Ale",
                englishTranslation = "Aunt (Paternal or Maternal)",
                civilDegree = "3rd Degree Collateral",
                legalClassification = "Prohibited to marry under Art. 38 Family Code",
                definition = "A sister of one's father or mother.",
                practicalExample = "Your mother's sister is your Ale (3rd civil degree)."
            ),
            KamagAnakTerm(
                tagalogTerm = "Ninong / Ninang",
                englishTranslation = "Godfather / Godmother",
                civilDegree = "Compadrazgo (Spiritual Kinship)",
                legalClassification = "Spiritual kinship; traditional moral affinity",
                definition = "Sponsors in Roman Catholic baptism, confirmation, or marriage who assume spiritual guardianship.",
                practicalExample = "A trusted family friend or relative chosen to sponsor a child's baptism."
            ),
            KamagAnakTerm(
                tagalogTerm = "Inaanak",
                englishTranslation = "Godchild",
                civilDegree = "Compadrazgo (Spiritual Kinship)",
                legalClassification = "Spiritual kinship",
                definition = "The individual sponsored by a Ninong or Ninang during baptism or confirmation.",
                practicalExample = "The child whom a godparent vows to guide spiritually."
            ),
            KamagAnakTerm(
                tagalogTerm = "Ninuno",
                englishTranslation = "Ancestor / Forebear",
                civilDegree = "Direct Lineal Ascendant",
                legalClassification = "Direct Lineage (Articles 963-964)",
                definition = "A person from whom one is descended; direct genealogical ascendants (grandparents, great-grandparents, etc.).",
                practicalExample = "The founding progenitors who planted the root branches of your family tree."
            ),
            KamagAnakTerm(
                tagalogTerm = "Ka-angkan",
                englishTranslation = "Clan Member / Lineage Kin",
                civilDegree = "General Lineage Kinship",
                legalClassification = "Members belonging to the same genealogical family clan",
                definition = "Individuals sharing the same bloodline, ancestral root, or clan surname.",
                practicalExample = "All descendants tracing back to a common ancestral couple."
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. SEARCH & FILTER UTILITIES
    // ─────────────────────────────────────────────────────────────────────────

    fun searchModules(query: String): List<KinAcademyModule> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return getAllModules()
        return getAllModules().filter { module ->
            module.title.lowercase().contains(trimmed) ||
            module.summary.lowercase().contains(trimmed) ||
            module.category.lowercase().contains(trimmed) ||
            module.detailedSections.any { section ->
                section.heading.lowercase().contains(trimmed) ||
                section.paragraphs.any { it.lowercase().contains(trimmed) } ||
                section.bulletPoints.any { it.lowercase().contains(trimmed) }
            }
        }
    }

    fun searchGlossary(query: String): List<KamagAnakTerm> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return getKamagAnakDictionary()
        return getKamagAnakDictionary().filter { term ->
            term.tagalogTerm.lowercase().contains(trimmed) ||
            term.englishTranslation.lowercase().contains(trimmed) ||
            term.definition.lowercase().contains(trimmed) ||
            term.civilDegree.lowercase().contains(trimmed) ||
            term.legalClassification.lowercase().contains(trimmed)
        }
    }
}
