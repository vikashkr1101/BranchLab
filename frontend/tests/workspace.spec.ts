import { expect, test } from "@playwright/test";
import { randomUUID } from "node:crypto";

test("an owner can sign up, reload the workspace, sign out and log back in", async ({ page }) => {
  const email = `owner-${randomUUID()}@example.test`;
  await page.goto("/");
  await page.getByRole("link", { name: "Create an account" }).click();
  await page.getByLabel("Workspace name").fill("Indigo Goods");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill("local-test-password");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page.getByRole("heading", { name: "Indigo Goods" })).toBeVisible();
  await page.reload();
  await expect(page.getByText("No runs yet")).toBeVisible();
  await page.getByRole("button", { name: "Sign out" }).click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill("local-test-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Indigo Goods" })).toBeVisible();
});
