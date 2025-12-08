const API_BASE = '/workflow';

let currentRequestId = null;
let isPausedForReview = false;

// Инициализация
document.addEventListener('DOMContentLoaded', function() {
    initEventListeners();
    loadSessionFromUrl();
});

async function loadSessionFromUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const requestId = urlParams.get('requestId');
    
    if (requestId) {
        currentRequestId = requestId;
        document.getElementById('sessionId').textContent = requestId;
        document.getElementById('sessionInfo').style.display = 'block';
        
        // Загружаем данные сессии
        try {
            const response = await fetch(`${API_BASE}/session/${requestId}`);
            
            if (!response.ok) {
                if (response.status === 404) {
                    showStatus('Сессия не найдена', 'error');
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            
            // Заполняем поля данными из сессии
            if (data.artifacts) {
                // Заполняем поле ввода нарратива
                if (data.artifacts.narrative) {
                    document.getElementById('narrativeInput').value = cleanText(data.artifacts.narrative);
                }
                
                // Заполняем поля результатов
                if (data.artifacts.narrative) {
                    document.getElementById('narrativeOutput').value = cleanText(data.artifacts.narrative);
                }
                
                if (data.artifacts.plantuml) {
                    document.getElementById('domainOutput').value = cleanText(data.artifacts.plantuml);
                }
                
                if (data.artifacts.useCaseModel) {
                    document.getElementById('usecaseOutput').value = cleanText(data.artifacts.useCaseModel);
                }
                
                if (data.artifacts.mvcDiagram) {
                    document.getElementById('mvcOutput').value = cleanText(data.artifacts.mvcDiagram);
                }
                
                // Проверяем статус
                const status = data.artifacts._status;
                isPausedForReview = status === 'PAUSED_FOR_REVIEW';
                
                if (isPausedForReview) {
                    showStatus('Сессия ожидает вашего подтверждения. Отредактируйте поля и нажмите "Отправить обновления"', 'info');
                    document.getElementById('resumeButton').style.display = 'block';
                    
                    // Если есть данные для ревью, заполняем поля
                    if (data.artifacts._reviewData) {
                        const reviewData = data.artifacts._reviewData;
                        if (reviewData.narrative) {
                            document.getElementById('narrativeOutput').value = cleanText(reviewData.narrative);
                        }
                        if (reviewData.domainModel) {
                            document.getElementById('domainOutput').value = cleanText(reviewData.domainModel);
                        }
                    }
                } else {
                    document.getElementById('resumeButton').style.display = 'none';
                    if (status === 'COMPLETED') {
                        showStatus('Workflow завершен', 'success');
                    } else if (status === 'FAILED') {
                        showStatus('Workflow завершился с ошибкой', 'error');
                    } else if (status === 'RUNNING') {
                        showStatus('Workflow выполняется...', 'info');
                    }
                }
            }
            
            // Обновляем состояние кнопок просмотра диаграмм
            updateViewDiagramButtons();
            
        } catch (error) {
            console.error('Error loading session:', error);
            showStatus(`Ошибка загрузки сессии: ${error.message}`, 'error');
        }
    }
}

function initEventListeners() {
    // Кнопка отправки запроса
    document.getElementById('sendButton').addEventListener('click', sendRequest);
    
    // Кнопка отправки обновлений
    document.getElementById('resumeButton').addEventListener('click', sendResume);
    
    // Переключение вкладок
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });
    
    // Кнопки копирования
    document.querySelectorAll('.copy-btn').forEach(btn => {
        btn.addEventListener('click', () => copyToClipboard(btn.dataset.copy));
    });
    
    // Enter для отправки (Ctrl+Enter)
    document.getElementById('narrativeInput').addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 'Enter') {
            if (isPausedForReview) {
                sendResume();
            } else {
                sendRequest();
            }
        }
    });
    
    // Отслеживание изменений в полях PlantUML для активации/деактивации кнопок
    const domainField = document.getElementById('domainOutput');
    const usecaseField = document.getElementById('usecaseOutput');
    const mvcField = document.getElementById('mvcOutput');
    
    if (domainField) {
        domainField.addEventListener('input', updateViewDiagramButtons);
    }
    if (usecaseField) {
        usecaseField.addEventListener('input', updateViewDiagramButtons);
    }
    if (mvcField) {
        mvcField.addEventListener('input', updateViewDiagramButtons);
    }
}

function switchTab(tabName) {
    // Обновляем активные вкладки
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.classList.toggle('active', pane.id === `${tabName}-tab`);
    });
}

