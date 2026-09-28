/** data.gov.il's main private-car file: "מאגר מספרי רישוי של כלי רכב", replaced nightly, 874 MB. */
export const PRIVATE_RESOURCE = "053cea08-09bc-40ec-8f7a-156f0677aff3";

/** The file's columns, in its order. The datastore adds `_id`; the copy does not. */
export const PRIVATE_COLUMNS = [
  "mispar_rechev", "tozeret_cd", "sug_degem", "tozeret_nm", "degem_cd", "degem_nm", "ramat_gimur",
  "ramat_eivzur_betihuty", "kvutzat_zihum", "shnat_yitzur", "degem_manoa", "mivchan_acharon_dt",
  "tokef_dt", "baalut", "misgeret", "tzeva_cd", "tzeva_rechev", "zmig_kidmi", "zmig_ahori",
  "sug_delek_nm", "horaat_rishum", "moed_aliya_lakvish", "kinuy_mishari",
] as const;
