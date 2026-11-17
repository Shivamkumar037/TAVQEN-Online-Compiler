# Requested workspace fixes

- Selecting a folder now marks it active; Explorer New Folder/New File actions create inside that folder, defaulting to the workspace root when no folder is selected.
- Added a VS Code-style horizontally scrollable open-file tab strip with a fixed plus button on the left and close controls on each tab.
- HTML files now open in an isolated browser preview when Run is clicked, with Expand/Restore and Back to Code controls.
- Added session restoration through `GET /api/v1/users/me` using the existing HttpOnly cookie. No JWT is stored in localStorage. LocalStorage stores only UI preferences, open file IDs, and unsaved editor drafts.
- Added a Settings entry with Midnight, Daylight, Ocean, and Amethyst themes.
- REST endpoints use the single `VITE_API_BASE_URL` setting in `src/api.js`; the execution WebSocket URL is derived from the same setting when it is configured.
- Added `GET /api/v1/users/me` and `POST /api/v1/users/logout` frontend API methods.

The existing execution and code-content unit tests pass. A Vite production build could not be run in this environment because dependency installation did not complete.
