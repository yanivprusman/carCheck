import type { Metadata } from "next";
import "./globals.css";
import FeedbackChatMount from "./FeedbackChatMount";

export const metadata: Metadata = {
  title: "בדיקת רכב",
  description: "כל מה שמשרד התחבורה יודע על רכב, לפי מספר הרישוי — אפליקציית אנדרואיד",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="he" dir="rtl">
      <body>{children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
