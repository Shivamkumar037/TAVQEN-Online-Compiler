# Online Compiler – React frontend (fixed + WebSocket execution)

This React/Vite project keeps the dark blue, macOS-style 4-state UI:
1. Guest editor
2. Logged-in file explorer + editor
3. Editor + AI panel
4. Explorer + editor + AI panel

The explorer and AI panel can be toggled independently. Mobile uses drawers/full-screen AI.

## Run locally (Windows / macOS / Linux)

Requires Node.js 20.19+ or 22.12+ (Vite 6).

1. Unzip the project and open a terminal **inside** the folder containing `package.json`.
2. Run `npm install` (requires an internet connection the first time).
3. Copy `.env.example` to `.env` (or leave `.env` absent for default proxy settings).
4. Start Spring Boot on port `8080`.
5. Run `npm run dev` and open `http://localhost:5173`.

**If the browser shows only a blue background:** Open F12 → Console and check for errors. Verify that `npm install` finished, that `npm run dev` shows a ready message, and that you opened `http://localhost:5173` rather than opening `index.html` directly. The app includes an error boundary and a startup message to help diagnose failures. If dependencies are stale, delete `node_modules` and reinstall.

## API connections

The existing REST calls use `src/api.js` and the Swagger endpoints you provided:
- Register and login
- Languages (`GET /api/v1/languages`)
- Folder create, read, update, delete
- File create, read, update, delete

The interactive execution WebSocket protocol is in `src/execution.js`:

```
ws://localhost:8080/api/v1/execute
```

In **local Vite development**, requests to `/api` are proxied to Spring Boot `localhost:8080`. Therefore the browser connects to `ws://localhost:5173/api/v1/execute` and Vite forwards that connection to the real `ws://localhost:8080/api/v1/execute`. This is intentional and avoids cross-origin issues.

To connect directly without the Vite proxy, set in `.env`:

```
VITE_API_BASE_URL=http://localhost:8080
VITE_EXECUTION_WS_URL=ws://localhost:8080/api/v1/execute
```

Direct cross-origin access requires proper Spring Boot CORS / WebSocket origin settings. For production, configure a reverse proxy or set the production URLs.

### Execution flow

1. Open a file or select a language.
2. Click **Run**; the client sends `{ "type":"START", "language", "version", "filename", "code" }`.
3. `STATUS`, `OUTPUT`, `EXIT`, `ERROR`, and `TIMEOUT` messages update the console.
4. When status is `RUNNING`, type into the Input tab or the inline stdin field and send `{ "type":"INPUT", "executionId", "data":"...\\n" }`.
5. **Stop** sends `{ "type":"CANCEL", "executionId" }`.

The **open file's extension** controls execution language, e.g. `Main.java` → `java`, `main.py` → `python`, `main.cpp` → `cpp`. Version comes from `GET /api/v1/languages` where available. The six demo versions from the provided WebSocket HTML are fallbacks. Other languages require a backend-reported version. Non-executable files such as `.txt`, `.html`, and `.sql` show a helpful error rather than incorrectly executing as Java.

The UI does not fabricate output; all execution results come from WebSocket messages.

## AI

AI chat and analysis UI are present, but no AI endpoint was provided. These buttons show a configuration message until the AI API is connected.

## Auth notes

The server must return `UserRes.userFolder.id` on successful login/registration. Requests include cookies (`credentials: 'include'`). If your server marks cookies `Secure`, use HTTPS or a compatible localhost setup. There is no logout endpoint in the provided Swagger spec; the frontend logout only resets its local state.

## Tests

Run `npm test` to check extension-based execution routing and the WebSocket protocol. Tests use built-in Node.js test support and do not need backend access.

## Validation limitation

Source files and the WebSocket protocol were syntax/unit tested in this environment. A full `npm install`/browser build could not be completed here because npm registry DNS was unavailable. Please run `npm install && npm run build` locally to verify the complete bundle.


## UI refinements in this update

- The language overflow (three-dot) control is hidden for logged-out visitors and shown after login.
- Analyze is hidden until the user is logged in.
- The execution console now combines stdout, stdin, and stderr in one Terminal view; Details is a separate tab.
- The console has an expand/restore control to give long output more vertical space.
- The desktop explorer uses the available workspace height and scrolls its own tree content.
- The desktop AI panel stays fixed in the workspace while only its message history scrolls; the composer remains anchored at the bottom.


## Latest focused fixes

- Guest mode explicitly hides the language overflow (three-dot) control, file context menus, and Analyze action.
- Legacy file content containing literal `\\n` / `\\"` sequences is normalized when opened so Monaco shows real line breaks and quotes. Saving that opened file sends the corrected editor source.
- The terminal renders an Errors section only when stderr is non-empty; no “No errors reported” placeholder is shown.
- Standard input is hidden until the backend requests it via `INPUT_REQUEST`, `STDIN_REQUEST`, `WAITING_FOR_INPUT`, or a `STATUS` value of `WAITING_FOR_INPUT` / `INPUT_REQUIRED` / `AWAITING_INPUT`. Prompt-like stdout ending in `:` or `?` also reveals the input field. For programs that read stdin without printing a prompt, the backend should emit one of the explicit input-request events/statuses.
- AI chat content remains scrollable while its scrollbar is visually hidden.

## Latest focused fixes

- File updates now send the editor source as raw `text/plain` instead of `JSON.stringify(source)`. This matches a Spring controller that accepts `@RequestBody String` and prevents new saves from storing the source as a quoted, escaped JSON string.
- Legacy records saved by the old JSON-stringified flow are decoded when opened. Normal source strings (including valid escaped `\\n` inside code literals) are left unchanged.
- Monaco now uses an editor model listener with an uncontrolled `defaultValue`; React no longer re-applies the entire editor value on every keystroke. This prevents caret jumps and text being inserted at an unexpected position. When a different file is loaded, the editor model is synchronized from the loaded source.
- The hamburger/File Explorer toggle is rendered only for authenticated users. It is absent from the logged-out header.

Run `npm test` to execute the project tests.

## AI assistant integration

The AI composer sends `POST /api/v1/chat` with JSON `{ "message": "...", "mode": 1 }`.
The custom mode selector maps Ask Anything=1 (default), DSA=2, Code Generator=3,
Error Fixer=4, Dry Run=5, and Code Analyze=6. The editor Analyze action sends the
current editor source as `message` with mode 6. The assistant renders the returned
`message` field as HTML, as required by the backend response contract. Only render
HTML returned by your trusted backend.

On desktop, drag the divider immediately to the left of the AI panel to resize it;
the AI panel is capped at 50% of the workspace width and the editor takes the
remaining width. On mobile, the AI panel remains a full-screen view.
