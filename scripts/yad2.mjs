#!/usr/bin/env node
/**
 * Reads Yad2's vehicle catalog and its for-sale listings, through a real Chrome (see chrome.mjs).
 *
 *   node scripts/yad2.mjs catalog [<manufacturerId>]          → {manufacturer:[…], model:[…]}
 *   node scripts/yad2.mjs listings <manufacturerId> <modelId> <year>
 *                                                             → {url, total, ads:[{price, hand, …}]}
 *   node scripts/yad2.mjs truck-catalog                       → {manufacturer:[…]}
 *   node scripts/yad2.mjs truck-listings <makeId> <fromYear> <toYear>
 *                                                             → {url, total, ads:[{price, model, year, …}]}
 *
 * Trucks are a section of their own: it has a list of makes but no models — a seller types the
 * model as free text ("סומו", "7.5 טון הקפאה") or leaves it out — so truck ads can be asked for
 * by make and years only.
 *
 * JSON on stdout. Exit 2 = Yad2 answered with its bot check instead of data.
 *
 * The catalog comes from Yad2's own gateway (gw.yad2.co.il/vehicles-cars-catalog), fetched from
 * inside a yad2.co.il page so it carries that page's cookies. The listings are the search page's
 * server-rendered data (__NEXT_DATA__ → feed-mix → ads), the same 40-per-page feed a visitor sees.
 */
import { withChrome } from "./chrome.mjs";

const HOME = "https://www.yad2.co.il/vehicles/cars";
const TRUCKS = "https://www.yad2.co.il/vehicles/trucks";

async function feedAds(page) {
  const next = await page.$eval("#__NEXT_DATA__", (el) => el.textContent);
  const queries = JSON.parse(next).props.pageProps.dehydratedState.queries;
  const feed = queries.find((q) => q.queryKey[0] === "feed-mix" || q.queryKey[0] === "feed")?.state?.data;
  if (!feed) throw new Error("no listings feed on the search page");
  const raw = feed.ads ?? ["platinum", "boost", "solo", "commercial", "private"].flatMap((k) => feed[k] ?? []);
  // A promoted ad can appear twice in one feed (its paid slot and its own place).
  const unique = raw.filter((a, i) => !a.token || raw.findIndex((b) => b.token === a.token) === i);
  return { total: feed.pagination?.total ?? unique.length, raw: unique };
}
const [cmd, ...args] = process.argv.slice(2);

class Blocked extends Error {}

async function open(page, url) {
  await page.goto(url, { waitUntil: "networkidle2", timeout: 40_000 });
  const title = await page.title();
  if (!/יד2|yad2/i.test(title)) throw new Blocked(`Yad2 answered "${title}" instead of its page`);
}

try {
  const out = await withChrome(async (page) => {
    if (cmd === "catalog") {
      await open(page, HOME);
      const url = `https://gw.yad2.co.il/vehicles-cars-catalog/${args[0] ? `?manufacturer=${encodeURIComponent(args[0])}` : ""}`;
      const r = await page.evaluate(async (u) => {
        const res = await fetch(u, { credentials: "include" });
        return { status: res.status, body: await res.text() };
      }, url);
      if (r.status !== 200) throw new Blocked(`catalog HTTP ${r.status}`);
      const { data } = JSON.parse(r.body);
      return { manufacturer: data.manufacturer, model: data.model ?? [] };
    }
    if (cmd === "listings") {
      const [manufacturer, model, year] = args;
      const url = `${HOME}?manufacturer=${manufacturer}&model=${model}&year=${year}-${year}`;
      await open(page, url);
      const { total, raw } = await feedAds(page);
      // The feed is padded with recommendations of other cars; keep exactly the asked model and year.
      const ads = raw
        .filter((a) => String(a.manufacturer?.id) === manufacturer && String(a.model?.id) === model && String(a.vehicleDates?.yearOfProduction) === year)
        .map((a) => ({
          price: typeof a.price === "number" ? a.price : null,
          hand: a.hand?.id ?? null,
          subModel: a.subModel?.text ?? null,
          km: a.km ?? null,
          adType: a.adType ?? null,
        }));
      return { url, total, ads };
    }
    if (cmd === "truck-catalog") {
      await open(page, TRUCKS);
      const r = await page.evaluate(async () => {
        const res = await fetch("https://gw.yad2.co.il/vehicles-trucks-catalog/base", { credentials: "include" });
        return { status: res.status, body: await res.text() };
      });
      if (r.status !== 200) throw new Blocked(`truck catalog HTTP ${r.status}`);
      return { manufacturer: JSON.parse(r.body).data.CarSpecialSubCatID };
    }
    if (cmd === "truck-listings") {
      const [make, from, to] = args;
      const url = `${TRUCKS}?CarSpecialSubCatID=${make}&year=${from}-${to}`;
      await open(page, url);
      const { total, raw } = await feedAds(page);
      const ads = raw
        .filter((a) => String(a.specialType?.id) === make)
        .filter((a) => { const y = a.vehicleDates?.yearOfProduction; return y >= Number(from) && y <= Number(to); })
        .map((a) => ({
          price: typeof a.price === "number" ? a.price : null,
          hand: a.hand?.id ?? null,
          subModel: a.specialModel && a.specialModel !== "undefined" ? a.specialModel : null,
          year: a.vehicleDates?.yearOfProduction ?? null,
        }));
      return { url, total, ads };
    }
    throw new Error(`unknown command ${cmd}`);
  });
  process.stdout.write(JSON.stringify(out));
} catch (e) {
  console.error(e.message);
  process.exit(e instanceof Blocked ? 2 : 1);
}