async function sendRequest() {
    const narrative = document.getElementById('narrativeInput').value.trim();
    
    if (!narrative) {
        showStatus('Пожалуйста, введите нарратив', 'error');
        return;
    }
    
    const sendBtn = document.getElementById('sendButton');
    const btnText = sendBtn.querySelector('.btn-text');
    const btnLoader = sendBtn.querySelector('.btn-loader');
    
    // Показываем loader
    sendBtn.disabled = true;
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
        handleResponse(data);
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка: ${error.message}`, 'error');
    } finally {
        sendBtn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

async function sendResume() {
    if (!currentRequestId) {
        showStatus('Нет активной сессии', 'error');
        return;
    }
    
    const narrative = document.getElementById('narrativeOutput').value.trim();
    const domainModel = document.getElementById('domainOutput').value.trim();
    
    const resumeBtn = document.getElementById('resumeButton');
    const btnText = resumeBtn.querySelector('.btn-text');
    const btnLoader = resumeBtn.querySelector('.btn-loader');
    
    // Показываем loader
    resumeBtn.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    try {
        // JSON.stringify автоматически экранирует все специальные символы
        // Не нужно дополнительное экранирование
        const response = await fetch(`${API_BASE}/resume`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                requestId: currentRequestId,
                narrative: narrative,
                domainModel: domainModel
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        handleResponse(data);
        showStatus('Обновления отправлены успешно', 'success');
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка при отправке обновлений: ${error.message}`, 'error');
    } finally {
        resumeBtn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

function handleResponse(data) {
    // Сохраняем requestId
    currentRequestId = data.requestId;
    document.getElementById('sessionId').textContent = currentRequestId;
    document.getElementById('sessionInfo').style.display = 'block';
    
    // Проверяем статус
    const status = data.artifacts?._status;
    isPausedForReview = status === 'PAUSED_FOR_REVIEW';
    
    if (isPausedForReview) {
        showStatus('Требуется ваше подтверждение. Отредактируйте поля и нажмите "Отправить обновления"', 'info');
        document.getElementById('resumeButton').style.display = 'block';
        
        // Если есть данные для ревью, заполняем поля
        if (data.artifacts._reviewData) {
            const reviewData = data.artifacts._reviewData;
            if (reviewData.narrative) {
                document.getElementById('narrativeOutput').value = cleanText(reviewData.narrative);
            }
            if (reviewData.domainModel) {
                document.getElementById('domainOutput').value = cleanText(reviewData.domainModel);
            }
        }
    } else {
        document.getElementById('resumeButton').style.display = 'none';
        if (status === 'COMPLETED') {
            showStatus('Workflow завершен успешно', 'success');
        }
    }
    
    // Заполняем поля результатами
    if (data.artifacts) {
        if (data.artifacts.narrative) {
            document.getElementById('narrativeOutput').value = cleanText(data.artifacts.narrative);
        }
        
        if (data.artifacts.plantuml) {
            document.getElementById('domainOutput').value = cleanText(data.artifacts.plantuml);
        }
        
        if (data.artifacts.useCaseModel) {
            document.getElementById('usecaseOutput').value = cleanText(data.artifacts.useCaseModel);
        }
        
        if (data.artifacts.mvcDiagram) {
            document.getElementById('mvcOutput').value = cleanText(data.artifacts.mvcDiagram);
        }
    }
    
    // Обновляем состояние кнопок просмотра диаграмм
    updateViewDiagramButtons();
    
    // Переключаемся на первую вкладку с данными
    if (data.artifacts?.narrative) {
        switchTab('narrative');
    } else if (data.artifacts?.plantuml) {
        switchTab('domain');
    }
}

function updateViewDiagramButtons() {
    // Обновляем состояние кнопок на основе содержимого полей
    const domainBtn = document.getElementById('viewDomainDiagram');
    const domainField = document.getElementById('domainOutput');
    if (domainBtn && domainField) {
        domainBtn.disabled = !domainField.value.trim();
    }
    
    const usecaseBtn = document.getElementById('viewUseCaseDiagram');
    const usecaseField = document.getElementById('usecaseOutput');
    if (usecaseBtn && usecaseField) {
        usecaseBtn.disabled = !usecaseField.value.trim();
    }
    
    const mvcBtn = document.getElementById('viewMvcDiagram');
    const mvcField = document.getElementById('mvcOutput');
    if (mvcBtn && mvcField) {
        mvcBtn.disabled = !mvcField.value.trim();
    }
}

function cleanText(text) {
    if (!text) return '';
    
    // Убираем экранирование \n
    let cleaned = String(text).replace(/\\n/g, '\n');
    
    // Убираем экранирование других символов
    cleaned = cleaned.replace(/\\t/g, '\t');
    cleaned = cleaned.replace(/\\r/g, '\r');
    
    // Убираем двойные экранирования
    cleaned = cleaned.replace(/\\\\/g, '\\');
    
    // Убираем лишние пробелы в начале/конце строк
    cleaned = cleaned.trim();
    
    return cleaned;
}

function escapeForJson(text) {
    if (!text) return '';
    
    // JSON.stringify автоматически экранирует все специальные символы
    // Просто возвращаем текст как есть - JSON.stringify сделает всю работу
    return String(text);
}

function copyToClipboard(fieldName) {
    const fieldMap = {
        'narrative': 'narrativeOutput',
        'domain': 'domainOutput',
        'usecase': 'usecaseOutput',
        'mvc': 'mvcOutput'
    };
    
    const fieldId = fieldMap[fieldName];
    if (!fieldId) return;
    
    const field = document.getElementById(fieldId);
    const text = field.value;
    
    if (!text) {
        showStatus('Нет данных для копирования', 'error');
        return;
    }
    
    navigator.clipboard.writeText(text).then(() => {
        showStatus('Скопировано в буфер обмена', 'success');
        
        // Визуальная обратная связь
        const btn = document.querySelector(`[data-copy="${fieldName}"]`);
        const originalText = btn.textContent;
        btn.textContent = 'Скопировано!';
        setTimeout(() => {
            btn.textContent = originalText;
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy:', err);
        showStatus('Ошибка при копировании', 'error');
    });
}

function showStatus(message, type) {
    const statusEl = document.getElementById('statusMessage');
    statusEl.textContent = message;
    statusEl.className = `status-message ${type}`;
    
    // Автоматически скрываем через 5 секунд для success/info
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            statusEl.style.display = 'none';
        }, 5000);
    }
}

