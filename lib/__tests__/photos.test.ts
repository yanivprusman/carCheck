import { test } from "node:test";
import assert from "node:assert/strict";
import { distinctive, googleQuery, mentionsYear, searchCode, selectPhotos } from "../google-photos";
import { pickArticle, rankFiles, yearDistance } from "../wikipedia-photos";

test("MAN codes are searched the way MAN writes them", () => {
  assert.equal(searchCode("מאן", "12163LL"), "12.163");
  assert.equal(searchCode("מאן", "8163"), "8.163");
  assert.equal(searchCode("פיאט", "250"), "250");
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
