/* ── ClassBridge AI — App JavaScript ────────────────────────────────────── */
// Configurable API base URL — change this if backend runs on a different host/port
const API = (window.CLASSBRIDGE_API_URL || 'http://localhost:8080') + '/api';
let token = localStorage.getItem('token');
let currentUser = JSON.parse(localStorage.getItem('user') || 'null');

/* ── Auth Helpers ───────────────────────────────────────────────────────── */
function setAuth(t, user) {
  token = t; currentUser = user;
  localStorage.setItem('token', t);
  localStorage.setItem('user', JSON.stringify(user));
}
function clearAuth() {
  token = null; currentUser = null;
  localStorage.removeItem('token');
  localStorage.removeItem('user');
}
function isLoggedIn() { return !!token; }
function authHeaders() { return { 'Authorization': 'Bearer ' + token }; }

async function apiFetch(path, opts = {}) {
  const headers = { ...authHeaders(), ...opts.headers };
  if (!headers['Content-Type'] && !(opts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  let res;
  try {
    res = await fetch(API + path, { ...opts, headers });
  } catch (networkErr) {
    // Backend is down or unreachable
    throw new Error('Cannot connect to server. Is the backend running on port 8080?');
  }

  // Only redirect to login on 401 if user was already authenticated (not during login/signup itself)
  const isAuthRoute = path.startsWith('/auth/');
  if (res.status === 401 && !isAuthRoute) {
    clearAuth();
    window.location.href = 'index.html';
    throw new Error('Session expired. Please log in again.');
  }

  if (!res.ok) {
    // Try JSON first, then plain text, then fallback
    let errMsg = 'Request failed';
    try {
      const errBody = await res.json();
      errMsg = errBody.error || errBody.message || JSON.stringify(errBody);
    } catch (_) {
      try {
        const text = await res.text();
        if (text) errMsg = text;
      } catch (_2) { /* ignore */ }
    }
    throw new Error(errMsg);
  }

  if (res.status === 204) return null;
  return res.json();
}

/* ── Toast Notifications ────────────────────────────────────────────────── */
function toast(msg, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = msg;
  container.appendChild(el);
  setTimeout(() => { el.style.animation = 'toastOut 0.2s ease-in forwards'; setTimeout(() => el.remove(), 200); }, 3500);
}

/* ── Auth Page ──────────────────────────────────────────────────────────── */
function initAuthPage() {
  if (isLoggedIn()) { window.location.href = 'dashboard.html'; return; }

  document.querySelectorAll('.auth-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById(tab.dataset.form).classList.add('active');
      hideAlerts();
    });
  });

  document.getElementById('signup-form').addEventListener('submit', async (e) => {
    e.preventDefault(); hideAlerts();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = 'Creating account...';
    try {
      const data = await apiFetch('/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          name: document.getElementById('signup-name').value,
          email: document.getElementById('signup-email').value,
          password: document.getElementById('signup-password').value
        })
      });
      setAuth(data.token, { email: data.email, name: data.name });
      toast('Welcome to ClassBridge AI!', 'success');
      setTimeout(() => window.location.href = 'dashboard.html', 500);
    } catch (err) { showAlert('signup-error', err.message); }
    finally { btn.disabled = false; btn.textContent = 'Create Account'; }
  });

  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault(); hideAlerts();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true; btn.textContent = 'Logging in...';
    try {
      const data = await apiFetch('/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: document.getElementById('login-email').value,
          password: document.getElementById('login-password').value
        })
      });
      setAuth(data.token, { email: data.email, name: data.name });
      toast('Welcome back!', 'success');
      setTimeout(() => window.location.href = 'dashboard.html', 500);
    } catch (err) { showAlert('login-error', err.message); }
    finally { btn.disabled = false; btn.textContent = 'Log In'; }
  });
}

function showAlert(id, msg) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg; el.classList.add('show');
}
function hideAlerts() { document.querySelectorAll('.alert').forEach(a => a.classList.remove('show')); }

/* ── Dashboard & Views ────────────────────────────────────────────────── */
let currentView = 'home';

function showView(viewName, filterVal = null) {
  currentView = viewName;
  document.querySelectorAll('.nav-item').forEach(item => {
    item.classList.toggle('active', item.dataset.view === viewName);
  });

  const titles = {
    'home': 'Home Dashboard',
    'my-classes': 'My Classes',
    'subjects': 'Course Subjects',
    'progress': 'Learning & Recovery Progress',
    'settings': 'Account & Settings'
  };

  const titleEl = document.getElementById('page-title');
  if (titleEl) titleEl.textContent = titles[viewName] || 'Dashboard';

  document.querySelectorAll('.view-section').forEach(sec => {
    sec.style.display = sec.id === 'view-' + viewName ? 'block' : 'none';
  });

  // Close sidebar on mobile after selection
  const sidebar = document.getElementById('app-sidebar');
  if (sidebar) sidebar.classList.remove('open');

  if (viewName === 'my-classes' && filterVal) {
    document.querySelectorAll('.filter-chip').forEach(c => {
      c.classList.toggle('active', c.dataset.filter === filterVal);
    });
    activeStatusFilter = filterVal;
    runClassSearch();
  } else if (viewName === 'progress') {
    renderProgressView();
  }
}

window.showView = showView;

async function initDashboard() {
  if (!isLoggedIn()) { window.location.href = 'index.html'; return; }

  const name = currentUser?.name || currentUser?.email || 'Student';
  const email = currentUser?.email || 'Logged in';
  
  if (document.getElementById('user-name')) document.getElementById('user-name').textContent = name;
  if (document.getElementById('user-email')) document.getElementById('user-email').textContent = email;
  if (document.getElementById('user-avatar')) document.getElementById('user-avatar').textContent = name[0].toUpperCase();
  if (document.getElementById('settings-user-name')) document.getElementById('settings-user-name').textContent = name;
  if (document.getElementById('settings-user-email')) document.getElementById('settings-user-email').textContent = email;
  if (document.getElementById('settings-api-url')) document.getElementById('settings-api-url').textContent = API;

  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) logoutBtn.addEventListener('click', () => { clearAuth(); window.location.href = 'index.html'; });

  // Sidebar navigation
  document.querySelectorAll('.sidebar-nav .nav-item').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const targetView = link.dataset.view;
      if (targetView) showView(targetView);
    });
  });

  // Modal backdrop close
  document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) closeModal(backdrop.id);
    });
  });

  // Search input + clear button
  let searchTimeout;
  const searchInput = document.getElementById('class-search');
  const searchClearBtn = document.getElementById('search-clear-btn');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      // Show/hide × button
      if (searchClearBtn) {
        searchClearBtn.style.display = searchInput.value ? 'flex' : 'none';
      }
      clearTimeout(searchTimeout);
      searchTimeout = setTimeout(() => runClassSearch(), 250);
    });
  }
  if (searchClearBtn) {
    searchClearBtn.addEventListener('click', () => {
      if (searchInput) searchInput.value = '';
      searchClearBtn.style.display = 'none';
      runClassSearch();
      searchInput?.focus();
    });
  }

  // Filter chips
  document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      activeStatusFilter = chip.dataset.filter;
      runClassSearch();
    });
  });

  // Clear filter
  const clearBtn = document.getElementById('btn-clear-filter');
  if (clearBtn) clearBtn.addEventListener('click', clearAllFilters);

  // Load initial data
  await Promise.all([loadSubjects(), loadClasses()]);

  // Handle URL hash navigation if present
  const hash = window.location.hash.replace('#', '');
  if (hash && ['home', 'my-classes', 'subjects', 'progress', 'settings'].includes(hash)) {
    showView(hash);
  }
}

let activeSubjectFilter = null;
let activeStatusFilter = 'all';
let allClasses = [];

async function loadSubjects() {
  try {
    const subjects = await apiFetch('/subjects');
    const statSub = document.getElementById('stat-subjects');
    if (statSub) statSub.textContent = subjects.length;
    const grid = document.getElementById('subject-grid');
    if (!grid) return;

    if (!subjects.length) {
      grid.innerHTML = '<div class="empty-state" style="grid-column:1/-1"><div class="empty-icon">📚</div><h3>No subjects yet</h3><p>Create your first subject to start organizing missed lectures and tracking gaps.</p><button class="btn btn-primary" id="empty-create-subject">+ Create Subject</button></div>';
      document.getElementById('empty-create-subject')?.addEventListener('click', showCreateSubject);
      return;
    }

    grid.innerHTML = subjects.map(s => `
      <div class="subject-card" data-id="${esc(s.id)}">
        <h3>${esc(s.name)}</h3>
        <p>${esc(s.description || 'General course lectures and recovery materials.')}</p>
        <div class="subject-meta">
          <span>📄 ${s.classCount} lecture${s.classCount !== 1 ? 's' : ''}</span>
          <span>📅 ${s.createdAt ? new Date(s.createdAt).toLocaleDateString() : 'Active'}</span>
        </div>
      </div>
    `).join('');

    grid.querySelectorAll('.subject-card').forEach(card => {
      card.addEventListener('click', () => {
        const subjectId = card.dataset.id;
        const subjectName = card.querySelector('h3').textContent;
        filterClassesBySubject(subjectId, subjectName);
        showView('my-classes');
      });
    });
  } catch (err) { console.error('Failed to load subjects', err); }
}

async function filterClassesBySubject(subjectId, subjectName) {
  activeSubjectFilter = subjectId;
  const clearBtn = document.getElementById('btn-clear-filter');
  if (clearBtn) clearBtn.style.display = 'flex';

  document.querySelectorAll('.subject-card').forEach(card => {
    card.style.borderColor = card.dataset.id === subjectId ? 'var(--primary)' : '';
  });

  await runClassSearch();
}

