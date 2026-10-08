import { test } from "node:test";
import assert from "node:assert/strict";
import { findEntry, priceStats } from "../market-price";

test("a model is matched by its Hebrew name, exactly, never a near one", () => {
  const models = [
    { id: 10756, title: "קנגו" }, { id: 10762, title: "מגאן" }, { id: 13056, title: "מגאן E-Tech" }, { id: 10627, title: "500" }, { id: 10628, title: "500X" },
  ];
  assert.equal(findEntry(models, ["KANGOO", "קנגו"])?.id, 10756);
  assert.equal(findEntry(models, ["מגאן"])?.id, 10762);
  assert.equal(findEntry(models, ["500"])?.id, 10627);
  assert.equal(findEntry(models, ["דוקאטו"]), null);
});

test("a make is matched in Hebrew or English", () => {
  const makers = [{ id: 51, title: "רנו", engTitle: "Renault" }, { id: 86, title: "מאן", engTitle: "MAN" }];
  assert.equal(findEntry(makers, ["רנו"])?.id, 51);
  assert.equal(findEntry(makers, ["MAN"])?.id, 86);
});

test("price summary: placeholders out, median and the middle half", () => {
  const ad = (price: number | null) => ({ price, hand: null, subModel: null });
  const s = priceStats([ad(600), ad(null), ad(19900), ad(22000), ad(24900), ad(26000), ad(34000)]);
  assert.deepEqual(s, { priced: 5, median: 24900, low: 22000, high: 26000 });
  assert.deepEqual(priceStats([ad(30000), ad(20000)]), { priced: 2, median: 30000, low: 20000, high: 30000 });
  assert.equal(priceStats([ad(1)]), null);
});
