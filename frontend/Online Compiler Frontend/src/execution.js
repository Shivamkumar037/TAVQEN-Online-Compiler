/**
 * WebSocket protocol taken from the supplied interactive execution HTML.
 * START -> STATUS / OUTPUT / EXIT / ERROR / TIMEOUT, INPUT, CANCEL.
 * This module does not emulate execution; all results come from the server.
 */
export const DEMO_EXECUTION_VERSIONS = Object.freeze({
  java: '15.0.2', python: '3.12.0', c: '10.2.0',
  cpp: '10.2.0', javascript: '18.15.0', go: '1.16.2',
});

export const EXTENSION_TO_EXECUTOR = Object.freeze({
  java: 'java', py: 'python', c: 'c', cpp: 'cpp', js: 'javascript',
  ts: 'typescript', go: 'go', rs: 'rust', php: 'php', rb: 'ruby',
  kt: 'kotlin', swift: 'swift', cs: 'csharp',
});

export function cleanExtension(extension = '') {
  return String(extension || '').trim().replace(/^\./, '').toLowerCase();
}

export function languageIdentifier(name = '') {
  const value = String(name || '').trim().toLowerCase();
  if (value === 'c++') return 'cpp';
  if (value === 'c#') return 'csharp';
  if (value === 'node.js' || value === 'nodejs') return 'javascript';
  return value.replace(/[^a-z0-9]/g, '');
}

export function resolveExecution({ activeFile, selectedLanguage, languages = [] }) {
  const extension = cleanExtension(activeFile?.fileExtension || selectedLanguage?.extension);
  const requested = activeFile
    ? EXTENSION_TO_EXECUTOR[extension]
    : languageIdentifier(selectedLanguage?.name);
  if (!requested) {
    return { error: `.${extension || 'unknown'} files are not executable by the configured compiler.` };
  }
  const lang = languages.find(item =>
    languageIdentifier(item.name) === requested ||
    (cleanExtension(item.extension) === extension && extension !== '')
  );
  // The six versions below are documented in the supplied working HTML example.
  // For any other language, require a version from /api/v1/languages.
  const version = lang?.version || DEMO_EXECUTION_VERSIONS[requested];
  if (!version) return { error: `${requested} execution version is missing from /api/v1/languages.` };
  const baseName = activeFile?.fileName || selectedLanguage?.defaultFileName ||
    ({ java:'Main', javascript:'index' })[requested] || 'main';
  const filename = `${baseName}.${extension || cleanExtension(lang?.extension)}`;
  return { language: requested, version, filename };
}

export function defaultExecutionWsUrl() {
  // Prefer the same backend base URL used by REST in src/api.js.
  // Example: https://api.example.com -> wss://api.example.com/api/v1/execute
  const apiBase = (import.meta.env?.VITE_API_BASE_URL || '').replace(/\/$/, '');
  if (apiBase) {
    const wsBase = apiBase.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
    return `${wsBase}/api/v1/execute`;
  }

  // Backward compatibility for an explicitly configured WebSocket URL.
  const configuredWs = import.meta.env?.VITE_EXECUTION_WS_URL;
  if (configuredWs) return configuredWs;

  // In local development the Vite proxy forwards /api/v1/execute.
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/api/v1/execute`;
}

export function connectExecution({ url, language, version, filename, code, onEvent, onConnection, onError, onClose }) {
  const ws = new WebSocket(url);
  let executionId = null;
  let closedIntentionally = false;
  ws.onopen = () => {
    onConnection?.();
    ws.send(JSON.stringify({ type:'START', language, version, filename, code }));
  };
  ws.onmessage = (event) => {
    let message;
    try { message = JSON.parse(event.data); }
    catch { message = { type:'OUTPUT', stream:'stdout', data:String(event.data) }; }
    if (message.executionId) executionId = message.executionId;
    onEvent?.(message);
  };
  ws.onerror = () => onError?.('WebSocket connection failed. Check the Spring Boot execution endpoint.');
  ws.onclose = (event) => onClose?.(event, closedIntentionally);
  return {
    sendInput(value) {
      if (ws.readyState !== WebSocket.OPEN) return false;
      ws.send(JSON.stringify({ type:'INPUT', executionId, data: String(value).replace(/\n?$/, '\n') }));
      return true;
    },
    stop() {
      if (ws.readyState !== WebSocket.OPEN) return false;
      ws.send(JSON.stringify({ type:'CANCEL', executionId }));
      return true;
    },
    close() {
      closedIntentionally = true;
      if (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN) ws.close();
    },
  };
}

// Compatibility adapter for the existing React component.
// The executor is chosen from the OPEN FILE'S extension, not from an old tab selection.
export function executionLanguage(extension, selectedName, languages = []) {
  const ext = cleanExtension(extension);
  const selectedLanguage = languages.find(item =>
    cleanExtension(item.extension) === ext || languageIdentifier(item.name) === languageIdentifier(selectedName)
  ) || { name: selectedName, extension: ext };
  const result = resolveExecution({
    activeFile: { fileName: ({ java:'Main', js:'index' })[ext] || 'main', fileExtension: ext },
    selectedLanguage,
    languages,
  });
  return result.error ? null : result;
}

export function createExecution({ onMessage, onError, onClose }) {
  let connection = null;
  return {
    start({ language, version, filename, code }) {
      connection = connectExecution({
        url: defaultExecutionWsUrl(), language, version, filename, code,
        onEvent: onMessage, onError, onClose: (event, intentional) => { if (!intentional) onClose?.(event); },
      });
    },
    input(value) {
      if (!connection?.sendInput(value)) throw new Error('No running WebSocket execution to send input to.');
    },
    cancel() {
      if (!connection?.stop()) throw new Error('No running execution to cancel.');
    },
    close() { connection?.close(); },
  };
}
