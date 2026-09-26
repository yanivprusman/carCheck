/**
 * The app is the phone app; this page is its front door on the web: what it is,
 * what a report holds, and the same plate mark the launcher icon carries.
 */
const SECTIONS: Array<[string, string]> = [
  ["זיהוי", "יצרן, דגם, כינוי מסחרי, שנת יצור, צבע, רמת גימור, סוג מרכב"],
  ["רישוי", "תוקף הטסט, טסט אחרון, עלייה לכביש, בעלות, שלדה ומנוע"],
  ["היסטוריה", "ק״מ בטסט האחרון, כל חילופי הבעלות, שינוי מבנה, הסבה לגז, שינוי צבע"],
  ["ריקולים", "קריאות שירות שלא בוצעו, עם טלפון ואתר של היבואן"],
  ["מנוע והנעה", "דלק, נפח, כוח סוס, הנעה, תיבת הילוכים, משקלים, וו גרירה וכושר גרירה"],
  ["בטיחות וזיהום", "רמת אבזור בטיחותי, כריות אוויר, מערכות עזר לנהג, קבוצת זיהום, פליטות"],
  ["מחיר", "מחיר המחירון של היבואן בשנת היצור"],
];

export default function Home() {
  return (
    <main data-id="home" className="min-h-dvh bg-[#F3F4F6] text-[#15171C]">
      <div className="mx-auto max-w-2xl px-5 py-14">
        <div className="flex items-center gap-4">
          <img src="/icon.svg" alt="" width={56} height={56} className="rounded-2xl" />
          <div>
            <h1 className="text-3xl font-black tracking-tight">בדיקת רכב</h1>
            <p className="text-[#646A76]">כל מה שמשרד התחבורה יודע על רכב, לפי מספר הרישוי</p>
          </div>
        </div>

        <div className="mt-10 flex justify-center" dir="ltr">
          <div className="flex h-20 w-[336px] overflow-hidden rounded-lg border-[3px] border-[#14161A] bg-[#FFD21F]">
            <div className="flex w-7 flex-col items-center justify-between bg-[#123C8C] py-1.5 text-white">
              <span className="h-3 w-4 bg-white" style={{ boxShadow: "inset 0 3px 0 #123C8C, inset 0 -3px 0 #123C8C" }} />
              <span className="text-xs font-bold">IL</span>
            </div>
            <div className="flex flex-1 items-center justify-center text-[42px] font-black tracking-tight text-[#1A1400] tabular-nums">
              12-345-67
            </div>
          </div>
        </div>

        <p className="mt-10 text-lg leading-8">
          אפליקציית אנדרואיד. מקלידים מספר רכב, ומקבלים דוח אחד מכל קובצי הרישוי הפתוחים של משרד
          התחבורה ב-data.gov.il — הטלפון שואל את המאגר ישירות, בלי שרת באמצע.
        </p>

        <dl className="mt-8 divide-y divide-[#E4E6EB] overflow-hidden rounded-2xl bg-white">
          {SECTIONS.map(([title, body]) => (
            <div key={title} className="flex gap-4 px-5 py-4">
              <dt className="w-28 shrink-0 font-bold">{title}</dt>
              <dd className="text-[#646A76]">{body}</dd>
            </div>
          ))}
        </dl>

        <p className="mt-8 text-sm text-[#646A76]">
          מקור הנתונים: משרד התחבורה, דרך data.gov.il. רכבים פרטיים משנת 1996 ומסחריים קלים משנת 1998;
          דו-גלגליים, כבדים, ציבוריים ויבוא אישי; רכבים שירדו מהכביש מאז 2000.
        </p>
      </div>
    </main>
  );
}
