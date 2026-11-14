# Marcus AI Worker

This Worker is the server-side AI bridge used by the Android app. It keeps the OpenAI API key on Cloudflare; never put that key in Android source code or GitHub.

## Deploy

1. In Cloudflare Dashboard, open **Workers & Pages** and create or open the Worker named `marcus-ai`.
2. Paste the contents of `worker.js` into the Worker editor and deploy.
3. In **Settings → Variables and Secrets**, add a secret named `OPENAI_API_KEY` containing your own OpenAI API key. Do not commit the key.
4. Optionally add a text variable named `OPENAI_MODEL` (default: `gpt-4.1-mini`).
5. To enable saved memory, create a Cloudflare KV namespace and bind it to this Worker with the variable name `MARCUS_MEMORY`. Without KV, chat works but saved memory is not persistent.

The Android client posts JSON to `https://marcus-ai.cbaby8099.workers.dev`:
- Chat: `{ "action": "chat", "userId": "...", "message": "...", "history": ["You: ...", "Marcus: ..."] }`
- Memory: `{ "action": "get_memory", "userId": "..." }`

Responses use `{ "reply": "..." }` or `{ "error": "..." }`.

## Important limits

- This Worker does not make the Android app listen in the background. Background microphone access requires a separate Android foreground service and runtime permissions.
- The current memory implementation stores a short recent-conversation note per app user ID; it is not a full searchable long-term memory system.
- The endpoint is publicly reachable. Before a public release, add abuse controls such as Cloudflare rate limiting and an app-facing authentication design.
