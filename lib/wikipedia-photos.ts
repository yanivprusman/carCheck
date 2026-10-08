import fs from "node:fs/promises";
import path from "node:path";
import { cached, photoKey, stagingDir, type PhotoIndex, type StoredPhoto } from "./photo-store";

/**
 * Photos of a model from its Wikipedia article — for vehicles the registry names by a real
 * commercial name ("רנו KANGOO"). Openly licensed, chosen by editors, one or more per
 * generation, and Commons file names start with the years they show, so the photos nearest the
 * car's production year come first. Looked up once per make, name and year (see photo-store).
 *
 * Hebrew Wikipedia's search matches the registry's mix well: "טויוטה COROLLA" finds "טויוטה
 * קורולה". A hit counts only when its title holds the make and its own or its English twin's
 * title holds the model name; otherwise the answer is "no article", never another car's.
 */
const HE_API = "https://he.wikipedia.org/w/api.php";
const EN_API = "https://en.wikipedia.org/w/api.php";
const MAX_PHOTOS = 8;
/** A width Wikimedia pre-renders; arbitrary widths are throttled. */
const THUMB_WIDTH = 500;
/** Wikimedia refuses clients that do not name themselves. */
const UA = "carCheck/1 (+https://ya-niv.com)";

const ACCENTS: Record<string, string> = {
  á: "a", à: "a", ä: "a", â: "a", ã: "a", å: "a", é: "e", è: "e", ë: "e", ê: "e", ě: "e", í: "i", ï: "i", î: "i",
  ó: "o", ö: "o", ô: "o", ø: "o", ú: "u", ü: "u", û: "u", ů: "u", ç: "c", č: "c", š: "s", ž: "z", ř: "r", ñ: "n", ý: "y",
};

/** Lower case, letters and digits only, Latin accents dropped ("Škoda" → "skoda", "I-20" → "i20"). */
export function normalize(s: string): string {
  return [...s.toLowerCase()].map((c) => ACCENTS[c] ?? c).filter((c) => /[\p{L}\p{N}]/u.test(c)).join("");
}

/** "COROLLA CROSS" → ["corollacross", "corolla"]: the full name, then its first word if specific enough. */
export function modelKeys(name: string): string[] {
  const full = normalize(name);
  const first = normalize(name.trim().split(/[ -]/)[0]);
  return [...new Set([full, first])].filter((k) => k && (k === full || k.length >= 3));
}

export type ArticleHit = { heTitle: string; enTitle: string | null };

/** First hit, in search order, that is plainly about this make and model. */
export function pickArticle(hits: ArticleHit[], make: string, name: string): ArticleHit | null {
  const m = normalize(make);
  const keys = modelKeys(name);
  return hits.find((h) => {
    const he = normalize(h.heTitle);
    const en = h.enTitle ? normalize(h.enTitle) : "";
    return he.includes(m) && keys.some((k) => he.includes(k) || en.includes(k));
  }) ?? null;
}

/** 0 when the file's leading years cover [year], else how far off; undated files after all dated ones. */
export function yearDistance(fileTitle: string, year: number | null): number {
  const m = fileTitle.replace(/^File:/, "").match(/^((?:19|20)\d{2})(?:\s*[-–]\s*(\d{4}|\d{2})(?!\d))?/);
  if (!m) return 1000;
  if (year === null) return 0;
  const from = Number(m[1]);
  const to = m[2] ? (m[2].length === 2 ? Math.floor(from / 100) * 100 + Number(m[2]) : Number(m[2])) : from;
  return year < from ? from - year : year > to ? year - to : 0;
}

type FileInfo = { title: string; mime: string; thumbUrl: string; pageUrl: string; width: number; height: number };

const PHOTO_MIMES = new Set(["image/jpeg", "image/png", "image/webp"]);
const NOT_PHOTOS = ["logo", "emblem", "badge", "icon", "map", "diagram", "flag of", "chart"];

