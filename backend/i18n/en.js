'use strict';

// English message catalog (flat key → string, {placeholders} supported).
module.exports = {
  // Auth
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
  auth_required: 'Authentication required.',
  forbidden: 'You are not allowed to access this resource.',

  // UI / general
  welcome: 'Welcome to PuzzleWorld!',
  language_updated: 'Language updated.',
  invalid_language: "Language must be 'en' or 'ko'.",
  not_found: 'Not found',
  too_many_attempts: 'Too many attempts. Please try again later.',
  too_many_requests: 'Too many requests. Please slow down.',
  invalid_json: 'Invalid JSON body',
  bad_request: 'Bad request',
  internal_error: 'Internal server error',

  // Game
  level_complete: 'Level complete!',
  room_cleared: 'Room cleared!',
  congratulations: 'Congratulations!',
  hint_used: 'Hint used',
  challenge_complete: 'Daily challenge complete!',
  leaderboard: 'Leaderboard',
};
