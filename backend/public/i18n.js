/* =========================================================
   PuzzleWorld frontend localization (English / 한국어)
   Loaded before app.js and auth.js. Exposes window.I18N.
   ========================================================= */
(function () {
  'use strict';

  const CATALOGS = {
    en: {
      // ---- App bar / nav ----
      'nav.search': 'Search games & rooms…',
      'nav.get_app': 'Get the app',
      'nav.sign_in': 'Sign in',
      'nav.mute': 'Mute sound',
      'nav.unmute': 'Unmute sound',
      'menu.profile': 'Profile',
      'menu.settings': 'Settings',
      'menu.terms': 'Terms & Privacy',
      'menu.logout': 'Log out',
      'menu.login': 'Sign in',

      // ---- Hero ----
      'hero.welcome': 'Welcome back, {name}',
      'hero.sub': "Ready for today's challenge?",
      'hero.games_played': 'Games Played',
      'hero.high_score': 'High Score',
      'hero.global_rank': 'Global Rank',
      'hero.play_now': 'Play Now',
      'hero.surprise': 'Surprise me',
      'hero.resume': 'Resume',

      // ---- Dashboard tabs ----
      'tab.games': 'All Games',
      'tab.board': 'Leaderboard',
      'tab.stats': 'My Stats',
      'tab.settings': 'Settings',

      // ---- Category chips ----
      'cat.all': 'All',
      'cat.escape': 'Escape',
      'cat.puzzle': 'Puzzle',
      'cat.word': 'Word',
      'cat.daily': 'Daily',

      // ---- Games catalogue ----
      'game.rooms.title': 'Escape Rooms',
      'game.rooms.desc': '9 themed rooms · 90 brain-melting levels across 7 mechanics.',
      'game.sudoku.title': 'Sudoku',
      'game.sudoku.desc': 'Classic 9×9 logic with notes, undo, hints and a live timer.',
      'game.word.title': 'Word Descrambler',
      'game.word.desc': 'Unscramble words against the clock. Streaks multiply your score.',
      'game.daily.title': 'Daily Challenge',
      'game.daily.desc': 'One fresh puzzle every day. Compete on the global leaderboard.',
      'game.rooms.chip1': '9 Rooms',
      'game.rooms.chip2': '90 Levels',
      'game.sudoku.chip1': 'Logic',
      'game.sudoku.chip2': '4 Difficulties',
      'game.word.chip1': 'Word Game',
      'game.word.chip2': 'Streaks',
      'game.daily.chip1': 'Daily',
      'game.daily.chip2': 'Global',
      'game.play': 'Play',

      // ---- Sections ----
      'section.games': 'Games',
      'section.tap_to_play': 'Tap a card to play',
      'section.rooms': 'Escape Rooms',
      'section.loading_rooms': 'Loading rooms…',
      'section.top_players': 'Top Players',
      'section.view_all': 'View all →',
      'section.leaderboard': 'Global Leaderboard',
      'section.refresh': 'Refresh',

      // ---- Bottom nav ----
      'bottom.games': 'Games',
      'bottom.board': 'Board',
      'bottom.stats': 'Stats',
      'bottom.settings': 'Settings',

      // ---- Footer ----
      'footer.version': 'PuzzleWorld · v1.0',
      'footer.terms': 'Terms & Privacy',

      // ---- Settings ----
      'settings.sound': 'Sound effects',
      'settings.sound_sub': 'Synthesized UI tones',
      'settings.language': 'Language',
      'settings.language_sub': 'English · 한국어',
      'settings.account': 'Account',
      'settings.reset': 'Reset progress',
      'settings.reset_sub': 'Clears score, streaks & solved levels',
      'settings.reset_btn': 'Reset',
      'settings.terms': 'Terms & Privacy',
      'settings.logout': 'Log out',
      'settings.create_account': 'Create account',
      'settings.profile': 'Profile',
      'settings.sign_in': 'Sign in',

      // ---- Rooms / levels ----
      'rooms.offline': 'Rooms offline',
      'rooms.offline_sub': 'Start the backend to load rooms.',
      'rooms.levels': '{n} Levels',
      'rooms.enter': 'Enter',
      'rooms.level': 'Level {n}',
      'rooms.no_levels': 'No levels yet.',
      'rooms.loading_levels': 'Loading levels…',
      'rooms.load_error': "Couldn't load levels",
      'rooms.levels_count': '{n} levels',
      'rooms.summary': '{rooms} rooms · {levels} levels',

      // ---- Game session ----
      'game.levels': 'Levels',
      'game.back': '← Levels',
      'game.hint': 'Hint',
      'game.no_more_hints': 'No more hints.',
      'game.level_solved': 'Level solved!',
      'game.locked_out': 'Locked out',
      'game.try_again': 'Give it another try.',
      'game.next_level': 'Next level',
      'game.retry': 'Retry',
      'game.close': 'Close',
      'game.puzzle': 'Puzzle',

      // ---- Leaderboard ----
      'lb.no_scores': 'No scores yet — be the first!',
      'lb.solved': '{n} solved',
      'lb.you': '(you)',
      'lb.offline': 'Leaderboard unavailable (offline)',
      'lb.player': 'Player',

      // ---- Stats ----
      'stat.puzzles_solved': 'Puzzles solved',
      'stat.total_score': 'Total score',
      'stat.best_streak': 'Best streak',
      'stat.games_played': 'Games played',
      'stat.hints_used': 'Hints used',
      'stat.global_rank': 'Global rank',
      'stat.signed_in_as': 'Signed in as',

      // ---- Daily challenge ----
      'daily.title': 'Daily Challenge',
      'daily.sub': 'Play the full daily puzzle in the PuzzleWorld mobile app, or mark it solved here to claim your points.',
      'daily.got_it': 'Got it',
      'daily.mark_solved': 'Mark solved',
      'daily.solved': 'Daily challenge solved! Points added.',
      'daily.failed': "Couldn't complete challenge",

      // ---- Profile ----
      'pf.score': 'Score',
      'pf.rank': 'Rank',
      'pf.solved': 'Solved',
      'pf.best_streak': 'Best Streak',
      'pf.badges': 'Badges',
      'pf.close': 'Close',
      'pf.reset': 'Reset progress',

      // ---- Terms ----
      'terms.title': 'Terms & Privacy Policy',
      'terms.sub': 'PuzzleWorld · effective 2026',
      'terms.body': 'By using PuzzleWorld you agree to play fairly. Scores and progress may be stored locally and, if you connect, synced to our servers. Don\'t attempt to exploit or disrupt the service.',
      'terms.privacy': 'We store only what\'s needed to run the game: a player ID, your chosen username, and gameplay stats (score, streaks, solved levels). Passwords are salted and hashed — never stored in plaintext. We never sell your data.',
      'terms.accept': 'Accept & Close',

      // ---- Toasts / banners ----
      'toast.sound_on': 'Sound on',
      'toast.sound_off': 'Sound muted',
      'toast.lang_ko': '한국어로 설정되었습니다',
      'toast.lang_en': 'Language set to English',

      // ---- Auth page ----
      'auth.welcome_back': 'Welcome back',
      'auth.sign_in_sub': 'Sign in to continue your progress.',
      'auth.create_account': 'Create your account',
      'auth.join_sub': 'Join PuzzleWorld and sync your progress.',
      'auth.sign_in': 'Sign In',
      'auth.create_btn': 'Create Account',
      'auth.email': 'Email address',
      'auth.password': 'Password',
      'auth.confirm': 'Confirm password',
      'auth.verify_title': 'Verify your email',
      'auth.verify_sub': 'We sent a 6-digit code to {email}.',
      'auth.verify': 'Verify',
      'auth.otp': 'Verification code',
      'auth.otp_hint': 'Enter the 6-digit code sent to your email.',
      'auth.resend': 'Resend code',
      'auth.username_title': 'Choose your username',
      'auth.username_sub': 'Almost done — pick the name that shows on the leaderboard.',
      'auth.username': 'Choose a username',
      'auth.username_hint': 'Pick a unique name (3–32 characters) for the leaderboard.',
      'auth.save_username': 'Save username',
      'auth.guest': 'Continue as guest',
      'auth.new_here': 'New here?',
      'auth.have_account': 'Already have an account?',
      'auth.enter_email': 'Please enter your email address.',
      'auth.invalid_email': 'Please enter a valid email address.',
      'auth.enter_password': 'Please enter a password.',
      'auth.password_len': 'Password must be at least 8 characters.',
      'auth.password_policy': 'Password needs an uppercase letter, a lowercase letter, and a number.',
      'auth.password_mismatch': 'Passwords do not match.',
      'auth.accept_terms': 'Please accept the Terms of Service and Privacy Policy.',
      'auth.enter_otp': 'Please enter the 6-digit code.',
      'auth.verify_failed': 'Verification failed.',
      'auth.username_len': 'Username must be 3–32 characters.',
      'auth.could_not_save': 'Could not save username.',
      'auth.welcome': 'Welcome, {name}!',
      'auth.resend_done': 'A new code has been sent.',
      'auth.resend_failed': 'Could not resend the code.',
      'auth.new_code_sent': 'Check your email for the code.',
      'auth.something_wrong': 'Something went wrong. Please try again.',
      'banner.leaderboard_refreshed': 'Leaderboard refreshed',
      'banner.reset_done': 'Progress reset — starting fresh!',
      'banner.reset_failed': 'Reset failed',
      'banner.no_rooms': 'No rooms available yet.',
      'banner.failed_load_levels': 'Failed to load levels',
      'banner.no_engine': 'No engine for puzzle type',

      // ---- Difficulties & themes ----
      'diff.easy': 'easy',
      'diff.medium': 'medium',
      'diff.hard': 'hard',
      'diff.expert': 'expert',
      'theme.industrial': 'Industrial',
      'theme.laboratory': 'Laboratory',
      'theme.observatory': 'Observatory',
      'theme.study': 'Study',
      'theme.vault': 'Vault',
      'theme.alchemy': 'Alchemy',
      'theme.cartography': 'Cartography',
      'theme.forge': 'Forge',
      'theme.arcade': 'Arcade',
      'theme.mystery': 'Mystery',

      // ---- Room titles ----
      'room.room_01_vault': 'The First Vault',
      'room.room_02_cipher_lab': 'The Cipher Laboratory',
      'room.room_03_observatory': 'The Clockwork Observatory',
      'room.room_04_study': "The Puzzle Master's Study",
      'room.room_05_crypto': "The Cryptographer's Cache",
      'room.room_06_alchemy': "The Alchemist's Laboratory",
      'room.room_07_cartography': "The Cartographer's Vault",
      'room.room_08_locksmith': "The Locksmith's Forge",
      'room.room_09_arcade': 'The Neon Arcade',

      // ---- Puzzle / mechanic types ----
      'puzzle.vault': 'Vault',
      'puzzle.cipher': 'Cipher',
      'puzzle.gear': 'Gear',
      'puzzle.sliding_tile': 'Sliding Tile',
      'puzzle.terminal': 'Terminal',
      'puzzle.potion': 'Potion',
      'puzzle.maze': 'Maze',
      'puzzle.pattern_lock': 'Pattern Lock',
      'puzzle.simon': 'Color Sequence',
      'puzzle.puzzle': 'Puzzle',
    },

    ko: {
      // ---- App bar / nav ----
      'nav.search': '게임 및 방 검색…',
      'nav.get_app': '앱 받기',
      'nav.sign_in': '로그인',
      'nav.mute': '소리 끄기',
      'nav.unmute': '소리 켜기',
      'menu.profile': '프로필',
      'menu.settings': '설정',
      'menu.terms': '약관 및 개인정보',
      'menu.logout': '로그아웃',
      'menu.login': '로그인',

      // ---- Hero ----
      'hero.welcome': '다시 오신 것을 환영합니다, {name}',
      'hero.sub': '오늘의 도전을 준비하셨나요?',
      'hero.games_played': '플레이한 게임',
      'hero.high_score': '최고 점수',
      'hero.global_rank': '글로벌 순위',
      'hero.play_now': '지금 플레이',
      'hero.surprise': '깜짝 선택',
      'hero.resume': '이어하기',

      // ---- Dashboard tabs ----
      'tab.games': '모든 게임',
      'tab.board': '리더보드',
      'tab.stats': '내 통계',
      'tab.settings': '설정',

      // ---- Category chips ----
      'cat.all': '전체',
      'cat.escape': '탈출',
      'cat.puzzle': '퍼즐',
      'cat.word': '단어',
      'cat.daily': '일일',

      // ---- Games catalogue ----
      'game.rooms.title': '탈출 방',
      'game.rooms.desc': '테마 방 9개 · 두뇌를 깨우는 레벨 90개 (7가지 장치).',
      'game.sudoku.title': '스도쿠',
      'game.sudoku.desc': '메모, 실행 취소, 힌트, 실시간 타이머가 있는 클래식 9×9 논리 퍼즐.',
      'game.word.title': '단어 맞추기',
      'game.word.desc': '시간과 대결하여 단어를 맞춰보세요. 연속 성공 시 점수가 배가됩니다.',
      'game.daily.title': '일일 도전',
      'game.daily.desc': '매일 새로운 퍼즐. 글로벌 리더보드에서 경쟁하세요.',
      'game.rooms.chip1': '방 9개',
      'game.rooms.chip2': '레벨 90개',
      'game.sudoku.chip1': '논리',
      'game.sudoku.chip2': '난이도 4개',
      'game.word.chip1': '단어 게임',
      'game.word.chip2': '연속',
      'game.daily.chip1': '일일',
      'game.daily.chip2': '글로벌',
      'game.play': '플레이',

      // ---- Sections ----
      'section.games': '게임',
      'section.tap_to_play': '카드를 눌러 플레이',
      'section.rooms': '탈출 방',
      'section.loading_rooms': '방 불러오는 중…',
      'section.top_players': '상위 플레이어',
      'section.view_all': '전체 보기 →',
      'section.leaderboard': '글로벌 리더보드',
      'section.refresh': '새로고침',

      // ---- Bottom nav ----
      'bottom.games': '게임',
      'bottom.board': '순위',
      'bottom.stats': '통계',
      'bottom.settings': '설정',

      // ---- Footer ----
      'footer.version': 'PuzzleWorld · v1.0',
      'footer.terms': '약관 및 개인정보',

      // ---- Settings ----
      'settings.sound': '효과음',
      'settings.sound_sub': '합성 UI 효과음',
      'settings.language': '언어',
      'settings.language_sub': 'English · 한국어',
      'settings.account': '계정',
      'settings.reset': '진행 상황 초기화',
      'settings.reset_sub': '점수, 연속 기록, 해결한 레벨을 지웁니다',
      'settings.reset_btn': '초기화',
      'settings.terms': '약관 및 개인정보',
      'settings.logout': '로그아웃',
      'settings.create_account': '계정 만들기',
      'settings.profile': '프로필',
      'settings.sign_in': '로그인',

      // ---- Rooms / levels ----
      'rooms.offline': '방 오프라인',
      'rooms.offline_sub': '백엔드를 실행하면 방이 로드됩니다.',
      'rooms.levels': '레벨 {n}개',
      'rooms.enter': '입장',
      'rooms.level': '레벨 {n}',
      'rooms.no_levels': '아직 레벨이 없습니다.',
      'rooms.loading_levels': '레벨 불러오는 중…',
      'rooms.load_error': '레벨을 불러오지 못했습니다',
      'rooms.levels_count': '레벨 {n}개',
      'rooms.summary': '{rooms}개 방 · 레벨 {levels}개',

      // ---- Game session ----
      'game.levels': '레벨',
      'game.back': '← 레벨',
      'game.hint': '힌트',
      'game.no_more_hints': '더 이상 힌트가 없습니다.',
      'game.level_solved': '레벨 해결!',
      'game.locked_out': '잠겼습니다',
      'game.try_again': '다시 시도해 보세요.',
      'game.next_level': '다음 레벨',
      'game.retry': '재시도',
      'game.close': '닫기',
      'game.puzzle': '퍼즐',

      // ---- Leaderboard ----
      'lb.no_scores': '아직 점수가 없습니다 — 1등이 되어보세요!',
      'lb.solved': '{n}개 해결',
      'lb.you': '(나)',
      'lb.offline': '리더보드를 사용할 수 없습니다 (오프라인)',
      'lb.player': '플레이어',

      // ---- Stats ----
      'stat.puzzles_solved': '해결한 퍼즐',
      'stat.total_score': '총 점수',
      'stat.best_streak': '최고 연속 기록',
      'stat.games_played': '플레이한 게임',
      'stat.hints_used': '사용한 힌트',
      'stat.global_rank': '글로벌 순위',
      'stat.signed_in_as': '로그인 계정',

      // ---- Daily challenge ----
      'daily.title': '일일 도전',
      'daily.sub': '전체 일일 퍼즐은 PuzzleWorld 모바일 앱에서 플레이하거나, 여기서 해결로 표시하여 포인트를 받으세요.',
      'daily.got_it': '확인',
      'daily.mark_solved': '해결로 표시',
      'daily.solved': '일일 도전 해결! 포인트가 추가되었습니다.',
      'daily.failed': '도전을 완료하지 못했습니다',

      // ---- Profile ----
      'pf.score': '점수',
      'pf.rank': '순위',
      'pf.solved': '해결',
      'pf.best_streak': '최고 연속',
      'pf.badges': '배지',
      'pf.close': '닫기',
      'pf.reset': '진행 초기화',

      // ---- Terms ----
      'terms.title': '약관 및 개인정보처리방침',
      'terms.sub': 'PuzzleWorld · 2026년 시행',
      'terms.body': 'PuzzleWorld를 사용하면 공정하게 플레이할 것에 동의하는 것입니다. 점수와 진행 상황은 로컬에 저장될 수 있으며, 연결 시 서버에 동기화됩니다. 서비스를 악용하거나 방해하려 하지 마세요.',
      'terms.privacy': '게임 실행에 필요한 정보만 저장합니다: 플레이어 ID, 선택한 사용자 이름, 게임 통계(점수, 연속 기록, 해결한 레벨). 비밀번호는 솔트 처리 후 해시되며 평문으로 저장되지 않습니다. 데이터를 판매하지 않습니다.',
      'terms.accept': '동의 및 닫기',

      // ---- Toasts / banners ----
      'toast.sound_on': '소리 켜짐',
      'toast.sound_off': '소리 꺼짐',
      'toast.lang_ko': '한국어로 설정되었습니다',
      'toast.lang_en': 'Language set to English',

      // ---- Auth page ----
      'auth.welcome_back': '다시 오신 것을 환영합니다',
      'auth.sign_in_sub': '진행 상황을 이어가려면 로그인하세요.',
      'auth.create_account': '계정 만들기',
      'auth.join_sub': 'PuzzleWorld에 가입하고 진행 상황을 동기화하세요.',
      'auth.sign_in': '로그인',
      'auth.create_btn': '계정 만들기',
      'auth.email': '이메일 주소',
      'auth.password': '비밀번호',
      'auth.confirm': '비밀번호 확인',
      'auth.verify_title': '이메일 인증',
      'auth.verify_sub': '{email}(으)로 6자리 코드를 보냈습니다.',
      'auth.verify': '인증',
      'auth.otp': '인증 코드',
      'auth.otp_hint': '이메일로 받은 6자리 코드를 입력하세요.',
      'auth.resend': '코드 다시 받기',
      'auth.username_title': '사용자 이름 선택',
      'auth.username_sub': '거의 다 됐어요 — 리더보드에 표시될 이름을 정하세요.',
      'auth.username': '사용자 이름 선택',
      'auth.username_hint': '리더보드에 표시될 고유한 이름(3–32자)을 정하세요.',
      'auth.save_username': '이름 저장',
      'auth.guest': '게스트로 계속하기',
      'auth.new_here': '처음이신가요?',
      'auth.have_account': '이미 계정이 있으신가요?',
      'auth.enter_email': '이메일 주소를 입력하세요.',
      'auth.invalid_email': '유효한 이메일 주소를 입력하세요.',
      'auth.enter_password': '비밀번호를 입력하세요.',
      'auth.password_len': '비밀번호는 8자 이상이어야 합니다.',
      'auth.password_policy': '비밀번호에 대문자, 소문자, 숫자가 각각 포함되어야 합니다.',
      'auth.password_mismatch': '비밀번호가 일치하지 않습니다.',
      'auth.accept_terms': '이용약관 및 개인정보처리방침에 동의해 주세요.',
      'auth.enter_otp': '6자리 코드를 입력하세요.',
      'auth.verify_failed': '인증에 실패했습니다.',
      'auth.username_len': '사용자 이름은 3–32자여야 합니다.',
      'auth.could_not_save': '사용자 이름을 저장하지 못했습니다.',
      'auth.welcome': '환영합니다, {name}!',
      'auth.resend_done': '새 코드를 보냈습니다.',
      'auth.resend_failed': '코드를 다시 보내지 못했습니다.',
      'auth.new_code_sent': '이메일에서 코드를 확인하세요.',
      'auth.something_wrong': '문제가 발생했습니다. 다시 시도해 주세요.',
      'banner.leaderboard_refreshed': '리더보드를 새로고침했습니다',
      'banner.reset_done': '진행 상황이 초기화되었습니다!',
      'banner.reset_failed': '초기화 실패',
      'banner.no_rooms': '아직 사용 가능한 방이 없습니다.',
      'banner.failed_load_levels': '레벨을 불러오지 못했습니다',
      'banner.no_engine': '해당 퍼즐 유형의 엔진이 없습니다',

      // ---- Difficulties & themes ----
      'diff.easy': '쉬움',
      'diff.medium': '보통',
      'diff.hard': '어려움',
      'diff.expert': '전문가',
      'theme.industrial': '산업',
      'theme.laboratory': '연구소',
      'theme.observatory': '천문대',
      'theme.study': '서재',
      'theme.vault': '금고',
      'theme.alchemy': '연금술',
      'theme.cartography': '지도 제작',
      'theme.forge': '대장간',
      'theme.arcade': '아케이드',
      'theme.mystery': '미스터리',

      // ---- Room titles ----
      'room.room_01_vault': '첫 번째 금고',
      'room.room_02_cipher_lab': '암호 연구소',
      'room.room_03_observatory': '태엽 천문대',
      'room.room_04_study': '퍼즐 마스터의 서재',
      'room.room_05_crypto': '암호학자의 은닉처',
      'room.room_06_alchemy': '연금술사의 연구실',
      'room.room_07_cartography': '지도 제작자의 금고',
      'room.room_08_locksmith': '자물쇠 장인의 대장간',
      'room.room_09_arcade': '네온 아케이드',

      // ---- Puzzle / mechanic types ----
      'puzzle.vault': '금고',
      'puzzle.cipher': '암호',
      'puzzle.gear': '기어',
      'puzzle.sliding_tile': '슬라이딩 타일',
      'puzzle.terminal': '터미널',
      'puzzle.potion': '물약',
      'puzzle.maze': '미로',
      'puzzle.pattern_lock': '패턴 잠금',
      'puzzle.simon': '색 순서',
      'puzzle.puzzle': '퍼즐',
    },
  };

  const SUPPORTED = ['en', 'ko'];

  function getLang() {
    return localStorage.getItem('pw_lang') || 'en';
  }

  function t(key, vars) {
    const lang = getLang();
    let msg = (CATALOGS[lang] && CATALOGS[lang][key]) || CATALOGS.en[key] || key;
    if (vars) {
      for (const k of Object.keys(vars)) {
        msg = msg.split('{' + k + '}').join(String(vars[k]));
      }
    }
    return msg;
  }

  // Translate a room title from its room_id, falling back to the raw name.
  function roomName(roomId, fallback) {
    const key = 'room.' + roomId;
    const translated = t(key);
    return (translated !== key) ? translated : (fallback || translated);
  }

  // Translate a mechanic/puzzle_type token like "sliding_tile" or "sliding tile".
  function puzzleType(raw) {
    const norm = String(raw || 'puzzle').trim().toLowerCase().replace(/\s+/g, '_');
    const key = 'puzzle.' + norm;
    const translated = t(key);
    return translated !== key ? translated : String(raw || '').replace(/_/g, ' ');
  }

  function apply(root) {
    root = root || document;
    root.querySelectorAll('[data-i18n]').forEach((el) => {
      el.textContent = t(el.getAttribute('data-i18n'));
    });
    root.querySelectorAll('[data-i18n-placeholder]').forEach((el) => {
      el.setAttribute('placeholder', t(el.getAttribute('data-i18n-placeholder')));
    });
    root.querySelectorAll('[data-i18n-title]').forEach((el) => {
      el.setAttribute('title', t(el.getAttribute('data-i18n-title')));
    });
    root.querySelectorAll('[data-i18n-aria-label]').forEach((el) => {
      el.setAttribute('aria-label', t(el.getAttribute('data-i18n-aria-label')));
    });
  }

  function setLang(lang, opts) {
    const l = SUPPORTED.includes(lang) ? lang : 'en';
    localStorage.setItem('pw_lang', l);
    document.documentElement.lang = l === 'ko' ? 'ko' : 'en';
    apply();
    if (opts && opts.persist) {
      fetch('/api/auth/language', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ language: l }),
      }).catch(() => { /* guest / offline */ });
    }
    window.dispatchEvent(new CustomEvent('pw:lang', { detail: { lang: l } }));
    return l;
  }

  window.I18N = { CATALOGS, SUPPORTED, getLang, setLang, t, roomName, puzzleType, apply };
  window.t = t;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => { apply(); });
  } else {
    apply();
  }
})();
