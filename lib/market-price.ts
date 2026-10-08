import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { runChromeScript } from "./chrome-job";

/**
 * What a model and year is being offered for right now: the asking prices on Yad2.
 *
 * The registry names a model in Latin ("KANGOO") or by a type code ("250"); Yad2's catalog names
 * it in Hebrew ("קנגו", "דוקאטו"). The Hebrew name comes from what the photo lookup already found
 * — the Hebrew Wikipedia title, or the model name Google's results agreed on — and is matched to
 * Yad2's catalog exactly; no match ⇒ no price, never another model's.
 *
 * Asking prices move, so a result is kept for a week, not forever. The catalog is kept a month.
 */
const ROOT = "/var/lib/carcheck/market";
const LISTINGS_TTL_MS = 7 * 24 * 3600_000;
const CATALOG_TTL_MS = 30 * 24 * 3600_000;
/** Below this an ad's price is a placeholder ("1 ₪", a monthly lease), not an asking price. */
const MIN_PRICE = 2_000;

type CatalogEntry = { id: number; title: string; engTitle?: string };
type Catalog = { manufacturer: CatalogEntry[]; model: CatalogEntry[] };
type Ad = { price: number | null; hand: number | null; subModel: string | null };
type Listings = { url: string; total: number; ads: Ad[] };

export type MarketPrice = {
  /** What the prices are of: "רנו קנגו 2017", or for a truck "משאיות איסוזו 2007–2009, כל הדגמים". */
  comparedTo: string;
  /** "model": ads of this exact model and year. "make": a truck — same make and years, any model. */
  scope: "model" | "make";
  /** Ads for this model and year on Yad2 now; prices come from those with a real price. */
  total: number;
  priced: number;
  median: number;
  low: number;
  high: number;
  url: string;
  fetchedAt: string;
};

export function normalize(s: string): string {
  return s.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
}

/** The Yad2 entry whose title is [name] — exactly (normalised), else the only one that starts with it. */
export function findEntry(entries: CatalogEntry[], names: string[]): CatalogEntry | null {
  for (const name of names) {
    const n = normalize(name);
    if (!n) continue;
    const exact = entries.find((e) => normalize(e.title) === n || (e.engTitle && normalize(e.engTitle) === n));
    if (exact) return exact;
    const prefixed = entries.filter((e) => normalize(e.title).startsWith(n) || n.startsWith(normalize(e.title)));
    if (prefixed.length === 1) return prefixed[0];
  }
  return null;
}

/** Median and the middle half's range of the real asking prices; null when there are none. */
export function priceStats(ads: Ad[]): { priced: number; median: number; low: number; high: number } | null {
  const prices = ads.map((a) => a.price).filter((p): p is number => p !== null && p >= MIN_PRICE).sort((a, b) => a - b);
  if (prices.length === 0) return null;
  const at = (q: number) => prices[Math.min(prices.length - 1, Math.max(0, Math.round(q * (prices.length - 1))))];
  // With few ads the quartiles are just the ends; say the full range then.
  const [low, high] = prices.length >= 4 ? [at(0.25), at(0.75)] : [prices[0], prices[prices.length - 1]];
  return { priced: prices.length, median: at(0.5), low, high };
}

async function fresh<T>(file: string, ttl: number, produce: () => Promise<T>): Promise<T> {
  try {
    const stat = await fs.stat(file);
    if (Date.now() - stat.mtimeMs < ttl) return JSON.parse(await fs.readFile(file, "utf8")) as T;
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code !== "ENOENT") throw e;
  }
  const value = await produce();
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(`${file}.tmp`, JSON.stringify(value));
  await fs.rename(`${file}.tmp`, file);
  return value;
}

const catalog = (manufacturerId?: number) =>
  fresh<Catalog>(path.join(ROOT, `catalog-${manufacturerId ?? "all"}.json`), CATALOG_TTL_MS, async () =>
    JSON.parse(await runChromeScript("yad2.mjs", manufacturerId ? ["catalog", String(manufacturerId)] : ["catalog"])) as Catalog,
  );

/**
 * The asking-price summary for [make] and any of [modelNames] in [year], or null when Yad2 has no
 * such model or no priced ad for it. Throws when Yad2 cannot be read. A [truck] that the cars
 * catalog does not know is looked up in the trucks section instead (see [truckPrice]).
 */
export async function marketPrice(make: string, modelNames: string[], year: number, truck: boolean): Promise<MarketPrice | null> {
  const makers = (await catalog()).manufacturer;
  const maker = findEntry(makers, [make]);
  const model = maker ? findEntry((await catalog(maker.id)).model, modelNames) : null;
  if (!maker || !model) return truck ? truckPrice(make, year) : null;
  const key = createHash("sha1").update(`${maker.id}:${model.id}:${year}`).digest("hex").slice(0, 20);
  const listings = await fresh<Listings & { fetchedAt: string }>(path.join(ROOT, `listings-${key}.json`), LISTINGS_TTL_MS, async () => ({
    ...(JSON.parse(await runChromeScript("yad2.mjs", ["listings", String(maker.id), String(model.id), String(year)])) as Listings),
    fetchedAt: new Date().toISOString(),
  }));
  const stats = priceStats(listings.ads);
  if (!stats) return null;
  return { comparedTo: `${maker.title} ${model.title} ${year}`, scope: "model", total: listings.total, ...stats, url: listings.url, fetchedAt: listings.fetchedAt };
}

/** Years either side of a truck's own: truck ads are few, and a truck's generation runs for years. */
const TRUCK_YEARS = 1;

const truckCatalog = () =>
  fresh<{ manufacturer: CatalogEntry[] }>(path.join(ROOT, "truck-catalog.json"), CATALOG_TTL_MS, async () =>
    JSON.parse(await runChromeScript("yad2.mjs", ["truck-catalog"])) as { manufacturer: CatalogEntry[] },
  );

/**
 * Asking prices for trucks of [make] from [year] ± TRUCK_YEARS, any model. Yad2's truck section
 * has no models to match — sellers type them freely or not at all — so this is the closest
 * honest comparison, and it is labelled as covering every model of the make.
 */
async function truckPrice(make: string, year: number): Promise<MarketPrice | null> {
  const maker = findEntry((await truckCatalog()).manufacturer, [make]);
  if (!maker) return null;
  const [from, to] = [year - TRUCK_YEARS, year + TRUCK_YEARS];
  const key = createHash("sha1").update(`truck:${maker.id}:${from}:${to}`).digest("hex").slice(0, 20);
  const listings = await fresh<Listings & { fetchedAt: string }>(path.join(ROOT, `listings-${key}.json`), LISTINGS_TTL_MS, async () => ({
    ...(JSON.parse(await runChromeScript("yad2.mjs", ["truck-listings", String(maker.id), String(from), String(to)])) as Listings),
    fetchedAt: new Date().toISOString(),
  }));
  const stats = priceStats(listings.ads);
  if (!stats) return null;
  return {
    comparedTo: `משאיות ${maker.title} ${from}–${to}, כל הדגמים`,
    scope: "make",
    total: listings.ads.length,
    ...stats,
    url: listings.url,
    fetchedAt: listings.fetchedAt,
  };
}
