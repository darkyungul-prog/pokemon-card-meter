const http = require("node:http");

const port = process.env.PORT || 10000;

const send = (res, code, obj) => {
  res.writeHead(code, {
    "content-type": "application/json; charset=utf-8"
  });
  res.end(JSON.stringify(obj));
};

const schema = {
  type: "object",
  additionalProperties: false,
  properties: {
    cardName: { type: "string" },
    language: { type: "string" },
    setCode: { type: "string" },
    cardNumber: { type: "string" },
    hp: { type: "string" },
    rarity: { type: "string" },
    confidence: {
      type: "integer",
      minimum: 0,
      maximum: 100
    },
    notes: { type: "string" }
  },
  required: [
    "cardName",
    "language",
    "setCode",
    "cardNumber",
    "hp",
    "rarity",
    "confidence",
    "notes"
  ]
};

http.createServer(async (req, res) => {

  if (req.method === "GET" && req.url === "/health") {
    return send(res, 200, {
      ok: true,
      service: "pokemon-card-meter-api"
    });
  }

  if (req.method !== "POST" || req.url !== "/analyze") {
    return send(res, 404, {
      error: "Not found"
    });
  }

  if (!process.env.OPENAI_API_KEY) {
    return send(res, 500, {
      error: "OPENAI_API_KEY is not configured"
    });
  }

  let body = "";

  for await (const chunk of req) {
    body += chunk;

    if (body.length > 15000000) {
      return send(res, 413, {
        error: "Payload too large"
      });
    }
  }

  try {

    const input = JSON.parse(body || "{}");

    if (!input.imageBase64) {
      return send(res, 400, {
        error: "imageBase64 is required"
      });
    }

    const mime = input.mimeType || "image/jpeg";

    const imageUrl = String(input.imageBase64).startsWith("data:")
      ? input.imageBase64
      : `data:${mime};base64,${input.imageBase64}`;

    const prompt =
      "Identify the Pokemon trading card in the photo. Read only the printed card. Return exact visible values. Preserve Korean card names when readable. Keep card number slash format such as 018/098. If a field is unclear, return an empty string rather than guessing. Confidence is 0-100.";

    const oa = await fetch(
      "https://api.openai.com/v1/responses",
      {
        method: "POST",

        headers: {
          "authorization":
            `Bearer ${process.env.OPENAI_API_KEY}`,
          "content-type": "application/json"
        },

        body: JSON.stringify({
          model:
            process.env.OPENAI_MODEL ||
            "gpt-6-luna",

          store: false,

          input: [
            {
              role: "user",
              content: [
                {
                  type: "input_text",
                  text: prompt
                },
                {
                  type: "input_image",
                  image_url: imageUrl,
                  detail: "high"
                }
              ]
            }
          ],

          text: {
            format: {
              type: "json_schema",
              name: "pokemon_card_identification",
              strict: true,
              schema
            }
          }
        })
      }
    );

    const data = await oa.json();

    if (!oa.ok) {
      return send(res, oa.status, {
        error:
          data?.error?.message ||
          "OpenAI request failed"
      });
    }

    let text = "";

    for (const item of (data.output || [])) {
      for (const c of (item.content || [])) {
        if (c.type === "output_text") {
          text += c.text || "";
        }
      }
    }

    const result = JSON.parse(text || "{}");

    return send(res, 200, {
      ok: true,
      result
    });

  } catch (e) {

    return send(res, 500, {
      error:
        e?.message ||
        "Server error"
    });
  }

}).listen(
  port,
  "0.0.0.0",
  () => {
    console.log(
      "pokemon-card-meter-api listening",
      port
    );
  }
);
