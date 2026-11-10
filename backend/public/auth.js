(function () {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));

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
    $$('.tab').forEach((t) => t.classList.toggle('is-active', t.dataset.mode === next));
    $$('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.mode === next)));
    $('#formTitle').textContent = isReg ? 'Create your account' : 'Welcome back';
    $('#formSub').textContent = isReg ? 'Join PuzzleWorld and sync your progress.' : 'Sign in to continue your progress.';
    $('#btnLabel').textContent = isReg ? 'Create Account' : 'Sign In';
    $('#switchText').innerHTML = isReg
      ? 'Already have an account? <a href="#" data-mode="login">Sign in</a>'
      : 'New here? <a href="#" data-mode="register">Create an account</a>';
    $$('.field--confirm').forEach((f) => { f.hidden = !isReg; });
    $$('.agree').forEach((a) => { a.hidden = !isReg; });
    hideMsg();
    setLoading(false);
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

  // Prefill username if already signed in
  const uname = localStorage.getItem('pw_username');
  if (uname && uname !== 'Guest') { $('#username').value = uname; }

  // Submit → backend
  $('#authForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMsg();
    const username = $('#username').value.trim();
    const password = $('#password').value;

    if (!username) return showMsg('Please enter a username.', 'error');
    if (!password) return showMsg('Please enter a password.', 'error');
    if (mode === 'register') {
      if (password.length < 8) return showMsg('Password must be at least 8 characters.', 'error');
      if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password))
        return showMsg('Password needs an uppercase letter, a lowercase letter, and a number.', 'error');
      if (password !== $('#confirm').value) return showMsg('Passwords do not match.', 'error');
      if (!$('#agree').checked) return showMsg('Please accept the Terms of Service and Privacy Policy.', 'error');
    }

    setLoading(true);
    try {
      const res = await fetch('/api/auth/' + mode, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Something went wrong. Please try again.');

      const profile = data.user || { user_id: data.user_id, username: data.username };
      showMsg(mode === 'register' ? 'Account created — signing you in…' : 'Welcome back, ' + profile.username + '!', 'success');
      localStorage.setItem('pw_user_id', profile.user_id);
      localStorage.setItem('pw_username', profile.username);
      if (data.token) localStorage.setItem('pw_token', data.token);
      setTimeout(() => { location.href = '/'; }, 900);
    } catch (err) {
      showMsg(err.message, 'error');
      setLoading(false);
    }
  });
$$('.field').forEach((f, i) => { f.classList.add('seq', `seq-${i + 1}`); });
  $('#submitBtn')?.classList.add('seq-btn');
})();
