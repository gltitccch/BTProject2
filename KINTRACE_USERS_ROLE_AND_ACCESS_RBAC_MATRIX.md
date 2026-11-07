# KINTRACE: USERS ROLE AND ACCESS MATRIX
**Application:** KinTrace - Interactive Family Tree & Clan Heritage Management System  
**Specification:** Role-Based Access Control (RBAC) Governance & Module Permission Architecture  

---

## Users Role and Access

To safeguard family lineage integrity, ensure genealogical data security, and comply with the statutory principles of the Data Privacy Act of 2012 (Republic Act No. 10173), KinTrace enforces a comprehensive Role-Based Access Control (RBAC) architecture. Permissions are granted hierarchically across four (4) distinct system roles: **Viewer (Seedling)**, **Contributor (Sprout)**, **Clan Steward (Elder)**, and **System Administrator (Citadel SuperAdmin)**. This governance framework protects sensitive personal records—such as unmasked living birthdates, confidential biographical notes, and parental linkages—from unauthorized modification or accidental deletion while facilitating collaborative family tree research across multi-generational relatives. Outlined below are the default system permissions designated for each role across all core modules.

| Module / Functionality | Viewer (Seedling) | Contributor (Sprout) | Clan Steward (Elder) | System Admin (Citadel) |
| :--- | :---: | :---: | :---: | :---: |
| **Dashboard & Clan Workspace Management** | | | | |
| View Home Dashboard & Clan Statistics | ✓ | ✓ | ✓ | ✓ |
| Switch Between Joined Family Trees | ✓ | ✓ | ✓ | ✓ |
| Plant / Create New Clan Family Tree | X | ✓ | ✓ | ✓ |
| Join Clan Tree via 6-Character Invite Code | ✓ | ✓ | ✓ | ✓ |
| Access Clan Workspace Settings Drawer | X | X | ✓ | ✓ |
| Request Clan Quota Expansion (Hourglass) | X | X | ✓ | ✓ |
| Approve / Reject Clan Quota Expansions | X | X | X | ✓ |
| **Interactive Canvas & Tree Exploration** | | | | |
| View Multi-Generation Interactive Tree Canvas | ✓ | ✓ | ✓ | ✓ |
| View Upward Ancestor Pedigree Chart | ✓ | ✓ | ✓ | ✓ |
| View 200-Member Adaptive Fan Chart & Spokes | ✓ | ✓ | ✓ | ✓ |
| Use 800% Lossless Retina Vector Zoom & Pan | ✓ | ✓ | ✓ | ✓ |
| Run 1-Tap Bloodline Kinship Trace & Swap | ✓ | ✓ | ✓ | ✓ |
| Reposition Tree Cards (Custom Drag Layout) | X | ✓ | ✓ | ✓ |
| **Member Directory & Profile Maintenance** | | | | |
| Browse Directory with Quick Filter Chips | ✓ | ✓ | ✓ | ✓ |
| Sort Member Records (A-Z, Age) | ✓ | ✓ | ✓ | ✓ |
| View Biographical Details & Historical Notes | ✓ | ✓ | ✓ | ✓ |
| View Attached Photos & Certificate Proofs | ✓ | ✓ | ✓ | ✓ |
| Add New Family Member (Parents, Spouses, Children) | X | ✓ | ✓ | ✓ |
| Edit Member Profile Details & Vital Dates | X | ✓ | ✓ | ✓ |
| Add Sequential Spouses & Select Co-Parents | X | ✓ | ✓ | ✓ |
| Upload Portrait Photos & Archival PDF Proofs | X | ✓ | ✓ | ✓ |
| Soft-Delete Member to Recovery Archive | X | X | ✓ | ✓ |
| **Archival Recovery & Member Restoration** | | | | |
| View Soft-Deleted Members in Activity Feed | ✓ | ✓ | ✓ | ✓ |
| Submit Member Restoration Request (Doorbell) | X | ✓ | ✓ | ✓ |
| Review Restoration Requests in Recovery Console | X | X | ✓ | ✓ |
| Approve Restoration (100% Reciprocal Link Recovery) | X | X | ✓ | ✓ |
| Permanently Delete Clan Tree (Irreversible) | X | X | ✓ | X |
| **Privacy Controls & Governance (RA 10173)** | | | | |
| View Masked Age of Living Relatives (~45 yrs) | ✓ | ✓ | ✓ | ✓ |
| View Exact Unmasked Birthdates of Living Relatives | X | X | ✓ | ✓ |
| Toggle 'Conceal Living Birthdates' Privacy Guard | X | X | ✓ | ✓ |
| Toggle Tree Discoverability (Public vs. Private) | X | X | ✓ | ✓ |
| Generate & Invalidate 6-Character Invite Codes | X | X | ✓ | ✓ |
| Promote / Demote Collaborator Roles | X | X | ✓ | ✓ |
| Revoke Collaborator Access & Expel from Clan | X | X | ✓ | ✓ |
| **System Administration & OTA Operations** | | | | |
| Access Citadel SuperAdmin Governance Citadel | X | X | X | ✓ |
| Monitor Real-Time System Health Telemetry | X | X | X | ✓ |
| Dispatch Global Maintenance Broadcast Banners | X | X | X | ✓ |
| Configure Cloud InAppUpdateManager OTA Releases | X | X | X | ✓ |
| Trigger Automated Daily Cloud Firestore Backups | X | X | X | ✓ |

