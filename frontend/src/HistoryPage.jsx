import { useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowRight, Clock3, FileText, LoaderCircle, RefreshCw, Search, Sparkles } from 'lucide-react';

function formatLabel(label) {
  return String(label || 'Unknown').replaceAll('_', ' ').replaceAll('-', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Date unavailable'
    : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export default function HistoryPage({ navigate }) {
  const [items, setItems] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function loadHistory() {
    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/predictions/history', { credentials: 'include' });
      const payload = await response.json().catch(() => []);
      if (!response.ok) {
        const authError = response.status === 401 || response.status === 403;
        throw new Error(authError
          ? 'Sign in to view your prediction history.'
          : payload.detail || payload.message || `Could not load history (${response.status}).`);
      }
      if (!Array.isArray(payload)) throw new Error('The history service returned an unexpected response.');
      setItems(payload);
    } catch (requestError) {
      setError(requestError.message || 'Could not reach the history service. Make sure Spring Boot is running.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadHistory(); }, []);

  const filteredItems = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return items;
    return items.filter((item) => `${item.title} ${item.body} ${item.label}`.toLowerCase().includes(needle));
  }, [items, query]);

  return (
    <div className="content-wrap history-content">
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-icon"><Clock3 size={14} /></span> YOUR WORKSPACE</div>
          <h1>Prediction history</h1>
          <p className="subtitle">Review issues you’ve classified and their predicted categories.</p>
        </div>
        <button className="history-refresh" type="button" onClick={loadHistory} disabled={loading}>
          {loading ? <LoaderCircle className="spin" size={15} /> : <RefreshCw size={15} />} Refresh
        </button>
      </div>

      {error ? <section className="panel-history history-state-panel" role="alert">
        <span className="history-state-icon error-state-icon"><AlertCircle size={19} /></span>
        <h2>{error.includes('Sign in') ? 'Sign in required' : 'History is unavailable'}</h2>
        <p>{error}</p>
        {error.includes('Sign in')
          ? <button className="submit-button history-state-action" type="button" onClick={() => navigate('/signin')}>Sign in <ArrowRight size={15} /></button>
          : <button className="secondary-action" type="button" onClick={loadHistory}>Try again</button>}
      </section> : loading ? <section className="panel-history history-state-panel">
        <span className="loader-orbit"><LoaderCircle className="spin" size={23} /></span>
        <h2>Loading your history…</h2>
        <p>Fetching your saved predictions.</p>
      </section> : items.length === 0 ? <section className="panel-history history-state-panel">
        <span className="history-state-icon"><FileText size={19} /></span>
        <h2>No predictions yet</h2>
        <p>Classified issues will appear here when you’re signed in.</p>
        <button className="submit-button history-state-action" type="button" onClick={() => navigate('/')}>Classify an issue <ArrowRight size={15} /></button>
      </section> : <>
        <div className="history-toolbar">
          <span>{filteredItems.length} {filteredItems.length === 1 ? 'prediction' : 'predictions'}</span>
          <label className="history-search"><Search size={15} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search your history" /></label>
        </div>
        {filteredItems.length === 0 ? <section className="panel-history history-state-panel compact-state"><h2>No matches</h2><p>Try a different title, description, or category.</p></section> : <div className="history-list">
          {filteredItems.map((item) => {
            const rankedProbabilities = Object.entries(item.probabilities || {})
              .sort(([, left], [, right]) => Number(right) - Number(left));
            const topScore = rankedProbabilities.length ? Number(rankedProbabilities[0][1]) : 0;
            return <article className="panel-history history-card" key={item.id}>
              <div className="history-card-main">
                <div className="history-card-icon"><FileText size={16} /></div>
                <div className="history-issue-copy"><h2>{item.title || 'Untitled issue'}</h2><p>{item.body || 'No description provided.'}</p></div>
                <div className="history-label"><span>BEST MATCH</span><strong>{formatLabel(item.label)}</strong></div>
                <div className="history-score"><strong>{Math.round(topScore * 100)}%</strong><span>confidence</span></div>
              </div>
              {rankedProbabilities.length > 0 && <div className="history-probabilities" aria-label="Prediction confidence scores">
                {rankedProbabilities.slice(0, 3).map(([label, probability]) => {
                  const score = Math.max(0, Math.min(100, Number(probability) * 100));
                  return <div className="history-probability" key={label}>
                    <span>{formatLabel(label)}</span><span className="history-probability-track"><span style={{ width: `${score}%` }} /></span><strong>{Math.round(score)}%</strong>
                  </div>;
                })}
              </div>}
              <div className="history-card-meta"><span><Clock3 size={13} /> {formatDate(item.createdAt)}</span><span><Sparkles size={13} /> ML classifier</span></div>
            </article>;
          })}
        </div>}
      </>}
    </div>
  );
}
