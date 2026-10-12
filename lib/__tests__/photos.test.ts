import { test } from "node:test";
import assert from "node:assert/strict";
import { distinctive, googleQuery, mentionsYear, searchCode, selectPhotos } from "../google-photos";
import { pickArticle, rankFiles, yearDistance } from "../wikipedia-photos";

test("MAN codes are searched the way MAN writes them", () => {
  assert.equal(searchCode("מאן", "12163LL"), "12.163");
  assert.equal(searchCode("מאן", "8163"), "8.163");
  assert.equal(searchCode("פיאט", "250"), "250");
});

test("MAN's TG range and Hino's chassis codes are searched the way their listings write them", () => {
  // Measured 2026-10-12: whole, each found nothing of its own; rewritten, pages about that truck.
  assert.equal(searchCode("מאן", "TGS354308X4BBNNME6"), "TGS 35.430");
  assert.equal(searchCode("מאן", "TGE51804X2SBPVL4H3"), "TGE 5.180");
  assert.equal(searchCode("מאן", "TGL122204X2BLCCGEU"), "TGL 12.220");
  assert.equal(searchCode("מאן", "TGM 13.250BL"), "TGM 13.250");
  assert.equal(searchCode("מאן", "TGM15.240BL"), "TGM 15.240");
  assert.equal(searchCode("הינו", "XJC740L"), "XJC740");
  assert.equal(searchCode("איסוזו", "FRR90"), "FRR90");
});

test("a model name run together with its type number is searched by the name", () => {
  assert.equal(searchCode("מרצדס בנץ", "ANTOS963020"), "ANTOS");
  assert.equal(searchCode("איסוזו", "NPR75"), "NPR75");
  assert.equal(searchCode("איווקו", "50C15V"), "50C15V");
  // Measured: with "משאית" the answer was Sprinters; without it, 2018 Antoses.
  assert.equal(googleQuery("מרצדס בנץ", "ANTOS963020", 2018, "משאית"), "מרצדס בנץ ANTOS 2018");
});

test("pages about the make, naming no model, give no photos", () => {
  // Measured 2026-10-12: Google's answer for "מרצדס בנץ ANTOS963020 2018", a 26-tonne truck.
  const photos = [
    { alt: "מרצדס 2018 - מחירון ומפרט | Carzone" },
    { alt: "מרצדס S קלאס 2018 יד שניה - אוטו" },
    { alt: "מרצדס E קלאס 2018 יד שניה - אוטו" },
    { alt: "מרצדס-בנץ A-class 2018 AMG A 250 אוט׳ 2.0 (218 כ״ס) | מחירון יד2" },
  ];
  assert.deepEqual(selectPhotos("מרצדס בנץ ANTOS 2018", "ANTOS", 2018, photos), []);
});

test("a code is matched without its letter suffix, the way listings write it", () => {
  const photos = [
    { alt: "2009 Iveco Daily 50C15 (PSV) - Coach & Bus Market" },
    { alt: "IVECO Daily II 50 C 15 2009 Box Photos and Info" },
    { alt: "סוזוקי סוויפט 2009 1.5 GLX אוט' יד שניה" },
  ];
  assert.deepEqual(selectPhotos("איווקו 50C15V 2009", "50C15V", 2009, photos).length, 2);
});

test("the vehicle kind is added only when the code alone is ambiguous", () => {
  // Measured: "פיאט 250 2023" is Fiat 500s; with "רכב מסחרי" it is Ducatos.
  assert.equal(googleQuery("פיאט", "250", 2023, "רכב מסחרי"), "פיאט 250 2023 רכב מסחרי");
  // Measured: "מאן 12.163 2000 משאית" finds no 12.163 at all; without "משאית", a page of them.
  assert.equal(googleQuery("מאן", "12163LL", 2000, "משאית"), "מאן 12.163 2000");
  assert.ok(distinctive("NPR75") && distinctive("12.163") && !distinctive("250") && !distinctive("KANGOO"));
});

test("year ranges in page text cover the years between", () => {
  assert.ok(mentionsYear("פיאט סקודו 2023-2025", 2024));
  assert.ok(mentionsYear("2019–23 Corolla", 2021));
  assert.ok(!mentionsYear("Molten BG2000", 2000));
});

test("a distinctive code that pages name wins over everything else", () => {
  const photos = [
    { alt: "MAN M 2000 L 12.163 LC 2001 Box Truck" },
    { alt: "מובילית מאן 8.163 מודל 2000" },
    { alt: "Używany MAN 12.163 2000 - Otomoto.pl" },
    { alt: "MAN TGL 2000 something" },
  ];
  assert.deepEqual(
    selectPhotos("מאן 12.163 2000", "12.163", 2000, photos).map((p) => p.alt),
    ["MAN M 2000 L 12.163 LC 2001 Box Truck", "Używany MAN 12.163 2000 - Otomoto.pl"],
  );
});

test("an ambiguous code falls to the model name the pages agree on", () => {
  const photos = [
    { alt: "מידע מקיף ומקצועי על פיאט סקודו 2023-2025 - אתר iCar" },
    { alt: "פיאט דוקאטו 2023 יד שניה - אוטו" },
    { alt: "פיאט דוקאטו קבינה 2023 - מחירון ומפרט | Carzone" },
    { alt: "פיאט דוקאטו 2026 - רכב מסחרי עוצמתי" },
  ];
  assert.deepEqual(
    selectPhotos("פיאט 250 2023 רכב מסחרי", "250", 2023, photos).map((p) => p.alt),
    ["פיאט דוקאטו 2023 יד שניה - אוטו", "פיאט דוקאטו קבינה 2023 - מחירון ומפרט | Carzone"],
  );
});

test("Wikipedia: the article about this model, not the first hit or another make's", () => {
  const hits = [{ heTitle: "טויוטה AE85", enTitle: "Toyota AE85" }, { heTitle: "טויוטה קורולה", enTitle: "Toyota Corolla" }];
  assert.equal(pickArticle(hits, "טויוטה", "COROLLA")?.enTitle, "Toyota Corolla");
  assert.equal(pickArticle([{ heTitle: "מרצדס-בנץ סיטאן", enTitle: "Mercedes-Benz Citan" }], "רנו", "KANGOO"), null);
  assert.equal(pickArticle([{ heTitle: "סקודה אוקטביה", enTitle: "Škoda Octavia" }], "סקודה", "OCTAVIA")?.enTitle, "Škoda Octavia");
});

test("Wikipedia: photos of the car's generation first, logos and other models out", () => {
  assert.equal(yearDistance("File:2007-2010 Toyota Corolla.jpg", 2008), 0);
  assert.equal(yearDistance("File:2014 Toyota Corolla.jpg", 2017), 3);
  const f = (title: string, mime = "image/jpeg") => ({ title, mime, thumbUrl: "", pageUrl: "", width: 0, height: 0 });
  const ranked = rankFiles(
    [f("File:1968 Toyota Corolla.jpg"), f("File:2013-2016 Toyota Corolla sedan.jpg"), f("File:Toyota logo Corolla.png", "image/png"), f("File:Honda Civic 2015.jpg")],
    "COROLLA",
    2015,
  ).map((x) => x.title);
  assert.deepEqual(ranked, ["File:2013-2016 Toyota Corolla sedan.jpg", "File:1968 Toyota Corolla.jpg"]);
});