---

### System Role Descriptions and Governance Boundaries

* **a. Viewer (Seedling Role) – Read-Only Lineage Explorer:** Assigned to newly invited clan members, younger family relatives, and temporary visitors holding Reunion Passes. Viewers have read-only access to browse the interactive tree canvas, upward pedigree view, and 200-member horseshoe fan chart. To safeguard living family members from identity exposure, exact birthdates of living relatives are automatically redacted and displayed as an approximate age (e.g., '~45 yrs'). Viewers are restricted from creating, editing, or deleting any member profiles or relationship links.
* **b. Contributor / Editor (Sprout Role) – Active Branch Archivist:** Assigned to trusted family historians, branch keepers, and active relatives who actively document clan history. Contributors possess full permissions to add new relatives, record parent-child associations, enter chronological marriages, attach portrait photographs, and upload historical civil certificates. If a member is soft-deleted, Contributors can submit an on-demand restoration request via the Recent Activities feed. Contributors cannot modify clan privacy policies, grant permissions, or permanently purge records.
* **c. Clan Steward / Owner (Elder Role) – Clan Administrator & Custodian:** Assigned to clan founders, elders, and designated family tree administrators who carry legal and custodial responsibility for the clan workspace. Clan Stewards possess comprehensive administrative authority within their family tree, including generating 6-character invite codes, toggling the 'Conceal Living Birthdates' privacy shield, reviewing and approving soft-deleted member restoration requests in the Recovery Console, promoting or demoting collaborator roles, and requesting clan quota capacity expansions.
* **d. System Administrator (Citadel SuperAdmin) – Platform Operator:** Assigned to technical platform administrators who oversee cross-clan infrastructure health, database capacity, and platform maintenance. System Administrators have no operational need to interfere with private family lineages, but possess full platform-level authority to review clan quota expansion queues, monitor real-time database storage telemetry, broadcast global maintenance announcements, configure cloud InAppUpdateManager version targets for over-the-air updates, and verify daily automated disaster recovery backups.

---

### Architectural Enforcement Mechanisms in Software Implementation

The permission boundaries detailed in the matrix above are enforced at dual layers within the KinTrace software architecture:
1. **Client Interface Level:** UI controls (such as 'Add Member', 'Edit Details', and 'Delete Relative') dynamically adjust their visibility and interactive states based on the user's validated role; any unauthorized event triggers an immediate `ActionRestrictedDialog` advising the user of the required privilege level.
2. **Cloud Backend Level:** Cloud Firestore Security Rules cryptographically validate the user's role claim within Firebase Authentication tokens prior to executing any write, update, or soft-deletion transaction. This dual-layer architecture guarantees that client tampering cannot bypass defined access policies.
