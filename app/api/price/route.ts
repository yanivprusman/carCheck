import { NextRequest, NextResponse } from "next/server";
import { BlockedError } from "@/lib/chrome-job";
import { agreedWord, googlePhotos, googleQuery, mentionsYear } from "@/lib/google-photos";
import { marketPrice } from "@/lib/market-price";
import { wikipediaPhotos } from "@/lib/wikipedia-photos";

export const dynamic = "force-dynamic";

/**
 * Asking prices on Yad2 for the model and year: `?make=&model=&name=&year=&kind=`, the same
 * parameters as /api/photos — whose lookups (kept on disk) supply the model's Hebrew name:
 * the Hebrew Wikipedia title when there is an article, else the name Google's results agreed on.
 *
 *   {found: true, comparedTo, scope, total, priced, median, low, high, url, fetchedAt}
 *   {found: false, reason, tried}
 */
export async function GET(req: NextRequest) {
  const sp = req.nextUrl.searchParams;
  const make = sp.get("make")?.trim() ?? "";
  const model = sp.get("model")?.trim() || null;
  const name = sp.get("name")?.trim() || null;
  const yearText = sp.get("year")?.trim() ?? "";
  const kind = sp.get("kind")?.trim() || null;
  if (!make || !(name || model) || !/^\d{4}$/.test(yearText)) {
    return NextResponse.json({ error: "make, model or name, and year are required" }, { status: 400 });
  }
  const year = Number(yearText);
  try {
    const names: string[] = [];
    if (name) {
      names.push(name);
      const { index } = await wikipediaPhotos(make, name, year);
      if (index.heTitle) names.push(index.heTitle.replace(make, "").trim());
    }
    if (names.length < 2) {
      const query = googleQuery(make, name ?? model!, year, kind);
      const { index } = await googlePhotos(query);
      const agreed = agreedWord(query, index.photos.filter((p) => mentionsYear(p.alt, year)));
      if (agreed) names.push(agreed);
    }
    if (model) names.push(model);

    // A vehicle the phone calls a truck or a van over 3.5 t may only be listed in Yad2's truck section.
    const truck = kind === "משאית" || kind === "רכב מסחרי";
    const price = await marketPrice(make, names, year, truck);
    if (!price) return NextResponse.json({ found: false, reason: "no-listings", tried: names });
    return NextResponse.json({ found: true, ...price });
  } catch (e) {
    if (e instanceof BlockedError) return NextResponse.json({ error: "blocked", detail: e.message }, { status: 503 });
    return NextResponse.json({ error: "price-failed", detail: (e as Error).message }, { status: 502 });
  }
}
