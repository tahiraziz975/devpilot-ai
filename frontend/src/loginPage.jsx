import { useEffect, useState } from 'react';
import {
  Eye,
  EyeOff,
  LockKeyhole,
  LogIn,
  Mail,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';

export default function LoginPage({ path, navigate, onAuthenticated }) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [googleOAuthEnabled, setGoogleOAuthEnabled] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const isSignIn = path === '/signin';

  useEffect(() => {
    fetch('/api/auth/providers', { credentials: 'include' })
      .then((response) => response.ok ? response.json() : {})
      .then((providers) => setGoogleOAuthEnabled(Boolean(providers.google)))
      .catch(() => setGoogleOAuthEnabled(false));

    const oauthError = new URLSearchParams(window.location.search).get('oauthError');
    if (oauthError === 'unverified_email') {
      setMessage('Google could not confirm this email address. Please try an account with a verified email.');
    } else if (oauthError) {
      setMessage('Google sign-in did not finish. Please try again or sign in with your email and password.');
    }
  }, []);

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
      <div className="auth-art" aria-hidden="true" />
      <section className="auth-panel" aria-labelledby="auth-title">
        <div className="auth-panel-content">
          <div className="auth-pill"><Sparkles size={17} fill="currentColor" /> Built with Machine Learning</div>
          <header className="auth-heading">
            <h1 id="auth-title">{isSignIn ? 'Welcome Back!' : 'Create Account'}</h1>
            <p>{isSignIn ? 'Sign in to continue to DevPilot-AI' : 'Get started with your DevPilot-AI workspace'}</p>
          </header>

          {googleOAuthEnabled && <>
            <a className="google-auth-button" href="/oauth2/authorization/google">
              <svg aria-hidden="true" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.91c-.58 2.96-2.26 5.48-4.74 7.18l7.7 5.98c4.5-4.16 7.11-10.29 7.11-17.63Z"/><path fill="#FBBC05" d="M10.53 28.59A14.4 14.4 0 0 1 9.75 24c0-1.59.27-3.13.76-4.59l-7.98-6.19A23.9 23.9 0 0 0 0 24c0 3.87.93 7.53 2.56 10.78l7.97-6.19Z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.9-5.82l-7.7-5.98c-2.14 1.44-4.88 2.3-8.2 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48Z"/></svg>
              Continue with Google
            </a>
            <div className="auth-divider"><span />or continue with email<span /></div>
          </>}

          <form className="account-form auth-form" onSubmit={submitAuth}>
            {!isSignIn && <label className="auth-input-wrap">
              <span className="visually-hidden">Full name</span>
              <input value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={100} placeholder="Full Name" autoComplete="name" />
            </label>}
            <label className="auth-input-wrap">
              <Mail size={22} aria-hidden="true" />
              <span className="visually-hidden">Email address</span>
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={255} placeholder="Email Address" autoComplete="email" />
            </label>
            <label className="auth-input-wrap">
              <LockKeyhole size={22} aria-hidden="true" />
              <span className="visually-hidden">Password</span>
              <input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} maxLength={72} placeholder="Password" autoComplete={isSignIn ? 'current-password' : 'new-password'} />
              <button className="password-toggle" type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword((shown) => !shown)}>{showPassword ? <EyeOff size={20} /> : <Eye size={20} />}</button>
            </label>

            {isSignIn && <div className="auth-options">
              <label className="remember-option"><input type="checkbox" /> <span>Remember me</span></label>
              <button type="button" className="auth-text-link" onClick={() => setMessage('Contact your administrator to reset your password.')}>Forgot Password?</button>
            </div>}

            <button className="auth-submit" type="submit" disabled={loading}>
              {loading ? 'Please wait…' : <><LogIn size={21} /> {isSignIn ? 'Sign In' : 'Create Account'}</>}
            </button>
            {message && <p className="auth-message" role="status">{message}</p>}
          </form>

          {!googleOAuthEnabled && isSignIn && <>
            <div className="auth-divider"><span />or<span /></div>
            <button className="github-button" type="button" disabled title="GitHub sign-in is not connected">Continue with GitHub</button>
          </>}

          <p className="auth-switch">
            {isSignIn ? "Don't have an account?" : 'Already have an account?'}{' '}
            <button type="button" onClick={() => navigate(isSignIn ? '/signup' : '/signin')}>
              {isSignIn ? 'Sign Up' : 'Sign In'}
            </button>
          </p>
          <p className="auth-unavailable"><ShieldCheck size={14} /> Your account details are verified by DevPilot.</p>
        </div>
        </section>
    </div>
  );
}
