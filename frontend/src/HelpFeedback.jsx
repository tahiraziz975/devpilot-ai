import { useState } from 'react';
import {
  ArrowRight, BookOpen, Bot, Bug, ChartNoAxesColumnIncreasing, Check,
  CircleHelp, Layers3, Heart, Lightbulb, Mail, MessageCircle, Send,
  ShieldCheck, Sparkles, TrendingUp, WandSparkles,
} from 'lucide-react';
import './help-feedback.css';

const categories = [
  { id: 'bug', label: 'Bug Report', icon: Bug, color: 'violet' },
  { id: 'feature', label: 'Feature Request', icon: Lightbulb, color: 'amber' },
  { id: 'improvement', label: 'Improvement', icon: TrendingUp, color: 'teal' },
  { id: 'general', label: 'General Feedback', icon: MessageCircle, color: 'purple' },
];

const categoryPrompts = {
  bug: 'What happened? Include the steps that led to the issue…',
  feature: 'What would you like DevPilot-AI to help you do?',
  improvement: 'What could work better, and how would you improve it?',
  general: 'Tell us what you think…',
};

const supportEmail = (import.meta.env.VITE_SUPPORT_EMAIL || '').trim();

function HelpFeedback() {
  const [category, setCategory] = useState('bug');
  const [description, setDescription] = useState('');
  const [details, setDetails] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [notice, setNotice] = useState('');

  function submitFeedback(event) {
    event.preventDefault();
    setNotice('');
    // Frontend-only submission placeholder. Replace this isolated handler with the feedback API when one is available.
    setSubmitted(true);
    setDescription('');
    setDetails('');
  }

  function changeCategory(nextCategory) {
    setCategory(nextCategory);
    setSubmitted(false);
    setNotice('');
  }

  return (
    <div className="content-wrap help-feedback-page">
      <div className="hf-hero">
        <div className="hf-hero-icon"><CircleHelp size={34} /></div>
        <div className="hf-hero-copy"><div className="hf-eyebrow"><Sparkles size={14} /> DEVpilot AI SUPPORT</div><h1>Help &amp; Feedback</h1><p>We’re here to help you get the most out of DevPilot-AI.</p></div>
        <div className="hf-hero-art" aria-hidden="true"><span className="hf-orb"/><span className="hf-chat-bubble"><MessageCircle size={24}/></span><span className="hf-robot"><Bot size={58}/></span><span className="hf-question">?</span></div>
      </div>

      <div className="hf-shortcuts">
        <a className="hf-shortcut hf-shortcut-blue" href="#about-devpilot"><span className="hf-shortcut-icon"><BookOpen size={35}/></span><span><strong>About DevPilot-AI</strong><small>Learn about our platform and its capabilities.</small><span className="hf-pill">Learn More <ArrowRight size={15}/></span></span></a>
        <a className="hf-shortcut hf-shortcut-purple" href="#share-feedback"><span className="hf-shortcut-icon"><Heart size={35}/></span><span><strong>Share Feedback</strong><small>Report issues or suggest improvements.</small><span className="hf-pill">Give Feedback <ArrowRight size={15}/></span></span></a>
      </div>

      <div className="hf-columns">
        <section className="hf-card hf-about-card" id="about-devpilot">
          <div className="hf-card-title"><span className="hf-title-icon info"><Sparkles size={19}/></span><h2>About DevPilot-AI</h2></div>
          <h3>Your AI-powered development companion.</h3>
          <p className="hf-intro">DevPilot-AI helps developers understand, classify, and investigate software issues using Machine Learning and AI-powered repository assistance.</p>
          <div className="hf-capabilities">
            <article><span className="hf-cap-icon lilac"><WandSparkles size={22}/></span><div><strong>Intelligent Issue Classification</strong><p>Automatically classify issues into Bug, Feature, Question and Documentation.</p></div></article>
            <article><span className="hf-cap-icon blue"><Layers3 size={22}/></span><div><strong>GitHub Integration</strong><p>Connect your repositories and let DevPilot-AI understand your project.</p></div></article>
            <article><span className="hf-cap-icon blue"><Bot size={22}/></span><div><strong>AI Development Assistant</strong><p>Ask questions, get insights, and explore your codebase.</p></div></article>
            <article><span className="hf-cap-icon lilac"><ChartNoAxesColumnIncreasing size={22}/></span><div><strong>Issue Management</strong><p>Organize and track classified issues efficiently.</p></div></article>
          </div>
          <div className="hf-readonly"><ShieldCheck size={17}/><span>GitHub AI assistant currently provides <strong>read-only</strong> repository access.</span></div>
        </section>

        <section className="hf-card hf-feedback-card" id="share-feedback">
          <div className="hf-card-title"><span className="hf-title-icon feedback"><MessageCircle size={19}/></span><h2>Share Your Feedback</h2></div>
          <p className="hf-feedback-lead">Your feedback helps us improve DevPilot-AI.</p>
          <form onSubmit={submitFeedback}>
            <fieldset className="hf-category-fieldset"><legend className="visually-hidden">Choose a feedback category</legend><div className="hf-categories">{categories.map(({ id, label, icon: Icon, color }) => <button type="button" key={id} onClick={() => changeCategory(id)} className={`hf-category ${category === id ? 'selected' : ''}`} aria-pressed={category === id}><Icon className={color} size={22}/><span>{label}</span>{category === id && <Check className="hf-selected-check" size={15}/>}</button>)}</div></fieldset>
            {category === 'bug' && <label className="hf-label" htmlFor="hf-subject">Issue summary <span>(optional)</span><input id="hf-subject" value={details} onChange={(event) => setDetails(event.target.value)} maxLength={120} placeholder="e.g. Classification results don’t load" /></label>}
            {category === 'feature' && <label className="hf-label" htmlFor="hf-details">Feature name <span>(optional)</span><input id="hf-details" value={details} onChange={(event) => setDetails(event.target.value)} maxLength={120} placeholder="What capability would you like?" /></label>}
            {category === 'improvement' && <label className="hf-label" htmlFor="hf-details">Area to improve <span>(optional)</span><input id="hf-details" value={details} onChange={(event) => setDetails(event.target.value)} maxLength={120} placeholder="Which feature or workflow?" /></label>}
            {category === 'general' && <label className="hf-label" htmlFor="hf-details">Topic <span>(optional)</span><input id="hf-details" value={details} onChange={(event) => setDetails(event.target.value)} maxLength={120} placeholder="What is your feedback about?" /></label>}
            <label className="hf-label" htmlFor="hf-description">Describe your feedback <span className="required">*</span><textarea id="hf-description" value={description} onChange={(event) => { setDescription(event.target.value); setSubmitted(false); }} required minLength={10} maxLength={3000} rows={4} placeholder={categoryPrompts[category]} /></label>
            <div className="hf-form-footer"><span>{description.length}/3000</span><button className="hf-submit" type="submit" disabled={description.trim().length < 10}><Send size={17}/> Submit Feedback</button></div>
            <p className="hf-form-note" aria-live="polite">{submitted ? 'Thanks for your feedback. It has been captured in this page session; connect a feedback API to deliver it to your team.' : notice || 'Feedback submission is not connected to a server yet.'}</p>
          </form>
        </section>
      </div>

      <section className="hf-contact"><span className="hf-contact-icon"><Mail size={29}/></span><div><h2>Need direct assistance?</h2><p>Have a question or need help? Get in touch with our support team.</p></div>{supportEmail ? <a className="hf-contact-button" href={`mailto:${encodeURIComponent(supportEmail)}`}><Mail size={19}/> Contact Us</a> : <div className="hf-contact-setup"><button className="hf-contact-button" type="button" disabled title="Set VITE_SUPPORT_EMAIL to enable email support"><Mail size={19}/> Contact Us</button><small>Set VITE_SUPPORT_EMAIL to enable</small></div>}</section>
      <footer className="hf-footer">DevPilot-AI <span>·</span> Built for clearer issue triage</footer>
    </div>
  );
}

export default HelpFeedback;
