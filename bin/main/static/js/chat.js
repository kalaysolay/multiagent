let chatHistory = [];
let currentConversationId = null;

// Инициализация: при наличии conversationId загружаем его историю, иначе — новый разговор (пустой чат)
document.addEventListener('DOMContentLoaded', function() {
    initChatModeSwitcher();
    initChatInput();
    const urlParams = new URLSearchParams(window.location.search);
    const conversationId = urlParams.get('conversationId') || localStorage.getItem('currentConversationId') || null;
    if (conversationId) {
        loadChatHistory(conversationId);
    } else {
        startNewConversation();
    }
});

function initChatModeSwitcher() {
    const radios = document.querySelectorAll('input[name="chatMode"]');
    radios.forEach(radio => {
        radio.addEventListener('change', (e) => {
            if (e.target.value === 'librechat') {
                document.getElementById('simple-chat-container').style.display = 'none';
                document.getElementById('librechat-container').style.display = 'block';
            } else {
                document.getElementById('librechat-container').style.display = 'none';
                document.getElementById('simple-chat-container').style.display = 'flex';
            }
        });
    });
}

function initChatInput() {
    const chatInput = document.getElementById('chatInput');
    const sendButton = document.getElementById('sendChatButton');
    
    sendButton.addEventListener('click', sendMessage);
    
    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
}

async function sendMessage() {
    const input = document.getElementById('chatInput');
    const message = input.value.trim();
    
    if (!message) return;
    
    // Отключаем кнопку и очищаем input
    const sendButton = document.getElementById('sendChatButton');
    sendButton.disabled = true;
    sendButton.textContent = 'Обработка...';
    input.value = '';
    
    // Добавляем сообщение пользователя
    addMessage('user', message);
    
    // Показываем индикатор загрузки
    const loadingDiv = showLoadingIndicator();
    
    try {
        // Получаем ID текущей workflow сессии из URL или localStorage
        const urlParams = new URLSearchParams(window.location.search);
        const workflowSessionId = urlParams.get('sessionId') || localStorage.getItem('currentWorkflowSessionId');
        
        // Отправляем запрос на бэкенд
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message: message,
                history: chatHistory,
                workflowSessionId: workflowSessionId || null,
                conversationId: currentConversationId
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Убираем индикатор загрузки
        if (loadingDiv) loadingDiv.remove();
        
        // Сохраняем conversation ID (для контекста и для загрузки при следующем открытии страницы)
        if (data.conversationId) {
            currentConversationId = data.conversationId;
            localStorage.setItem('currentConversationId', data.conversationId);
        }
        
        // Добавляем ответ ассистента с диаграммами и tool calls
        addAssistantMessage(data.response, data.toolCalls, data.diagrams);
        
        // Обновляем историю
        chatHistory.push({ role: 'user', content: message });
        chatHistory.push({ role: 'assistant', content: data.response });
        
    } catch (error) {
        console.error('Error:', error);
        if (loadingDiv) loadingDiv.remove();
        addMessage('assistant', `Ошибка: ${error.message}. Убедитесь, что API endpoint /api/chat настроен.`);
    } finally {
        sendButton.disabled = false;
        sendButton.textContent = 'Отправить';
        input.focus();
    }
}