function clearAllFilters() {
  activeSubjectFilter = null;
  activeStatusFilter = 'all';
  const clearBtn = document.getElementById('btn-clear-filter');
  if (clearBtn) clearBtn.style.display = 'none';
  const searchEl = document.getElementById('class-search');
  if (searchEl) searchEl.value = '';
  document.querySelectorAll('.subject-card').forEach(c => c.style.borderColor = '');
  document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
  document.querySelector('.filter-chip[data-filter="all"]')?.classList.add('active');
  runClassSearch();
}

async function runClassSearch() {
  const searchEl = document.getElementById('class-search');
  const query = searchEl ? searchEl.value.trim() : '';

  try {
    let classes;
    if (activeSubjectFilter) {
      classes = await apiFetch(`/classes/subject/${activeSubjectFilter}`);
      if (query) {
        const q = query.toLowerCase();
        classes = classes.filter(c =>
          (c.className || '').toLowerCase().includes(q) ||
          (c.subject || '').toLowerCase().includes(q) ||
          (c.description || '').toLowerCase().includes(q)
        );
      }
    } else if (query) {
      classes = await apiFetch(`/classes/search?q=${encodeURIComponent(query)}`);
    } else {
      classes = allClasses;
    }

    // Apply status filter
    if (activeStatusFilter !== 'all') {
      if (activeStatusFilter === 'ANALYZED') {
        classes = classes.filter(c => c.status === 'ANALYZED' || c.status === 'DONE');
      } else if (activeStatusFilter === 'PROCESSING') {
        classes = classes.filter(c => ['PROCESSING', 'AUDIO_READY', 'TRANSCRIBED'].includes(c.status));
      } else if (activeStatusFilter === 'PENDING') {
        classes = classes.filter(c => ['PENDING', 'UPLOADED'].includes(c.status));
      } else {
        classes = classes.filter(c => c.status === activeStatusFilter);
      }
    }

    renderClassList(classes, 'class-list');
  } catch (err) {
    toast('Search failed: ' + err.message, 'error');
  }
}

async function loadClasses() {
  try {
    allClasses = await apiFetch('/classes');
    
    // Update overview numbers
    const totalCount = allClasses.length;
    const readyClasses = allClasses.filter(c => c.status === 'ANALYZED' || c.status === 'DONE');
    const processingClasses = allClasses.filter(c => ['PROCESSING', 'AUDIO_READY', 'TRANSCRIBED'].includes(c.status));
    const missedClasses = allClasses.filter(c => ['PENDING', 'UPLOADED'].includes(c.status));

    if (document.getElementById('stat-classes')) document.getElementById('stat-classes').textContent = totalCount;
    if (document.getElementById('stat-analyzed')) document.getElementById('stat-analyzed').textContent = readyClasses.length;
    if (document.getElementById('stat-processing')) document.getElementById('stat-processing').textContent = processingClasses.length;

    // Render continue catch-up card
    renderContinueCard(readyClasses, processingClasses, missedClasses);

    // Render Home Sections
    renderCategorizedHome(readyClasses, processingClasses, missedClasses);

    // Render All Classes list
    renderClassList(allClasses, 'class-list');
  } catch (err) { console.error('Failed to load classes', err); }
}

function renderContinueCard(ready, processing, missed) {
  const container = document.getElementById('continue-catchup-container');
  if (!container) return;

  // Pick the most relevant class: latest analyzed class first, or processing
  const target = ready[0] || processing[0] || missed[0];
  if (!target) {
    container.innerHTML = '';
    return;
  }

  const isReady = target.status === 'ANALYZED' || target.status === 'DONE';
  const actionLabel = isReady ? 'Continue Catch-up →' : 'View Processing Status →';
  const statusBadge = isReady
    ? '<span class="status-badge status-ANALYZED">Ready to Learn</span>'
    : `<span class="status-badge status-${target.status}">${formatStatus(target.status)}</span>`;

  container.innerHTML = `
    <div class="continue-card" onclick="window.location.href='class.html?id=${esc(target.id)}'">
      <div class="continue-info">
        <div class="continue-icon">${isReady ? '🎯' : '⏳'}</div>
        <div class="continue-text">
          <div class="continue-label">${isReady ? 'Recommended Next Action' : 'Recent Upload'}</div>
          <div class="continue-title">${esc(target.className)}</div>
          <div class="continue-sub">${esc(target.subject || 'General Subject')} ${target.durationSeconds ? '· ' + formatDuration(target.durationSeconds) : ''}</div>
        </div>
      </div>
      <div class="continue-action" style="display:flex;align-items:center;gap:12px">
        ${statusBadge}
        <button class="btn btn-primary btn-sm">${actionLabel}</button>
      </div>
    </div>
  `;
}

function renderCategorizedHome(ready, processing, missed) {
  // Ready Section
  const readyGrid = document.getElementById('home-ready-grid');
  const countReady = document.getElementById('count-ready');
  if (countReady) countReady.textContent = ready.length;
  if (readyGrid) {
    if (ready.length) {
      readyGrid.innerHTML = ready.slice(0, 6).map(c => renderClassCardHtml(c)).join('');
      bindClassCardClicks(readyGrid);
    } else {
      readyGrid.innerHTML = '<div class="empty-state" style="grid-column:1/-1;padding:24px"><p style="color:var(--text-muted);font-size:14px">No analyzed classes yet. Upload your first lecture recording above to generate a catch-up plan.</p></div>';
    }
  }

  // Processing Section
  const secProcessing = document.getElementById('section-processing');
  const processingGrid = document.getElementById('home-processing-grid');
  const countProcessing = document.getElementById('count-processing');
  if (secProcessing) {
    if (processing.length) {
      secProcessing.style.display = 'block';
      if (countProcessing) countProcessing.textContent = processing.length;
      if (processingGrid) {
        processingGrid.innerHTML = processing.map(c => renderClassCardHtml(c)).join('');
        bindClassCardClicks(processingGrid);
      }
    } else {
      secProcessing.style.display = 'none';
    }
  }

  // Missed Section
  const secMissed = document.getElementById('section-missed');
  const missedGrid = document.getElementById('home-missed-grid');
  const countMissed = document.getElementById('count-missed');
  if (secMissed) {
    if (missed.length) {
      secMissed.style.display = 'block';
      if (countMissed) countMissed.textContent = missed.length;
      if (missedGrid) {
        missedGrid.innerHTML = missed.map(c => renderClassCardHtml(c)).join('');
        bindClassCardClicks(missedGrid);
      }
    } else {
      secMissed.style.display = 'none';
    }
  }
}

function renderClassCardHtml(c) {
  const isReady = c.status === 'ANALYZED' || c.status === 'DONE';
  const isFailed = c.status === 'FAILED';
  const isProcessing = ['PROCESSING', 'AUDIO_READY', 'TRANSCRIBED'].includes(c.status);
  const statusClass = isReady ? 'ready' : (isFailed ? 'failed' : (isProcessing ? 'processing' : 'pending'));
  const statusText = isReady ? '⚡ Recovery Ready'
    : (isFailed ? '❌ Failed'
    : (isProcessing ? '⚙️ Processing...' : '⏳ Pending'));

  const topicsPreview = c.topics?.length
    ? `<div style="display:flex;gap:4px;flex-wrap:wrap;margin-top:6px">${c.topics.slice(0, 3).map(t => `<span class="prev-topic-tag">${esc(t)}</span>`).join('')}</div>`
    : '';

  const retryBtn = isFailed
    ? `<button class="btn btn-ghost btn-sm card-retry-btn" data-id="${esc(c.id)}" title="Retry processing" style="color:var(--primary);padding:2px 8px;font-size:11px">
        🔄 Retry
       </button>`
    : '';

  return `
    <div class="class-card-enhanced ${isFailed ? 'card-failed' : ''}" data-id="${esc(c.id)}">
      <div class="card-top">
        <div class="card-title-group">
          <div class="card-subject">${esc(c.subject || 'General Course')}</div>
          <h4>${esc(c.className)}</h4>
          ${topicsPreview}
          ${isFailed && c.failureReason ? `<div style="font-size:11px;color:var(--error);margin-top:4px;line-height:1.4">⚠️ ${esc(c.failureReason.length > 80 ? c.failureReason.substring(0,80)+'...' : c.failureReason)}</div>` : ''}
        </div>
        <span class="status-badge status-${c.status}">${formatStatus(c.status)}</span>
      </div>
      <div class="card-bottom">
        <span class="recovery-indicator ${statusClass}">${statusText}</span>
        <div style="display:flex;align-items:center;gap:6px">
          ${retryBtn}
          <span>${c.durationSeconds ? formatDuration(c.durationSeconds) : (c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '')}</span>
        </div>
      </div>
    </div>
  `;
}

function bindClassCardClicks(container) {
  container.querySelectorAll('.class-card-enhanced').forEach(card => {
    card.addEventListener('click', () => {
      window.location.href = 'class.html?id=' + card.dataset.id;
    });
  });
}

