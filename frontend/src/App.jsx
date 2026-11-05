import { useEffect, useState } from 'react';
import { Activity, ArrowLeft, ArrowUpRight, Check, ChevronDown, CircleHelp, Command, FileText, Layers3, LoaderCircle, LogIn, LogOut, Moon, Save, Settings, Sparkles, Sun, UserRound, WandSparkles } from 'lucide-react';
import LoginPage from "./loginPage.jsx";
import HistoryPage from './HistoryPage.jsx';

const initialTitle = 'Unable to log in to my account';
const initialBody = 'When I click login with my username and password, it shows the same login page again.';

function formatLabel(label) {
  return String(label).replaceAll('_', ' ').replaceAll('-', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [authUser, setAuthUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('devpilot-auth-user'));
    } catch {
      return null;
    }
  });
  const [profileSaved, setProfileSaved] = useState(false);
  const [settings, setSettings] = useState({ emailUpdates: true, compactResults: false });
  const [theme, setTheme] = useState(() => localStorage.getItem('devpilot-theme') === 'light' ? 'light' : 'dark');
  const [themeSaveError, setThemeSaveError] = useState('');
  const [title, setTitle] = useState(initialTitle);
  const [body, setBody] = useState(initialBody);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const syncPath = () => setPath(window.location.pathname);
    window.addEventListener('popstate', syncPath);
    fetch('/api/auth/me', { credentials: 'include' })
      .then(async (response) => {
        if (response.status === 401 || response.status === 403) {
          localStorage.removeItem('devpilot-auth-user');
          setAuthUser(null);
          return;
        }
        if (!response.ok) return;
        const user = await response.json();
        setAuthUser(user);
        localStorage.setItem('devpilot-auth-user', JSON.stringify(user));
      })
      .catch(() => {});
    return () => window.removeEventListener('popstate', syncPath);
  }, []);

  useEffect(() => {
    if (!authUser?.id) return;
    fetch('/api/settings', { credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) return;
        const savedSettings = await response.json();
        const savedTheme = savedSettings.theme === 'light' ? 'light' : 'dark';
        setTheme(savedTheme);
        localStorage.setItem('devpilot-theme', savedTheme);
        setSettings((current) => ({ ...current, emailUpdates: Boolean(savedSettings.emailNotifications) }));
      })
      .catch(() => {});
  }, [authUser?.id]);

  function navigate(nextPath) {
    window.history.pushState({}, '', nextPath);
    setPath(nextPath);
    setAccountMenuOpen(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function saveProfile(event) {
    event.preventDefault();
    localStorage.setItem('devpilot-auth-user', JSON.stringify(authUser));
    setProfileSaved(true);
    window.setTimeout(() => setProfileSaved(false), 2200);
  }

  async function handleAuthenticated() {
    const response = await fetch('/api/auth/me', { credentials: 'include' });
    const user = await response.json().catch(() => ({}));
    if (!response.ok || !user.id) {
      throw new Error('The backend did not establish a sign-in session. Restart Spring Boot, then sign in again.');
    }
    setAuthUser(user);
    localStorage.setItem('devpilot-auth-user', JSON.stringify(user));
    navigate('/');
  }

  function handleSignOut() {
    setAccountError('');
    fetch('/api/auth/signout', { method: 'POST', credentials: 'include' })
      .then((response) => {
        if (!response.ok) throw new Error('Could not sign out. Please try again.');
        localStorage.removeItem('devpilot-auth-user');
        setAuthUser(null);
        navigate('/');
      })
      .catch((signoutError) => setAccountError(signoutError.message || 'Could not sign out.'));
  }

  async function toggleTheme() {
    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    setTheme(nextTheme);
    localStorage.setItem('devpilot-theme', nextTheme);
    setThemeSaveError('');
    if (!authUser) return;

    try {
      const response = await fetch('/api/settings', {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ theme: nextTheme }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.detail || 'Could not save your theme preference.');
      const savedTheme = payload.theme === 'light' ? 'light' : 'dark';
      setTheme(savedTheme);
      localStorage.setItem('devpilot-theme', savedTheme);
    } catch {
      setThemeSaveError('Theme changed for now, but your preference could not be saved to your account.');
    }
  }

  const displayName = authUser?.fullName || 'Guest';
  const displayEmail = authUser?.email || 'Sign in to your account';
  const initials = authUser?.fullName?.split(/\s+/).map((part) => part[0]).slice(0, 2).join('').toUpperCase() || 'G';

  const currentPage = path === '/history' ? 'History' : path === '/profile' ? 'Profile' : path === '/settings' ? 'Settings' : path === '/signin' ? 'Sign in' : path === '/signup' ? 'Create account' : 'Classify issue';
  const authPage = path === '/signin' || path === '/signup' || (path === '/profile' && !authUser);

  async function classifyIssue(event) {
    event.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const response = await fetch('/api/predictions', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: title.trim(), body: body.trim() }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload.message || payload.error || `Prediction failed (${response.status}). Please try again.`);
      }
      if (!payload.label || !payload.probabilities || typeof payload.probabilities !== 'object') {
        throw new Error('The prediction service returned an unexpected response. Please check the backend response format.');
      }
      setResult(payload);
    } catch (requestError) {
      setError(requestError.message || 'Could not reach the prediction service. Make sure Spring Boot is running and try again.');
    } finally {
      setLoading(false);
    }
  }

  const probabilities = result
    ? Object.entries(result.probabilities).sort(([, a], [, b]) => Number(b) - Number(a))
    : [];
  const bestScore = probabilities.length ? Number(probabilities[0][1]) : 0;
  const scorePercent = Math.round(bestScore * 100);

  return (
    <div className={`app-shell ${authPage ? 'is-auth' : ''} ${settings.compactResults ? 'compact-results' : ''} ${theme === 'light' ? 'light-theme' : ''}`}>
      <aside className="sidebar">
        <a className="brand" href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }} aria-label="DevPilot AI home">
          <span className="brand-mark"><Command size={19} strokeWidth={2.6} /></span>
          <span>devpilot<span className="brand-ai">.ai</span></span>
        </a>
        <div className="workspace-switch">
          <span className="workspace-avatar">D</span>
          <span className="workspace-copy"><strong>DevPilot workspace</strong><small>Free plan</small></span>
          <ChevronDown size={15} />
        </div>
        <div className="nav-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="Workspace">
          <a href="/" onClick={(event) => { event.preventDefault(); navigate('/'); }} className={`nav-item ${path === '/' ? 'active' : ''}`}><WandSparkles size={17} /> Classify issue {path === '/' && <span className="nav-dot" />}</a>
          <a href="/history" onClick={(event) => { event.preventDefault(); navigate('/history'); }} className={`nav-item ${path === '/history' ? 'active' : ''}`}><Layers3 size={17} /> History {path === '/history' && <span className="nav-dot" />}</a>
          <a href="#activity" className="nav-item disabled" aria-disabled="true"><Activity size={17} /> Activity <span className="soon">SOON</span></a>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-tip"><Sparkles size={16} /><p><strong>Better triage starts here.</strong><br />Give your team a head start on every issue.</p></div>
          <button className="help-link" type="button"><CircleHelp size={16} /> Help & feedback <ArrowUpRight size={14} /></button>
          <div className="account-menu-wrap">
            {accountMenuOpen && <><button className="menu-dismiss" aria-label="Close account menu" onClick={() => setAccountMenuOpen(false)} /><div className="account-menu" role="menu" aria-label="Account menu">
              {authUser && <><div className="account-menu-heading"><span className="profile-avatar">{initials}</span><span><strong>{displayName}</strong><small>{displayEmail}</small></span></div>
              <div className="account-menu-divider" />
              <button type="button" role="menuitem" onClick={() => navigate('/profile')}><UserRound size={15} /> Profile</button>
              <button type="button" role="menuitem" onClick={() => navigate('/settings')}><Settings size={15} /> Settings</button>
              <div className="account-menu-divider" /></>}
              {authUser
                ? <button type="button" role="menuitem" onClick={handleSignOut}><LogOut size={15} /> Sign out</button>
                : <button type="button" role="menuitem" onClick={() => navigate('/signin')}><LogIn size={15} /> Sign in</button>}
              {accountError && <p className="account-menu-error" role="alert">{accountError}</p>}
            </div></>}
            <button className="profile" type="button" aria-haspopup="menu" aria-expanded={accountMenuOpen} onClick={() => setAccountMenuOpen((open) => !open)}><span className="profile-avatar">{initials}</span><span><strong>{displayName}</strong><small>{displayEmail}</small></span><ChevronDown size={15} /></button>
          </div>
        </div>
      </aside>

      <main className="main-content" id="home">
        <header className="topbar"><div className="breadcrumbs"><span>Workspace</span><span className="crumb-separator">/</span><strong>{currentPage}</strong></div><div className="topbar-right"><span className="system-status"><i /> All systems operational</span><button className={`theme-switch ${theme}`} type="button" role="switch" aria-checked={theme === 'light'} aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} title={themeSaveError || `Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} onClick={toggleTheme}><span className="theme-switch-icon"><Sun size={16} /></span><span className="theme-switch-track"><span className="theme-switch-thumb" /></span><span className="theme-switch-icon"><Moon size={15} /></span></button><button className="icon-button" aria-label="Help"><CircleHelp size={18} /></button><button className="topbar-account" type="button" aria-label="Open account menu" onClick={() => setAccountMenuOpen((open) => !open)}><span className="mini-avatar">{initials}</span></button></div></header>
        {!authPage && <nav className="mobile-tab-bar" aria-label="Primary navigation">
          <a href="/" aria-current={path === '/' ? 'page' : undefined} className={`mobile-tab ${path === '/' ? 'active' : ''}`} onClick={(event) => { event.preventDefault(); navigate('/'); }}><WandSparkles size={18} /><span>Classify</span></a>
          <a href="/history" aria-current={path === '/history' ? 'page' : undefined} className={`mobile-tab ${path === '/history' ? 'active' : ''}`} onClick={(event) => { event.preventDefault(); navigate('/history'); }}><Layers3 size={18} /><span>History</span></a>
        </nav>}
        {path === '/history' ? <HistoryPage navigate={navigate} /> : path === '/profile' && !authUser ? <LoginPage path="/signin" navigate={navigate} onAuthenticated={handleAuthenticated} /> : path === '/profile' ? <div className="content-wrap account-content">
          <button className="back-link" type="button" onClick={() => navigate('/')}><ArrowLeft size={15} /> Back to workspace</button>
          <div className="page-heading account-page-heading"><div><div className="eyebrow"><span className="eyebrow-icon"><UserRound size={14} /></span> ACCOUNT</div><h1>Your profile</h1><p className="subtitle">Manage the details associated with your DevPilot account.</p></div></div>
          <section className="panel account-panel"><div className="panel-heading"><div className="step-icon"><UserRound size={17} /></div><div><h2>Personal details</h2><p>Update how your profile appears in DevPilot.</p></div></div>
            <form className="account-form" onSubmit={saveProfile}>
              <div className="profile-hero"><span className="large-profile-avatar">{initials}</span><div><strong>{authUser.fullName || 'Your name'}</strong><span>DevPilot account</span></div></div>
              <label className="account-field">Full name<input value={authUser.fullName} onChange={(event) => setAuthUser({ ...authUser, fullName: event.target.value })} required maxLength={100} /></label>
              <label className="account-field">Email address<input type="email" value={authUser.email} onChange={(event) => setAuthUser({ ...authUser, email: event.target.value })} required maxLength={255} /></label>
              <label className="account-field">Profile ID<input value={authUser.profileId} readOnly /><small>Your unique profile identifier.</small></label>
              <div className="account-actions"><span className="save-confirmation" aria-live="polite">{profileSaved ? 'Profile saved on this device.' : ''}</span><button className="submit-button save-button" type="submit"><Save size={15} /> Save changes</button></div>
            </form>
          </section>
        </div> : path === '/settings' ? <div className="content-wrap account-content">
          <button className="back-link" type="button" onClick={() => navigate('/')}><ArrowLeft size={15} /> Back to workspace</button>
          <div className="page-heading account-page-heading"><div><div className="eyebrow"><span className="eyebrow-icon"><Settings size={14} /></span> PREFERENCES</div><h1>Settings</h1><p className="subtitle">Choose how DevPilot works for you.</p></div></div>
          <section className="panel account-panel settings-panel"><div className="panel-heading"><div className="step-icon"><Settings size={17} /></div><div><h2>Workspace preferences</h2><p>These preferences are saved for this session.</p></div></div>
            <label className="setting-row"><span><strong>Email updates</strong><small>Receive product and workspace update emails.</small></span><input type="checkbox" checked={settings.emailUpdates} onChange={(event) => setSettings({ ...settings, emailUpdates: event.target.checked })} /></label>
            <label className="setting-row"><span><strong>Compact results</strong><small>Use a denser layout for classification results.</small></span><input type="checkbox" checked={settings.compactResults} onChange={(event) => setSettings({ ...settings, compactResults: event.target.checked })} /></label>
            <div className="settings-note"><Sparkles size={15} /> More account and notification settings will be available as the workspace grows.</div>
          </section>
        </div> : authPage ? <LoginPage path={path} navigate={navigate} onAuthenticated={handleAuthenticated} /> : <div className="content-wrap" id="classify">
          <div className="page-heading"><div><div className="eyebrow"><span className="eyebrow-icon"><Sparkles size={14} /></span> AI ISSUE TRIAGE</div><h1>Make sense of every issue.</h1><p className="subtitle">Turn a bug report into a clear starting point for your team.</p></div><div className="model-chip"><span className="model-pulse" /> CLASSIFIER <span className="chip-divider" /> v1.0</div></div>

          <div className="work-grid">
            <section className="panel form-panel">
              <div className="panel-heading"><div className="step-icon"><FileText size={17} /></div><div><h2>Issue details</h2><p>Share the report you’d like to classify.</p></div><span className="step-count">01</span></div>
              <form onSubmit={classifyIssue}>
                <label className="field-label" htmlFor="issue-title">Issue title <span>REQUIRED</span></label>
                <input id="issue-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={10000} required placeholder="e.g. Unable to log in to my account" />
                <div className="field-label body-label"><label htmlFor="issue-body">Description</label><span>REQUIRED</span></div>
                <textarea id="issue-body" value={body} onChange={(event) => setBody(event.target.value)} maxLength={500000} required rows={6} placeholder="Describe what happened, what you expected, and any steps to reproduce…" />
                <div className="input-hint">Include steps to reproduce for a more useful prediction.</div>
                {error && <div className="error-box" role="alert"><span className="error-mark">!</span><span><strong>We couldn’t classify this issue</strong><br />{error}</span></div>}
                <button className="submit-button" type="submit" disabled={loading || !title.trim() || !body.trim()}>{loading ? <><LoaderCircle className="spin" size={17} /> Analyzing issue…</> : <><Sparkles size={16} /> Classify issue <span className="button-arrow">→</span></>}</button>
                <div className="privacy-note"><span className="privacy-dot" /> Your issue is only used to generate this prediction.</div>
              </form>
            </section>

            <section className={`panel result-panel ${result ? 'has-result' : ''}`} aria-live="polite">
              <div className="panel-heading result-heading"><div className="step-icon result-step"><Activity size={17} /></div><div><h2>Classification</h2><p>{result ? 'Your issue has been analyzed.' : 'Your prediction will appear here.'}</p></div><span className="step-count">02</span></div>
              {loading ? <div className="result-empty loading-state"><span className="loader-orbit"><LoaderCircle size={25} /></span><strong>Reading between the lines…</strong><span>Finding the best category for this issue.</span></div> : result ? <div className="result-data"><div className="result-overline"><span className="success-mark"><Check size={13} /></span> BEST MATCH</div><div className="prediction-card"><div><span className="prediction-label">PREDICTED LABEL</span><strong className="prediction-value">{formatLabel(result.label)}</strong></div><div className="confidence-ring" style={{ '--score': `${scorePercent}%` }}><span>{scorePercent}<small>%</small></span></div></div><div className="probability-heading"><span>All label probabilities</span><span>CONFIDENCE</span></div><div className="probability-list">{probabilities.map(([label, probability]) => { const score = Math.max(0, Math.min(100, Number(probability) * 100)); return <div className="probability-row" key={label}><span className="probability-label">{formatLabel(label)}</span><span className="progress-track"><span className="progress-fill" style={{ width: `${score}%` }} /></span><span className="probability-score">{Math.round(score)}%</span></div>; })}</div><div className="result-footnote"><Sparkles size={14} /> {authUser ? 'Use this as a helpful starting point for triage.' : <>Sign in to save predictions to your history. <button type="button" className="history-signin-link" onClick={() => navigate('/signin')}>Sign in</button></>}</div></div> : <div className="result-empty"><div className="empty-illustration"><div className="illustration-orbit orbit-one" /><div className="illustration-orbit orbit-two" /><div className="illustration-core"><Sparkles size={22} /></div><span className="float-dot dot-one" /><span className="float-dot dot-two" /><span className="float-dot dot-three" /></div><strong>Ready when you are</strong><span>Submit an issue to see its predicted label<br />and confidence scores.</span><div className="empty-footnote"><span /> Predictions are powered by your ML classifier</div></div>}
            </section>
          </div>
          <footer className="page-footer"><span>DEVpilot AI <span className="footer-dot">·</span> ISSUE CLASSIFICATION</span><span>Built for clearer triage <span className="footer-heart">✳</span></span></footer>
        </div>}
      </main>
    </div>
  );
}

export default App;
