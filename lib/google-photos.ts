import fs from "node:fs/promises";
import path from "node:path";
import { BlockedError, runChromeScript } from "./chrome-job";
import { cached, photoKey, stagingDir, type PhotoIndex } from "./photo-store";

/**
 * Photos of a model from Google Images, for vehicles Wikipedia cannot place — mostly those the
 * registry names only by a type code. One search per query, ever (see photo-store).
 */

export class GoogleCaptchaError extends Error {}

/**
 * The model code as the rest of the world writes it. MAN's registry codes run tonnes and
 * horsepower together with a suffix ("12163LL"); its trucks are sold, listed and photographed
 * as "12.163". Searched as "12163LL" Google finds speakers and fridges; as "12.163", a page of
 * that exact truck (measured 2026-10-08). Other makes' codes are used as the registry has them.
 */
export function searchCode(make: string, code: string): string {
  const man = /^מאן/.test(make) && code.match(/^(\d{1,2})(\d{3})[A-Z]*$/);
  return man ? `${man[1]}.${man[2]}` : code;
}

/** A code specific enough that pages naming it are about this model ("12.163", "NPR75"), unlike "250". */
export function distinctive(code: string): boolean {
  return alnum(code).length >= 4 && /\d/.test(code);
}

/**
 * "<make> <model> <year>", and what kind of vehicle it is only when the model code alone would
 * not pin it down: "פיאט 250 2023" is Fiat 500s and F-250s, "פיאט 250 2023 רכב מסחרי" Ducatos —
 * while "מאן 12.163 2000 משאית" loses every 12.163 that "מאן 12.163 2000" finds.
 */
export function googleQuery(make: string, model: string, year: number | null, kind: string | null): string {
  const code = searchCode(make, model);
  return [make, code, year?.toString(), distinctive(code) ? null : kind].filter(Boolean).join(" ");
}

export function alnum(s: string): string {
  return s.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
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
 * The Google photos worth showing, best first, at most [max]. Each photo comes with the text of
 * the page it is on:
 * 1. Keep only photos whose text names the car's production year.
 * 2. If the model code is distinctive and at least two of those name it, keep only those —
 *    a page that says "MAN 12.163 … 2000" is about this truck.
 * 3. Otherwise find the word most of them share that the query does not already say — the model
 *    name Google resolved the code to ("דוקאטו" for "פיאט 250"). If more than half agree on one,
 *    keep only the photos that carry it; that drops the odd "סקודו" among the Ducatos.
 * Nothing left ⇒ no photos, rather than wrong ones.
 */
export function selectPhotos<T extends { alt: string }>(query: string, code: string, year: number | null, photos: T[], max = 8): T[] {
  const dated = year ? photos.filter((p) => mentionsYear(p.alt, year)) : photos;
  if (dated.length === 0) return [];
  if (distinctive(code)) {
    const key = alnum(code);
    const named = dated.filter((p) => alnum(p.alt).includes(key));
    if (named.length >= 2) return named.slice(0, max);
  }
  const top = agreedWord(query, dated);
  const agreed = top ? dated.filter((p) => tokens(p.alt).includes(top)) : dated;
  return agreed.slice(0, max);
}

/**
 * The word more than half of [photos]' page texts share that [query] does not already say —
 * the model name Google resolved a code to ("דוקאטו" for "פיאט 250 2023 רכב מסחרי"), or null.
 */
export function agreedWord<T extends { alt: string }>(query: string, photos: T[]): string | null {
  const asked = new Set(tokens(query));
  const counts = new Map<string, number>();
  for (const p of photos) {
    for (const t of new Set(tokens(p.alt))) {
      if (asked.has(t) || STOP.has(t) || t.length < 3 || /^\d+$/.test(t)) continue;
      counts.set(t, (counts.get(t) ?? 0) + 1);
    }
  }
  const [top, n] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0] ?? ["", 0];
  return n * 2 > photos.length ? top : null;
}

export function googleImagesUrl(query: string): string {
  return `https://www.google.com/search?udm=2&q=${encodeURIComponent(query)}`;
}

/** Everything the search found (up to 20), stored; selection happens when it is served. */
export function googlePhotos(query: string) {
  const key = photoKey("google", query);
  return cached(key, () => search(key, query));
}

/** Runs scripts/google-images.mjs into a staging folder, then moves it into place. */
async function search(key: string, query: string): Promise<PhotoIndex> {
  const stage = await stagingDir(key);
  let stdout: string;
  try {
    stdout = await runChromeScript("google-images.mjs", [query, stage.dir]);
  } catch (e) {
    await stage.discard();
    if (e instanceof BlockedError) throw new GoogleCaptchaError("Google asked to verify this computer is not a robot");
    throw e;
  }
  const found = JSON.parse(stdout) as Omit<PhotoIndex, "source">;
  const index: PhotoIndex = { source: "google", ...found };
  await fs.writeFile(path.join(stage.dir, "index.json"), JSON.stringify(index, null, 2));
  await stage.commit();
  return index;
}
