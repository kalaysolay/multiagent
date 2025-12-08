const API_BASE = '/api/git-analyser';

document.addEventListener('DOMContentLoaded', function() {
    initEventListeners();
});

function initEventListeners() {
    document.getElementById('analyzeButton').addEventListener('click', analyzeRepository);
    
    // Enter для отправки
    document.getElementById('repositoryUrl').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            analyzeRepository();
        }
    });
    
    document.getElementById('branch').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            analyzeRepository();
        }
    });
}

async function analyzeRepository() {
    const repositoryUrl = document.getElementById('repositoryUrl').value.trim();
    const branch = document.getElementById('branch').value.trim() || 'main';
    const accessToken = document.getElementById('accessToken').value.trim();
    
    if (!repositoryUrl) {
        showStatus('Пожалуйста, введите URL репозитория', 'error');
        return;
    }
    
    const analyzeBtn = document.getElementById('analyzeButton');
    const btnText = analyzeBtn.querySelector('.btn-text');
    const btnLoader = analyzeBtn.querySelector('.btn-loader');
    
    // Показываем loader
    analyzeBtn.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    // Скрываем предыдущие результаты
    document.getElementById('resultsSection').style.display = 'none';
    
    try {
        const response = await fetch(`${API_BASE}/analyze`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                repositoryUrl: repositoryUrl,
                branch: branch,
                accessToken: accessToken || null
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        displayResults(data);
        showStatus('Анализ завершен успешно', 'success');
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка: ${error.message}`, 'error');
    } finally {
        analyzeBtn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

function displayResults(data) {
    const result = data.result;
    
    // Обновляем сводку
    document.getElementById('totalFiles').textContent = result.totalFiles;
    document.getElementById('analyzedFiles').textContent = result.analyzedFiles;
    document.getElementById('unusedCount').textContent = result.unusedFiles.length;
    document.getElementById('brokenCount').textContent = result.brokenReferences.length;
    
    // Отображаем неиспользуемые файлы
    const unusedFilesList = document.getElementById('unusedFilesList');
    if (result.unusedFiles.length === 0) {
        unusedFilesList.innerHTML = '<div class="file-item">Неиспользуемых файлов не найдено</div>';
    } else {
        unusedFilesList.innerHTML = result.unusedFiles.map(file => `
            <div class="file-item">
                <div class="file-path">${escapeHtml(file.filePath)}</div>
                <div class="file-info">
                    <span>Размер: ${formatFileSize(file.fileSize)}</span>
                    <span>Причина: ${escapeHtml(file.reason)}</span>
                </div>
            </div>
        `).join('');
    }
    
    // Отображаем битые ссылки
    const brokenReferencesList = document.getElementById('brokenReferencesList');
    if (result.brokenReferences.length === 0) {
        brokenReferencesList.innerHTML = '<div class="reference-item">Битых ссылок не найдено</div>';
    } else {
        brokenReferencesList.innerHTML = result.brokenReferences.map(ref => `
            <div class="reference-item">
                <div class="reference-source">${escapeHtml(ref.sourceFile)}</div>
                <div class="reference-details">
                    <span>Ссылка: <span class="reference-path">${escapeHtml(ref.referencedPath)}</span></span>
                    <span>Тип: ${escapeHtml(ref.referenceType)}</span>
                    <span>Строка: ${ref.lineNumber}</span>
                </div>
            </div>
        `).join('');
    }
    
    // Показываем результаты
    document.getElementById('resultsSection').style.display = 'block';
}

function showStatus(message, type) {
    const statusEl = document.getElementById('statusMessage');
    statusEl.textContent = message;
    statusEl.className = `status-message ${type}`;
    statusEl.style.display = 'block';
    
    // Автоматически скрываем через 5 секунд для success/info
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            statusEl.style.display = 'none';
        }, 5000);
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

