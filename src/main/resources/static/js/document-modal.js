const DOCUMENT_RENDER_API = '/render/adoc';

let currentDocumentHtml = null;
let currentDocumentTitle = '';

// Инициализация
document.addEventListener('DOMContentLoaded', function() {
    console.log('Document modal: DOMContentLoaded');
    initDocumentModalListeners();
});

function initDocumentModalListeners() {
    // Закрытие модального окна
    const closeBtn = document.getElementById('closeDocumentModal');
    if (closeBtn) {
        closeBtn.addEventListener('click', closeDocumentModal);
    }
    
    const modal = document.getElementById('documentModal');
    if (modal) {
        // Закрытие по клику вне модального окна
        modal.addEventListener('click', function(e) {
            if (e.target === this) {
                closeDocumentModal();
            }
        });
        
        // Закрытие по Escape
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && modal.style.display !== 'none') {
                closeDocumentModal();
            }
        });
    }
    
    // Кнопка скачивания
    const downloadBtn = document.getElementById('downloadDocument');
    if (downloadBtn) {
        downloadBtn.addEventListener('click', downloadDocument);
    }
}

/**
 * Открывает модальное окно с отрендеренным AsciiDoc документом
 */
async function showDocument(asciiDocContent, title) {
    if (!asciiDocContent || asciiDocContent.trim().length === 0) {
        console.warn('Cannot show document: content is empty');
        return;
    }
    
    const modal = document.getElementById('documentModal');
    const container = document.getElementById('documentContainer');
    
    if (!modal || !container) {
        console.error('Document modal elements not found');
        return;
    }
    
    // Показываем индикатор загрузки
    container.innerHTML = '<div class="loading-indicator">Рендеринг документа...</div>';
    modal.style.display = 'flex';
    currentDocumentTitle = title || 'Документ';
    
    try {
        // Отправляем запрос на рендеринг
        const response = await fetch(DOCUMENT_RENDER_API, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                asciiDoc: asciiDocContent
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        currentDocumentHtml = data.html;
        
        // Отображаем HTML
        container.innerHTML = currentDocumentHtml;
        
        // Прокручиваем в начало документа
        const modalBody = document.querySelector('#documentModal .document-modal-body');
        if (modalBody) {
            modalBody.scrollTop = 0;
        }
        
        // Добавляем стили для AsciiDoc документа, если их еще нет
        ensureAsciiDocStyles();
        
    } catch (error) {
        console.error('Error rendering document:', error);
        container.innerHTML = `
            <div class="error-message">
                <h3>Ошибка при рендеринге документа</h3>
                <p>${escapeHtml(error.message)}</p>
            </div>
        `;
    }
}

/**
 * Закрывает модальное окно документа
 */
function closeDocumentModal() {
    const modal = document.getElementById('documentModal');
    if (modal) {
        modal.style.display = 'none';
        currentDocumentHtml = null;
        currentDocumentTitle = '';
    }
}

/**
 * Скачивает отрендеренный HTML документ
 */
function downloadDocument() {
    if (!currentDocumentHtml) {
        console.warn('No document to download');
        return;
    }
    
    const blob = new Blob([currentDocumentHtml], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${currentDocumentTitle.replace(/[^a-z0-9]/gi, '_')}_document.html`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

/**
 * Убеждается, что стили для AsciiDoc документа подключены
 */
function ensureAsciiDocStyles() {
    // Проверяем, есть ли уже стили
    if (document.getElementById('asciidoc-styles')) {
        return;
    }
    
    // Создаем элемент style для базовых стилей AsciiDoc
    const style = document.createElement('style');
    style.id = 'asciidoc-styles';
    style.textContent = `
        #documentContainer {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            line-height: 1.6;
            color: #333;
        }
        
        #documentContainer h1, #documentContainer h2, #documentContainer h3 {
            margin-top: 1.5em;
            margin-bottom: 0.5em;
            font-weight: 600;
        }
        
        #documentContainer h1 {
            font-size: 2em;
            border-bottom: 2px solid #e0e0e0;
            padding-bottom: 0.3em;
        }
        
        #documentContainer h2 {
            font-size: 1.5em;
            border-bottom: 1px solid #e0e0e0;
            padding-bottom: 0.2em;
        }
        
        #documentContainer h3 {
            font-size: 1.2em;
        }
        
        #documentContainer p {
            margin: 1em 0;
        }
        
        #documentContainer ul, #documentContainer ol {
            margin: 1em 0;
            padding-left: 2em;
        }
        
        #documentContainer code {
            background: #f5f5f5;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
            font-size: 0.9em;
        }
        
        #documentContainer pre {
            background: #f5f5f5;
            padding: 1em;
            border-radius: 6px;
            overflow-x: auto;
            margin: 1em 0;
        }
        
        #documentContainer pre code {
            background: none;
            padding: 0;
        }
        
        #documentContainer table {
            border-collapse: collapse;
            width: 100%;
            margin: 1em 0;
        }
        
        #documentContainer table th,
        #documentContainer table td {
            border: 1px solid #ddd;
            padding: 8px 12px;
            text-align: left;
        }
        
        #documentContainer table th {
            background: #f0f0f0;
            font-weight: 600;
        }
        
        #documentContainer blockquote {
            border-left: 4px solid #667eea;
            padding-left: 1em;
            margin: 1em 0;
            color: #666;
        }
    `;
    
    document.head.appendChild(style);
}

/**
 * Экранирует HTML для безопасного отображения
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Делаем функцию доступной глобально
window.showDocument = showDocument;

