#!/usr/bin/env node
/**
 * Copy data.gov.il's main private-car file ("מאגר מספרי רישוי של כלי רכב", ~4.2 M rows,
 * replaced nightly) into this peer's MySQL, so the app can answer while the government
 * datastore is empty — it is, for hours after every upload.
 *
 * Run on demand:  npm run sync-registry   (add --force to reload the same file)
 *
 * The rows come through the datastore API in keyset pages (`_id > last`), the same
 * channel the phone uses. The raw CSV download would be fresher — it exists the moment
 * the ministry uploads it — but it sits behind AWS WAF bot control, which answered
 * every request with `x-amzn-waf-action: challenge` after a few probes (2026-09-28),
 * so a script cannot rely on it. The datastore, then, and only while it is full: a
 * sync during the reload refuses and keeps the copy on hand.
 *
 * The new copy goes into a fresh table, is counted against the datastore's own total,
 * and only then swapped in with one RENAME — the copy being served is always a
 * complete one: the old until the new one is, never a half-loaded one.
 */
import fs from "node:fs";
import path from "node:path";
import mysql from "mysql2/promise";

const RESOURCE = "053cea08-09bc-40ec-8f7a-156f0677aff3";
const COLUMNS = [
  "mispar_rechev", "tozeret_cd", "sug_degem", "tozeret_nm", "degem_cd", "degem_nm", "ramat_gimur",
  "ramat_eivzur_betihuty", "kvutzat_zihum", "shnat_yitzur", "degem_manoa", "mivchan_acharon_dt",
  "tokef_dt", "baalut", "misgeret", "tzeva_cd", "tzeva_rechev", "zmig_kidmi", "zmig_ahori",
  "sug_delek_nm", "horaat_rishum", "moed_aliya_lakvish", "kinuy_mishari",
];
// The file holds every registered private and light commercial vehicle in Israel;
// anything much smaller than that is a truncated copy, not a smaller registry.
const MIN_ROWS = 3_000_000;
const PAGE = 20_000;
const UA = "carCheck-sync/1 (+https://ya-niv.com)";

const force = process.argv.includes("--force");
const t0 = Date.now();
const log = (m) => console.log(`[${((Date.now() - t0) / 1000).toFixed(1)}s] ${m}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function loadEnvLocal() {
  const p = path.join(path.dirname(new URL(import.meta.url).pathname), "..", ".env.local");
  if (!fs.existsSync(p)) throw new Error(`${p} is missing — it holds the carcheck MySQL password`);
  for (const line of fs.readFileSync(p, "utf8").split("\n")) {
    const m = /^([A-Z_]+)=(.*)$/.exec(line.trim());
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2];
  }
}

/** One CKAN action, GET only (POST gets a WAF page). Retries the transient answers a CDN gives. */
async function ckan(action, params, attempts = 6) {
  const url = `https://data.gov.il/api/3/action/${action}?${new URLSearchParams(params)}`;
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url, { headers: { "User-Agent": UA, Accept: "application/json" } });
      const type = res.headers.get("content-type") ?? "";
      if (!res.ok || !type.includes("json")) throw new Error(`${action}: HTTP ${res.status} ${type}`);
      const body = await res.json();
      if (!body.success) throw new Error(`${action}: success=false ${JSON.stringify(body.error ?? "").slice(0, 200)}`);
      return body.result;
    } catch (e) {
      lastErr = e;
      const wait = 2000 * 2 ** i;
      log(`  ${e.message}; retrying in ${wait / 1000}s`);
      await sleep(wait);
    }
  }
  throw lastErr;
}

async function datastoreTotal() {
  const r = await ckan("datastore_search", { resource_id: RESOURCE, limit: 0 });
  return Number(r.total);
}

