import { createHash } from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

/**
 * Photos of models, fetched once and kept on this peer's disk: PHOTO_ROOT/<key>/{1.jpg…, index.json}.
 * Every later ask for the same key is answered from here; a lookup that found nothing is kept
 * too, so it is not repeated either.
 */
export const PHOTO_ROOT = "/var/lib/carcheck/photos";

export type StoredPhoto = {
  file: string;
  alt: string;
  width: number;
  height: number;
  type: string;
  /** Where a tap on this photo goes (its Commons page); absent for Google, whose tap opens the search. */
  link?: string;
};

export type PhotoIndex = {
  source: "wikipedia" | "google";
  query: string;
  fetchedAt: string;
  /** The Wikipedia article the photos came from, when they did. */
  articleUrl?: string;
  photos: StoredPhoto[];
};

export function photoKey(kind: string, query: string): string {
  const norm = `${kind}:${query.trim().replace(/\s+/g, " ").toLowerCase()}`;
  return createHash("sha1").update(norm).digest("hex").slice(0, 20);
}

export function photoDir(key: string): string {
  return path.join(PHOTO_ROOT, key);
}

export async function readIndex(key: string): Promise<PhotoIndex | null> {
  try {
    return JSON.parse(await fs.readFile(path.join(photoDir(key), "index.json"), "utf8")) as PhotoIndex;
  } catch (e) {
    if ((e as NodeJS.ErrnoException).code === "ENOENT") return null;
    throw e;
  }
}

/** A fresh folder to fill; [commit] renames it into place, so a half-written fetch is never served. */
export async function stagingDir(key: string): Promise<{ dir: string; commit: () => Promise<void>; discard: () => Promise<void> }> {
  await fs.mkdir(PHOTO_ROOT, { recursive: true });
  const dir = `${photoDir(key)}.tmp-${process.pid}-${Date.now()}`;
  await fs.mkdir(dir, { recursive: true });
  return {
    dir,
    commit: () => fs.rename(dir, photoDir(key)),
    discard: () => fs.rm(dir, { recursive: true, force: true }),
  };
}

const inFlight = new Map<string, Promise<PhotoIndex>>();

/** The stored index for [key], or [produce] it once — concurrent asks for the same key share one fetch. */
export async function cached(key: string, produce: () => Promise<PhotoIndex>): Promise<{ index: PhotoIndex; cached: boolean }> {
  const saved = await readIndex(key);
  if (saved) return { index: saved, cached: true };
  let p = inFlight.get(key);
  if (!p) {
    p = produce();
    inFlight.set(key, p);
    p.finally(() => inFlight.delete(key)).catch(() => undefined);
  }
  return { index: await p, cached: false };
}
