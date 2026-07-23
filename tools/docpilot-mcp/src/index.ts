import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

import { createServer } from "./server.js";

async function main(): Promise<void> {
  const server = await createServer();
  const transport = new StdioServerTransport();

  await server.connect(transport);

  console.error("DocPilot MCP server started.");
}

main().catch((error: unknown) => {
  console.error("Failed to start DocPilot MCP server:", error);
  process.exit(1);
});
