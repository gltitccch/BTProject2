# KINTRACE: SCOPE OF THE STUDY (MODULES 1 TO 3)
**Deliverable:** Chapter 1: Scope and Limitations (STI Capstone Manuscript)  
**Standard:** Pure Monochrome Black-and-White, Times New Roman, Single-Level `a. Title – Description.` Format  

---

## Scope of the Study

This study focused on the design, development, and implementation of the Mobile-Based Genealogical Bloodline Tracing System (KinTrace), an Android-based mobile application that enables Filipino families to document ancestral heritage, manage multi-generational records, calculate consanguinity degrees under Philippine civil law, and visualize family trees in real time. The application was developed natively for Android using Android Studio and Kotlin, supported by Google Cloud Firebase for authentication, real-time database synchronization, and secure media storage.

The system encompasses eight (8) core operational modules. The specific features, capabilities, and boundaries of the first three modules are detailed below:

### 1. User Management and Security Module
The User Management and Security Module manages user onboarding, session authentication, access permissions, and privacy protection for sensitive family records in accordance with the Data Privacy Act of 2012 (Republic Act No. 10173). This module ensures that only verified users can access family workspaces and that sensitive details are protected from unauthorized viewing or accidental deletion. Specifically, this module covers the following features:

* **a. Account Registration and Login –** Allows new users to create accounts using a valid email and password, verified through automated checks that reject invalid email formats, blank fields, and users under the minimum age policy.
* **b. Disposable Email Shield and Domain Shortcuts –** Protects family tree records by blocking temporary or fake email addresses, and provides one-tap domain buttons (such as @gmail.com and @yahoo.com) for faster account setup.
* **c. Password Security and Recovery –** Implements strong password rules to prevent easy guessing, and provides a password reset feature that sends a secure recovery link to the user's registered email.
* **d. Mandatory Privacy and Terms Agreement –** Requires users to read and accept the data privacy policy and terms of service before an account can be created in the database.
* **e. Role-Based Access Control –** Assigns users to one of three roles (Viewer, Editor, or Owner) to control who can view, add, edit, or delete family records, showing a clear warning message if an unauthorized action is attempted.
* **f. Living Relative Privacy Protection –** Automatically conceals the exact birthdates of living family members and displays an approximate age instead, preventing identity exposure while keeping historical dates visible for deceased ancestors.
* **g. Demo Account Vault –** Provides pre-configured test accounts with sample family trees, allowing evaluators, researchers, and panelists to test different roles and features instantly without creating new accounts.
* **h. Administrative System Management –** Provides platform tools for reviewing clan member expansion requests, monitoring cloud database health, broadcasting system notices, and performing daily cloud backups.

### 2. Dashboard Module
The Dashboard Module serves as the central command center of the KinTrace application upon logging in. It displays real-time statistics about the family tree, monitors tree completeness, presents quick shortcuts to core features, and provides learning guides for relatives of all ages. Specifically, this module covers the following features:

* **a. Dashboard State Display –** Automatically switches between an Empty State (guiding first-time users to plant a tree or join with an invite code) and a Populated State (displaying clan statistics, quick actions, and recent activities).
* **b. Family Tree Switcher –** Allows users who belong to multiple families to switch seamlessly between different clan trees using a sliding side menu.
* **c. Real-Time Clan Counters –** Computes and displays live counts of total family members, total generations deep, unique marriage unions, and living versus deceased relatives.
* **d. Clan Health and Missing-Parents Alert –** Scans the family tree and displays an advisory banner when relatives have unrecorded parents, with a one-tap shortcut that opens the filtered list to fix missing links.
* **e. Quick Action Menu –** Provides one-tap shortcut buttons to add relatives, open the interactive tree canvas, browse the member directory, start a bloodline trace, and export tree charts.
* **f. Recent Activities Log –** Displays a chronological feed of recent family updates, showing newly added members, updated profiles, and deleted relatives with a restoration request option.
* **g. In-App User Guide and Learning Hub –** Offers built-in tutorials and a Filipino kinship terms guide to help users of all ages navigate the application confidently.

### 3. Family Information Management Module
The Family Information Management Module handles the creation, updating, organizing, searching, and archival recovery of individual family member records. It ensures that biological parentage, chronological marriages, and historical biographical details are recorded accurately without data loss. Specifically, this module covers the following features:

* **a. Add Family Member Record –** Allows authorized users to record new relatives with complete biographical information, including full name, maiden name, gender, birth date, birthplace, and vital status.
* **b. Direct Lineage Linking –** Connects relatives by selecting biological fathers and mothers from dropdown lists of existing members, automatically creating matching reciprocal parent-child connections across the tree.
* **c. Multiple Marriages and Biological Co-Parent Selection –** Records sequential marriages in chronological order and allows users to choose the specific biological father or mother for each child when a parent has had multiple spouses.
* **d. Searchable Member Directory –** Displays all clan members in an organized list with an instant search bar that filters relatives by full name or nickname in real time.
* **e. Horizontal Filter Chips and Sorting –** Features quick filter buttons (All, Living, Deceased, and Missing Parents) with live count badges, along with sorting options by alphabetical order (A-Z) or age (oldest or youngest first).
* **f. Archival Photos and Document Attachments –** Enables users to upload profile portraits and attach historical documents (such as birth certificates or marriage contracts) directly to member profiles.
* **g. Safe Member Deletion and Restoration –** Moves deleted relatives into a recovery archive instead of erasing them immediately, allowing clan stewards to approve restoration requests and restore all original family connections cleanly.
* **h. Direct Bloodline Trace Shortcut –** Provides a one-tap shortcut on any member's profile that locks that person as Person A, opens an auto-focused search for Person B, and traces their exact blood relationship immediately.
