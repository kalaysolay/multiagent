/**
 * vector-store.js — логика страницы управления векторным хранилищем.
 * Загрузка файлов, добавление текста, просмотр списка, удаление, поиск.
 */
(function () {
    'use strict';

    const PAGE_SIZE = 20;
    let currentPage = 0;
    let totalDocs = 0;
    let selectedFiles = [];

    const accessDenied = document.getElementById('accessDenied');
    const mainContent = document.getElementById('mainContent');
    const statusMessage = document.getElementById('statusMessage');
    const uploadForm = document.getElementById('uploadForm');
    const fileInput = document.getElementById('fileInput');
    const uploadZone = document.getElementById('uploadZone');
    const browseLink = document.getElementById('browseLink');
    const selectedFilesList = document.getElementById('selectedFilesList');
    const uploadBtn = document.getElementById('uploadBtn');
    const textInput = document.getElementById('textInput');
    const addTextBtn = document.getElementById('addTextBtn');
    const searchInput = document.getElementById('searchInput');
    const searchBtn = document.getElementById('searchBtn');
    const searchResults = document.getElementById('searchResults');
    const tableLoading = document.getElementById('tableLoading');
    const documentsTable = document.getElementById('documentsTable');
    const documentsTableBody = document.getElementById('documentsTableBody');
    const pagination = document.getElementById('pagination');

    function fetchWithAuth(url, options) {
        const token = window.auth && window.auth.getToken ? window.auth.getToken() : null;
        const opts = options || {};
        opts.headers = opts.headers || {};
        if (token) opts.headers['Authorization'] = 'Bearer ' + token;
        return (window.auth && window.auth.fetch ? window.auth.fetch(url, opts) : fetch(url, opts));
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!window.auth || !window.auth.isAdmin()) {
            accessDenied.style.display = '';
            return;
        }
        mainContent.style.display = '';
        loadDocuments(0);

        browseLink.addEventListener('click', function (e) { e.preventDefault(); fileInput.click(); });
        fileInput.addEventListener('change', function () {
            updateFilesFromInput();
        });
        uploadZone.addEventListener('dragover', function (e) {
            e.preventDefault();
            e.stopPropagation();
            uploadZone.classList.add('dragover');
        });
        uploadZone.addEventListener('dragleave', function (e) {
            e.preventDefault();
            e.stopPropagation();
            uploadZone.classList.remove('dragover');
        });
        uploadZone.addEventListener('drop', function (e) {
            e.preventDefault();
            e.stopPropagation();
            uploadZone.classList.remove('dragover');
            var files = e.dataTransfer.files;
            if (files && files.length > 0) {
                selectedFiles = Array.from(files);
                try {
                    var dt = new DataTransfer();
                    for (var i = 0; i < files.length; i++) dt.items.add(files[i]);
                    fileInput.files = dt.files;
                } catch (err) {
                    console.warn('DataTransfer not supported, using selectedFiles only');
                }
                updateSelectedFilesUI();
            }
        });
        uploadForm.addEventListener('submit', function (e) {
            e.preventDefault();
            if (selectedFiles.length === 0) return;
            doUpload();
        });
        addTextBtn.addEventListener('click', addText);
        searchBtn.addEventListener('click', doSearch);
    });

    function updateFilesFromInput() {
        selectedFiles = Array.from(fileInput.files || []);
        updateSelectedFilesUI();
    }

    function updateSelectedFilesUI() {
        if (selectedFiles.length === 0) {
            selectedFilesList.style.display = 'none';
            selectedFilesList.innerHTML = '';
            uploadBtn.disabled = true;
        } else {
            selectedFilesList.style.display = 'block';
            selectedFilesList.innerHTML = '<strong>Выбрано файлов: ' + selectedFiles.length + '</strong><ul>' +
                selectedFiles.map(function (f) { return '<li>' + escapeHtml(f.name || 'файл') + '</li>'; }).join('') + '</ul>';
            uploadBtn.disabled = false;
        }
    }

    async function loadDocuments(page) {
        tableLoading.style.display = '';
        documentsTable.style.display = 'none';
        try {
            const resp = await fetchWithAuth('/api/vector-store/documents?page=' + page + '&size=' + PAGE_SIZE);
            if (resp.status === 403) {
                accessDenied.style.display = '';
                mainContent.style.display = 'none';
                return;
            }
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            totalDocs = data.total || 0;
            currentPage = page;
            renderTable(data.documents || []);
            renderPagination();
        } catch (e) {
            showStatus('Ошибка загрузки: ' + e.message, 'error');
        } finally {
            tableLoading.style.display = 'none';
            documentsTable.style.display = '';
        }
    }

    function renderTable(docs) {
        documentsTableBody.innerHTML = '';
        docs.forEach(function (d) {
            const tr = document.createElement('tr');
            tr.innerHTML =
                '<td class="doc-id">' + escapeHtml((d.id || '').substring(0, 8) + '...') + '</td>' +
                '<td class="content-preview" title="' + escapeHtml(d.contentPreview || '') + '">' + escapeHtml(d.contentPreview || '') + '</td>' +
                '<td>' + escapeHtml(JSON.stringify(d.metadata || {})) + '</td>' +
                '<td>' + (d.createdAt ? formatDate(d.createdAt) : '—') + '</td>' +
                '<td><button class="btn-delete" data-id="' + escapeHtml(d.id) + '">Удалить</button></td>';
            tr.querySelector('.btn-delete').addEventListener('click', function () {
                if (confirm('Удалить документ?')) deleteDocument(d.id);
            });
            documentsTableBody.appendChild(tr);
        });
    }

    function renderPagination() {
        const totalPages = Math.max(1, Math.ceil(totalDocs / PAGE_SIZE));
        let html = '';
        if (currentPage > 0) {
            html += '<button class="btn btn-secondary" id="prevPage">← Назад</button>';
        }
        html += '<span>Стр. ' + (currentPage + 1) + ' из ' + totalPages + ' (всего ' + totalDocs + ')</span>';
        if (currentPage < totalPages - 1) {
            html += '<button class="btn btn-secondary" id="nextPage">Вперёд →</button>';
        }
        pagination.innerHTML = html;
        const prev = document.getElementById('prevPage');
        const next = document.getElementById('nextPage');
        if (prev) prev.addEventListener('click', function () { loadDocuments(currentPage - 1); });
        if (next) next.addEventListener('click', function () { loadDocuments(currentPage + 1); });
    }

    async function doUpload() {
        if (selectedFiles.length === 0) return;
        uploadBtn.disabled = true;
        var formData = new FormData();
        selectedFiles.forEach(function (f) { formData.append('files', f); });
        try {
            const resp = await fetchWithAuth('/api/vector-store/documents/upload', {
                method: 'POST',
                body: formData
            });
            const contentType = resp.headers.get('Content-Type') || '';
            if (!resp.ok) {
                if (contentType.indexOf('application/json') !== -1) {
                    const err = await resp.json();
                    throw new Error(err.error || 'HTTP ' + resp.status);
                }
                const text = await resp.text();
                if (text && text.trim().startsWith('<')) {
                    throw new Error('Сервер вернул страницу вместо ответа (возможно, сессия истекла или файл слишком большой). Обновите страницу и попробуйте снова.');
                }
                throw new Error(text || 'HTTP ' + resp.status);
            }
            let data;
            try {
                data = await resp.json();
            } catch (parseErr) {
                throw new Error('Сервер вернул неверный ответ. Возможно, сессия истекла — обновите страницу.');
            }
            showStatus('Загружено документов: ' + (data.count || 0), 'success');
            selectedFiles = [];
            fileInput.value = '';
            updateSelectedFilesUI();
            loadDocuments(currentPage);
        } catch (e) {
            showStatus('Ошибка загрузки: ' + e.message, 'error');
        } finally {
            uploadBtn.disabled = false;
        }
    }

    async function addText() {
        const text = textInput.value.trim();
        if (!text) {
            showStatus('Введите текст', 'error');
            return;
        }
        addTextBtn.disabled = true;
        try {
            const resp = await fetchWithAuth('/api/vector-store/documents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: text, metadata: { source: 'manual' } })
            });
            if (!resp.ok) {
                const err = await resp.json();
                throw new Error(err.error || 'HTTP ' + resp.status);
            }
            showStatus('Документ добавлен', 'success');
            textInput.value = '';
            loadDocuments(currentPage);
        } catch (e) {
            showStatus('Ошибка: ' + e.message, 'error');
        } finally {
            addTextBtn.disabled = false;
        }
    }

    async function doSearch() {
        const q = searchInput.value.trim();
        if (!q) return;
        searchBtn.disabled = true;
        searchResults.innerHTML = 'Поиск...';
        try {
            const resp = await fetchWithAuth('/api/vector-store/search', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ query: q, topK: 5 })
            });
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            const results = data.results || [];
            if (results.length === 0) {
                searchResults.innerHTML = '<p>Ничего не найдено</p>';
            } else {
                searchResults.innerHTML = '<ul>' + results.map(function (r) {
                    return '<li><strong>' + (r.similarity ? (r.similarity * 100).toFixed(1) + '%</strong> — ' : '') +
                        escapeHtml((r.content || '').substring(0, 150)) + '...</li>';
                }).join('') + '</ul>';
            }
        } catch (e) {
            searchResults.innerHTML = '<p class="status-message error">Ошибка: ' + escapeHtml(e.message) + '</p>';
        } finally {
            searchBtn.disabled = false;
        }
    }

    async function deleteDocument(id) {
        try {
            const resp = await fetchWithAuth('/api/vector-store/documents/' + id, { method: 'DELETE' });
            if (!resp.ok) {
                const err = await resp.json();
                throw new Error(err.error || 'HTTP ' + resp.status);
            }
            showStatus('Документ удалён', 'success');
            loadDocuments(currentPage);
        } catch (e) {
            showStatus('Ошибка удаления: ' + e.message, 'error');
        }
    }

    function showStatus(msg, type) {
        statusMessage.textContent = msg;
        statusMessage.className = 'status-message ' + type;
        setTimeout(function () { statusMessage.className = 'status-message'; }, 5000);
    }

    function formatDate(iso) {
        try {
            var d = new Date(iso);
            return d.toLocaleDateString('ru-RU') + ' ' + d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
        } catch (e) { return iso; }
    }

    function escapeHtml(s) {
        var div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }
})();
