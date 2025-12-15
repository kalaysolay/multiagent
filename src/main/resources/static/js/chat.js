let chatHistory = [];

// Инициализация
document.addEventListener('DOMContentLoaded', function() {
    initChatModeSwitcher();
    initChatInput();
    loadChatHistory();
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
    input.value = '';
    
    // Добавляем сообщение пользователя
    addMessage('user', message);
    
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
                workflowSessionId: workflowSessionId || null
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Добавляем ответ ассистента
        addMessage('assistant', data.response);
        
        // Обновляем историю
        chatHistory.push({ role: 'user', content: message });
        chatHistory.push({ role: 'assistant', content: data.response });
        
    } catch (error) {
        console.error('Error:', error);
        addMessage('assistant', `Ошибка: ${error.message}. Убедитесь, что API endpoint /api/chat настроен.`);
    } finally {
        sendButton.disabled = false;
        input.focus();
    }
}

async function loadChatHistory() {
    try {
        const response = await fetch('/api/chat/history');
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.messages && data.messages.length > 0) {
            // Очищаем контейнер сообщений
            const messagesContainer = document.getElementById('chatMessages');
            messagesContainer.innerHTML = '';
            
            // Добавляем сообщения из истории
            data.messages.forEach(msg => {
                addMessageFromHistory(msg.role, msg.content, msg.timestamp);
                // Обновляем историю для контекста
                chatHistory.push({ role: msg.role, content: msg.content });
            });
            
            // Прокручиваем вниз
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    } catch (error) {
        console.error('Error loading chat history:', error);
        // Не показываем ошибку пользователю, просто не загружаем историю
    }
}

function addMessage(role, content) {
    addMessageFromHistory(role, content, new Date().toISOString());
}

function addMessageFromHistory(role, content, timestamp) {
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
    contentP.textContent = content;
    
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
    messageDiv.appendChild(timeSpan);
    messagesContainer.appendChild(messageDiv);
    
    // Прокручиваем вниз
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

