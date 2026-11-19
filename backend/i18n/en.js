'use strict';

// English message catalog (flat key → string, {placeholders} supported).
module.exports = {
  // Auth
  invalid_username: 'Username must be 3–32 characters (letters, numbers, dot, dash, underscore).',
  password_length: 'Password must be at least 8 characters.',
  password_policy: 'Password must include an uppercase letter, a lowercase letter, and a number.',
  username_length: 'Username must be 3–32 characters.',
  username_taken: 'That username is already taken.',
  invalid_login: 'Invalid username or password.',
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
  room_not_found: 'Room not found',
  level_not_found: 'Level not found',
  player_not_found: 'Player not found',
  incorrect_answer: 'Incorrect answer.',
  no_gradable_levels: 'No gradable levels',
  user_id_required: 'user_id required',
};
