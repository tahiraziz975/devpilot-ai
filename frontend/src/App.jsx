import { useState } from 'react';
import { Activity, ArrowUpRight, Check, ChevronDown, CircleHelp, Command, FileText, Layers3, LoaderCircle, Sparkles, WandSparkles } from 'lucide-react';

const initialTitle = 'Unable to log in to my account';
const initialBody = 'When I click login with my username and password, it shows the same login page again.';

function formatLabel(label) {
  return String(label).replaceAll('_', ' ').replaceAll('-', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function App() {
  const [title, setTitle] = useState(initialTitle);
  const [body, setBody] = useState(initialBody);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function classifyIssue(event) {
    event.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const response = await fetch('/api/predictions', {
        method: 'POST',
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
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#home" aria-label="DevPilot AI home">
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
          <a href="#classify" className="nav-item active"><WandSparkles size={17} /> Classify issue <span className="nav-dot" /></a>
          <a href="#history" className="nav-item disabled" aria-disabled="true"><Layers3 size={17} /> History <span className="soon">SOON</span></a>
          <a href="#activity" className="nav-item disabled" aria-disabled="true"><Activity size={17} /> Activity <span className="soon">SOON</span></a>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-tip"><Sparkles size={16} /><p><strong>Better triage starts here.</strong><br />Give your team a head start on every issue.</p></div>
          <button className="help-link" type="button"><CircleHelp size={16} /> Help & feedback <ArrowUpRight size={14} /></button>
          <div className="profile"><span className="profile-avatar">JD</span><span><strong>Jamie Developer</strong><small>jamie@devpilot.ai</small></span><ChevronDown size={15} /></div>
        </div>
      </aside>

      <main className="main-content" id="home">
        <header className="topbar"><div className="breadcrumbs"><span>Workspace</span><span className="crumb-separator">/</span><strong>Classify issue</strong></div><div className="topbar-right"><span className="system-status"><i /> All systems operational</span><button className="icon-button" aria-label="Help"><CircleHelp size={18} /></button><span className="mini-avatar">JD</span></div></header>
        <div className="content-wrap" id="classify">
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
              {loading ? <div className="result-empty loading-state"><span className="loader-orbit"><LoaderCircle size={25} /></span><strong>Reading between the lines…</strong><span>Finding the best category for this issue.</span></div> : result ? <div className="result-data"><div className="result-overline"><span className="success-mark"><Check size={13} /></span> BEST MATCH</div><div className="prediction-card"><div><span className="prediction-label">PREDICTED LABEL</span><strong className="prediction-value">{formatLabel(result.label)}</strong></div><div className="confidence-ring" style={{ '--score': `${scorePercent}%` }}><span>{scorePercent}<small>%</small></span></div></div><div className="probability-heading"><span>All label probabilities</span><span>CONFIDENCE</span></div><div className="probability-list">{probabilities.map(([label, probability]) => { const score = Math.max(0, Math.min(100, Number(probability) * 100)); return <div className="probability-row" key={label}><span className="probability-label">{formatLabel(label)}</span><span className="progress-track"><span className="progress-fill" style={{ width: `${score}%` }} /></span><span className="probability-score">{Math.round(score)}%</span></div>; })}</div><div className="result-footnote"><Sparkles size={14} /> Use this as a helpful starting point for triage.</div></div> : <div className="result-empty"><div className="empty-illustration"><div className="illustration-orbit orbit-one" /><div className="illustration-orbit orbit-two" /><div className="illustration-core"><Sparkles size={22} /></div><span className="float-dot dot-one" /><span className="float-dot dot-two" /><span className="float-dot dot-three" /></div><strong>Ready when you are</strong><span>Submit an issue to see its predicted label<br />and confidence scores.</span><div className="empty-footnote"><span /> Predictions are powered by your ML classifier</div></div>}
            </section>
          </div>
          <footer className="page-footer"><span>DEVpilot AI <span className="footer-dot">·</span> ISSUE CLASSIFICATION</span><span>Built for clearer triage <span className="footer-heart">✳</span></span></footer>
        </div>
      </main>
    </div>
  );
}

export default App;
