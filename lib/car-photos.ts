import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

/**
 * Google Images photos of a model, searched once and kept on this peer's disk.
 *
 * The phone asks with the query it built ("פיאט 250 2023 רכב מסחרי"); the first ask runs
 * scripts/google-images.mjs and saves what it finds under PHOTO_ROOT/<key>/, and every later
 * ask — any plate of the same model and year — is answered from there without touching Google.
 * A search that found nothing is kept too, so it is not repeated either.
 */
export const PHOTO_ROOT = "/var/lib/carcheck/photos";

export type PhotoIndex = {
  query: string;
  fetchedAt: string;
  photos: { file: string; alt: string; width: number; height: number; type: string }[];
};

export class GoogleCaptchaError extends Error {}

/** Same query, same folder: case and spacing do not make a second search. */
export function photoKey(query: string): string {
  const norm = query.trim().replace(/\s+/g, " ").toLowerCase();
  return createHash("sha1").update(norm).digest("hex").slice(0, 20);
}

const inFlight = new Map<string, Promise<PhotoIndex>>();
/** One Chrome at a time: searches queue rather than open several browsers at once. */
let queue: Promise<unknown> = Promise.resolve();

export async function photosFor(query: string): Promise<{ key: string; index: PhotoIndex; cached: boolean }> {
  const key = photoKey(query);
  const dir = path.join(PHOTO_ROOT, key);
  const saved = await readIndex(dir);
  if (saved) return { key, index: saved, cached: true };
  let p = inFlight.get(key);
  if (!p) {
    p = (queue = queue.catch(() => undefined).then(() => search(query, dir))) as Promise<PhotoIndex>;
    inFlight.set(key, p);
    p.finally(() => inFlight.delete(key)).catch(() => undefined);
  }
  return { key, index: await p, cached: false };
}

async function readIndex(dir: string): Promise<PhotoIndex | null> {
  try {
    return JSON.parse(await fs.readFile(path.join(dir, "index.json"), "utf8")) as PhotoIndex;
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return null;
    throw e;
  }
}

/**
 * Runs the search in a transient systemd scope under xvfb-run: Chrome cannot start its thread
 * pool inside the app service's task limit, and needs a display it will not find on a server.
 * Saves into a temp folder renamed into place, so a half-written search is never served.
 */
async function search(query: string, dir: string): Promise<PhotoIndex> {
  const tmp = `${dir}.tmp-${process.pid}-${Date.now()}`;
  await fs.mkdir(PHOTO_ROOT, { recursive: true });
  const script = path.join(process.cwd(), "scripts", "google-images.mjs");
  const { code, stdout, stderr } = await run("systemd-run", [
    "--scope", "--quiet", "--collect", "--property=TasksMax=4096", "--property=MemoryMax=2G",
    "xvfb-run", "-a", "-s", "-screen 0 1366x1800x24",
    process.execPath, script, query, tmp,
  ]);
  if (code === 2) {
    await fs.rm(tmp, { recursive: true, force: true });
    throw new GoogleCaptchaError("Google asked to verify this computer is not a robot");
  }
  if (code !== 0) {
    await fs.rm(tmp, { recursive: true, force: true });
    throw new Error(`google-images exited ${code}: ${stderr.trim().slice(-300)}`);
  }
  await fs.rename(tmp, dir);
  return JSON.parse(stdout) as PhotoIndex;
}

function run(cmd: string, args: string[]): Promise<{ code: number | null; stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "", stderr = "";
    child.stdout.on("data", (d) => (stdout += d));
    child.stderr.on("data", (d) => (stderr += d));
    const timer = setTimeout(() => child.kill("SIGKILL"), 90_000);
    child.on("error", reject);
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve({ code, stdout, stderr });
    });
  });
}

/** Words that say nothing about which model a photo shows: listing and site boilerplate. */
const STOP = new Set([
  "יד", "שניה", "שנייה", "למכירה", "מחירון", "ומפרט", "מפרט", "חוות", "דעת", "מידע", "טכני", "מקיף", "ומקצועי",
  "חדשות", "רכב", "רכבי", "מסחרי", "מסחרית", "משאית", "משאיות", "אוטובוס", "אופנוע", "דגם", "מודל", "שנת", "של",
  "עם", "על", "גם", "בכל", "יום", "אתר", "for", "sale", "the", "and", "photos", "info", "price", "review", "specs",
]);

function tokens(s: string): string[] {
  return s.toLowerCase().split(/[^\p{L}\p{N}.]+/u).map((t) => t.replace(/^\.+|\.+$/g, "")).filter(Boolean);
}

/** True when [alt] names [year] itself, or a year range ("2019-2023", "2019–23") that covers it. */
export function mentionsYear(alt: string, year: number): boolean {
  for (const m of alt.matchAll(/(?<![\p{L}\p{N}])((?:19|20)\d{2})(?:\s*[-–]\s*((?:19|20)?\d{2}))?(?![\p{L}\p{N}])/gu)) {
    const from = Number(m[1]);
    const to = m[2] ? (m[2].length === 2 ? Math.floor(from / 100) * 100 + Number(m[2]) : Number(m[2])) : from;
    if (year >= from && year <= to) return true;
  }
  return false;
}

/**
 * The photos worth showing for [query], best first, at most [max].
 *
 * Google's grid mixes in whatever is near the query: newer generations of the make, a sibling
 * model, a listings page's other trucks. Each photo comes with the text of the page it is on, so:
 * 1. Keep only photos whose text names the car's production year (the year in the query).
 * 2. Among those, find the word most of them share that the query does not already say — the
 *    model name Google resolved the code to ("דוקאטו" for "פיאט 250"). If more than half agree on
 *    one, keep only the photos that carry it; that drops the odd "סקודו" among the Ducatos.
 * No year in the query ⇒ step 1 is skipped. Nothing left ⇒ no photos, rather than wrong ones.
 */
export function selectPhotos<T extends { alt: string }>(query: string, photos: T[], max = 8): T[] {
  const yearMatch = query.match(/(?<!\d)(19|20)\d{2}(?!\d)/);
  const dated = yearMatch ? photos.filter((p) => mentionsYear(p.alt, Number(yearMatch[0]))) : photos;
  if (dated.length === 0) return [];
  const asked = new Set(tokens(query));
  const counts = new Map<string, number>();
  for (const p of dated) {
    for (const t of new Set(tokens(p.alt))) {
      if (asked.has(t) || STOP.has(t) || t.length < 3 || /^\d+$/.test(t)) continue;
      counts.set(t, (counts.get(t) ?? 0) + 1);
    }
  }
  const [top, n] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0] ?? ["", 0];
  const agreed = n * 2 > dated.length ? dated.filter((p) => tokens(p.alt).includes(top)) : dated;
  return agreed.slice(0, max);
}
