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
        FAN_CHART,
        TRACE,
        RELATIONSHIP_REPORT,
        ADD_MEMBER,
        EDIT_MEMBER,
        RECORDS,
        MERGE_BRANCHES,
        PRIVACY_CONTROLS,
        TREE_AUDIT,
        EXPORT_TREE,
        INSIGHTS,
        CREATE_TREE,
        JOIN_TREE
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
                title = "Dashboard & Clan Hub",
                subtitle = "Multi-Tree Switching, Role Badges & Real-Time Sync",
                proTips = listOf(
                    "Multi-Tree Switching: Maintain distinct paternal and maternal family trees. Tap your tree title anytime to switch active clans.",
                    "Role-Based Access Badges: Your active permission level (Owner, Editor, or Viewer) is enforced in real time across all tools.",
                    "Clan Builder Quest HUD: Complete all 4 onboarding milestones (Plant Tree → Add 2 Members → Explore Canvas → Share Code) to activate your clan."
                ),
                legalGuardrails = listOf(
                    "All account onboarding states and role permissions are strictly isolated per user and synchronized reactively via Cloud Firestore."
                ),
                targetAcademyModuleId = 1,
                actionCtaText = "Open Getting Started Masterclass →"
            )

            QuickAssistContext.CANVAS -> QuickAssistPayload(
                context = context,
                title = "Interactive Tree & View Modes",
                subtitle = "Tree Canvas, Pedigree View, Fan Chart & Touch Gestures",
                proTips = listOf(
                    "3 Visual Modes: Switch seamlessly between the full Interactive Tree Canvas, Ancestor Pedigree View, and Concentric Fan Chart using the top mode selector.",
                    "Pinch-to-Zoom & Pan: Smoothly scale between a bird's-eye 5-generation overview and individual member cards.",
                    "Tap or Long-Press Nodes: Tap any member card to inspect their profile, or long-press (Owners/Editors) to quickly add a spouse, child, or parent."
                ),
                legalGuardrails = listOf(
                    "Node Badge Colors: Primary Blue = Direct Lineage, Emerald Green = Spouses, Amber = Collateral Relatives, Purple = In-Laws, Teal = Bridge Anchors."
                ),
                targetAcademyModuleId = 2,
                actionCtaText = "Open Canvas & Pedigree Masterclass →"
            )

            QuickAssistContext.PEDIGREE_FAN_CHART -> QuickAssistPayload(
                context = context,
                title = "Family Generations List Guide",
                subtitle = "Generations, Ancestor Cards & Family Roles",
                proTips = listOf(
                    "Generation Rows: Organizes your family from youngest to oldest. Generation 1 is the starting person, Generation 2 is parents, Generation 3 is grandparents.",
                    "Tap to View Story: Tap any person's card to open their full profile, photos, and family records.",
                    "Gold Role Badges: Tags show who started the family line (Founder, Patriarch, Matriarch) at a glance."
                ),
                legalGuardrails = null,
                targetAcademyModuleId = 2,
                actionCtaText = "Start Pedigree Tour →"
            )

            QuickAssistContext.FAN_CHART -> QuickAssistPayload(
                context = context,
                title = "360° Family Wheel Guide",
                subtitle = "Center Person, Generational Rings & Paternal/Maternal Halves",
                proTips = listOf(
                    "Person in the Middle: The gold circle right at the center is the main person. All rings around them show the ancestors who came before them.",
                    "Blue vs. Pink Sides: The left half (blue) shows your father's side, and the right half (pink) shows your mother's side.",
                    "Tap Any Slice: Tap on any colored block on the wheel to see that ancestor's full name, birth year, and birthplace in an instant popup.",
                    "Zoom & Center Buttons: Use the + and - buttons at the bottom to zoom into outer rings, or tap ⟲ to jump back to center."
                ),
                legalGuardrails = null,
                targetAcademyModuleId = 2,
                actionCtaText = "Start Family Wheel Tour →"
            )

            QuickAssistContext.TRACE -> QuickAssistPayload(
                context = context,
                title = "Bloodline Tracing Engine",
                subtitle = "Spotlight Name Search, LCA Math & Affinal Kinship",
                proTips = listOf(
                    "Spotlight Name Search: Tap the Person A or Person B search bar to type a first name, surname, or birth year, or filter by All, Living, Deceased, Male, or Female.",
                    "One-Tap Swap (⇅): Tap the center ⇅ icon between Person A and Person B to instantly reverse the trace direction.",
                    "Lowest / Most Recent Common Ancestor (LCA / MRCA) & In-Laws: Automatically finds the closest shared ancestor (minimizing total generational steps) or traces Affinal / In-Law paths (Spouse, Hipag, Bayaw, Balae, Bilas).",
                    "Official Report & Tree Highlight: Tap 'View Official Relationship Report' for a certified PDF or 'Highlight on Tree Canvas' to visualize the route."
                ),
                legalGuardrails = listOf(
                    "Civil Degrees: Computed strictly under Philippine Civil Code Articles 963–966 and Family Code Articles 37–38 (prohibiting marriages up to the 4th civil degree / first cousins)."
                ),
                targetAcademyModuleId = 3,
                actionCtaText = "Open Bloodline Tracing Masterclass →"
            )

            QuickAssistContext.RELATIONSHIP_REPORT -> QuickAssistPayload(
                context = context,
                title = "Relationship Reports & Path",
                subtitle = "Official Bloodline Certification & Visual Genealogy Trails",
                proTips = listOf(
                    "Interactive Path Ladder: Step-by-step generational breakdown from Person A up to the Most Recent Common Ancestor (MRCA) and down to Person B.",
                    "Export Official PDF: Generate a formal, high-resolution bloodline certificate with verified civil degrees and legal standing.",
                    "Share Summary: Copy or share the formatted legal and genealogical summary directly to family chats."
                ),
                legalGuardrails = listOf(
                    "All generated relationship certificates comply with Philippine Civil Code consanguinity definitions and Family Code marriage criteria."
                ),
                targetAcademyModuleId = 3,
                actionCtaText = "Read Relationship Report Guide →"
            )

            QuickAssistContext.ADD_MEMBER, QuickAssistContext.EDIT_MEMBER -> QuickAssistPayload(
                context = context,
                title = "Member Forms & Smart Links",
                subtitle = "Linked-Spouse Auto-Selection, Marriage Dates & Legal Guardrails",
                proTips = listOf(
                    "Linked-Spouse Co-Parent Auto-Selection: Selecting a Biological Father automatically highlights and pre-selects his linked wife as Biological Mother (with '-- None --' available if the child has a different mother), and vice versa.",
                    "Marriage Date Validation: When linking a spouse, enter the Date of Marriage to verify both partners were at least 18 years old at marriage and alive on that date.",
                    "Cross-Device Portraits & Surnames: Profile photos are compressed for instant cloud sync across all clan devices. Record married females by their maiden surname."
                ),
                legalGuardrails = listOf(
                    "Biological Guardrails: Enforces a minimum 12-year parent-child age gap, realistic maternal (≤60) and paternal (≤80) childbearing windows, and blocks parent-sibling loop conflicts.",
                    "RA 11596 & Family Code Arts. 37–38: Strictly blocks child marriages (<18 at marriage date) and incestuous/4th-degree consanguineous unions."
                ),
                targetAcademyModuleId = 4,
                actionCtaText = "Open Member Records Masterclass →"
            )

            QuickAssistContext.RECORDS -> QuickAssistPayload(
                context = context,
                title = "Family Records Directory",
                subtitle = "Instant Search, Generation Filters & RA 10173 Privacy Masking",
                proTips = listOf(
                    "Instant Search & Sort: Search by first name, surname, or birth place, and sort alphabetically or by birth year.",
                    "Filter by Generation & Status: Narrow down records by generation tier, living/deceased status, or gender.",
                    "RA 10173 Birthdate Masking: When 'Mask Living Birthdates' is enabled in Privacy Controls, living members display 'YYYY ( Protected )' instead of exact dates."
                ),
                legalGuardrails = listOf(
                    "Deleting a member automatically cleans up all reciprocal parent, child, spouse, and sibling references across the cloud tree."
                ),
                targetAcademyModuleId = 4,
                actionCtaText = "Explore Records Management →"
            )

            QuickAssistContext.MERGE_BRANCHES -> QuickAssistPayload(
                context = context,
                title = "Branch Merging Wizard",
                subtitle = "Cross-Tree Cloud Synthesis & Smart Duplicate Deduplication",
                proTips = listOf(
                    "3-Step Merge Wizard: Select target tree → choose anchor members from both clans → pick bridge type (Spouse, Parent-Child, or Shared Ancestor).",
                    "Smart Duplicate Deduplication: Automatically reconciles identical relatives (matching normalized name, birth date, and gender) so merged branches never create duplicate clones.",
                    "Editor & Owner Access: Branch merging requires Owner or Editor permissions on the active tree."
                ),
                legalGuardrails = listOf(
                    "Zero-Risk Master Tree C Guarantee: Source trees remain 100% untouched while unified records are synthesized cleanly into the target merge space."
                ),
                targetAcademyModuleId = 5,
                actionCtaText = "Open Branch Merging Masterclass →"
            )

            QuickAssistContext.PRIVACY_CONTROLS -> QuickAssistPayload(
                context = context,
                title = "Privacy & RA 10173 Compliance",
                subtitle = "Real-Time Data Masking & Collaborator Role Governance",
                proTips = listOf(
                    "Mask Living Birthdates (RA 10173): Automatically redacts exact birth dates for living relatives across Family Records, Member Details, and Tree Exports.",
                    "Hide Sensitive Notes & Causes of Death: Restricts biographical notes and medical/death details from non-owner viewers in real time.",
                    "Collaborator Role Governance: Tree Owners can promote/demote collaborators between Editor and Viewer roles or revoke access at any time."
                ),
                legalGuardrails = listOf(
                    "Compliant with RA 10173 (Philippine Data Privacy Act of 2012): Living relatives' personal data is protected while historical ancestor records remain accessible."
                ),
                targetAcademyModuleId = 6,
                actionCtaText = "Open Data Privacy Masterclass →"
            )

            QuickAssistContext.TREE_AUDIT -> QuickAssistPayload(
                context = context,
                title = "Tree Legal & Health Audit",
                subtitle = "Detecting Legal Impediments, Age Anomalies & Graph Cycles",
                proTips = listOf(
                    "Comprehensive Clan Scan: Audits every member for Family Code violations, RA 11596 marriage age rules, childbearing age bounds, and orphaned links.",
                    "Severity Badges: Red = Critical Legal/Cycle Error; Amber = Chronological or Biographical Warning.",
                    "One-Tap Resolution: Owners and Editors can tap any audit card to jump directly to the affected member profile and resolve the anomaly."
                ),
                legalGuardrails = listOf(
                    "Article 37 & Article 38 Family Code: Flags incestuous marriages (lineal/siblings) and collateral marriages up to the 4th civil degree (first cousins).",
                    "RA 11596: Flags any marriage where either spouse was under 18 years old on the marriage date."
                ),
                targetAcademyModuleId = 7,
                actionCtaText = "Open Legal Audit Masterclass →"
            )

            QuickAssistContext.EXPORT_TREE -> QuickAssistPayload(
                context = context,
                title = "Tree Export & Archival Heritage",
                subtitle = "High-Res PDF, HD PNG, CSV Roster & GEDCOM Interoperability",
                proTips = listOf(
                    "4 Export Formats: Export your clan as a Vector PDF poster, HD PNG chart, CSV spreadsheet, or standard GEDCOM genealogical file.",
                    "Generation Depth Filter: Limit exports to 3, 4, or all generations for optimal printing layout.",
                    "Privacy-Safe Exports: Automatically respects your tree's RA 10173 living-member birthdate masking rules."
                ),
                legalGuardrails = listOf(
                    "Living Member Privacy Mask: When enabled, exported files automatically redact birthdates and sensitive details of living relatives in accordance with RA 10173."
                ),
                targetAcademyModuleId = 8,
                actionCtaText = "Open Tree Export Guide →"
            )

            QuickAssistContext.INSIGHTS -> QuickAssistPayload(
                context = context,
                title = "Clan Analytics & Completeness",
                subtitle = "Profile Completeness Scorecard, Demographics & Longevity",
                proTips = listOf(
                    "Profile Completeness Scorecard (0–100%): Evaluates every member across 6 core fields (First Name, Last Name, Gender, Birth Date, Parents, and Biography).",
                    "Generational & Gender Metrics: Visualizes unbroken generational depth, branch distribution, and gender balance across your clan.",
                    "Longevity & Activity Trail: Computes historical lifespan averages and tracks collaborative edits in real time."
                ),
                legalGuardrails = null,
                targetAcademyModuleId = 9,
                actionCtaText = "Open Clan Analytics Masterclass →"
            )

            QuickAssistContext.CREATE_TREE -> QuickAssistPayload(
                context = context,
                title = "Clan Genesis Guidance",
                subtitle = "Naming, Founder Role & Cloud Tree Setup",
                proTips = listOf(
                    "Clan Naming: Choose an official family surname (e.g., Dela Cruz Family). You can invite family co-owners to collaborate anytime.",
                    "Founder Anchor: Designate whether you are starting the tree as Patriarch, Matriarch, or Member. This anchors Generation 1.",
                    "Strict Privacy Standard: New family trees are private by default under the Philippine Data Privacy Act (RA 10173)."
                ),
                legalGuardrails = listOf(
                    "Only users aged 18+ may establish a Patriarch or Matriarch title. All tree records are secured under Role-Based Access Control."
                ),
                targetAcademyModuleId = 1,
                actionCtaText = "Open Getting Started Masterclass →"
            )

            QuickAssistContext.JOIN_TREE -> QuickAssistPayload(
                context = context,
                title = "Join Clan Family Tree",
                subtitle = "Invite Code, Roles & Collaboration",
                proTips = listOf(
                    "6-Character Invite Code: Ask a family tree Owner or Editor for their tree invite code to connect immediately.",
                    "Role Permissions: When you join, your role determines whether you can add members or explore as a Viewer.",
                    "Multiple Clans: You can belong to multiple family trees and switch between them anytime from the Home dashboard."
                ),
                legalGuardrails = listOf(
                    "Tree membership is subject to verification by clan administrators. You can revoke your participation at any time."
                ),
                targetAcademyModuleId = 1,
                actionCtaText = "Open Getting Started Masterclass →"
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
                tagline = "Touch gestures, 3 view modes, node badges, Ahnentafel math, and concentric fan charts",
                iconEmoji = "🌳",
                summary = "Explore family branches visually using KinTrace's hardware-accelerated rendering engine. Switch between Tree View, Pedigree View, and Fan Chart, use pinch-to-zoom gestures, decode color badges, and understand Ahnentafel ancestor numbering.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Mastering Canvas Gestures & 3 Visual Modes",
                        paragraphs = listOf(
                            "The Interactive Family Tree screen provides three synchronized visual lenses—Tree View, Pedigree View, and Fan Chart—with fluid touch controls:"
                        ),
                        bulletPoints = listOf(
                            "Pinch-to-Zoom: Pinch inward or outward to scale between a bird's-eye 5-generation overview and close-up member cards.",
                            "Two-Finger Pan / Drag: Slide across the screen to traverse wide horizontal generational tiers.",
                            "Double-Tap Recenter: Double-tap anywhere on the canvas to instantly center the camera back to your root progenitor ancestor.",
                            "Long-Press Context Menu: Owners and Editors can press and hold any member node to trigger quick action shortcuts (Add Spouse, Add Child, Add Parent, View Profile)."
                        )
                    ),
                    ModuleSection(
                        heading = "Decoding Node Badge Colors & Lineages",
                        paragraphs = listOf(
                            "Every node on the canvas is color-coded (with adaptive Dark Mode and Light Mode contrast) to communicate relationship types at a glance:"
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
                deepLinkTarget = "InteractiveTreeActivity",
                deepLinkActionText = "Open Interactive Family Tree"
            ),

            KinAcademyModule(
                id = 3,
                title = "The Bloodline Tracing Engine",
                category = "Kinship Engine",
                tagline = "Spotlight name search, LCA math, civil degrees (Art. 963-966), and affinal in-law tracing",
                iconEmoji = "🩸",
                summary = "The crown jewel of KinTrace: search and select any two individuals in your clan to calculate their Lowest Common Ancestor (LCA), Roman civil degrees of consanguinity, affinal in-law connections, and official relationship certificates.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Minimalist Spotlight Search & Lowest Common Ancestor (LCA)",
                        paragraphs = listOf(
                            "In Trace Relationship, tap the Person A or Person B search bar to open the Spotlight Search sheet—allowing you to type a first name, surname, or birth year, or filter by All, Living, Deceased, Male, or Female. Tap the center ⇅ button anytime to swap Person A and Person B.",
                            "1. KinTrace ascends through both individuals' parental lines, evaluating all shared ancestral candidates and selecting the true Lowest Common Ancestor (LCA) that minimizes total generational steps (distanceA + distanceB).",
                            "2. If no biological blood tie exists, the engine automatically checks Affinal / In-Law paths—identifying Spouse, Father/Mother-in-Law, Brother/Sister-in-Law (Hipag / Bayaw), Co-Parent-in-Law (Balae), and Co-Sibling-in-Law (Bilas) relationships."
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
                        heading = "Visual Paths, Tree Highlight & Certified PDF Reports",
                        paragraphs = listOf(
                            "Once a trace is computed, you can explore three interactive outputs:",
                            "Highlight on Tree Canvas: Opens the Interactive Family Tree with the exact bloodline path and MRCA highlighted.",
                            "Interactive Path Ladder: Step-by-step generational breakdown between Person A and Person B.",
                            "Official Relationship Report: A formal genealogical certificate with verified civil degrees and Family Code legal standing, exportable to PDF."
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
                tagline = "Linked-spouse co-parent auto-selection, marriage dates, naming customs, and cloud portraits",
                iconEmoji = "📋",
                summary = "Capture rich family records while adhering to Philippine civil registry standards. Learn how Linked-Spouse Co-Parent Auto-Selection works, validate marriage dates, and search across multi-generational directories.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Linked-Spouse Co-Parent Auto-Selection & Cloud Portraits",
                        paragraphs = listOf(
                            "Smart Co-Parent Auto-Selection: When adding or editing a family member, selecting a Biological Father automatically highlights and pre-selects his linked wife as Biological Mother (marked with '★ Linked Spouse'), while keeping '-- None --' at the top if the child has a different mother. The same works in reverse when selecting the Mother first.",
                            "Cross-Device Cloud Portraits: Profile photos selected from your gallery are automatically compressed and encoded into portable cloud portraits that synchronize instantaneously across all clan members' devices.",
                            "Philippine Naming Customs: Record married female relatives by their maiden surname (birth surname) to preserve unbroken lineage lines."
                        )
                    ),
                    ModuleSection(
                        heading = "Vital Dates, Marriage Chronology & Biological Guardrails",
                        paragraphs = listOf(
                            "Flexible Date Parsing: Supports YYYY-MM-DD, MM/DD/YYYY, Month DD YYYY, or approximate birth years.",
                            "Parent-Child & Sibling Guardrails: Enforces a minimum 12-year parent-child age gap, realistic maternal (≤60 yrs) and paternal (≤80 yrs) childbearing windows, and prevents linking a child as a sibling of their own parent or ancestor.",
                            "Date of Marriage Validation: When linking spouses, entering the Date of Marriage verifies that both partners were at least 18 years old on their wedding date (RA 11596) and that the marriage occurred during both spouses' lifetimes."
                        )
                    ),
                    ModuleSection(
                        heading = "Searching, Filtering & Clean Deletion",
                        paragraphs = listOf(
                            "In Family Records, filter members by name, generation tier, or living status. Deleting a member automatically cleans up all reciprocal parent, child, spouse, and sibling links across the entire tree."
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
                tagline = "The 3-step merge wizard, smart duplicate deduplication, and zero-risk synthesis",
                iconEmoji = "🔗",
                summary = "Combine separate family trees with distant relatives without fear of data loss. Learn how the 3 bridge types and Smart Duplicate Deduplication reconcile overlapping relatives cleanly.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "The 3-Step Merge Wizard & Smart Deduplication",
                        paragraphs = listOf(
                            "Step 1: Select Active Tree (Tree A) and Incoming Tree (Tree B from your account or an external tree joined via Invite Code).",
                            "Step 2: Choose Anchor Members—the connecting individuals from Tree A and Tree B.",
                            "Step 3: Choose the Bridge Type (Spouse, Parent-Child, or Shared Ancestor) and execute the cloud merge.",
                            "Smart Duplicate Deduplication: KinTrace automatically detects overlapping members across both trees (matching normalized first/last name, birth date, and gender) and reconciles their parent, spouse, and sibling links instead of creating duplicate clones."
                        )
                    ),
                    ModuleSection(
                        heading = "The 3 Bridge Types Explained",
                        paragraphs = listOf(
                            "1. Spouse Bridge: Connects two unrelated family clans united by a marriage between their respective members.",
                            "2. Parent-Child Bridge: Connects an offshoot branch to a discovered historical ancestor in the main tree.",
                            "3. Shared Ancestor Bridge: Unifies two branches that independently trace back to the same forebear."
                        )
                    ),
                    ModuleSection(
                        heading = "The Zero-Risk Master Tree C Guarantee",
                        paragraphs = listOf(
                            "Tree A and Tree B remain 100% read-only throughout the merge process. KinTrace synthesizes the combined lineage safely and requires Owner or Editor permissions to commit."
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
                tagline = "Real-time living birthdate redaction, note hiding, and collaborator role governance",
                iconEmoji = "🛡️",
                summary = "Protect sensitive family information in full compliance with Republic Act 10173. Learn how real-time birthdate masking, sensitive note redaction, and Owner/Editor/Viewer RBAC permissions work.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "RA 10173 (Philippine Data Privacy Act of 2012)",
                        paragraphs = listOf(
                            "Deceased Ancestor Records: Historical records of deceased forebears remain open for family history documentation.",
                            "Living Relatives Protection: Exact birthdates and personal notes of living individuals are protected via reactive cloud privacy toggles."
                        )
                    ),
                    ModuleSection(
                        heading = "Granular Real-Time Privacy Toggles",
                        paragraphs = listOf(
                            "1. Private Tree Switch: Restricts tree discovery strictly to invited collaborators.",
                            "2. Mask Living Relatives' Birthdates: Automatically replaces exact birth dates of living members with 'YYYY ( Protected )' across Family Records, Member Details, and Exports.",
                            "3. Hide Sensitive Notes: Redacts biographical notes from non-owner viewers.",
                            "4. Hide Cause of Death & Vital Details: Masks sensitive mortality details for non-owners."
                        )
                    ),
                    ModuleSection(
                        heading = "Collaborator Roles & Access Governance",
                        paragraphs = listOf(
                            "Owner: Full administrative authority, privacy configuration, collaborator role changes, and tree deletion rights.",
                            "Editor: Authorized to add, edit, merge, and link family members within the tree.",
                            "Viewer: Read-only access to explore the tree and trace relationships without modifying records."
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
                tagline = "Family Code Articles 37/38, RA 11596 marriage date checks, childbearing bounds, and loop detection",
                iconEmoji = "⚖️",
                summary = "Ensure your family tree is legally sound and topologically valid. Learn how KinTrace scans for incestuous marriages, 4th-degree cousin restrictions, marriage-date age violations, childbearing bounds, and circular ancestry loops.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Running the Automated Tree Health Audit",
                        paragraphs = listOf(
                            "KinTrace's audit engine scans every member and relationship in real time:",
                            "Red Badges (Critical Errors): Direct legal violations, circular ancestry loops, or impossible parent-child chronologies.",
                            "Amber Badges (Data Warnings): Missing vital dates, orphaned links, or childbearing age warnings."
                        )
                    ),
                    ModuleSection(
                        heading = "Philippine Family Code & RA 11596 Marriage Checks",
                        paragraphs = listOf(
                            "Article 37 (Incestuous Marriages): Marriages between ascendants and descendants of any degree, and between full or half siblings, are void from the beginning.",
                            "Article 38 (Public Policy Prohibitions): Marriages between collateral blood relatives up to the 4th civil degree (uncles/aunts, nieces/nephews, and first cousins / pinsan buo) are void.",
                            "Republic Act 11596 (Child Marriage Prohibition): Validates that both spouses were at least 18 years old on their actual Date of Marriage and alive at the time of marriage."
                        )
                    ),
                    ModuleSection(
                        heading = "Biological & Topological Graph Safeguards",
                        paragraphs = listOf(
                            "Childbearing & Parent Gap Bounds: Verifies a minimum 12-year parent-child gap and checks maternal (≤60) and paternal (≤80) age at a child's birth.",
                            "Cycle / Loop Prevention: Detects and blocks circular ancestry loops or parent-sibling contradictions."
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
                tagline = "Generating high-res PDFs, HD PNG charts, CSV rosters, and GEDCOM files",
                iconEmoji = "📤",
                summary = "Transform your digital family tree into tangible physical documents. Learn how to export high-resolution vector PDFs, high-definition PNGs, CSV spreadsheets, and GEDCOM files for family reunions.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Supported Export Formats",
                        paragraphs = listOf(
                            "1. High-Resolution Vector PDF: Multi-page vector document formatted for large-format physical printing and archival binders.",
                            "2. High-Definition Image (PNG): Visual tree chart formatted for digital presentations and family group chats.",
                            "3. CSV Data Spreadsheet: Complete tabular roster containing member vitals, dates, and relationship metadata.",
                            "4. GEDCOM 5.5.1 / 7.0: Standard international genealogical format for cross-platform research."
                        )
                    ),
                    ModuleSection(
                        heading = "Export Customization & Privacy Masking",
                        paragraphs = listOf(
                            "Generation Depth Filter: Restrict exports to 3, 4, or full clan generations.",
                            "Living Member Privacy Masking: Automatically redacts living relatives' exact birthdates in exported files when enabled."
                        )
                    )
                ),
                deepLinkTarget = "ExportTreeActivity",
                deepLinkActionText = "Export Family Tree"
            ),

            KinAcademyModule(
                id = 9,
                title = "Clan Analytics & Profile Completeness",
                category = "Analytics",
                tagline = "Profile Completeness Scorecard (0–100%), generational depth, gender balance, and longevity",
                iconEmoji = "📊",
                summary = "Discover deep insights into your family's demographic history and data health. Track your Clan Profile Completeness Scorecard, generational depth, gender distribution ratios, and ancestral longevity.",
                detailedSections = listOf(
                    ModuleSection(
                        heading = "Data Quality & Profile Completeness Scorecard",
                        paragraphs = listOf(
                            "At the top of Clan Insights, the Profile Completeness Scorecard (0%–100%) evaluates every member in your tree across 6 core biographical fields: First Name, Last Name, Gender, Birth Date, Biological Parent Links, and Biography.",
                            "Scores of 80%+ earn an 'Excellent Profile Completeness' badge, 50%–79% indicate 'Moderate Profile Completeness', and below 50% highlight branches that need additional research."
                        )
                    ),
                    ModuleSection(
                        heading = "Demographic, Generational & Activity Metrics",
                        paragraphs = listOf(
                            "Generational Depth & Gender Balance: Measures the longest unbroken ancestral line and visualizes male/female distribution across branches.",
                            "Ancestral Longevity & Activity Log: Computes average lifespan across documented generations and displays a chronological audit trail of collaborative edits."
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