/** Photos of the model, nearest to [year] first; files whose names do not mention the model are dropped. */
export function rankFiles(files: FileInfo[], name: string, year: number | null): FileInfo[] {
  const keys = modelKeys(name);
  return files
    .filter((f) => PHOTO_MIMES.has(f.mime))
    .filter((f) => keys.some((k) => normalize(f.title).includes(k)))
    .filter((f) => !NOT_PHOTOS.some((w) => f.title.toLowerCase().includes(w)))
    .sort((a, b) => yearDistance(a.title, year) - yearDistance(b.title, year));
}

export class WikipediaError extends Error {}

async function api(base: string, params: Record<string, string>): Promise<Record<string, unknown>> {
  const url = `${base}?${new URLSearchParams({ format: "json", ...params })}`;
  const r = await fetch(url, { headers: { "User-Agent": UA }, signal: AbortSignal.timeout(15_000) });
  if (!r.ok) throw new WikipediaError(`Wikipedia HTTP ${r.status}`);
  return (await r.json()) as Record<string, unknown>;
}

type Page = { title: string; index?: number; langlinks?: { "*": string }[]; imageinfo?: { mime: string; thumburl: string; descriptionurl: string; thumbwidth: number; thumbheight: number }[] };

function pages(obj: Record<string, unknown>): Page[] {
  const q = obj.query as { pages?: Record<string, Page> } | undefined;
  return Object.values(q?.pages ?? {});
}

/** Stored photos for [make] [name] [year] — an index with no photos when no article fits. */
export function wikipediaPhotos(make: string, name: string, year: number | null) {
  const query = [make, name, year].filter(Boolean).join(" ");
  const key = photoKey("wikipedia", query);
  return cached(key, () => fetchPhotos(key, query, make, name, year));
}

async function fetchPhotos(key: string, query: string, make: string, name: string, year: number | null): Promise<PhotoIndex> {
  const found = await api(HE_API, {
    action: "query", generator: "search", gsrlimit: "5", gsrsearch: `${make} ${name}`, prop: "langlinks", lllang: "en",
  });
  const hits = pages(found)
    .sort((a, b) => (a.index ?? 99) - (b.index ?? 99))
    .map((p) => ({ heTitle: p.title, enTitle: p.langlinks?.[0]?.["*"] ?? null }));
  const hit = pickArticle(hits, make, name);

  const stage = await stagingDir(key);
  try {
    const photos: StoredPhoto[] = [];
    let articleUrl: string | undefined;
    if (hit) {
      // The English article when there is one: it shows every generation, with years in the file names.
      const [base, host, title] = hit.enTitle ? [EN_API, "en", hit.enTitle] : [HE_API, "he", hit.heTitle];
      articleUrl = `https://${host}.wikipedia.org/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`;
      const imgs = await api(base, {
        action: "query", redirects: "1", generator: "images", gimlimit: "max", titles: title,
        prop: "imageinfo", iiprop: "url|mime", iiurlwidth: String(THUMB_WIDTH),
      });
      const files: FileInfo[] = pages(imgs)
        .sort((a, b) => a.title.localeCompare(b.title))
        .flatMap((p) => {
          const ii = p.imageinfo?.[0];
          return ii?.thumburl ? [{ title: p.title, mime: ii.mime, thumbUrl: ii.thumburl, pageUrl: ii.descriptionurl, width: ii.thumbwidth, height: ii.thumbheight }] : [];
        });
      for (const f of rankFiles(files, name, year).slice(0, MAX_PHOTOS)) {
        const r = await fetch(f.thumbUrl, { headers: { "User-Agent": UA }, signal: AbortSignal.timeout(15_000) });
        if (!r.ok) throw new WikipediaError(`Wikimedia HTTP ${r.status} for ${f.title}`);
        const file = `${photos.length + 1}.jpg`;
        await fs.writeFile(path.join(stage.dir, file), Buffer.from(await r.arrayBuffer()));
        photos.push({ file, alt: f.title.replace(/^File:/, ""), width: f.width, height: f.height, type: f.mime, link: f.pageUrl });
      }
    }
    const index: PhotoIndex = { source: "wikipedia", query, fetchedAt: new Date().toISOString(), articleUrl, photos };
    await fs.writeFile(path.join(stage.dir, "index.json"), JSON.stringify(index, null, 2));
    await stage.commit();
    return index;
  } catch (e) {
    await stage.discard();
    throw e;
  }
}
