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
            window.__lastSessionData = data;

            // Заполняем поля данными из сессии
            if (data.artifacts) {
                // Поле ввода «Цель / Запрос» — только исходная цель пользователя, не подменяем нарративом
                if (data.artifacts.goal) {
                    document.getElementById('narrativeInput').value = cleanText(data.artifacts.goal);
                }
                // Результат генерации нарратива — в поле вывода
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
            
            // Загружаем декомпозированные сценарии, если есть
            // Используем requestId, который уже объявлен выше в функции
            if (requestId) {
                loadDecomposedArtifacts(requestId);
            }
            
            // Убеждаемся, что обработчики кликов установлены (если они еще не установлены)
            ensureDiagramButtonHandlers();

            // Показываем/скрываем кнопки документации в зависимости от статуса и наличия доков
            updateDocumentationButtons(data);

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

    // Документация: кнопки и модальные окна
    const createDocBtn = document.getElementById('createDocumentationBtn');
    const viewDocBtn = document.getElementById('viewDocumentationBtn');
    if (createDocBtn) createDocBtn.addEventListener('click', openDocumentationGenerateModal);
    if (viewDocBtn) viewDocBtn.addEventListener('click', openDocumentationPage);
    const closeGenModal = document.getElementById('closeDocumentationGenerateModal');
    if (closeGenModal) closeGenModal.addEventListener('click', closeDocumentationGenerateModal);
    const generateDocBtn = document.getElementById('generateDocumentationBtn');
    if (generateDocBtn) generateDocBtn.addEventListener('click', handleGenerateDocumentation);
    const genModal = document.getElementById('documentationGenerateModal');
    if (genModal) genModal.addEventListener('click', function(e) { if (e.target === genModal) closeDocumentationGenerateModal(); });

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
    const goalText = document.getElementById('narrativeInput').value.trim();
    
    if (!goalText) {
        showStatus('Пожалуйста, введите цель или запрос', 'error');
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
                goal: goalText
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
    window.__lastSessionData = data;
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
    
    // Заполняем поля результатами; цель пользователя не перезаписываем нарративом
    if (data.artifacts) {
        if (data.artifacts.goal) {
            document.getElementById('narrativeInput').value = cleanText(data.artifacts.goal);
        }
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

    // Кнопки документации (Создать / Документация)
    updateDocumentationButtons(data);

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
        // Приоритетный паттерн - извлекает и ID, и стереотип
        {
            regex: /usecase\s+"([^"]+)"\s+as\s+(\w+)(?:\s*<<\s*([^>]+)\s*>>)?/gi,
            nameIndex: 1,
            idIndex: 2,
            stereotypeIndex: 3,
            description: 'usecase "Name" as ID <<stereotype>>'
        },
        // usecase "Название" << stereotype >> (без as)
        {
            regex: /usecase\s+"([^"]+)"(?:\s*<<\s*([^>]+)\s*>>)?/gi,
            nameIndex: 1,
            idIndex: null,
            stereotypeIndex: 2,
            description: 'usecase "Name" <<stereotype>>'
        }
        // Примечание: убрали паттерны без стереотипов и связи с акторами,
        // так как показываем только Use Case с << base >> стереотипом
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
            
            // Извлекаем стереотип из match (если есть индекс для стереотипа) или из полного совпадения
            let stereotype = null;
            if (patternInfo.stereotypeIndex && match[patternInfo.stereotypeIndex]) {
                stereotype = match[patternInfo.stereotypeIndex].trim().toLowerCase();
            } else {
                // Пробуем извлечь из полного совпадения как fallback
                const fullMatch = match[0];
                const stereotypeMatch = fullMatch.match(/<<\s*([^>]+)\s*>>/i);
                if (stereotypeMatch) {
                    stereotype = stereotypeMatch[1].trim().toLowerCase();
                }
            }
            
            // Фильтруем: показываем только Use Case со стереотипом << base >>
            // Регистронезависимая проверка
            if (!stereotype || stereotype !== 'base') {
                if (stereotype) {
                    console.log(`Skipping Use Case "${name.trim()}" with stereotype "${stereotype}" (only << base >> are shown)`);
                } else {
                    console.log(`Skipping Use Case "${name.trim()}" without stereotype (only << base >> are shown)`);
                }
                return;
            }
            
            // Создаем уникальный ключ
            const key = id ? `${id}:${name}` : name;
            
            if (!foundIds.has(key)) {
                foundIds.add(key);
                useCases.push({
                    id: id ? id.trim() : null,
                    name: name.trim(),
                    stereotype: stereotype
                });
                console.log(`Found Base Use Case: "${name.trim()}"${id ? ' (ID: ' + id.trim() + ')' : ''} using pattern: ${patternInfo.description}`);
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
    
    // Получаем данные о выбранных Use Case (алиас и название)
    const selectedUseCases = Array.from(checkedBoxes).map(cb => {
        const item = cb.closest('.usecase-item');
        return {
            alias: cb.dataset.usecaseId || cb.value,
            name: item ? item.querySelector('label').textContent.trim() : cb.value
        };
    });
    
    console.log('Decomposing Use Cases:', selectedUseCases);
    showStatus(`Запускаем декомпозицию для: ${selectedUseCases.map(uc => uc.name).join(', ')}`, 'info');
    
    const decomposeButton = document.getElementById('decomposeButton');
    const btnText = decomposeButton.querySelector('.btn-text');
    const btnLoader = decomposeButton.querySelector('.btn-loader');
    
    // Показываем loader
    decomposeButton.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    try {
        // Получаем requestId из URL или из сохраненной сессии
        const urlParams = new URLSearchParams(window.location.search);
        const requestId = urlParams.get('requestId') || getCurrentRequestId();
        
        if (!requestId) {
            throw new Error('Request ID не найден. Убедитесь, что вы находитесь на странице деталей workflow сессии.');
        }
        
        // Вызываем API для декомпозиции
        const response = await fetch('/api/usecase/decomposition', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                requestId: requestId,
                useCases: selectedUseCases
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Обновляем отображение сценариев и MVC
        await loadDecomposedArtifacts(requestId);
        
        // Показываем результаты
        const successCount = data.results.filter(r => r.success).length;
        const failCount = data.results.filter(r => !r.success).length;
        
        if (failCount === 0) {
            showStatus(`Декомпозиция завершена успешно для ${successCount} Use Case`, 'success');
        } else {
            showStatus(`Декомпозиция завершена: ${successCount} успешно, ${failCount} с ошибками`, 'warning');
        }
        
        // Снимаем выделение с чекбоксов после успешной декомпозиции
        checkedBoxes.forEach(cb => {
            if (data.results.find(r => r.useCaseAlias === cb.dataset.usecaseId && r.success)) {
                cb.checked = false;
            }
        });
        updateDecomposeButtonState();
        
    } catch (error) {
        console.error('Decomposition failed:', error);
        showStatus(`Ошибка декомпозиции: ${error.message}`, 'error');
    } finally {
        decomposeButton.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

/**
 * Загружает декомпозированные сценарии и MVC для текущей workflow сессии
 */
async function loadDecomposedArtifacts(requestId) {
    try {
        const response = await fetch(`/api/usecase/decomposition/${requestId}`);
        
        if (!response.ok) {
            console.warn('Failed to load decomposition artifacts:', response.status);
            return;
        }
        
        const data = await response.json();
        displayScenarios(data.scenarios || []);
        displayMvcDiagrams(data.mvcDiagrams || []);
        
    } catch (error) {
        console.error('Error loading decomposition artifacts:', error);
    }
}

/**
 * Отображает декомпозированные сценарии в правом контейнере
 */
function displayScenarios(scenarios) {
    const container = document.getElementById('scenariosContainer');
    if (!container) return;
    
    if (!scenarios || scenarios.length === 0) {
        container.innerHTML = '<div class="scenarios-empty"><p>Выберите Use Case и нажмите "Декомпозировать" для генерации сценариев</p></div>';
        return;
    }
    
    container.innerHTML = scenarios.map(scenario => {
        const createdAt = new Date(scenario.createdAt).toLocaleString('ru-RU');
        const updatedAt = new Date(scenario.updatedAt).toLocaleString('ru-RU');
        
        return `
            <div class="scenario-item" data-scenario-id="${scenario.id}">
                <div class="scenario-header">
                    <div class="scenario-title">${escapeHtml(scenario.useCaseName || 'Без названия')}</div>
                    ${scenario.useCaseAlias ? `<div class="scenario-alias">${escapeHtml(scenario.useCaseAlias)}</div>` : ''}
                </div>
                <div class="scenario-meta">
                    Создан: ${createdAt}${scenario.updatedAt !== scenario.createdAt ? ` | Обновлен: ${updatedAt}` : ''}
                </div>
                <div class="scenario-content">${escapeHtml(scenario.scenarioContent)}</div>
                <div class="scenario-actions">
                    <button onclick="showDocumentFromScenario('${scenario.id}')" 
                            ${scenario.scenarioContent && scenario.scenarioContent.trim().length > 0 ? '' : 'disabled'}
                            class="btn-view-document">Показать документ</button>
                    <button onclick="copyScenario('${scenario.id}')">Копировать</button>
                    <button onclick="downloadScenario('${scenario.id}', '${escapeHtml(scenario.useCaseName || 'scenario')}')">Скачать</button>
                </div>
            </div>
        `;
    }).join('');
}

/**
 * Отображает декомпозированные MVC-диаграммы в контейнере
 */
function displayMvcDiagrams(mvcDiagrams) {
    const container = document.getElementById('mvcDiagramsContainer');
    if (!container) return;
    
    if (!mvcDiagrams || mvcDiagrams.length === 0) {
        container.innerHTML = '<div class="mvc-empty"><p>Выберите Use Case и нажмите "Декомпозировать" для генерации MVC</p></div>';
        return;
    }
    
    container.innerHTML = mvcDiagrams.map(mvc => {
        const createdAt = new Date(mvc.createdAt).toLocaleString('ru-RU');
        const updatedAt = new Date(mvc.updatedAt).toLocaleString('ru-RU');
        const title = mvc.useCaseName || 'MVC';
        const safeTitle = escapeHtml(title);
        return `
            <div class="mvc-item scenario-item" data-mvc-id="${escapeHtml(mvc.id)}">
                <div class="scenario-header">
                    <div class="scenario-title">${escapeHtml(mvc.useCaseName || 'Без названия')}</div>
                    ${mvc.useCaseAlias ? `<div class="scenario-alias">${escapeHtml(mvc.useCaseAlias)}</div>` : ''}
                </div>
                <div class="scenario-meta">
                    Создан: ${createdAt}${mvc.updatedAt !== mvc.createdAt ? ` | Обновлен: ${updatedAt}` : ''}
                </div>
                <pre class="mvc-plantuml">${escapeHtml(mvc.mvcPlantuml || '')}</pre>
                <div class="scenario-actions">
                    <button type="button" class="btn-view-document mvc-view-diagram">Просмотреть диаграмму</button>
                    <button type="button" class="mvc-copy">Копировать</button>
                    <button type="button" class="mvc-download">Скачать</button>
                </div>
            </div>
        `;
    }).join('');
    
    // Привязываем обработчики к кнопкам MVC
    container.querySelectorAll('.mvc-item').forEach(item => {
        const mvcId = item.dataset.mvcId;
        const plantumlEl = item.querySelector('.mvc-plantuml');
        const plantumlCode = plantumlEl ? plantumlEl.textContent : '';
        const useCaseName = item.querySelector('.scenario-title')?.textContent || 'MVC';
        
        item.querySelector('.mvc-view-diagram')?.addEventListener('click', () => {
            if (plantumlCode && plantumlCode.trim() && typeof window.renderAndShowDiagram === 'function') {
                window.renderAndShowDiagram(plantumlCode.trim(), 'MVC: ' + useCaseName);
            } else {
                showStatus('Нет данных диаграммы для отображения', 'warning');
            }
        });
        item.querySelector('.mvc-copy')?.addEventListener('click', () => copyMvcDiagram(mvcId));
        item.querySelector('.mvc-download')?.addEventListener('click', () => downloadMvcDiagram(mvcId, useCaseName));
    });
}

function copyMvcDiagram(mvcId) {
    const item = document.querySelector(`[data-mvc-id="${mvcId}"]`);
    if (!item) return;
    const content = item.querySelector('.mvc-plantuml')?.textContent || '';
    navigator.clipboard.writeText(content).then(() => {
        showStatus('MVC-диаграмма скопирована в буфер обмена', 'success');
    }).catch(err => {
        console.error('Failed to copy:', err);
        showStatus('Ошибка копирования', 'error');
    });
}

function downloadMvcDiagram(mvcId, useCaseName) {
    const item = document.querySelector(`[data-mvc-id="${mvcId}"]`);
    if (!item) return;
    const content = item.querySelector('.mvc-plantuml')?.textContent || '';
    const safeName = (useCaseName || 'mvc').replace(/[^a-z0-9\u0400-\u04FF]/gi, '_');
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${safeName}_mvc.puml`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showStatus('MVC-диаграмма скачана', 'success');
}

/**
 * Получает текущий requestId из URL или из сохраненной сессии
 */
function getCurrentRequestId() {
    const urlParams = new URLSearchParams(window.location.search);
    let requestId = urlParams.get('requestId');
    
    if (!requestId) {
        // Пробуем получить из URL страницы (например, /workflow/detail?requestId=...)
        const match = window.location.search.match(/requestId=([^&]+)/);
        if (match) {
            requestId = match[1];
        }
    }
    
    if (!requestId) {
        // Пробуем получить из сохраненных данных сессии
        const sessionData = sessionStorage.getItem('currentWorkflowSession');
        if (sessionData) {
            try {
                const data = JSON.parse(sessionData);
                requestId = data.requestId;
            } catch (e) {
                console.warn('Failed to parse session data:', e);
            }
        }
    }
    
    return requestId;
}

/**
 * Экранирует HTML для безопасного отображения
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Копирует сценарий в буфер обмена
 */
function copyScenario(scenarioId) {
    const scenarioItem = document.querySelector(`[data-scenario-id="${scenarioId}"]`);
    if (!scenarioItem) return;
    
    const content = scenarioItem.querySelector('.scenario-content').textContent;
    navigator.clipboard.writeText(content).then(() => {
        showStatus('Сценарий скопирован в буфер обмена', 'success');
    }).catch(err => {
        console.error('Failed to copy:', err);
        showStatus('Ошибка копирования', 'error');
    });
}

/**
 * Скачивает сценарий как файл
 */
function downloadScenario(scenarioId, useCaseName) {
    const scenarioItem = document.querySelector(`[data-scenario-id="${scenarioId}"]`);
    if (!scenarioItem) return;
    
    const content = scenarioItem.querySelector('.scenario-content').textContent;
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${useCaseName.replace(/[^a-z0-9]/gi, '_')}_scenario.adoc`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    showStatus('Сценарий скачан', 'success');
}

/**
 * Показывает документ из сценария в модальном окне
 */
function showDocumentFromScenario(scenarioId) {
    const scenarioItem = document.querySelector(`[data-scenario-id="${scenarioId}"]`);
    if (!scenarioItem) {
        console.warn('Scenario item not found:', scenarioId);
        return;
    }
    
    const content = scenarioItem.querySelector('.scenario-content').textContent;
    if (!content || content.trim().length === 0) {
        showStatus('Сценарий пуст, невозможно отобразить документ', 'warning');
        return;
    }
    
    const title = scenarioItem.querySelector('.scenario-title').textContent;
    
    // Используем глобальную функцию из document-modal.js
    if (window.showDocument) {
        window.showDocument(content, title);
    } else {
        console.error('showDocument function not available');
        showStatus('Ошибка: функция отображения документа не загружена', 'error');
    }
}

// --- Документация: генерация и просмотр ---

const DOCS_API = '/api/usecase/documentation';

/** Показывает или скрывает блок с кнопками «Создать документацию» и «Документация» по данным сессии. */
function updateDocumentationButtons(data) {
    const section = document.getElementById('documentationActionsSection');
    const createBtn = document.getElementById('createDocumentationBtn');
    const viewBtn = document.getElementById('viewDocumentationBtn');
    if (!section || !createBtn || !viewBtn) return;

    const status = data?.artifacts?._status;
    const hasDocs = data?.artifacts?.hasGeneratedDocs === true;

    const showSection = status === 'COMPLETED' || hasDocs;
    section.style.display = showSection ? 'flex' : 'none';
    createBtn.style.display = status === 'COMPLETED' ? 'inline-flex' : 'none';
    viewBtn.style.display = hasDocs ? 'inline-flex' : 'none';
}

function openDocumentationGenerateModal() {
    // Если уже есть доки — предзаполняем имя папки для удобного обновления
    const folderInput = document.getElementById('documentationFolderName');
    const data = window.__lastSessionData;
    if (data?.artifacts?.documentationFolderName) {
        folderInput.value = data.artifacts.documentationFolderName;
    } else {
        folderInput.value = '';
    }
    document.getElementById('documentationGenerateError').style.display = 'none';
    document.getElementById('documentationGenerateError').textContent = '';
    document.getElementById('documentationGenerateModal').style.display = 'flex';
    folderInput.focus();
}

function closeDocumentationGenerateModal() {
    document.getElementById('documentationGenerateModal').style.display = 'none';
}

/** Открывает отдельную страницу просмотра документации с текущим requestId. */
function openDocumentationPage() {
    const requestId = getCurrentRequestId();
    if (!requestId) {
        showStatus('Нет активной сессии', 'error');
        return;
    }
    window.location.href = '/iconix-documentation.html?requestId=' + encodeURIComponent(requestId);
}

async function handleGenerateDocumentation() {
    const requestId = getCurrentRequestId();
    if (!requestId) {
        showStatus('Нет активной сессии', 'error');
        return;
    }
    const folderName = document.getElementById('documentationFolderName').value.trim();
    if (!folderName) {
        document.getElementById('documentationGenerateError').textContent = 'Введите название папки.';
        document.getElementById('documentationGenerateError').style.display = 'block';
        return;
    }
    if (!/^[a-zA-Z0-9_-]+$/.test(folderName)) {
        document.getElementById('documentationGenerateError').textContent = 'Только буквы, цифры, дефис и подчёркивание.';
        document.getElementById('documentationGenerateError').style.display = 'block';
        return;
    }

    const btn = document.getElementById('generateDocumentationBtn');
    const btnText = btn.querySelector('.btn-text');
    const btnLoader = btn.querySelector('.btn-loader');
    document.getElementById('documentationGenerateError').style.display = 'none';
    btn.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';

    try {
        const res = await fetch(DOCS_API + '/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ requestId: requestId, folderName: folderName })
        });
        const body = await res.json().catch(() => ({}));
        if (res.ok) {
            closeDocumentationGenerateModal();
            showStatus(body.updated ? 'Документация обновлена в каталоге «' + folderName + '»' : 'Документация успешно сгенерирована в каталоге «' + folderName + '»', 'success');
            document.getElementById('viewDocumentationBtn').style.display = 'inline-flex';
            document.getElementById('documentationActionsSection').style.display = 'flex';
        } else if (res.status === 409) {
            document.getElementById('documentationGenerateError').textContent = body.error || 'Каталог с таким именем уже существует. Укажите другое имя.';
            document.getElementById('documentationGenerateError').style.display = 'block';
        } else {
            document.getElementById('documentationGenerateError').textContent = body.error || 'Ошибка генерации.';
            document.getElementById('documentationGenerateError').style.display = 'block';
        }
    } catch (e) {
        document.getElementById('documentationGenerateError').textContent = 'Ошибка сети: ' + e.message;
        document.getElementById('documentationGenerateError').style.display = 'block';
    } finally {
        btn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}
