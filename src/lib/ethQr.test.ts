// src/lib/ethQr.test.ts
import {
  ETH_QR_TITLES,
  ETH_QR_LANGUAGE_OPTIONS,
  toLocalPhoneDigits,
  toCodeDigits,
  type EthQrLanguage,
} from './ethQr';

function ok(name: string, condition: boolean) {
  console.log(`  ${condition ? 'ok' : 'FAIL'}  ${name}`);
  if (!condition) process.exitCode = 1;
}

// ---- the 5 official languages ---------------------------------------------
// Pinned verbatim. These are approved translations printed onto physical
// standees beside a national payment mark — see the header comment in
// ethQr.ts. A failure here means approved copy was edited.

ok('English title is the sanctioned wording', ETH_QR_TITLES.english === 'PAY FROM YOUR BANK OR WALLET');
ok('Amharic title is the sanctioned wording', ETH_QR_TITLES.amharic === 'ከባንክ ወይም ከዋሌት ይክፈሉ');
ok('Afan Oromo title is the sanctioned wording', ETH_QR_TITLES.oromo === 'Baankii Ykn Waaleetii Irraa Haa Kaffallu');
ok('Tigrinya title is the sanctioned wording', ETH_QR_TITLES.tigrinya === 'ካብ ባንኪ ወይ ካብ ዋሌት ንኽፈል');
ok('Somali title is the sanctioned wording', ETH_QR_TITLES.somali === 'Aan Ka Bixinno Abangiga Ama Walet Ka');

const LANGUAGES: EthQrLanguage[] = ['english', 'amharic', 'oromo', 'tigrinya', 'somali'];

ok('exactly 5 languages ship', Object.keys(ETH_QR_TITLES).length === 5);
ok('every language has a title', LANGUAGES.every((l) => typeof ETH_QR_TITLES[l] === 'string'));
ok('no title is blank', LANGUAGES.every((l) => ETH_QR_TITLES[l].trim().length > 0));
// A duplicated title means a language silently renders another's text — the
// exact bug a merchant printing 100 Tigrinya standees would not notice.
ok('no two languages share a title', new Set(LANGUAGES.map((l) => ETH_QR_TITLES[l])).size === 5);

// The picker is what makes a language reachable at all; a title with no option
// is dead code, and an option with no title renders `undefined` on the banner.
ok('every language is offered in the picker', ETH_QR_LANGUAGE_OPTIONS.length === 5);
ok(
  'the picker and the titles cover the same set',
  ETH_QR_LANGUAGE_OPTIONS.every((o) => LANGUAGES.includes(o.value)) &&
    LANGUAGES.every((l) => ETH_QR_LANGUAGE_OPTIONS.some((o) => o.value === l)),
);
ok('every picker option has a visible label', ETH_QR_LANGUAGE_OPTIONS.every((o) => o.label.trim().length > 0));
ok(
  'the two Ethiopic languages name themselves in their own script',
  ETH_QR_LANGUAGE_OPTIONS.find((o) => o.value === 'amharic')!.label.includes('አማርኛ') &&
    ETH_QR_LANGUAGE_OPTIONS.find((o) => o.value === 'tigrinya')!.label.includes('ትግርኛ'),
);
ok('English is the default/first option', ETH_QR_LANGUAGE_OPTIONS[0].value === 'english');

// ---- phone digit strip ----------------------------------------------------

ok(
  'a local 10-digit number becomes 10 boxes',
  toLocalPhoneDigits('0718788479').join('') === '0718788479' && toLocalPhoneDigits('0718788479').length === 10,
);
// The documented provider shape — the country code collapses to a single 0,
// so the strip stays 10 boxes wide rather than 12.
ok('a +251 number is localised to a leading 0', toLocalPhoneDigits('+251718788479').join('') === '0718788479');
ok('a 251 number without the plus is localised too', toLocalPhoneDigits('251718788479').join('') === '0718788479');
ok('the localised strip is still 10 boxes', toLocalPhoneDigits('+251718788479').length === 10);
ok('separators are dropped, not boxed', toLocalPhoneDigits('+251 71 878 8479').join('') === '0718788479');
ok('every element is a single digit', toLocalPhoneDigits('+251718788479').every((d) => /^[0-9]$/.test(d)));
// Absent rather than wrong: EthQrCard omits the whole strip on an empty array.
ok('a missing number yields no strip', toLocalPhoneDigits(null).length === 0 && toLocalPhoneDigits(undefined).length === 0);
ok('an empty string yields no strip', toLocalPhoneDigits('').length === 0);
ok('a digitless value yields no strip rather than throwing', toLocalPhoneDigits('n/a').length === 0);

// ---- shortcode row --------------------------------------------------------

ok('the sample shortcode splits to 7 digits', toCodeDigits('8319389').join(' ') === '8 3 1 9 3 8 9');
ok('non-digits are stripped from the code', toCodeDigits('83-19 389').join('') === '8319389');
ok('a missing code yields nothing rather than throwing', toCodeDigits(null).length === 0 && toCodeDigits('').length === 0);
ok('a digitless code yields nothing', toCodeDigits('----').length === 0);

console.log('all ethQr tests passed');
