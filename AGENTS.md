<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# The registry copy (`npm run sync-registry`)

data.gov.il's main private-car file (`053cea08…`, ~4.2 M rows) is replaced nightly and its
datastore serves **zero rows for about three hours** afterwards (measured 2026-09-28: upload
02:46 UTC, empty until ~08:35 local). There is no previous version to read there. So this
peer keeps a copy in the system MySQL (`carcheck.private_vehicles`, password in the
gitignored `.env.local`), served by `GET /api/registry/private?plate=…`, and the phone asks
it **only when the live table answers empty** — a lookup never depends on a home server
otherwise. The copy is taken through the datastore API in keyset pages (`SELECT * … WHERE
_id > last`), loaded into a fresh table and swapped in with one RENAME, so what is served is
always a complete file: the old one until the new one is. It is **on demand**: run
`npm run sync-registry` after the table is back (it refuses, keeping the copy on hand, while
the table is empty; `--force` reloads the same file). The raw CSV download would be fresher
but sits behind AWS WAF bot control (`x-amzn-waf-action: challenge`), so the script does not
use it. Naming the 23 columns in the SQL gets a 404 page from the WAF; `SELECT *` passes.
