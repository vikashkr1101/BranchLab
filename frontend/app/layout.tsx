import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BranchLab",
  description: "Replay historical orders to measure the impact of a policy change.",
};

export default function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
