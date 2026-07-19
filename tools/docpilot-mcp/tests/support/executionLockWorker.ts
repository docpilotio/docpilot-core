import { RepositoryExecutionLock } from "../../src/orchestration/RepositoryExecutionLock.js";

const root = process.argv[2];
if (root === undefined) throw new Error("repository root is required");
const lock = await new RepositoryExecutionLock().acquire(root, "RFC-0039-test", "RFC-0039");
process.stdout.write("LOCKED\n");
await new Promise<void>((resolve) => { process.stdin.once("data", () => resolve()); process.stdin.once("end", resolve); });
await lock.release();
