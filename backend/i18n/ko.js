'use strict';

// Korean message catalog (flat key → string, {placeholders} supported).
module.exports = {
  // Auth
  invalid_username: '사용자 이름은 3~32자(영문, 숫자, 점, 대시, 밑줄)여야 합니다.',
  password_length: '비밀번호는 최소 8자 이상이어야 합니다.',
  password_policy: '비밀번호에는 대문자, 소문자, 숫자가 각각 하나 이상 포함되어야 합니다.',
  username_length: '사용자 이름은 3~32자여야 합니다.',
  username_taken: '이미 사용 중인 사용자 이름입니다.',
  invalid_login: '사용자 이름 또는 비밀번호가 올바르지 않습니다.',
  mfa_invalid: 'MFA 코드가 올바르지 않거나 누락되었습니다.',
  mfa_not_started: 'MFA 등록이 시작되지 않았습니다.',
  mfa_not_enabled: 'MFA가 활성화되어 있지 않습니다.',
  mfa_invalid_code: '잘못된 코드입니다.',
  account_not_found: '계정을 찾을 수 없습니다.',
  auth_required: '인증이 필요합니다.',
  forbidden: '이 리소스에 접근할 권한이 없습니다.',

  // UI / general
  welcome: 'PuzzleWorld에 오신 것을 환영합니다!',
  language_updated: '언어가 업데이트되었습니다.',
  invalid_language: "언어는 'en' 또는 'ko'여야 합니다.",
  not_found: '찾을 수 없습니다',
  too_many_attempts: '시도 횟수가 너무 많습니다. 나중에 다시 시도해 주세요.',
  too_many_requests: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  invalid_json: '잘못된 JSON 본문입니다',
  bad_request: '잘못된 요청입니다',
  internal_error: '서버 내부 오류입니다',

  // Game
  level_complete: '레벨 완료!',
  room_cleared: '방을 탈출했습니다!',
  congratulations: '축하합니다!',
  hint_used: '힌트 사용',
  challenge_complete: '일일 도전 완료!',
  leaderboard: '리더보드',
  room_not_found: '방을 찾을 수 없습니다',
  level_not_found: '레벨을 찾을 수 없습니다',
  player_not_found: '플레이어를 찾을 수 없습니다',
  incorrect_answer: '정답이 아닙니다.',
  no_gradable_levels: '채점 가능한 레벨이 없습니다',
  user_id_required: 'user_id가 필요합니다',
};
