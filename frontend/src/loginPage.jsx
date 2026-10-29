import { useState } from 'react';
import {
  ArrowLeft,
  Command,
  ShieldCheck,
  type
} from 'lucide-react';

type LoginPageProps = {
  path: string;
  navigate: (nextPath: string) => void;
};

export default function LoginPage({ path, navigate }: LoginPageProps) {
  const [message, setMessage] = useState('');
  const isSignIn = path === '/signin';

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
          onSubmit={(event) => {
            event.preventDefault();
            setMessage('Authentication is not connected yet. Your details were not submitted.');
          }}
        >
          <label className="account-field">
            Email address
            <input type="email" required placeholder="you@company.com" />
          </label>
          <label className="account-field">
            Password
            <input type="password" required minLength={8} placeholder="At least 8 characters" />
          </label>
          <button className="submit-button" type="submit">
            {isSignIn ? 'Sign in' : 'Create account'} <span className="button-arrow">→</span>
          </button>
          {message && <p className="auth-message" role="status">{message}</p>}
        </form>

        <p className="auth-switch">
          {isSignIn ? 'New to DevPilot?' : 'Already have an account?'}{' '}
          <button type="button" onClick={() => navigate(isSignIn ? '/signup' : '/signin')}>
            {isSignIn ? 'Sign up' : 'Sign in'}
          </button>
        </p>
        <p className="auth-unavailable"><ShieldCheck size={14} /> Account authentication will connect here.</p>
      </section>
    </div>
  );
}
