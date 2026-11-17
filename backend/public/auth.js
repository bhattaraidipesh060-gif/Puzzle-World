(function () {
  'use strict';
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));
  const t = (k, v) => window.t(k, v);

  let mode = 'login';
  let phase = 'auth'; // 'auth' (email+password) → 'otp' (verify) → 'username' (handle)
  let pendingEmail = '';
  let resendCooldown = 0;
  let resendInterval = null;

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
      $('#formTitle').textContent = t('auth.verify_title');
      $('#formSub').textContent = t('auth.verify_sub', { email: pendingEmail });
      $('#btnLabel').textContent = t('auth.verify');
      $('#switchText').innerHTML = '';
    } else if (isUsername) {
      $('#formTitle').textContent = t('auth.username_title');
      $('#formSub').textContent = t('auth.username_sub');
      $('#btnLabel').textContent = t('auth.save_username');
      $('#switchText').innerHTML = '';
    }
  }

  function setMode(next) {
    mode = next;
    phase = 'auth';
    const isReg = next === 'register';
    $$('.tab').forEach((t) => t.classList.toggle('is-active', t.dataset.mode === next));
    $$('.tab').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.mode === next)));
    $('#formTitle').textContent = isReg ? t('auth.create_account') : t('auth.welcome_back');
    $('#formSub').textContent = isReg ? t('auth.join_sub') : t('auth.sign_in_sub');
    $('#btnLabel').textContent = isReg ? t('auth.create_btn') : t('auth.sign_in');
    $('#emailLabel').textContent = t('auth.email');
    $('#switchText').innerHTML = isReg
      ? t('auth.have_account') + ' <a href="#" data-mode="login">' + t('auth.sign_in') + '</a>'
      : t('auth.new_here') + ' <a href="#" data-mode="register">' + t('auth.create_account') + '</a>';
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
    startResendCountdown(RESEND_COOLDOWN);
  }

  const RESEND_COOLDOWN = 60; // seconds before a new code can be requested

  function stopResendCountdown() {
    if (resendInterval) { clearInterval(resendInterval); resendInterval = null; }
  }

  function startResendCountdown(seconds) {
    stopResendCountdown();
    const link = $('#resendOtp');
    if (!link) return;
    resendCooldown = seconds;
    const update = () => {
      if (resendCooldown <= 0) {
        stopResendCountdown();
        link.textContent = t('auth.resend');
        link.classList.remove('is-disabled');
        link.removeAttribute('aria-disabled');
        return;
      }
      link.textContent = t('auth.resend_in', { s: resendCooldown });
      link.classList.add('is-disabled');
      link.setAttribute('aria-disabled', 'true');
      resendCooldown -= 1;
    };
    update();
    resendInterval = setInterval(update, 1000);
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
      showMsg(t('auth.welcome', { name: profile.username }), 'success');
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
    if (resendCooldown > 0) return; // still cooling down
    hideMsg();
    setLoading(true);
    try {
      const res = await fetch('/api/auth/resend-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: pendingEmail })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || t('auth.resend_failed'));
      startResendCountdown(RESEND_COOLDOWN);
      showMsg(
        data.delivery === 'failed' ? t('auth.email_failed') : (data.message || t('auth.resend_done')),
        data.delivery === 'failed' ? 'error' : 'success'
      );
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
      if (!/^\d{6}$/.test(otp)) return showMsg(t('auth.enter_otp'), 'error');
      setLoading(true);
      try {
        const res = await fetch('/api/auth/verify-otp', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: pendingEmail, otp })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || t('auth.verify_failed'));
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
      if (username.length < 3 || username.length > 32) return showMsg(t('auth.username_len'), 'error');
      setLoading(true);
      try {
        const res = await fetch('/api/auth/set-username', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || t('auth.could_not_save'));
        localStorage.setItem('pw_username', data.user.username);
        showMsg(t('auth.welcome', { name: data.user.username }), 'success');
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

    if (!emailVal) return showMsg(t('auth.enter_email'), 'error');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailVal)) return showMsg(t('auth.invalid_email'), 'error');
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
      const res = await fetch('/api/auth/' + mode, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: emailVal, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || t('auth.something_wrong'));

      if (mode === 'register') {
        // Register returns a message (no token) — move to the OTP step.
        enterOtpStep(emailVal);
        setLoading(false);
        showMsg(
          data.delivery === 'failed' ? t('auth.email_failed') : (data.message || t('auth.new_code_sent')),
          data.delivery === 'failed' ? 'error' : 'success'
        );
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
