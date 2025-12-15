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
                    // Парсим и отображаем Use Case после загрузки данных
                    setTimeout(() => parseAndDisplayUseCases(), 100);
                }
                
                if (data.artifacts.mvcDiagram) {
                    document.getElementById('mvcOutput').value = cleanText(data.artifacts.mvcDiagram);
                }
                
                // Заполняем сценарии (массив)
                if (data.artifacts.scenarios && Array.isArray(data.artifacts.scenarios) && data.artifacts.scenarios.length > 0) {
                    // Берем первый сценарий (пока один)
                    const scenario = data.artifacts.scenarios[0];
                    document.getElementById('scenarioOutput').value = cleanText(scenario);
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
            
            // Парсим и отображаем Use Case для декомпозиции
            parseAndDisplayUseCases();
            
            // Убеждаемся, что обработчики кликов установлены (если они еще не установлены)
            // Это нужно, если diagram-modal.js загрузился раньше, чем данные
            ensureDiagramButtonHandlers();
            
        } catch (error) {
            console.error('Error loading session:', error);
            showStatus(`Ошибка загрузки сессии: ${error.message}`, 'error');
        }
    }
}

/**
 * Убеждается, что обработчики кликов для кнопок просмотра диаграмм установлены.
 * Вызывается после загрузки данных, чтобы гарантировать работу кнопок.
 */
