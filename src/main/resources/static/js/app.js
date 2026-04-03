'use strict';

/**
 * Roundtable — Frontend Application
 *
 * All UI logic lives here. No framework, no build step.
 * State is plain variables. DOM manipulation is direct.
 *
 * Flow:
 *   1. On load  → fetch provider status, load active sessions
 *   2. Start    → POST /api/debate/start → renders rounds as they come back
 *   3. Continue → POST /api/debate/continue/{id}
 *   4. Synthesis → rendered after rounds complete
 */

const API = 'http://localhost:8080/api';

const PROVIDER_COLORS = {
    gemini: '#4b8ff5', groq: '#f07262',
    mistral: '#19c37d', openrouter: '#f0b962'
};

// ─── Initialisation ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    loadProviderStatus();
    loadActiveSessions();
});

async function loadProviderStatus() {
    try {
        const res  = await fetch(`${API}/config/providers`);
        const data = await res.json();
        renderProviderBadges(data);
    } catch (e) {
        console.warn('Could not load provider status:', e.message);
    }
}

function renderProviderBadges(data) {
    const el = document.getElementById('provider-badges');
    el.innerHTML = Object.entries(data).map(([key, info]) => `
        <div class="provider-badge ${info.configured ? 'ok' : 'warn'}">
            <span>${info.configured ? '✓' : '⚠'}</span>
            <span>${info.displayName} ${info.configured ? '' : '— key missing'}</span>
        </div>`).join('');
}

async function loadActiveSessions() {
    try {
        const res      = await fetch(`${API}/sessions`);
        const sessions = await res.json();
        const section  = document.getElementById('ongoing-section');
        const list     = document.getElementById('ongoing-sessions');

        if (!sessions || sessions.length === 0) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'block';
        list.innerHTML = sessions.map(s => `
            <div class="session-card" onclick="continueSession('${s.id}', ${esc(s.topic)}, ${esc(s.title)})">
                <div>
                    <div class="session-card-title">${escHtml(s.title)}</div>
                    <div class="session-card-meta">${escHtml(s.topic)} · ${s.roundCount} round${s.roundCount !== 1 ? 's' : ''}</div>
                </div>
                <div class="session-card-arrow">→</div>
            </div>`).join('');
    } catch (e) {
        console.warn('Could not load active sessions:', e.message);
    }
}

// ─── Start Debate ─────────────────────────────────────────────────────────────

async function startDebate() {
    const topic  = document.getElementById('topic-input').value.trim();
    const title  = document.getElementById('session-title').value.trim()
                   || topic.substring(0, 60);

    if (!topic) { alert('Please enter a topic.'); return; }

    const body = {
        title:         title,
        topic:         topic,
        type:          document.getElementById('session-type').value,
        globalContext: document.getElementById('global-context').value,
        riskTolerance: document.getElementById('risk-tolerance').value,
        tickers:       parseTicker(document.getElementById('ticker-input').value),
        targetRounds:  parseInt(document.getElementById('target-rounds').value),
        dataTypes:     ['PRICE', 'FUNDAMENTALS', 'MACRO_INDICATOR']
    };

    showLoading('Fetching market data and starting debate...');
    switchToDebate(topic);

    try {
        const res  = await fetch(`${API}/debate/start`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(body)
        });
        const data = await res.json();
        hideLoading();

        if (!res.ok) {
            showError(data.message || 'Debate failed to start.');
            return;
        }

        renderDebateOutput(data, body.targetRounds);

    } catch (e) {
        hideLoading();
        showError('Network error: ' + e.message);
    }
}

// ─── Continue Session ─────────────────────────────────────────────────────────

async function continueSession(sessionId, topic, title) {
    showLoading('Loading session...');
    switchToDebate(topic);

    try {
        const res  = await fetch(`${API}/debate/continue/${sessionId}`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ targetRounds: 1 })
        });
        const data = await res.json();
        hideLoading();

        if (!res.ok) {
            showError(data.message || 'Could not continue session.');
            return;
        }

        renderDebateOutput(data, 1);

    } catch (e) {
        hideLoading();
        showError('Network error: ' + e.message);
    }
}

