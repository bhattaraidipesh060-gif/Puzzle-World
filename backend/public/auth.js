(function () {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));

  let mode = 'login';
  let phase = 'auth'; // 'auth' (email + password) or 'username' (choose handle)

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

  // Toggle field visibility for the current phase/mode.
  function renderPhase() {
    const isUsername = phase === 'username';
    const isReg = mode === 'register';
    $('.tabs').style.display = isUsername ? 'none' : '';
    $$('.field--email, .field--password').forEach((f) => { f.hidden = isUsername; });
    $$('.field--confirm').forEach((f) => { f.hidden = isUsername || !isReg; });
    $$('.agree').forEach((a) => { a.hidden = isUsername || !isReg; });
    $('#usernameField').hidden = !isUsername;
    if (isUsername) {
      $('#formTitle').textContent = 'Choose your username';
      $('#formSub').textContent = 'Almost done — pick the name that shows on the leaderboard.';
      $('#btnLabel').textContent = 'Save username';
      $('#switchText').innerHTML = '';
    }
  }

  function setMode(next) {
    mode = next;
    phase = 'auth';
    const isReg = next === 'register';
    $$('.tab').forEach((t) => t.classList.toggle('is-active', t.dataset.mode === next));
    $$('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.mode === next)));
    $('#formTitle').textContent = isReg ? 'Create your account' : 'Welcome back';
    $('#formSub').textContent = isReg ? 'Join PuzzleWorld and sync your progress.' : 'Sign in to continue your progress.';
    $('#btnLabel').textContent = isReg ? 'Create Account' : 'Sign In';
    $('#switchText').innerHTML = isReg
      ? 'Already have an account? <a href="#" data-mode="login">Sign in</a>'
      : 'New here? <a href="#" data-mode="register">Create an account</a>';
    renderPhase();
    hideMsg();
    setLoading(false);
  }

  // Enter the "choose username" step after auth with no username yet.
  function enterUsernameStep() {
    phase = 'username';
    renderPhase();
    hideMsg();
    setLoading(false);
    $('#username').value = '';
    $('#username').focus();
  }

  // After a successful login/register, persist identity and either redirect
  // (username already set) or move to the username step.
  function finishAuth(data) {
    const profile = data.user || {};
    localStorage.setItem('pw_user_id', profile.user_id || '');
    if (profile.email) localStorage.setItem('pw_email', profile.email);
    if (profile.username) {
      localStorage.setItem('pw_username', profile.username);
      showMsg('Welcome back, ' + profile.username + '!', 'success');
      setTimeout(() => { location.href = '/'; }, 700);
    } else {
      localStorage.removeItem('pw_username');
      enterUsernameStep();
    }
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

  // Prefill email if previously signed in
  const email = localStorage.getItem('pw_email');
  if (email) { $('#email').value = email; }

  // Submit → backend
  $('#authForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMsg();

    // Step 2: choose username
    if (phase === 'username') {
      const username = $('#username').value.trim();
      if (username.length < 3 || username.length > 32) return showMsg('Username must be 3–32 characters.', 'error');
      setLoading(true);
      try {
        const res = await fetch('/api/auth/set-username', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Could not save username.');
        localStorage.setItem('pw_username', data.user.username);
        showMsg('Welcome, ' + data.user.username + '!', 'success');
        setTimeout(() => { location.href = '/'; }, 700);
      } catch (err) {
        showMsg(err.message, 'error');
        setLoading(false);
      }
      return;
    }

    // Step 1: email + password
    const emailVal = $('#email').value.trim().toLowerCase();
    const password = $('#password').value;

    if (!emailVal) return showMsg('Please enter your email.', 'error');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailVal)) return showMsg('Please enter a valid email address.', 'error');
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
        body: JSON.stringify({ email: emailVal, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Something went wrong. Please try again.');
      finishAuth(data);
    } catch (err) {
      showMsg(err.message, 'error');
      setLoading(false);
    }
  });

  $$('.field').forEach((f, i) => { f.classList.add('seq', `seq-${i + 1}`); });
  $('#submitBtn')?.classList.add('seq-btn');
})();