function ensureDiagramButtonHandlers() {
    console.log('Ensuring diagram button handlers are set up...');
    
    // Обновляем состояние кнопок
    updateViewDiagramButtons();
    
    // Переинициализируем обработчики, если они еще не установлены или были потеряны
    // Это гарантирует, что обработчики работают даже после динамической загрузки данных
    if (typeof initViewDiagramButtons === 'function') {
        console.log('Re-initializing view diagram buttons...');
        initViewDiagramButtons();
    } else {
        console.warn('initViewDiagramButtons function not found');
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
        
        // Кнопка декомпозиции
        const decomposeButton = document.getElementById('decomposeButton');
        if (decomposeButton) {
            decomposeButton.addEventListener('click', handleDecomposition);
        }
        
        // Отслеживание изменений в чекбоксах Use Case
        document.addEventListener('change', (e) => {
            if (e.target.type === 'checkbox' && e.target.closest('.usecase-list')) {
                updateDecomposeButtonState();
            }
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
        usecaseField.addEventListener('input', () => {
            updateViewDiagramButtons();
            // Также обновляем список Use Case при изменении содержимого
            parseAndDisplayUseCases();
        });
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
    
    // Если переключились на вкладку Use Case, обновляем список
    if (tabName === 'usecase') {
        setTimeout(() => {
            console.log('Switched to usecase tab, updating Use Case list');
            parseAndDisplayUseCases();
        }, 100);
    }
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
            // Парсим и отображаем Use Case после загрузки данных
            setTimeout(() => {
                console.log('Calling parseAndDisplayUseCases from handleResponse');
                parseAndDisplayUseCases();
            }, 200);
        }
        
        if (data.artifacts.mvcDiagram) {
            document.getElementById('mvcOutput').value = cleanText(data.artifacts.mvcDiagram);
        }
        
        // Заполняем сценарии (массив)
        if (data.artifacts.scenarios && Array.isArray(data.artifacts.scenarios) && data.artifacts.scenarios.length > 0) {
            // Берем первый сценарий (пока один)
            const scenario = data.artifacts.scenarios[0];
            document.getElementById('scenarioOutput').value = cleanText(scenario);
        }
    }
    
    // Обновляем состояние кнопок просмотра диаграмм
    updateViewDiagramButtons();
    
    // Убеждаемся, что обработчики кликов установлены
    ensureDiagramButtonHandlers();
    
    // Переключаемся на первую вкладку с данными
    if (data.artifacts?.narrative) {
        switchTab('narrative');
    } else if (data.artifacts?.plantuml) {
        switchTab('domain');
    }
}

function updateViewDiagramButtons() {
    console.log('Updating view diagram buttons state...');
    // Обновляем состояние кнопок на основе содержимого полей
    const domainBtn = document.getElementById('viewDomainDiagram');
    const domainField = document.getElementById('domainOutput');
    if (domainBtn && domainField) {
        const hasContent = domainField.value.trim().length > 0;
        domainBtn.disabled = !hasContent;
        console.log('Domain button disabled:', !hasContent, 'Content length:', domainField.value.trim().length);
    }
    
    const usecaseBtn = document.getElementById('viewUseCaseDiagram');
    const usecaseField = document.getElementById('usecaseOutput');
    if (usecaseBtn && usecaseField) {
        const hasContent = usecaseField.value.trim().length > 0;
        usecaseBtn.disabled = !hasContent;
        console.log('UseCase button disabled:', !hasContent, 'Content length:', usecaseField.value.trim().length);
    }
    
    const mvcBtn = document.getElementById('viewMvcDiagram');
    const mvcField = document.getElementById('mvcOutput');
    if (mvcBtn && mvcField) {
        const hasContent = mvcField.value.trim().length > 0;
        mvcBtn.disabled = !hasContent;
        console.log('MVC button disabled:', !hasContent, 'Content length:', mvcField.value.trim().length);
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
    
    // Убираем markdown блоки для PlantUML
    // Удаляем ```plantuml или ```puml в начале
    cleaned = cleaned.replace(/^```(?:plantuml|puml|uml)\s*/i, '');
    // Удаляем ``` в конце
    cleaned = cleaned.replace(/```\s*$/i, '');
    // Удаляем ``` в начале и конце (если остались)
    cleaned = cleaned.replace(/^```\s*/, '').replace(/\s*```$/, '');
    
    // Исправляем двойные фигурные скобки {{ на одинарные {
    cleaned = cleaned.replace(/\{\{/g, '{');
    cleaned = cleaned.replace(/\}\}/g, '}');
    
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
        'mvc': 'mvcOutput',
        'scenario': 'scenarioOutput'
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
    statusEl.style.display = 'block';
    
    // Автоматически скрываем через 5 секунд для success/info
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            statusEl.style.display = 'none';
        }, 5000);
    }
}

/**
 * Парсит Use Case из PlantUML диаграммы прецедентов и отображает их в списке
 */
function parseAndDisplayUseCases() {
    console.log('parseAndDisplayUseCases called');
    const usecaseOutput = document.getElementById('usecaseOutput');
    const usecaseList = document.getElementById('usecaseList');
    const decompositionSection = document.getElementById('usecaseDecompositionSection');
    
    if (!usecaseOutput || !usecaseList || !decompositionSection) {
        console.error('Required elements not found:', {
            usecaseOutput: !!usecaseOutput,
            usecaseList: !!usecaseList,
            decompositionSection: !!decompositionSection
        });
        return;
    }
    
    const useCaseModel = usecaseOutput.value.trim();
    console.log('Use Case Model length:', useCaseModel.length);
    
    if (!useCaseModel || useCaseModel.length === 0) {
        console.log('Use Case Model is empty, hiding section');
        decompositionSection.style.display = 'none';
        return;
    }
    
    // Парсим Use Case из PlantUML
    const useCases = extractUseCasesFromPlantUml(useCaseModel);
    console.log('Extracted Use Cases:', useCases);
    
    if (useCases.length === 0) {
        console.log('No Use Cases found, hiding section');
        decompositionSection.style.display = 'none';
        return;
    }
    
    // Отображаем секцию декомпозиции
    console.log('Displaying decomposition section with', useCases.length, 'Use Cases');
    decompositionSection.style.display = 'block';
    
    // Очищаем список
    usecaseList.innerHTML = '';
    
    // Добавляем Use Case в список
    useCases.forEach((uc, index) => {
        const item = document.createElement('div');
        item.className = 'usecase-item';
        
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `usecase-${index}`;
        checkbox.value = uc.name;
        checkbox.dataset.usecaseId = uc.id || uc.name;
        
        const label = document.createElement('label');
        label.htmlFor = `usecase-${index}`;
        label.textContent = uc.name;
        
        const idSpan = document.createElement('span');
        idSpan.className = 'usecase-id';
        idSpan.textContent = uc.id || `UC${index + 1}`;
        
        item.appendChild(checkbox);
        item.appendChild(label);
        if (uc.id) {
            item.appendChild(idSpan);
        }
        
        usecaseList.appendChild(item);
    });
    
    // Обновляем состояние кнопки
    updateDecomposeButtonState();
}

/**
 * Извлекает Use Case из PlantUML кода
 */
function extractUseCasesFromPlantUml(plantUmlCode) {
    const useCases = [];
    
    if (!plantUmlCode) {
        console.log('extractUseCasesFromPlantUml: plantUmlCode is empty');
        return useCases;
    }
    
    console.log('extractUseCasesFromPlantUml: parsing PlantUML code, length:', plantUmlCode.length);
    
    // Паттерны для поиска Use Case в PlantUML:
    // 1. usecase "Название" as identifier << stereotype >>
    // 2. usecase "Название" as identifier
    // 3. usecase "Название"
    // 4. (actor) --> (usecase "Название")
    // 5. actor --> usecase "Название"
    
    const patterns = [
        // usecase "Название" as ID << stereotype >>
        {
            regex: /usecase\s+"([^"]+)"\s+as\s+(\w+)(?:\s*<<[^>]+>>)?/gi,
            nameIndex: 1,
            idIndex: 2,
            description: 'usecase "Name" as ID <<stereotype>>'
        },
        // usecase "Название" as ID (без стереотипа)
        {
            regex: /usecase\s+"([^"]+)"\s+as\s+(\w+)(?!\s*<<)/gi,
            nameIndex: 1,
            idIndex: 2,
            description: 'usecase "Name" as ID'
        },
        // usecase "Название" << stereotype >> (без as)
        {
            regex: /usecase\s+"([^"]+)"(?:\s*<<[^>]+>>)?/gi,
            nameIndex: 1,
            idIndex: null,
            description: 'usecase "Name" <<stereotype>>'
        },
        // (actor) --> (usecase "Название")
        {
            regex: /\([^)]*\)\s*-->\s*\(usecase\s+"([^"]+)"\)/gi,
            nameIndex: 1,
            idIndex: null,
            description: '(actor) --> (usecase "Name")'
        },
        // actor --> usecase "Название"
        {
            regex: /actor\s+-->\s+usecase\s+"([^"]+)"/gi,
            nameIndex: 1,
            idIndex: null,
            description: 'actor --> usecase "Name"'
        }
    ];
    
    const foundIds = new Set();
    
    patterns.forEach((patternInfo, patternIndex) => {
        const pattern = patternInfo.regex;
        let match;
        let matchCount = 0;
        
        // Сбрасываем lastIndex для глобального регулярного выражения
        pattern.lastIndex = 0;
        
        // Пробуем найти все совпадения
        const allMatches = [];
        while ((match = pattern.exec(plantUmlCode)) !== null) {
            allMatches.push(match);
        }
        
        // Обрабатываем найденные совпадения
        allMatches.forEach(match => {
            matchCount++;
            const name = match[patternInfo.nameIndex];
            const id = patternInfo.idIndex ? match[patternInfo.idIndex] : null;
            
            if (!name || name.trim().length === 0) {
                return;
            }
            
            // Создаем уникальный ключ
            const key = id ? `${id}:${name}` : name;
            
            if (!foundIds.has(key)) {
                foundIds.add(key);
                useCases.push({
                    id: id ? id.trim() : null,
                    name: name.trim()
                });
                console.log(`Found Use Case: "${name.trim()}"${id ? ' (ID: ' + id.trim() + ')' : ''} using pattern: ${patternInfo.description}`);
            }
        });
        
        if (matchCount > 0) {
            console.log(`Pattern ${patternIndex + 1} (${patternInfo.description}) found ${matchCount} matches`);
        }
    });
    
    // Удаляем дубликаты по имени
    const uniqueUseCases = [];
    const seenNames = new Set();
    
    useCases.forEach(uc => {
        if (!seenNames.has(uc.name)) {
            seenNames.add(uc.name);
            uniqueUseCases.push(uc);
        }
    });
    
    console.log(`Total unique Use Cases found: ${uniqueUseCases.length}`);
    return uniqueUseCases;
}

