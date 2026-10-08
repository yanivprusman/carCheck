import { NextRequest, NextResponse } from "next/server";
import { GoogleCaptchaError, googleImagesUrl, googlePhotos, googleQuery, searchCode, selectPhotos } from "@/lib/google-photos";
import { photoKey, type StoredPhoto } from "@/lib/photo-store";
import { wikipediaPhotos } from "@/lib/wikipedia-photos";

export const dynamic = "force-dynamic";

/**
 * Photos of a model for the report.
 *
 *   ?make=רנו&model=KW0&name=KANGOO&year=2017            a car with a commercial name
 *   ?make=פיאט&model=250&year=2023&kind=רכב מסחרי        a heavy vehicle, named only by its code
 *
 * Wikipedia when the registry gives a commercial name and an article about exactly that model
 * exists — its photos are editor-chosen, freely licensed and dated. Google Images for every other
 * vehicle, checked against the code and year (lib/google-photos). Each lookup runs once; later
 * asks are read from disk.
 */
export async function GET(req: NextRequest) {
  const sp = req.nextUrl.searchParams;
  const make = sp.get("make")?.trim() ?? "";
  const model = sp.get("model")?.trim() || null;
  const name = sp.get("name")?.trim() || null;
  const yearText = sp.get("year")?.trim();
  const year = yearText && /^\d{4}$/.test(yearText) ? Number(yearText) : null;
  const kind = sp.get("kind")?.trim() || null;
  if (!make || !(name || model)) {
    return NextResponse.json({ error: "make and model or name are required" }, { status: 400 });
  }
  try {
    if (name) {
      const { index, cached } = await wikipediaPhotos(make, name, year);
      if (index.photos.length > 0) {
        return NextResponse.json({
          source: "wikipedia",
          cached,
          moreUrl: index.articleUrl,
          photos: index.photos.map((p) => served(photoKey("wikipedia", index.query), p)),
        });
      }
    }
    const code = (name ?? model)!;
    const query = googleQuery(make, code, year, kind);
    const { index, cached } = await googlePhotos(query);
    return NextResponse.json({
      source: "google",
      cached,
      moreUrl: googleImagesUrl(query),
      // Everything the search found stays on disk; only what passes selectPhotos is shown.
      photos: selectPhotos(query, searchCode(make, code), year, index.photos).map((p) => served(photoKey("google", query), p)),
    });
  } catch (e) {
    if (e instanceof GoogleCaptchaError) {
      return NextResponse.json({ error: "google-captcha", detail: e.message }, { status: 503 });
    }
    return NextResponse.json({ error: "photos-failed", detail: (e as Error).message }, { status: 502 });
  }
}

function served(key: string, p: StoredPhoto) {
  return { url: `/api/photos/${key}/${p.file}`, alt: p.alt, link: p.link ?? null };
}
