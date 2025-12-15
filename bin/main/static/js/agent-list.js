const API_BASE = '/workflow';

document.addEventListener('DOMContentLoaded', function() {
    loadAgents();
    initNewRequestForm();
});

function initNewRequestForm() {
    const createButton = document.getElementById('createRequestButton');
    const narrativeInput = document.getElementById('newNarrativeInput');
    
    if (createButton) {
        createButton.addEventListener('click', createNewRequest);
    }
    
    // Отправка по Ctrl+Enter
    if (narrativeInput) {
        narrativeInput.addEventListener('keydown', (e) => {
            if (e.ctrlKey && e.key === 'Enter') {
                createNewRequest();
            }
        });
    }
}

async function createNewRequest() {
    const narrativeInput = document.getElementById('newNarrativeInput');
    const narrative = narrativeInput.value.trim();
    
    if (!narrative) {
        alert('Пожалуйста, введите нарратив');
        return;
    }
    
    const createBtn = document.getElementById('createRequestButton');
    const btnText = createBtn.querySelector('.btn-text');
    const btnLoader = createBtn.querySelector('.btn-loader');
    
    // Показываем loader
    createBtn.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    try {
        const response = await fetch(`${API_BASE}/run`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                narrative: narrative,
                goal: '',
                task: ''
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Перенаправляем на страницу деталей с новым requestId
        window.location.href = `/iconix-agent-detail.html?requestId=${data.requestId}`;
        
    } catch (error) {
        console.error('Error:', error);
        alert(`Ошибка при создании запроса: ${error.message}`);
    } finally {
        createBtn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

async function loadAgents() {
    const tbody = document.getElementById('agentsTableBody');
    
    try {
        const response = await fetch(`${API_BASE}/sessions`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const sessions = await response.json();
        
        if (sessions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="empty">Нет запущенных агентов</td></tr>';
            return;
        }
        
        tbody.innerHTML = sessions.map((session, index) => {
            const rowNumber = index + 1;
            const statusBadge = getStatusBadge(session.status);
            const createdAt = formatDate(session.createdAt);
            const updatedAt = formatDate(session.updatedAt);
            
            return `
                <tr onclick="window.location.href='/iconix-agent-detail.html?requestId=${session.requestId}'">
                    <td>${rowNumber}</td>
                    <td><span class="request-id">${session.requestId}</span></td>
                    <td>${statusBadge}</td>
                    <td>${session.author || 'System'}</td>
                    <td class="date-cell">${createdAt}</td>
                    <td class="date-cell">${updatedAt}</td>
                </tr>
            `;
        }).join('');
        
    } catch (error) {
        console.error('Error loading agents:', error);
        tbody.innerHTML = `<tr><td colspan="6" class="empty">Ошибка загрузки данных: ${error.message}</td></tr>`;
    }
}

function getStatusBadge(status) {
    const statusMap = {
        'RUNNING': { class: 'running', text: 'Запущен' },
        'COMPLETED': { class: 'completed', text: 'Завершен' },
        'FAILED': { class: 'failed', text: 'Ошибка' },
        'PAUSED_FOR_REVIEW': { class: 'paused_for_review', text: 'На ревью' }
    };
    
    const statusInfo = statusMap[status] || { class: 'running', text: status };
    return `<span class="status-badge ${statusInfo.class}">${statusInfo.text}</span>`;
}

function formatDate(dateString) {
    if (!dateString) return '-';
    
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    
    if (diffMins < 1) {
        return 'только что';
    } else if (diffMins < 60) {
        return `${diffMins} мин. назад`;
    } else if (diffHours < 24) {
        return `${diffHours} ч. назад`;
    } else if (diffDays < 7) {
        return `${diffDays} дн. назад`;
    } else {
        return date.toLocaleDateString('ru-RU', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
}

