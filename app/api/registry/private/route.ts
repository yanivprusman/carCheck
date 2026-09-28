import { NextRequest, NextResponse } from "next/server";
import { q } from "@/lib/db";
import { PRIVATE_RESOURCE, PRIVATE_COLUMNS } from "@/lib/registry-mirror";

export const dynamic = "force-dynamic";

type MetaRow = { file_modified: string; loaded_at: string; row_count: number };

/**
 * One row of the copy we keep of data.gov.il's main private-car file, by plate.
 *
 * The phone asks data.gov.il directly and comes here only while that datastore is
 * empty (it is, for hours after each nightly upload). The row is returned with the
 * registry's own column names, so the phone reads it exactly like a live record,
 * together with when the copied file was uploaded — the date the report is "as of".
 */
export async function GET(req: NextRequest) {
  const plate = (req.nextUrl.searchParams.get("plate") ?? "").replace(/\D/g, "").replace(/^0+/, "");
  if (plate.length < 2 || plate.length > 8) {
    return NextResponse.json({ error: "plate must be 2–8 digits" }, { status: 400 });
  }
  let meta: MetaRow[];
  try {
    meta = await q<MetaRow>(
      "SELECT file_modified, loaded_at, row_count FROM registry_mirror WHERE resource_id = ?",
      [PRIVATE_RESOURCE],
    );
  } catch (e) {
    // No table yet: the mirror was never synced on this peer.
    return NextResponse.json({ error: "mirror-empty", detail: (e as Error).message }, { status: 503 });
  }
  if (meta.length === 0) {
    return NextResponse.json({ error: "mirror-empty", detail: "never synced — run `npm run sync-registry`" }, { status: 503 });
  }
  const rows = await q<Record<string, string | number | null>>(
    `SELECT ${PRIVATE_COLUMNS.join(", ")} FROM private_vehicles WHERE mispar_rechev = ? LIMIT 1`,
    [Number(plate)],
  );
  return NextResponse.json({
    found: rows.length > 0,
    record: rows[0] ?? null,
    fileModified: meta[0].file_modified,
    loadedAt: meta[0].loaded_at,
    rowCount: meta[0].row_count,
  });
}
