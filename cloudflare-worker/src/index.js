const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const FCM_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const PUSH_ACCESS_KEY_HEADER = "X-BondMail-Push-Key";
const ALLOWED_INTERVALS = new Set([1, 5, 10, 15, 30, 60]);
const MAX_DUE_DEVICES_PER_TICK = 50;
const STALE_DEVICE_SECONDS = 30 * 24 * 60 * 60;

let cachedGoogleAccessToken = null;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "bondmail-push" });
    }

    if (request.method === "GET" && url.pathname === "/v1/client-config") {
      return clientConfig(request, env);
    }

    if (request.method === "POST" && url.pathname === "/v1/devices/register") {
      return registerDevice(request, env);
    }

    if (request.method === "POST" && url.pathname === "/v1/devices/unregister") {
      return unregisterDevice(request, env);
    }

    return json({ ok: false, error: "not_found" }, 404);
  },

  async scheduled(_controller, env, ctx) {
    ctx.waitUntil(sendScheduledSyncs(env));
  },
};

async function clientConfig(request, env) {
  if (request.headers.has("Origin")) {
    return json({ ok: false, error: "browser_requests_not_allowed" }, 403);
  }
  if (!(await hasValidPushAccessKey(request, env))) {
    return json({ ok: false, error: "invalid_push_access_key" }, 401);
  }

  const config = parseFirebaseClientConfig(env.FIREBASE_CLIENT_CONFIG_JSON);
  const credentials = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  if (config.projectId !== credentials.project_id) {
    throw new Error("Firebase client and service-account projects do not match");
  }
  return json({
    ok: true,
    projectId: config.projectId,
    applicationId: config.applicationId,
    apiKey: config.apiKey,
    senderId: config.senderId,
  });
}

async function registerDevice(request, env) {
  if (request.headers.has("Origin")) {
    return json({ ok: false, error: "browser_requests_not_allowed" }, 403);
  }

  const body = await readJsonBody(request);
  if (!body.ok) return body.response;

  const installationId = normalizedString(body.value.installationId, 36);
  const installationSecret = normalizedString(body.value.installationSecret, 128);
  const fcmToken = normalizedString(body.value.fcmToken, 4096);
  const appVersion = normalizedString(body.value.appVersion, 32);
  const intervalMinutes = Number(body.value.intervalMinutes);
  const enabled = body.value.enabled !== false;

  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(installationId)) {
    return json({ ok: false, error: "invalid_installation_id" }, 400);
  }
  if (installationSecret.length < 32 || !/^[A-Za-z0-9_-]+$/.test(installationSecret)) {
    return json({ ok: false, error: "invalid_installation_secret" }, 400);
  }
  if (fcmToken.length < 80 || !/^[A-Za-z0-9_:\-]+$/.test(fcmToken)) {
    return json({ ok: false, error: "invalid_fcm_token" }, 400);
  }
  if (!ALLOWED_INTERVALS.has(intervalMinutes)) {
    return json({ ok: false, error: "invalid_interval" }, 400);
  }

  const secretHash = await sha256Hex(installationSecret);
  const requiredAccessKeyHash = await configuredAccessKeyHash(env);
  if (!(await hasValidPushAccessKey(request, env, requiredAccessKeyHash))) {
    // A device that replaces or removes a previously valid key must stop receiving immediately.
    // The per-installation secret prevents another client from revoking someone else's record.
    await env.DB.prepare(
      "DELETE FROM devices WHERE installation_id = ?1 AND installation_secret_hash = ?2",
    ).bind(installationId, secretHash).run();
    return json({ ok: false, error: "invalid_push_access_key" }, 401);
  }

  const existing = await env.DB.prepare(
    "SELECT installation_secret_hash FROM devices WHERE installation_id = ?1",
  ).bind(installationId).first();
  if (existing && existing.installation_secret_hash !== secretHash) {
    return json({ ok: false, error: "installation_secret_mismatch" }, 403);
  }

  const now = unixSeconds();
  const nextSyncAt = now + Math.min(intervalMinutes * 60, 30);
  await env.DB.batch([
    env.DB.prepare(
      "DELETE FROM devices WHERE fcm_token = ?1 AND installation_id <> ?2",
    ).bind(fcmToken, installationId),
    env.DB.prepare(
      `INSERT INTO devices (
         installation_id, installation_secret_hash, fcm_token, interval_minutes,
         enabled, next_sync_at, app_version, created_at, updated_at, access_key_hash,
         last_registered_at
       ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8, ?9, ?8)
       ON CONFLICT(installation_id) DO UPDATE SET
         fcm_token = excluded.fcm_token,
         interval_minutes = excluded.interval_minutes,
         enabled = excluded.enabled,
         next_sync_at = MIN(devices.next_sync_at, excluded.next_sync_at),
         app_version = excluded.app_version,
         access_key_hash = excluded.access_key_hash,
         last_registered_at = excluded.last_registered_at,
         updated_at = excluded.updated_at`,
    ).bind(
      installationId,
      secretHash,
      fcmToken,
      intervalMinutes,
      enabled ? 1 : 0,
      nextSyncAt,
      appVersion,
      now,
      requiredAccessKeyHash,
    ),
  ]);

  return json({ ok: true, nextSyncAt });
}

