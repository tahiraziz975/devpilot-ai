import { useState } from 'react';
import {
  ArrowLeft,
  Command,
  ShieldCheck,
} from 'lucide-react';

export default function LoginPage({ path, navigate, onAuthenticated }) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const isSignIn = path === '/signin';

  async function submitAuth(event) {
    event.preventDefault();
    setMessage('');
    setLoading(true);
    try {
      const response = await fetch(`/api/auth/${isSignIn ? 'signin' : 'signup'}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(isSignIn ? { email: email.trim(), password } : {
          fullName: fullName.trim(),
          email: email.trim(),
          password,
        }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload.detail || payload.message || payload.error || `Request failed (${response.status}).`);
      }
      if (!payload.id || !payload.profileId || !payload.fullName || !payload.email) {
        throw new Error('The authentication service returned an unexpected response.');
      }
      await onAuthenticated(payload);
    } catch (requestError) {
      setMessage(requestError.message || 'Could not reach the authentication service. Make sure Spring Boot is running.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="panel auth-panel">
        <button className="back-link" type="button" onClick={() => navigate('/')}>
          <ArrowLeft size={15} /> Back to workspace
        </button>
        <div className="auth-brand">
          <span className="brand-mark"><Command size={19} /></span>
          <span>devpilot<span className="brand-ai">.ai</span></span>
        </div>
        <div className="eyebrow">ACCOUNT ACCESS</div>
        <h1>{isSignIn ? 'Welcome back.' : 'Create your account.'}</h1>
        <p className="subtitle">
          {isSignIn
            ? 'Sign in to continue to your DevPilot workspace.'
            : 'Get started with your DevPilot workspace.'}
        </p>

        <form
          className="account-form auth-form"
          onSubmit={submitAuth}
        >
          {!isSignIn && <label className="account-field">
            Full name
            <input value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={100} placeholder="Your name" autoComplete="name" />
          </label>}
          <label className="account-field">
            Email address
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={255} placeholder="you@company.com" autoComplete="email" />
          </label>
          <label className="account-field">
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} maxLength={72} placeholder="At least 8 characters" autoComplete={isSignIn ? 'current-password' : 'new-password'} />
          </label>
          <button className="submit-button" type="submit" disabled={loading}>
            {loading ? 'Please wait…' : isSignIn ? 'Sign in' : 'Create account'} {!loading && <span className="button-arrow">→</span>}
          </button>
          {message && <p className="auth-message" role="status">{message}</p>}
        </form>

        <p className="auth-switch">
          {isSignIn ? 'New to DevPilot?' : 'Already have an account?'}{' '}
          <button type="button" onClick={() => navigate(isSignIn ? '/signup' : '/signin')}>
            {isSignIn ? 'Sign up' : 'Sign in'}
          </button>
        </p>
        <p className="auth-unavailable"><ShieldCheck size={14} /> Your account details are verified by DevPilot.</p>
      </section>
    </div>
  );
}
