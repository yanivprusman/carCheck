/**
 * The one way this app's scripts drive Chrome: an ordinary Chrome process with its own profile
 * (/var/lib/carcheck/chrome-profile), started normally and only read over DevTools.
 *
 * Google and Yad2 both refuse anything less than a full browser — a plain fetch gets a "browser
 * not supported" page, a puppeteer-launched Chrome (headless or not) gets a robot check — so
 * scripts run under xvfb-run with this, never with puppeteer.launch, and never the user's browser.
 */
import { spawn } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import puppeteer from "puppeteer-core";

const PROFILE = "/var/lib/carcheck/chrome-profile";

function freePort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => {
      const { port } = s.address();
      s.close(() => resolve(port));
    });
    s.on("error", reject);
  });
}

/** Runs [work] with a page of a fresh Chrome, then closes it, whatever happens. */
export async function withChrome(work) {
  fs.mkdirSync(PROFILE, { recursive: true });
  const port = await freePort();
  const chrome = spawn(
    "/usr/bin/google-chrome",
    [
      "--no-sandbox", // runs as root
      `--user-data-dir=${PROFILE}`,
      `--remote-debugging-port=${port}`,
      "--no-first-run",
      "--no-default-browser-check",
      "--window-size=1366,1800",
      "about:blank",
    ],
    { stdio: "ignore" },
  );
  let browser;
  try {
    const deadline = Date.now() + 15_000;
    for (;;) {
      try {
        browser = await puppeteer.connect({ browserURL: `http://127.0.0.1:${port}`, defaultViewport: null });
        break;
      } catch (e) {
        if (Date.now() > deadline) throw new Error(`Chrome did not open DevTools on ${port}: ${e.message}`);
        await new Promise((r) => setTimeout(r, 300));
      }
    }
    const [page] = await browser.pages();
    return await work(page);
  } finally {
    if (browser) await browser.disconnect();
    chrome.kill();
  }
}
