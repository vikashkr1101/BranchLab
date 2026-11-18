import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  use: { baseURL: "http://127.0.0.1:3100" },
  webServer: [
    {
      cwd: "../backend",
      command: process.platform === "win32" ? ".\\mvnw.cmd spring-boot:run" : "sh ./mvnw spring-boot:run",
      url: "http://127.0.0.1:8080/actuator/health",
      timeout: 180_000,
    },
    {
      command: "npm run start -- --hostname 127.0.0.1 --port 3100",
      url: "http://127.0.0.1:3100/login",
      timeout: 90_000,
    },
  ],
});
