import { useEffect, useRef, useState } from 'react';
import { Check, CircleAlert, CodeXml, LoaderCircle, LockKeyhole, Unplug } from 'lucide-react';
import githubImage from '../../images/github.png';
import { beginGitHubConnection, disconnectGitHub, getGitHubIntegration, getGitHubRepositories, setGitHubRepositoryAgentAccess } from '../../services/githubIntegrationService.js';

function GitHubIntegrationCard({ onSignIn }) {
  const [integration, setIntegration] = useState(null);
  const [authenticated, setAuthenticated] = useState(null);
  const [loading, setLoading] = useState(true);
  const [disconnecting, setDisconnecting] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [callbackResult, setCallbackResult] = useState(() => new URLSearchParams(window.location.search).get('github') || '');
  const [connecting, setConnecting] = useState(false);
  const [repositories, setRepositories] = useState([]);
  const [repositoriesLoading, setRepositoriesLoading] = useState(false);
  const [repositoriesError, setRepositoriesError] = useState('');
  const [savingRepositoryId, setSavingRepositoryId] = useState(null);
  const popupRef = useRef(null);
  const connectionTimeoutRef = useRef(null);
  const popupCheckRef = useRef(null);

  async function refreshStatus({ returnedFromGitHub = false, callback = callbackResult } = {}) {
    setLoading(true);
    setError('');
    try {
      const result = await getGitHubIntegration();
      setAuthenticated(result.authenticated);
      setIntegration(result.integration || null);
      if (result.authenticated && result.integration?.connected) {
        setRepositoriesLoading(true);
        setRepositoriesError('');
        try { setRepositories(await getGitHubRepositories()); }
        catch (repositoryError) { setRepositoriesError(repositoryError.message); }
        finally { setRepositoriesLoading(false); }
      } else {
        setRepositories([]);
      }
      if (result.authenticated && callback === 'error') {
        setError('GitHub could not be connected. No connection was confirmed. Please try again.');
      } else if (result.authenticated && callback === 'connected' && !result.integration?.connected) {
        setError('GitHub returned to DevPilot, but the backend has not confirmed a connection. Please check the app Setup URL and try again.');
      } else if (result.authenticated && callback === 'connected' && result.integration?.connected) {
        setNotice('GitHub connection confirmed by DevPilot.');
      } else if (returnedFromGitHub && result.authenticated && result.integration?.connected) {
        setNotice('GitHub connection confirmed by DevPilot.');
      } else if (returnedFromGitHub && result.authenticated) {
        setError('GitHub did not finish connecting to DevPilot. Check the app Setup URL and try again.');
      } else {
        setNotice('');
      }
    } catch {
      setError('We could not check your GitHub connection right now. Please refresh this page to try again.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const handlePopupMessage = (event) => {
      if (event.origin !== window.location.origin || event.source !== popupRef.current || event.data?.type !== 'devpilot-github-callback') return;
      clearTimeout(connectionTimeoutRef.current);
      clearInterval(popupCheckRef.current);
      popupRef.current = null;
      setConnecting(false);
      setCallbackResult(event.data.result);
      refreshStatus({ returnedFromGitHub: true, callback: event.data.result });
    };
    window.addEventListener('message', handlePopupMessage);
    return () => {
      window.removeEventListener('message', handlePopupMessage);
      clearTimeout(connectionTimeoutRef.current);
      clearInterval(popupCheckRef.current);
    };
  }, []);

  async function handleConnect(event) {
    event.preventDefault();
    if (connecting) return;
    // Open synchronously during the user gesture, before awaiting the backend.
    const popup = window.open('about:blank', 'devpilot-github-install', 'popup,width=900,height=760');
    if (!popup) {
      setError('Your browser blocked the GitHub window. Allow popups for this site, then try again.');
      return;
    }
    popupRef.current = popup;
    setConnecting(true);
    setError('');
    setNotice('');
    try {
      const installationUrl = await beginGitHubConnection();
      if (!installationUrl.startsWith('https://github.com/apps/')) {
        throw new Error('The backend returned an invalid GitHub installation URL.');
      }
      popup.location.assign(installationUrl);
      popupCheckRef.current = window.setInterval(() => {
        if (popup.closed && popupRef.current === popup) {
          clearInterval(popupCheckRef.current);
          clearTimeout(connectionTimeoutRef.current);
          popupRef.current = null;
          setConnecting(false);
          refreshStatus({ returnedFromGitHub: true });
        }
      }, 800);
      // The callback redirects the popup to /settings?github=connected|error.
      // Its same-origin page reports that result to this tab.
      connectionTimeoutRef.current = window.setTimeout(() => {
        if (popupRef.current === popup) {
          clearInterval(popupCheckRef.current);
          popupRef.current = null;
          setConnecting(false);
          setError('GitHub did not return a result. Check the popup and try connecting again.');
        }
      }, 10 * 60 * 1000);
    } catch (connectError) {
      clearInterval(popupCheckRef.current);
      clearTimeout(connectionTimeoutRef.current);
      popup.close();
      popupRef.current = null;
      setConnecting(false);
      setError(connectError.message || 'Could not start the GitHub connection. Please try again.');
    }
  }

  useEffect(() => { refreshStatus(); }, [callbackResult]);

  async function handleRepositoryAccess(repository, enabled) {
    setSavingRepositoryId(repository.id);
    setRepositoriesError('');
    try {
      const updated = await setGitHubRepositoryAgentAccess(repository.id, enabled);
      setRepositories((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (accessError) {
      setRepositoriesError(accessError.message || 'Could not update repository access.');
    } finally {
      setSavingRepositoryId(null);
    }
  }

  async function handleDisconnect() {
    setDisconnecting(true);
    setError('');
    setNotice('');
    try {
      await disconnectGitHub();
      setIntegration({ connected: false });
      setConfirmDisconnect(false);
      setNotice('GitHub has been disconnected from your DevPilot account.');
    } catch (disconnectError) {
      setError(disconnectError.message || 'Could not disconnect GitHub. Please try again.');
    } finally {
      setDisconnecting(false);
    }
  }

  const connected = integration?.connected === true;

  return (
    <section className="panel github-integration-panel" aria-labelledby="github-integration-title">
      <div className="panel-heading github-panel-heading">
        <div className="github-app-icon"><img src={githubImage} alt="" /></div>
        <div><h2 id="github-integration-title">GitHub</h2><p>Choose whether DevPilot can work with your repositories.</p></div>
        {!loading && authenticated && <span className={`github-status-pill ${connected ? 'is-connected' : ''}`}>
          <i /> {connected ? 'Connected' : 'Not connected'}
        </span>}
      </div>

      {loading ? <div className="github-loading" role="status"><LoaderCircle className="spin" size={17} /> Checking your GitHub connection…</div>
        : authenticated === false ? <div className="github-empty-state">
          <p>Sign in to DevPilot before connecting a GitHub account.</p>
          <button className="submit-button github-action-button" type="button" onClick={onSignIn}>Sign in to DevPilot</button>
        </div>
          : connected ? <div className="github-connected-content">
            <div className="github-account-card">
              <span className="github-account-avatar"><img src={githubImage} alt="" /></span>
              <span className="github-account-copy"><strong>{integration.username}</strong><small>{integration.accountType || 'GitHub account'} · Connected to DevPilot</small></span>
              <span className="github-confirmed"><Check size={14} /> Connected</span>
            </div>
            <div className="github-permission-row">
              <span className="github-permission-icon"><LockKeyhole size={15} /></span>
              <span><strong>Requested access</strong><small>{integration.permissions || 'Metadata: read-only · Contents: read-only'}</small></span>
            </div>
            <div className="github-repositories-panel">
              <div className="github-repositories-heading"><div><strong>Repository access</strong><p>GitHub grants this app read-only access. Enable only the repositories the AI agent may inspect. Requested file contents are sent to your configured AI provider to answer chat questions.</p></div><button type="button" onClick={() => refreshStatus()} disabled={repositoriesLoading}>{repositoriesLoading ? 'Refreshing…' : 'Refresh'}</button></div>
              {repositoriesLoading ? <div className="github-loading" role="status"><LoaderCircle className="spin" size={15} /> Loading repositories from GitHub…</div>
                : repositories.length ? <div className="github-repository-list">{repositories.map((repository) => <div className="github-repository-row" key={repository.id}>
                  <span className="github-repository-mark"><CodeXml size={15} /></span>
                  <span className="github-repository-details"><strong>{repository.fullName}</strong><small>{repository.privateRepository ? 'Private' : 'Public'} · GitHub App access: read-only</small></span>
                  <label className="github-repository-toggle"><input type="checkbox" checked={repository.agentEnabled} disabled={savingRepositoryId === repository.id} onChange={(event) => handleRepositoryAccess(repository, event.target.checked)} /><span>Agent access</span></label>
                </div>)}</div> : <p className="github-repository-empty">No repositories are currently available to this GitHub App.</p>}
              {repositoriesError && <div className="github-repository-error" role="alert">{repositoriesError} <button type="button" onClick={() => refreshStatus()}>Retry</button></div>}
            </div>
            {confirmDisconnect ? <div className="github-disconnect-confirm" role="group" aria-label="Confirm disconnect GitHub">
              <div><strong>Disconnect GitHub?</strong><p>DevPilot will stop using this installation. You can reconnect later.</p></div>
              <div className="github-confirm-actions"><button type="button" className="secondary-button" disabled={disconnecting} onClick={() => setConfirmDisconnect(false)}>Cancel</button><button type="button" className="danger-button" disabled={disconnecting} onClick={handleDisconnect}>{disconnecting ? <><LoaderCircle className="spin" size={14} /> Disconnecting…</> : 'Disconnect'}</button></div>
            </div> : <button type="button" className="github-disconnect-button" onClick={() => setConfirmDisconnect(true)}><Unplug size={15} /> Disconnect GitHub</button>}
          </div> : <div className="github-disconnected-content">
            <div className="github-privacy-note"><LockKeyhole size={16} /><span><strong>Read-only repository access</strong><small>DevPilot requests read-only metadata and contents permissions. You choose which repositories to include during GitHub installation.</small></span></div>
            <form onSubmit={handleConnect}>
              <button className="submit-button github-action-button" type="submit" disabled={connecting}>{connecting ? <><LoaderCircle className="spin" size={16} /> Waiting for GitHub…</> : <><img className="github-button-icon" src={githubImage} alt="" /> Connect GitHub</>}</button>
            </form>
          </div>}

      {(error || notice) && <div className={`github-feedback ${error ? 'is-error' : 'is-success'}`} role={error ? 'alert' : 'status'}>
        {error ? <CircleAlert size={15} /> : <Check size={15} />}<span>{error || notice}</span>
        {!loading && authenticated && error && <button type="button" onClick={refreshStatus}>Retry</button>}
      </div>}
      {callbackResult === 'error' && <span className="sr-only">The GitHub callback did not complete.</span>}
    </section>
  );
}

export default GitHubIntegrationCard;
