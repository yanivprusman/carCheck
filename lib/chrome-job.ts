import { spawn } from "node:child_process";
import path from "node:path";

/**
 * Runs one of this app's Chrome scripts (scripts/*.mjs, built on scripts/chrome.mjs) and returns
 * its stdout. One at a time — jobs queue rather than open several browsers at once — each in a
 * transient systemd scope under xvfb-run: Chrome cannot start its thread pool inside the app
 * service's task limit, and needs a display a server does not have.
 *
 * Exit code 2 is the scripts' "the site answered with a robot check" and becomes [BlockedError].
 */
export class BlockedError extends Error {}

let queue: Promise<unknown> = Promise.resolve();

export function runChromeScript(script: string, args: string[]): Promise<string> {
  const job = queue.catch(() => undefined).then(() => run(script, args));
  queue = job;
  return job;
}

async function run(script: string, args: string[]): Promise<string> {
  const { code, stdout, stderr } = await exec("systemd-run", [
    "--scope", "--quiet", "--collect", "--property=TasksMax=4096", "--property=MemoryMax=2G",
    "xvfb-run", "-a", "-s", "-screen 0 1366x1800x24",
    process.execPath, path.join(process.cwd(), "scripts", script), ...args,
  ]);
  if (code === 2) throw new BlockedError(stderr.trim().slice(-300) || `${script}: blocked`);
  if (code !== 0) throw new Error(`${script} exited ${code}: ${stderr.trim().slice(-300)}`);
  return stdout;
}

function exec(cmd: string, args: string[]): Promise<{ code: number | null; stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "", stderr = "";
    child.stdout.on("data", (d) => (stdout += d));
    child.stderr.on("data", (d) => (stderr += d));
    const timer = setTimeout(() => child.kill("SIGKILL"), 90_000);
    child.on("error", reject);
    child.on("close", (c) => {
      clearTimeout(timer);
      resolve({ code: c, stdout, stderr });
    });
  });
}