async function unregisterDevice(request, env) {
  const body = await readJsonBody(request);
  if (!body.ok) return body.response;

  const installationId = normalizedString(body.value.installationId, 36);
  const installationSecret = normalizedString(body.value.installationSecret, 128);
  if (!installationId || !installationSecret) {
    return json({ ok: false, error: "invalid_request" }, 400);
  }

  const secretHash = await sha256Hex(installationSecret);
  const result = await env.DB.prepare(
    "DELETE FROM devices WHERE installation_id = ?1 AND installation_secret_hash = ?2",
  ).bind(installationId, secretHash).run();
  return json({ ok: true, removed: result.meta.changes > 0 });
}

async function sendScheduledSyncs(env) {
  const now = unixSeconds();
  const staleResult = await env.DB.prepare(
    "DELETE FROM devices WHERE last_registered_at < ?1",
  ).bind(now - STALE_DEVICE_SECONDS).run();
  if (Number(staleResult.meta?.changes || 0) > 0) {
    console.log("Removed stale FCM device registrations", {
      count: staleResult.meta.changes,
    });
  }

  const accessKeyHash = await configuredAccessKeyHash(env);
  const due = await env.DB.prepare(
    `SELECT installation_id, fcm_token, interval_minutes
       FROM devices
      WHERE access_key_hash = ?1
        AND enabled = 1
        AND next_sync_at <= ?2
      ORDER BY next_sync_at ASC
      LIMIT ?3`,
  ).bind(accessKeyHash, now, MAX_DUE_DEVICES_PER_TICK).all();

  await Promise.all((due.results || []).map((device) => sendToDevice(env, device, now)));
}

async function sendToDevice(env, device, now) {
  try {
    await sendFcmSync(env, device.fcm_token, now);
    await env.DB.prepare(
      `UPDATE devices
          SET next_sync_at = ?2,
              last_sent_at = ?1,
              consecutive_failures = 0,
              updated_at = ?1
        WHERE installation_id = ?3`,
    ).bind(
      now,
      now + Number(device.interval_minutes) * 60,
      device.installation_id,
    ).run();
  } catch (error) {
    if (error instanceof PermanentFcmTokenError) {
      await env.DB.prepare(
        "DELETE FROM devices WHERE installation_id = ?1",
      ).bind(device.installation_id).run();
      return;
    }

    console.error("Scheduled FCM sync failed", {
      installationId: device.installation_id,
      error: safeErrorMessage(error),
    });
    await env.DB.prepare(
      `UPDATE devices
          SET next_sync_at = ?2,
              consecutive_failures = consecutive_failures + 1,
              updated_at = ?1
        WHERE installation_id = ?3`,
    ).bind(now, now + 60, device.installation_id).run();
  }
}

async function sendFcmSync(env, fcmToken, sentAt) {
  const credentials = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  const accessToken = await googleAccessToken(credentials);
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(credentials.project_id)}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: {
            action: "sync",
            reason: "scheduled",
            sentAt: String(sentAt),
          },
          android: {
            // This is a periodic synchronization hint, not a user-visible notification. FCM
            // explicitly deprioritizes repeated HIGH messages that do not immediately display a
            // notification, which eventually made background delivery look as if it had stopped.
            // WorkManager remains the Android-side fallback while NORMAL keeps this traffic within
            // the contract intended for email/data synchronization.
            priority: "NORMAL",
            ttl: "120s",
            collapse_key: "bondmail-scheduled-sync",
            restricted_package_name: "com.bond.mail",
          },
        },
      }),
    },
  );

  if (response.ok) return;

  const payload = await response.text();
  if (
    response.status === 404 ||
    payload.includes("UNREGISTERED") ||
    payload.includes("registration-token-not-registered")
  ) {
    throw new PermanentFcmTokenError();
  }
  throw new Error(`FCM HTTP ${response.status}`);
}

