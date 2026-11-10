/* =========================================================
   PuzzleWorld web portal — glassmorphic dashboard
   ========================================================= */
(() => {
  'use strict';

  const API = '/api';

  // ---------- DOM helpers ----------
  const $  = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const el = (tag, cls, html) => {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  };
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => (
    { '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]
  ));
  const debounce = (fn, ms) => {
    let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
  };

  // =========================================================
  //  AUDIO MANAGER — synthesized effects, autoplay-safe
  // =========================================================
  const Audio = (() => {
    let ctx = null, master = null;
    let muted = localStorage.getItem('pw_muted') === '1';
    let armed = false;

    function ensure() {
      if (ctx) return ctx;
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      try {
        ctx = new AC();
        master = ctx.createGain();
        master.gain.value = muted ? 0 : 0.55;
        master.connect(ctx.destination);
      } catch (_) { return null; }
      return ctx;
    }
    function arm() {
      const c = ensure();
      if (!c) return;
      if (c.state === 'suspended') { const p = c.resume(); if (p && p.catch) p.catch(() => {}); }
      if (!armed) {
        armed = true;
        try {
          const o = c.createOscillator(), g = c.createGain();
          g.gain.value = 0.0001;
          o.connect(g).connect(master);
          o.start(); o.stop(c.currentTime + 0.02);
        } catch (_) {}
      }
    }
    ['pointerdown', 'keydown', 'touchstart'].forEach(ev =>
      document.addEventListener(ev, arm, { passive: true }));
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && ctx && ctx.state === 'suspended') arm();
    });

    function tone(freq, dur, type = 'sine', gain = 0.25, when = 0) {
      const c = ensure(); if (!c || muted) return;
      if (c.state === 'suspended') { const r = c.resume(); if (r && r.catch) r.catch(() => {}); }
      const t = c.currentTime + when;
      const o = c.createOscillator(), g = c.createGain();
      o.type = type;
      o.frequency.setValueAtTime(freq, t);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(gain, t + 0.012);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
      o.connect(g).connect(master);
      o.start(t); o.stop(t + dur + 0.02);
    }
    function sweep(f1, f2, dur, type = 'sine', gain = 0.22) {
      const c = ensure(); if (!c || muted) return;
      if (c.state === 'suspended') { const r = c.resume(); if (r && r.catch) r.catch(() => {}); }
      const t = c.currentTime;
      const o = c.createOscillator(), g = c.createGain();
      o.type = type;
      o.frequency.setValueAtTime(f1, t);
      o.frequency.exponentialRampToValueAtTime(Math.max(f2, 1), t + dur);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(gain, t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
      o.connect(g).connect(master);
      o.start(t); o.stop(t + dur + 0.03);
    }
    const sfx = {
      click:   () => tone(520, 0.06, 'triangle', 0.18),
      nav:     () => { tone(440, 0.05, 'sine', 0.16); tone(660, 0.07, 'sine', 0.12, 0.05); },
      move:    () => tone(360, 0.04, 'square', 0.12),
      open:    () => sweep(300, 720, 0.16, 'sine', 0.2),
      close:   () => sweep(620, 300, 0.14, 'sine', 0.16),
      win:     () => [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(f, 0.22, 'sine', 0.24, i * 0.09)),
      loss:    () => { sweep(380, 120, 0.45, 'sawtooth', 0.18); tone(140, 0.3, 'sine', 0.12, 0.05); },
      error:   () => { tone(200, 0.12, 'sawtooth', 0.18); tone(160, 0.16, 'sawtooth', 0.16, 0.1); },
      success: () => { tone(660, 0.1, 'triangle', 0.2); tone(990, 0.16, 'triangle', 0.16, 0.08); },
      toggle:  () => tone(720, 0.07, 'sine', 0.16),
      tick:    () => tone(880, 0.025, 'square', 0.06),
    };
    function setMuted(v) {
      muted = v; localStorage.setItem('pw_muted', v ? '1' : '0');
      if (master) master.gain.value = v ? 0 : 0.55;
      if (!v) arm();
    }
    return { sfx, setMuted, isMuted: () => muted, unlock: arm };
  })();

  // =========================================================
  //  UI: toasts, banners, loader, modal, button spinner
  // =========================================================
  const toastWrap = $('#toastWrap');
  function toast(msg, type = 'info', ttl = 3200) {
    const icons = { success: '✅', error: '⚠️', info: 'ℹ️' };
    const t = el('div', `toast toast--${type}`,
      `<span class="toast__icon">${icons[type] || 'ℹ️'}</span><span>${esc(msg)}</span>`);
    toastWrap.appendChild(t);
    setTimeout(() => { t.classList.add('is-out'); setTimeout(() => t.remove(), 240); }, ttl);
  }

  const bannerWrap = $('#bannerWrap');
  function showBanner(type, msg, ttl = 5000) {
    const icons = { success: '✅', error: '⚠️', info: 'ℹ️' };
    const b = el('div', `banner banner--${type}`, `
      <span class="banner__icon">${icons[type] || 'ℹ️'}</span>
      <span class="banner__text">${esc(msg)}</span>
      <button class="banner__close" aria-label="Dismiss">×</button>`);
    bannerWrap.appendChild(b);
    const remove = () => { b.classList.add('is-out'); setTimeout(() => b.remove(), 300); };
    b.querySelector('.banner__close').addEventListener('click', remove);
    if (ttl) setTimeout(remove, ttl);
  }

  const loader = $('#loader');
  const showLoader = () => loader.classList.add('is-active');
  const hideLoader = () => loader.classList.remove('is-active');

  async function withButtonSpinner(btn, fn) {
    if (!btn) return fn();
    btn.classList.add('is-loading'); btn.disabled = true;
    try { return await fn(); }
    finally { btn.classList.remove('is-loading'); btn.disabled = false; }
  }

  const modal = $('#modal');
  const modalBody = $('#modalBody');
  function openModal(html) {
    const wasOpen = modal.classList.contains('is-open');
    if (wasOpen) modalBody.style.opacity = '0';
    modalBody.innerHTML = html;
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    if (wasOpen) {
      requestAnimationFrame(() => {
        modalBody.style.opacity = '1';
        setTimeout(() => { modalBody.style.opacity = ''; }, 160);
      });
    } else {
      Audio.sfx.open();
    }
  }
  function closeModal() {
    if (!modal.classList.contains('is-open') || modal.classList.contains('is-out')) return;
    modal.classList.add('is-out');
    Audio.sfx.close();
    setTimeout(() => {
      modal.classList.remove('is-open', 'is-out');
      modal.setAttribute('aria-hidden', 'true');
    }, 260);
  }
  $('#modalClose')?.addEventListener('click', closeModal);
  modal?.addEventListener('click', (e) => { if (e.target.dataset.close !== undefined) closeModal(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { closeModal(); } });

  // =========================================================
  //  Networking + identity
  // =========================================================
  async function api(path, opts) {
    const res = await fetch(API + path, { headers: { 'Content-Type': 'application/json' }, ...opts });
    if (!res.ok) {
      let msg = `HTTP ${res.status}`;
      try { const j = await res.json(); if (j.error) msg = j.error; } catch (_) {}
      throw new Error(msg);
    }
    return res.json();
  }

  const uid = (() => {
    let id = localStorage.getItem('pw_user_id');
    if (!id) { id = 'web_' + Math.random().toString(36).slice(2, 10); localStorage.setItem('pw_user_id', id); }
    return id;
  })();
  const uname = localStorage.getItem('pw_username') || 'Guest';
  const isSignedIn = uname !== 'Guest';

  // =========================================================
  //  Data: games catalogue + room styles
  // =========================================================
  const GAMES = [
    { id: 'rooms', cls: 'blue', emoji: '🚪', title: 'Escape Rooms', cat: 'escape', rating: '4.9',
      difficulty: 'easy', desc: '9 themed rooms · 90 brain-melting levels across 7 mechanics.',
      chips: [['9 Rooms', 'chip'], ['90 Levels', 'chip--purple']] },
    { id: 'sudoku', cls: 'purple', emoji: '🔢', title: 'Sudoku', cat: 'puzzle', rating: '4.7',
      difficulty: 'medium', desc: 'Classic 9×9 logic with notes, undo, hints and a live timer.',
      chips: [['Logic', 'chip--purple'], ['4 Difficulties', 'chip--emerald']] },
    { id: 'word', cls: 'emerald', emoji: '🔤', title: 'Word Descrambler', cat: 'word', rating: '4.6',
      difficulty: 'medium', desc: 'Unscramble words against the clock. Streaks multiply your score.',
      chips: [['Word Game', 'chip--emerald'], ['Streaks', 'chip--rose']] },
    { id: 'daily', cls: 'amber', emoji: '📅', title: 'Daily Challenge', cat: 'daily', rating: '4.8',
      difficulty: 'hard', desc: 'One fresh puzzle every day. Compete on the global leaderboard.',
      chips: [['Daily', 'chip--amber'], ['Global', 'chip']] },
  ];
  const DIFF_BADGE = { easy: 'badge--easy', medium: 'badge--medium', hard: 'badge--hard', expert: 'badge--expert' };
  const ROOM_STYLES = ['blue', 'purple', 'emerald', 'rose', 'amber'];
  const ROOM_EMOJI  = ['🏦', '🔬', '🔭', '📚', '🔐', '⚗️', '🗺️', '🔑', '🕹️'];
  const ROOM_DIFF    = ['easy', 'medium', 'hard', 'expert'];

  let ROOMS = [];
  let activeCat = 'all';
  let searchQuery = '';
  let STATS = {};
  let RANK = null;
  const SOLVED = new Set();   // level_ids solved this session
  let currentGame = null;     // { room, level, levelIndex, render, hintIndex }
  let lastRoom = null;        // { data, room } for back-navigation
  let cachedBadges = new Map(); // badge_id -> boolean (was unlocked last time we fetched)

  // =========================================================
  //  Render: games grid
  // =========================================================
  function renderGames() {
    const grid = $('#gamesGrid');
    grid.innerHTML = '';
    GAMES.forEach((g, i) => {
      const badge = DIFF_BADGE[g.difficulty] || 'badge--medium';
      const card = el('button', `card card--${g.cls}`, `
        <div class="card__art">${g.emoji}</div>
        <div class="card__body">
          <h3 class="card__title">${esc(g.title)}</h3>
          <p class="card__desc">${esc(g.desc)}</p>
          <div class="card__meta">
            <span class="badge ${badge}">${esc(g.difficulty)}</span>
            ${g.chips.map(([t, c]) => `<span class="chip ${c}">${esc(t)}</span>`).join('')}
          </div>
          <div class="card__footer">
            <span class="card__rating"><span class="card__stars">★★★★★</span> ${g.rating}</span>
            <span class="card__play">▶ Play</span>
          </div>
        </div>`);
      card.dataset.title = g.title.toLowerCase();
      card.dataset.cat = g.cat;
      card.style.opacity = '0';
      card.style.transform = 'translateY(14px)';
      card.style.transition = 'opacity .35s var(--ease), transform .35s var(--ease)';
      card.style.transitionDelay = `${i * 55}ms`;
      card.addEventListener('click', () => { Audio.sfx.click(); onGameClick(g.id); });
      grid.appendChild(card);
      requestAnimationFrame(() => setTimeout(() => {
        card.style.opacity = '1'; card.style.transform = 'translateY(0)';
      }, 30));
    });
  }

  function onGameClick(id) {
    if (id === 'rooms') {
      $('#roomsSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
      Audio.sfx.nav();
    } else if (id === 'daily') {
      loadDaily(true);
    } else {
      showBanner('info', `${id === 'sudoku' ? 'Sudoku' : 'Word Descrambler'} — full play is in the PuzzleWorld mobile app. This portal previews the catalogue.`);
      Audio.sfx.click();
    }
  }

  // =========================================================
  //  Render: escape rooms
  // =========================================================
  async function loadRooms() {
    const grid = $('#roomsGrid');
    grid.innerHTML = '<div class="skeleton"></div>'.repeat(6);
    try {
      const data = await api('/rooms');
      ROOMS = data.rooms || [];
      renderRooms();
    } catch (e) {
      grid.innerHTML = '<div class="empty"><span class="empty__icon">📡</span><span class="empty__title">Rooms offline</span><span>Start the backend to load rooms.</span></div>';
      showBanner('error', `Couldn't load rooms: ${e.message}`);
      Audio.sfx.error();
    }
  }

  function renderRooms() {
    const grid = $('#roomsGrid');
    grid.innerHTML = '';
    ROOMS.forEach((r, i) => {
      const cls = ROOM_STYLES[i % ROOM_STYLES.length];
      const emoji = ROOM_EMOJI[i % ROOM_EMOJI.length];
      const stars = (4.5 + (i % 5) * 0.1).toFixed(1);
      const diff = ROOM_DIFF[i % ROOM_DIFF.length];
      const card = el('button', `card card--${cls}`, `
        <div class="card__art">${emoji}</div>
        <div class="card__body">
          <h3 class="card__title">${esc(r.room_name)}</h3>
          <p class="card__desc">${esc(r.description || 'A themed escape room.')}</p>
          <div class="card__meta">
            <span class="chip">${r.level_count || 10} Levels</span>
            <span class="chip chip--purple">${esc(r.theme || 'Mystery')}</span>
            <span class="badge ${DIFF_BADGE[diff] || 'badge--medium'}">${esc(diff)}</span>
          </div>
          <div class="card__footer">
            <span class="card__rating"><span class="card__stars">★★★★★</span> ${stars}</span>
            <span class="card__play">▶ Enter</span>
          </div>
        </div>`);
      card.dataset.search = `${r.room_name || ''} ${r.theme || ''} ${r.description || ''}`.toLowerCase();
      card.addEventListener('click', () => { Audio.sfx.click(); openRoom(r); });
      grid.appendChild(card);
    });
    const totalLevels = ROOMS.reduce((n, r) => n + (r.level_count || 0), 0);
    $('#roomsHint').textContent = `${ROOMS.length} rooms · ${totalLevels} levels`;
    applyFilters();
  }

  function renderRoomList(data, room) {
    const wasOpen = modal.classList.contains('is-open');
    if (wasOpen) modalBody.style.opacity = '0';
    const levels = data.levels || [];
    const rows = levels.map((l, i) => {
      const done = SOLVED.has(l.level_id);
      return `
        <button class="level-row" data-level="${esc(l.level_id)}" data-idx="${i}">
          <span class="level-row__num">${i + 1}</span>
          <span class="level-row__body">
            <span class="level-row__title">${esc(l.title || 'Level ' + (i + 1))}</span>
            <span class="level-row__mech">${esc((l.mechanic?.mechanic_type || l.puzzle_type || 'puzzle').replace(/_/g, ' '))} · ${esc(l.difficulty || '')}</span>
          </span>
          <span class="level-row__state">${done ? '✅' : '▶'}</span>
        </button>`;
    }).join('');
    modalBody.innerHTML = `
      <h3>${esc(data.room_name || room.room_name)}</h3>
      <p class="sub">${esc(data.description || '')} · ${levels.length} levels</p>
      <div class="level-list">${rows || '<p class="sub">No levels yet.</p>'}</div>`;
    if (wasOpen) {
      requestAnimationFrame(() => {
        modalBody.style.opacity = '1';
        setTimeout(() => { modalBody.style.opacity = ''; }, 160);
      });
    }
    $$('.level-row', modalBody).forEach((btn, i) => {
      btn.addEventListener('click', () => {
        Audio.sfx.move();
        launchLevel(data, levels[i], i);
      });
    });
  }

  async function openRoom(room) {
    localStorage.setItem('pw_last_room_id', room.room_id);
    localStorage.setItem('pw_last_room_name', room.room_name);
    showResumeBtn();
    openModal(`<h3>${esc(room.room_name)}</h3><p class="sub">Loading levels…</p>`);
    showLoader();
    try {
      const data = await api(`/rooms/${encodeURIComponent(room.room_id)}`);
      lastRoom = { data, room };
      renderRoomList(data, room);
    } catch (e) {
      modalBody.innerHTML = `<h3>${esc(room.room_name)}</h3><p class="sub">Couldn't load levels: ${esc(e.message)}</p>`;
      showBanner('error', 'Failed to load levels');
      Audio.sfx.error();
    } finally {
      hideLoader();
    }
  }

  // =========================================================
  //  Minigame launcher — puzzle_type -> render via dispatcher
  // =========================================================
  function launchLevel(roomData, level, levelIndex) {
    const puzzleType = level.puzzle_type || roomData.puzzle_type || (level.mechanic && level.mechanic.mechanic_type);
    const render = window.PuzzleWorld && PuzzleWorld.dispatch(puzzleType);
    if (!render) {
      showBanner('error', `No engine for puzzle type "${puzzleType}"`);
      Audio.sfx.error();
      return;
    }
    currentGame = { room: roomData, level, levelIndex, render, hintIndex: 0 };
    startSession();
  }

  function startSession() {
    const g = currentGame;
    if (!g) return;
    const { room, level } = g;
    const puzzleType = level.puzzle_type || room.puzzle_type || (level.mechanic && level.mechanic.mechanic_type) || 'puzzle';
    const render = PuzzleWorld.dispatch(puzzleType) || g.render;
    openModal(`
      <div class="game-head">
        <div class="game-head__title">${esc(level.title || 'Puzzle')}</div>
        <div class="game-head__meta">
          <button class="text-btn" id="btnBackLevels">← Levels</button>
          <span class="chip">${esc(puzzleType.replace(/_/g, ' '))}</span>
          <span class="badge ${DIFF_BADGE[(level.difficulty || '').toLowerCase()] || 'badge--medium'}">${esc(level.difficulty || '')}</span>
          ${(level.hints && level.hints.length) ? '<button class="text-btn" id="btnHint">💡 Hint</button>' : ''}
        </div>
      </div>
      <div id="gameHints" class="g-col" style="gap:6px;margin-bottom:12px"></div>
      <div id="gameRoot" class="game-root"></div>`);

    $('#btnBackLevels')?.addEventListener('click', () => { Audio.sfx.click(); if (lastRoom) renderRoomList(lastRoom.data, lastRoom.room); });
    $('#btnHint')?.addEventListener('click', () => revealHint());

    const ctx = {
      mechanic: level.mechanic || {},
      level, room,
      root: $('#gameRoot'),
      el, esc, $, $$,
      sfx: Audio.sfx,
      toast, showBanner,
      win: () => onWin(),
      lose: () => onLose(),
    };
    render(ctx);
  }

  function revealHint() {
    const g = currentGame;
    const hints = (g && g.level && g.level.hints) || [];
    if (g.hintIndex >= hints.length) { showBanner('info', 'No more hints.'); return; }
    const box = $('#gameHints');
    box.appendChild(el('div', 'g-hint', '💡 ' + esc(hints[g.hintIndex++])));
    Audio.sfx.success();
    if (g.hintIndex >= hints.length) { const b = $('#btnHint'); if (b) b.disabled = true; }
  }

  function nextLevel(g) {
    const levels = (g.room && g.room.levels) || [];
    return levels[g.levelIndex + 1] || null;
  }

  function onWin() {
    const g = currentGame;
    if (!g) return;
    (async () => {
      try {
        const solved = { [g.level.level_id]: true };
        await api('/progress', {
          method: 'POST',
          body: JSON.stringify({ user_id: uid, username: uname, solved_levels: solved, total_attempts: 1 }),
        });
        SOLVED.add(g.level.level_id);
        loadStats(); loadLeaderboard(false);
      } catch (_) { /* sync failure is non-fatal */ }
    })();
    renderResult(true);
  }

  function onLose() {
    renderResult(false);
  }

  function renderResult(won) {
    const root = $('#gameRoot');
    if (!root) return;
    const g = currentGame;
    const next = won && g ? nextLevel(g) : null;
    root.innerHTML = `
      <div class="result">
        <div class="result__icon">${won ? '🎉' : '😵'}</div>
        <div class="result__title">${won ? 'Level solved!' : 'Locked out'}</div>
        <div class="result__sub">${won ? esc((g && g.level.title) || '') : 'Give it another try.'}</div>
        <div class="modal__actions" style="justify-content:center">
          ${next ? '<button class="btn btn--primary" id="btnNext"><span class="btn__label">▶ Next level</span></button>' : ''}
          ${won ? '' : '<button class="btn btn--primary" id="btnRetry"><span class="btn__label">↻ Retry</span></button>'}
          <button class="btn btn--ghost" id="btnDone"><span class="btn__label">Close</span></button>
        </div>
      </div>`;
    if (won) Audio.sfx.win(); else Audio.sfx.loss();
    $('#btnDone')?.addEventListener('click', () => { Audio.sfx.click(); closeModal(); });
    $('#btnRetry')?.addEventListener('click', () => { Audio.sfx.click(); startSession(); });
    $('#btnNext')?.addEventListener('click', () => {
      Audio.sfx.click();
      currentGame.level = next;
      currentGame.levelIndex += 1;
      currentGame.hintIndex = 0;
      startSession();
    });
  }

  // =========================================================
  //  Leaderboard + mini-leaderboard + rank
  // =========================================================
  function computeRank(rows) {
    const idx = rows.findIndex(r => r.user_id === uid);
    return idx >= 0 ? idx + 1 : null;
  }

  function renderMini(rows) {
    const box = $('#miniBoard');
    const top = rows.slice(0, 5);
    if (!top.length) {
      box.innerHTML = '<p style="color:var(--muted);font-size:13px">No scores yet — be the first!</p>';
      return;
    }
    const max = Math.max(1, ...top.map(r => r.solved_count || 0));
    const medals = ['🥇', '🥈', '🥉'];
    box.innerHTML = top.map((r, i) => {
      const pct = Math.round(((r.solved_count || 0) / max) * 100);
      const medal = i < 3
        ? `<span class="mini-lb__rank">${medals[i]}</span>`
        : `<span class="mini-lb__rank num">#${i + 1}</span>`;
      const barCls = i === 0 ? 'progress--gold' : i === 1 ? 'progress--silver' : i === 2 ? 'progress--bronze' : '';
      const me = r.user_id === uid ? ' is-me' : '';
      return `<div style="${i ? 'border-top:1px solid var(--stroke);padding-top:8px;margin-top:8px' : ''}">
        <div class="mini-lb__row">
          ${medal}
          <span class="mini-lb__name${me}">${esc(r.username || 'Player')}</span>
          <span class="mini-lb__solved">${r.solved_count || 0} solved</span>
        </div>
        <div class="progress ${barCls}" style="margin-left:46px"><i style="width:${pct}%"></i></div>
      </div>`;
    }).join('');
  }

  async function loadLeaderboard(showSpin) {
    const list = $('#leaderboard');
    if (showSpin) { list.innerHTML = '<li class="skeleton" style="min-height:56px"></li>'.repeat(3); }
    try {
      const data = await api('/progress');
      const rows = (data.leaderboard || []).slice(0, 10);
      RANK = computeRank(rows);
      refreshHero();
      renderMini(rows);
      if (!rows.length) {
        list.innerHTML = '<li class="lb-row"><span class="lb-name">No scores yet — be the first!</span></li>';
        return;
      }
      const medals = ['🥇', '🥈', '🥉'];
      list.innerHTML = rows.map((r, i) => `
        <li class="lb-row">
          <span class="lb-rank">${i < 3 ? medals[i] : i + 1}</span>
          <span class="lb-name${r.user_id === uid ? ' is-me' : ''}">${esc(r.username || 'Player')}${r.user_id === uid ? ' (you)' : ''}</span>
          <span class="lb-solved">${r.solved_count || 0} solved</span>
          <span class="lb-score">${(r.score || 0).toLocaleString()}</span>
        </li>`).join('');
      if (showSpin) Audio.sfx.success();
    } catch (e) {
      list.innerHTML = '<li class="lb-row"><span class="lb-name">Leaderboard unavailable (offline)</span></li>';
      showBanner('error', `Leaderboard: ${e.message}`);
      Audio.sfx.error();
    }
  }

  // =========================================================
  //  Stats: hero + detail panel
  // =========================================================
  function refreshHero() {
    $('#heroGames').textContent = (STATS.total_attempts || 0).toLocaleString();
    $('#heroScore').textContent = (STATS.total_score || 0).toLocaleString();
    $('#heroRank').textContent = RANK ? '#' + RANK : '—';
  }

  function renderStatsDetail() {
    const solved = STATS.solved_count || 0;
    const streak = STATS.best_streak || 0;
    const solvedPct = Math.min(100, Math.round(solved / 90 * 100));
    const streakPct = Math.min(100, Math.round(streak / 10 * 100));
    $('#statsDetail').innerHTML = `
      <div class="stat-block">
        <div class="stat-block__row"><span>🧩 Puzzles solved</span><strong class="grad">${solved}<small style="font-size:12px;color:var(--muted)"> / 90</small></strong></div>
        <div class="progress"><i style="width:${solvedPct}%"></i></div>
      </div>
      <div class="stat-block">
        <div class="stat-block__row"><span>⭐ Total score</span><strong class="grad">${(STATS.total_score || 0).toLocaleString()}</strong></div>
      </div>
      <div class="stat-block">
        <div class="stat-block__row"><span>🔥 Best streak</span><strong>${streak}</strong></div>
        <div class="progress progress--gold"><i style="width:${streakPct}%"></i></div>
      </div>
      <div class="stat-block">
        <div class="stat-block__row"><span>🎮 Games played</span><strong>${(STATS.total_attempts || 0).toLocaleString()}</strong></div>
      </div>
      <div class="stat-block">
        <div class="stat-block__row"><span>💡 Hints used</span><strong>${STATS.hints_used || 0}</strong></div>
      </div>
      <div class="stat-block">
        <div class="stat-block__row"><span>🏆 Global rank</span><strong>${RANK ? '#' + RANK : '—'}</strong></div>
      </div>
      <div class="account-box">
        <span style="color:var(--muted);font-size:12px">Signed in as</span>
        <strong>${esc(uname)}</strong>
        <code>${esc(uid)}</code>
      </div>`;
  }

  async function loadSolved() {
    try {
      const p = await api(`/progress/${encodeURIComponent(uid)}`);
      const solved = p.solved_levels || {};
      Object.entries(solved).forEach(([lid, ok]) => { if (ok) SOLVED.add(lid); });
    } catch (_) { /* guest with no player row yet, or offline */ }
  }

  async function loadStats() {
    try {
      STATS = await api(`/stats/${encodeURIComponent(uid)}`);
    } catch (_) {
      STATS = {};
    }
    refreshHero();
    renderStatsDetail();
  }

  // =========================================================
  //  Hero / daily challenge
  // =========================================================
  async function loadDaily(announce) {
    try {
      const d = await api('/daily-challenge');
      if (!announce) return;
      Audio.sfx.nav();
      openModal(`
        <h3>🔥 ${esc(d.level?.title || 'Daily Challenge')}</h3>
        <p class="sub">${esc(d.level?.room_name || '')} · ${esc(d.level?.difficulty || '')} · ${esc((d.level?.mechanic?.mechanic_type || 'puzzle').replace(/_/g, ' '))}</p>
        <p class="sub">Play the full daily puzzle in the PuzzleWorld mobile app, or mark it solved here to claim your points.</p>
        <div class="modal__actions">
          <button class="btn btn--ghost" id="mDailyOk"><span class="btn__label">Got it</span></button>
          <button class="btn btn--primary" id="mDailyDone"><span class="btn__label">✅ Mark solved</span></button>
        </div>`);
      $('#mDailyOk')?.addEventListener('click', () => { Audio.sfx.click(); closeModal(); });
      $('#mDailyDone')?.addEventListener('click', (e) => withButtonSpinner(e.currentTarget, async () => {
        try {
          await api('/daily-challenge/complete', {
            method: 'POST',
            body: JSON.stringify({ user_id: uid, challenge_date: d.challenge_date }),
          });
          Audio.sfx.win();
          showBanner('success', '🎉 Daily challenge solved! Points added.');
          loadStats(); loadLeaderboard(false);
          closeModal();
        } catch (err) {
          Audio.sfx.loss();
          showBanner('error', `Couldn't complete challenge: ${err.message}`);
        }
      }));
    } catch (e) {
      if (announce) { showBanner('error', `Daily challenge: ${e.message}`); Audio.sfx.error(); }
    }
  }

  // =========================================================
  //  Search + category filters
  // =========================================================
  function applyFilters() {
    const games = $$('.card', $('#gamesGrid'));
    let visible = 0;
    games.forEach(card => {
      const matchCat = activeCat === 'all' || card.dataset.cat === activeCat;
      const matchSearch = !searchQuery || (card.dataset.title || '').includes(searchQuery);
      const show = matchCat && matchSearch;
      card.style.display = show ? '' : 'none';
      if (show) visible++;
    });
    const gamesGrid = $('#gamesGrid');
    let empty = gamesGrid.querySelector('.empty');
    if (!visible) {
      if (!empty) gamesGrid.appendChild(el('div', 'empty',
        '<span class="empty__icon">🔍</span><span class="empty__title">No games match</span><span>Try a different search or category.</span>'));
    } else if (empty) empty.remove();

    $$('.card', $('#roomsGrid')).forEach(card => {
      const ok = !searchQuery || (card.dataset.search || '').includes(searchQuery);
      card.style.display = ok ? '' : 'none';
    });
  }

  function bindFilters() {
    $$('.cat-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        Audio.sfx.click();
        $$('.cat-chip').forEach(c => { c.classList.remove('is-active'); c.setAttribute('aria-selected', 'false'); });
        chip.classList.add('is-active'); chip.setAttribute('aria-selected', 'true');
        activeCat = chip.dataset.cat;
        applyFilters();
      });
    });
    const input = $('#searchInput');
    input?.addEventListener('input', debounce(() => {
      searchQuery = input.value.trim().toLowerCase();
      if (searchQuery) Audio.sfx.tick();
      applyFilters();
    }, 140));
  }

  // =========================================================
  //  Dashboard tabs
  // =========================================================
  function switchTab(name) {
    $$('.tab').forEach(t => { const on = t.dataset.tab === name; t.classList.toggle('is-active', on); t.setAttribute('aria-selected', String(on)); });
    $$('.bottomnav__item').forEach(b => b.classList.toggle('is-active', b.dataset.tab === name));
    $$('.panel').forEach(p => p.classList.toggle('is-active', p.dataset.panel === name));
    Audio.sfx.nav();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function bindTabs() {
    $$('.tab').forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));
    $$('.bottomnav__item').forEach(b => b.addEventListener('click', () => switchTab(b.dataset.tab)));
    $$('[data-act="goto-board"]').forEach(b => b.addEventListener('click', () => switchTab('board')));
  }

  // =========================================================
  //  Sound toggle
  // =========================================================
  function syncSound() {
    const btn = $('#btnSound');
    if (!btn) return;
    const m = Audio.isMuted();
    btn.classList.toggle('is-muted', m);
    btn.setAttribute('aria-pressed', String(!m));
    btn.setAttribute('aria-label', m ? 'Unmute sound' : 'Mute sound');
    btn.title = m ? 'Unmute sound' : 'Mute sound';
  }
  function bindSound() {
    syncSound();
    $('#btnSound').addEventListener('click', () => {
      Audio.unlock();
      const next = !Audio.isMuted();
      Audio.setMuted(next);
      syncSound();
      if (!next) Audio.sfx.toggle();
      const btn = $('#btnSound');
      btn.classList.remove('pulse'); void btn.offsetWidth; btn.classList.add('pulse');
      renderSettings();
      toast(next ? '🔇 Sound muted' : '🔊 Sound on', 'info', 1400);
    });
  }

  // =========================================================
  //  User dropdown
  // =========================================================
  function refreshIdentity() {
    const initial = (isSignedIn ? uname.trim().charAt(0) : 'G').toUpperCase();
    $('#avatarInitial').textContent = initial;
    $('#heroAvatar').textContent = initial;
    $('#greeting').textContent = `Welcome, ${uname}`;
    $('#heroGreeting').textContent = `Welcome back, ${uname}`;
    $('#menuName').textContent = uname;
    $('#menuId').textContent = isSignedIn ? uid : 'guest';
    $('#menuLogout').innerHTML = isSignedIn ? '🚪&nbsp; Log out' : '🔑&nbsp; Sign in';
    $('#menuLogout').dataset.act = isSignedIn ? 'logout' : 'login';
    $('#signinLink').hidden = isSignedIn;
  }

  function bindDropdown() {
    const btn = $('#avatarBtn'), menu = $('#userMenu');
    refreshIdentity();
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      Audio.sfx.click();
      const open = menu.hidden;
      menu.hidden = !open;
      btn.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('click', (e) => {
      if (!menu.hidden && !e.target.closest('#userWrap')) { menu.hidden = true; btn.setAttribute('aria-expanded', 'false'); }
    });
    menu.addEventListener('click', (e) => {
      const item = e.target.closest('[data-act]');
      if (!item) return;
      menu.hidden = true; btn.setAttribute('aria-expanded', 'false');
      const act = item.dataset.act;
      if (act === 'profile') { Audio.sfx.click(); openProfile(); }
      else if (act === 'settings') { switchTab('settings'); }
      else if (act === 'terms') { Audio.sfx.click(); openTerms(); }
      else if (act === 'logout') { signout(); }
      else if (act === 'login') { location.href = 'auth.html'; }
    });
  }

  function signout() {
    localStorage.removeItem('pw_user_id');
    localStorage.removeItem('pw_username');
    localStorage.removeItem('pw_last_room_id');
    localStorage.removeItem('pw_last_room_name');
    Audio.sfx.close();
    location.reload();
  }

  function showResumeBtn() {
    const id = localStorage.getItem('pw_last_room_id');
    const name = localStorage.getItem('pw_last_room_name');
    const btn = $('#btnResume');
    if (!btn) return;
    if (id) {
      btn.hidden = false;
      $('#resumeHint').textContent = name || '';
    } else {
      btn.hidden = true;
    }
  }

  function resumeRoom() {
    const id = localStorage.getItem('pw_last_room_id');
    if (!id) return;
    if (!ROOMS.length) { Audio.sfx.error(); return; }
    const room = ROOMS.find(r => r.room_id === id);
    if (room) { Audio.sfx.nav(); openRoom(room); }
  }

  // =========================================================
  //  Profile / reset / terms modals
  // =========================================================
  async function resetProgress() {
    try {
      await api(`/progress/${encodeURIComponent(uid)}`, { method: 'DELETE' });
      showBanner('success', 'Progress reset — starting fresh!');
      Audio.sfx.success();
      loadStats(); loadLeaderboard(false);
      closeModal();
    } catch (err) {
      showBanner('error', `Reset failed: ${err.message}`);
      Audio.sfx.error();
    }
  }

  async function openProfile() {
    const avatar = (uname || 'G')[0].toUpperCase();
    let stats = STATS, badges = [], unlockedCount = 0, totalBadges = 0;

    // Fetch fresh profile from API; fall back to cached STATS on failure
    try {
      const p = await api(`/user/profile/${encodeURIComponent(uid)}`);
      stats = p.stats || stats;
      badges = p.badges || [];
      unlockedCount = p.unlocked_count || 0;
      totalBadges = p.total_badges || badges.length;
    } catch (_) {
      // Fallback: construct simple badges from local STATS
      const s = STATS.solved_count || 0;
      const b = STATS.best_streak || 0;
      const sc = STATS.total_score || 0;
      badges = ['🚪','🏃','🔥','💯','👑'].map((icon, i) => ({
        id: `local_${i}`, icon, name: ['First Room','5 Solved','Streak 3','100 Pts','Top 10'][i],
        unlocked: [s>=1, s>=5, b>=3, sc>=100, (RANK>=1 && RANK<=10)][i],
        description: '', progress: null
      }));
      totalBadges = badges.length;
      unlockedCount = badges.filter(x => x.unlocked).length;
    }

    const score = (stats.total_score || 0).toLocaleString();
    const rank = RANK ? '#' + RANK : '—';
    const solved = stats.solved_count || 0;
    const streak = stats.best_streak || stats.streak || 0;

    // Detect newly unlocked badges (for glow animation)
    const newUnlocks = [];
    badges.forEach(b => {
      const was = cachedBadges.get(b.id);
      if (b.unlocked && !was) newUnlocks.push(b.id);
    });
    cachedBadges = new Map(badges.map(b => [b.id, b.unlocked]));

    const badgeHTML = badges.map(b => {
      const newly = newUnlocks.includes(b.id) ? ' pf-badge--new-anim' : '';
      const prog = !b.unlocked && b.progress ? `
        <div class="pf-badge__prog"><div class="pf-badge__bar" style="width:${Math.round(b.progress.current / b.progress.target * 100)}%"></div></div>` : '';
      const tipHidden = !b.unlocked && (b.description || b.progress) ? `hidden` : '';
      return `<div class="pf-badge${b.unlocked ? '' : ' pf-badge--locked'}${newly}" data-badge-id="${esc(b.id)}" tabindex="${b.unlocked ? '-1' : '0'}">
        <span class="pf-badge__icon">${esc(b.icon)}</span>
        ${!b.unlocked ? '<span class="pf-badge__lock">🔒</span>' : ''}
        <span class="pf-badge__name">${esc(b.name)}</span>
        ${prog}
        <div class="pf-badge__tip" ${tipHidden}>${esc(b.description)}${b.progress ? '<br><em>' + b.progress.current + ' / ' + b.progress.target + '</em>' : ''}</div>
      </div>`;
    }).join('');

    const headerBadge = totalBadges ? `<span class="pf__unlocked">${unlockedCount} / ${totalBadges}</span>` : '';

    openModal(`
      <div class="pf-avatar">${esc(avatar)}</div>
      <div class="pf-name">${esc(uname)}</div>
      <div class="pf-id"><code>${esc(uid)}</code></div>
      <div class="pf-stats">
        <div class="pf-stat"><span class="pf-stat__v">${score}</span><span class="pf-stat__l">Score</span></div>
        <div class="pf-stat"><span class="pf-stat__v">${rank}</span><span class="pf-stat__l">Rank</span></div>
        <div class="pf-stat"><span class="pf-stat__v">${solved}</span><span class="pf-stat__l">Solved</span></div>
        <div class="pf-stat"><span class="pf-stat__v">🔥 ${streak}</span><span class="pf-stat__l">Best Streak</span></div>
      </div>
      <div class="pf-badges-head"><span class="pf-badges-head__title">Badges</span>${headerBadge}</div>
      <div class="pf-badges">${badgeHTML}</div>
      <div class="modal__actions" style="margin-top:10px">
        <button class="btn btn--ghost" id="mProfileClose">Close</button>
        <button class="btn btn--primary" id="mProfileReset">Reset progress</button>
      </div>`);

    // Wire badge tooltip toggles (click to show tip for locked badges)
    modalBody.querySelectorAll('.pf-badge--locked').forEach(el => {
      el.addEventListener('click', () => {
        const tip = el.querySelector('.pf-badge__tip');
        if (tip) {
          const was = tip.hidden;
          // Hide all other tips
          modalBody.querySelectorAll('.pf-badge__tip').forEach(t => t.hidden = true);
          tip.hidden = !was;
        }
      });
    });

    $('#mProfileClose')?.addEventListener('click', () => { Audio.sfx.click(); closeModal(); });
    $('#mProfileReset')?.addEventListener('click', (e) => withButtonSpinner(e.currentTarget, resetProgress));
  }

  function openTerms() {
    openModal(`
      <h3>Terms &amp; Privacy Policy</h3>
      <p class="sub">PuzzleWorld · effective 2026</p>
      <p class="sub"><strong>Terms of Service.</strong> By using PuzzleWorld you agree to play fairly. Scores and progress may be stored locally and, if you connect, synced to our servers. Don't attempt to exploit or disrupt the service.</p>
      <p class="sub"><strong>Privacy Policy.</strong> We store only what's needed to run the game: a player ID, your chosen username, and gameplay stats (score, streaks, solved levels). Passwords are salted and hashed — never stored in plaintext. We never sell your data.</p>
      <div class="modal__actions">
        <button class="btn btn--primary" id="mTermsOk"><span class="btn__label">Accept &amp; Close</span></button>
      </div>`);
    $('#mTermsOk')?.addEventListener('click', () => { Audio.sfx.success(); closeModal(); });
  }

  // =========================================================
  //  Settings panel
  // =========================================================
  function renderSettings() {
    $('#settingsPanel').innerHTML = `
      <div class="settings__row">
        <div><div class="lbl">🔊 Sound effects</div><div class="sub">Synthesized UI tones</div></div>
        <button class="switch ${Audio.isMuted() ? '' : 'on'}" id="setSound" aria-label="Toggle sound"></button>
      </div>
      <div class="settings__row">
        <div><div class="lbl">👤 Account</div><div class="sub">${esc(uname)}</div></div>
        <button class="text-btn" id="setAccountAct">${isSignedIn ? 'Profile' : 'Sign in'}</button>
      </div>
      <div class="settings__row">
        <div><div class="lbl">🗑️ Reset progress</div><div class="sub">Clears score, streaks &amp; solved levels</div></div>
        <button class="btn btn--ghost" id="setReset" style="height:38px;padding:0 16px">Reset</button>
      </div>
      <div class="settings__links">
        <button class="text-btn" id="setTerms">📜 Terms &amp; Privacy</button>
        ${isSignedIn ? '<button class="btn btn--danger" id="setLogout" style="height:40px;padding:0 18px">Log out</button>' : '<a class="text-btn" href="auth.html">Create account</a>'}
      </div>`;
  }

  function bindSettings() {
    $('#settingsPanel').addEventListener('click', (e) => {
      const t = e.target.closest('[id]');
      if (!t) return;
      if (t.id === 'setSound') {
        Audio.unlock();
        const next = !Audio.isMuted();
        Audio.setMuted(next);
        syncSound();
        if (!next) Audio.sfx.toggle();
        renderSettings();
        toast(next ? '🔇 Sound muted' : '🔊 Sound on', 'info', 1400);
      } else if (t.id === 'setReset') {
        Audio.sfx.click();
        withButtonSpinner(t, resetProgress);
      } else if (t.id === 'setTerms') {
        Audio.sfx.click(); openTerms();
      } else if (t.id === 'setLogout') {
        signout();
      } else if (t.id === 'setAccountAct') {
        Audio.sfx.click();
        if (isSignedIn) openProfile(); else location.href = 'auth.html';
      }
    });
  }

  // =========================================================
  //  Boot
  // =========================================================
  function boot() {
    bindDropdown();
    renderGames();
    renderSettings();
    bindTabs();
    bindFilters();
    bindSound();
    bindSettings();

    $('#btnRefreshLb')?.addEventListener('click', (e) => withButtonSpinner(e.currentTarget, async () => {
      Audio.sfx.click();
      await loadLeaderboard(true);
      showBanner('success', 'Leaderboard refreshed');
    }));
    $('#btnPlayNow')?.addEventListener('click', () => { Audio.sfx.click(); loadDaily(true); });
    $('#btnSurprise')?.addEventListener('click', async (e) => withButtonSpinner(e.currentTarget, async () => {
      Audio.sfx.nav();
      if (!ROOMS.length) await loadRooms();
      const pick = ROOMS.length ? ROOMS[Math.floor(Math.random() * ROOMS.length)] : null;
      if (pick) openRoom(pick); else showBanner('error', 'No rooms available yet.');
    }));
    $('#btnResume')?.addEventListener('click', () => {
      Audio.sfx.nav();
      resumeRoom();
    });
    $('#btnTerms')?.addEventListener('click', (e) => { e.preventDefault(); Audio.sfx.click(); openTerms(); });
    showResumeBtn();

    loadSolved();
    loadRooms();
    loadLeaderboard(false);
    loadStats();

    document.addEventListener('visibilitychange', () => { if (!document.hidden) loadLeaderboard(false); });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
