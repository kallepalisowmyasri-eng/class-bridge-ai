/* ── ClassBridge AI — App JavaScript ────────────────────────────────────── */
const API = '/api';
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
  const res = await fetch(API + path, { ...opts, headers });
  if (res.status === 401) { clearAuth(); window.location.href = 'index.html'; throw new Error('Session expired'); }
  if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.error || 'Request failed'); }
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

/* ── Dashboard ──────────────────────────────────────────────────────────── */
async function initDashboard() {
  if (!isLoggedIn()) { window.location.href = 'index.html'; return; }

  const name = currentUser?.name || currentUser?.email || 'User';
  document.getElementById('user-name').textContent = name;
  document.getElementById('user-avatar').textContent = name[0].toUpperCase();
  document.getElementById('logout-btn').addEventListener('click', () => { clearAuth(); window.location.href = 'index.html'; });

  // Modal backdrop close
  document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) closeModal(backdrop.id);
    });
  });

  // Search input
  let searchTimeout;
  const searchInput = document.getElementById('class-search');
  searchInput.addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => runClassSearch(), 300);
  });

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
  document.getElementById('btn-clear-filter').addEventListener('click', clearAllFilters);

  await Promise.all([loadSubjects(), loadClasses()]);
}

let activeSubjectFilter = null;
let activeStatusFilter = 'all';
let allClasses = [];

async function loadSubjects() {
  try {
    const subjects = await apiFetch('/subjects');
    document.getElementById('stat-subjects').textContent = subjects.length;
    const grid = document.getElementById('subject-grid');

    if (!subjects.length) {
      grid.innerHTML = '<div class="empty-state"><div class="empty-icon">📚</div><h3>No subjects yet</h3><p>Create your first subject to start organizing your classes.</p><button class="btn btn-primary" id="empty-create-subject">Create Subject</button></div>';
      document.getElementById('empty-create-subject')?.addEventListener('click', showCreateSubject);
      return;
    }

    grid.innerHTML = subjects.map(s => `
      <div class="subject-card" data-id="${esc(s.id)}">
        <h3>${esc(s.name)}</h3>
        <p>${esc(s.description || 'No description')}</p>
        <div class="subject-meta">
          <span>📄 ${s.classCount} class${s.classCount !== 1 ? 'es' : ''}</span>
          <span>📅 ${new Date(s.createdAt).toLocaleDateString()}</span>
        </div>
      </div>
    `).join('');

    grid.querySelectorAll('.subject-card').forEach(card => {
      card.addEventListener('click', () => {
        const subjectId = card.dataset.id;
        const subjectName = card.querySelector('h3').textContent;
        filterClassesBySubject(subjectId, subjectName);
      });
    });
  } catch (err) { console.error('Failed to load subjects', err); }
}

async function filterClassesBySubject(subjectId, subjectName) {
  activeSubjectFilter = subjectId;
  document.getElementById('classes-section-title').textContent = subjectName;
  document.getElementById('btn-clear-filter').style.display = 'flex';

  // Highlight selected subject card
  document.querySelectorAll('.subject-card').forEach(card => {
    card.style.borderColor = card.dataset.id === subjectId ? 'var(--primary)' : '';
  });

  await runClassSearch();
}

function clearAllFilters() {
  activeSubjectFilter = null;
  activeStatusFilter = 'all';
  document.getElementById('classes-section-title').textContent = 'All Classes';
  document.getElementById('btn-clear-filter').style.display = 'none';
  document.getElementById('class-search').value = '';
  document.querySelectorAll('.subject-card').forEach(c => c.style.borderColor = '');
  document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
  document.querySelector('.filter-chip[data-filter="all"]').classList.add('active');
  runClassSearch();
}

async function runClassSearch() {
  const query = document.getElementById('class-search').value.trim();

  try {
    let classes;
    if (activeSubjectFilter) {
      classes = await apiFetch(`/classes/subject/${activeSubjectFilter}`);
    } else {
      classes = await apiFetch(`/classes/search?q=${encodeURIComponent(query)}`);
    }

    // Apply status filter
    if (activeStatusFilter !== 'all') {
      classes = classes.filter(c => c.status === activeStatusFilter);
    }

    allClasses = classes;
    renderClassList(classes);
  } catch (err) {
    toast('Search failed: ' + err.message, 'error');
  }
}

async function loadClasses() {
  try {
    allClasses = await apiFetch('/classes');
    document.getElementById('stat-classes').textContent = allClasses.length;
    document.getElementById('stat-analyzed').textContent = allClasses.filter(c => c.status === 'ANALYZED').length;
    renderClassList(allClasses);
  } catch (err) { console.error('Failed to load classes', err); }
}

