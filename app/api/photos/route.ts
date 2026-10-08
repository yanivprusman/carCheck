import { NextRequest, NextResponse } from "next/server";
import { GoogleCaptchaError, photosFor, selectPhotos } from "@/lib/car-photos";

export const dynamic = "force-dynamic";

/**
 * Photos of a model for the report: `?q=<make> <model> <year>[ <vehicle type>]`.
 * The first ask searches Google Images (several seconds); every later one is read from disk.
 * Only photos whose page names the car's year, and the model most of them agree on, are returned.
 */
export async function GET(req: NextRequest) {
  const q = (req.nextUrl.searchParams.get("q") ?? "").trim();
  if (q.length < 2 || q.length > 120) {
    return NextResponse.json({ error: "q must be 2–120 characters" }, { status: 400 });
  }
  try {
    const { key, index, cached } = await photosFor(q);
    return NextResponse.json({
      query: index.query,
      fetchedAt: index.fetchedAt,
      cached,
      // Everything the search found is kept on disk; only what passes selectPhotos is shown.
      photos: selectPhotos(index.query, index.photos).map((p) => ({ url: `/api/photos/${key}/${p.file}`, alt: p.alt, width: p.width, height: p.height })),
    });
  } catch (e) {
    if (e instanceof GoogleCaptchaError) {
      return NextResponse.json({ error: "google-captcha", detail: e.message }, { status: 503 });
    }
    return NextResponse.json({ error: "search-failed", detail: (e as Error).message }, { status: 502 });
  }
}