async function main() {
  loadEnvLocal();
  const resource = await ckan("resource_show", { id: RESOURCE });
  const fileModified = resource.last_modified;
  const total = await datastoreTotal();
  log(`data.gov.il: file uploaded ${fileModified}, datastore holds ${total} rows`);

  const db = await mysql.createConnection({
    host: process.env.DB_HOST ?? "127.0.0.1",
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER ?? "carcheck",
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME ?? "carcheck",
    charset: "utf8mb4",
    dateStrings: true,
  });
  await db.query(`CREATE TABLE IF NOT EXISTS registry_mirror (
    resource_id CHAR(36) NOT NULL PRIMARY KEY,
    file_modified VARCHAR(32) NOT NULL,
    loaded_at DATETIME NOT NULL,
    row_count INT UNSIGNED NOT NULL
  )`);
  const [meta] = await db.query("SELECT file_modified, loaded_at, row_count FROM registry_mirror WHERE resource_id = ?", [RESOURCE]);
  const onHand = meta.length ? `the file of ${meta[0].file_modified} (${meta[0].row_count} rows, loaded ${meta[0].loaded_at})` : null;

  if (total < MIN_ROWS) {
    await db.end();
    throw new Error(
      `the datastore holds ${total} rows right now — data.gov.il is mid-reload. ` +
        (onHand ? `Keeping ${onHand}. ` : "There is no copy yet. ") + "Run again once the table is back.",
    );
  }
  if (meta.length && meta[0].file_modified === fileModified && !force) {
    log(`copy is current: ${onHand}. Nothing to do (--force reloads).`);
    await db.end();
    return;
  }
  log(onHand ? `copy on hand is ${onHand}; replacing` : "no copy yet");

  const textCols = COLUMNS.filter((c) => c !== "mispar_rechev");
  await db.query("DROP TABLE IF EXISTS private_vehicles_new");
  await db.query(`CREATE TABLE private_vehicles_new (
    mispar_rechev INT UNSIGNED NOT NULL,
    ${textCols.map((c) => `${c} VARCHAR(255) NULL`).join(",\n    ")}
  ) CHARACTER SET utf8mb4`);

  log(`copying ${total} rows in pages of ${PAGE}`);
  const insertSql = `INSERT INTO private_vehicles_new (${COLUMNS.join(", ")}) VALUES ?`;
  let lastId = 0;
  let n = 0;
  let nextMark = 500_000;
  for (;;) {
    // SELECT *: naming the 23 columns makes a URL the WAF answers with a 404 page (measured).
    const sql = `SELECT * FROM "${RESOURCE}" WHERE _id > ${lastId} ORDER BY _id LIMIT ${PAGE}`;
    const r = await ckan("datastore_search_sql", { sql });
    const records = r.records ?? [];
    if (records.length === 0) break;
    if (n === 0) {
      const missing = COLUMNS.filter((c) => !(c in records[0]));
      if (missing.length) throw new Error(`the datastore's columns changed — missing ${missing.join(", ")}; the table definition needs updating first`);
    }
    const values = records.map((rec) =>
      COLUMNS.map((c) => {
        const v = rec[c];
        if (v === null || v === undefined) return null;
        const s = String(v).trim();
        if (c === "mispar_rechev") return Number(s);
        return s === "" ? null : s;
      }),
    );
    await db.query(insertSql, [values]);
    n += records.length;
    lastId = Number(records[records.length - 1]._id);
    if (n >= nextMark) { log(`  ${n} rows`); nextMark += 500_000; }
  }
  log(`copied ${n} rows`);

  // The same file all along, and all of it: the datastore fills row by row for hours after
  // an upload (measured: 4,173,696 → 4,181,617 within a minute), so a count that moved
  // during the copy means the copy is of a table that was still being loaded.
  const after = await ckan("resource_show", { id: RESOURCE });
  const totalAfter = await datastoreTotal();
  if (after.last_modified !== fileModified || totalAfter !== total || n < MIN_ROWS || n < total * 0.98) {
    await db.query("DROP TABLE private_vehicles_new");
    await db.end();
    throw new Error(
      `copy is not whole: ${n} rows copied, datastore ${total} → ${totalAfter}, file ${fileModified} → ${after.last_modified}` +
        ` — data.gov.il was still loading. ` + (onHand ? `Keeping ${onHand}.` : "No copy.") + " Run again in a while.",
    );
  }
  log("indexing by plate");
  await db.query("ALTER TABLE private_vehicles_new ADD INDEX idx_plate (mispar_rechev)");

  // The swap: one statement, so a lookup sees either the old copy or the new, never neither.
  const [existing] = await db.query("SHOW TABLES LIKE 'private_vehicles'");
  if (existing.length) {
    await db.query("RENAME TABLE private_vehicles TO private_vehicles_old, private_vehicles_new TO private_vehicles");
    await db.query("DROP TABLE private_vehicles_old");
  } else {
    await db.query("RENAME TABLE private_vehicles_new TO private_vehicles");
  }
  await db.query(
    `INSERT INTO registry_mirror (resource_id, file_modified, loaded_at, row_count) VALUES (?, ?, NOW(), ?)
     ON DUPLICATE KEY UPDATE file_modified = VALUES(file_modified), loaded_at = NOW(), row_count = VALUES(row_count)`,
    [RESOURCE, fileModified, n],
  );
  await db.end();
  log(`done: serving the file of ${fileModified}, ${n} rows`);
}

main().catch((e) => {
  console.error(`sync-registry failed: ${e.message}`);
  process.exit(1);
});