function showLoadingIndicator() {
    const messagesContainer = document.getElementById('chatMessages');
    const loadingDiv = document.createElement('div');
    loadingDiv.className = 'message assistant-message loading-message';
    loadingDiv.innerHTML = `
        <p class="message-content">
            <span class="loading-dots">Обрабатываю запрос<span>.</span><span>.</span><span>.</span></span>
        </p>
    `;
    messagesContainer.appendChild(loadingDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
    return loadingDiv;
}

/**
 * Загружает историю разговора по conversationId и отображает её.
 * Вызывать при открытии чата с conversationId (URL/localStorage) или при выборе разговора из списка.
 * @param {string} conversationId - ID разговора (обязателен для загрузки)
 */
async function loadChatHistory(conversationId) {
    if (!conversationId) return;
    try {
        const response = await fetch(`/api/chat/history?conversationId=${encodeURIComponent(conversationId)}&limit=20`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        // Привязываем историю к текущему разговору
        currentConversationId = conversationId;
        chatHistory = [];
        const messagesContainer = document.getElementById('chatMessages');
        messagesContainer.innerHTML = '';
        if (data.messages && data.messages.length > 0) {
            data.messages.forEach(msg => {
                if (msg.role === 'tool') return;
                addMessageFromHistory(msg.role, msg.content, msg.timestamp, msg.toolCalls);
                if (msg.role === 'user' || msg.role === 'assistant') {
                    chatHistory.push({ role: msg.role, content: msg.content });
                }
            });
        }
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    } catch (error) {
        console.error('Error loading chat history:', error);
        currentConversationId = conversationId;
        chatHistory = [];
    }
}

/**
 * Переключиться на другой разговор (для UI списка разговоров).
 * Загружает историю выбранного разговора и обновляет экран.
 */
function selectConversation(conversationId) {
    if (!conversationId) {
        startNewConversation();
        return;
    }
    loadChatHistory(conversationId);
}

function addMessage(role, content) {
    addMessageFromHistory(role, content, new Date().toISOString(), null);
}

function addAssistantMessage(content, toolCalls, diagrams) {
    const messagesContainer = document.getElementById('chatMessages');
    
    // Удаляем системное сообщение, если есть
    const systemMessage = messagesContainer.querySelector('.system-message');
    if (systemMessage && chatHistory.length > 0) {
        systemMessage.remove();
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message assistant-message';
    
    // Основной контент
    const contentP = document.createElement('p');
    contentP.className = 'message-content';
    contentP.innerHTML = formatMessageContent(content);
    messageDiv.appendChild(contentP);
    
    // Tool calls info (если есть)
    if (toolCalls && toolCalls.length > 0) {
        const toolsDiv = document.createElement('div');
        toolsDiv.className = 'tool-calls-info';
        toolsDiv.innerHTML = `
            <details>
                <summary>🔧 Использовано инструментов: ${toolCalls.length}</summary>
                <ul>
                    ${toolCalls.map(tc => `<li><strong>${tc.name}</strong></li>`).join('')}
                </ul>
            </details>
        `;
        messageDiv.appendChild(toolsDiv);
    }
    
    // Диаграммы (если есть)
    if (diagrams && diagrams.length > 0) {
        const diagramsDiv = document.createElement('div');
        diagramsDiv.className = 'diagrams-container';
        
        diagrams.forEach((diagram, index) => {
            const btn = document.createElement('button');
            btn.className = 'diagram-btn';
            btn.textContent = `📊 ${diagram.title || 'Диаграмма ' + (index + 1)}`;
            btn.onclick = () => showDiagram(diagram.code, diagram.title);
            diagramsDiv.appendChild(btn);
        });
        
        messageDiv.appendChild(diagramsDiv);
    }
    
    // Время
    const timeSpan = document.createElement('span');
    timeSpan.className = 'message-time';
    timeSpan.textContent = new Date().toLocaleTimeString('ru-RU');
    messageDiv.appendChild(timeSpan);
    
    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function addMessageFromHistory(role, content, timestamp, toolCalls) {
    const messagesContainer = document.getElementById('chatMessages');
    
    // Удаляем системное сообщение, если есть
    const systemMessage = messagesContainer.querySelector('.system-message');
    if (systemMessage && chatHistory.length > 0) {
        systemMessage.remove();
    }
    
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}-message`;
    
    const contentP = document.createElement('p');
    contentP.className = 'message-content';
    contentP.innerHTML = formatMessageContent(content);
    
    const timeSpan = document.createElement('span');
    timeSpan.className = 'message-time';
    
    // Форматируем время
    if (timestamp) {
        const date = new Date(timestamp);
        timeSpan.textContent = date.toLocaleTimeString('ru-RU');
    } else {
        timeSpan.textContent = new Date().toLocaleTimeString('ru-RU');
    }
    
    messageDiv.appendChild(contentP);
    
    // Tool calls (если есть в истории)
    if (toolCalls && toolCalls.length > 0) {
        const toolsDiv = document.createElement('div');
        toolsDiv.className = 'tool-calls-info';
        toolsDiv.innerHTML = `
            <details>
                <summary>🔧 Использовано инструментов: ${toolCalls.length}</summary>
                <ul>
                    ${toolCalls.map(tc => `<li><strong>${tc.name}</strong></li>`).join('')}
                </ul>
            </details>
        `;
        messageDiv.appendChild(toolsDiv);
    }
    
    messageDiv.appendChild(timeSpan);
    messagesContainer.appendChild(messageDiv);
    
    // Прокручиваем вниз
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

/**
 * Форматирует контент сообщения (поддержка markdown-подобного форматирования)
 */
function formatMessageContent(content) {
    if (!content) return '';
    
    // Экранируем HTML
    let formatted = content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    
    // Преобразуем ```code``` блоки
    formatted = formatted.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
        return `<pre class="code-block"><code class="language-${lang}">${code.trim()}</code></pre>`;
    });
    
    // Преобразуем `inline code`
    formatted = formatted.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');
    
    // Преобразуем **bold**
    formatted = formatted.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    
    // Преобразуем *italic*
    formatted = formatted.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    
    // Преобразуем переносы строк
    formatted = formatted.replace(/\n/g, '<br>');
    
    return formatted;
}

/**
 * Показывает диаграмму в модальном окне
 */
function showDiagram(plantUmlCode, title) {
    // Проверяем, есть ли функция renderAndShowDiagram из diagram-modal.js
    if (typeof window.renderAndShowDiagram === 'function') {
        window.renderAndShowDiagram(plantUmlCode, title || 'Диаграмма');
    } else {
        // Fallback: показываем код в alert
        alert('Модуль diagram-modal не загружен.\n\nPlantUML код:\n' + plantUmlCode);
    }
}

/**
 * Начать новый разговор
 */
function startNewConversation() {
    currentConversationId = null;
    chatHistory = [];
    localStorage.removeItem('currentConversationId');
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.innerHTML = `
        <div class="system-message">
            <p>Начните новый разговор. Вы можете:</p>
            <ul>
                <li>Спросить о статусе workflow сессий</li>
                <li>Попросить сгенерировать доменную модель по нарративу</li>
                <li>Запросить список доступных инструментов</li>
                <li>Задать вопросы по ICONIX методологии</li>
            </ul>
        </div>
    `;
}

