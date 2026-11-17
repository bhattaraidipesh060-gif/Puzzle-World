'use strict';

// Lightweight i18n dictionary for API messages. Language is negotiated per request
// via the `?lang=` query parameter or the `Accept-Language` header (default: English).
const MESSAGES = {
  en: {
    invalid_email: 'Invalid email address',
    email_exists: 'An account with that email already exists.',
    password_length: 'Password must be at least 8 characters.',
    password_policy: 'Password must include an uppercase letter, a lowercase letter, and a number.',
    username_length: 'Username must be 3–32 characters.',
    username_taken: 'That username is already taken.',
    otp_sent: 'Verification code sent to {email}.',
    otp_resent: 'A new verification code has been sent to {email}.',
    otp_format: 'Please enter a valid 6-digit code.',
    otp_invalid: 'Invalid or expired verification code.',
    otp_already_verified: 'Email already verified. Please log in.',
    otp_no_account: 'No account found for that email.',
    invalid_login: 'Invalid email/username or password.',
    unverified: 'Email not verified. Please check your inbox for the verification code.',
    mfa_invalid: 'Invalid or missing MFA code.',
    mfa_not_started: 'MFA enrollment has not been started.',
    mfa_not_enabled: 'MFA is not enabled.',
    mfa_invalid_code: 'Invalid code.',
    account_not_found: 'Account not found.',
  },
  ko: {
    invalid_email: '유효하지 않은 이메일 주소입니다',
    email_exists: '이미 등록된 이메일입니다.',
    password_length: '비밀번호는 최소 8자 이상이어야 합니다.',
    password_policy: '비밀번호에는 대문자, 소문자, 숫자가 각각 하나 이상 포함되어야 합니다.',
    username_length: '사용자 이름은 3~32자여야 합니다.',
    username_taken: '이미 사용 중인 사용자 이름입니다.',
    otp_sent: '{email}(으)로 인증 코드를 보냈습니다.',
    otp_resent: '{email}(으)로 새 인증 코드를 보냈습니다.',
    otp_format: '6자리 인증 코드를 입력해 주세요.',
    otp_invalid: '인증 코드가 올바르지 않거나 만료되었습니다.',
    otp_already_verified: '이미 인증된 이메일입니다. 로그인해 주세요.',
    otp_no_account: '해당 이메일로 등록된 계정이 없습니다.',
    invalid_login: '이메일 또는 비밀번호가 올바르지 않습니다.',
    unverified: '이메일이 인증되지 않았습니다. 받은 편지함에서 인증 코드를 확인해 주세요.',
    mfa_invalid: 'MFA 코드가 올바르지 않거나 누락되었습니다.',
    mfa_not_started: 'MFA 등록이 시작되지 않았습니다.',
    mfa_not_enabled: 'MFA가 활성화되어 있지 않습니다.',
    mfa_invalid_code: '잘못된 코드입니다.',
    account_not_found: '계정을 찾을 수 없습니다.',
  },
};

// Determine the requested language for a request.
function detectLang(req) {
  const q = String((req.query && req.query.lang) || '').toLowerCase();
  if (q === 'ko' || q === 'kr' || q === 'ko-kr') return 'ko';
  const header = String((req.headers && req.headers['accept-language']) || '').toLowerCase();
  if (header.includes('ko')) return 'ko';
  return 'en';
}

// Translate a message key, substituting `{placeholders}`.
function t(req, key, vars) {
  const lang = detectLang(req);
  let msg = (MESSAGES[lang] && MESSAGES[lang][key]) || MESSAGES.en[key] || key;
  if (vars) {
    for (const k of Object.keys(vars)) {
      msg = msg.split('{' + k + '}').join(vars[k]);
    }
  }
  return msg;
}

module.exports = { t, detectLang, MESSAGES };
