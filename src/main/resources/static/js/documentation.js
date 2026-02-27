/**
 * Страница просмотра сгенерированной документации.
 * Параметр requestId в URL. Слева — список файлов, справа — содержимое выбранного файла.
 */
(function () {
    const DOCS_API = '/api/usecase/documentation';
    const urlParams = new URLSearchParams(window.location.search);
    const requestId = urlParams.get('requestId');

    let currentFileName = null;
    let currentFileType = null;

    function init() {
        const backLink = document.getElementById('docBackLink');
        if (backLink && requestId) {
            backLink.href = '/iconix-agent-detail.html?requestId=' + encodeURIComponent(requestId);
        }

        if (!requestId) {
            document.getElementById('docFileList').innerHTML = '<p class="doc-empty">Нет requestId в URL.</p>';
            return;
        }

        loadFileList();
        document.getElementById('docRenderDiagramBtn').addEventListener('click', renderDiagram);
        document.getElementById('docShowAsDocumentBtn').addEventListener('click', showAdocAsDocument);
        document.getElementById('docShowSourceBtn').addEventListener('click', showAdocSource);
    }

    async function loadFileList() {
        const listEl = document.getElementById('docFileList');
        try {
            const res = await fetch(DOCS_API + '/' + encodeURIComponent(requestId) + '/files');
            if (!res.ok) {
                listEl.innerHTML = '<p class="doc-empty">Не удалось загрузить список файлов.</p>';
                return;
            }
            const data = await res.json();
            const files = data.files || [];
            if (files.length === 0) {
                listEl.innerHTML = '<p class="doc-empty">Нет файлов.</p>';
                return;
            }
            listEl.innerHTML = '';
            files.forEach(function (f) {
                const name = f.name || f;
                const type = f.type || (name.endsWith('.puml') ? 'puml' : name.endsWith('.adoc') ? 'adoc' : 'text');
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'file-item';
                btn.textContent = name;
                btn.dataset.fileName = name;
                btn.dataset.fileType = type;
                btn.addEventListener('click', function () {
                    loadFileContent(name, type, btn);
                });
                listEl.appendChild(btn);
            });
        } catch (e) {
            listEl.innerHTML = '<p class="doc-empty">Ошибка: ' + escapeHtml(e.message) + '</p>';
        }
    }

    async function loadFileContent(fileName, fileType, activeBtn) {
        const contentEl = document.getElementById('docFileContent');
        const renderedEl = document.getElementById('docFileContentRendered');
        const toolbarEl = document.getElementById('docToolbar');
        const toolbarAdocEl = document.getElementById('docToolbarAdoc');
        document.querySelectorAll('.doc-file-list .file-item').forEach(function (b) {
            b.classList.remove('active');
        });
        if (activeBtn) activeBtn.classList.add('active');
        currentFileName = fileName;
        currentFileType = fileType;
        contentEl.textContent = 'Загрузка...';
        renderedEl.style.display = 'none';
        renderedEl.innerHTML = '';
        contentEl.style.display = 'block';
        toolbarEl.style.display = fileType === 'puml' ? 'block' : 'none';
        toolbarAdocEl.style.display = fileType === 'adoc' ? 'block' : 'none';

        try {
            const res = await fetch(DOCS_API + '/' + encodeURIComponent(requestId) + '/files/' + encodeURIComponent(fileName));
            if (!res.ok) {
                contentEl.textContent = 'Ошибка загрузки файла.';
                return;
            }
            const text = await res.text();
            contentEl.textContent = text;
        } catch (e) {
            contentEl.textContent = 'Ошибка: ' + e.message;
        }
    }

    async function showAdocAsDocument() {
        const contentEl = document.getElementById('docFileContent');
        const renderedEl = document.getElementById('docFileContentRendered');
        const text = contentEl.textContent || '';
        if (!text.trim()) return;
        renderedEl.innerHTML = '<p>Рендеринг...</p>';
        renderedEl.style.display = 'block';
        contentEl.style.display = 'none';
        try {
            const res = await fetch('/render/adoc', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ asciiDoc: text })
            });
            if (!res.ok) {
                var err = await res.json().catch(function () { return { error: 'Ошибка' }; });
                renderedEl.innerHTML = '<p class="doc-empty">Ошибка рендеринга: ' + escapeHtml(err.error || res.status) + '</p>';
                return;
            }
            var data = await res.json();
            renderedEl.innerHTML = data.html || '';
        } catch (e) {
            renderedEl.innerHTML = '<p class="doc-empty">Ошибка: ' + escapeHtml(e.message) + '</p>';
        }
    }

    function showAdocSource() {
        document.getElementById('docFileContentRendered').style.display = 'none';
        document.getElementById('docFileContent').style.display = 'block';
    }

    function renderDiagram() {
        if (!currentFileName || currentFileType !== 'puml') return;
        const contentEl = document.getElementById('docFileContent');
        const code = contentEl && contentEl.textContent ? contentEl.textContent.trim() : '';
        if (!code) return;
        if (typeof window.renderAndShowDiagram === 'function') {
            window.renderAndShowDiagram(code, currentFileName);
        }
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
