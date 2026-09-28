import mysql from "mysql2/promise";

// One pool against the system MySQL (3306), where the `carcheck` database lives — the
// same arrangement as the sibling apps (accumulatedKnowledge, tally, veggieBox). The
// password is in the gitignored .env.local; a missing one is an error, not a guess.
const globalForDb = globalThis as typeof globalThis & { _carCheckPool?: mysql.Pool };

function createPool(): mysql.Pool {
  if (!process.env.DB_PASSWORD) {
    throw new Error("DB_PASSWORD is not set — put the carcheck MySQL password in .env.local");
  }
  return mysql.createPool({
    host: process.env.DB_HOST ?? "127.0.0.1",
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER ?? "carcheck",
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME ?? "carcheck",
    waitForConnections: true,
    connectionLimit: 5,
    charset: "utf8mb4",
    dateStrings: true,
  });
}

export const pool = globalForDb._carCheckPool ?? createPool();
if (process.env.NODE_ENV !== "production") globalForDb._carCheckPool = pool;

export async function q<T = Record<string, unknown>>(sql: string, params?: unknown[]): Promise<T[]> {
  const [rows] = await pool.query(sql, params);
  return rows as T[];
}
