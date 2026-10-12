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
  if (man) return `${man[1]}.${man[2]}`;
  // MAN's TG range runs series, tonnes, horsepower, axles and options together:
  // "TGS354308X4BBNNME6" is a TGS 35.430 8x4, "TGE51804X2SB…" a TGE 5.180. Searched whole it finds
  // nothing of its own; as "TGS 35.430", pages about that truck.
  // Undotted, the axle count ("4X2") is what tells where the tonnes end: "TGE51804X2" is 5 + 180.
  const tg = /^מאן/.test(make) && (code.match(/^(TG[A-Z])(\d{1,2})(\d{3})\dX\d/) || code.match(/^(TG[A-Z])\s*(\d{1,2})\.(\d{3})/));
  if (tg) return `${tg[1]} ${tg[2]}.${tg[3]}`;
  // Hino's chassis codes end in a cab letter the world drops: "XJC740L" is listed as "XJC740".
  const hino = /^הינו/.test(make) && code.match(/^([A-Z]{3}\d{3})[LR]$/);
  if (hino) return hino[1];
  // A model name run together with a type number: Mercedes' "ANTOS963020" is an Antos, type
  // 963.020. Searched whole, Google matches nothing and returns any 2018 Mercedes — sedans, for a
  // 26-tonne truck (measured 2026-10-12). The name is what its pages carry.
  const named = code.match(/^([A-Z]{3,})\d{4,}$/);
  return named ? named[1] : code;
}

/**
 * What a page naming this model contains, squeezed like alnum: the code without a trailing letter
 * suffix when that leaves a code still worth matching — Iveco's "50C15V" is listed as "50C15" and
 * "50 C 15"; "NPR75" and "ANTOS" stay as they are.
 */
export function nameKey(code: string): string {
  const key = alnum(code);
  const bare = key.replace(/[a-z]+$/, "");
  return bare.length >= 4 && /\d/.test(bare) ? bare : key;
}

/** A code specific enough that pages naming it are about this model ("12.163", "NPR75"), unlike "250". */
export function distinctive(code: string): boolean {
  return alnum(code).length >= 4 && /\d/.test(code);
}

/** A model name rather than a code ("ANTOS", "DAILY"): it pins the model down by itself. */
export function isModelName(code: string): boolean {
  return /^[A-Z]{4,}$/i.test(code);
}

/**
 * "<make> <model> <year>", and what kind of vehicle it is only when the model code alone would
 * not pin it down: "פיאט 250 2023" is Fiat 500s and F-250s, "פיאט 250 2023 רכב מסחרי" Ducatos —
 * while "מאן 12.163 2000 משאית" loses every 12.163 that "מאן 12.163 2000" finds, and "מרצדס בנץ
 * ANTOS 2018 משאית" turns up Hebrew pages about every Mercedes truck and a Sprinter, where without
 * it nineteen of twenty are 2018 Antoses (measured 2026-10-12).
 */
export function googleQuery(make: string, model: string, year: number | null, kind: string | null): string {
  const code = searchCode(make, model);
  return [make, code, year?.toString(), distinctive(code) || isModelName(code) ? null : kind].filter(Boolean).join(" ");
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
 *    The same holds for a model name the query carries ("ANTOS").
 * 3. Otherwise find the word most of them share that the query does not already say — the model
 *    name Google resolved the code to ("דוקאטו" for "פיאט 250"). If more than half agree on one,
 *    keep only the photos that carry it; that drops the odd "סקודו" among the Ducatos.
 * 4. A code pages would name, that none of them do, with no model they agree on: the pages are
 *    about the make, not this vehicle — Mercedes sedans for an Antos truck. No photos.
 * Nothing left ⇒ no photos, rather than wrong ones.
 */
export function selectPhotos<T extends { alt: string }>(query: string, code: string, year: number | null, photos: T[], max = 8): T[] {
  const dated = year ? photos.filter((p) => mentionsYear(p.alt, year)) : photos;
  if (dated.length === 0) return [];
  const key = nameKey(code);
  const nameable = key.length >= 4;
  if (nameable) {
    const named = dated.filter((p) => alnum(p.alt).includes(key));
    if (named.length >= 2) return named.slice(0, max);
  }
  const top = agreedWord(query, dated);
  if (top) return dated.filter((p) => tokens(p.alt).includes(top)).slice(0, max);
  return nameable ? [] : dated.slice(0, max);
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
