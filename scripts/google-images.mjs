#!/usr/bin/env node
/**
 * One Google Images search, the top photos saved to a directory.
 *
 *   node scripts/google-images.mjs "<query>" <out-dir>
 *
 * Writes <out-dir>/1.jpg … and <out-dir>/index.json, and prints that index to stdout.
 * Exit 2 = Google answered with its "unusual traffic" page instead of results.
 *
 * Google refuses anything that is not a full browser: a plain HTTP fetch gets "your browser
 * is no longer supported", and a puppeteer-launched Chrome (headless or not) is sent to
 * /sorry on the first query. An ordinary Chrome, started as a normal process and only read
 * over DevTools, gets the real page (measured 2026-10-08). So that is what this does — on an
 * Xvfb display (the caller wraps it in xvfb-run), with its own persistent profile, never the
 * user's browser.
 *
 * It runs once per model and year: the server keeps what it saves and never searches the
 * same query again.
 */
import { spawn } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import puppeteer from "puppeteer-core";

const MAX_PHOTOS = 20;
const PROFILE = "/var/lib/carcheck/chrome-profile";

const [query, outDir] = process.argv.slice(2);
if (!query || !outDir) {
  console.error('usage: google-images.mjs "<query>" <out-dir>');
  process.exit(64);
}

function freePort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => {
      const { port } = s.address();
      s.close(() => resolve(port));
    });
    s.on("error", reject);
  });
}

async function connect(port) {
  const deadline = Date.now() + 15_000;
  for (;;) {
    try {
      return await puppeteer.connect({ browserURL: `http://127.0.0.1:${port}`, defaultViewport: null });
    } catch (e) {
      if (Date.now() > deadline) throw new Error(`Chrome did not open DevTools on ${port}: ${e.message}`);
      await new Promise((r) => setTimeout(r, 300));
    }
  }
}

fs.mkdirSync(PROFILE, { recursive: true });
const port = await freePort();
const chrome = spawn(
  "/usr/bin/google-chrome",
  [
    "--no-sandbox", // runs as root
    `--user-data-dir=${PROFILE}`,
    `--remote-debugging-port=${port}`,
    "--no-first-run",
    "--no-default-browser-check",
    "--window-size=1366,1800",
    "about:blank",
  ],
  { stdio: "ignore" },
);

let browser;
try {
  browser = await connect(port);
  const [page] = await browser.pages();
  await page.goto(`https://www.google.com/search?udm=2&hl=iw&q=${encodeURIComponent(query)}`, {
    waitUntil: "networkidle2",
    timeout: 30_000,
  });
  if (page.url().includes("/sorry/")) {
    console.error("google-captcha: Google answered with its unusual-traffic page");
    process.exitCode = 2;
  } else {
    // The result grid, in Google's order. Landscape photos only: a car photo is wider than tall,
    // and that one rule drops the toy-car blister packs, portraits and parts shots Google mixes in.
    const shots = await page.$$eval("img", async (els, max) => {
      const out = [];
      const seen = new Set(); // Google repeats a photo it found on several pages
      for (const img of els) {
        if (out.length >= max) break;
        const w = img.naturalWidth, h = img.naturalHeight;
        const src = img.currentSrc || img.src;
        if (w < 160 || h < 100 || w / h < 1.1 || w / h > 2.4) continue;
        if (!src.startsWith("data:image/") && !src.includes("gstatic.com/images")) continue;
        if (seen.has(src)) continue;
        seen.add(src);
        const r = await fetch(src);
        const bytes = new Uint8Array(await r.arrayBuffer());
        let bin = "";
        for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
        out.push({ alt: img.alt || "", width: w, height: h, type: r.headers.get("content-type") || src.slice(5, src.indexOf(";")), b64: btoa(bin) });
      }
      return out;
    }, MAX_PHOTOS);

    fs.mkdirSync(outDir, { recursive: true });
    // The same photo can come back under two thumbnail URLs; identical bytes are one photo.
    const unique = shots.filter((s, i) => shots.findIndex((o) => o.b64 === s.b64) === i);
    const photos = unique.map((s, i) => {
      const file = `${i + 1}.jpg`;
      fs.writeFileSync(path.join(outDir, file), Buffer.from(s.b64, "base64"));
      return { file, alt: s.alt, width: s.width, height: s.height, type: s.type };
    });
    const index = { query, fetchedAt: new Date().toISOString(), photos };
    fs.writeFileSync(path.join(outDir, "index.json"), JSON.stringify(index, null, 2));
    process.stdout.write(JSON.stringify(index));
  }
} finally {
  if (browser) await browser.disconnect();
  chrome.kill();
}