// ─── Render debate output ─────────────────────────────────────────────────────

function renderDebateOutput(data, totalRounds) {
    const feed = document.getElementById('debate-feed');
    feed.innerHTML = '';

    renderRoundPips(data.rounds ? data.rounds.length : 0, totalRounds);

    if (!data.success) {
        feed.innerHTML = `<div class="debate-bubble error" style="border-left-color:var(--error)">
            ⚠ ${escHtml(data.errorMessage || 'Debate failed.')}
        </div>`;
        setBottomStatus('Debate failed.', false);
        return;
    }

    // Render each round
    if (data.rounds && data.rounds.length > 0) {
        data.rounds.forEach((round, i) => {
            renderRound(feed, round, i + 1);
        });
    }

    // Render synthesis
    if (data.synthesis) {
        renderSynthesis(feed, data.synthesis);
    }

    // Data sources used
    if (data.dataSourcesUsed && data.dataSourcesUsed.length > 0) {
        const sources = document.createElement('div');
        sources.style.cssText = 'color:var(--text-muted);font-size:11px;margin-top:12px;text-align:center;';
        sources.textContent = 'Data: ' + data.dataSourcesUsed.join(' · ');
        feed.appendChild(sources);
    }

    setBottomStatus('Debate complete.', true);
    renderRoundPips(data.rounds ? data.rounds.length : 0, totalRounds, true);
}

function renderRound(feed, round, roundNumber) {
    // Round divider
    const divider = document.createElement('div');
    divider.className = 'round-divider';
    divider.innerHTML = `
        <div class="round-divider-line"></div>
        <div class="round-divider-label">Round ${roundNumber}</div>
        <div class="round-divider-line right"></div>`;
    feed.appendChild(divider);

    // Agent responses
    if (!round.responses) return;
    round.responses.forEach(resp => renderAgentResponse(feed, resp));
}

function renderAgentResponse(feed, resp) {
    const color = PROVIDER_COLORS[resp.provider] || '#8b7df8';
    const entry = document.createElement('div');
    entry.className = 'debate-entry';

    const structured = resp.structuredOutput;

    entry.innerHTML = `
        <div class="debate-entry-header">
            <div class="agent-avatar" style="color:${color};border-color:${color};background:${color}18">
                ${escHtml((resp.agentName || '?')[0])}
            </div>
            <div>
                <div class="agent-name">${escHtml(resp.agentName || 'Agent')}</div>
                <div class="agent-model">${escHtml(resp.provider || '')}${resp.model ? ' · ' + escHtml(resp.model) : ''}</div>
            </div>
        </div>
        <div class="debate-bubble ${resp.success ? '' : 'error'}" style="border-left-color:${color}">
            ${resp.success && structured
                ? renderStructuredOutput(structured)
                : escHtml(resp.rawText || resp.errorMessage || '⚠ No response')}
        </div>`;

    feed.appendChild(entry);
    entry.scrollIntoView({ behavior: 'smooth', block: 'end' });
}

function renderStructuredOutput(s) {
    const confidenceClass = (s.confidence || '').toLowerCase().includes('high')   ? 'confidence-high'
                          : (s.confidence || '').toLowerCase().includes('medium') ? 'confidence-medium'
                          : 'confidence-low';
    return `
        <div class="structured-field">
            <div class="structured-label">Position</div>
            <div class="structured-value">${escHtml(s.position || '')}</div>
        </div>
        <div class="structured-field">
            <div class="structured-label">Reasoning</div>
            <div class="structured-value">${escHtml(s.reasoning || '')}</div>
        </div>
        <div class="structured-field">
            <div class="structured-label">Key Risk</div>
            <div class="structured-value">${escHtml(s.keyRisk || '')}</div>
        </div>
        <div class="structured-field">
            <div class="structured-label">Confidence</div>
            <div class="structured-value">
                <span class="confidence-badge ${confidenceClass}">${escHtml((s.confidence || '').split('—')[0].trim())}</span>
                ${s.confidence && s.confidence.includes('—')
                    ? ' — ' + escHtml(s.confidence.split('—').slice(1).join('—').trim())
                    : ''}
            </div>
        </div>`;
}

