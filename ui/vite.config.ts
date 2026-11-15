import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const gateway = { target: process.env.GATEWAY_URL ?? "http://localhost:8087", xfwd: true };

// The dev server passes the API, sign-in and sign-out to the customer gateway, the same way nginx
// does in Docker. The browser's Host header is kept, so the gateway returns to the dev server
// after sign-in.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": gateway,
      "/oauth2": gateway,
      "/login/oauth2": gateway,
      "/logout": gateway,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