async function googleAccessToken(credentials) {
  const now = unixSeconds();
  if (cachedGoogleAccessToken && cachedGoogleAccessToken.expiresAt > now + 120) {
    return cachedGoogleAccessToken.value;
  }

  const header = base64UrlJson({ alg: "RS256", typ: "JWT" });
  const claims = base64UrlJson({
    iss: credentials.client_email,
    scope: FCM_SCOPE,
    aud: FCM_TOKEN_ENDPOINT,
    iat: now,
    exp: now + 3600,
  });
  const unsignedJwt = `${header}.${claims}`;
  const privateKey = await importPrivateKey(credentials.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(unsignedJwt),
  );
  const assertion = `${unsignedJwt}.${base64UrlBytes(new Uint8Array(signature))}`;

  const response = await fetch(FCM_TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const payloadText = await response.text();
  let payload;
  try {
    payload = JSON.parse(payloadText);
  } catch {
    payload = {};
  }
  if (!response.ok || typeof payload.access_token !== "string") {
    const oauthError = normalizedString(payload.error, 80);
    const description = normalizedString(payload.error_description, 160);
    throw new Error(
      `Google OAuth HTTP ${response.status}` +
        (oauthError ? ` ${oauthError}` : "") +
        (description ? `: ${description}` : ""),
    );
  }

  cachedGoogleAccessToken = {
    value: payload.access_token,
    expiresAt: now + Number(payload.expires_in || 3600),
  };
  return cachedGoogleAccessToken.value;
}

function parseServiceAccount(raw) {
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is not configured");
  const value = JSON.parse(raw);
  if (!value.project_id || !value.client_email || !value.private_key) {
    throw new Error("Firebase service account JSON is incomplete");
  }
  return value;
}

function parseFirebaseClientConfig(raw) {
  if (!raw) throw new Error("FIREBASE_CLIENT_CONFIG_JSON is not configured");
  const value = JSON.parse(raw);
  const projectId = normalizedString(value.projectId, 128);
  const applicationId = normalizedString(value.applicationId, 256);
  const apiKey = normalizedString(value.apiKey, 256);
  const senderId = normalizedString(value.senderId, 64);
  if (!projectId || !applicationId || !apiKey || !/^\d{6,32}$/.test(senderId)) {
    throw new Error("Firebase client config JSON is incomplete");
  }
  return { projectId, applicationId, apiKey, senderId };
}

async function importPrivateKey(pem) {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const bytes = Uint8Array.from(atob(body), (character) => character.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    bytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function readJsonBody(request) {
  if (!request.headers.get("Content-Type")?.toLowerCase().startsWith("application/json")) {
    return { ok: false, response: json({ ok: false, error: "json_required" }, 415) };
  }
  try {
    return { ok: true, value: await request.json() };
  } catch {
    return { ok: false, response: json({ ok: false, error: "invalid_json" }, 400) };
  }
}

function normalizedString(value, maxLength) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function unixSeconds() {
  return Math.floor(Date.now() / 1000);
}

async function configuredAccessKeyHash(env) {
  const value = normalizedString(env.pwd, 512);
  if (!value) throw new Error("Cloudflare secret binding pwd is not configured");
  return sha256Hex(value);
}

async function hasValidPushAccessKey(request, env, configuredHash = null) {
  const suppliedValue = normalizedString(
    request.headers.get(PUSH_ACCESS_KEY_HEADER),
    512,
  );
  const suppliedHash = await sha256Hex(suppliedValue);
  const requiredHash = configuredHash || await configuredAccessKeyHash(env);
  return constantTimeEqual(suppliedHash, requiredHash);
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

function base64UrlJson(value) {
  return base64UrlBytes(new TextEncoder().encode(JSON.stringify(value)));
}

function base64UrlBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function safeErrorMessage(error) {
  return error instanceof Error ? error.message.slice(0, 200) : "Unknown error";
}

class PermanentFcmTokenError extends Error {}