function renderSynthesis(feed, synthesis) {
    const block = document.createElement('div');
    block.className = 'synthesis-block';

    const consensus = Array.isArray(synthesis.consensus) ? synthesis.consensus : [];
    const disputed  = Array.isArray(synthesis.disputed)  ? synthesis.disputed  : [];
    const risks     = Array.isArray(synthesis.keyRisks)  ? synthesis.keyRisks  : [];

    block.innerHTML = `
        <div class="synthesis-title">⬡ Synthesis</div>
        ${consensus.length ? `
        <div class="synthesis-section">
            <div class="synthesis-section-label">Consensus</div>
            ${consensus.map(i => `<div class="synthesis-item">${escHtml(i)}</div>`).join('')}
        </div>` : ''}
        ${disputed.length ? `
        <div class="synthesis-section">
            <div class="synthesis-section-label">Disputed</div>
            ${disputed.map(i => `<div class="synthesis-item">${escHtml(i)}</div>`).join('')}
        </div>` : ''}
        ${synthesis.strongestCase ? `
        <div class="synthesis-section">
            <div class="synthesis-section-label">Strongest Case</div>
            <div class="synthesis-item">${escHtml(synthesis.strongestCase)}</div>
        </div>` : ''}
        ${risks.length ? `
        <div class="synthesis-section">
            <div class="synthesis-section-label">Key Risks</div>
            ${risks.map(i => `<div class="synthesis-item">${escHtml(i)}</div>`).join('')}
        </div>` : ''}
        ${synthesis.suggestedNext ? `
        <div class="synthesis-section">
            <div class="synthesis-section-label">Suggested Next</div>
            <div class="synthesis-next">${escHtml(synthesis.suggestedNext)}</div>
        </div>` : ''}`;

    feed.appendChild(block);
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

function switchToDebate(topic) {
    document.getElementById('screen-startup').classList.remove('active');
    document.getElementById('screen-debate').classList.add('active');
    document.getElementById('topbar-topic').textContent = topic;
    document.getElementById('debate-feed').innerHTML = '';
    setBottomStatus('Running debate...', null);
}

function goHome() {
    document.getElementById('screen-debate').classList.remove('active');
    document.getElementById('screen-startup').classList.add('active');
    loadActiveSessions();
}

function renderRoundPips(completed, total, allDone) {
    const el = document.getElementById('round-pips');
    let html = '';
    for (let i = 1; i <= total; i++) {
        const cls = allDone || i <= completed ? 'done' : i === completed + 1 ? 'active' : '';
        html += `<div class="round-pip ${cls}"></div>`;
    }
    html += `<span class="round-pip-label">${completed}/${total}</span>`;
    el.innerHTML = html;
}

function setBottomStatus(message, success) {
    const el = document.getElementById('bottom-status');
    if (success === null) {
        el.innerHTML = `<div class="status-text"><span class="spinner" style="color:var(--accent)"></span>${escHtml(message)}</div>`;
    } else if (success) {
        el.innerHTML = `<span style="color:var(--ok);font-size:13px">✓ ${escHtml(message)}</span>
                        <button class="btn-secondary" onclick="goHome()">← New Debate</button>`;
    } else {
        el.innerHTML = `<span style="color:var(--error);font-size:13px">⚠ ${escHtml(message)}</span>
                        <button class="btn-secondary" onclick="goHome()">← Back</button>`;
    }
}

function showError(message) {
    document.getElementById('debate-feed').innerHTML = `
        <div class="debate-bubble error" style="border-left-color:var(--error)">
            ⚠ ${escHtml(message)}
        </div>`;
    setBottomStatus(message, false);
}

function showLoading(message) {
    const el = document.getElementById('loading-overlay');
    document.getElementById('loading-msg').textContent = message;
    el.classList.remove('hidden');
}

function hideLoading() {
    document.getElementById('loading-overlay').classList.add('hidden');
}

function parseTicker(raw) {
    if (!raw || !raw.trim()) return [];
    return raw.split(',').map(s => s.trim().toUpperCase()).filter(Boolean);
}

function escHtml(str) {
    return String(str || '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function esc(str) {
    return JSON.stringify(String(str || ''));
}
