(function () {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));

  let mode = 'login';
  let phase = 'auth'; // 'auth' (email+password) → 'otp' (verify) → 'username' (handle)
  let pendingEmail = '';

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
    const isAuth = phase === 'auth';
    const isOtp = phase === 'otp';
    const isUsername = phase === 'username';
    const isReg = mode === 'register';
    $('.tabs').style.display = isAuth ? '' : 'none';
    $$('.field--email, .field--password').forEach((f) => { f.hidden = !isAuth; });
    $$('.field--confirm').forEach((f) => { f.hidden = !isAuth || !isReg; });
    $$('.agree').forEach((a) => { a.hidden = !isAuth || !isReg; });
    $('#otpField').hidden = !isOtp;
    $('#usernameField').hidden = !isUsername;
    if (isOtp) {
      $('#formTitle').textContent = 'Verify your email';
      $('#formSub').textContent = 'We sent a 6-digit code to ' + pendingEmail + '.';
      $('#btnLabel').textContent = 'Verify';
      $('#switchText').innerHTML = '';
    } else if (isUsername) {
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
    $('#emailLabel').textContent = 'Email address';
    $('#switchText').innerHTML = isReg
      ? 'Already have an account? <a href="#" data-mode="login">Sign in</a>'
      : 'New here? <a href="#" data-mode="register">Create an account</a>';
    renderPhase();
    hideMsg();
    setLoading(false);
  }

  function enterOtpStep(email) {
    pendingEmail = email;
    phase = 'otp';
    renderPhase();
    $('#otp').value = '';
    $('#otp').focus();
  }

  function enterUsernameStep() {
    phase = 'username';
    renderPhase();
    hideMsg();
    setLoading(false);
    $('#username').value = '';
    $('#username').focus();
  }

  // After a successful login/verify, persist identity and either redirect
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

  // Resend OTP link
  $('#resendOtp').addEventListener('click', async (e) => {
    e.preventDefault();
    hideMsg();
    setLoading(true);
    try {
      const res = await fetch('/api/auth/resend-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: pendingEmail })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not resend the code.');
      showMsg(data.message || 'A new code has been sent.', 'success');
    } catch (err) {
      showMsg(err.message, 'error');
    }
    setLoading(false);
  });

  // Submit → backend
  $('#authForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMsg();

    // Phase: verify OTP
    if (phase === 'otp') {
      const otp = $('#otp').value.trim();
      if (!/^\d{6}$/.test(otp)) return showMsg('Please enter the 6-digit code.', 'error');
      setLoading(true);
      try {
        const res = await fetch('/api/auth/verify-otp', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: pendingEmail, otp })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Verification failed.');
        finishAuth(data);
      } catch (err) {
        showMsg(err.message, 'error');
        setLoading(false);
      }
      return;
    }

    // Phase: choose username
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

    // Phase: auth (login / register)
    const emailVal = $('#email').value.trim().toLowerCase();
    const password = $('#password').value;

    if (!emailVal) return showMsg('Please enter your email address.', 'error');
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

      if (mode === 'register') {
        // Register returns a message (no token) — move to the OTP step.
        enterOtpStep(emailVal);
        setLoading(false);
        showMsg(data.message || 'Check your email for the code.', 'success');
      } else if (data.unverified) {
        // Login blocked because email isn't verified — let them enter/resend the code.
        enterOtpStep(emailVal);
        setLoading(false);
        showMsg(data.error || 'Please verify your email first.', 'error');
      } else {
        finishAuth(data);
      }
    } catch (err) {
      showMsg(err.message, 'error');
      setLoading(false);
    }
  });

  $$('.field').forEach((f, i) => { f.classList.add('seq', `seq-${i + 1}`); });
  $('#submitBtn')?.classList.add('seq-btn');
})();
