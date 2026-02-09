/**
 * prompts.js — логика страницы управления справочником промптов.
 *
 * Загружает список промптов из API, отображает в таблице.
 * При клике на строку открывается модальное окно для редактирования текста промпта.
 * После сохранения старая версия автоматически попадает в историю (на сервере).
 */
(function () {
    'use strict';

    // ----- Ссылки на DOM-элементы -----
    const loadingIndicator = document.getElementById('loadingIndicator');
    const accessDenied = document.getElementById('accessDenied');
    const statusMessage = document.getElementById('statusMessage');
    const promptsTable = document.getElementById('promptsTable');
    const promptsTableBody = document.getElementById('promptsTableBody');
    const adminSection = document.getElementById('adminSection');

    // Модальное окно
    const editModal = document.getElementById('editModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalCode = document.getElementById('modalCode');
    const promptContent = document.getElementById('promptContent');
    const changeReason = document.getElementById('changeReason');
    const modalClose = document.getElementById('modalClose');
    const modalCancel = document.getElementById('modalCancel');
    const modalSave = document.getElementById('modalSave');

    // Текущий редактируемый код промпта
    let currentPromptCode = null;

    // ----- Инициализация -----
    document.addEventListener('DOMContentLoaded', function () {
        // Показать секцию "Администрирование" в меню, если пользователь — админ
        if (window.auth && window.auth.isAdmin()) {
            adminSection.style.display = '';
        }

        // Проверяем, является ли пользователь администратором
        if (!window.auth || !window.auth.isAdmin()) {
            loadingIndicator.style.display = 'none';
            accessDenied.style.display = '';
            return;
        }

        // Загружаем список промптов
        loadPrompts();

        // Обработчики модального окна
        modalClose.addEventListener('click', closeModal);
        modalCancel.addEventListener('click', closeModal);
        modalSave.addEventListener('click', savePrompt);

        // Закрытие модального окна по клику на оверлей
        editModal.addEventListener('click', function (e) {
            if (e.target === editModal) {
                closeModal();
            }
        });

        // Закрытие по Escape
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && editModal.classList.contains('active')) {
                closeModal();
            }
        });
    });

    // ----- Загрузка списка промптов -----
    async function loadPrompts() {
        try {
            const response = await fetch('/api/prompts');

            if (response.status === 403) {
                loadingIndicator.style.display = 'none';
                accessDenied.style.display = '';
                return;
            }

            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            const prompts = await response.json();
            renderPromptsTable(prompts);

            loadingIndicator.style.display = 'none';
            promptsTable.style.display = '';
        } catch (error) {
            loadingIndicator.textContent = 'Ошибка загрузки промптов: ' + error.message;
        }
    }

    // ----- Рендеринг таблицы промптов -----
    function renderPromptsTable(prompts) {
        promptsTableBody.innerHTML = '';

        prompts.forEach(function (prompt) {
            var row = document.createElement('tr');
            row.addEventListener('click', function () {
                openEditModal(prompt.code);
            });

            // Код
            var codeCell = document.createElement('td');
            var codeSpan = document.createElement('span');
            codeSpan.className = 'prompt-code';
            codeSpan.textContent = prompt.code;
            codeCell.appendChild(codeSpan);
            row.appendChild(codeCell);

            // Название
            var nameCell = document.createElement('td');
            nameCell.textContent = prompt.name;
            row.appendChild(nameCell);

            // Описание
            var descCell = document.createElement('td');
            descCell.textContent = prompt.description || '—';
            descCell.style.maxWidth = '300px';
            descCell.style.overflow = 'hidden';
            descCell.style.textOverflow = 'ellipsis';
            descCell.style.whiteSpace = 'nowrap';
            row.appendChild(descCell);

            // Размер
            var sizeCell = document.createElement('td');
            sizeCell.className = 'prompt-size';
            sizeCell.textContent = formatSize(prompt.contentLength);
            row.appendChild(sizeCell);

            // Дата обновления
            var dateCell = document.createElement('td');
            dateCell.className = 'prompt-date';
            dateCell.textContent = prompt.updatedAt ? formatDate(prompt.updatedAt) : '—';
            row.appendChild(dateCell);

            promptsTableBody.appendChild(row);
        });
    }

    // ----- Открытие модального окна для редактирования -----
    async function openEditModal(code) {
        currentPromptCode = code;
        modalTitle.textContent = 'Редактирование: ' + code;
        modalCode.textContent = code;
        promptContent.value = 'Загрузка...';
        changeReason.value = '';
        modalSave.disabled = true;

        editModal.classList.add('active');

        try {
            var response = await fetch('/api/prompts/' + encodeURIComponent(code));

            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            var data = await response.json();
            promptContent.value = data.content;
            modalSave.disabled = false;
        } catch (error) {
            promptContent.value = 'Ошибка загрузки: ' + error.message;
        }
    }

    // ----- Сохранение промпта -----
    async function savePrompt() {
        if (!currentPromptCode) return;

        var content = promptContent.value;
        var reason = changeReason.value.trim();

        if (!content) {
            showStatus('Текст промпта не может быть пустым', 'error');
            return;
        }

        modalSave.disabled = true;
        modalSave.textContent = 'Сохранение...';

        try {
            var response = await fetch('/api/prompts/' + encodeURIComponent(currentPromptCode), {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    content: content,
                    reason: reason || null
                })
            });

            if (!response.ok) {
                var errorData = await response.json();
                throw new Error(errorData.error || 'HTTP ' + response.status);
            }

            closeModal();
            showStatus('Промпт "' + currentPromptCode + '" успешно обновлён', 'success');
            // Перезагружаем таблицу для обновления дат
            loadPrompts();
        } catch (error) {
            showStatus('Ошибка сохранения: ' + error.message, 'error');
        } finally {
            modalSave.disabled = false;
            modalSave.textContent = 'Сохранить';
        }
    }

    // ----- Закрытие модального окна -----
    function closeModal() {
        editModal.classList.remove('active');
        currentPromptCode = null;
    }

    // ----- Показать статусное сообщение -----
    function showStatus(message, type) {
        statusMessage.textContent = message;
        statusMessage.className = 'status-message ' + type;
        // Автоматически скрываем через 5 секунд
        setTimeout(function () {
            statusMessage.className = 'status-message';
        }, 5000);
    }

    // ----- Форматирование размера (символы) -----
    function formatSize(chars) {
        if (chars < 1000) return chars + ' симв.';
        if (chars < 1000000) return (chars / 1000).toFixed(1) + 'K симв.';
        return (chars / 1000000).toFixed(2) + 'M симв.';
    }

    // ----- Форматирование даты -----
    function formatDate(isoString) {
        try {
            var d = new Date(isoString);
            return d.toLocaleDateString('ru-RU') + ' ' + d.toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return isoString;
        }
    }
})();
