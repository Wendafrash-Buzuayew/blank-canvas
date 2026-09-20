/**
 * ETHQR standee content rules — the 5 official languages, and the two
 * digit-strip splits the card's layout needs.
 *
 * Pure data and pure functions only, no React, so the language set and the
 * digit handling can be asserted directly (see ethQr.test.ts). The rendering
 * lives in src/components/qr/EthQrCard.tsx, which re-exports the language
 * type and picker options so existing importers keep working.
 *
 * The header titles are the sanctioned wording for each language and are NOT
 * ours to paraphrase: a merchant prints these onto physical standees that sit
 * on a table next to a national payment mark, so a "tidied up" translation is
 * a mistranslation on someone else's brand. ethQr.test.ts pins all five
 * verbatim for exactly that reason — a failing assertion there means someone
 * edited approved copy, which is a review question, not a test to relax.
 */

export type EthQrLanguage = 'english' | 'amharic' | 'oromo' | 'tigrinya' | 'somali';

export const ETH_QR_LANGUAGE_OPTIONS: Array<{ value: EthQrLanguage; label: string }> = [
  { value: 'english', label: 'English' },
  { value: 'amharic', label: 'Amharic (አማርኛ)' },
  { value: 'oromo', label: 'Afaan Oromo' },
  { value: 'tigrinya', label: 'Tigrinya (ትግርኛ)' },
  { value: 'somali', label: 'Somali' },
];

/** The header banner title in each of the 5 official languages. */
export const ETH_QR_TITLES: Record<EthQrLanguage, string> = {
  english: 'PAY FROM YOUR BANK OR WALLET',
  amharic: 'ከባንክ ወይም ከዋሌት ይክፈሉ',
  oromo: 'Baankii Ykn Waaleetii Irraa Haa Kaffallu',
  tigrinya: 'ካብ ባንኪ ወይ ካብ ዋሌት ንኽፈል',
  somali: 'Aan Ka Bixinno Abangiga Ama Walet Ka',
};

/**
 * Splits the merchant's phone number into the individual digits the card
 * renders as one box each.
 *
 * Safaricom's `mobileNumber` field comes back in international form
 * ("+251718788479", or sometimes without the "+"), but the official template
 * prints the local 10-digit form — so a leading 251 becomes a single 0. Any
 * separator the provider includes is dropped: only digits get a box.
 *
 * Returns an empty array for a missing or digitless value rather than
 * throwing; EthQrCard then omits the whole strip, which is the right result
 * for a merchant whose provider record carries no phone number.
 */
export function toLocalPhoneDigits(phone?: string | null): string[] {
  if (!phone) return [];
  const digits = phone.replace(/\D/g, '');
  if (digits.length === 0) return [];
  const local = digits.startsWith('251') ? `0${digits.slice(3)}` : digits;
  return local.split('');
}

/**
 * What the footer band prints when the provider has no city for the short
 * code. Addis Ababa is where the overwhelming majority of ETHQR merchants
 * actually are, and the band's bottom-left slot cannot simply be left empty:
 * the slot is one of three fixed positions in the brand footer lockup, so a
 * blank there reads as a printing fault rather than as missing data.
 */
export const DEFAULT_ETH_QR_LOCATION = 'ADDIS';

/**
 * The acquiring location printed at the bottom left of the footer band.
 *
 * `city` is null far more often than not in real provider responses (see
 * EthQrResponse's Javadoc), and an all-whitespace string has been observed
 * too — both mean "no city on file", so both fall back rather than only the
 * null case. Uppercased because the band sets every mark in caps.
 */
export function resolveLocation(city?: string | null): string {
  const trimmed = city?.trim();
  return trimmed ? trimmed.toUpperCase() : DEFAULT_ETH_QR_LOCATION;
}

/**
 * The short code's digits, for the spaced "CODE: 8 3 1 9 3 8 9" row.
 *
 * Non-digits are stripped rather than kept: the row is letter-spaced as a
 * numeric run, and a stray separator would be spaced out as though it were
 * part of the code a guest is meant to read back.
 */
export function toCodeDigits(code?: string | null): string[] {
  if (!code) return [];
  const digits = code.replace(/\D/g, '');
  return digits.length === 0 ? [] : digits.split('');
}
