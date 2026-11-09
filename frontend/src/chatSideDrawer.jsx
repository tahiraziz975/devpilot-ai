import { useEffect, useRef, useState } from 'react';
import { Bot, Check, ChevronRight, Circle, LoaderCircle, MessageSquareText, Send, Sparkles, X } from 'lucide-react';

function chatText(payload) {
  return payload?.reply || payload?.response || payload?.message || payload?.content || payload?.answer || '';
}


function CodeSnippet({ code, language }) {
  const [copied, setCopied] = useState(false);
  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }
  return <div className="chat-code-block"><div className="chat-code-toolbar"><span>{language || 'Code'}</span><button type="button" onClick={copyCode} aria-label="Copy code snippet">{copied ? 'Copied' : 'Copy'}</button></div><pre><code>{code}</code></pre></div>;
}

function FormattedText({ content }) {
  const parts = [];
  const codePattern = /```([^\n]*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match;
  while ((match = codePattern.exec(content)) !== null) {
    if (match.index > lastIndex) parts.push({ type: 'text', value: content.slice(lastIndex, match.index) });
    parts.push({ type: 'code', language: match[1].trim(), value: match[2].replace(/\n$/, '') });
    lastIndex = codePattern.lastIndex;
  }
  if (lastIndex < content.length) parts.push({ type: 'text', value: content.slice(lastIndex) });
  if (!parts.length) parts.push({ type: 'text', value: content });

  return <div className="formatted-response">{parts.map((part, partIndex) => {
    if (part.type === 'code') return <CodeSnippet key={`code-${partIndex}`} code={part.value} language={part.language} />;
    const lines = part.value.split('\n');
    const blocks = [];
    for (let index = 0; index < lines.length;) {
      const line = lines[index];
      if (!line.trim()) { index += 1; continue; }
      const heading = line.match(/^(#{1,4})\s+(.+)$/);
      if (heading) { blocks.push(<h4 key={`h-${partIndex}-${index}`}>{heading[2]}</h4>); index += 1; continue; }
      if (/^\s*[-*]\s+/.test(line)) {
        const items = [];
        while (index < lines.length && /^\s*[-*]\s+/.test(lines[index])) { items.push(<li key={index}>{lines[index].replace(/^\s*[-*]\s+/, '')}</li>); index += 1; }
        blocks.push(<ul key={`ul-${partIndex}-${index}`}>{items}</ul>); continue;
      }
      if (/^\s*\d+[.)]\s+/.test(line)) {
        const items = [];
        while (index < lines.length && /^\s*\d+[.)]\s+/.test(lines[index])) { items.push(<li key={index}>{lines[index].replace(/^\s*\d+[.)]\s+/, '')}</li>); index += 1; }
        blocks.push(<ol key={`ol-${partIndex}-${index}`}>{items}</ol>); continue;
      }
      if (line.trim().startsWith('|')) {
        const rows = [];
        while (index < lines.length && lines[index].trim().startsWith('|')) {
          const cells = lines[index].split('|').slice(1, -1).map((cell) => cell.trim());
          if (!cells.every((cell) => /^:?-{3,}:?$/.test(cell))) rows.push(cells);
          index += 1;
        }
        if (rows.length) blocks.push(<div className="chat-table-wrap" key={`table-${partIndex}-${index}`}><table><tbody>{rows.map((row, rowIndex) => { const Cell = rowIndex === 0 ? 'th' : 'td'; return <tr key={rowIndex}>{row.map((cell, cellIndex) => <Cell key={cellIndex}>{cell.replace(/\*\*(.*?)\*\*/g, '$1')}</Cell>)}</tr>; })}</tbody></table></div>);
        continue;
      }
      const paragraph = [line.trim()];
      index += 1;
      while (index < lines.length && lines[index].trim() && !/^(#{1,4}\s|\s*[-*]\s+|\s*\d+[.)]\s+|\s*\|)/.test(lines[index])) { paragraph.push(lines[index].trim()); index += 1; }
      blocks.push(<p key={`p-${partIndex}-${index}`}>{paragraph.join(' ')}</p>);
    }
    return <div key={`text-${partIndex}`}>{blocks}</div>;
  })}</div>;
}

export default function ChatSideDrawer({ open, onClose, issue, classification, fullScreen = false }) {
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef(null);
  const composerRef = useRef(null);

  useEffect(() => {
    if (!open || (!fullScreen && !issue)) return;
    setMessages([{ role: 'assistant', content: issue
      ? `I’ve got your ${classification?.label ? `${String(classification.label).replaceAll('_', ' ')} issue` : 'issue'}: “${issue.title}”. Let’s investigate it together. We can start by tracing the affected flow, identifying likely causes, or outlining a fix.`
      : 'Hi, I’m DevPilot AI. Ask me about code, debugging, architecture, or your development workflow. I can explain concepts and suggest next steps.' }]);
    setDraft('');
    setError('');
  }, [open, issue?.title, classification?.label, fullScreen]);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }, [messages, sending]);
  useEffect(() => { if (composerRef.current) { composerRef.current.style.height = 'auto'; composerRef.current.style.height = `${Math.min(composerRef.current.scrollHeight, 160)}px`; } }, [draft]);
  useEffect(() => {
    if (!open || fullScreen) return undefined;
    const onKeyDown = (event) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose, fullScreen]);

  async function sendMessage(text = draft) {
    const content = text.trim();
    if (!content || sending) return;
    const nextMessages = [...messages, { role: 'user', content }];
    setMessages(nextMessages);
    setDraft('');
    setError('');
    setSending(true);
    try {
      const transcript = nextMessages.map((item) => `${item.role === 'assistant' ? 'DevPilot AI' : 'User'}: ${item.content}`).join('\n');
      const message = issue ? `Classified issue context:
Title: ${issue.title}
Description: ${issue.body}
Predicted category: ${classification?.label || 'unknown'}

Conversation so far:
${transcript}` : `Continue this conversation as DevPilot AI, a coding and development assistant. Be clear and practical.

Conversation so far:
${transcript}`;
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ message }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.message || payload.error || `Chat request failed (${response.status}).`);
      const reply = chatText(payload);
      if (!reply || typeof reply !== 'string') throw new Error('The chat service returned an unexpected response. Expected a reply field.');
      setMessages((current) => [...current, { role: 'assistant', content: reply }]);
    } catch (requestError) {
      setError(requestError.message || 'Could not reach the chat service. Sign in and make sure the Spring Boot backend is running.');
    } finally {
      setSending(false);
    }
  }

  if (!open && !fullScreen) return null;
  const prompts = fullScreen
    ? ['Help me debug an issue', 'Explain a code concept', 'Suggest a development workflow']
    : ['Investigate this issue', 'Suggest a likely fix', 'What should I check first?'];

  const panel = <section className={`chat-drawer ${fullScreen ? 'assistant-chat-panel' : ''}`} role={fullScreen ? 'region' : 'dialog'} aria-modal={fullScreen ? undefined : 'true'} aria-labelledby="chat-title">
      <header className="chat-header"><div className="chat-agent-mark"><Bot size={19} /></div><div className="chat-header-copy"><h2 id="chat-title">DevPilot AI</h2><span><i /> Ready to help</span></div><span className="chat-context-pill"><Sparkles size={12} /> {fullScreen ? 'AI ASSISTANT' : 'ISSUE CONTEXT'}</span>{!fullScreen && <button className="chat-close" type="button" aria-label="Close chat" onClick={onClose}><X size={18} /></button>}</header>
      {!fullScreen && <div className="chat-issue-card"><div className="chat-issue-label"><span><MessageSquareText size={13} /> ATTACHED ISSUE</span><span className="context-loaded"><Check size={12} /> Loaded</span></div><strong>{issue.title}</strong><p>{issue.body}</p><div className="chat-issue-meta"><span className="issue-type-dot" />{classification?.label ? String(classification.label).replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()) : 'Classified issue'}{classification?.probabilities && <span className="issue-confidence">{Math.round(Math.max(...Object.values(classification.probabilities).map(Number)) * 100)}% confidence</span>}</div></div>}
      <div className="chat-message-list" aria-live="polite">
        <div className="chat-date-divider"><span /> NEW CONVERSATION <span /></div>
        {messages.map((message, index) => <article className={`chat-message ${message.role}`} key={`${message.role}-${index}`}><span className="message-avatar">{message.role === 'assistant' ? <Sparkles size={14} /> : 'JD'}</span><div className="message-content"><span className="message-author">{message.role === 'assistant' ? 'DevPilot AI' : 'You'} <small>{message.role === 'assistant' ? 'AI AGENT' : 'JUST NOW'}</small></span><FormattedText content={message.content} /></div></article>)}
        {messages.length === 1 && <div className="chat-suggestions">{prompts.map((prompt) => <button key={prompt} type="button" onClick={() => sendMessage(prompt)} disabled={sending}>{prompt}<ChevronRight size={14} /></button>)}</div>}
        {sending && <div className="chat-thinking"><span className="message-avatar"><Sparkles size={14} /></span><span className="thinking-dots"><i /><i /><i /></span><span>DevPilot is thinking</span></div>}
        {error && <div className="chat-error" role="alert">{error} <button type="button" onClick={() => setError('')}>Dismiss</button></div>}
        <div ref={endRef} />
      </div>
      <form className="chat-composer" onSubmit={(event) => { event.preventDefault(); sendMessage(); }}><label className="sr-only" htmlFor={fullScreen ? 'assistant-chat-message' : 'chat-message'}>Message DevPilot AI</label><textarea ref={composerRef} id={fullScreen ? 'assistant-chat-message' : 'chat-message'} rows={1} value={draft} onChange={(event) => { setDraft(event.target.value); event.target.style.height = 'auto'; event.target.style.height = `${Math.min(event.target.scrollHeight, 160)}px`; }} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendMessage(); } }} placeholder={fullScreen ? 'Ask DevPilot anything about your development work…' : 'Ask DevPilot about this issue…'} disabled={sending} /><div className="composer-footer"><span><Circle size={8} /> {fullScreen ? 'AI Assistant · Enter to send' : 'Issue context included · Enter to send'}</span><button type="submit" aria-label="Send message" disabled={sending || !draft.trim()}>{sending ? <LoaderCircle className="spin" size={16} /> : <Send size={15} />}</button></div></form>
      <footer className="chat-disclaimer"><Sparkles size={12} /> AI suggestions may need review before you apply them.</footer>
    </section>;

  if (fullScreen) return <div className="assistant-fullscreen-chat">{panel}</div>;
  return <div className="chat-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>{panel}</div>;
}