/**
 * Обновляет состояние кнопки "Декомпозировать" в зависимости от выбранных Use Case
 */
function updateDecomposeButtonState() {
    const decomposeButton = document.getElementById('decomposeButton');
    if (!decomposeButton) return;
    
    const checkedBoxes = document.querySelectorAll('.usecase-list input[type="checkbox"]:checked');
    decomposeButton.disabled = checkedBoxes.length === 0;
}

/**
 * Обрабатывает декомпозицию выбранных Use Case
 */
async function handleDecomposition() {
    const checkedBoxes = document.querySelectorAll('.usecase-list input[type="checkbox"]:checked');
    
    if (checkedBoxes.length === 0) {
        showStatus('Выберите хотя бы один Use Case для декомпозиции', 'error');
        return;
    }
    
    const selectedUseCases = Array.from(checkedBoxes).map(cb => ({
        id: cb.dataset.usecaseId,
        name: cb.value
    }));
    
    const decomposeButton = document.getElementById('decomposeButton');
    const btnText = decomposeButton.querySelector('.btn-text');
    const btnLoader = decomposeButton.querySelector('.btn-loader');
    
    // Показываем loader
    decomposeButton.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    try {
        // TODO: Реализовать API endpoint для декомпозиции
        // Пока просто показываем сообщение
        showStatus(`Декомпозиция ${selectedUseCases.length} Use Case будет реализована в следующей версии`, 'info');
        
        // Пример вызова API (когда будет реализован):
        /*
        const response = await fetch(`${API_BASE}/decompose`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                requestId: currentRequestId,
                useCases: selectedUseCases
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        showStatus('Декомпозиция выполнена успешно', 'success');
        */
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка при декомпозиции: ${error.message}`, 'error');
    } finally {
        decomposeButton.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

