/**
 * Chat with LLM — rich rendering (Markdown, code, PlantUML, AsciiDoc).
 * Uses marked.js + highlight.js loaded from CDN in chat.html.
 * Backend: POST /api/chat, GET /api/chat/history, GET /api/chat/conversations.
 */
(function () {
    'use strict';

    const API = '/api/chat';
    const RENDER_API = '/render';

    let currentConversationId = null;
    let chatHistory = [];
    let sending = false;

    // ──── marked.js setup ────
    const renderer = new marked.Renderer();

    // Code blocks: detect plantuml/puml and adoc/asciidoc for special handling
    renderer.code = function (code, lang) {
        const langLower = (lang || '').toLowerCase();

        // PlantUML → render button + collapsible source
        if (langLower === 'plantuml' || langLower === 'puml') {
            const id = 'puml-' + Math.random().toString(36).slice(2, 9);
            const escaped = escapeHtml(code);
            return `<div class="plantuml-block" id="${id}">
                <div class="plantuml-actions">
                    <button class="btn-render-puml" onclick="window.__renderPuml('${id}')">Показать диаграмму</button>
                    <button class="btn-copy" onclick="window.__copyCode(this)" data-code="${encodeURIComponent(code)}">Копировать</button>
                </div>
                <details class="puml-source"><summary>PlantUML код</summary><pre><code class="language-plaintext">${escaped}</code></pre></details>
                <div class="puml-preview"></div>
            </div>`;
        }

        // AsciiDoc → rendered HTML + collapsible source
        if (langLower === 'adoc' || langLower === 'asciidoc') {
            const escaped = escapeHtml(code);
            const id = 'adoc-' + Math.random().toString(36).slice(2, 9);
            return `<div class="adoc-block" id="${id}">
                <div class="adoc-actions">
                    <button class="btn-render-adoc" onclick="window.__renderAdoc('${id}')">Показать документ</button>
                    <button class="btn-copy" onclick="window.__copyCode(this)" data-code="${encodeURIComponent(code)}">Копировать</button>
                </div>
                <details class="adoc-source"><summary>AsciiDoc код</summary><pre><code class="language-asciidoc">${escaped}</code></pre></details>
                <div class="adoc-preview"></div>
            </div>`;
        }

        // Regular code: syntax highlighting + copy button
        let highlighted;
        if (lang && hljs.getLanguage(lang)) {
            highlighted = hljs.highlight(code, { language: lang }).value;
        } else {
            highlighted = hljs.highlightAuto(code).value;
        }
        const langLabel = lang || 'code';
        return `<div class="code-block-wrapper">
            <div class="code-block-header">
                <span class="code-lang">${escapeHtml(langLabel)}</span>
                <button class="btn-copy" onclick="window.__copyCode(this)" data-code="${encodeURIComponent(code)}">Копировать</button>
            </div>
            <pre><code class="hljs language-${escapeHtml(langLabel)}">${highlighted}</code></pre>
        </div>`;
    };

    marked.setOptions({
        renderer: renderer,
        breaks: true,
        gfm: true,
        highlight: function (code, lang) {
            if (lang && hljs.getLanguage(lang)) {
                return hljs.highlight(code, { language: lang }).value;
            }
            return hljs.highlightAuto(code).value;
        }
    });

    // ──── Global helper functions (called from onclick) ────

    window.__copyCode = function (btn) {
        const code = decodeURIComponent(btn.dataset.code);
        navigator.clipboard.writeText(code).then(() => {
            const orig = btn.textContent;
            btn.textContent = 'Скопировано!';
            setTimeout(() => btn.textContent = orig, 1500);
        });
    };

    window.__renderPuml = async function (blockId) {
        const block = document.getElementById(blockId);
        if (!block) return;
        const preview = block.querySelector('.puml-preview');
        const source = block.querySelector('.puml-source pre code');
        const code = source ? source.textContent : '';
        if (!code.trim()) return;

        preview.innerHTML = '<p class="rendering-hint">Рендеринг диаграммы…</p>';
        try {
            const resp = await fetch(RENDER_API + '/png', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ plantUml: code })
            });
            if (!resp.ok) throw new Error('Render error ' + resp.status);
            const data = await resp.json();
            if (data.error) throw new Error(data.error);
            preview.innerHTML = `<img src="data:image/png;base64,${data.image}" alt="PlantUML diagram" class="puml-img" onclick="window.__showDiagramModal(this.src, 'PlantUML диаграмма')">`;
        } catch (e) {
            preview.innerHTML = `<p class="render-error">Ошибка рендеринга: ${escapeHtml(e.message)}</p>`;
        }
    };

    window.__renderAdoc = async function (blockId) {
        const block = document.getElementById(blockId);
        if (!block) return;
        const preview = block.querySelector('.adoc-preview');
        const source = block.querySelector('.adoc-source pre code');
        const code = source ? source.textContent : '';
        if (!code.trim()) return;

        preview.innerHTML = '<p class="rendering-hint">Рендеринг документа…</p>';
        try {
            const resp = await fetch('/render/adoc', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ adoc: code })
            });
            if (!resp.ok) throw new Error('Render error ' + resp.status);
            const data = await resp.json();
            preview.innerHTML = `<div class="adoc-rendered">${data.html}</div>`;
        } catch (e) {
            preview.innerHTML = `<p class="render-error">Ошибка рендеринга: ${escapeHtml(e.message)}</p>`;
        }
    };

    window.__showDiagramModal = function (src, title) {
        const modal = document.getElementById('chatDiagramModal');
        const container = document.getElementById('chatDiagramContainer');
        const titleEl = document.getElementById('chatDiagramTitle');
        if (!modal) return;
        titleEl.textContent = title || 'Диаграмма';
        container.innerHTML = `<img src="${src}" alt="diagram" style="max-width:100%;max-height:80vh;">`;
        modal.style.display = 'flex';
    };

    // ──── Initialization ────

    document.addEventListener('DOMContentLoaded', function () {
        initInput();
        initConversations();
        initDiagramModal();

        const urlParams = new URLSearchParams(window.location.search);
        const convId = urlParams.get('conversationId') || localStorage.getItem('currentConversationId') || null;
        if (convId) {
            loadConversation(convId);
        } else {
            startNewConversation();
        }
    });

    function initInput() {
        const input = document.getElementById('chatInput');
        const btn = document.getElementById('sendChatButton');
        btn.addEventListener('click', sendMessage);
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
        // Auto-resize textarea
        input.addEventListener('input', function () {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 200) + 'px';
        });
    }

    function initConversations() {
        document.getElementById('newConversationBtn').addEventListener('click', startNewConversation);
        document.getElementById('toggleConversations').addEventListener('click', function () {
            document.getElementById('conversationsPanel').classList.toggle('collapsed');
        });
        loadConversationsList();
    }

    function initDiagramModal() {
        const modal = document.getElementById('chatDiagramModal');
        const closeBtn = document.getElementById('closeChatDiagramModal');
        if (closeBtn) closeBtn.addEventListener('click', () => modal.style.display = 'none');
        if (modal) modal.addEventListener('click', (e) => { if (e.target === modal) modal.style.display = 'none'; });
    }

    // ──── Conversations panel ────

    async function loadConversationsList() {
        const list = document.getElementById('conversationsList');
        try {
            const resp = await fetch(API + '/conversations?limit=20');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            if (!data.conversations || data.conversations.length === 0) {
                list.innerHTML = '<div class="conv-empty">Нет бесед</div>';
                return;
            }
            list.innerHTML = data.conversations.map(c => {
                const active = c.conversationId === currentConversationId ? ' active' : '';
                const date = new Date(c.startedAt).toLocaleDateString('ru-RU');
                const preview = escapeHtml(c.preview || '').substring(0, 80);
                return `<div class="conv-item${active}" data-id="${escapeHtml(c.conversationId)}" onclick="window.__selectConversation('${escapeHtml(c.conversationId)}')">
                    <div class="conv-preview">${preview}</div>
                    <div class="conv-meta">${date} &middot; ${c.messageCount} сообщ.</div>
                </div>`;
            }).join('');
        } catch (e) {
            console.warn('Failed to load conversations', e);
            list.innerHTML = '<div class="conv-empty">Ошибка загрузки</div>';
        }
    }

    window.__selectConversation = function (id) {
        loadConversation(id);
    };

    async function loadConversation(conversationId) {
        if (!conversationId) return;
        currentConversationId = conversationId;
        localStorage.setItem('currentConversationId', conversationId);
        chatHistory = [];
        clearMessages();

        try {
            const resp = await fetch(API + `/history?conversationId=${encodeURIComponent(conversationId)}&limit=50`);
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            if (data.messages && data.messages.length > 0) {
                data.messages.forEach(msg => {
                    if (msg.role === 'tool') return;
                    appendMessage(msg.role, msg.content, msg.toolCalls, msg.timestamp);
                    if (msg.role === 'user' || msg.role === 'assistant') {
                        chatHistory.push({ role: msg.role, content: msg.content });
                    }
                });
            }
            scrollToBottom();
        } catch (e) {
            console.error('Error loading conversation:', e);
        }
        highlightActiveConversation();
    }

    function startNewConversation() {
        currentConversationId = null;
        chatHistory = [];
        localStorage.removeItem('currentConversationId');
        clearMessages();
        const container = document.getElementById('chatMessages');
        container.innerHTML = `<div class="message system-message"><div class="message-body">
            <p>Начните новый разговор. Вы можете:</p>
            <ul>
                <li>Спросить о статусе workflow сессий</li>
                <li>Попросить сгенерировать доменную модель по нарративу</li>
                <li>Запросить список доступных инструментов</li>
                <li>Задать вопросы по ICONIX методологии</li>
            </ul>
        </div></div>`;
        highlightActiveConversation();
    }

    function highlightActiveConversation() {
        document.querySelectorAll('.conv-item').forEach(el => {
            el.classList.toggle('active', el.dataset.id === currentConversationId);
        });
    }

    // ──── Send message ────

    async function sendMessage() {
        if (sending) return;
        const input = document.getElementById('chatInput');
        const message = input.value.trim();
        if (!message) return;

        sending = true;
        const btn = document.getElementById('sendChatButton');
        btn.disabled = true;
        input.value = '';
        input.style.height = 'auto';

        appendMessage('user', message);
        chatHistory.push({ role: 'user', content: message });

        const loadingEl = showLoading();

        try {
            const urlParams = new URLSearchParams(window.location.search);
            const workflowSessionId = urlParams.get('sessionId') || localStorage.getItem('currentWorkflowSessionId');

            const resp = await fetch(API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message: message,
                    history: chatHistory,
                    workflowSessionId: workflowSessionId || null,
                    conversationId: currentConversationId
                })
            });

            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();

            if (loadingEl) loadingEl.remove();

            if (data.conversationId) {
                currentConversationId = data.conversationId;
                localStorage.setItem('currentConversationId', data.conversationId);
                loadConversationsList();
            }

            appendMessage('assistant', data.response, data.toolCalls, null, data.diagrams);
            chatHistory.push({ role: 'assistant', content: data.response });

        } catch (e) {
            if (loadingEl) loadingEl.remove();
            appendMessage('assistant', `Ошибка: ${e.message}`);
        } finally {
            sending = false;
            btn.disabled = false;
            input.focus();
        }
    }

    // ──── Message rendering ────

    function appendMessage(role, content, toolCalls, timestamp, diagrams) {
        const container = document.getElementById('chatMessages');

        // Remove welcome message after first real message
        const sys = container.querySelector('.system-message');
        if (sys && chatHistory.length > 0) sys.remove();

        const div = document.createElement('div');
        div.className = `message ${role}-message`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = role === 'user' ? 'Вы' : 'AI';
        div.appendChild(avatar);

        const body = document.createElement('div');
        body.className = 'message-body';

        // Rendered content
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        if (role === 'assistant') {
            contentDiv.innerHTML = renderMarkdown(content || '');
        } else {
            contentDiv.innerHTML = `<p>${escapeHtml(content || '').replace(/\n/g, '<br>')}</p>`;
        }
        body.appendChild(contentDiv);

        // Tool calls
        if (toolCalls && toolCalls.length > 0) {
            const toolDiv = document.createElement('div');
            toolDiv.className = 'tool-calls-info';
            toolDiv.innerHTML = `<details><summary>Использовано инструментов: ${toolCalls.length}</summary><ul>${toolCalls.map(tc =>
                `<li><strong>${escapeHtml(tc.name || '')}</strong></li>`).join('')}</ul></details>`;
            body.appendChild(toolDiv);
        }

        // Diagrams from backend
        if (diagrams && diagrams.length > 0) {
            const dDiv = document.createElement('div');
            dDiv.className = 'diagrams-container';
            diagrams.forEach((d, i) => {
                const btn = document.createElement('button');
                btn.className = 'btn-diagram';
                btn.textContent = d.title || 'Диаграмма ' + (i + 1);
                btn.addEventListener('click', () => renderPumlInModal(d.code, d.title));
                dDiv.appendChild(btn);
            });
            body.appendChild(dDiv);
        }

        // Timestamp
        const timeEl = document.createElement('div');
        timeEl.className = 'message-time';
        const t = timestamp ? new Date(timestamp) : new Date();
        timeEl.textContent = t.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
        body.appendChild(timeEl);

        div.appendChild(body);
        container.appendChild(div);
        scrollToBottom();
    }

    function renderMarkdown(text) {
        if (!text) return '';
        try {
            return marked.parse(text);
        } catch (e) {
            console.error('Markdown parse error', e);
            return `<p>${escapeHtml(text).replace(/\n/g, '<br>')}</p>`;
        }
    }

    async function renderPumlInModal(code, title) {
        const modal = document.getElementById('chatDiagramModal');
        const container = document.getElementById('chatDiagramContainer');
        const titleEl = document.getElementById('chatDiagramTitle');
        titleEl.textContent = title || 'Диаграмма';
        container.innerHTML = '<p>Рендеринг…</p>';
        modal.style.display = 'flex';

        try {
            const resp = await fetch(RENDER_API + '/png', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ plantUml: code })
            });
            if (!resp.ok) throw new Error('Render error');
            const data = await resp.json();
            container.innerHTML = `<img src="data:image/png;base64,${data.image}" alt="diagram" style="max-width:100%;max-height:80vh;">`;
        } catch (e) {
            container.innerHTML = `<p>Ошибка: ${escapeHtml(e.message)}</p>`;
        }
    }

    // ──── Helpers ────

    function showLoading() {
        const container = document.getElementById('chatMessages');
        const div = document.createElement('div');
        div.className = 'message assistant-message loading-msg';
        div.innerHTML = `<div class="message-avatar">AI</div><div class="message-body"><div class="message-content"><span class="typing-indicator"><span></span><span></span><span></span></span></div></div>`;
        container.appendChild(div);
        scrollToBottom();
        return div;
    }

    function clearMessages() {
        document.getElementById('chatMessages').innerHTML = '';
    }

    function scrollToBottom() {
        const el = document.getElementById('chatMessages');
        el.scrollTop = el.scrollHeight;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

})();
