(function () {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  const t = (k, v) => window.t(k, v);
  // Backend API base — '' = same-origin (backend serves this frontend);
  // overridable via window.PW_API_BASE or ?api=... (resolved by i18n.js).
  const API = (window.PW_API_BASE || '') + '/api';

  let mode = 'login';

  function hideMsg() { $('#formMsg').hidden = true; }
  function showMsg(text, kind) {
    const el = $('#formMsg');
    el.textContent = text;
    el.className = 'form-msg form-msg--' + kind;
    el.hidden = false;
  }
  function setLoading(on) {
    $('#submitBtn').classList.toggle('is-loading', on);
  }

  function setMode(next) {
    mode = next;
    const isReg = next === 'register';
    $$('.tab').forEach((el) => el.classList.toggle('is-active', el.dataset.mode === next));
    $$('.tab').forEach((el) => el.setAttribute('aria-selected', String(el.dataset.mode === next)));
    $$('.field--confirm').forEach((f) => { f.hidden = !isReg; });
    $$('.agree').forEach((a) => { a.hidden = !isReg; });
    $('#formTitle').textContent = isReg ? t('auth.create_account') : t('auth.welcome_back');
    $('#formSub').textContent = isReg ? t('auth.join_sub') : t('auth.sign_in_sub');
    $('#btnLabel').textContent = isReg ? t('auth.create_btn') : t('auth.sign_in');
    $('#switchText').innerHTML = isReg
      ? t('auth.have_account') + ' <a href="#" data-mode="login">' + t('auth.sign_in') + '</a>'
      : t('auth.new_here') + ' <a href="#" data-mode="register">' + t('auth.create_account') + '</a>';
    hideMsg();
    setLoading(false);
  }

  // After a successful login/register, persist identity and go to the dashboard.
  function finishAuth(data) {
    const profile = data.user || {};
    localStorage.setItem('pw_user_id', profile.user_id || '');
    if (profile.username) localStorage.setItem('pw_username', profile.username);
    showMsg(t('auth.welcome', { name: profile.username }), 'success');
    setTimeout(() => { location.href = '/'; }, 700);
  }

  // Tab + link switching
  document.addEventListener('click', (e) => {
    const modeEl = e.target.closest('[data-mode]');
    if (modeEl && !e.target.closest('[data-modal]')) { e.preventDefault(); setMode(modeEl.dataset.mode); }
  });

  // Password visibility toggles
  $$('.reveal').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.dataset.target);
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.textContent = show ? '🙈' : '👁';
    });
  });

  // Modals (terms / privacy)
  const modals = { terms: '#termsModal', privacy: '#privacyModal' };
  function openModal(key) { document.querySelector(modals[key]).classList.add('is-open'); }
  function closeModals() { $$('.modal').forEach((m) => m.classList.remove('is-open')); }
  document.addEventListener('click', (e) => {
    const open = e.target.closest('[data-modal]');
    if (open) { e.preventDefault(); openModal(open.dataset.modal); return; }
    const close = e.target.closest('[data-close]');
    if (close) closeModals();
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModals(); });

  // Prefill username if previously signed in
  const uname = localStorage.getItem('pw_username');
  if (uname && uname !== 'Guest') { $('#username').value = uname; }

  // Submit → backend
  $('#authForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMsg();

    const username = $('#username').value.trim();
    const password = $('#password').value;

    if (!username) return showMsg(t('auth.enter_username'), 'error');
    if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username)) return showMsg(t('auth.invalid_username'), 'error');
    if (!password) return showMsg(t('auth.enter_password'), 'error');
    if (mode === 'register') {
      if (password.length < 8) return showMsg(t('auth.password_len'), 'error');
      if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password))
        return showMsg(t('auth.password_policy'), 'error');
      if (password !== $('#confirm').value) return showMsg(t('auth.password_mismatch'), 'error');
      if (!$('#agree').checked) return showMsg(t('auth.accept_terms'), 'error');
    }

    setLoading(true);
    try {
      const res = await fetch(API + '/auth/' + mode, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || t('auth.something_wrong'));
      finishAuth(data);
    } catch (err) {
      showMsg(err.message, 'error');
      setLoading(false);
    }
  });

  $$('.field').forEach((f, i) => { f.classList.add('seq', `seq-${i + 1}`); });
  $('#submitBtn')?.classList.add('seq-btn');
  setMode('login');
})();