function renderClassList(classes) {
  const list = document.getElementById('class-list');
  if (!classes.length) {
    list.innerHTML = '<div class="empty-state"><div class="empty-icon">🎬</div><h3>No classes found</h3><p>Upload a class recording and let AI analyze it for you.</p><button class="btn btn-primary" id="empty-upload">Upload Recording</button></div>';
    document.getElementById('empty-upload')?.addEventListener('click', showUploadModal);
    return;
  }

  const icons = { PENDING: '⏳', PROCESSING: '⚙️', UPLOADED: '📤', AUDIO_READY: '🎵', TRANSCRIBED: '📝', ANALYZED: '✅', DONE: '✅', FAILED: '❌' };

  list.innerHTML = classes.map(c => `
    <div class="class-card" data-id="${esc(c.id)}">
      <div class="class-icon">${icons[c.status] || '📄'}</div>
      <div class="class-info">
        <h4>${esc(c.className)}</h4>
        <p>${esc(c.subject || '')} ${c.durationSeconds ? '· ' + formatDuration(c.durationSeconds) : ''}</p>
      </div>
      <div class="class-right">
        <span class="status-badge status-${c.status}">${formatStatus(c.status)}</span>
      </div>
    </div>
  `).join('');

  list.querySelectorAll('.class-card').forEach(card => {
    card.addEventListener('click', () => {
      window.location.href = 'class.html?id=' + card.dataset.id;
    });
  });
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
    document.getElementById('progress-fill').style.width = progress + '%';
    document.getElementById('progress-text').textContent = progress + '%';

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
      const fill = document.getElementById('progress-fill');
      const text = document.getElementById('progress-text');
      if (fill) fill.style.width = progress + '%';
      if (text) text.textContent = progress + '%';

      const labelEl = document.getElementById('progress-status-label');
      if (labelEl) {
        const labels = {
          PENDING: 'Waiting in queue...',
          UPLOADED: 'Recording uploaded, extracting audio...',
          PROCESSING: 'Extracting audio with FFmpeg...',
          AUDIO_READY: 'Transcribing with Whisper...',
          TRANSCRIBED: 'Analyzing class with Gemini AI...',
          ANALYZED: 'Processing Complete! Ready for recovery.',
          DONE: 'Processing Complete!',
          FAILED: 'Processing failed.'
        };
        labelEl.textContent = labels[cls.status] || 'Processing Progress';
      }

      if (cls.status === 'ANALYZED' || cls.status === 'DONE' || cls.status === 'FAILED') {
        clearInterval(pollInterval);
        pollInterval = null;
        setupAudioPlayer(cls);
        renderOverview(cls);
        if (cls.status === 'ANALYZED' || cls.status === 'DONE') {
          toast('AI Analysis complete! Recovery materials ready.', 'success');
          document.querySelectorAll('.tab-panel').forEach(p => { if (p.id !== 'tab-overview') delete p.dataset.loaded; });
        } else {
          toast('Processing encountered an error.', 'error');
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

function setupAudioPlayer(cls) {
  const card = document.getElementById('audio-player-card');
  const audio = document.getElementById('class-audio-player');
  if (!card || !audio) return;

  if (cls.status === 'PENDING' || cls.status === 'PROCESSING') {
    card.style.display = 'none';
    return;
  }

  card.style.display = 'block';
  const audioUrl = `${API}/classes/${cls.id}/audio?token=${encodeURIComponent(token)}`;
  if (audio.getAttribute('src') !== audioUrl) {
    audio.src = audioUrl;
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
    dlBtn.onclick = () => {
      window.open(`${API}/classes/${cls.id}/recording?token=${encodeURIComponent(token)}`, '_blank');
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
            <a class="btn btn-ghost btn-sm" href="${API}/classes/${classId}/recording?token=${encodeURIComponent(token)}" target="_blank">Download Recording</a>
          </div>
        </div>
      </div>
    `;
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
          <h3>Ask about this class</h3>
          <p>Ask any question about the lecture content. Answers are based on the transcript and include source timestamps.</p>
        </div>
      </div>
      <form class="chat-input-bar" id="chat-form">
        <input class="chat-input" id="chat-input" type="text" placeholder="e.g. What were the main topics covered in the first 10 minutes?" autocomplete="off">
        <button class="btn btn-primary chat-send" type="submit" id="chat-send">
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

    // Add bot response
    const timestampsHtml = data.sourceTimestamps?.length
      ? `<div class="chat-timestamps">${data.sourceTimestamps.map(t => `<span class="chat-ts-badge clickable-ts" onclick="seekAudio(${parseTimestampToSeconds(t)})">📌 ${esc(t)}</span>`).join('')}</div>`
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

function formatChatAnswer(text) {
  // Convert markdown-like formatting to HTML
  return esc(text)
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
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
        'chat': loadChat
      };
      if (loaders[tab] && panel && !panel.dataset.loaded) {
        panel.dataset.loaded = '1';
        loaders[tab](classId);
      }
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
