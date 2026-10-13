import os
import sys
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

def create_presentation():
    prs = Presentation()
    # 16:9 Widescreen dimensions
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)

    blank_layout = prs.slide_layouts[6] # Blank slide layout

    # KinTrace Color Palette
    BG_DARK = RGBColor(6, 13, 9)          # #060D09 Deep Emerald
    CARD_DARK = RGBColor(14, 30, 20)      # #0E1E14 Forest Surface
    CARD_BORDER = RGBColor(27, 56, 37)    # #1B3825 Forest Border
    GOLD_PRIMARY = RGBColor(212, 175, 55) # #D4AF37 Imperial Gold
    GOLD_LIGHT = RGBColor(245, 228, 149)  # #F5E495 Warm Gold Light
    TEXT_WHITE = RGBColor(240, 253, 244)  # #F0FDF4 White
    TEXT_MUTED = RGBColor(139, 163, 148)  # #8BA394 Muted Green-Gray
    MINT_ACCENT = RGBColor(74, 222, 128)  # #4ADE80 Health Mint
    PHONE_CHASSIS = RGBColor(18, 38, 26)  # High-contrast device frame

    artifact_dir = r"C:\Users\Renzy\.gemini\antigravity\brain\4897225e-a747-409b-a902-2f6c3c6f1e92"

    def apply_slide_bg(slide):
        bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
        bg.fill.solid()
        bg.fill.fore_color.rgb = BG_DARK
        bg.line.fill.background()
        return bg

    def add_header(slide, category_tag, title_text, subtitle_text):
        tag_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.35), Inches(7.0), Inches(0.35))
        tf_tag = tag_box.text_frame
        tf_tag.word_wrap = True
        p_tag = tf_tag.paragraphs[0]
        p_tag.text = f"✨  {category_tag.upper()}"
        p_tag.font.size = Pt(11)
        p_tag.font.bold = True
        p_tag.font.color.rgb = GOLD_PRIMARY

        title_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.68), Inches(11.8), Inches(0.65))
        tf_title = title_box.text_frame
        tf_title.word_wrap = True
        p_title = tf_title.paragraphs[0]
        p_title.text = title_text
        p_title.font.size = Pt(23)
        p_title.font.bold = True
        p_title.font.color.rgb = TEXT_WHITE

        sub_box = slide.shapes.add_textbox(Inches(0.8), Inches(1.25), Inches(11.8), Inches(0.38))
        tf_sub = sub_box.text_frame
        tf_sub.word_wrap = True
        p_sub = tf_sub.paragraphs[0]
        p_sub.text = subtitle_text
        p_sub.font.size = Pt(12.5)
        p_sub.font.color.rgb = TEXT_MUTED

    def add_card(slide, left, top, width, height, title, items):
        card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
        card.fill.solid()
        card.fill.fore_color.rgb = CARD_DARK
        card.line.color.rgb = CARD_BORDER
        card.line.width = Pt(1.5)

        tb = slide.shapes.add_textbox(left + Inches(0.2), top + Inches(0.15), width - Inches(0.4), height - Inches(0.3))
        tf = tb.text_frame
        tf.word_wrap = True

        p_head = tf.paragraphs[0]
        p_head.text = title
        p_head.font.size = Pt(14)
        p_head.font.bold = True
        p_head.font.color.rgb = GOLD_LIGHT
        p_head.space_after = Pt(6)

        for item in items:
            p = tf.add_paragraph()
            p.text = f"•  {item}"
            p.font.size = Pt(11)
            p.font.color.rgb = TEXT_WHITE
            p.space_after = Pt(5)

    def add_prominent_device(slide, img_filename, left, top=Inches(1.2), height=Inches(5.9)):
        """Adds a significantly larger, high-visibility smartphone frame with gold chassis border."""
        img_path = os.path.join(artifact_dir, img_filename)
        if not os.path.exists(img_path):
            print(f"Warning: Image {img_filename} not found at {img_path}")
            return

        img_width = height * 0.45
        padding = Inches(0.12)
        frame_width = img_width + (padding * 2)
        frame_height = height + (padding * 2)
        frame_top = top - padding

        chassis = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left - padding, frame_top, frame_width, frame_height)
        chassis.fill.solid()
        chassis.fill.fore_color.rgb = PHONE_CHASSIS
        chassis.line.color.rgb = GOLD_PRIMARY
        chassis.line.width = Pt(1.8)

        try:
            slide.shapes.add_picture(img_path, left, top, height=height)
        except Exception as e:
            print(f"Error adding picture {img_filename}: {e}")

    def add_dual_devices(slide, img1, img2, label1="Primary Screen", label2="Companion View"):
        """Renders two large mobile screens side-by-side so both states are prominently visible."""
        dev_height = Inches(5.6)
        dev_width = dev_height * 0.45
        left1 = Inches(6.8)
        left2 = left1 + dev_width + Inches(0.4)
        top = Inches(1.4)

        add_prominent_device(slide, img1, left1, top, dev_height)
        add_prominent_device(slide, img2, left2, top, dev_height)

        for pos_left, label in [(left1, label1), (left2, label2)]:
            lbl_box = slide.shapes.add_textbox(pos_left - Inches(0.1), Inches(0.95), dev_width + Inches(0.2), Inches(0.35))
            tf = lbl_box.text_frame
            p = tf.paragraphs[0]
            p.text = label.upper()
            p.alignment = PP_ALIGN.CENTER
            p.font.size = Pt(9.5)
            p.font.bold = True
            p.font.color.rgb = GOLD_LIGHT

    # =========================================================================
    # SLIDE 1: TITLE SLIDE
    # =========================================================================
    s1 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s1)

    bar = s1.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(1.8), Inches(0.12), Inches(3.2))
    bar.fill.solid()
    bar.fill.fore_color.rgb = GOLD_PRIMARY
    bar.line.fill.background()

    tb1 = s1.shapes.add_textbox(Inches(1.2), Inches(1.6), Inches(11.0), Inches(3.8))
    tf1 = tb1.text_frame
    tf1.word_wrap = True

    p_badge = tf1.paragraphs[0]
    p_badge.text = "OFFICIAL APPLICATION PRODUCT SHOWCASE • VERSION 1.2.0"
    p_badge.font.size = Pt(12)
    p_badge.font.bold = True
    p_badge.font.color.rgb = GOLD_PRIMARY
    p_badge.space_after = Pt(12)

    p_title = tf1.add_paragraph()
    p_title.text = "KinTrace"
    p_title.font.size = Pt(54)
    p_title.font.bold = True
    p_title.font.color.rgb = TEXT_WHITE
    p_title.space_after = Pt(6)

    p_sub = tf1.add_paragraph()
    p_sub.text = "The Real-Time Collaborative Heritage & Clan Preservation Ecosystem"
    p_sub.font.size = Pt(22)
    p_sub.font.color.rgb = GOLD_LIGHT
    p_sub.space_after = Pt(14)

    p_desc = tf1.add_paragraph()
    p_desc.text = "An exhaustive walkthrough of features, mathematical kinship engines, vector tree navigation, and security architecture."
    p_desc.font.size = Pt(14)
    p_desc.font.color.rgb = TEXT_MUTED

    s1.notes_slide.notes_text_frame.text = (
        "Welcome everyone. Today I am presenting KinTrace, a next-generation Android application designed "
        "to solve generational record fragmentation through interactive vector family trees, statutory kinship calculations, "
        "and real-time collaborative clan federation."
    )

    # =========================================================================
    # SLIDE 2: THE PROBLEM & CORE VALUE PROPOSITION
    # =========================================================================
    s2 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s2)
    add_header(s2, "Executive Overview", "The Genealogy Problem & KinTrace Solution", "Transforming scattered family records into an authoritative, living digital heritage.")

    add_card(s2, Inches(0.8), Inches(1.8), Inches(5.6), Inches(5.1), "The Three Genealogy Dilemmas", [
        "Data Fragmentation: Family memories and lineages are scattered across unorganized chat groups, paper albums, and memory.",
        "Kinship Ambiguity: Users struggle to compute exact legal and biological degrees of relationship under Philippine civil law.",
        "Collaboration Friction: Multiple family branches work on isolated trees with zero automated branch-merging or conflict resolution."
    ])

    add_card(s2, Inches(6.8), Inches(1.8), Inches(5.6), Inches(5.1), "The KinTrace Architectural Solution", [
        "Single Source of Truth: Centralized Cloud Firestore repository with real-time reactive sync across all relatives simultaneously.",
        "Exact Statutory Math: Automated MRCA (Nearest Common Ancestor) & Civil Kinship calculation (Family Code Art. 963–966).",
        "Clan Federation Engine: Non-destructive 3-step multi-tree branch stitching with granular role-based privacy (RBAC)."
    ])

    s2.notes_slide.notes_text_frame.text = (
        "Traditional genealogy apps are either single-user silos or overly academic. KinTrace addresses three core pain points: "
        "scattered memories, legal confusion regarding civil degrees, and the technical challenge of merging two distinct family branches."
    )

    # =========================================================================
    # SLIDE 3: ONBOARDING & WELCOME STORYBOOK
    # =========================================================================
    s3 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s3)
    add_header(s3, "User Journey", "Seamless Onboarding & Clan Initiation", "Guiding new and veteran relatives into the family ecosystem with zero friction.")

    add_card(s3, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Onboarding Feature Walkthrough", [
        "Dual-Mode Authentication: Secure email/password with verification safeguards, password recovery, and live credential validation.",
        "Terms of Clan Stewardship: Explicit 10-clause privacy covenant ensuring mutual family consent and data ethics.",
        "Interactive Welcome Storybook: 3-slide visual orientation introducing the canvas, bloodline tracing, and collaborative archiving.",
        "Clan Quest HUD: Gamified progress checklist on the home screen guiding users to complete key milestones.",
        "Instant Demo Vault: 1-click evaluation access with pre-configured personas (Clan Leader, Editor, Viewer)."
    ])

    add_prominent_device(s3, "slide2_screen.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s3.notes_slide.notes_text_frame.text = (
        "From the first launch, KinTrace prioritizes engagement and transparency. Users are greeted by a 3-slide Welcome Storybook, "
        "sign a clear Stewardship covenant, and receive interactive onboarding milestones via the Clan Quest HUD."
    )

    # =========================================================================
    # SLIDE 4: TWIN-POD EXECUTIVE DASHBOARD (DUAL LARGE SCREENS)
    # =========================================================================
    s4 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s4)
    add_header(s4, "Home Dashboard", "The Twin-Pod Executive Command Center", "High-craft floating micro-islands paired with comprehensive heritage analytics.")

    add_card(s4, Inches(0.8), Inches(1.8), Inches(5.4), Inches(5.1), "Dashboard Architecture & Components", [
        "Twin-Pod Floating Islands: Ultra-slim 44dp split-capsule header separating User Identity from the Utility Dock.",
        "Live Presence Beacon: Real-time green cloud sync indicator reflecting active Firestore handshake status.",
        "Heritage Hero Analytics Card: Prominently highlights total documented generations ('4 Generations') and real-time health score (92%).",
        "4-Stat Real-Time Counters: Dynamic counts of Members, Branches, Generations, and Archival Records.",
        "One-Touch Theme Switcher: Instant seamless transition between Dark Emerald and Light Linen modes without reload."
    ])

    add_dual_devices(s4, "live_twin_pod_dark_home.png", "live_twin_pod_nobanner.png", "Dark Emerald Mode", "Light Linen Mode")

    s4.notes_slide.notes_text_frame.text = (
        "Here is the newly engineered Twin-Pod Executive Header shown in both Dark Emerald and Light Linen themes. "
        "The left pod handles identity, while the right pod docks quick tools, flowing cleanly into the 4-Generation Hero Analytics card."
    )

    # =========================================================================
    # SLIDE 5: INTERACTIVE FAMILY TREE CANVAS (HERO SPOTLIGHT)
    # =========================================================================
    s5 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s5)
    add_header(s5, "Core Canvas Engine", "Interactive Family Tree Canvas Navigator", "Multi-touch, hardware-accelerated vector tree rendering with full generation mapping.")

    add_card(s5, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Canvas Capabilities & Navigation", [
        "Infinite Pan & Pinch-Zoom: Fluid multi-touch navigation across expansive multi-generational family trees without stutter.",
        "Custom Vector Tree Rendering: Bespoke anti-aliased connector curves joining parent-child nodes and marital unions.",
        "Generational Badge Hierarchy: Gold Ahnentafel badges and generation indicators distinguish patriarchs from newest descendants.",
        "Color-Coded Status Badges: Distinct visual markers for biological relatives, spouses, and in-law connections.",
        "Interactive Node Selection: Tapping any member instantly summons a contextual bottom sheet showing relationships and actions."
    ])

    add_prominent_device(s5, "live_minimalist_tree.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s5.notes_slide.notes_text_frame.text = (
        "The Interactive Canvas is the crown jewel of KinTrace. It uses Android hardware-accelerated Custom Views to draw vector connecting lines, "
        "manage pinch-to-zoom gestures, and render color-coded relationship badges across an infinite virtual layout."
    )

    # =========================================================================
    # SLIDE 6: ANCESTOR PEDIGREE & AHNENTAFEL TRAVERSAL
    # =========================================================================
    s6 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s6)
    add_header(s6, "Genealogical Science", "Ancestor Pedigree & Ahnentafel Traversal", "Systematic lineage mapping based on standardized binary genealogical mathematics.")

    add_card(s6, Inches(0.8), Inches(1.8), Inches(5.6), Inches(5.1), "Ahnentafel Math & Traversal", [
        "Strict Binary Tree Formula: Root person is 1; father is always 2n; mother is 2n+1.",
        "Generational Depth Calculation: Immediate computation of direct patrilineal and matrilineal ascent lines.",
        "Pedigree Collapse Prevention: Algorithms safely manage endogamy (cousin marriages) without infinite recursive loops.",
        "Instant Direct Ancestry Filter: Quickly isolates direct forebears while temporarily hiding lateral collateral branches."
    ])

    add_card(s6, Inches(6.8), Inches(1.8), Inches(5.6), Inches(5.1), "Fan Chart Visualization", [
        "Concentric Generation Rings: Renders ancestral lines radiating outwards like a classic pedigree fan.",
        "Color-Coded Quadrants: Paternal grandfather, paternal grandmother, maternal grandfather, maternal grandmother branches.",
        "High-Density Overview: Displays 5 to 7 ancestral generations on a single mobile viewport without panning."
    ])

    s6.notes_slide.notes_text_frame.text = (
        "Beyond simple box-and-line trees, KinTrace supports standard Ahnentafel pedigree numbering. This ensures mathematical accuracy "
        "when tracking direct ancestors, avoiding loop recursion in complex intermarried lineages."
    )

    # =========================================================================
    # SLIDE 7: BLOODLINE TRACING & STATUTORY KINSHIP DEGREES
    # =========================================================================
    s7 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s7)
    add_header(s7, "Kinship Intelligence", "Bloodline Tracing Engine & Statutory Degrees", "Automated Nearest Common Ancestor (MRCA) pathing and Philippine Family Code compliance.")

    add_card(s7, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Tracing Engine Specifications", [
        "MRCA Graph Traversal: Automated algorithm calculates the Lowest Common Ancestor connecting any two arbitrary tree members.",
        "Civil Law Degrees (Family Code Art. 963–966): Computes exact civil relationship degrees (1st degree parent, 2nd degree sibling/grandparent, 3rd degree uncle/aunt, 4th degree 1st cousin).",
        "Direct vs. Collateral Line Differentiation: Distinguishes direct ascending/descending lines from collateral sibling branches.",
        "Visual Step-by-Step Path Breakdown: Displays the step-by-step ascendant and descendant trail connecting the two selected relatives.",
        "Legal & Succession Relevance: Vital for inheritance validation, marriage impediment verification, and reunion introductions."
    ])

    add_prominent_device(s7, "trace_screen.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s7.notes_slide.notes_text_frame.text = (
        "Our Bloodline Tracing Engine is grounded in real statutory law. By implementing Articles 963 to 966 of the Philippine Family Code, "
        "the app counts generation steps up to the common ancestor and down to the relative, presenting the exact legal civil degree."
    )

    # =========================================================================
    # SLIDE 8: MEMBER RECORDS & BIOGRAPHICAL ARCHIVAL
    # =========================================================================
    s8 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s8)
    add_header(s8, "Archival Vault", "Member Records & Biographical Archival", "Preserving oral history, physical portraits, and historical milestones for posterity.")

    add_card(s8, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Record Vault Capabilities", [
        "Rich Biographical Cards: Detailed profiling including full legal names, maiden names, aliases, birthdates, and resting places.",
        "Multimedia Memory Attachments: High-resolution profile photos, historical family heirloom snapshots, and recorded voice notes.",
        "Geographical Heritage Tags: Documenting ancestral hometowns, birth municipalities, and migration history.",
        "Fast Search & Generational Filtering: Instant indexed search by first name, surname, or generation badge.",
        "Live Relational Anchors: Direct navigation from a member's record straight into their position on the interactive canvas."
    ])

    add_prominent_device(s8, "vacation_rerun_records_opened.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s8.notes_slide.notes_text_frame.text = (
        "KinTrace is not just a diagram; it's a digital archive. Every member has a rich profile containing biographical dates, "
        "historical hometowns, multimedia attachments, and oral history recordings preserved in cloud storage."
    )

    # =========================================================================
    # SLIDE 9: MULTI-TREE MERGING & CLAN FEDERATION
    # =========================================================================
    s9 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s9)
    add_header(s9, "Advanced Collaboration", "Multi-Tree Merging & Clan Federation", "Unifying separate family branches without data loss through guided synthesis.")

    add_card(s9, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "3-Step Merge Engine Workflow", [
        "Step 1 — Tree Selection: Select an external tree branch via secure Tree Invite Code or shared Clan Federation Pass.",
        "Step 2 — Bridge Person Linking: Pinpoint the shared relative or marriage bridge that connects the two distinct lineages.",
        "Step 3 — Conflict & Duplicate Resolution: Visual side-by-side reconciliation of overlapping records and differing dates.",
        "Zero-Risk Master Tree Synthesis: Synthesizes a unified Master Tree C while keeping original trees A and B safe as pristine backups.",
        "Automatic Relationship Bidirectionality: Ensures parent, child, and spouse pointers are reciprocally updated across both branches."
    ])

    add_prominent_device(s9, "dumgen_phase4_merge_screen.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s9.notes_slide.notes_text_frame.text = (
        "When two cousins build separate trees, merging them is notoriously risky. KinTrace provides a 3-step Merge Wizard that identifies "
        "a shared bridge relative, resolves conflicting records, and synthesizes a master tree without touching the original backups."
    )

    # =========================================================================
    # SLIDE 10: FAMILY PRIVACY & RBAC SECURITY MATRIX (USER'S EXACT SCREENSHOT PROMINENT)
    # =========================================================================
    s10 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s10)
    add_header(s10, "Governance & Security", "Family Privacy & RBAC Security Matrix", "Granular role-based access control protecting living relatives from unauthorized exposure.")

    add_card(s10, Inches(0.8), Inches(1.8), Inches(5.4), Inches(5.1), "Privacy Engine & Controls Walkthrough", [
        "Tree Visibility Switch: Instant one-tap toggle between completely Private tree (authorized only) and Shared Clan access.",
        "Granular Member Masking: Selective switches to hide birth dates, personal notes, and sensitive deceased statuses from non-owners.",
        "4-Tier Role Hierarchy: Clan Owner (full rights), Editor (branch updates), Contributor (suggestions), and Viewer (read-only).",
        "Authorized Members Management: Real-time list of delegated clan managers with quick Invite Code generation.",
        "Legal Ethics Standard: Prevents data harvesting while keeping heritage discovery accessible to verified descendants."
    ])

    add_dual_devices(s10, "privacy_dark_screen.png", "privacy_screen.png", "Dark Emerald Privacy View", "Light Linen Privacy View")

    s10.notes_slide.notes_text_frame.text = (
        "Privacy is paramount. Here is the Privacy & Access control center shown in both themes. "
        "Clan owners can toggle tree visibility, invite authorized relatives with specific roles, and mask birth dates or sensitive notes for living relatives."
    )

    # =========================================================================
    # SLIDE 11: KINACADEMY KNOWLEDGE HUB
    # =========================================================================
    s11 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s11)
    add_header(s11, "Education & Stewardship", "KinAcademy Genealogical Hub & Lexicon", "A built-in 10-module curriculum teaching relatives the science of family preservation.")

    add_card(s11, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "KinAcademy Learning Modules", [
        "10 Structured Lessons: Covers Clan Initiation, Canvas Navigation, Ahnentafel Math, Bloodline Tracing, Archival Ethics, and Multi-Tree Merging.",
        "Searchable Genealogical Lexicon: Comprehensive glossary explaining terms like MRCA, Consanguinity, Collateral Kin, and Pedigree Collapse.",
        "Statutory Law Primer: Interactive explanations of Philippine civil laws regarding inheritance, legitimate succession, and civil degrees.",
        "Offline Educational Access: All courseware and glossaries are bundled locally for instant access during rural family reunions.",
        "Interactive Knowledge Quests: Users unlock Clan Builder achievements by exploring lessons and applying principles."
    ])

    add_prominent_device(s11, "kin_academy_screen.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s11.notes_slide.notes_text_frame.text = (
        "KinTrace is also an educational platform. The KinAcademy features 10 comprehensive modules and a built-in genealogical lexicon, "
        "teaching family members how genealogy works, legal definitions of kinship, and oral history best practices."
    )

    # =========================================================================
    # SLIDE 12: SYSTEM ADMIN CITADEL & LIVE TELEMETRY
    # =========================================================================
    s12 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s12)
    add_header(s12, "System Operations", "System Admin Citadel & Live Operations", "Centralized operational control, live aggregate telemetry, and broadcast advisories.")

    add_card(s12, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Admin Citadel Controls", [
        "Real-Time Aggregate Telemetry: Live metrics monitoring total active trees, registered users, relationship links, and cloud storage health.",
        "System Broadcast Announcer: Instant global delivery of maintenance notices, update advisories, and system alerts to all client home screens.",
        "Demo Account Vault: Pre-populated test environment for live demonstrations, auditor inspections, and test verifications.",
        "Emergency Maintenance Switch: Instant toggle to pause branch merges during server indexing or scheduled cloud database migrations.",
        "Audit Trail Logging: Immutable log tracking administrative changes, system announcements, and security modifications."
    ])

    add_prominent_device(s12, "tab_ops_opened.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s12.notes_slide.notes_text_frame.text = (
        "For system managers, the Admin Citadel provides real-time telemetry, broadcast announcement tools, and security controls. "
        "Admins can broadcast maintenance notices directly to all users' dashboards in real time."
    )

    # =========================================================================
    # SLIDE 13: OVER-THE-AIR IN-APP AUTO-UPDATES
    # =========================================================================
    s13 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s13)
    add_header(s13, "DevOps & Delivery", "Over-The-Air (OTA) Updates & Cloud Sync", "Zero-friction client updates and reactive multi-device state synchronization.")

    add_card(s13, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Continuous Delivery & Reactive Sync", [
        "In-App Version Handshake: Automatically checks Firebase remote configuration on launch for new APK releases.",
        "Zero-Friction In-App Download: Integrated download progress dialog with SHA-256 hash verification and direct package install prompt.",
        "CentralTreeSynchronizer Bus: Reactive event bus ensuring that changes made on one phone propagate to other family members within milliseconds.",
        "Offline-First SQLite Cache: Full tree view and records remain completely browseable even in remote provincial areas without cellular coverage.",
        "Automatic Conflict Merging: Debounced network retries and automated conflict reconciliation prevent state corruption."
    ])

    add_prominent_device(s13, "ota_dialog_live_success.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s13.notes_slide.notes_text_frame.text = (
        "Updates are seamless. KinTrace features an Over-The-Air update manager that detects new releases, downloads the patch with progress feedback, "
        "and prompts the user to install. Meanwhile, our reactive sync engine guarantees real-time multi-device consistency."
    )

    # =========================================================================
    # SLIDE 14: EXPORT, ARCHIVAL & PHYSICAL HERITAGE
    # =========================================================================
    s14 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s14)
    add_header(s14, "Preservation & Output", "Export, Archival & Physical Heritage", "Delivering timeless physical and digital artifacts for clan reunions and legal records.")

    add_card(s14, Inches(0.8), Inches(1.8), Inches(5.8), Inches(5.1), "Export & Archival Outputs", [
        "High-Resolution PDF Tree Posters: Renders vector family trees into publication-ready PDF documents suitable for large-format reunion banners.",
        "Standard Genealogical CSV Export: Exports structured tabular member directories for import into desktop genealogy suites or legal proceedings.",
        "Encrypted Cloud Backup: Automated snapshot creation ensuring family heritage is shielded from phone loss, theft, or hardware failure.",
        "Physical Heirloom Framing Guide: Pre-formatted printable ancestral cards highlighting patriarchs, matriarchs, and generational statistics.",
        "Legal Relationship Certificates: Formatted relationship degree summaries ready for civil registry submissions or estate validation."
    ])

    add_prominent_device(s14, "export_screen.png", Inches(8.2), Inches(1.3), Inches(5.8))

    s14.notes_slide.notes_text_frame.text = (
        "KinTrace connects digital agility with physical heirloom preservation. Relatives can export high-resolution vector PDF posters "
        "for clan reunion printouts, generate CSV files for legal documentation, or produce certified relationship reports."
    )

    # =========================================================================
    # SLIDE 15: HERITAGE THEME SYSTEM (DUAL LARGE DEVICES)
    # =========================================================================
    s15 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s15)
    add_header(s15, "Design & Craft", "Dark Emerald & Light Linen Dual-Theme Engine", "Bespoke executive visual craftsmanship tailored for both day and night viewing.")

    add_card(s15, Inches(0.8), Inches(1.8), Inches(5.4), Inches(5.1), "Executive Visual Identity", [
        "Dark Emerald Heritage (#060D09): Rich obsidian-green surfaces conveying prestige, permanence, and historical legacy.",
        "Warm Gold Accents (#D4AF37): Metallic gold badges and indicators highlighting key generation milestones.",
        "Light Linen Mode (#EAECE4): Eye-comfortable warm linen surfaces that avoid harsh glare in outdoor reunion environments.",
        "Executive Typography: Custom Sora (display), Inter (UI), and JetBrains Mono (dates & codes) typefaces.",
        "Instant One-Tap Switch: Theme toggle right in the hero banner allows instant switching without restarting the app."
    ])

    add_dual_devices(s15, "live_twin_pod_dark_home.png", "live_twin_pod_nobanner.png", "Dark Emerald Heritage", "Light Linen Mode")

    s15.notes_slide.notes_text_frame.text = (
        "Visual design reflects the gravity of family heritage. KinTrace provides two carefully calibrated themes: "
        "Dark Emerald Heritage for high-craft executive elegance, and Light Linen for non-glare daytime outdoor viewing."
    )

    # =========================================================================
    # SLIDE 16: CONCLUSION & LIVE DEMO
    # =========================================================================
    s16 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s16)

    bar16 = s16.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.8), Inches(1.8), Inches(0.12), Inches(3.2))
    bar16.fill.solid()
    bar16.fill.fore_color.rgb = GOLD_PRIMARY
    bar16.line.fill.background()

    tb16 = s16.shapes.add_textbox(Inches(1.2), Inches(1.6), Inches(11.0), Inches(4.0))
    tf16 = tb16.text_frame
    tf16.word_wrap = True

    p_badge16 = tf16.paragraphs[0]
    p_badge16.text = "SUMMARY & NEXT STEPS"
    p_badge16.font.size = Pt(12)
    p_badge16.font.bold = True
    p_badge16.font.color.rgb = GOLD_PRIMARY
    p_badge16.space_after = Pt(12)

    p_title16 = tf16.add_paragraph()
    p_title16.text = "KinTrace: Built for Generations"
    p_title16.font.size = Pt(46)
    p_title16.font.bold = True
    p_title16.font.color.rgb = TEXT_WHITE
    p_title16.space_after = Pt(14)

    p_bullets16 = tf16.add_paragraph()
    p_bullets16.text = (
        "•  100% Native Android Architecture (Kotlin, Material You, Hardware-Accelerated Vector Canvas)\n"
        "•  Statutory Compliance with Philippine Family Code Kinship Degrees (Articles 963–966)\n"
        "•  Full Collaborative Federation: Non-destructive multi-branch tree synthesis & live sync\n"
        "•  Executive Modern Aesthetics: Minimalist Executive Typography & Twin-Pod Island design\n\n"
        "Thank you! We will now proceed with a live interactive demonstration."
    )
    p_bullets16.font.size = Pt(14)
    p_bullets16.font.color.rgb = TEXT_MUTED

    s16.notes_slide.notes_text_frame.text = (
        "In conclusion, KinTrace is a fully realized, battle-tested heritage management system. It combines "
        "statutory legal math, custom vector canvas rendering, and modern collaborative engineering. I am now open for questions and live app demonstration."
    )

    out_path_project = r"c:\Users\Renzy\AndroidStudioProjects\BTProject2\KinTrace_App_Presentation.pptx"
    out_path_artifact = os.path.join(artifact_dir, "KinTrace_App_Presentation.pptx")
    out_path_high_vis = r"c:\Users\Renzy\AndroidStudioProjects\BTProject2\KinTrace_App_Presentation_HighVisibility.pptx"

    saved = False
    try:
        prs.save(out_path_project)
        prs.save(out_path_artifact)
        saved = True
        print(f"Presentation saved successfully to primary paths:\n- {out_path_project}\n- {out_path_artifact}")
    except PermissionError:
        print(f"Primary file is currently open in PowerPoint! Saving to High Visibility edition...")
        prs.save(out_path_high_vis)
        out_artifact_hv = os.path.join(artifact_dir, "KinTrace_App_Presentation_HighVisibility.pptx")
        prs.save(out_artifact_hv)
        print(f"Saved to:\n- {out_path_high_vis}\n- {out_artifact_hv}")

if __name__ == "__main__":
    create_presentation()
