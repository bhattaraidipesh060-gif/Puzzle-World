'use strict';

// Language-pack loader + per-request negotiation.
//
// Language resolution priority:
//   1. `?lang=` query parameter   (explicit per-request override)
//   2. stored user preference      (req.user.language, set by requireAuth from the JWT)
//   3. `Accept-Language` header    (browser/device default)
//   4. 'en' (fallback)
const en = require('./en');
const ko = require('./ko');

const CATALOGS = { en, ko };
const SUPPORTED = ['en', 'ko'];

function detectLang(req) {
  // 1. Explicit override via query string.
  const q = String((req.query && req.query.lang) || '').toLowerCase();
  if (q === 'en' || q === 'ko') return q;
  if (q === 'kr' || q === 'ko-kr') return 'ko';

  // 2. Stored user preference (populated by requireAuth from the JWT).
  const userLang = req.user && req.user.language;
  if (userLang === 'ko' || userLang === 'en') return userLang;

  // 3. Accept-Language header.
  const header = String((req.headers && req.headers['accept-language']) || '').toLowerCase();
  if (header.includes('ko')) return 'ko';
  if (header.startsWith('en')) return 'en';

  return 'en';
}

// Translate a message key, substituting `{placeholders}` and falling back to
// English (then the raw key) when a string is missing.
function t(req, key, vars) {
  const lang = detectLang(req);
  let msg = (CATALOGS[lang] && CATALOGS[lang][key]) || en[key] || key;
  if (vars) {
    for (const k of Object.keys(vars)) {
      msg = msg.split('{' + k + '}').join(String(vars[k]));
    }
  }
  return msg;
}

module.exports = { t, detectLang, CATALOGS, SUPPORTED };