function renderClassList(classes, containerId = 'class-list') {
  const list = document.getElementById(containerId);
  if (!list) return;

  if (!classes.length) {
    const searchInput = document.getElementById('class-search');
    const hasQuery = searchInput && searchInput.value.trim().length > 0;
    const hasFilter = activeStatusFilter && activeStatusFilter !== 'all';
    const hasSubject = !!activeSubjectFilter;

    if (hasQuery || hasFilter || hasSubject) {
      // No results for active search/filter
      const queryText = hasQuery ? `"${esc(searchInput.value.trim())}"` : '';
      list.innerHTML = `
        <div class="empty-state" style="grid-column:1/-1">
          <div class="empty-icon">🔍</div>
          <h3>No results found${hasQuery ? ' for ' + queryText : ''}</h3>
          <p>Try a different search term, or clear your filters.</p>
          <div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap">
            ${hasQuery ? '<button class="btn btn-secondary" onclick="document.getElementById(\'class-search\').value=\'\';document.getElementById(\'search-clear-btn\').style.display=\'none\';runClassSearch()">Clear Search</button>' : ''}
            <button class="btn btn-primary" onclick="clearAllFilters()">Clear All Filters</button>
          </div>
        </div>
      `;
    } else {
      // Genuinely no classes uploaded yet
      list.innerHTML = `
        <div class="empty-state" style="grid-column:1/-1">
          <div class="empty-icon">🎬</div>
          <h3>No classes yet</h3>
          <p>Upload your first class recording to start your AI-powered catch-up journey.</p>
          <button class="btn btn-primary" onclick="showUploadModal()">Upload Recording</button>
        </div>
      `;
    }
    return;
  }

  list.innerHTML = classes.map(c => renderClassCardHtml(c)).join('');
  bindClassCardClicks(list);
}

function renderProgressView() {
  const readyCount = allClasses.filter(c => c.status === 'ANALYZED' || c.status === 'DONE').length;
  if (document.getElementById('prog-stat-classes')) document.getElementById('prog-stat-classes').textContent = readyCount;
  
  // Estimate concepts and quiz from all ready classes
  let totalConcepts = 0;
  allClasses.forEach(c => {
    if (c.mustLearn) totalConcepts += c.mustLearn.length;
    else if (c.topics) totalConcepts += c.topics.length;
  });

  if (document.getElementById('prog-stat-concepts')) document.getElementById('prog-stat-concepts').textContent = totalConcepts || readyCount * 3;
  if (document.getElementById('prog-stat-quizzes')) document.getElementById('prog-stat-quizzes').textContent = readyCount;
  if (document.getElementById('prog-stat-avg-score')) document.getElementById('prog-stat-avg-score').textContent = readyCount > 0 ? '85%' : '0%';

  const container = document.getElementById('progress-subjects-overview');
  if (container) {
    if (!allClasses.length) {
      container.innerHTML = '<p style="font-size:14px;color:var(--text-muted)">No classes analyzed yet.</p>';
      return;
    }
    // Group by subject
    const subjectMap = {};
    allClasses.forEach(c => {
      const s = c.subject || 'General';
      if (!subjectMap[s]) subjectMap[s] = { total: 0, ready: 0 };
      subjectMap[s].total++;
      if (c.status === 'ANALYZED' || c.status === 'DONE') subjectMap[s].ready++;
    });

    container.innerHTML = Object.entries(subjectMap).map(([subject, counts]) => {
      const pct = Math.round((counts.ready / counts.total) * 100);
      return `
        <div style="margin-bottom:16px">
          <div style="display:flex;justify-content:space-between;margin-bottom:6px;font-size:14px;font-weight:600">
            <span>${esc(subject)}</span>
            <span style="color:var(--primary)">${counts.ready}/${counts.total} lectures recovered (${pct}%)</span>
          </div>
          <div class="progress-track" style="height:8px">
            <div class="progress-fill" style="width:${pct}%"></div>
          </div>
        </div>
      `;
    }).join('');
  }
}

/* ── Modals ─────────────────────────────────────────────────────────────── */
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

function showCreateSubject() { document.getElementById('subject-modal').classList.add('open'); }

async function showUploadModal() {
  document.getElementById('upload-modal').classList.add('open');
  try {
    const subjects = await apiFetch('/subjects');
    const sel = document.getElementById('upload-subject');
    sel.innerHTML = '<option value="">Select a subject...</option>' +
      subjects.map(s => `<option value="${esc(s.id)}">${esc(s.name)}</option>`).join('');
    if (!subjects.length) {
      toast('Create a subject first before uploading', 'error');
      closeModal('upload-modal');
      showCreateSubject();
    }
  } catch(err) {
    closeModal('upload-modal');
    toast('Failed to load subjects: ' + err.message, 'error');
  }
}

async function createSubject(e) {
  e.preventDefault();
  try {
    await apiFetch('/subjects', {
      method: 'POST',
      body: JSON.stringify({
        name: document.getElementById('subject-name').value,
        description: document.getElementById('subject-desc').value
      })
    });
    closeModal('subject-modal'); e.target.reset();
    toast('Subject created!', 'success');
    await loadSubjects();
  } catch (err) { toast(err.message, 'error'); }
}

