import fs from "node:fs/promises";
import path from "node:path";
import { NextResponse } from "next/server";
import { PHOTO_ROOT } from "@/lib/photo-store";

export const dynamic = "force-dynamic";

/** One saved photo. Never changes once written, so the phone may cache it for good. */
export async function GET(_req: Request, ctx: RouteContext<"/api/photos/[key]/[file]">) {
  const { key, file } = await ctx.params;
  if (!/^[0-9a-f]{20}$/.test(key) || !/^\d{1,2}\.jpg$/.test(file)) {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  const dir = path.join(PHOTO_ROOT, key);
  let index: { photos: { file: string; type: string }[] };
  let bytes: Buffer;
  try {
    index = JSON.parse(await fs.readFile(path.join(dir, "index.json"), "utf8"));
    bytes = await fs.readFile(path.join(dir, file));
  } catch {
    return NextResponse.json({ error: "not found" }, { status: 404 });
  }
  const type = index.photos.find((p) => p.file === file)?.type ?? "image/jpeg";
  return new NextResponse(new Uint8Array(bytes), {
    headers: { "Content-Type": type, "Cache-Control": "public, max-age=31536000, immutable" },
  });
}
