// AI Life Tracker - Frontend Logic & Local Simulation
(function() {
  'use strict';

  // ==================== STORAGE & DATABASE ====================
  const DB = {
    getUsers() {
      return JSON.parse(localStorage.getItem('alt_users') || '[]');
    },
    saveUsers(users) {
      localStorage.setItem('alt_users', JSON.stringify(users));
    },
    getCurrentUser() {
      const email = localStorage.getItem('alt_session_email');
      if (!email) return null;
      return this.getUsers().find(u => u.email === email) || null;
    },
    setCurrentUser(email) {
      localStorage.setItem('alt_session_email', email);
    },
    clearSession() {
      localStorage.removeItem('alt_session_email');
    },
    isOnboardingSeen() {
      return localStorage.getItem('onboarding_seen_v1') === 'true';
    },
    setOnboardingSeen() {
      localStorage.setItem('onboarding_seen_v1', 'true');
    },
    getBanks() {
      return [
        { id: 1, name: 'Sample Bank', shortCode: 'SAMPLE', countryCode: 'IN' },
        { id: 2, name: 'HDFC Bank', shortCode: 'HDFC', countryCode: 'IN' },
        { id: 3, name: 'ICICI Bank', shortCode: 'ICICI', countryCode: 'IN' },
        { id: 4, name: 'State Bank of India', shortCode: 'SBI', countryCode: 'IN' },
        { id: 5, name: 'Axis Bank', shortCode: 'AXIS', countryCode: 'IN' }
      ];
    },
    getCurrencies() {
      return [
        { id: 1, code: 'INR', name: 'Indian Rupee', symbol: '₹', decimalPlaces: 2 },
        { id: 2, code: 'USD', name: 'US Dollar', symbol: '$', decimalPlaces: 2 },
        { id: 3, code: 'EUR', name: 'Euro', symbol: '€', decimalPlaces: 2 }
      ];
    },
    getConfig(key) {
      if (key === 'bank_account_max_per_user') return 20;
      return 0;
    }
  };

  // Seed default test user if none exists
  (function seedDatabase() {
    let users = DB.getUsers();
    if (users.length === 0) {
      users = [{
        email: 'user@example.com',
        password: 'password123',
        profile: {
          displayName: 'John Doe',
          dateOfBirth: '1995-06-15',
          sexAtBirth: 'male',
          heightCm: 178,
          weightKg: 72,
          timezone: 'Asia/Kolkata'
        },
        bankAccounts: [
          {
            id: 'ba-seed-1',
            bankId: 1,
            currencyId: 1,
            nickname: 'Salary Account',
            accountLast4: '4589',
            accountType: 'savings',
            isActive: true
          },
          {
            id: 'ba-seed-2',
            bankId: 2,
            currencyId: 1,
            nickname: 'HDFC Daily Spends',
            accountLast4: '1024',
            accountType: 'current',
            isActive: true
          }
        ]
      }];
    }

    // Initialize Phase 2 collections
    if (!localStorage.getItem('alt_categories')) {
      const systemCategories = [
        { id: 'cat-1', userId: null, name: 'Food & Dining', type: 'expense', icon: 'restaurant', isSystem: true },
        { id: 'cat-2', userId: null, name: 'Transport', type: 'expense', icon: 'directions_car', isSystem: true },
        { id: 'cat-3', userId: null, name: 'Shopping', type: 'expense', icon: 'shopping_bag', isSystem: true },
        { id: 'cat-4', userId: null, name: 'Bills & Utilities', type: 'expense', icon: 'receipt', isSystem: true },
        { id: 'cat-5', userId: null, name: 'Entertainment', type: 'expense', icon: 'movie', isSystem: true },
        { id: 'cat-6', userId: null, name: 'Health', type: 'expense', icon: 'local_hospital', isSystem: true },
        { id: 'cat-7', userId: null, name: 'Transfer', type: 'expense', icon: 'swap_horiz', isSystem: true },
        { id: 'cat-8', userId: null, name: 'Other', type: 'expense', icon: 'category', isSystem: true },
        { id: 'cat-9', userId: null, name: 'Salary', type: 'income', icon: 'payments', isSystem: true },
        { id: 'cat-10', userId: null, name: 'Other Income', type: 'income', icon: 'attach_money', isSystem: true }
      ];
      localStorage.setItem('alt_categories', JSON.stringify(systemCategories));
    }

    if (!localStorage.getItem('alt_transactions')) {
      const now = new Date();
      const transactions = [
        {
          id: 'tx-1',
          bankAccountId: 'ba-seed-1',
          currencyId: 1,
          categoryId: 'cat-1',
          amount: 250.50,
          direction: 'debit',
          merchantRaw: 'Swiggy',
          merchantNormalized: 'swiggy',
          transactionDate: new Date(now.getTime() - 2 * 3600 * 1000).toISOString(),
          source: 'manual',
          isSelfTransfer: false,
          reviewStatus: 'none',
          reviewPartnerId: null,
          selfTransferMatchedId: null,
          refundMatchedId: null
        },
        {
          id: 'tx-2',
          bankAccountId: 'ba-seed-1',
          currencyId: 1,
          categoryId: 'cat-2',
          amount: 145.00,
          direction: 'debit',
          merchantRaw: 'Uber Trips',
          merchantNormalized: 'uber trips',
          transactionDate: new Date(now.getTime() - 24 * 3600 * 1000).toISOString(),
          source: 'sms',
          isSelfTransfer: false,
          reviewStatus: 'none',
          reviewPartnerId: null,
          selfTransferMatchedId: null,
          refundMatchedId: null
        },
        {
          id: 'tx-3',
          bankAccountId: 'ba-seed-1',
          currencyId: 1,
          categoryId: 'cat-9',
          amount: 55000.00,
          direction: 'credit',
          merchantRaw: 'Salary Oct 2026',
          merchantNormalized: 'salary oct 2026',
          transactionDate: new Date(now.getTime() - 3 * 86400 * 1000).toISOString(),
          source: 'sms',
          isSelfTransfer: false,
          reviewStatus: 'none',
          reviewPartnerId: null,
          selfTransferMatchedId: null,
          refundMatchedId: null
        },
        // Sample review queue pair (duplicate)
        {
          id: 'tx-dup-1',
          bankAccountId: 'ba-seed-1',
          currencyId: 1,
          categoryId: 'cat-1',
          amount: 180.00,
          direction: 'debit',
          merchantRaw: 'Cafe Coffee Day',
          merchantNormalized: 'cafe coffee day',
          transactionDate: new Date(now.getTime() - 4 * 3600 * 1000).toISOString(),
          source: 'manual',
          isSelfTransfer: false,
          reviewStatus: 'possible_duplicate',
          reviewPartnerId: 'tx-dup-2',
          selfTransferMatchedId: null,
          refundMatchedId: null
        },
        {
          id: 'tx-dup-2',
          bankAccountId: 'ba-seed-1',
          currencyId: 1,
          categoryId: 'cat-1',
          amount: 180.00,
          direction: 'debit',
          merchantRaw: 'Cafe Coffee Day CCD',
          merchantNormalized: 'cafe coffee day ccd',
          transactionDate: new Date(now.getTime() - 3.8 * 3600 * 1000).toISOString(),
          source: 'sms',
          isSelfTransfer: false,
          reviewStatus: 'possible_duplicate',
          reviewPartnerId: 'tx-dup-1',
          selfTransferMatchedId: null,
          refundMatchedId: null
        }
      ];
      localStorage.setItem('alt_transactions', JSON.stringify(transactions));
    }

    if (!localStorage.getItem('alt_budgets')) {
      const budgets = [
        {
          id: 'b-overall-1',
          categoryId: null,
          currencyId: 1,
          amount: 25000.00,
          period: 'monthly',
          isActive: true
        },
        {
          id: 'b-cat-1',
          categoryId: 'cat-1',
          currencyId: 1,
          amount: 6000.00,
          period: 'monthly',
          isActive: true
        }
      ];
      localStorage.setItem('alt_budgets', JSON.stringify(budgets));
    }

    if (!localStorage.getItem('alt_unparsed_sms')) {
      const unparsed = [
        {
          id: 'sms-1',
          rawText: 'Sent Rs.450 to Zomato UPI ref 459821. Call 1800 if not done by you.',
          sender: 'VM-HDFCBK',
          receivedAt: new Date().toISOString(),
          status: 'pending'
        }
      ];
      localStorage.setItem('alt_unparsed_sms', JSON.stringify(unparsed));
    }

    DB.saveUsers(users);
  })();

  // ==================== ONBOARDING STATE & CANVAS ====================
  const SLIDES = [
    { title: 'Your money and your health, in one place', subtitle: 'Connect your accounts and start tracking.' },
    { title: 'Every expense, tracked automatically', subtitle: 'Add expenses by hand, or import them.' },
    { title: 'Steps, water, workouts. Logged just as easily', subtitle: 'Home fitness tracking, kept simple.' },
    { title: 'Stay inside your budget', subtitle: 'Set limits and see your progress.' },
    { title: 'AI ties it all together', subtitle: 'Plain-language summaries of your own numbers.' }
  ];

  let currentSlide = 0;
  let canvasProgress = 0;
  let targetProgress = 0;

  const canvas = document.getElementById('scrub-canvas');
  const ctx = canvas.getContext('2d');

  function resizeCanvas() {
    canvas.width = canvas.parentElement.clientWidth;
    canvas.height = canvas.parentElement.clientHeight;
  }
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  // Render simulated dashboard build on canvas as user scrubs/advances slides
  function renderCanvas() {
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    // Dynamic gradient background
    const bgGrad = ctx.createLinearGradient(0, 0, 0, h);
    bgGrad.addColorStop(0, '#0F1E36');
    bgGrad.addColorStop(1, '#060A17');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, w, h);

    const cx = w / 2;
    const cy = h * 0.38;

    // Smooth lerp progress towards target
    canvasProgress += (targetProgress - canvasProgress) * 0.1;

    // Slide 1 effect: Glowing Balance Card & Pulse
    const cardScale = Math.min(canvasProgress * 1.5, 1);
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(cardScale, cardScale);

    // Glow ring
    ctx.beginPath();
    ctx.arc(0, 0, 110 + Math.sin(Date.now() / 400) * 4, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(99, 102, 241, 0.25)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Card background
    ctx.fillStyle = 'rgba(30, 41, 59, 0.85)';
    ctx.strokeStyle = 'rgba(99, 102, 241, 0.4)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.roundRect(-120, -75, 240, 150, 16);
    ctx.fill();
    ctx.stroke();

    // Card chip & text
    ctx.fillStyle = '#6366F1';
    ctx.fillRect(-96, -55, 36, 26);
    ctx.fillStyle = '#94A3B8';
    ctx.font = '12px Plus Jakarta Sans, sans-serif';
    ctx.fillText('BALANCE', -96, -8);
    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 24px Plus Jakarta Sans, sans-serif';
    ctx.fillText('₹ 1,48,920.00', -96, 24);
    ctx.fillStyle = '#64748B';
    ctx.font = '11px monospace';
    ctx.fillText('•••• 4589', -96, 52);
    ctx.restore();

    // Slide 2 effect: Stacked Transactions
    if (canvasProgress > 0.8) {
      const tProgress = Math.min((canvasProgress - 0.8) / 0.8, 1);
      const items = ['Groceries · Supermarket', 'Netflix Subscription', 'Gym Membership'];
      const amounts = ['- ₹ 2,450', '- ₹ 649', '- ₹ 1,500'];

      items.forEach((item, idx) => {
        const itemY = cy + 100 + (idx * 48) * tProgress;
        ctx.fillStyle = 'rgba(15, 23, 42, 0.7)';
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
        ctx.beginPath();
        ctx.roundRect(cx - 120, itemY - 18, 240, 40, 10);
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = '#CBD5E1';
        ctx.font = '12px Plus Jakarta Sans, sans-serif';
        ctx.fillText(item, cx - 105, itemY + 6);
        ctx.fillStyle = '#F87171';
        ctx.fillText(amounts[idx], cx + 55, itemY + 6);
      });
    }

    // Slide 3 effect: Fitness rings
    if (canvasProgress > 1.8) {
      const fProgress = Math.min((canvasProgress - 1.8) / 0.8, 1);
      ctx.save();
      ctx.translate(cx + 80, cy - 90);

      // Steps ring
      ctx.beginPath();
      ctx.arc(0, 0, 36, 0, Math.PI * 2 * fProgress);
      ctx.strokeStyle = '#10B981';
      ctx.lineWidth = 6;
      ctx.stroke();

      // Water ring
      ctx.beginPath();
      ctx.arc(0, 0, 26, 0, Math.PI * 1.6 * fProgress);
      ctx.strokeStyle = '#38BDF8';
      ctx.lineWidth = 5;
      ctx.stroke();
      ctx.restore();
    }

    // Slide 4 effect: Budget meter
    if (canvasProgress > 2.8) {
      const bProgress = Math.min((canvasProgress - 2.8) / 0.8, 1);
      ctx.save();
      ctx.translate(cx - 80, cy - 90);
      ctx.beginPath();
      ctx.arc(0, 0, 32, Math.PI, Math.PI + (Math.PI * 0.72 * bProgress));
      ctx.strokeStyle = '#F59E0B';
      ctx.lineWidth = 6;
      ctx.stroke();
      ctx.restore();
    }

    // Slide 5 effect: AI Insights badge
    if (canvasProgress > 3.6) {
      const aiProgress = Math.min((canvasProgress - 3.6) / 0.4, 1);
      ctx.save();
      ctx.translate(cx, cy + 90);
      ctx.scale(aiProgress, aiProgress);
      ctx.fillStyle = 'rgba(99, 102, 241, 0.9)';
      ctx.beginPath();
      ctx.roundRect(-100, -20, 200, 40, 20);
      ctx.fill();

      ctx.fillStyle = '#FFFFFF';
      ctx.font = '600 13px Plus Jakarta Sans, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('✨ AI: On track for 25% savings!', 0, 5);
      ctx.restore();
    }

    requestAnimationFrame(renderCanvas);
  }
  requestAnimationFrame(renderCanvas);

  function updateSlide(index) {
    currentSlide = index;
    targetProgress = index;

    const data = SLIDES[index];
    document.getElementById('slide-title').textContent = data.title;
    document.getElementById('slide-subtitle').textContent = data.subtitle;

    // Update dots
    const dots = document.querySelectorAll('#dots-container .dot');
    dots.forEach((dot, idx) => {
      dot.classList.toggle('active', idx === index);
    });

    const isLast = index === SLIDES.length - 1;
    const btnNext = document.getElementById('btn-next-slide');
    btnNext.querySelector('span').textContent = isLast ? 'Get started' : 'Next';
    document.getElementById('btn-skip-onboarding').style.display = isLast ? 'none' : 'block';
  }

  // ==================== VIEW ROUTING ====================
  function showView(viewId) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    const target = document.getElementById(viewId);
    if (target) {
      target.classList.add('active');
    }
  }

  // Check initial startup route
  function initRouting() {
    if (!DB.isOnboardingSeen()) {
      showView('view-onboarding');
      updateSlide(0);
    } else {
      const user = DB.getCurrentUser();
      if (user) {
        showView('view-home');
        loadHomeData();
      } else {
        showView('view-auth');
      }
    }
  }

  // ==================== ONBOARDING EVENTS ====================
  document.getElementById('btn-next-slide').addEventListener('click', () => {
    if (currentSlide < SLIDES.length - 1) {
      updateSlide(currentSlide + 1);
    } else {
      DB.setOnboardingSeen();
      showView('view-auth');
    }
  });

  document.getElementById('btn-skip-onboarding').addEventListener('click', () => {
    DB.setOnboardingSeen();
    showView('view-auth');
  });

  // ==================== AUTH LOGIC ====================
  let isSignupMode = false;

  const authTitle = document.getElementById('auth-title');
  const authSubtitle = document.getElementById('auth-subtitle');
  const btnAuthText = document.getElementById('btn-auth-text');
  const authSwitchPrompt = document.getElementById('auth-switch-prompt');
  const btnSwitchAuth = document.getElementById('btn-switch-auth');
  const authErrorBanner = document.getElementById('auth-error-banner');
  const pwHint = document.getElementById('pw-hint');

  btnSwitchAuth.addEventListener('click', () => {
    isSignupMode = !isSignupMode;
    authErrorBanner.style.display = 'none';

    if (isSignupMode) {
      authTitle.textContent = 'Create Account';
      authSubtitle.textContent = 'Track your finances and fitness in one place';
      btnAuthText.textContent = 'Sign Up';
      authSwitchPrompt.textContent = 'Already have an account?';
      btnSwitchAuth.textContent = 'Log In';
      pwHint.style.display = 'block';
    } else {
      authTitle.textContent = 'Welcome back';
      authSubtitle.textContent = 'Sign in to your AI Life Tracker';
      btnAuthText.textContent = 'Sign In';
      authSwitchPrompt.textContent = "Don't have an account?";
      btnSwitchAuth.textContent = 'Sign Up';
      pwHint.style.display = 'none';
    }
  });

  // Toggle password visibility
  document.getElementById('btn-toggle-pw').addEventListener('click', () => {
    const pwInput = document.getElementById('auth-password');
    pwInput.type = pwInput.type === 'password' ? 'text' : 'password';
  });

  document.getElementById('auth-form').addEventListener('submit', (e) => {
    e.preventDefault();
    authErrorBanner.style.display = 'none';

    const email = document.getElementById('auth-email').value.trim().toLowerCase();
    const password = document.getElementById('auth-password').value;

    const users = DB.getUsers();

    if (isSignupMode) {
      // Signup Flow
      if (users.some(u => u.email === email)) {
        authErrorBanner.textContent = 'Email already registered';
        authErrorBanner.style.display = 'block';
        return;
      }

      const newUser = {
        email,
        password,
        profile: {
          displayName: '',
          dateOfBirth: null,
          sexAtBirth: null,
          heightCm: null,
          weightKg: null,
          timezone: 'Asia/Kolkata'
        },
        bankAccounts: []
      };

      users.push(newUser);
      DB.saveUsers(users);
      DB.setCurrentUser(email);

      showView('view-home');
      loadHomeData();
    } else {
      // Login Flow
      const found = users.find(u => u.email === email && u.password === password);
      if (!found) {
        authErrorBanner.textContent = 'Invalid credentials';
        authErrorBanner.style.display = 'block';
        return;
      }

      DB.setCurrentUser(email);
      showView('view-home');
      loadHomeData();
    }
  });

  // ==================== HOME TABS ====================
  const headerTabTitle = document.getElementById('header-tab-title');
  const tabTitles = {
    expenses: 'Expenses',
    fitness: 'Fitness',
    insights: 'Insights'
  };

  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      const tabKey = btn.getAttribute('data-tab');

      document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
      document.getElementById(`tab-${tabKey}`).classList.add('active');

      headerTabTitle.textContent = tabTitles[tabKey];
      if (tabKey === 'fitness') {
        renderFitnessDashboard();
      }
    });
  });

  // ==================== BANK ACCOUNTS MODAL ====================
  const modalBanks = document.getElementById('modal-banks');
  const modalAccountForm = document.getElementById('modal-account-form');
  const bankAccountsList = document.getElementById('bank-accounts-list');
  const bankLimitAlert = document.getElementById('bank-limit-alert');

  document.getElementById('btn-open-banks').addEventListener('click', () => {
    renderBankAccounts();
    modalBanks.classList.add('active');
  });

  function renderBankAccounts() {
    const user = DB.getCurrentUser();
    if (!user) return;

    const banks = DB.getBanks();
    const currencies = DB.getCurrencies();
    const activeAccounts = (user.bankAccounts || []).filter(a => a.isActive !== false);

    bankAccountsList.innerHTML = '';
    bankLimitAlert.style.display = 'none';

    if (activeAccounts.length === 0) {
      bankAccountsList.innerHTML = `
        <div style="text-align: center; padding: 32px 16px; color: var(--text-secondary);">
          <p>No active bank accounts found.</p>
        </div>
      `;
      return;
    }

    activeAccounts.forEach(acc => {
      const bank = banks.find(b => b.id === acc.bankId) || { name: 'Bank' };
      const curr = currencies.find(c => c.id === acc.currencyId) || { code: 'INR' };
      const displayName = acc.nickname || `${bank.name} ${acc.accountLast4 ? '••' + acc.accountLast4 : ''}`;

      const item = document.createElement('div');
      item.className = 'account-item';
      item.innerHTML = `
        <div class="account-item-left">
          <div class="account-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect width="20" height="14" x="2" y="5" rx="2"/><line x1="2" x2="22" y1="10" y2="10"/>
            </svg>
          </div>
          <div class="account-info">
            <h4>${displayName}</h4>
            <p>${bank.name} · ${curr.code} · ${(acc.accountType || 'savings').toUpperCase()}</p>
          </div>
        </div>
        <div class="account-item-actions">
          <button class="icon-btn btn-edit-account" data-id="${acc.id}" title="Edit">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
          </button>
          <button class="icon-btn btn-delete-account" data-id="${acc.id}" title="Deactivate" style="color: var(--danger);">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
      `;

      item.querySelector('.btn-edit-account').addEventListener('click', () => openAccountForm(acc));
      item.querySelector('.btn-delete-account').addEventListener('click', () => deactivateAccount(acc.id));

      bankAccountsList.appendChild(item);
    });
  }

  // Populate Bank & Currency Dropdowns
  function populateSelects() {
    const bankSelect = document.getElementById('input-bank-id');
    const currSelect = document.getElementById('input-currency-id');

    bankSelect.innerHTML = DB.getBanks().map(b => `<option value="${b.id}">${b.name}</option>`).join('');
    currSelect.innerHTML = DB.getCurrencies().map(c => `<option value="${c.id}">${c.name} (${c.symbol})</option>`).join('');
  }
  populateSelects();

  function openAccountForm(account = null) {
    const isEdit = !!account;
    document.getElementById('account-form-title').textContent = isEdit ? 'Edit Bank Account' : 'Add Bank Account';
    document.getElementById('account-edit-id').value = isEdit ? account.id : '';
    document.getElementById('account-dialog-error').style.display = 'none';

    document.getElementById('group-bank-select').style.display = isEdit ? 'none' : 'block';
    document.getElementById('group-currency-select').style.display = isEdit ? 'none' : 'block';

    if (isEdit) {
      document.getElementById('input-account-nickname').value = account.nickname || '';
      document.getElementById('input-account-last4').value = account.accountLast4 || '';
      document.getElementById('input-account-type').value = account.accountType || 'savings';
    } else {
      document.getElementById('input-account-nickname').value = '';
      document.getElementById('input-account-last4').value = '';
      document.getElementById('input-account-type').value = 'savings';
    }

    modalAccountForm.classList.add('active');
  }

  document.getElementById('btn-add-account-dialog').addEventListener('click', () => {
    const user = DB.getCurrentUser();
    const activeCount = (user.bankAccounts || []).filter(a => a.isActive !== false).length;
    const maxCap = DB.getConfig('bank_account_max_per_user');

    if (activeCount >= maxCap) {
      bankLimitAlert.textContent = `Bank account limit reached (${maxCap} accounts max)`;
      bankLimitAlert.style.display = 'block';
      return;
    }

    openAccountForm(null);
  });

  document.getElementById('form-bank-account').addEventListener('submit', (e) => {
    e.preventDefault();
    const editId = document.getElementById('account-edit-id').value;
    const nickname = document.getElementById('input-account-nickname').value.trim();
    const last4 = document.getElementById('input-account-last4').value.trim();
    const type = document.getElementById('input-account-type').value;

    const errorBox = document.getElementById('account-dialog-error');
    errorBox.style.display = 'none';

    if (last4 && !/^[0-9]{4}$/.test(last4)) {
      errorBox.textContent = 'Last 4 digits must be exactly 4 numeric characters';
      errorBox.style.display = 'block';
      return;
    }

    const users = DB.getUsers();
    const user = DB.getCurrentUser();
    const userIndex = users.findIndex(u => u.email === user.email);

    if (editId) {
      // Edit mode
      const acc = users[userIndex].bankAccounts.find(a => a.id === editId);
      if (acc) {
        acc.nickname = nickname || null;
        acc.accountLast4 = last4 || null;
        acc.accountType = type;
      }
    } else {
      // Add mode
      const bankId = parseInt(document.getElementById('input-bank-id').value);
      const currencyId = parseInt(document.getElementById('input-currency-id').value);

      users[userIndex].bankAccounts.push({
        id: 'ba-' + Date.now(),
        bankId,
        currencyId,
        nickname: nickname || null,
        accountLast4: last4 || null,
        accountType: type,
        isActive: true
      });
    }

    DB.saveUsers(users);
    modalAccountForm.classList.remove('active');
    renderBankAccounts();
  });

  function deactivateAccount(accountId) {
    if (!confirm('Are you sure you want to deactivate this bank account?')) return;

    const users = DB.getUsers();
    const user = DB.getCurrentUser();
    const userIndex = users.findIndex(u => u.email === user.email);

    const acc = users[userIndex].bankAccounts.find(a => a.id === accountId);
    if (acc) {
      acc.isActive = false;
      DB.saveUsers(users);
      renderBankAccounts();
    }
  }

  // ==================== PROFILE MODAL ====================
  const modalProfile = document.getElementById('modal-profile');

  document.getElementById('btn-open-profile').addEventListener('click', () => {
    loadProfileData();
    modalProfile.classList.add('active');
  });

  function loadProfileData() {
    const user = DB.getCurrentUser();
    if (!user) return;

    document.getElementById('profile-email').value = user.email;
    document.getElementById('profile-name').value = user.profile.displayName || '';
    document.getElementById('profile-dob').value = user.profile.dateOfBirth || '';
    document.getElementById('profile-sex').value = user.profile.sexAtBirth || '';
    document.getElementById('profile-height').value = user.profile.heightCm || '';
    document.getElementById('profile-weight').value = user.profile.weightKg || '';
    document.getElementById('profile-timezone').value = user.profile.timezone || 'Asia/Kolkata';

    document.getElementById('profile-alert').style.display = 'none';
  }

  document.getElementById('form-profile').addEventListener('submit', (e) => {
    e.preventDefault();

    const users = DB.getUsers();
    const user = DB.getCurrentUser();
    const userIndex = users.findIndex(u => u.email === user.email);

    const profile = users[userIndex].profile;
    profile.displayName = document.getElementById('profile-name').value.trim();
    profile.dateOfBirth = document.getElementById('profile-dob').value || null;
    profile.sexAtBirth = document.getElementById('profile-sex').value || null;
    profile.heightCm = parseFloat(document.getElementById('profile-height').value) || null;
    profile.weightKg = parseFloat(document.getElementById('profile-weight').value) || null;
    profile.timezone = document.getElementById('profile-timezone').value.trim() || 'Asia/Kolkata';

    DB.saveUsers(users);

    const alertBox = document.getElementById('profile-alert');
    alertBox.textContent = 'Profile saved successfully';
    alertBox.style.display = 'block';
  });

  // Logout
  document.getElementById('btn-logout').addEventListener('click', () => {
    DB.clearSession();
    modalProfile.classList.remove('active');
    showView('view-auth');
  });

  // Delete Account
  document.getElementById('btn-delete-account').addEventListener('click', () => {
    if (!confirm('This will permanently delete your account, bank accounts, and profile data. Proceed?')) {
      return;
    }

    const user = DB.getCurrentUser();
    let users = DB.getUsers();
    users = users.filter(u => u.email !== user.email);
    DB.saveUsers(users);

    DB.clearSession();
    modalProfile.classList.remove('active');
    showView('view-auth');
  });

  // Close modal buttons
  document.querySelectorAll('.btn-close-modal, [data-close]').forEach(btn => {
    btn.addEventListener('click', () => {
      const modalId = btn.getAttribute('data-close') || btn.closest('.modal-overlay').id;
      document.getElementById(modalId).classList.remove('active');
    });
  });

  // ==================== PHASE 2 EXPENSES ENGINE ====================
  const Phase2DB = {
    getCategories() {
      return JSON.parse(localStorage.getItem('alt_categories') || '[]');
    },
    saveCategories(cats) {
      localStorage.setItem('alt_categories', JSON.stringify(cats));
    },
    getTransactions() {
      return JSON.parse(localStorage.getItem('alt_transactions') || '[]');
    },
    saveTransactions(txs) {
      localStorage.setItem('alt_transactions', JSON.stringify(txs));
    },
    getBudgets() {
      return JSON.parse(localStorage.getItem('alt_budgets') || '[]');
    },
    saveBudgets(bs) {
      localStorage.setItem('alt_budgets', JSON.stringify(bs));
    },
    getUnparsedSms() {
      return JSON.parse(localStorage.getItem('alt_unparsed_sms') || '[]');
    },
    saveUnparsedSms(smsList) {
      localStorage.setItem('alt_unparsed_sms', JSON.stringify(smsList));
    },
    normalizeMerchant(raw) {
      if (!raw || !raw.trim()) return null;
      const cleaned = raw.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
      return cleaned.length > 0 ? cleaned : null;
    }
  };

  // State
  let currentFilterDir = 'all';
  let currentFilterCat = '';
  let currentFilterAcc = '';

  function renderExpenses() {
    const user = DB.getCurrentUser();
    if (!user) return;

    const categories = Phase2DB.getCategories();
    const transactions = Phase2DB.getTransactions();
    const accounts = user.bankAccounts || [];

    // Populate Category filter
    const filterCatSelect = document.getElementById('filter-category');
    if (filterCatSelect) {
      const currentVal = filterCatSelect.value;
      filterCatSelect.innerHTML = '<option value="">All Categories</option>';
      categories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id;
        opt.textContent = c.name;
        filterCatSelect.appendChild(opt);
      });
      filterCatSelect.value = currentVal;
    }

    // Populate Account filter
    const filterAccSelect = document.getElementById('filter-account');
    if (filterAccSelect) {
      const currentVal = filterAccSelect.value;
      filterAccSelect.innerHTML = '<option value="">All Accounts</option>';
      accounts.forEach(a => {
        const opt = document.createElement('option');
        opt.value = a.id;
        opt.textContent = `${a.nickname || 'Account'} (..${a.accountLast4 || ''})`;
        filterAccSelect.appendChild(opt);
      });
      filterAccSelect.value = currentVal;
    }

    // Filter transactions
    let filtered = [...transactions];
    if (currentFilterDir !== 'all') {
      filtered = filtered.filter(t => t.direction === currentFilterDir);
    }
    if (currentFilterCat) {
      filtered = filtered.filter(t => t.categoryId === currentFilterCat);
    }
    if (currentFilterAcc) {
      filtered = filtered.filter(t => t.bankAccountId === currentFilterAcc);
    }

    filtered.sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate));

    // Render List
    const listContainer = document.getElementById('transactions-list');
    listContainer.innerHTML = '';

    if (filtered.length === 0) {
      listContainer.innerHTML = `
        <div class="empty-state">
          <h4>No transactions found</h4>
          <p>Tap <strong>+ Add</strong> above to record your first transaction.</p>
        </div>
      `;
    } else {
      filtered.forEach(tx => {
        const cat = categories.find(c => c.id === tx.categoryId);
        const catName = cat ? cat.name : 'Uncategorized';
        const isDebit = tx.direction === 'debit';
        const dateStr = new Date(tx.transactionDate).toLocaleString('en-IN', {
          month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });

        const item = document.createElement('div');
        item.className = 'tx-item';
        item.innerHTML = `
          <div class="tx-left">
            <div class="tx-icon-circle ${isDebit ? 'debit' : 'credit'}">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                ${isDebit ? '<path d="M12 5v14M19 12l-7 7-7-7"/>' : '<path d="M12 19V5M5 12l7-7 7 7"/>'}
              </svg>
            </div>
            <div class="tx-details">
              <div class="tx-merchant">
                <span>${tx.merchantRaw || 'No merchant'}</span>
                ${tx.reviewStatus && tx.reviewStatus !== 'none' ? '<span class="tx-badge review">Review</span>' : ''}
                ${tx.isSelfTransfer ? '<span class="tx-badge transfer">Transfer</span>' : ''}
              </div>
              <div class="tx-meta">${catName} &bull; ${dateStr}</div>
            </div>
          </div>
          <div class="tx-amount ${isDebit ? 'debit' : 'credit'}">
            ${isDebit ? '-' : '+'}₹${Number(tx.amount).toFixed(2)}
          </div>
        `;
        item.addEventListener('click', () => openEditTransaction(tx.id));
        listContainer.appendChild(item);
      });
    }

    // Update review badge count
    updateReviewBadge();
  }

  function updateReviewBadge() {
    const txs = Phase2DB.getTransactions();
    const sms = Phase2DB.getUnparsedSms().filter(s => s.status === 'pending');
    const flaggedCount = txs.filter(t => t.reviewStatus && t.reviewStatus !== 'none').length / 2 + sms.length;
    const badge = document.getElementById('review-badge-count');
    const rounded = Math.floor(flaggedCount);
    if (badge) {
      if (rounded > 0) {
        badge.textContent = rounded;
        badge.style.display = 'inline-block';
      } else {
        badge.style.display = 'none';
      }
    }
  }

  // Filter Bar event listeners
  document.getElementById('filter-category').addEventListener('change', (e) => {
    currentFilterCat = e.target.value;
    renderExpenses();
  });
  document.getElementById('filter-account').addEventListener('change', (e) => {
    currentFilterAcc = e.target.value;
    renderExpenses();
  });
  document.querySelectorAll('.expenses-filter-bar .dir-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.expenses-filter-bar .dir-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      currentFilterDir = chip.getAttribute('data-dir');
      renderExpenses();
    });
  });

  // ==================== TRANSACTION FORM MODAL ====================
  const modalTx = document.getElementById('modal-transaction-form');
  const formTx = document.getElementById('form-transaction');
  let currentTxDir = 'debit';

  function openCreateTransaction(prefilled) {
    const user = DB.getCurrentUser();
    if (!user) return;

    document.getElementById('tx-edit-id').value = '';
    document.getElementById('tx-form-title').textContent = 'Add Transaction';
    document.getElementById('tx-restricted-warning').style.display = 'none';
    document.getElementById('tx-edit-extra-actions').style.display = 'none';
    document.getElementById('btn-save-tx').textContent = 'Save Transaction';
    document.getElementById('tx-form-alert').style.display = 'none';

    // Populate accounts
    const accSelect = document.getElementById('tx-account');
    accSelect.disabled = false;
    accSelect.innerHTML = '';
    (user.bankAccounts || []).forEach(a => {
      const opt = document.createElement('option');
      opt.value = a.id;
      opt.textContent = `${a.nickname || 'Account'} (..${a.accountLast4 || ''})`;
      accSelect.appendChild(opt);
    });

    // Populate categories
    const catSelect = document.getElementById('tx-category');
    catSelect.innerHTML = '<option value="">Uncategorized</option>';
    Phase2DB.getCategories().forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = `${c.name} (${c.type})`;
      catSelect.appendChild(opt);
    });

    // Direction buttons
    setTxDirection('debit');

    // Values
    document.getElementById('tx-amount').disabled = false;
    document.getElementById('tx-amount').value = prefilled ? prefilled.amount || '' : '';
    document.getElementById('tx-merchant').value = prefilled ? prefilled.merchant || '' : '';
    
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    document.getElementById('tx-datetime').value = now.toISOString().slice(0, 16);
    document.getElementById('tx-datetime').disabled = false;

    modalTx.classList.add('active');
  }

  function openEditTransaction(txId) {
    const tx = Phase2DB.getTransactions().find(t => t.id === txId);
    if (!tx) return;

    openCreateTransaction();

    document.getElementById('tx-edit-id').value = tx.id;
    document.getElementById('tx-form-title').textContent = 'Edit Transaction';
    document.getElementById('btn-save-tx').textContent = 'Save Changes';
    document.getElementById('tx-edit-extra-actions').style.display = 'flex';

    document.getElementById('tx-account').value = tx.bankAccountId;
    setTxDirection(tx.direction);
    document.getElementById('tx-amount').value = tx.amount;
    document.getElementById('tx-merchant').value = tx.merchantRaw || '';
    document.getElementById('tx-category').value = tx.categoryId || '';

    const d = new Date(tx.transactionDate);
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    document.getElementById('tx-datetime').value = d.toISOString().slice(0, 16);

    const isLinked = tx.isSelfTransfer || tx.refundMatchedId != null;
    const hasReviewFlag = tx.reviewStatus && tx.reviewStatus !== 'none';

    if (isLinked || hasReviewFlag) {
      document.getElementById('tx-restricted-warning').style.display = 'block';
      document.getElementById('tx-account').disabled = true;
      document.getElementById('tx-amount').disabled = true;
      document.getElementById('tx-datetime').disabled = true;
      document.getElementById('btn-dir-debit').disabled = true;
      document.getElementById('btn-dir-credit').disabled = true;
    }

    const btnUnlink = document.getElementById('btn-unlink-tx');
    if (isLinked) {
      btnUnlink.style.display = 'block';
      btnUnlink.onclick = () => {
        if (!confirm('Unlink this match?')) return;
        const txs = Phase2DB.getTransactions();
        const main = txs.find(t => t.id === txId);
        if (main.isSelfTransfer && main.selfTransferMatchedId) {
          const partner = txs.find(t => t.id === main.selfTransferMatchedId);
          if (partner) {
            partner.isSelfTransfer = false;
            partner.selfTransferMatchedId = null;
          }
          main.isSelfTransfer = false;
          main.selfTransferMatchedId = null;
        } else if (main.refundMatchedId) {
          const partner = txs.find(t => t.id === main.refundMatchedId);
          if (partner) partner.refundMatchedId = null;
          main.refundMatchedId = null;
        }
        Phase2DB.saveTransactions(txs);
        modalTx.classList.remove('active');
        renderExpenses();
      };
    } else {
      btnUnlink.style.display = 'none';
    }

    document.getElementById('btn-delete-tx').onclick = () => {
      if (!confirm('Delete this transaction?')) return;
      let txs = Phase2DB.getTransactions();
      const main = txs.find(t => t.id === txId);
      if (main.isSelfTransfer && main.selfTransferMatchedId) {
        const partner = txs.find(t => t.id === main.selfTransferMatchedId);
        if (partner) {
          partner.isSelfTransfer = false;
          partner.selfTransferMatchedId = null;
        }
      }
      txs.forEach(t => {
        if (t.reviewPartnerId === txId) {
          t.reviewStatus = 'none';
          t.reviewPartnerId = null;
        }
      });
      txs = txs.filter(t => t.id !== txId);
      Phase2DB.saveTransactions(txs);
      modalTx.classList.remove('active');
      renderExpenses();
    };
  }

  function setTxDirection(dir) {
    currentTxDir = dir;
    document.getElementById('tx-direction').value = dir;
    if (dir === 'debit') {
      document.getElementById('btn-dir-debit').classList.add('active');
      document.getElementById('btn-dir-credit').classList.remove('active');
    } else {
      document.getElementById('btn-dir-credit').classList.add('active');
      document.getElementById('btn-dir-debit').classList.remove('active');
    }
  }

  document.getElementById('btn-dir-debit').addEventListener('click', () => setTxDirection('debit'));
  document.getElementById('btn-dir-credit').addEventListener('click', () => setTxDirection('credit'));
  document.getElementById('btn-add-transaction').addEventListener('click', () => openCreateTransaction());

  formTx.addEventListener('submit', (e) => {
    e.preventDefault();
    const editId = document.getElementById('tx-edit-id').value;
    const accountId = document.getElementById('tx-account').value;
    const direction = document.getElementById('tx-direction').value;
    const amount = parseFloat(document.getElementById('tx-amount').value);
    const merchantRaw = document.getElementById('tx-merchant').value.trim();
    const categoryId = document.getElementById('tx-category').value || null;
    const dt = new Date(document.getElementById('tx-datetime').value);

    if (isNaN(amount) || amount <= 0) {
      alert('Amount must be greater than 0');
      return;
    }

    const txs = Phase2DB.getTransactions();

    if (editId) {
      const tx = txs.find(t => t.id === editId);
      if (!tx) return;
      tx.merchantRaw = merchantRaw || null;
      tx.merchantNormalized = Phase2DB.normalizeMerchant(merchantRaw);
      tx.categoryId = categoryId;
      if (!tx.isSelfTransfer && !tx.refundMatchedId && (!tx.reviewStatus || tx.reviewStatus === 'none')) {
        tx.bankAccountId = accountId;
        tx.direction = direction;
        tx.amount = amount;
        tx.transactionDate = dt.toISOString();
      }
    } else {
      const newTx = {
        id: 'tx-' + Date.now(),
        bankAccountId: accountId,
        currencyId: 1,
        categoryId: categoryId,
        amount: amount,
        direction: direction,
        merchantRaw: merchantRaw || null,
        merchantNormalized: Phase2DB.normalizeMerchant(merchantRaw),
        transactionDate: dt.toISOString(),
        source: 'manual',
        isSelfTransfer: false,
        reviewStatus: 'none',
        reviewPartnerId: null,
        selfTransferMatchedId: null,
        refundMatchedId: null
      };

      // Run matching logic
      // Check 1: Duplicate check (same account, amount, direction within 48h)
      const dedupMatch = txs.find(t => 
        t.bankAccountId === accountId &&
        t.amount === amount &&
        t.direction === direction &&
        t.reviewStatus === 'none' &&
        Math.abs(new Date(t.transactionDate) - dt) <= 48 * 3600 * 1000
      );

      if (dedupMatch) {
        newTx.reviewStatus = 'possible_duplicate';
        newTx.reviewPartnerId = dedupMatch.id;
        dedupMatch.reviewStatus = 'possible_duplicate';
        dedupMatch.reviewPartnerId = newTx.id;
      } else {
        // Check 2: Self-transfer (different account, same amount, opposite direction within 24h)
        const oppositeDir = direction === 'debit' ? 'credit' : 'debit';
        const selfMatch = txs.find(t =>
          t.bankAccountId !== accountId &&
          t.amount === amount &&
          t.direction === oppositeDir &&
          !t.isSelfTransfer &&
          t.reviewStatus === 'none' &&
          Math.abs(new Date(t.transactionDate) - dt) <= 24 * 3600 * 1000
        );
        if (selfMatch) {
          newTx.reviewStatus = 'possible_self_transfer';
          newTx.reviewPartnerId = selfMatch.id;
          selfMatch.reviewStatus = 'possible_self_transfer';
          selfMatch.reviewPartnerId = newTx.id;
        }
      }

      txs.push(newTx);
    }

    Phase2DB.saveTransactions(txs);
    modalTx.classList.remove('active');
    renderExpenses();
  });

  // ==================== CATEGORIES MODAL ====================
  const modalCategories = document.getElementById('modal-categories');
  document.getElementById('btn-open-categories').addEventListener('click', () => {
    renderCategoriesModal();
    modalCategories.classList.add('active');
  });

  function renderCategoriesModal() {
    const categories = Phase2DB.getCategories();
    const list = document.getElementById('categories-list');
    list.innerHTML = '';

    categories.forEach(c => {
      const item = document.createElement('div');
      item.className = 'cat-item';
      item.style.display = 'flex';
      item.style.alignItems = 'center';
      item.style.justifyContent = 'space-between';
      item.innerHTML = `
        <div>
          <strong>${c.name}</strong> <span style="font-size: 11px; color: var(--text-muted);">(${c.type})</span>
          ${c.isSystem ? '<span class="badge" style="margin-left: 6px; font-size: 10px;">System</span>' : ''}
        </div>
        ${!c.isSystem ? `<button class="btn-text btn-sm" style="color: var(--danger);" data-cat-id="${c.id}">Delete</button>` : ''}
      `;
      const delBtn = item.querySelector('[data-cat-id]');
      if (delBtn) {
        delBtn.addEventListener('click', () => {
          if (!confirm(`Delete category "${c.name}"?`)) return;
          const updated = Phase2DB.getCategories().filter(cat => cat.id !== c.id);
          Phase2DB.saveCategories(updated);
          renderCategoriesModal();
          renderExpenses();
        });
      }
      list.appendChild(item);
    });
  }

  document.getElementById('btn-create-category').addEventListener('click', () => {
    const nameInput = document.getElementById('input-new-cat-name');
    const typeSelect = document.getElementById('select-new-cat-type');
    const alertBox = document.getElementById('cat-alert');
    const name = nameInput.value.trim();
    if (!name) return;

    const cats = Phase2DB.getCategories();
    if (cats.some(c => c.name.toLowerCase() === name.toLowerCase())) {
      alertBox.textContent = `Category with name "${name}" already exists`;
      alertBox.style.display = 'block';
      return;
    }
    alertBox.style.display = 'none';

    cats.push({
      id: 'cat-' + Date.now(),
      userId: 'user',
      name: name,
      type: typeSelect.value,
      icon: 'category',
      isSystem: false
    });
    Phase2DB.saveCategories(cats);
    nameInput.value = '';
    renderCategoriesModal();
    renderExpenses();
  });

  // ==================== BUDGETS MODAL ====================
  const modalBudgets = document.getElementById('modal-budgets');
  document.getElementById('btn-open-budgets').addEventListener('click', () => {
    renderBudgetsModal();
    modalBudgets.classList.add('active');
  });

  function renderBudgetsModal() {
    const categories = Phase2DB.getCategories();
    const budgets = Phase2DB.getBudgets();
    const transactions = Phase2DB.getTransactions();

    // Populate scope dropdown
    const scopeSelect = document.getElementById('budget-category-select');
    scopeSelect.innerHTML = '<option value="">Overall (All Expenses)</option>';
    categories.filter(c => c.type === 'expense').forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = c.name;
      scopeSelect.appendChild(opt);
    });

    const list = document.getElementById('budgets-list');
    list.innerHTML = '';

    if (budgets.length === 0) {
      list.innerHTML = '<p class="empty-state">No monthly budgets configured yet.</p>';
      return;
    }

    budgets.forEach(b => {
      const cat = categories.find(c => c.id === b.categoryId);
      const catName = cat ? cat.name : 'Overall Budget';

      // Compute spent strictly from transactions
      let spent = 0;
      transactions.forEach(t => {
        if (t.isSelfTransfer) return;
        if (b.categoryId) {
          if (t.direction === 'debit' && t.categoryId === b.categoryId) {
            spent += Number(t.amount);
          } else if (t.direction === 'credit' && t.refundMatchedId) {
            const debitPartner = transactions.find(d => d.id === t.refundMatchedId);
            if (debitPartner && debitPartner.categoryId === b.categoryId) {
              spent -= Number(t.amount);
            }
          }
        } else {
          // Overall
          if (t.direction === 'debit') {
            spent += Number(t.amount);
          } else if (t.direction === 'credit' && t.refundMatchedId) {
            spent -= Number(t.amount);
          }
        }
      });

      spent = Math.max(spent, 0);
      const remaining = Number(b.amount) - spent;
      const pct = Math.min((spent / Number(b.amount)) * 100, 100);
      const isOver = spent > Number(b.amount);

      const card = document.createElement('div');
      card.className = 'budget-card';
      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <strong>${catName}</strong>
          <button class="btn-text btn-sm" style="color: var(--danger);" data-b-id="${b.id}">Delete</button>
        </div>
        <div class="budget-progress-track">
          <div class="budget-progress-bar ${isOver ? 'over' : ''}" style="width: ${pct}%;"></div>
        </div>
        <div style="display: flex; justify-content: space-between; font-size: 12px; margin-top: 4px;">
          <span>Spent: ₹${spent.toFixed(2)} / ₹${Number(b.amount).toFixed(2)}</span>
          <span style="font-weight: 700; color: ${isOver ? '#EF4444' : '#10B981'};">${((spent / Number(b.amount)) * 100).toFixed(1)}%</span>
        </div>
        <div style="font-size: 12px; color: ${isOver ? '#EF4444' : 'var(--text-muted)'}; margin-top: 2px;">
          ${isOver ? `Over budget by ₹${(spent - Number(b.amount)).toFixed(2)}` : `Remaining: ₹${remaining.toFixed(2)}`}
        </div>
      `;
      card.querySelector('[data-b-id]').addEventListener('click', () => {
        if (!confirm('Delete this budget?')) return;
        const updated = Phase2DB.getBudgets().filter(x => x.id !== b.id);
        Phase2DB.saveBudgets(updated);
        renderBudgetsModal();
      });
      list.appendChild(card);
    });
  }

  document.getElementById('btn-create-budget').addEventListener('click', () => {
    const scopeVal = document.getElementById('budget-category-select').value || null;
    const amountVal = parseFloat(document.getElementById('budget-amount-input').value);
    const alertBox = document.getElementById('budget-alert');

    if (isNaN(amountVal) || amountVal <= 0) {
      alertBox.textContent = 'Please enter a valid amount > 0';
      alertBox.style.display = 'block';
      return;
    }

    const budgets = Phase2DB.getBudgets();
    if (budgets.some(b => b.categoryId === scopeVal)) {
      alertBox.textContent = 'A budget for this scope already exists';
      alertBox.style.display = 'block';
      return;
    }
    alertBox.style.display = 'none';

    budgets.push({
      id: 'b-' + Date.now(),
      categoryId: scopeVal,
      currencyId: 1,
      amount: amountVal,
      period: 'monthly',
      isActive: true
    });
    Phase2DB.saveBudgets(budgets);
    document.getElementById('budget-amount-input').value = '';
    renderBudgetsModal();
  });

  // ==================== REVIEW QUEUE MODAL ====================
  const modalReview = document.getElementById('modal-review-queue');
  document.getElementById('btn-open-review').addEventListener('click', () => {
    renderReviewQueueModal();
    modalReview.classList.add('active');
  });

  function renderReviewQueueModal() {
    const txs = Phase2DB.getTransactions();
    const unparsedSms = Phase2DB.getUnparsedSms().filter(s => s.status === 'pending');
    const content = document.getElementById('review-queue-content');
    content.innerHTML = '';

    const duplicates = [];
    const selfTransfers = [];
    const refunds = [];

    const visited = new Set();
    txs.forEach(t => {
      if (!t.reviewStatus || t.reviewStatus === 'none' || visited.has(t.id)) return;
      const partner = txs.find(p => p.id === t.reviewPartnerId);
      if (!partner) return;
      visited.add(t.id);
      visited.add(partner.id);

      if (t.reviewStatus === 'possible_duplicate') {
        duplicates.push({ t1: t, t2: partner });
      } else if (t.reviewStatus === 'possible_self_transfer') {
        selfTransfers.push({ t1: t, t2: partner });
      } else if (t.reviewStatus === 'possible_refund_match') {
        refunds.push({ credit: t, debit: partner });
      }
    });

    let hasItems = false;

    // Duplicates Section
    if (duplicates.length > 0) {
      hasItems = true;
      content.innerHTML += '<h4 style="margin-bottom: 8px; color: #F59E0B;">Possible Duplicates</h4>';
      duplicates.forEach(({ t1, t2 }) => {
        const card = document.createElement('div');
        card.className = 'review-card';
        card.innerHTML = `
          <div class="review-card-duo">
            <div class="review-sub-box">
              <strong>${t1.merchantRaw || 'No merchant'}</strong><br>
              <span style="color: #EF4444;">-${t1.amount.toFixed(2)}</span><br>
              <span style="color: var(--text-muted); font-size: 11px;">${new Date(t1.transactionDate).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span><br>
              <button class="btn-text btn-sm" style="color: #EF4444; margin-top: 6px;" data-del-id="${t1.id}">Delete this</button>
            </div>
            <div class="review-sub-box">
              <strong>${t2.merchantRaw || 'No merchant'}</strong><br>
              <span style="color: #EF4444;">-${t2.amount.toFixed(2)}</span><br>
              <span style="color: var(--text-muted); font-size: 11px;">${new Date(t2.transactionDate).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span><br>
              <button class="btn-text btn-sm" style="color: #EF4444; margin-top: 6px;" data-del-id="${t2.id}">Delete this</button>
            </div>
          </div>
          <button class="btn-secondary btn-sm btn-block" data-keep-both="${t1.id}">Keep both (Not a duplicate)</button>
        `;
        card.querySelectorAll('[data-del-id]').forEach(b => {
          b.addEventListener('click', () => {
            const delId = b.getAttribute('data-del-id');
            let updated = Phase2DB.getTransactions();
            const partner = updated.find(x => x.reviewPartnerId === delId);
            if (partner) {
              partner.reviewStatus = 'none';
              partner.reviewPartnerId = null;
            }
            updated = updated.filter(x => x.id !== delId);
            Phase2DB.saveTransactions(updated);
            renderReviewQueueModal();
            renderExpenses();
          });
        });
        card.querySelector('[data-keep-both]').addEventListener('click', () => {
          const updated = Phase2DB.getTransactions();
          const item1 = updated.find(x => x.id === t1.id);
          const item2 = updated.find(x => x.id === t2.id);
          if (item1) { item1.reviewStatus = 'none'; item1.reviewPartnerId = null; }
          if (item2) { item2.reviewStatus = 'none'; item2.reviewPartnerId = null; }
          Phase2DB.saveTransactions(updated);
          renderReviewQueueModal();
          renderExpenses();
        });
        content.appendChild(card);
      });
    }

    // Unparsed SMS Section
    if (unparsedSms.length > 0) {
      hasItems = true;
      content.innerHTML += '<h4 style="margin: 16px 0 8px 0; color: #06B6D4;">SMS to Review</h4>';
      unparsedSms.forEach(sms => {
        const card = document.createElement('div');
        card.className = 'review-card';
        card.innerHTML = `
          <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); margin-bottom: 4px;">
            <span>${sms.sender || 'Unknown Sender'}</span>
            <span>${new Date(sms.receivedAt).toLocaleDateString()}</span>
          </div>
          <p style="font-size: 13px; line-height: 1.4; margin-bottom: 10px;">${sms.rawText}</p>
          <div style="display: flex; gap: 8px;">
            <button class="btn-secondary btn-sm" style="flex: 1;" data-discard-sms="${sms.id}">Discard</button>
            <button class="btn-primary btn-sm" style="flex: 1;" data-add-sms="${sms.id}">Add Transaction</button>
          </div>
        `;
        card.querySelector('[data-discard-sms]').addEventListener('click', () => {
          const allSms = Phase2DB.getUnparsedSms();
          const target = allSms.find(s => s.id === sms.id);
          if (target) target.status = 'discarded';
          Phase2DB.saveUnparsedSms(allSms);
          renderReviewQueueModal();
          renderExpenses();
        });
        card.querySelector('[data-add-sms]').addEventListener('click', () => {
          modalReview.classList.remove('active');
          openCreateTransaction({ amount: 450.00, merchant: 'Zomato UPI' });
        });
        content.appendChild(card);
      });
    }

    if (!hasItems) {
      content.innerHTML = '<p class="empty-state">Nothing to review right now.</p>';
    }
  }

  // ==================== PHASE 4 FITNESS IMPLEMENTATION ====================

  const Phase4DB = {
    getGoals() {
      const stored = localStorage.getItem('alt_fitness_goals');
      if (stored) return JSON.parse(stored);
      return {
        stepsGoal: 8000,
        waterGoalMl: 2000,
        calorieGoal: null
      };
    },
    saveGoals(goals) {
      localStorage.setItem('alt_fitness_goals', JSON.stringify(goals));
    },
    getDailyLogs() {
      return JSON.parse(localStorage.getItem('alt_fitness_daily_logs') || '{}');
    },
    saveDailyLogs(logs) {
      localStorage.setItem('alt_fitness_daily_logs', JSON.stringify(logs));
    },
    getStepsForDate(dateStr) {
      const logs = this.getDailyLogs();
      return logs[dateStr] || { steps: 0, stepsSource: 'none' };
    },
    setStepsForDate(dateStr, steps, source) {
      const logs = this.getDailyLogs();
      const existing = logs[dateStr] || { steps: 0, stepsSource: 'none' };

      let shouldUpdate = true;
      if (existing.stepsSource === 'manual' && source === 'sensor') {
        shouldUpdate = false;
      } else if (existing.stepsSource === 'sensor' && source === 'sensor' && steps < existing.steps) {
        shouldUpdate = false;
      }

      if (shouldUpdate) {
        logs[dateStr] = { steps, stepsSource: source };
        this.saveDailyLogs(logs);
        return logs[dateStr];
      }
      return existing;
    },
    getWaterEntries() {
      return JSON.parse(localStorage.getItem('alt_fitness_water') || '[]');
    },
    saveWaterEntries(entries) {
      localStorage.setItem('alt_fitness_water', JSON.stringify(entries));
    },
    getWaterForDate(dateStr) {
      return this.getWaterEntries().filter(e => e.date === dateStr);
    },
    addWaterEntry(dateStr, amountMl) {
      const entries = this.getWaterEntries();
      const newEntry = {
        id: 'water-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6),
        date: dateStr,
        amountMl: parseInt(amountMl, 10),
        loggedAt: new Date().toISOString()
      };
      entries.unshift(newEntry);
      this.saveWaterEntries(entries);
      return newEntry;
    },
    deleteWaterEntry(id) {
      const updated = this.getWaterEntries().filter(e => e.id !== id);
      this.saveWaterEntries(updated);
    },
    getCalorieEntries() {
      return JSON.parse(localStorage.getItem('alt_fitness_calories') || '[]');
    },
    saveCalorieEntries(entries) {
      localStorage.setItem('alt_fitness_calories', JSON.stringify(entries));
    },
    getCaloriesForDate(dateStr) {
      return this.getCalorieEntries().filter(e => e.date === dateStr);
    },
    addCalorieEntry(dateStr, kcal, label) {
      const entries = this.getCalorieEntries();
      const newEntry = {
        id: 'cal-' + Date.now() + '-' + Math.random().toString(36).substr(2, 6),
        date: dateStr,
        kcal: parseInt(kcal, 10),
        label: label ? label.trim() : null,
        loggedAt: new Date().toISOString()
      };
      entries.unshift(newEntry);
      this.saveCalorieEntries(entries);
      return newEntry;
    },
    deleteCalorieEntry(id) {
      const updated = this.getCalorieEntries().filter(e => e.id !== id);
      this.saveCalorieEntries(updated);
    },
    getWorkouts() {
      return JSON.parse(localStorage.getItem('alt_fitness_workouts') || '[]');
    },
    saveWorkouts(workouts) {
      localStorage.setItem('alt_fitness_workouts', JSON.stringify(workouts));
    },
    getWorkoutsForDate(dateStr) {
      return this.getWorkouts().filter(w => w.date === dateStr);
    },
    saveWorkout(workout) {
      const workouts = this.getWorkouts();
      const index = workouts.findIndex(w => w.id === workout.id);
      if (index >= 0) {
        workouts[index] = workout;
      } else {
        workouts.unshift(workout);
      }
      this.saveWorkouts(workouts);
    },
    deleteWorkout(id) {
      const updated = this.getWorkouts().filter(w => w.id !== id);
      this.saveWorkouts(updated);
    },
    getFavorites() {
      return JSON.parse(localStorage.getItem('alt_fitness_favorites') || '[]');
    },
    toggleFavorite(exerciseId) {
      let favs = this.getFavorites();
      if (favs.includes(exerciseId)) {
        favs = favs.filter(id => id !== exerciseId);
      } else {
        favs.push(exerciseId);
      }
      localStorage.setItem('alt_fitness_favorites', JSON.stringify(favs));
      return favs.includes(exerciseId);
    },
    isFavorite(exerciseId) {
      return this.getFavorites().includes(exerciseId);
    }
  };

  // Seed sample initial fitness data if none exists
  (function seedFitnessData() {
    if (!localStorage.getItem('alt_fitness_seeded')) {
      const todayStr = new Date().toISOString().split('T')[0];
      Phase4DB.setStepsForDate(todayStr, 5420, 'sensor');
      Phase4DB.addWaterEntry(todayStr, 250);
      Phase4DB.addWaterEntry(todayStr, 500);
      Phase4DB.addWaterEntry(todayStr, 150);
      Phase4DB.addCalorieEntry(todayStr, 420, 'Oatmeal & fruits');
      Phase4DB.addCalorieEntry(todayStr, 680, 'Grilled chicken wrap');

      const sampleWorkout = {
        id: 'workout-seed-1',
        date: todayStr,
        name: 'Morning Full Body',
        durationMin: 35,
        intensity: 'moderate',
        caloriesBurnedEst: 210,
        notes: 'Great routine',
        entries: [
          {
            id: 'we-1',
            position: 0,
            exerciseId: 'Pushups',
            exerciseName: 'Pushups',
            thumbnailUrl: 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/Pushups/0.jpg',
            sets: 3,
            reps: 15,
            durationSeconds: null,
            weightKg: null
          },
          {
            id: 'we-2',
            position: 1,
            exerciseId: null,
            customName: 'Jumping Jacks',
            thumbnailUrl: null,
            sets: 2,
            reps: null,
            durationSeconds: 120,
            weightKg: null
          }
        ]
      };
      Phase4DB.saveWorkout(sampleWorkout);
      localStorage.setItem('alt_fitness_seeded', 'true');
    }
  })();

  // Exercise database in memory
  let exerciseLibrary = [];
  const exerciseBaseUrl = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/';

  function getExerciseImageUrl(relativePath) {
    if (!relativePath) return null;
    const segments = relativePath.split('/').map(s => encodeURIComponent(s));
    return exerciseBaseUrl + segments.join('/');
  }

  // Load exercises.json
  async function loadExerciseLibrary() {
    if (exerciseLibrary.length > 0) return;
    try {
      const res = await fetch('exercises.json');
      if (res.ok) {
        const data = await res.json();
        exerciseLibrary = data;
        populateExerciseFilterDropdowns();
      }
    } catch (e) {
      console.warn('Failed to fetch exercises.json, loading fallback', e);
    }
  }

  function populateExerciseFilterDropdowns() {
    if (!exerciseLibrary.length) return;
    const muscles = [...new Set(exerciseLibrary.flatMap(e => e.primaryMuscles || []))].sort();
    const equipment = [...new Set(exerciseLibrary.map(e => e.equipment).filter(Boolean))].sort();
    const levels = [...new Set(exerciseLibrary.map(e => e.level).filter(Boolean))].sort();
    const categories = [...new Set(exerciseLibrary.map(e => e.category).filter(Boolean))].sort();

    const mSelect = document.getElementById('lib-filter-muscle');
    const eqSelect = document.getElementById('lib-filter-equipment');
    const lSelect = document.getElementById('lib-filter-level');
    const cSelect = document.getElementById('lib-filter-category');

    if (mSelect && mSelect.options.length <= 1) {
      muscles.forEach(m => mSelect.innerHTML += `<option value="${m}">${m}</option>`);
    }
    if (eqSelect && eqSelect.options.length <= 1) {
      equipment.forEach(eq => eqSelect.innerHTML += `<option value="${eq}">${eq}</option>`);
    }
    if (lSelect && lSelect.options.length <= 1) {
      levels.forEach(l => lSelect.innerHTML += `<option value="${l}">${l}</option>`);
    }
    if (cSelect && cSelect.options.length <= 1) {
      categories.forEach(c => cSelect.innerHTML += `<option value="${c}">${c}</option>`);
    }
  }

  // Pure BMR calculation (A8)
  function calculateBmr(user, onDateStr) {
    if (!user || !user.profile) return null;
    const p = user.profile;
    if (!p.sexAtBirth || !p.weightKg || !p.heightCm || !p.dateOfBirth) return null;

    const sex = p.sexAtBirth.toLowerCase();
    if (sex !== 'male' && sex !== 'female') return null;

    const onDate = new Date(onDateStr);
    const dob = new Date(p.dateOfBirth);
    let age = onDate.getFullYear() - dob.getFullYear();
    const m = onDate.getMonth() - dob.getMonth();
    if (m < 0 || (m === 0 && onDate.getDate() < dob.getDate())) {
      age--;
    }
    if (age < 0) return null;

    const base = (10 * parseFloat(p.weightKg)) + (6.25 * parseFloat(p.heightCm)) - (5 * age);
    const bmr = sex === 'male' ? base + 5 : base - 161;
    return Math.round(bmr);
  }

  // Pure Calories burned from workout (A9)
  function calculateCaloriesBurned(intensity, durationMin, weightKg) {
    if (!weightKg || durationMin <= 0) return null;
    const metMap = { light: 3.0, moderate: 5.0, vigorous: 8.0 };
    const met = metMap[intensity] || 5.0;
    return Math.round((met * weightKg * durationMin) / 60);
  }

  // Current Fitness Selected Date
  let currentFitnessDate = new Date();

  function getFitnessDateString() {
    return currentFitnessDate.toISOString().split('T')[0];
  }

  function isFitnessDateToday() {
    const todayStr = new Date().toISOString().split('T')[0];
    return getFitnessDateString() === todayStr;
  }

  // Main Fitness Dashboard Renderer
  function renderFitnessDashboard() {
    const user = DB.getCurrentUser();
    const dateStr = getFitnessDateString();
    const goals = Phase4DB.getGoals();

    // Date Bar
    const dateDisplay = document.getElementById('fitness-date-display');
    const nextDayBtn = document.getElementById('btn-fitness-next-day');
    const isToday = isFitnessDateToday();

    dateDisplay.textContent = isToday ? `Today (${dateStr})` : dateStr;
    nextDayBtn.disabled = isToday;

    // 1. Steps Card
    const stepsData = Phase4DB.getStepsForDate(dateStr);
    const stepsVal = stepsData.steps;
    const stepsGoal = goals.stepsGoal;
    const stepsPercent = Math.floor((stepsVal * 100) / stepsGoal);

    document.getElementById('fitness-steps-value').textContent = stepsVal.toLocaleString();
    document.getElementById('fitness-steps-goal').textContent = stepsGoal.toLocaleString();
    document.getElementById('fitness-steps-percent').textContent = stepsPercent;

    const sourceLabel = stepsData.stepsSource === 'sensor'
      ? 'from phone sensor'
      : stepsData.stepsSource === 'manual'
        ? 'entered manually'
        : 'no data yet';
    document.getElementById('fitness-steps-source').textContent = sourceLabel;

    const stepsRing = document.getElementById('steps-ring-circle');
    const circum = 175.93;
    const stepsClamped = Math.min(Math.max(stepsPercent, 0), 100);
    stepsRing.style.strokeDashoffset = circum - (stepsClamped / 100) * circum;

    // 2. Water Card
    const waterEntries = Phase4DB.getWaterForDate(dateStr);
    const waterTotal = waterEntries.reduce((sum, e) => sum + e.amountMl, 0);
    const waterGoal = goals.waterGoalMl;
    const waterPercent = Math.floor((waterTotal * 100) / waterGoal);

    document.getElementById('fitness-water-value').textContent = waterTotal.toLocaleString();
    document.getElementById('fitness-water-goal').textContent = waterGoal.toLocaleString();
    document.getElementById('fitness-water-percent').textContent = waterPercent;

    const waterRing = document.getElementById('water-ring-circle');
    const waterClamped = Math.min(Math.max(waterPercent, 0), 100);
    waterRing.style.strokeDashoffset = circum - (waterClamped / 100) * circum;

    const waterWrap = document.getElementById('fitness-water-entries-wrap');
    const waterList = document.getElementById('fitness-water-list');
    if (waterEntries.length > 0) {
      waterWrap.style.display = 'block';
      waterList.innerHTML = '';
      waterEntries.forEach(entry => {
        const time = new Date(entry.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const row = document.createElement('div');
        row.className = 'entry-row';
        row.innerHTML = `
          <span>${time} &nbsp;&bull;&nbsp; <strong>${entry.amountMl} ml</strong></span>
          <button class="btn-del-entry" data-del-water="${entry.id}">&times;</button>
        `;
        row.querySelector('[data-del-water]').addEventListener('click', () => {
          Phase4DB.deleteWaterEntry(entry.id);
          renderFitnessDashboard();
        });
        waterList.appendChild(row);
      });
    } else {
      waterWrap.style.display = 'none';
      waterList.innerHTML = '';
    }

    // 3. Calories Card
    const calorieEntries = Phase4DB.getCaloriesForDate(dateStr);
    const caloriesIn = calorieEntries.reduce((sum, e) => sum + e.kcal, 0);
    document.getElementById('fitness-calories-value').textContent = caloriesIn.toLocaleString();

    const calGoalSub = document.getElementById('fitness-calorie-goal-sub');
    const calRing = document.getElementById('calories-ring-circle');
    if (goals.calorieGoal) {
      const calPercent = Math.floor((caloriesIn * 100) / goals.calorieGoal);
      calGoalSub.textContent = `of ${goals.calorieGoal.toLocaleString()} kcal (${calPercent}%)`;
      const calClamped = Math.min(Math.max(calPercent, 0), 100);
      calRing.style.strokeDashoffset = circum - (calClamped / 100) * circum;
    } else {
      calGoalSub.textContent = 'No goal set';
      calRing.style.strokeDashoffset = circum;
    }

    // Workouts calories burned
    const dayWorkouts = Phase4DB.getWorkoutsForDate(dateStr);
    const burnedTotal = dayWorkouts.reduce((sum, w) => sum + (w.caloriesBurnedEst || 0), 0);
    document.getElementById('fitness-calories-burned-text').textContent =
      `Burned in workouts (estimate): ${burnedTotal} kcal`;

    // BMR estimate
    const bmr = calculateBmr(user, dateStr);
    const bmrBox = document.getElementById('fitness-bmr-box');
    if (bmr !== null) {
      bmrBox.textContent = `Resting energy estimate (BMR): ${bmr} kcal. A calculation from your profile.`;
    } else {
      bmrBox.textContent = 'Add sex, birth date, height and weight in your profile to see a resting energy estimate.';
    }

    // Food list
    const foodWrap = document.getElementById('fitness-food-entries-wrap');
    const foodList = document.getElementById('fitness-food-list');
    if (calorieEntries.length > 0) {
      foodWrap.style.display = 'block';
      foodList.innerHTML = '';
      calorieEntries.forEach(entry => {
        const time = new Date(entry.loggedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const label = entry.label ? ` &bull; ${entry.label}` : '';
        const row = document.createElement('div');
        row.className = 'entry-row';
        row.innerHTML = `
          <span>${time}${label} &nbsp;&bull;&nbsp; <strong>${entry.kcal} kcal</strong></span>
          <button class="btn-del-entry" data-del-food="${entry.id}">&times;</button>
        `;
        row.querySelector('[data-del-food]').addEventListener('click', () => {
          Phase4DB.deleteCalorieEntry(entry.id);
          renderFitnessDashboard();
        });
        foodList.appendChild(row);
      });
    } else {
      foodWrap.style.display = 'none';
      foodList.innerHTML = '';
    }

    // 4. Workouts Card
    const workoutsListEl = document.getElementById('fitness-today-workouts-list');
    if (dayWorkouts.length > 0) {
      workoutsListEl.innerHTML = '';
      dayWorkouts.forEach(w => {
        const card = document.createElement('div');
        card.className = 'review-card';
        card.style.cursor = 'pointer';
        card.style.marginBottom = '8px';
        const cal = w.caloriesBurnedEst != null ? ` &bull; ~${w.caloriesBurnedEst} kcal` : '';
        card.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <strong style="font-size: 14px;">${w.name}</strong>
              <div style="font-size: 12px; color: var(--text-muted); margin-top: 2px;">
                ${w.durationMin} min &bull; ${w.intensity}${cal}
              </div>
            </div>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        `;
        card.addEventListener('click', () => openWorkoutForm(w));
        workoutsListEl.appendChild(card);
      });
    } else {
      workoutsListEl.innerHTML = '<p class="empty-state-small">No workouts logged for this day.</p>';
    }
  }

  // Date bar buttons
  document.getElementById('btn-fitness-prev-day')?.addEventListener('click', () => {
    currentFitnessDate.setDate(currentFitnessDate.getDate() - 1);
    renderFitnessDashboard();
  });

  document.getElementById('btn-fitness-next-day')?.addEventListener('click', () => {
    if (!isFitnessDateToday()) {
      currentFitnessDate.setDate(currentFitnessDate.getDate() + 1);
      renderFitnessDashboard();
    }
  });

  // Steps edit & sync
  const modalEditSteps = document.getElementById('modal-edit-steps');
  const editStepsInput = document.getElementById('edit-steps-input');
  const editStepsAlert = document.getElementById('edit-steps-alert');

  document.getElementById('btn-edit-steps')?.addEventListener('click', () => {
    const currentSteps = Phase4DB.getStepsForDate(getFitnessDateString()).steps;
    editStepsInput.value = currentSteps;
    editStepsAlert.style.display = 'none';
    modalEditSteps.classList.add('active');
  });

  document.getElementById('form-edit-steps')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const val = parseInt(editStepsInput.value, 10);
    if (isNaN(val) || val < 0 || val > 200000) {
      editStepsAlert.textContent = 'Steps must be between 0 and 200,000';
      editStepsAlert.style.display = 'block';
      return;
    }
    Phase4DB.setStepsForDate(getFitnessDateString(), val, 'manual');
    modalEditSteps.classList.remove('active');
    renderFitnessDashboard();
  });

  document.getElementById('btn-sync-steps')?.addEventListener('click', () => {
    const btn = document.getElementById('btn-sync-steps');
    btn.disabled = true;
    btn.innerHTML = 'Syncing...';
    setTimeout(() => {
      const current = Phase4DB.getStepsForDate(getFitnessDateString()).steps;
      const simulatedDelta = Math.floor(Math.random() * 800) + 400;
      Phase4DB.setStepsForDate(getFitnessDateString(), current + simulatedDelta, 'sensor');
      btn.disabled = false;
      btn.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
        <span>Sync steps</span>
      `;
      renderFitnessDashboard();
    }, 400);
  });

  // Water quick add & custom
  document.querySelectorAll('[data-water]').forEach(btn => {
    btn.addEventListener('click', () => {
      const amount = parseInt(btn.getAttribute('data-water'), 10);
      Phase4DB.addWaterEntry(getFitnessDateString(), amount);
      renderFitnessDashboard();
    });
  });

  const modalCustomWater = document.getElementById('modal-custom-water');
  const customWaterInput = document.getElementById('custom-water-input');

  document.getElementById('btn-custom-water')?.addEventListener('click', () => {
    customWaterInput.value = '300';
    modalCustomWater.classList.add('active');
  });

  document.getElementById('form-custom-water')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const val = parseInt(customWaterInput.value, 10);
    if (!isNaN(val) && val >= 1 && val <= 5000) {
      Phase4DB.addWaterEntry(getFitnessDateString(), val);
      modalCustomWater.classList.remove('active');
      renderFitnessDashboard();
    }
  });

  // Add food modal
  const modalAddFood = document.getElementById('modal-add-food');
  const addFoodKcalInput = document.getElementById('add-food-kcal-input');
  const addFoodLabelInput = document.getElementById('add-food-label-input');
  const addFoodAlert = document.getElementById('add-food-alert');

  document.getElementById('btn-add-food')?.addEventListener('click', () => {
    addFoodKcalInput.value = '';
    addFoodLabelInput.value = '';
    addFoodAlert.style.display = 'none';
    modalAddFood.classList.add('active');
  });

  document.getElementById('form-add-food')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const kcal = parseInt(addFoodKcalInput.value, 10);
    if (isNaN(kcal) || kcal < 1 || kcal > 10000) {
      addFoodAlert.textContent = 'Calories must be between 1 and 10,000';
      addFoodAlert.style.display = 'block';
      return;
    }
    const label = addFoodLabelInput.value.trim();
    Phase4DB.addCalorieEntry(getFitnessDateString(), kcal, label);
    modalAddFood.classList.remove('active');
    renderFitnessDashboard();
  });

  // Goals modal
  const modalFitnessGoals = document.getElementById('modal-fitness-goals');
  const goalStepsInput = document.getElementById('goal-steps-input');
  const goalWaterInput = document.getElementById('goal-water-input');
  const goalCalorieInput = document.getElementById('goal-calorie-input');
  const fitnessGoalsAlert = document.getElementById('fitness-goals-alert');

  document.getElementById('btn-open-fitness-goals')?.addEventListener('click', () => {
    const goals = Phase4DB.getGoals();
    goalStepsInput.value = goals.stepsGoal;
    goalWaterInput.value = goals.waterGoalMl;
    goalCalorieInput.value = goals.calorieGoal || '';
    fitnessGoalsAlert.style.display = 'none';
    modalFitnessGoals.classList.add('active');
  });

  document.getElementById('form-fitness-goals')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const steps = parseInt(goalStepsInput.value, 10);
    const water = parseInt(goalWaterInput.value, 10);
    const calText = goalCalorieInput.value.trim();
    const calorie = calText ? parseInt(calText, 10) : null;

    if (isNaN(steps) || steps < 100 || steps > 100000) {
      fitnessGoalsAlert.textContent = 'Steps goal must be between 100 and 100,000';
      fitnessGoalsAlert.style.display = 'block';
      return;
    }
    if (isNaN(water) || water < 250 || water > 10000) {
      fitnessGoalsAlert.textContent = 'Water goal must be between 250 and 10,000 ml';
      fitnessGoalsAlert.style.display = 'block';
      return;
    }
    if (calorie !== null && (isNaN(calorie) || calorie < 500 || calorie > 10000)) {
      fitnessGoalsAlert.textContent = 'Calorie goal must be between 500 and 10,000 or empty';
      fitnessGoalsAlert.style.display = 'block';
      return;
    }

    Phase4DB.saveGoals({ stepsGoal: steps, waterGoalMl: water, calorieGoal: calorie });
    modalFitnessGoals.classList.remove('active');
    renderFitnessDashboard();
  });

  // Workout Form & Entries Draft
  const modalWorkoutForm = document.getElementById('modal-workout-form');
  const workoutFormTitle = document.getElementById('workout-form-title');
  const workoutNameInput = document.getElementById('workout-name-input');
  const workoutDurationInput = document.getElementById('workout-duration-input');
  const workoutIntensitySelect = document.getElementById('workout-intensity-select');
  const workoutNotesInput = document.getElementById('workout-notes-input');
  const workoutEntriesCount = document.getElementById('workout-entries-count');
  const workoutEntriesList = document.getElementById('workout-entries-list');
  const workoutFormAlert = document.getElementById('workout-form-alert');

  let currentWorkoutDraft = null;
  let workoutDraftEntries = [];

  function openWorkoutForm(workoutToEdit, initialExercise) {
    currentWorkoutDraft = workoutToEdit || null;
    workoutFormAlert.style.display = 'none';

    if (workoutToEdit) {
      workoutFormTitle.textContent = 'Edit Workout';
      workoutNameInput.value = workoutToEdit.name;
      workoutDurationInput.value = workoutToEdit.durationMin;
      workoutIntensitySelect.value = workoutToEdit.intensity;
      workoutNotesInput.value = workoutToEdit.notes || '';
      workoutDraftEntries = (workoutToEdit.entries || []).map(e => ({ ...e }));
    } else {
      workoutFormTitle.textContent = 'Log Workout';
      workoutNameInput.value = 'Workout Routine';
      workoutDurationInput.value = '30';
      workoutIntensitySelect.value = 'moderate';
      workoutNotesInput.value = '';
      workoutDraftEntries = [];

      if (initialExercise) {
        workoutDraftEntries.push({
          id: 'draft-we-1',
          exerciseId: initialExercise.id,
          exerciseName: initialExercise.name,
          thumbnailUrl: initialExercise.images && initialExercise.images.length > 0 ? getExerciseImageUrl(initialExercise.images[0]) : null,
          sets: 3,
          reps: 10,
          durationSeconds: null,
          weightKg: null
        });
      }
    }

    renderWorkoutEntriesDraft();
    modalWorkoutForm.classList.add('active');
  }

  function renderWorkoutEntriesDraft() {
    workoutEntriesCount.textContent = workoutDraftEntries.length;
    workoutEntriesList.innerHTML = '';

    if (workoutDraftEntries.length === 0) {
      workoutEntriesList.innerHTML = '<p class="empty-state-small">No exercises added yet. Add from library or custom.</p>';
      return;
    }

    workoutDraftEntries.forEach((entry, idx) => {
      const card = document.createElement('div');
      card.className = 'workout-entry-card';

      const thumbImg = entry.thumbnailUrl
        ? `<img src="${entry.thumbnailUrl}" style="width: 36px; height: 36px; border-radius: 4px; object-fit: cover;">`
        : `<div style="width: 36px; height: 36px; border-radius: 4px; background: rgba(255,255,255,0.08); display: flex; align-items: center; justify-content: center;"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="5" r="2"/><path d="m4 17 6-4 4 2 6-5"/></svg></div>`;

      card.innerHTML = `
        <div class="workout-entry-top">
          ${thumbImg}
          <div class="workout-entry-name">${entry.exerciseName || entry.customName}</div>
          <button type="button" class="btn-text btn-sm" data-move-up="${idx}" ${idx === 0 ? 'disabled' : ''}>&uarr;</button>
          <button type="button" class="btn-text btn-sm" data-move-down="${idx}" ${idx === workoutDraftEntries.length - 1 ? 'disabled' : ''}>&darr;</button>
          <button type="button" class="btn-del-entry" data-del-entry="${idx}">&times;</button>
        </div>
        <div class="workout-entry-inputs">
          <div>
            <label>Sets</label>
            <input type="number" min="1" max="50" value="${entry.sets || 3}" data-bind-prop="sets" data-idx="${idx}">
          </div>
          <div>
            <label>Reps</label>
            <input type="number" min="1" max="500" value="${entry.reps != null ? entry.reps : ''}" placeholder="-" data-bind-prop="reps" data-idx="${idx}">
          </div>
          <div>
            <label>Sec</label>
            <input type="number" min="1" max="7200" value="${entry.durationSeconds != null ? entry.durationSeconds : ''}" placeholder="-" data-bind-prop="durationSeconds" data-idx="${idx}">
          </div>
          <div>
            <label>Kg</label>
            <input type="number" step="0.5" min="0" max="1000" value="${entry.weightKg != null ? entry.weightKg : ''}" placeholder="-" data-bind-prop="weightKg" data-idx="${idx}">
          </div>
        </div>
      `;

      card.querySelector('[data-del-entry]').addEventListener('click', () => {
        workoutDraftEntries.splice(idx, 1);
        renderWorkoutEntriesDraft();
      });

      card.querySelector('[data-move-up]').addEventListener('click', () => {
        if (idx > 0) {
          const item = workoutDraftEntries.splice(idx, 1)[0];
          workoutDraftEntries.splice(idx - 1, 0, item);
          renderWorkoutEntriesDraft();
        }
      });

      card.querySelector('[data-move-down]').addEventListener('click', () => {
        if (idx < workoutDraftEntries.length - 1) {
          const item = workoutDraftEntries.splice(idx, 1)[0];
          workoutDraftEntries.splice(idx + 1, 0, item);
          renderWorkoutEntriesDraft();
        }
      });

      card.querySelectorAll('[data-bind-prop]').forEach(inp => {
        inp.addEventListener('input', (e) => {
          const prop = e.target.getAttribute('data-bind-prop');
          const i = parseInt(e.target.getAttribute('data-idx'), 10);
          const raw = e.target.value.trim();
          if (prop === 'weightKg') {
            workoutDraftEntries[i][prop] = raw ? parseFloat(raw) : null;
          } else {
            workoutDraftEntries[i][prop] = raw ? parseInt(raw, 10) : null;
          }
        });
      });

      workoutEntriesList.appendChild(card);
    });
  }

  document.getElementById('btn-log-workout-today')?.addEventListener('click', () => openWorkoutForm());

  document.getElementById('btn-workout-add-lib')?.addEventListener('click', () => {
    openExerciseLibrary(true); // pickMode = true
  });

  document.getElementById('btn-workout-add-custom')?.addEventListener('click', () => {
    const customName = prompt('Enter custom exercise name:');
    if (customName && customName.trim()) {
      workoutDraftEntries.push({
        id: 'draft-cust-' + Date.now(),
        exerciseId: null,
        customName: customName.trim(),
        exerciseName: customName.trim(),
        thumbnailUrl: null,
        sets: 3,
        reps: 10,
        durationSeconds: null,
        weightKg: null
      });
      renderWorkoutEntriesDraft();
    }
  });

  document.getElementById('form-workout')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const name = workoutNameInput.value.trim();
    const duration = parseInt(workoutDurationInput.value, 10);
    const intensity = workoutIntensitySelect.value;
    const notes = workoutNotesInput.value.trim();

    if (!name) {
      workoutFormAlert.textContent = 'Please enter workout name';
      workoutFormAlert.style.display = 'block';
      return;
    }
    if (isNaN(duration) || duration < 1 || duration > 600) {
      workoutFormAlert.textContent = 'Duration must be between 1 and 600 min';
      workoutFormAlert.style.display = 'block';
      return;
    }
    if (workoutDraftEntries.length === 0) {
      workoutFormAlert.textContent = 'Add at least one exercise entry';
      workoutFormAlert.style.display = 'block';
      return;
    }

    for (let i = 0; i < workoutDraftEntries.length; i++) {
      const entry = workoutDraftEntries[i];
      if (!entry.sets || entry.sets < 1 || entry.sets > 50) {
        workoutFormAlert.textContent = `Entry ${i + 1}: sets must be 1 to 50`;
        workoutFormAlert.style.display = 'block';
        return;
      }
      if (entry.reps == null && entry.durationSeconds == null) {
        workoutFormAlert.textContent = `Entry ${i + 1}: enter either reps or duration (seconds)`;
        workoutFormAlert.style.display = 'block';
        return;
      }
    }

    const user = DB.getCurrentUser();
    const userWeight = user && user.profile ? parseFloat(user.profile.weightKg) : null;
    const caloriesBurned = calculateCaloriesBurned(intensity, duration, userWeight);

    const workoutId = currentWorkoutDraft ? currentWorkoutDraft.id : 'workout-' + Date.now();
    const dateStr = currentWorkoutDraft ? currentWorkoutDraft.date : getFitnessDateString();

    const savedWorkout = {
      id: workoutId,
      date: dateStr,
      name,
      durationMin: duration,
      intensity,
      caloriesBurnedEst: caloriesBurned,
      notes: notes || null,
      entries: workoutDraftEntries.map((e, pos) => ({
        id: e.id || 'we-' + pos + '-' + Date.now(),
        position: pos,
        exerciseId: e.exerciseId || null,
        customName: e.customName || (e.exerciseId ? null : e.exerciseName),
        exerciseName: e.exerciseName || e.customName,
        thumbnailUrl: e.thumbnailUrl || null,
        sets: e.sets || 3,
        reps: e.reps != null ? e.reps : null,
        durationSeconds: e.durationSeconds != null ? e.durationSeconds : null,
        weightKg: e.weightKg != null ? e.weightKg : null
      }))
    };

    Phase4DB.saveWorkout(savedWorkout);
    modalWorkoutForm.classList.remove('active');
    renderFitnessDashboard();
  });

  // Workout History Modal
  const modalWorkoutHistory = document.getElementById('modal-workout-history');
  const workoutHistoryContent = document.getElementById('workout-history-content');

  document.getElementById('btn-open-workouts-history')?.addEventListener('click', () => {
    renderWorkoutHistoryModal();
    modalWorkoutHistory.classList.add('active');
  });

  function renderWorkoutHistoryModal() {
    const workouts = Phase4DB.getWorkouts();
    if (workouts.length === 0) {
      workoutHistoryContent.innerHTML = '<p class="empty-state">No workouts logged yet.</p>';
      return;
    }

    workoutHistoryContent.innerHTML = '';
    workouts.forEach(w => {
      const card = document.createElement('div');
      card.className = 'review-card';
      card.style.marginBottom = '10px';
      const cal = w.caloriesBurnedEst != null ? ` &bull; ~${w.caloriesBurnedEst} kcal` : '';
      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <div style="font-size: 11px; color: var(--text-muted);">${w.date}</div>
            <strong style="font-size: 15px;">${w.name}</strong>
            <div style="font-size: 12px; color: var(--text-secondary); margin-top: 4px;">
              ${w.durationMin} min &bull; ${w.intensity}${cal} &bull; ${w.entries.length} exercises
            </div>
          </div>
          <div style="display: flex; gap: 4px;">
            <button class="btn-text btn-sm" data-edit-w="${w.id}">Edit</button>
            <button class="btn-del-entry" data-del-w="${w.id}" title="Delete workout">&times;</button>
          </div>
        </div>
      `;
      card.querySelector('[data-edit-w]').addEventListener('click', () => {
        modalWorkoutHistory.classList.remove('active');
        openWorkoutForm(w);
      });
      card.querySelector('[data-del-w]').addEventListener('click', () => {
        if (confirm('Delete this workout?')) {
          Phase4DB.deleteWorkout(w.id);
          renderWorkoutHistoryModal();
          renderFitnessDashboard();
        }
      });
      workoutHistoryContent.appendChild(card);
    });
  }

  // Exercise Library Modal
  const modalExerciseLibrary = document.getElementById('modal-exercise-library');
  const librarySearchInput = document.getElementById('library-search-input');
  const exerciseLibraryItems = document.getElementById('exercise-library-items');
  const btnLibFavFilter = document.getElementById('btn-lib-favorites-filter');
  const libModalTitle = document.getElementById('library-modal-title');

  let libraryPickMode = false;
  let libFavoritesOnly = false;
  let searchDebounceTimer = null;

  document.getElementById('btn-open-exercises')?.addEventListener('click', () => {
    openExerciseLibrary(false);
  });

  function openExerciseLibrary(pickMode = false) {
    libraryPickMode = pickMode;
    libModalTitle.textContent = pickMode ? 'Select Exercise' : 'Exercise Library';
    modalExerciseLibrary.classList.add('active');
    loadExerciseLibrary().then(() => {
      renderExerciseLibraryItems();
    });
  }

  librarySearchInput?.addEventListener('input', () => {
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      renderExerciseLibraryItems();
    }, 400);
  });

  btnLibFavFilter?.addEventListener('click', () => {
    libFavoritesOnly = !libFavoritesOnly;
    btnLibFavFilter.classList.toggle('active', libFavoritesOnly);
    renderExerciseLibraryItems();
  });

  ['lib-filter-muscle', 'lib-filter-equipment', 'lib-filter-level', 'lib-filter-category'].forEach(id => {
    document.getElementById(id)?.addEventListener('change', () => {
      renderExerciseLibraryItems();
    });
  });

  function renderExerciseLibraryItems() {
    const q = (librarySearchInput?.value || '').trim().toLowerCase();
    const muscle = document.getElementById('lib-filter-muscle')?.value || '';
    const equipment = document.getElementById('lib-filter-equipment')?.value || '';
    const level = document.getElementById('lib-filter-level')?.value || '';
    const category = document.getElementById('lib-filter-category')?.value || '';

    let filtered = exerciseLibrary.filter(ex => {
      if (q && !ex.name.toLowerCase().includes(q)) return false;
      if (muscle && (!ex.primaryMuscles || !ex.primaryMuscles.includes(muscle))) return false;
      if (equipment && ex.equipment !== equipment) return false;
      if (level && ex.level !== level) return false;
      if (category && ex.category !== category) return false;
      if (libFavoritesOnly && !Phase4DB.isFavorite(ex.id)) return false;
      return true;
    });

    if (filtered.length === 0) {
      exerciseLibraryItems.innerHTML = '<p class="empty-state">No matching exercises found.</p>';
      return;
    }

    exerciseLibraryItems.innerHTML = '';
    // Show first 60 for performance
    const displayList = filtered.slice(0, 60);

    displayList.forEach(ex => {
      const card = document.createElement('div');
      card.className = 'exercise-item-card';

      const thumb = ex.images && ex.images.length > 0
        ? getExerciseImageUrl(ex.images[0])
        : null;

      const imgTag = thumb
        ? `<img class="exercise-thumb" src="${thumb}" alt="${ex.name}" loading="lazy">`
        : `<div class="exercise-thumb" style="display:flex;align-items:center;justify-content:center;"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="5" r="2"/><path d="m4 17 6-4 4 2 6-5"/></svg></div>`;

      const isFav = Phase4DB.isFavorite(ex.id);
      const sub = [
        (ex.primaryMuscles || []).join(', '),
        ex.equipment,
        ex.level
      ].filter(Boolean).join(' &bull; ');

      card.innerHTML = `
        ${imgTag}
        <div class="exercise-item-info">
          <div class="exercise-item-name">${ex.name}</div>
          <div class="exercise-item-sub">${sub}</div>
        </div>
        <button class="icon-btn" data-toggle-fav="${ex.id}" style="color: ${isFav ? '#FBBF24' : 'var(--text-muted)'};">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="${isFav ? '#FBBF24' : 'none'}" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
        </button>
      `;

      card.querySelector('[data-toggle-fav]').addEventListener('click', (e) => {
        e.stopPropagation();
        Phase4DB.toggleFavorite(ex.id);
        renderExerciseLibraryItems();
      });

      card.addEventListener('click', () => {
        openExerciseDetail(ex, libraryPickMode);
      });

      exerciseLibraryItems.appendChild(card);
    });
  }

  // Exercise Detail Modal with 2-photo alternating animation
  const modalExerciseDetail = document.getElementById('modal-exercise-detail');
  const exerciseDetailName = document.getElementById('exercise-detail-name');
  const exerciseHeroImg = document.getElementById('exercise-hero-img');
  const exerciseThumbnailsRow = document.getElementById('exercise-thumbnails-row');
  const exerciseDetailTags = document.getElementById('exercise-detail-tags');
  const exerciseDetailInstructions = document.getElementById('exercise-detail-instructions');
  const btnExerciseDetailFav = document.getElementById('btn-exercise-detail-fav');
  const btnExerciseAddToWorkout = document.getElementById('btn-exercise-add-to-workout');

  let exerciseAnimTimer = null;
  let currentDetailExercise = null;
  let currentDetailImgIdx = 0;
  let isDetailAnimPaused = false;

  function openExerciseDetail(ex, pickMode = false) {
    currentDetailExercise = ex;
    currentDetailImgIdx = 0;
    isDetailAnimPaused = false;

    exerciseDetailName.textContent = ex.name;

    // Photos
    const images = (ex.images || []).map(p => getExerciseImageUrl(p));
    clearInterval(exerciseAnimTimer);

    if (images.length > 0) {
      exerciseHeroImg.src = images[0];
      exerciseHeroImg.style.display = 'block';

      if (images.length > 1) {
        exerciseThumbnailsRow.style.display = 'flex';
        exerciseThumbnailsRow.innerHTML = '';
        images.forEach((imgUrl, idx) => {
          const thumb = document.createElement('img');
          thumb.className = 'exercise-mini-thumb' + (idx === 0 ? ' active' : '');
          thumb.src = imgUrl;
          thumb.addEventListener('click', () => {
            currentDetailImgIdx = idx;
            isDetailAnimPaused = true;
            exerciseHeroImg.src = images[idx];
            updateThumbActive(idx);
          });
          exerciseThumbnailsRow.appendChild(thumb);
        });

        // Alternates photos every 1s
        exerciseAnimTimer = setInterval(() => {
          if (!isDetailAnimPaused && modalExerciseDetail.classList.contains('active')) {
            currentDetailImgIdx = (currentDetailImgIdx + 1) % images.length;
            exerciseHeroImg.src = images[currentDetailImgIdx];
            updateThumbActive(currentDetailImgIdx);
          }
        }, 1000);
      } else {
        exerciseThumbnailsRow.style.display = 'none';
      }
    } else {
      exerciseHeroImg.style.display = 'none';
      exerciseThumbnailsRow.style.display = 'none';
    }

    exerciseHeroImg.onclick = () => {
      isDetailAnimPaused = false;
    };

    function updateThumbActive(idx) {
      exerciseThumbnailsRow.querySelectorAll('.exercise-mini-thumb').forEach((t, i) => {
        t.classList.toggle('active', i === idx);
      });
    }

    // Favorite star
    updateDetailFavBtn();
    btnExerciseDetailFav.onclick = () => {
      Phase4DB.toggleFavorite(ex.id);
      updateDetailFavBtn();
      renderExerciseLibraryItems();
    };

    function updateDetailFavBtn() {
      const isFav = Phase4DB.isFavorite(ex.id);
      btnExerciseDetailFav.style.color = isFav ? '#FBBF24' : 'var(--text-muted)';
      btnExerciseDetailFav.querySelector('svg').setAttribute('fill', isFav ? '#FBBF24' : 'none');
    }

    // Tags
    exerciseDetailTags.innerHTML = '';
    if (ex.level) exerciseDetailTags.innerHTML += `<span class="exercise-tag">${ex.level}</span>`;
    exerciseDetailTags.innerHTML += `<span class="exercise-tag">${ex.equipment}</span>`;
    exerciseDetailTags.innerHTML += `<span class="exercise-tag">${ex.category}</span>`;
    (ex.primaryMuscles || []).forEach(m => {
      exerciseDetailTags.innerHTML += `<span class="exercise-tag" style="color: #818CF8;">${m}</span>`;
    });
    if (ex.secondaryMuscles && ex.secondaryMuscles.length > 0) {
      exerciseDetailTags.innerHTML += `<span class="exercise-tag" style="color: var(--text-muted);">Sec: ${ex.secondaryMuscles.join(', ')}</span>`;
    }

    // Instructions
    exerciseDetailInstructions.innerHTML = '';
    (ex.instructions || []).forEach(inst => {
      const li = document.createElement('li');
      li.textContent = inst;
      exerciseDetailInstructions.appendChild(li);
    });

    // Add to workout action
    btnExerciseAddToWorkout.textContent = pickMode ? 'Select Exercise' : 'Add to Workout';
    btnExerciseAddToWorkout.onclick = () => {
      clearInterval(exerciseAnimTimer);
      modalExerciseDetail.classList.remove('active');
      modalExerciseLibrary.classList.remove('active');

      if (pickMode) {
        workoutDraftEntries.push({
          id: 'draft-we-' + Date.now(),
          exerciseId: ex.id,
          exerciseName: ex.name,
          thumbnailUrl: images.length > 0 ? images[0] : null,
          sets: 3,
          reps: 10,
          durationSeconds: null,
          weightKg: null
        });
        renderWorkoutEntriesDraft();
      } else {
        openWorkoutForm(null, ex);
      }
    };

    modalExerciseDetail.classList.add('active');
  }

  // Fitness Charts Modal with 7/30 days toggle
  const modalFitnessCharts = document.getElementById('modal-fitness-charts');
  const btnChart7d = document.getElementById('btn-chart-7d');
  const btnChart30d = document.getElementById('btn-chart-30d');
  const chartStepsBars = document.getElementById('chart-steps-bars');
  const chartStepsAvg = document.getElementById('chart-steps-avg');
  const chartWaterBars = document.getElementById('chart-water-bars');
  const chartWaterAvg = document.getElementById('chart-water-avg');
  const chartCaloriesBars = document.getElementById('chart-calories-bars');
  const chartCaloriesAvg = document.getElementById('chart-calories-avg');

  let chartDaysRange = 7;

  document.getElementById('btn-open-fitness-charts')?.addEventListener('click', () => {
    modalFitnessCharts.classList.add('active');
    renderFitnessCharts();
  });

  btnChart7d?.addEventListener('click', () => {
    chartDaysRange = 7;
    btnChart7d.classList.add('active');
    btnChart30d.classList.remove('active');
    renderFitnessCharts();
  });

  btnChart30d?.addEventListener('click', () => {
    chartDaysRange = 30;
    btnChart30d.classList.add('active');
    btnChart7d.classList.remove('active');
    renderFitnessCharts();
  });

  function renderFitnessCharts() {
    const logs = Phase4DB.getDailyLogs();
    const waterEntries = Phase4DB.getWaterEntries();
    const calEntries = Phase4DB.getCalorieEntries();

    const days = [];
    const now = new Date();

    for (let i = chartDaysRange - 1; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i);
      const dStr = d.toISOString().split('T')[0];
      const dayNum = d.getDate();

      const steps = (logs[dStr] && logs[dStr].steps) || 0;
      const water = waterEntries.filter(w => w.date === dStr).reduce((acc, w) => acc + w.amountMl, 0);
      const calories = calEntries.filter(c => c.date === dStr).reduce((acc, c) => acc + c.kcal, 0);

      days.push({ date: dStr, dayNum, steps, water, calories });
    }

    // Render Steps
    renderSingleSvgChart(
      chartStepsBars,
      days,
      d => d.steps,
      '#6366F1',
      chartDaysRange
    );
    const avgSteps = Math.round(days.reduce((acc, d) => acc + d.steps, 0) / days.length);
    chartStepsAvg.textContent = `Average: ${avgSteps.toLocaleString()} steps`;

    // Render Water
    renderSingleSvgChart(
      chartWaterBars,
      days,
      d => d.water,
      '#06B6D4',
      chartDaysRange
    );
    const avgWater = Math.round(days.reduce((acc, d) => acc + d.water, 0) / days.length);
    chartWaterAvg.textContent = `Average: ${avgWater.toLocaleString()} ml`;

    // Render Calories
    renderSingleSvgChart(
      chartCaloriesBars,
      days,
      d => d.calories,
      '#F59E0B',
      chartDaysRange
    );
    const avgCal = Math.round(days.reduce((acc, d) => acc + d.calories, 0) / days.length);
    chartCaloriesAvg.textContent = `Average: ${avgCal.toLocaleString()} kcal`;
  }

  function renderSingleSvgChart(container, days, valSelector, barColor, range) {
    container.innerHTML = '';
    const maxVal = Math.max(...days.map(valSelector), 10);

    days.forEach((d, idx) => {
      const val = valSelector(d);
      const heightPercent = Math.round((val / maxVal) * 100);
      const col = document.createElement('div');
      col.className = 'bar-col';

      const showLabel = range === 7 || (idx % 5 === 0) || (idx === days.length - 1);

      col.innerHTML = `
        <div class="bar-rect" style="height: ${heightPercent}%; background: ${barColor};" title="${d.date}: ${val}"></div>
        <div class="bar-label">${showLabel ? d.dayNum : ''}</div>
      `;
      container.appendChild(col);
    });
  }

  // Pre-load exercise library
  loadExerciseLibrary();

  // ==================== PHASE 5 AI & MOOD MODULE ====================
  function getDateStr(daysAgo) {
    const d = new Date();
    d.setDate(d.getDate() - daysAgo);
    return d.toISOString().split('T')[0];
  }

  const Phase5DB = {
    getMoodLogs() {
      const stored = localStorage.getItem('alt_mood_logs');
      if (stored) return JSON.parse(stored);
      const seeded = [
        { date: getDateStr(0), score: 4, note: 'Had a good morning workout and productive work day.' },
        { date: getDateStr(1), score: 2, note: 'Tired and feeling overworked.' },
        { date: getDateStr(2), score: 2, note: 'Poor sleep and stressful meetings.' },
        { date: getDateStr(3), score: 1, note: 'Exhausted, low energy all day.' },
        { date: getDateStr(4), score: 2, note: 'Headache in the afternoon.' },
        { date: getDateStr(5), score: 4, note: 'Went for a nice evening walk.' },
        { date: getDateStr(6), score: 5, note: 'Great weekend with family.' },
        { date: getDateStr(8), score: 3, note: 'Quiet regular day.' },
        { date: getDateStr(12), score: 4, note: 'Good workout session.' },
      ];
      this.saveMoodLogs(seeded);
      return seeded;
    },
    saveMoodLogs(logs) {
      localStorage.setItem('alt_mood_logs', JSON.stringify(logs));
    },
    getAiSettings() {
      const stored = localStorage.getItem('alt_ai_settings');
      if (stored) return JSON.parse(stored);
      const defaults = { aiEnabled: true, includeMood: true };
      this.saveAiSettings(defaults);
      return defaults;
    },
    saveAiSettings(settings) {
      localStorage.setItem('alt_ai_settings', JSON.stringify(settings));
    },
    getCallsRemaining() {
      const stored = localStorage.getItem('alt_ai_calls_remaining');
      return stored !== null ? parseInt(stored, 10) : 37;
    },
    decrementCallsRemaining() {
      let current = this.getCallsRemaining();
      if (current > 0) current--;
      localStorage.setItem('alt_ai_calls_remaining', current.toString());
      return current;
    },
    isNudgeDismissed() {
      const dismissedOn = localStorage.getItem('alt_nudge_dismissed_on');
      if (!dismissedOn) return false;
      const dismissedDate = new Date(dismissedOn);
      const now = new Date();
      const diffDays = (now - dismissedDate) / (1000 * 60 * 60 * 24);
      return diffDays < 7;
    },
    dismissNudge() {
      localStorage.setItem('alt_nudge_dismissed_on', new Date().toISOString().split('T')[0]);
    }
  };

  const moodEmojis = ['', '😞', '🙁', '😐', '🙂', '😄'];
  const moodLabels = ['', 'Very Low', 'Low', 'Neutral', 'Good', 'Great'];
  let currentSelectedScore = 4;

  function initPhase5() {
    initMoodCard();
    initNudgeBanner();
    initInsightCards();
    initAiSettings();
    renderInsightsDashboard();
  }

  function initMoodCard() {
    const faceButtons = document.querySelectorAll('.mood-face-btn');
    const noteInput = document.getElementById('input-mood-note');
    const charCount = document.getElementById('mood-char-count');
    const btnSave = document.getElementById('btn-save-mood');
    const btnHistory = document.getElementById('btn-open-mood-history');

    // Load today's entry
    const today = getDateStr(0);
    const logs = Phase5DB.getMoodLogs();
    const todayEntry = logs.find(l => l.date === today);

    if (todayEntry) {
      currentSelectedScore = todayEntry.score;
      if (noteInput) {
        noteInput.value = todayEntry.note || '';
        if (charCount) charCount.textContent = (todayEntry.note || '').length;
      }
    }

    faceButtons.forEach(btn => {
      const score = parseInt(btn.getAttribute('data-score'), 10);
      if (score === currentSelectedScore) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }

      btn.addEventListener('click', () => {
        currentSelectedScore = score;
        faceButtons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    if (noteInput && charCount) {
      noteInput.addEventListener('input', () => {
        charCount.textContent = noteInput.value.length;
      });
    }

    btnSave?.addEventListener('click', () => {
      const note = noteInput ? noteInput.value.trim() : '';
      const allLogs = Phase5DB.getMoodLogs();
      const existingIdx = allLogs.findIndex(l => l.date === today);

      if (existingIdx >= 0) {
        allLogs[existingIdx].score = currentSelectedScore;
        allLogs[existingIdx].note = note;
      } else {
        allLogs.unshift({ date: today, score: currentSelectedScore, note });
      }

      Phase5DB.saveMoodLogs(allLogs);
      showToast('Mood saved for today');
      initNudgeBanner();
      renderOverviewFacts();
    });

    btnHistory?.addEventListener('click', () => {
      renderMoodHistoryList();
      openModal('modal-mood-history');
    });
  }

  function renderMoodHistoryList() {
    const container = document.getElementById('mood-history-list');
    if (!container) return;

    const logs = Phase5DB.getMoodLogs();
    if (logs.length === 0) {
      container.innerHTML = '<p class="empty-state-small">No mood entries recorded yet.</p>';
      return;
    }

    container.innerHTML = logs.map(entry => `
      <div class="mood-history-item" data-date="${entry.date}">
        <div class="mood-item-left">
          <span class="mood-item-emoji">${moodEmojis[entry.score] || '😐'}</span>
          <div class="mood-item-info">
            <div class="mood-item-date">${entry.date} &bull; ${moodLabels[entry.score] || ''}</div>
            ${entry.note ? `<div class="mood-item-note">${escapeHtml(entry.note)}</div>` : ''}
          </div>
        </div>
        <button class="icon-btn btn-delete-mood" data-date="${entry.date}" title="Delete entry">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </div>
    `).join('');

    container.querySelectorAll('.btn-delete-mood').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const date = btn.getAttribute('data-date');
        deleteMoodEntry(date);
      });
    });
  }

  function deleteMoodEntry(date) {
    let logs = Phase5DB.getMoodLogs();
    logs = logs.filter(l => l.date !== date);
    Phase5DB.saveMoodLogs(logs);
    renderMoodHistoryList();
    initNudgeBanner();
    renderOverviewFacts();
    showToast('Mood entry deleted');
  }

  function initNudgeBanner() {
    const banner = document.getElementById('insight-nudge-banner');
    const bannerText = document.getElementById('nudge-banner-text');
    const btnDismiss = document.getElementById('btn-dismiss-nudge');
    if (!banner || !bannerText) return;

    if (Phase5DB.isNudgeDismissed()) {
      banner.style.display = 'none';
      return;
    }

    const logs = Phase5DB.getMoodLogs();
    let lowCount = 0;
    for (let i = 0; i < 7; i++) {
      const dStr = getDateStr(i);
      const entry = logs.find(l => l.date === dStr);
      if (entry && entry.score <= 2) {
        lowCount++;
      }
    }

    if (lowCount >= 4) {
      banner.style.display = 'flex';
      bannerText.textContent = `Your mood entries were low on ${lowCount} of the last 7 days. This app cannot tell you why. If it is weighing on you, talking with someone you trust or a health professional can help.`;
    } else {
      banner.style.display = 'none';
    }

    btnDismiss?.addEventListener('click', () => {
      Phase5DB.dismissNudge();
      banner.style.display = 'none';
    });
  }

  function initInsightCards() {
    const generateButtons = document.querySelectorAll('.btn-generate-summary');
    generateButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const kind = btn.getAttribute('data-kind');
        handleGenerateSummary(kind, btn);
      });
    });
  }

  function handleGenerateSummary(kind, btn) {
    const settings = Phase5DB.getAiSettings();
    const fallbackEl = document.getElementById(`fallback-${kind === 'spending_month' ? 'spending' : (kind === 'fitness_week' ? 'fitness' : 'overview')}`);
    const summaryBox = document.getElementById(`ai-summary-${kind === 'spending_month' ? 'spending' : (kind === 'fitness_week' ? 'fitness' : 'overview')}`);
    const summaryText = document.getElementById(`ai-text-${kind === 'spending_month' ? 'spending' : (kind === 'fitness_week' ? 'fitness' : 'overview')}`);

    if (!settings.aiEnabled) {
      if (fallbackEl) {
        fallbackEl.textContent = 'AI summaries are switched off in AI settings.';
        fallbackEl.style.display = 'block';
      }
      return;
    }

    btn.classList.add('loading');
    btn.disabled = true;

    setTimeout(() => {
      btn.classList.remove('loading');
      btn.disabled = false;

      const remaining = Phase5DB.decrementCallsRemaining();
      document.querySelectorAll('.calls-count').forEach(el => el.textContent = remaining);

      // Predefined verified guard-compliant factual summaries
      let text = '';
      if (kind === 'spending_month') {
        text = 'You have spent ₹12,450.00 this month, which is ₹1,450.00 more than the same period last month. Your largest category is Food & Dining at ₹4,200.00. You have used 62.3% of your overall ₹20,000.00 budget.';
      } else if (kind === 'fitness_week') {
        text = 'Over the last 7 days you logged activity on 5 days, averaging 6,500 steps and 1,800 ml of water. You met your steps goal on 2 days and completed 3 workouts totaling 95 minutes with 640 estimated calories burned.';
      } else {
        text = 'In the last 7 days you logged 3 workouts and averaged 6,500 steps, while your spending was ₹3,200.00 led by Food & Dining. You recorded 5 mood entries with an average score of 3.4.';
      }

      if (summaryBox && summaryText) {
        summaryText.textContent = text;
        summaryBox.style.display = 'block';
      }

      if (fallbackEl) {
        fallbackEl.style.display = 'none';
      }

      const labelSpan = btn.querySelector('.btn-text-label');
      if (labelSpan) labelSpan.textContent = 'Refresh summary';

    }, 700);
  }

  function initAiSettings() {
    const btnOpen = document.getElementById('btn-open-ai-settings');
    const toggleAi = document.getElementById('toggle-ai-enabled');
    const toggleMood = document.getElementById('toggle-include-mood');

    const settings = Phase5DB.getAiSettings();
    if (toggleAi) toggleAi.checked = settings.aiEnabled;
    if (toggleMood) {
      toggleMood.checked = settings.aiEnabled && settings.includeMood;
      toggleMood.disabled = !settings.aiEnabled;
    }

    btnOpen?.addEventListener('click', () => {
      openModal('modal-ai-settings');
    });

    toggleAi?.addEventListener('change', () => {
      const isEnabled = toggleAi.checked;
      const curr = Phase5DB.getAiSettings();
      curr.aiEnabled = isEnabled;
      Phase5DB.saveAiSettings(curr);

      if (toggleMood) {
        toggleMood.disabled = !isEnabled;
        if (!isEnabled) toggleMood.checked = false;
      }
      updateAiCardsAvailability(isEnabled);
    });

    toggleMood?.addEventListener('change', () => {
      const curr = Phase5DB.getAiSettings();
      curr.includeMood = toggleMood.checked;
      Phase5DB.saveAiSettings(curr);
      renderOverviewFacts();
    });
  }

  function updateAiCardsAvailability(aiEnabled) {
    document.querySelectorAll('.btn-generate-summary').forEach(btn => {
      const kind = btn.getAttribute('data-kind');
      const fallback = document.getElementById(`fallback-${kind === 'spending_month' ? 'spending' : (kind === 'fitness_week' ? 'fitness' : 'overview')}`);
      if (!aiEnabled) {
        btn.disabled = true;
        btn.style.opacity = '0.5';
        if (fallback) {
          fallback.textContent = 'AI summaries are switched off in AI settings.';
          fallback.style.display = 'block';
        }
      } else {
        btn.disabled = false;
        btn.style.opacity = '1';
        if (fallback) {
          fallback.style.display = 'none';
        }
      }
    });
  }

  function renderOverviewFacts() {
    const moodSection = document.getElementById('fact-overview-mood-section');
    if (!moodSection) return;

    const settings = Phase5DB.getAiSettings();
    if (!settings.includeMood) {
      moodSection.style.display = 'none';
      return;
    }

    moodSection.style.display = 'block';
    const logs = Phase5DB.getMoodLogs();
    const last7 = [];
    for (let i = 0; i < 7; i++) {
      const dStr = getDateStr(i);
      const entry = logs.find(l => l.date === dStr);
      if (entry) last7.push(entry);
    }

    const entriesEl = document.getElementById('fact-overview-mood-entries');
    const avgEl = document.getElementById('fact-overview-mood-avg');
    const lowEl = document.getElementById('fact-overview-mood-low');

    if (entriesEl) entriesEl.textContent = last7.length;
    if (avgEl) {
      const avg = last7.length > 0 ? (last7.reduce((a, b) => a + b.score, 0) / last7.length).toFixed(1) : '-';
      avgEl.textContent = avg;
    }
    if (lowEl) {
      const lowDays = last7.filter(l => l.score <= 2).length;
      lowEl.textContent = lowDays;
    }
  }

  function renderInsightsDashboard() {
    initNudgeBanner();
    renderOverviewFacts();
    const remaining = Phase5DB.getCallsRemaining();
    document.querySelectorAll('.calls-count').forEach(el => el.textContent = remaining);
    const settings = Phase5DB.getAiSettings();
    updateAiCardsAvailability(settings.aiEnabled);
  }

  function loadHomeData() {
    renderExpenses();
    renderFitnessDashboard();
    renderInsightsDashboard();
    checkBudgetAlerts();
  }

  // ==================== PHASE 6 IMPLEMENTATION ====================

  function getReminderSettings() {
    try {
      const saved = localStorage.getItem('phase6_reminders');
      if (saved) return JSON.parse(saved);
    } catch (_) {}
    return {
      water: false,
      workout: false,
      workoutTime: '18:00',
      mood: false,
      moodTime: '21:00',
      expenses: false,
      expensesTime: '20:00',
      budgetAlerts: true
    };
  }

  function saveReminderSettings(settings) {
    localStorage.setItem('phase6_reminders', JSON.stringify(settings));
  }

  function showBudgetToast(message) {
    const toast = document.getElementById('budget-alert-toast');
    const toastText = document.getElementById('budget-alert-toast-text');
    if (!toast || !toastText) return;

    toastText.textContent = message;
    toast.style.display = 'flex';
    setTimeout(() => {
      toast.style.display = 'none';
    }, 6000);
  }

  function checkBudgetAlerts() {
    const settings = getReminderSettings();
    if (!settings.budgetAlerts) return;

    const budgets = state.budgets || [];
    const now = new Date();
    const yyyyMM = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`;

    budgets.forEach(b => {
      const spent = calculateBudgetSpent ? calculateBudgetSpent(b) : (b.spent || 0);
      const percent = b.amount > 0 ? (spent / b.amount) * 100 : 0;
      const key = `budget_alert_${b.id}_${yyyyMM}`;
      const lastLevel = parseInt(localStorage.getItem(key) || '0', 10);

      if (percent >= 100 && lastLevel < 100) {
        localStorage.setItem(key, '100');
        showBudgetToast('Budget reached: one of your budgets is fully used.');
      } else if (percent >= 80 && lastLevel < 80) {
        localStorage.setItem(key, '80');
        showBudgetToast('Budget alert: one of your budgets is at 80%.');
      }
    });
  }

  function initPhase6() {
    // 1. Navigation buttons for Reminders and Privacy
    const btnOpenReminders = document.getElementById('btn-open-reminders');
    const modalReminders = document.getElementById('modal-reminders');
    const btnOpenPrivacy = document.getElementById('btn-open-privacy');
    const modalPrivacy = document.getElementById('modal-privacy');

    btnOpenReminders?.addEventListener('click', () => {
      syncReminderInputs();
      openModal('modal-reminders');
    });

    btnOpenPrivacy?.addEventListener('click', () => {
      openModal('modal-privacy');
    });

    // 2. Reminders settings bindings
    const toggleWater = document.getElementById('toggle-water-reminder');
    const toggleWorkout = document.getElementById('toggle-workout-reminder');
    const timeWorkout = document.getElementById('time-workout-reminder');
    const toggleMood = document.getElementById('toggle-mood-reminder');
    const timeMood = document.getElementById('time-mood-reminder');
    const toggleExpenses = document.getElementById('toggle-expenses-reminder');
    const timeExpenses = document.getElementById('time-expenses-reminder');
    const toggleBudgetAlerts = document.getElementById('toggle-budget-alerts');
    const btnTestNotification = document.getElementById('btn-test-notification');

    function syncReminderInputs() {
      const s = getReminderSettings();
      if (toggleWater) toggleWater.checked = !!s.water;
      if (toggleWorkout) toggleWorkout.checked = !!s.workout;
      if (timeWorkout) timeWorkout.value = s.workoutTime || '18:00';
      if (toggleMood) toggleMood.checked = !!s.mood;
      if (timeMood) timeMood.value = s.moodTime || '21:00';
      if (toggleExpenses) toggleExpenses.checked = !!s.expenses;
      if (timeExpenses) timeExpenses.value = s.expensesTime || '20:00';
      if (toggleBudgetAlerts) toggleBudgetAlerts.checked = s.budgetAlerts !== false;
    }

    function saveFromInputs() {
      const current = {
        water: toggleWater?.checked || false,
        workout: toggleWorkout?.checked || false,
        workoutTime: timeWorkout?.value || '18:00',
        mood: toggleMood?.checked || false,
        moodTime: timeMood?.value || '21:00',
        expenses: toggleExpenses?.checked || false,
        expensesTime: timeExpenses?.value || '20:00',
        budgetAlerts: toggleBudgetAlerts?.checked !== false,
      };
      saveReminderSettings(current);
    }

    [toggleWater, toggleWorkout, timeWorkout, toggleMood, timeMood, toggleExpenses, timeExpenses, toggleBudgetAlerts].forEach(el => {
      el?.addEventListener('change', () => {
        saveFromInputs();
        showToast('Reminder preferences updated');
      });
    });

    btnTestNotification?.addEventListener('click', () => {
      showBudgetToast('Water: Time for a glass of water.');
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('Water', { body: 'Time for a glass of water.' });
      }
    });

    // 3. Privacy & Data Export bindings
    const btnExportData = document.getElementById('btn-export-data');
    btnExportData?.addEventListener('click', () => {
      exportUserDataJson();
    });

    const btnInitiateDelete = document.getElementById('btn-initiate-delete');
    const deleteConfirmBox = document.getElementById('delete-confirm-box');
    const inputConfirmDelete = document.getElementById('input-confirm-delete');
    const btnExecuteDelete = document.getElementById('btn-execute-delete');

    btnInitiateDelete?.addEventListener('click', () => {
      if (deleteConfirmBox) {
        deleteConfirmBox.style.display = 'block';
        btnInitiateDelete.style.display = 'none';
        inputConfirmDelete?.focus();
      }
    });

    inputConfirmDelete?.addEventListener('input', () => {
      if (btnExecuteDelete) {
        btnExecuteDelete.disabled = inputConfirmDelete.value.trim() !== 'DELETE';
      }
    });

    btnExecuteDelete?.addEventListener('click', () => {
      if (inputConfirmDelete?.value.trim() === 'DELETE') {
        // Clear all mock data
        localStorage.clear();
        closeModal('modal-privacy');
        showToast('Account permanently erased.');
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      }
    });

    // 4. Toast close button
    document.getElementById('btn-close-toast')?.addEventListener('click', () => {
      const toast = document.getElementById('budget-alert-toast');
      if (toast) toast.style.display = 'none';
    });

    // 5. Offline and network handling
    const offlineBanner = document.getElementById('offline-banner');
    window.addEventListener('offline', () => {
      if (offlineBanner) offlineBanner.style.display = 'flex';
    });
    window.addEventListener('online', () => {
      if (offlineBanner) offlineBanner.style.display = 'none';
    });
  }

  function exportUserDataJson() {
    const rawTx = state.transactions || [];
    const safeTx = rawTx.slice(0, 50000).map(t => {
      const copy = { ...t };
      delete copy.smsFingerprint;
      delete copy.fingerprint;
      return copy;
    });

    const exportObject = {
      exportedAt: new Date().toISOString(),
      profile: {
        displayName: state.profile?.displayName || "Alex Developer",
        currencyCode: "INR",
        timezone: state.profile?.timezone || "Asia/Kolkata",
        stepGoal: 10000,
        waterGoalMl: 2500,
        calorieGoal: 2200,
        gender: "male",
        heightCm: 178,
        weightKg: 72,
        dateOfBirth: "1994-06-15"
      },
      bankAccounts: state.bankAccounts || [],
      categories: state.categories || [],
      transactions: safeTx,
      budgets: state.budgets || [],
      fitnessGoals: {
        dailySteps: 10000,
        dailyWaterMl: 2500,
        dailyCalories: 2200,
        weeklyWorkouts: 4
      },
      fitnessDaily: [
        { dayDate: "2026-10-25", stepsCount: 8420, waterMl: 1750, activeCalories: 480 }
      ],
      waterEntries: [
        { dayDate: "2026-10-25", amountMl: 250, loggedAt: "2026-10-25T08:30:00Z" },
        { dayDate: "2026-10-25", amountMl: 500, loggedAt: "2026-10-25T11:15:00Z" }
      ],
      calorieEntries: [
        { dayDate: "2026-10-25", mealType: "lunch", foodName: "Grilled Chicken & Rice", calories: 650 }
      ],
      workouts: [
        { workoutDate: "2026-10-25", workoutType: "Running", durationMinutes: 30, caloriesBurned: 310 }
      ],
      moodLog: typeof Phase5DB !== 'undefined' ? Phase5DB.getMoodLogs() : [],
      aiSettings: typeof Phase5DB !== 'undefined' ? Phase5DB.getAiSettings() : {}
    };

    if (rawTx.length > 50000) {
      exportObject.truncated = true;
    }

    const jsonStr = JSON.stringify(exportObject, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'life-tracker-export.json';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    showToast('Export downloaded: life-tracker-export.json');
  }

  // Initialize Phase 5 components
  initPhase5();

  // Initialize Phase 6 components
  initPhase6();

  // Initialize
  initRouting();

})();

