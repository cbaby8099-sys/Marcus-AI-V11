const SYSTEM_PROMPT = `You are Marcus, a capable, clear, helpful personal AI assistant. Be accurate, explain uncertainty, and never claim to have performed an action you did not perform. Keep responses concise unless detail is useful. Do not expose server secrets.`;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders });
}

function safeHistory(value) {
  if (!Array.isArray(value)) return [];
  return value.slice(-12).map((entry) => {
    if (typeof entry !== "string") return null;
    const line = entry.slice(0, 2000);
    if (line.startsWith("You:")) return { role: "user", content: line.slice(4).trim() };
    if (line.startsWith("Marcus:")) return { role: "assistant", content: line.slice(7).trim() };
    return null;
  }).filter(Boolean);
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders });
    }
    if (request.method !== "POST") return json({ error: "Use POST with a JSON request." }, 405);
    if (!env.OPENAI_API_KEY) return json({ error: "Server is missing OPENAI_API_KEY. Add it as a Worker secret." }, 503);

    let body;
    try {
      const raw = await request.text();
      if (raw.length > 16000) return json({ error: "Request is too large." }, 413);
      body = JSON.parse(raw);
    } catch {
      return json({ error: "Request body must be valid JSON." }, 400);
    }

    const userId = typeof body.userId === "string" ? body.userId.slice(0, 80) : "";
    if (!/^[a-zA-Z0-9_-]{8,80}$/.test(userId)) return json({ error: "A valid userId is required." }, 400);

    if (body.action === "get_memory") {
      if (!env.MARCUS_MEMORY) return json({ reply: "Memory storage is not configured yet. Chat still works; add a Cloudflare KV binding named MARCUS_MEMORY to enable saved memory." });
      const saved = await env.MARCUS_MEMORY.get(userId);
      return json({ reply: saved || "I don't have any saved memory for you yet." });
    }

    if (body.action !== "chat") return json({ error: "Unknown action." }, 400);
    const message = typeof body.message === "string" ? body.message.trim().slice(0, 4000) : "";
    if (!message) return json({ error: "Message cannot be empty." }, 400);

    const history = safeHistory(body.history);
    const input = [
      ...history,
      { role: "user", content: message },
    ];

    try {
      const response = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-4.1-mini",
          instructions: SYSTEM_PROMPT,
          input,
          max_output_tokens: 700,
        }),
      });

      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        const detail = payload?.error?.message || `AI provider returned HTTP ${response.status}`;
        return json({ error: detail }, 502);
      }

      const reply = (payload.output || [])
        .flatMap((item) => item.content || [])
        .filter((part) => part.type === "output_text")
        .map((part) => part.text)
        .join("\n")
        .trim();

      if (!reply) return json({ error: "The AI returned an empty response. Please try again." }, 502);

      if (env.MARCUS_MEMORY) {
        const memoryNote = `Last conversation updated: ${new Date().toISOString()}\nRecent user message: ${message.slice(0, 500)}\nMarcus response: ${reply.slice(0, 700)}`;
        await env.MARCUS_MEMORY.put(userId, memoryNote, { expirationTtl: 60 * 60 * 24 * 90 });
      }
      return json({ reply });
    } catch (error) {
      return json({ error: "Could not reach the AI service. Check Worker logs and try again." }, 502);
    }
  },
};