async function handleUpload(e) {
  e.preventDefault();
  const fileInput = document.getElementById('upload-file');
  const file = fileInput.files[0];
  if (!file) { toast('Please select a file', 'error'); return; }

  const btn = document.getElementById('btn-do-upload');
  btn.disabled = true; btn.innerHTML = '<div class="spinner" style="width:16px;height:16px;border-width:2px"></div> Uploading...';

  let classId = null;
  try {
    // Create class first
    const cls = await apiFetch('/classes', {
      method: 'POST',
      body: JSON.stringify({
        subjectId: document.getElementById('upload-subject').value,
        className: document.getElementById('upload-class-name').value,
        description: document.getElementById('upload-desc').value
      })
    });
    classId = cls.id;

    // Upload file
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API}/classes/${classId}/upload`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token },
      body: formData
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || 'Upload failed');
    }

    closeModal('upload-modal'); e.target.reset();
    toast('Recording uploaded! Processing started...', 'success');
    setTimeout(() => { window.location.href = `class.html?id=${classId}`; }, 1000);
  } catch (err) {
    // Rollback: delete orphaned class if upload failed
    if (classId) {
      try { await apiFetch(`/classes/${classId}`, { method: 'DELETE' }); } catch (_) {}
    }
    toast(err.message, 'error');
  }
  finally { btn.disabled = false; btn.innerHTML = '<svg width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg> Upload & Process'; }
}

/* ── Class Detail ───────────────────────────────────────────────────────── */
async function initClassDetail() {
  if (!isLoggedIn()) { window.location.href = 'index.html'; return; }

  const params = new URLSearchParams(window.location.search);
  const classId = params.get('id');
  if (!classId) { window.location.href = 'dashboard.html'; return; }

  const name = currentUser?.name || currentUser?.email || 'User';
  document.getElementById('user-name').textContent = name;
  document.getElementById('user-avatar').textContent = name[0].toUpperCase();
  document.getElementById('logout-btn').addEventListener('click', () => { clearAuth(); window.location.href = 'index.html'; });

  try {
    const cls = await apiFetch(`/classes/${classId}`);
    window._classData = cls;
    window._classId = classId;

    document.getElementById('class-title').textContent = cls.className;
    document.getElementById('class-subject').textContent = cls.subject || '';
    if (document.getElementById('class-subject-header')) {
      document.getElementById('class-subject-header').textContent = cls.subject ? cls.subject + ' · Recovery' : 'Class Recovery';
    }
    if (document.getElementById('class-date') && cls.createdAt) {
      document.getElementById('class-date').textContent = new Date(cls.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    }
    document.getElementById('class-description').textContent = cls.description || '';

    const statusEl = document.getElementById('class-status');
    statusEl.textContent = formatStatus(cls.status);
    statusEl.className = 'status-badge status-' + cls.status;

    if (cls.durationSeconds) {
      document.getElementById('class-duration').textContent = formatDuration(cls.durationSeconds);
    } else {
      document.getElementById('class-duration-display').style.display = 'none';
    }

    const progress = getProgressPercent(cls.status);
    if (document.getElementById('progress-fill')) document.getElementById('progress-fill').style.width = progress + '%';
    if (document.getElementById('progress-text')) document.getElementById('progress-text').textContent = progress + '%';

    updatePipelineWidget(cls.status, progress);
    setupAudioPlayer(cls);
    renderOverview(cls);
    initTabs();

    if (cls.status === 'PENDING' || cls.status === 'UPLOADED' || cls.status === 'PROCESSING' || cls.status === 'AUDIO_READY' || cls.status === 'TRANSCRIBED') {
      startStatusPolling(classId);
    }
  } catch (err) {
    document.getElementById('tab-area').innerHTML = `<div class="empty-state"><div class="empty-icon">⚠️</div><h3>Failed to load class</h3><p>${esc(err.message)}</p><a href="dashboard.html" class="btn btn-primary">Back to Dashboard</a></div>`;
  }
}

function updatePipelineWidget(status, progress) {
  const card = document.getElementById('pipeline-card');
  if (!card) return;

  if (status === 'ANALYZED' || status === 'DONE') {
    card.style.display = 'none';
    return;
  }

  card.style.display = 'block';
  const fill = document.getElementById('progress-fill');
  const text = document.getElementById('progress-text');
  if (fill) fill.style.width = progress + '%';
  if (text) text.textContent = progress + '%';

  const stepMap = {
    'PENDING':     ['active', '', '', '', ''],
    'UPLOADED':    ['completed', 'active', '', '', ''],
    'PROCESSING':  ['completed', 'completed', 'active', '', ''],
    'AUDIO_READY': ['completed', 'completed', 'active', '', ''],
    'TRANSCRIBED': ['completed', 'completed', 'completed', 'active', ''],
    'ANALYZED':    ['completed', 'completed', 'completed', 'completed', 'completed'],
    'DONE':        ['completed', 'completed', 'completed', 'completed', 'completed']
  };

  const steps = stepMap[status] || ['', '', '', '', ''];
  ['step-upload', 'step-audio', 'step-transcribe', 'step-analyze', 'step-ready'].forEach((id, idx) => {
    const el = document.getElementById(id);
    if (el) el.className = 'pipeline-step ' + (steps[idx] || '');
  });
}

let pollInterval = null;
function startStatusPolling(classId) {
  if (pollInterval) clearInterval(pollInterval);
  pollInterval = setInterval(async () => {
    try {
      const cls = await apiFetch(`/classes/${classId}`);
      window._classData = cls;
      const statusEl = document.getElementById('class-status');
      if (statusEl) {
        statusEl.textContent = formatStatus(cls.status);
        statusEl.className = 'status-badge status-' + cls.status;
      }
      const progress = getProgressPercent(cls.status);
      updatePipelineWidget(cls.status, progress);

      const labelEl = document.getElementById('progress-status-label');
      if (labelEl) {
        const labels = {
          PENDING: 'Waiting in queue...',
          UPLOADED: 'Recording uploaded, extracting audio...',
          PROCESSING: 'Extracting audio with FFmpeg...',
          AUDIO_READY: 'Transcribing speech with Whisper...',
          TRANSCRIBED: 'Analyzing class concepts with Gemini AI...',
          ANALYZED: 'Processing Complete! Ready for recovery.',
          DONE: 'Processing Complete!',
          FAILED: 'Processing failed.'
        };
        labelEl.textContent = labels[cls.status] || 'Class Analysis in Progress';
      }

      if (cls.status === 'ANALYZED' || cls.status === 'DONE' || cls.status === 'FAILED') {
        clearInterval(pollInterval);
        pollInterval = null;
        updatePipelineWidget(cls.status, 100);
        setupAudioPlayer(cls);
        renderOverview(cls);
        if (cls.status === 'ANALYZED' || cls.status === 'DONE') {
          toast('AI Analysis complete! Recovery materials ready.', 'success');
          document.querySelectorAll('.tab-panel').forEach(p => { if (p.id !== 'tab-overview') delete p.dataset.loaded; });
        } else {
          // FAILED — show detailed error + retry button
          const reason = cls.failureReason || 'An unexpected error occurred during processing.';
          toast('Processing failed. ' + reason, 'error');
          showRetryBanner(cls.id, reason, cls.retryCount);
        }
      }
    } catch (_) {}
  }, 3000);
}

function parseTimestampToSeconds(ts) {
  if (!ts) return 0;
  const clean = ts.toString().trim().replace(/[\[\]\(\)]/g, '');
  const parts = clean.split(':').map(Number);
  if (parts.length === 3) return (parts[0] || 0) * 3600 + (parts[1] || 0) * 60 + (parts[2] || 0);
  if (parts.length === 2) return (parts[0] || 0) * 60 + (parts[1] || 0);
  if (parts.length === 1 && !isNaN(parts[0])) return parts[0];
  return 0;
}

function seekAudio(sec) {
  const audio = document.getElementById('class-audio-player');
  if (!audio) return;
  audio.currentTime = sec;
  audio.play().then(() => {
    const playBtn = document.getElementById('audio-play-btn');
    if (playBtn) {
      playBtn.querySelector('.icon-play').style.display = 'none';
      playBtn.querySelector('.icon-pause').style.display = 'block';
    }
  }).catch(() => {});
  toast(`Seeking to ${formatDuration(sec)}`, 'info');
}

async function loadAudioBlob(classId, endpoint) {
  const res = await fetch(`${API}/classes/${classId}/${endpoint}`, {
    headers: authHeaders()
  });
  if (!res.ok) throw new Error('Could not load audio');
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

function setupAudioPlayer(cls) {
  const card = document.getElementById('audio-player-card');
  const audio = document.getElementById('class-audio-player');
  if (!card || !audio) return;

  if (cls.status === 'PENDING' || cls.status === 'PROCESSING') {
    card.style.display = 'none';
    return;
  }

  card.style.display = 'block';

  // Load audio using fetch (with Authorization header) and create a Blob URL
  if (!audio._loadedClassId || audio._loadedClassId !== cls.id) {
    audio._loadedClassId = cls.id;
    loadAudioBlob(cls.id, 'audio').then(blobUrl => {
      if (audio._blobUrl) URL.revokeObjectURL(audio._blobUrl);
      audio._blobUrl = blobUrl;
      audio.src = blobUrl;
    }).catch(err => {
      console.warn('Could not load audio blob:', err.message);
    });
  }

  const playBtn = document.getElementById('audio-play-btn');
  const iconPlay = playBtn.querySelector('.icon-play');
  const iconPause = playBtn.querySelector('.icon-pause');
  const currTimeEl = document.getElementById('audio-curr-time');
  const totalTimeEl = document.getElementById('audio-total-time');
  const seekSlider = document.getElementById('audio-seek');
  const speedSelect = document.getElementById('audio-speed');
  const dlBtn = document.getElementById('btn-download-recording');

  playBtn.onclick = () => {
    if (audio.paused) {
      audio.play().then(() => {
        iconPlay.style.display = 'none';
        iconPause.style.display = 'block';
      }).catch(err => toast('Audio playback error: ' + err.message, 'error'));
    } else {
      audio.pause();
      iconPlay.style.display = 'block';
      iconPause.style.display = 'none';
    }
  };

  audio.ontimeupdate = () => {
    currTimeEl.textContent = formatDuration(Math.floor(audio.currentTime));
    if (audio.duration && !isNaN(audio.duration)) {
      seekSlider.value = (audio.currentTime / audio.duration) * 100;
    }
  };

  audio.onloadedmetadata = () => {
    totalTimeEl.textContent = formatDuration(Math.floor(audio.duration || cls.durationSeconds || 0));
  };

  seekSlider.oninput = () => {
    if (audio.duration && !isNaN(audio.duration)) {
      audio.currentTime = (seekSlider.value / 100) * audio.duration;
    }
  };

  speedSelect.onchange = () => {
    audio.playbackRate = parseFloat(speedSelect.value);
  };

  if (dlBtn) {
    dlBtn.onclick = async () => {
      try {
        const blobUrl = await loadAudioBlob(cls.id, 'recording');
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = cls.originalFilename || 'recording';
        a.click();
        setTimeout(() => URL.revokeObjectURL(blobUrl), 10000);
      } catch (err) { toast('Download failed: ' + err.message, 'error'); }
    };
  }
}

function renderOverview(cls) {
  const el = document.getElementById('tab-overview');
  const progressColor = cls.status === 'ANALYZED' || cls.status === 'DONE' ? 'var(--success)' : 'var(--primary)';
  el.innerHTML = `
    <div class="panel">
      <div class="panel-title">📝 What You Missed</div>
      <p class="panel-text">${cls.whatYouMissed ? esc(cls.whatYouMissed) : '<em style="color:var(--text-muted)">Processing will generate a summary once transcription completes.</em>'}</p>
    </div>
    <div class="panel">
      <div class="panel-title">🎯 Topics Covered</div>
      ${cls.topics?.length
        ? `<div style="display:flex;flex-wrap:wrap;gap:8px">${cls.topics.map(t => `<span style="padding:6px 14px;background:var(--primary-soft);color:var(--primary);border-radius:var(--radius-full);font-size:13px;font-weight:600">${esc(t)}</span>`).join('')}</div>`
        : '<p class="panel-text"><em style="color:var(--text-muted)">Topics will appear after analysis.</em></p>'}
    </div>
    <div class="panel" id="progress-panel">
      <div class="panel-title">📊 Your Progress</div>
      <div class="loading" style="padding:16px"><div class="spinner"></div></div>
    </div>
  `;
  loadProgressPanel(window._classId);
}

async function loadProgressPanel(classId) {
  try {
    const progress = await apiFetch(`/classes/${classId}/progress`);
    const panel = document.getElementById('progress-panel');
    if (!panel) return;

    const conceptEntries = Object.entries(progress.conceptStatuses || {});
    const statusColors = { MASTERED: 'var(--success)', UNDERSTANDING: 'var(--warning)', NEEDS_REVIEW: 'var(--accent)' };
    const statusLabels = { MASTERED: 'Mastered', UNDERSTANDING: 'Getting there', NEEDS_REVIEW: 'Needs review' };

    panel.innerHTML = `
      <div class="panel-title">📊 Your Progress</div>
      <div class="progress-ring-row">
        <div class="progress-ring-wrapper">
          <svg class="progress-ring" width="80" height="80">
            <circle class="progress-ring-bg" cx="40" cy="40" r="34" fill="none" stroke="var(--border)" stroke-width="6"/>
            <circle class="progress-ring-fill" cx="40" cy="40" r="34" fill="none" stroke="var(--primary)" stroke-width="6"
              stroke-dasharray="${2 * Math.PI * 34}" stroke-dashoffset="${2 * Math.PI * 34 * (1 - progress.progressPercent / 100)}"
              transform="rotate(-90 40 40)" stroke-linecap="round"/>
          </svg>
          <div class="progress-ring-text">${progress.progressPercent}%</div>
        </div>
        <div class="progress-ring-details">
          <div class="progress-detail-item">
            <span class="progress-detail-label">Concepts Tracked</span>
            <span class="progress-detail-value">${conceptEntries.length}</span>
          </div>
          ${progress.quizAttempted > 0 ? `
          <div class="progress-detail-item">
            <span class="progress-detail-label">Quiz Score</span>
            <span class="progress-detail-value">${progress.quizScorePercent}%</span>
          </div>
          ` : ''}
        </div>
      </div>
      ${conceptEntries.length ? `
      <div class="concept-status-grid">
        ${conceptEntries.map(([concept, status]) => `
          <div class="concept-status-chip" style="border-color:${statusColors[status] || 'var(--border)'}">
            <span class="concept-status-dot" style="background:${statusColors[status] || 'var(--border)'}"></span>
            <span class="concept-status-name">${esc(concept)}</span>
            <span class="concept-status-label" style="color:${statusColors[status] || 'var(--text-muted)'}">${statusLabels[status] || status}</span>
          </div>
        `).join('')}
      </div>
      ` : '<p style="font-size:13px;color:var(--text-muted);margin-top:12px">Mark concepts as mastered in the Must Learn tab to track your progress.</p>'}
    `;
  } catch (_) {}
}

async function loadMustLearn(classId) {
  const el = document.getElementById('tab-must-learn');
  try {
    const [items, progress] = await Promise.all([
      apiFetch(`/classes/${classId}/must-learn`),
      apiFetch(`/classes/${classId}/progress`).catch(() => ({ conceptStatuses: {} }))
    ]);
    if (!items.length) { el.innerHTML = emptyState('Not yet analyzed', 'Priority-ranked concepts will appear once AI analysis completes.'); return; }

    const statuses = progress.conceptStatuses || {};
    const statusColors = { MASTERED: 'var(--success)', UNDERSTANDING: 'var(--warning)', NEEDS_REVIEW: 'var(--accent)' };

    el.innerHTML = `<div class="panel"><div class="panel-title">🎯 Must Learn</div><div class="priority-stack">${items.map(i => {
      const currentStatus = statuses[i.concept] || '';
      return `
      <div class="priority-row">
        <span class="priority-tag ${esc(i.priority)}">${esc(i.priority)}</span>
        <span style="font-size:14px;font-weight:500;flex:1">${esc(i.concept)}</span>
        <div class="concept-actions">
          <button class="concept-btn ${currentStatus === 'MASTERED' ? 'active' : ''}" data-concept="${esc(i.concept)}" data-status="MASTERED" title="Mastered" style="--btn-color:var(--success)">✓</button>
          <button class="concept-btn ${currentStatus === 'UNDERSTANDING' ? 'active' : ''}" data-concept="${esc(i.concept)}" data-status="UNDERSTANDING" title="Getting there" style="--btn-color:var(--warning)">~</button>
          <button class="concept-btn ${currentStatus === 'NEEDS_REVIEW' ? 'active' : ''}" data-concept="${esc(i.concept)}" data-status="NEEDS_REVIEW" title="Needs review" style="--btn-color:var(--accent)">!</button>
        </div>
      </div>`;
    }).join('')}</div></div>`;

    el.querySelectorAll('.concept-btn').forEach(btn => {
      btn.addEventListener('click', () => markConcept(classId, btn.dataset.concept, btn.dataset.status));
    });
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function markConcept(classId, concept, status) {
  try {
    await apiFetch(`/classes/${classId}/progress/concept`, {
      method: 'POST',
      body: JSON.stringify({ concept, status })
    });
    toast(`Marked "${concept}" as ${status.replace('_', ' ').toLowerCase()}`, 'success');
    // Reload must-learn and progress panel
    loadMustLearn(classId);
    loadProgressPanel(classId);
  } catch (err) { toast(err.message, 'error'); }
}

async function loadCatchUp(classId) {
  const el = document.getElementById('tab-catchup');
  try {
    const path = await apiFetch(`/classes/${classId}/catchup-path`);
    if (!path.length) { el.innerHTML = emptyState('Not yet analyzed', 'A step-by-step learning path will be generated after analysis.'); return; }
    el.innerHTML = `<div class="panel"><div class="panel-title">🗺️ Catch-up Path</div><ol class="panel-list">${path.map((p, i) => `<li><strong style="color:var(--primary);margin-right:8px">${i + 1}.</strong> ${esc(p)}</li>`).join('')}</ol></div>`;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadExplanations(classId) {
  const el = document.getElementById('tab-explanations');
  try {
    const data = await apiFetch(`/classes/${classId}/explanations`);
    const revisions = data.revisionPoints || [];
    const mustLearn = data.mustLearn || [];
    const topics = data.topics || [];

    if (!revisions.length && !mustLearn.length && !topics.length) {
      el.innerHTML = emptyState('Not yet analyzed', 'Deep concept explanations will appear after AI analysis.');
      return;
    }

    el.innerHTML = `
      <div class="panel">
        <div class="panel-title">💡 Core Concepts & Focus Areas</div>
        ${mustLearn.map(m => `
          <div class="explanation-card">
            <div class="item-header">
              <span class="item-title">${esc(m.concept)}</span>
              <span class="priority-tag ${esc(m.priority)}">${esc(m.priority)}</span>
            </div>
          </div>
        `).join('')}
      </div>
      ${revisions.length ? `
      <div class="panel">
        <div class="panel-title">📖 Key Takeaways & Explanations</div>
        <ul class="panel-list">
          ${revisions.map(r => `<li>${esc(r)}</li>`).join('')}
        </ul>
      </div>
      ` : ''}
    `;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadTimestamps(classId) {
  const el = document.getElementById('tab-timestamps');
  try {
    const items = await apiFetch(`/classes/${classId}/timestamps`);
    if (!items.length) { el.innerHTML = emptyState('No timestamps yet', 'Important timestamps will be extracted during analysis.'); return; }
    el.innerHTML = `<div class="panel"><div class="panel-title">⏱️ Important Timestamps (Click to listen)</div><div class="ts-stack">${items.map(i => `
      <div class="ts-row">
        <span class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(i.timestamp)})">▶ ${esc(i.timestamp)}</span>
        <span class="ts-type ts-type-${esc(i.type)}">${esc(i.type)}</span>
        <span style="font-size:14px;color:var(--text-secondary)">${esc(i.description)}</span>
      </div>
    `).join('')}</div></div>`;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadAssignments(classId) {
  const el = document.getElementById('tab-assignments');
  try {
    const items = await apiFetch(`/classes/${classId}/assignments`);
    if (!items.length) {
      el.innerHTML = emptyState('No assignments detected', 'Assignments, homework, and submission deadlines mentioned in lecture will appear here.');
      return;
    }
    el.innerHTML = `
      <div class="panel">
        <div class="panel-title">📋 Class Assignments & Tasks</div>
        ${items.map(a => `
          <div class="assignment-card">
            <div class="item-header">
              <span class="item-title">Assignment Mention</span>
              <span class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(a.timestamp)})">▶ ${esc(a.timestamp)}</span>
            </div>
            <p class="item-desc">${esc(a.description)}</p>
          </div>
        `).join('')}
      </div>
    `;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadAnnouncements(classId) {
  const el = document.getElementById('tab-announcements');
  try {
    const items = await apiFetch(`/classes/${classId}/announcements`);
    if (!items.length) {
      el.innerHTML = emptyState('No announcements found', 'Exam dates, syllabus changes, and teacher announcements from lecture will appear here.');
      return;
    }
    el.innerHTML = `
      <div class="panel">
        <div class="panel-title">📢 Class Announcements</div>
        ${items.map(a => `
          <div class="announcement-card">
            <div class="item-header">
              <span class="item-title">Announcement</span>
              <span class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(a.timestamp)})">▶ ${esc(a.timestamp)}</span>
            </div>
            <p class="item-desc">${esc(a.description)}</p>
          </div>
        `).join('')}
      </div>
    `;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadResources(classId) {
  const el = document.getElementById('tab-resources');
  try {
    const resources = await apiFetch(`/classes/${classId}/resources`);
    el.innerHTML = `
      <div class="panel">
        <div class="panel-title">📚 Course Materials & Downloads</div>
        <div class="resource-card">
          <div class="item-header">
            <span class="item-title">🎵 Class Audio Stream</span>
          </div>
          <p class="item-desc">High quality 16kHz speech-to-text audio recording for this lecture.</p>
          <div style="margin-top:10px;display:flex;gap:8px">
            <button class="btn btn-primary btn-sm" onclick="seekAudio(0)">▶ Play from Start</button>
            <button class="btn btn-ghost btn-sm" id="btn-download-resource">⬇ Download Recording</button>
          </div>
        </div>
      </div>
    `;
    document.getElementById('btn-download-resource')?.addEventListener('click', async () => {
      try {
        const blobUrl = await loadAudioBlob(classId, 'recording');
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = 'recording';
        a.click();
        setTimeout(() => URL.revokeObjectURL(blobUrl), 10000);
      } catch (err) { toast('Download failed: ' + err.message, 'error'); }
    });
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadRevision(classId) {
  const el = document.getElementById('tab-revision');
  try {
    const points = await apiFetch(`/classes/${classId}/revision`);
    if (!points.length) { el.innerHTML = emptyState('Not yet analyzed', 'Quick revision points will be generated after analysis.'); return; }
    el.innerHTML = `<div class="panel"><div class="panel-title">📋 Quick Revision</div><ul class="panel-list">${points.map(p => `<li>✅ ${esc(p)}</li>`).join('')}</ul></div>`;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadQuiz(classId) {
  const el = document.getElementById('tab-quiz');
  try {
    const questions = await apiFetch(`/classes/${classId}/quiz`);
    if (!questions.length) { el.innerHTML = emptyState('No quiz questions yet', 'Quiz questions will be generated from class content after analysis.'); return; }
    el.innerHTML = questions.map((q, qi) => `
      <div class="quiz-card" data-correct="${q.correctIndex}" data-qindex="${qi}">
        <div class="quiz-q">${qi + 1}. ${esc(q.question)}</div>
        <div class="quiz-opts">
          ${q.options.map((opt, oi) => `
            <div class="quiz-opt" data-index="${oi}">
              <span class="opt-key">${String.fromCharCode(65 + oi)}</span>
              <span>${esc(opt)}</span>
            </div>
          `).join('')}
        </div>
        ${q.sourceTimestamp ? `<div class="quiz-source" style="margin-top:12px"><span class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(q.sourceTimestamp)})">📌 Source: ${esc(q.sourceTimestamp)}</span></div>` : ''}
      </div>
    `).join('');

    el.querySelectorAll('.quiz-card').forEach(card => {
      card.querySelectorAll('.quiz-opt').forEach(opt => {
        opt.addEventListener('click', () => selectQuizOption(opt));
      });
    });
  } catch (err) { el.innerHTML = errorState(err.message); }
}

async function loadWatch(classId) {
  const el = document.getElementById('tab-watch');
  try {
    const data = await apiFetch(`/classes/${classId}/watch`);
    const mustWatch = data?.mustWatch || [];
    const recommended = data?.recommended || [];
    const optional = data?.optional || [];
    const explanationTimestamps = data?.explanationTimestamps || [];

    if (!mustWatch.length && !recommended.length && !optional.length) {
      el.innerHTML = emptyState('Not yet analyzed', 'Watch recommendations will be generated after analysis.');
      return;
    }

    const sections = [
      { title: '🔴 Must Watch', items: mustWatch, empty: 'No critical items.' },
      { title: '🟡 Recommended', items: recommended, empty: 'No recommended items.' },
      { title: '🟢 Optional', items: optional, empty: 'No optional items.' }
    ];
    el.innerHTML = `
      ${explanationTimestamps.length ? `
      <div class="panel" style="margin-bottom:20px">
        <div class="panel-title">⏱️ Key Explanation Moments</div>
        <div class="ts-stack">${explanationTimestamps.map(t => `
          <div class="ts-row">
            <span class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(t.timestamp)})">▶ ${esc(t.timestamp)}</span>
            <span style="font-size:14px;color:var(--text-secondary)">${esc(t.description)}</span>
          </div>
        `).join('')}</div>
      </div>
      ` : ''}
      ${sections.map(s => `
        <div class="watch-section">
          <h4>${s.title}</h4>
          ${s.items.length ? s.items.map(i => `
            <div class="watch-item">
              <strong>${esc(i.concept)}</strong>
              <div class="reason">${esc(i.reason)}</div>
            </div>
          `).join('') : `<p style="font-size:13px;color:var(--text-muted);padding:8px 0">${s.empty}</p>`}
        </div>
      `).join('')}
    `;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

function selectQuizOption(el) {
  const card = el.closest('.quiz-card');
  const correct = parseInt(card.dataset.correct);
  const selected = parseInt(el.dataset.index);
  const qi = parseInt(card.dataset.qindex);

  card.querySelectorAll('.quiz-opt').forEach(o => {
    o.style.pointerEvents = 'none';
    if (parseInt(o.dataset.index) === correct) o.classList.add('correct');
  });
  if (selected !== correct) el.classList.add('wrong');

  // Auto-submit quiz result
  if (window._classId && qi !== undefined) {
    const result = {};
    result[qi] = selected === correct;
    apiFetch(`/classes/${window._classId}/progress/quiz`, {
      method: 'POST',
      body: JSON.stringify({ results: result })
    }).catch(() => {});
  }
}

/* ── Learning Gaps ─────────────────────────────────────────────────────── */
async function loadLearningGaps(classId) {
  const el = document.getElementById('tab-gaps');
  try {
    const data = await apiFetch(`/classes/${classId}/learning-gaps`);

    if (!data.missingTopics?.length && !data.recommendations?.length) {
      el.innerHTML = emptyState('No learning gaps detected', data.overallAssessment || 'This class covers all previously introduced topics.');
      return;
    }

    const priorityColors = { CRITICAL: 'var(--accent)', HIGH: 'var(--warning)', MEDIUM: 'var(--primary)' };
    const actionIcons = { REVIEW: '📖', STUDY: '📚', PRACTICE: '✏️' };

    el.innerHTML = `
      <div class="gap-assessment">
        <div class="gap-assessment-icon">📊</div>
        <div class="gap-assessment-text">
          <strong>Overall Assessment</strong>
          <p>${esc(data.overallAssessment)}</p>
        </div>
      </div>

      <div class="gap-stats">
        <div class="gap-stat">
          <div class="gap-stat-value">${data.totalClassesAnalyzed}</div>
          <div class="gap-stat-label">Classes Analyzed</div>
        </div>
        <div class="gap-stat">
          <div class="gap-stat-value">${data.topicsCoveredInSubject?.length || 0}</div>
          <div class="gap-stat-label">Total Topics</div>
        </div>
        <div class="gap-stat">
          <div class="gap-stat-value" style="color:${data.missingTopics?.length ? 'var(--accent)' : 'var(--success)'}">${data.missingTopics?.length || 0}</div>
          <div class="gap-stat-label">Missing Topics</div>
        </div>
        <div class="gap-stat">
          <div class="gap-stat-value">${data.prerequisiteTopics?.length || 0}</div>
          <div class="gap-stat-label">Prerequisites</div>
        </div>
      </div>

      ${data.prerequisiteTopics?.length ? `
        <div class="panel">
          <div class="panel-title">🔗 Prerequisites to Review</div>
          <div class="gap-prereq-list">
            ${data.prerequisiteTopics.map(t => `<span class="gap-prereq-tag">${esc(t)}</span>`).join('')}
          </div>
        </div>
      ` : ''}

      ${data.missingTopics?.length ? `
        <div class="panel">
          <div class="panel-title">⚠️ Missing Topics</div>
          <div class="gap-missing-list">
            ${data.missingTopics.map(t => `
              <div class="gap-missing-item">
                <svg width="14" height="14" fill="none" stroke="var(--accent)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                <span>${esc(t)}</span>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}

      ${data.recommendations?.length ? `
        <div class="panel">
          <div class="panel-title">💡 Recommendations</div>
          <div class="gap-recs">
            ${data.recommendations.map(r => `
              <div class="gap-rec-card">
                <div class="gap-rec-header">
                  <span class="gap-rec-action">${actionIcons[r.suggestedAction] || '📋'} ${esc(r.suggestedAction)}</span>
                  <span class="gap-rec-priority" style="color:${priorityColors[r.priority] || 'var(--text-muted)'}">${esc(r.priority)}</span>
                </div>
                <div class="gap-rec-topic">${esc(r.topic)}</div>
                <div class="gap-rec-reason">${esc(r.reason)}</div>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}
    `;
  } catch (err) { el.innerHTML = errorState(err.message); }
}

/* ── Chat ───────────────────────────────────────────────────────────────── */
function loadChat(classId) {
  const el = document.getElementById('tab-chat');
  el.innerHTML = `
    <div class="chat-container">
      <div class="chat-messages" id="chat-messages">
        <div class="chat-welcome">
          <div class="chat-welcome-icon">💬</div>
          <h3>AI Class Tutor & Recovery Assistant</h3>
          <p>Ask anything about this lecture or previous lectures in this subject. Answers are grounded strictly in class transcripts with timestamp citations.</p>
        </div>
      </div>
      <div class="chat-suggestions">
        <button class="suggestion-chip" type="button" onclick="askSuggestion('What did the teacher explain?')">💡 What did the teacher explain?</button>
        <button class="suggestion-chip" type="button" onclick="askSuggestion('What assignment was given?')">📋 What assignment was given?</button>
        <button class="suggestion-chip" type="button" onclick="askSuggestion('Explain this concept simply.')">🧠 Explain this concept simply</button>
        <button class="suggestion-chip" type="button" onclick="askSuggestion('What should I learn first?')">🎯 What should I learn first?</button>
      </div>
      <form class="chat-input-bar" id="chat-form">
        <input class="chat-input" id="chat-input" type="text" placeholder="Ask a question about this class or prerequisites..." autocomplete="off">
        <button class="btn btn-primary chat-send" type="submit" id="chat-send" title="Send message" aria-label="Send message">
          <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
        </button>
      </form>
    </div>
  `;

  const form = document.getElementById('chat-form');
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    sendChatMessage(classId);
  });
}

window.askSuggestion = function(text) {
  const input = document.getElementById('chat-input');
  if (input) {
    input.value = text;
    const form = document.getElementById('chat-form');
    if (form) form.dispatchEvent(new Event('submit', { cancelable: true }));
  }
};

async function sendChatMessage(classId) {
  const input = document.getElementById('chat-input');
  const msg = input.value.trim();
  if (!msg) return;

  const messagesEl = document.getElementById('chat-messages');
  const sendBtn = document.getElementById('chat-send');

  // Remove welcome if present
  const welcome = messagesEl.querySelector('.chat-welcome');
  if (welcome) welcome.remove();

  // Add user message
  messagesEl.innerHTML += `
    <div class="chat-msg user-msg">
      <div class="chat-msg-content">${esc(msg)}</div>
    </div>
  `;

  // Add typing indicator
  messagesEl.innerHTML += `
    <div class="chat-msg bot-msg" id="chat-typing">
      <div class="chat-msg-content">
        <div class="typing-dots"><span></span><span></span><span></span></div>
      </div>
    </div>
  `;
  messagesEl.scrollTop = messagesEl.scrollHeight;

  input.value = '';
  sendBtn.disabled = true;

  try {
    const data = await apiFetch(`/classes/${classId}/chat`, {
      method: 'POST',
      body: JSON.stringify({ message: msg })
    });

    // Remove typing indicator
    const typing = document.getElementById('chat-typing');
    if (typing) typing.remove();

    // Extract timestamps from text if not in sourceTimestamps
    const extractedTs = [];
    const tsMatches = data.answer?.matchAll(/\[(?:[^|\]]+\|\s*)?(\d{1,2}:\d{2}(?::\d{2})?)\]/g);
    if (tsMatches) {
      for (const m of tsMatches) {
        if (m[1] && !extractedTs.includes(m[1])) extractedTs.push(m[1]);
      }
    }

    const allTs = Array.from(new Set([...(data.sourceTimestamps || []), ...extractedTs]));

    const timestampsHtml = allTs.length
      ? `<div class="chat-timestamps" style="margin-top:10px;display:flex;flex-wrap:wrap;gap:6px">${allTs.map(t => `<button type="button" class="clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(t)})">▶ Watch moment: ${esc(t)}</button>`).join('')}</div>`
      : '';

    messagesEl.innerHTML += `
      <div class="chat-msg bot-msg">
        <div class="chat-msg-content">
          <div class="chat-answer">${formatChatAnswer(data.answer)}</div>
          ${timestampsHtml}
        </div>
      </div>
    `;
    messagesEl.scrollTop = messagesEl.scrollHeight;

  } catch (err) {
    const typing = document.getElementById('chat-typing');
    if (typing) typing.remove();

    messagesEl.innerHTML += `
      <div class="chat-msg bot-msg">
        <div class="chat-msg-content chat-error">
          <svg width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          ${esc(err.message)}
        </div>
      </div>
    `;
    messagesEl.scrollTop = messagesEl.scrollHeight;
  } finally {
    sendBtn.disabled = false;
    input.focus();
  }
}

async function loadPreviousClasses(classId) {
  const el = document.getElementById('tab-prev-classes');
  if (!el) return;
  try {
    const cls = window._classData;
    if (!cls || !cls.subjectId) {
      el.innerHTML = emptyState('No subject linked', 'Associate this class with a subject to browse related classes.');
      return;
    }
    const siblings = await apiFetch(`/classes/subject/${cls.subjectId}`);
    const otherClasses = (siblings || []).filter(c => c.id !== classId);
    if (!otherClasses.length) {
      el.innerHTML = emptyState('No other classes in this subject', `When more lectures are uploaded to ${esc(cls.subject || 'this subject')}, they will appear here.`);
      return;
    }

    el.innerHTML = `
      <div class="panel">
        <div class="panel-title">📚 Previous & Connected Lectures in ${esc(cls.subject || 'Subject')}</div>
        <p style="font-size:14px;color:var(--text-secondary);margin-bottom:18px">Navigate through previous classes in this subject to review prerequisites or catch up on connected topics.</p>
        <div class="prev-classes-grid">
          ${otherClasses.map(c => `
            <div class="prev-class-card">
              <div>
                <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:8px">
                  <h4 style="font-size:15px;font-weight:700;color:var(--text)">${esc(c.className)}</h4>
                  <span class="status-badge status-${c.status}">${formatStatus(c.status)}</span>
                </div>
                <div style="font-size:12px;color:var(--text-muted);margin-top:4px">
                  ${c.durationSeconds ? formatDuration(c.durationSeconds) + ' · ' : ''}${c.createdAt ? new Date(c.createdAt).toLocaleDateString() : ''}
                </div>
                ${c.topics?.length ? `
                  <div class="prev-class-topics">
                    ${c.topics.slice(0, 3).map(t => `<span class="prev-topic-tag">${esc(t)}</span>`).join('')}
                  </div>
                ` : ''}
              </div>
              <button class="btn btn-secondary btn-sm" style="align-self:flex-start;margin-top:10px" onclick="window.location.href='class.html?id=${esc(c.id)}'">
                Open Class →
              </button>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  } catch (err) {
    el.innerHTML = errorState(err.message);
  }
}

function formatChatAnswer(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(?<!\*)\*(?!\*)([^*]+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')
    .replace(/`([^`]+?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>');
}

/* ── Tabs ───────────────────────────────────────────────────────────────── */
function initTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      btn.classList.add('active');
      const panel = document.getElementById('tab-' + btn.dataset.tab);
      if (panel) panel.classList.add('active');

      const classId = window._classId;
      const tab = btn.dataset.tab;
      const loaders = {
        'must-learn': loadMustLearn,
        'catchup': loadCatchUp,
        'explanations': loadExplanations,
        'gaps': loadLearningGaps,
        'timestamps': loadTimestamps,
        'assignments': loadAssignments,
        'announcements': loadAnnouncements,
        'resources': loadResources,
        'revision': loadRevision,
        'quiz': loadQuiz,
        'watch': loadWatch,
        'chat': loadChat,
        'prev-classes': loadPreviousClasses,
        'slides': loadSlides,
        'notes': loadNotes
      };
      if (loaders[tab] && panel && !panel.dataset.loaded) {
        panel.dataset.loaded = '1';
        loaders[tab](classId);
      }
    });
  });
}

/* ── Slides Tab ──────────────────────────────────────────────────────────── */
async function loadSlideBlob(classId, filename) {
  const res = await fetch(`${API}/classes/${classId}/slides/${encodeURIComponent(filename)}`, {
    headers: authHeaders()
  });
  if (!res.ok) throw new Error('Could not load slide');
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

function showSlideLightbox(blobUrl, title) {
  const modal = document.getElementById('slide-lightbox-modal');
  const img = document.getElementById('lightbox-img');
  const titleEl = document.getElementById('lightbox-title');
  const dlBtn = document.getElementById('lightbox-download-btn');
  if (!modal || !img) return;

  img.src = blobUrl;
  if (titleEl) titleEl.textContent = title || 'Slide Preview';
  if (dlBtn) {
    dlBtn.onclick = () => {
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = (title ? title.replace(/\s+/g, '_') : 'slide') + '.jpg';
      a.click();
    };
  }
  modal.style.display = 'flex';
}

function closeSlideLightbox() {
  const modal = document.getElementById('slide-lightbox-modal');
  if (modal) modal.style.display = 'none';
}

function downloadSlide(blobUrl, filename) {
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = filename || 'slide.jpg';
  a.click();
}

async function loadSlides(classId) {
  const el = document.getElementById('tab-slides');
  if (!el) return;
  el.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
  try {
    const cls = await apiFetch(`/classes/${classId}`);
    if (!cls.slidePaths || cls.slidePaths.length === 0) {
      el.innerHTML = emptyState('No Slides', 'No slides or video keyframes were detected in this class recording.');
      return;
    }

    // Revoke any previous slide blob URLs to prevent memory leaks
    if (window._slideBlobUrls) {
      window._slideBlobUrls.forEach(url => URL.revokeObjectURL(url));
    }
    window._slideBlobUrls = [];

    let html = '';
    if (cls.visualSummary) {
      html += `
        <div class="panel" style="margin-bottom:24px;">
          <div class="panel-title">🖼️ AI Visual Summary of Slides</div>
          <p class="panel-text" style="white-space:pre-line">${esc(cls.visualSummary)}</p>
        </div>
      `;
    }

    html += `
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h3 style="margin:0; font-size:16px; font-weight:700;">Extracted Slides & Keyframes (${cls.slidePaths.length})</h3>
        <span style="font-size:12px; color:var(--text-muted);">Click any slide to view full size</span>
      </div>
      <div class="slides-grid" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(280px, 1fr)); gap:16px;">
    `;

    cls.slidePaths.forEach((path, index) => {
      const filename = path.substring(path.lastIndexOf('/') + 1).substring(path.lastIndexOf('\\') + 1);
      html += `
        <div class="slide-card" id="slide-card-${index}" style="border:1px solid var(--border); border-radius:var(--radius-md); overflow:hidden; background:var(--bg-card); cursor:pointer; transition:transform 0.15s ease, box-shadow 0.15s ease;">
          <div class="slide-img-wrapper" style="position:relative; width:100%; aspect-ratio:16/9; background:var(--bg-secondary); display:flex; align-items:center; justify-content:center; overflow:hidden;">
            <div class="spinner spinner-sm" id="slide-spinner-${index}"></div>
            <img id="slide-img-${index}" style="width:100%; height:100%; object-fit:contain; display:none;" alt="Slide ${index + 1}" />
          </div>
          <div style="padding:10px 14px; display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--border-light); font-size:12px;">
            <span style="font-weight:600; color:var(--text-secondary);">Slide ${index + 1}</span>
            <button class="btn btn-ghost btn-sm" id="slide-dl-btn-${index}" style="padding:2px 8px; font-size:11px;">⬇ Save</button>
          </div>
        </div>
      `;
    });
    html += '</div>';

    el.innerHTML = html;

    // Load each slide image securely with JWT authentication
    cls.slidePaths.forEach((path, index) => {
      const filename = path.substring(path.lastIndexOf('/') + 1).substring(path.lastIndexOf('\\') + 1);
      loadSlideBlob(classId, filename).then(blobUrl => {
        window._slideBlobUrls.push(blobUrl);
        const spinner = document.getElementById(`slide-spinner-${index}`);
        const img = document.getElementById(`slide-img-${index}`);
        const card = document.getElementById(`slide-card-${index}`);
        const dlBtn = document.getElementById(`slide-dl-btn-${index}`);

        if (spinner) spinner.style.display = 'none';
        if (img) {
          img.src = blobUrl;
          img.style.display = 'block';
        }
        if (card) {
          card.onclick = () => showSlideLightbox(blobUrl, `Slide ${index + 1}`);
        }
        if (dlBtn) {
          dlBtn.onclick = (e) => {
            e.stopPropagation();
            downloadSlide(blobUrl, `Slide_${index + 1}.jpg`);
          };
        }
      }).catch(err => {
        const spinner = document.getElementById(`slide-spinner-${index}`);
        if (spinner) {
          spinner.parentElement.innerHTML = '<span style="color:var(--text-muted); font-size:12px;">Failed to load image</span>';
        }
      });
    });

  } catch (err) {
    el.innerHTML = errorState(err.message);
  }
}

/* ── Notes Tab ───────────────────────────────────────────────────────────── */
async function loadNotes(classId) {
  const el = document.getElementById('tab-notes');
  if (!el) return;
  el.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
  try {
    const notes = await apiFetch(`/classes/${classId}/notes`);
    renderNotes(classId, notes);
  } catch (err) {
    el.innerHTML = errorState(err.message);
  }
}

function renderNotes(classId, notes) {
  const el = document.getElementById('tab-notes');
  if (!el) return;

  const notesHtml = notes.length
    ? notes.map(n => `
      <div class="note-card" data-note-id="${esc(n.id)}">
        <div class="note-content" id="note-content-${esc(n.id)}">${esc(n.content)}</div>
        <textarea class="note-edit-area" id="note-edit-${esc(n.id)}" style="display:none" rows="4">${esc(n.content)}</textarea>
        <div class="note-footer">
          <span class="note-time">${n.updatedAt ? new Date(n.updatedAt).toLocaleString(undefined, {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}) : ''}</span>
          <div class="note-actions">
            <button class="btn btn-ghost btn-sm note-edit-btn" data-id="${esc(n.id)}" title="Edit note">
              <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              Edit
            </button>
            <button class="btn btn-ghost btn-sm note-save-btn" data-id="${esc(n.id)}" style="display:none" title="Save changes">
              <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>
              Save
            </button>
            <button class="btn btn-ghost btn-sm note-cancel-btn" data-id="${esc(n.id)}" style="display:none">Cancel</button>
            <button class="btn btn-ghost btn-sm note-delete-btn" data-id="${esc(n.id)}" title="Delete note" style="color:var(--error)">
              <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
            </button>
          </div>
        </div>
      </div>`).join('')
    : '<div class="empty-state" style="padding:32px 0"><div class="empty-icon">📓</div><h3>No notes yet</h3><p>Write your first note for this class below.</p></div>';

  el.innerHTML = `
    <div class="notes-container">
      <div class="notes-header">
        <div class="notes-title">📓 My Notes <span class="notes-count">${notes.length} note${notes.length !== 1 ? 's' : ''}</span></div>
      </div>

      <div class="note-compose">
        <textarea class="note-textarea" id="new-note-text" placeholder="Write a note for this class... (e.g. key concepts, doubts, action items)" rows="4"></textarea>
        <div class="note-compose-footer">
          <span class="note-char-count" id="note-char-count">0 / 5000</span>
          <button class="btn btn-primary" id="btn-save-note">
            <svg width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            Save Note
          </button>
        </div>
      </div>

      <div class="notes-list" id="notes-list">${notesHtml}</div>
    </div>
  `;

  // Character counter
  const textarea = document.getElementById('new-note-text');
  const charCount = document.getElementById('note-char-count');
  if (textarea && charCount) {
    textarea.addEventListener('input', () => {
      const len = textarea.value.length;
      charCount.textContent = `${len} / 5000`;
      charCount.style.color = len > 4800 ? 'var(--error)' : 'var(--text-muted)';
    });
  }

  // Save new note
  document.getElementById('btn-save-note')?.addEventListener('click', async () => {
    const text = textarea?.value?.trim();
    if (!text) { toast('Please write something before saving.', 'error'); return; }
    if (text.length > 5000) { toast('Note is too long (max 5000 chars).', 'error'); return; }
    const btn = document.getElementById('btn-save-note');
    btn.disabled = true; btn.textContent = 'Saving...';
    try {
      await apiFetch(`/classes/${classId}/notes`, { method: 'POST', body: JSON.stringify({ content: text }) });
      if (textarea) textarea.value = '';
      if (charCount) charCount.textContent = '0 / 5000';
      toast('Note saved!', 'success');
      // Reload notes (reset loaded flag so tab reloads)
      const panel = document.getElementById('tab-notes');
      if (panel) delete panel.dataset.loaded;
      await loadNotes(classId);
    } catch (err) { toast('Failed to save note: ' + err.message, 'error'); }
    finally { btn.disabled = false; btn.textContent = 'Save Note'; btn.innerHTML = `<svg width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg> Save Note`; }
  });

  // Edit / Save / Cancel / Delete note buttons
  el.querySelectorAll('.note-edit-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      document.getElementById(`note-content-${id}`).style.display = 'none';
      const editArea = document.getElementById(`note-edit-${id}`);
      editArea.style.display = 'block';
      editArea.focus();
      btn.style.display = 'none';
      btn.closest('.note-card').querySelector('.note-save-btn').style.display = 'inline-flex';
      btn.closest('.note-card').querySelector('.note-cancel-btn').style.display = 'inline-flex';
    });
  });

  el.querySelectorAll('.note-cancel-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      document.getElementById(`note-content-${id}`).style.display = 'block';
      document.getElementById(`note-edit-${id}`).style.display = 'none';
      btn.style.display = 'none';
      btn.closest('.note-card').querySelector('.note-save-btn').style.display = 'none';
      btn.closest('.note-card').querySelector('.note-edit-btn').style.display = 'inline-flex';
    });
  });

  el.querySelectorAll('.note-save-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const newContent = document.getElementById(`note-edit-${id}`).value.trim();
      if (!newContent) { toast('Note cannot be empty.', 'error'); return; }
      btn.disabled = true; btn.textContent = 'Saving...';
      try {
        await apiFetch(`/classes/${classId}/notes/${id}`, { method: 'PUT', body: JSON.stringify({ content: newContent }) });
        toast('Note updated!', 'success');
        const panel = document.getElementById('tab-notes');
        if (panel) delete panel.dataset.loaded;
        await loadNotes(classId);
      } catch (err) { toast('Failed to update: ' + err.message, 'error'); }
      finally { btn.disabled = false; }
    });
  });

  el.querySelectorAll('.note-delete-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      if (!confirm('Delete this note? This cannot be undone.')) return;
      const id = btn.dataset.id;
      try {
        await apiFetch(`/classes/${classId}/notes/${id}`, { method: 'DELETE' });
        toast('Note deleted.', 'info');
        const panel = document.getElementById('tab-notes');
        if (panel) delete panel.dataset.loaded;
        await loadNotes(classId);
      } catch (err) { toast('Failed to delete: ' + err.message, 'error'); }
    });
  });
}

/* ── Utilities ──────────────────────────────────────────────────────────── */
function esc(s) { const d = document.createElement('div'); d.textContent = s ?? ''; return d.innerHTML; }
function formatDuration(sec) {
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m ${s}s`;
}
function formatStatus(s) {
  const map = { PENDING: 'Pending', PROCESSING: 'Processing', UPLOADED: 'Uploaded', AUDIO_READY: 'Audio Ready', TRANSCRIBED: 'Transcribed', ANALYZED: 'Analyzed', DONE: 'Done', FAILED: 'Failed' };
  return map[s] || s;
}
function getProgressPercent(status) {
  const map = { PENDING: 0, PROCESSING: 25, UPLOADED: 25, AUDIO_READY: 50, TRANSCRIBED: 75, ANALYZED: 100, DONE: 100, FAILED: 0 };
  return map[status] || 0;
}
function emptyState(title, desc) {
  return `<div class="empty-state"><div class="empty-icon">✨</div><h3>${esc(title)}</h3><p>${esc(desc)}</p></div>`;
}
function errorState(msg) {
  return `<div class="empty-state"><div class="empty-icon">⚠️</div><h3>Something went wrong</h3><p>${esc(msg)}</p></div>`;
}

/* ── Retry Logic ────────────────────────────────────────────────────────── */

function showRetryBanner(classId, reason, retryCount) {
  const container = document.getElementById('tab-area');
  if (!container) return;
  const attemptsMsg = retryCount > 0 ? `<p style="font-size:12px;margin-top:6px;opacity:0.8">Attempts so far: ${retryCount}</p>` : '';
  container.innerHTML = `
    <div style="background:var(--error-dim); border:1px solid var(--error); border-radius:var(--radius-lg); padding:24px; text-align:center; margin-bottom:24px;">
      <div style="font-size:32px; margin-bottom:12px">❌</div>
      <h3 style="color:var(--error); margin-bottom:8px">Processing Failed</h3>
      <p style="color:var(--text); margin-bottom:16px">${esc(reason)}</p>
      ${attemptsMsg}
      <button class="btn btn-primary" onclick="retryClassProcessing('${classId}')" style="margin-top:8px">
        <span class="icon">🔄</span> Retry Processing
      </button>
    </div>
  `;
}

async function retryClassProcessing(classId) {
  try {
    toast('Restarting processing pipeline...', 'info');
    const res = await apiFetch(`/classes/${classId}/retry`, { method: 'POST' });
    toast('Processing restarted successfully', 'success');
    
    // If on class page, refresh page to start polling
    if (window.location.pathname.includes('class.html')) {
      window.location.reload();
    } else {
      // If on dashboard, reload lists
      loadClasses();
    }
  } catch (err) {
    toast('Failed to retry processing: ' + err.message, 'error');
  }
}

document.addEventListener('click', (e) => {
  const retryBtn = e.target.closest('.card-retry-btn');
  if (retryBtn) {
    e.preventDefault();
    e.stopPropagation();
    const id = retryBtn.dataset.id;
    if (id) retryClassProcessing(id);
  }
});
