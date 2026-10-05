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

const marketSchema = {
  type: "object",
  additionalProperties: false,
  properties: {
    listings: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          market: { type: "string" },
          title: { type: "string" },
          url: { type: "string" },
          priceKRW: { type: "integer", minimum: 0 },
          condition: { type: "string" },
          matchReason: { type: "string" }
        },
        required: [
          "market",
          "title",
          "url",
          "priceKRW",
          "condition",
          "matchReason"
        ]
      }
    },
    notes: { type: "string" }
  },
  required: ["listings", "notes"]
};

http.createServer(async (req, res) => {

  if (req.method === "GET" && req.url === "/health") {
    return send(res, 200, {
      ok: true,
      service: "pokemon-card-meter-api"
    });
  }

  if (req.method === "POST" && req.url === "/market") {

    if (!process.env.OPENAI_API_KEY) {
      return send(res, 500, {
        error: "OPENAI_API_KEY is not configured"
      });
    }

    let body = "";

    for await (const chunk of req) {
      body += chunk;

      if (body.length > 1000000) {
        return send(res, 413, {
          error: "Payload too large"
        });
      }
    }

    try {

      const input = JSON.parse(body || "{}");

      const cardName =
        String(input.cardName || "").trim();

      const language =
        String(input.language || "").trim();

      const setCode =
        String(input.setCode || "").trim();

      const cardNumber =
        String(input.cardNumber || "").trim();

      const rarity =
        String(input.rarity || "").trim();

      if (!cardName || !cardNumber) {
        return send(res, 400, {
          error: "cardName and cardNumber are required"
        });
      }

      const today =
        new Date().toISOString().slice(0, 10);

      const marketPrompt = `
Search the live web for the current Korean market price
of exactly this physical Pokemon card.

Today is ${today}.

Card identity:
- Card name: ${cardName}
- Language: ${language || "unknown"}
- Set code: ${setCode || "unknown"}
- Card number: ${cardNumber}
- Rarity: ${rarity || "unknown"}

Search priority:
1. Korean second-hand marketplaces such as 중고나라 and 번개장터
2. Korean Pokemon card shops and domestic sales stores
3. Other Korean public sale pages only when useful

Strict matching rules:
- The card number must match exactly.
- The set must match when the set code is available.
- The language must match.
- For Korean cards, exclude Japanese, English and other languages.
- Exclude PSA, BGS, CGC and all graded cards.
- Exclude bundles, lots, decks and sealed products.
- Exclude proxy or custom cards.
- Exclude listings where the price is not for this single card.
- Exclude obvious duplicate listings.
- Do not guess prices.
- If an exact match cannot be verified, omit it.
- Price must be for one raw ungraded card in Korean won.

Return only verified matching listings.
Five to ten results is ideal, but fewer is fine.
If no exact listings are found, return an empty listings array.
`;

      const oa = await fetch(
        "https://api.openai.com/v1/responses",
        {
          method: "POST",

          headers: {
            "authorization":
              `Bearer ${process.env.OPENAI_API_KEY}`,
            "content-type":
              "application/json"
          },

          body: JSON.stringify({

            model:
              process.env.OPENAI_MARKET_MODEL ||
              process.env.OPENAI_MODEL ||
              "gpt-6-luna",

            store: false,

            tools: [
              {
                type: "web_search"
              }
            ],

            input: marketPrompt,

            text: {
              format: {
                type: "json_schema",
                name:
                  "pokemon_card_market_search",
                strict: true,
                schema: marketSchema
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
            "OpenAI market search failed"
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

      const marketResult =
        JSON.parse(text || "{}");

      const listings =
        Array.isArray(marketResult.listings)
          ? marketResult.listings.filter(
              (x) =>
                Number.isInteger(x?.priceKRW) &&
                x.priceKRW > 0
            )
          : [];

      const prices =
        listings
          .map((x) => x.priceKRW)
          .sort((a, b) => a - b);

      const sampleCount = prices.length;

      const minPrice =
        sampleCount
          ? prices[0]
          : 0;

      const maxPrice =
        sampleCount
          ? prices[prices.length - 1]
          : 0;

      const averagePrice =
        sampleCount
          ? Math.round(
              prices.reduce(
                (sum, price) => sum + price,
                0
              ) / sampleCount
            )
          : 0;

      return send(res, 200, {
        ok: true,

        result: {
          cardName,
          language,
          setCode,
          cardNumber,
          rarity,

          currency: "KRW",

          minPrice,
          averagePrice,
          maxPrice,
          sampleCount,

          listings,

          notes:
            String(marketResult.notes || "")
        }
      });

    } catch (e) {

      return send(res, 500, {
        error:
          e?.message ||
          "Market search server error"
      });
    }
  }

  if (
    req.method !== "POST" ||
    req.url !== "/analyze"
  ) {
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

    const input =
      JSON.parse(body || "{}");

    if (!input.imageBase64) {
      return send(res, 400, {
        error: "imageBase64 is required"
      });
    }

    const mime =
      input.mimeType || "image/jpeg";

    const imageUrl =
      String(input.imageBase64)
        .startsWith("data:")
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
          "content-type":
            "application/json"
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
              name:
                "pokemon_card_identification",
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

    const result =
      JSON.parse(text || "{}");

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
