/**
 * users.js — логика страницы управления пользователями.
 * 
 * Позволяет администраторам:
 * - Просматривать список всех пользователей
 * - Создавать новых пользователей
 * - Мягко удалять пользователей
 * - Восстанавливать удалённых пользователей
 */
(function () {
    'use strict';

    // ----- Ссылки на DOM-элементы -----
    const loadingIndicator = document.getElementById('loadingIndicator');
    const accessDenied = document.getElementById('accessDenied');
    const statusMessage = document.getElementById('statusMessage');
    const usersTable = document.getElementById('usersTable');
    const usersTableBody = document.getElementById('usersTableBody');
    const usersControls = document.getElementById('usersControls');
    const includeDeletedCheckbox = document.getElementById('includeDeletedCheckbox');
    const addUserButton = document.getElementById('addUserButton');

    // Модальное окно создания
    const createUserModal = document.getElementById('createUserModal');
    const closeCreateModal = document.getElementById('closeCreateModal');
    const cancelCreateButton = document.getElementById('cancelCreateButton');
    const saveCreateButton = document.getElementById('saveCreateButton');
    const newUsername = document.getElementById('newUsername');
    const newPassword = document.getElementById('newPassword');
    const newPasswordConfirm = document.getElementById('newPasswordConfirm');
    const newUserIsAdmin = document.getElementById('newUserIsAdmin');

    // ----- Инициализация -----
    document.addEventListener('DOMContentLoaded', function () {
        // Проверяем, является ли пользователь администратором
        if (!window.auth || !window.auth.isAdmin()) {
            loadingIndicator.style.display = 'none';
            accessDenied.style.display = '';
            return;
        }

        // Загружаем список пользователей
        loadUsers(false);

        // Обработчики
        includeDeletedCheckbox.addEventListener('change', function () {
            loadUsers(includeDeletedCheckbox.checked);
        });

        addUserButton.addEventListener('click', openCreateUserModal);
        closeCreateModal.addEventListener('click', closeCreateUserModal);
        cancelCreateButton.addEventListener('click', closeCreateUserModal);
        saveCreateButton.addEventListener('click', createUser);

        // Закрытие модального окна по клику на оверлей
        createUserModal.addEventListener('click', function (e) {
            if (e.target === createUserModal) {
                closeCreateUserModal();
            }
        });

        // Закрытие по Escape
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && createUserModal.classList.contains('active')) {
                closeCreateUserModal();
            }
        });
    });

    // ----- Загрузка списка пользователей -----
    async function loadUsers(includeDeleted) {
        try {
            const fetchFn = window.auth && window.auth.fetch ? window.auth.fetch : fetch;
            const response = await fetchFn(`/api/users?includeDeleted=${includeDeleted}`);

            if (response.status === 403) {
                loadingIndicator.style.display = 'none';
                accessDenied.style.display = '';
                return;
            }

            if (response.status === 401) {
                // Неаутентифицирован - перенаправляем на логин
                window.location.href = '/login.html';
                return;
            }

            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }

            // Проверяем, что ответ действительно JSON
            const contentType = response.headers.get('content-type');
            if (!contentType || !contentType.includes('application/json')) {
                throw new Error('Сервер вернул не JSON ответ. Возможно, требуется аутентификация.');
            }

            const users = await response.json();
            renderUsersTable(users);

            loadingIndicator.style.display = 'none';
            usersControls.style.display = '';
            usersTable.style.display = '';
        } catch (error) {
            // Если ошибка парсинга JSON, значит пришёл HTML (редирект на логин)
            if (error.message.includes('JSON') || error.message.includes('<!DOCTYPE')) {
                loadingIndicator.style.display = 'none';
                accessDenied.style.display = '';
                accessDenied.innerHTML = '<h2>Требуется аутентификация</h2><p>Пожалуйста, войдите в систему для доступа к этой странице.</p><p style="margin-top: 16px;"><a href="/login.html" class="back-link">Перейти на страницу входа</a></p>';
            } else {
                loadingIndicator.textContent = 'Ошибка загрузки пользователей: ' + error.message;
            }
        }
    }

    // ----- Рендеринг таблицы пользователей -----
    function renderUsersTable(users) {
        usersTableBody.innerHTML = '';

        if (users.length === 0) {
            const row = document.createElement('tr');
            row.innerHTML = '<td colspan="5" style="text-align: center; color: #86868b;">Пользователи не найдены</td>';
            usersTableBody.appendChild(row);
            return;
        }

        users.forEach(function (user) {
            const row = document.createElement('tr');
            const isDeleted = user.deletedAt && user.deletedAt !== '';
            
            if (isDeleted) {
                row.classList.add('deleted');
            }

            // Имя пользователя
            const usernameCell = document.createElement('td');
            usernameCell.textContent = user.username;
            row.appendChild(usernameCell);

            // Роль
            const roleCell = document.createElement('td');
            const roleSpan = document.createElement('span');
            roleSpan.className = 'user-role ' + (user.isAdmin ? 'admin' : 'user');
            roleSpan.textContent = user.isAdmin ? 'Администратор' : 'Пользователь';
            roleCell.appendChild(roleSpan);
            row.appendChild(roleCell);

            // Статус
            const statusCell = document.createElement('td');
            const statusSpan = document.createElement('span');
            if (isDeleted) {
                statusSpan.className = 'user-status deleted';
                statusSpan.textContent = 'Удалён';
            } else if (!user.enabled) {
                statusSpan.className = 'user-status disabled';
                statusSpan.textContent = 'Отключён';
            } else {
                statusSpan.className = 'user-status active';
                statusSpan.textContent = 'Активен';
            }
            statusCell.appendChild(statusSpan);
            row.appendChild(statusCell);

            // Дата создания
            const dateCell = document.createElement('td');
            dateCell.textContent = formatDate(user.createdAt);
            row.appendChild(dateCell);

            // Действия
            const actionsCell = document.createElement('td');
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'user-actions';

            if (isDeleted) {
                const restoreButton = document.createElement('button');
                restoreButton.className = 'btn-action restore';
                restoreButton.textContent = 'Восстановить';
                restoreButton.addEventListener('click', function () {
                    if (confirm('Восстановить пользователя ' + user.username + '?')) {
                        restoreUser(user.id);
                    }
                });
                actionsDiv.appendChild(restoreButton);
            } else {
                const deleteButton = document.createElement('button');
                deleteButton.className = 'btn-action delete';
                deleteButton.textContent = 'Удалить';
                deleteButton.addEventListener('click', function () {
                    if (confirm('Удалить пользователя ' + user.username + '? Это действие можно отменить.')) {
                        deleteUser(user.id);
                    }
                });
                actionsDiv.appendChild(deleteButton);
            }

            actionsCell.appendChild(actionsDiv);
            row.appendChild(actionsCell);

            usersTableBody.appendChild(row);
        });
    }

    // ----- Открытие модального окна создания -----
    function openCreateUserModal() {
        newUsername.value = '';
        newPassword.value = '';
        newPasswordConfirm.value = '';
        newUserIsAdmin.checked = false;
        clearFieldErrors();
        createUserModal.classList.add('active');
    }

    // ----- Закрытие модального окна создания -----
    function closeCreateUserModal() {
        createUserModal.classList.remove('active');
        clearFieldErrors();
    }

    // ----- Очистка ошибок полей -----
    function clearFieldErrors() {
        document.getElementById('usernameError').classList.remove('show');
        document.getElementById('passwordError').classList.remove('show');
        document.getElementById('passwordConfirmError').classList.remove('show');
    }

    // ----- Создание пользователя -----
    async function createUser() {
        const username = newUsername.value.trim();
        const password = newPassword.value;
        const passwordConfirm = newPasswordConfirm.value;
        const isAdmin = newUserIsAdmin.checked;

        clearFieldErrors();

        // Валидация
        let hasErrors = false;

        if (!username || username.length < 3) {
            document.getElementById('usernameError').textContent = 'Имя пользователя должно содержать минимум 3 символа';
            document.getElementById('usernameError').classList.add('show');
            hasErrors = true;
        }

        if (!password || password.length < 3) {
            document.getElementById('passwordError').textContent = 'Пароль должен содержать минимум 3 символа';
            document.getElementById('passwordError').classList.add('show');
            hasErrors = true;
        }

        if (password !== passwordConfirm) {
            document.getElementById('passwordConfirmError').textContent = 'Пароли не совпадают';
            document.getElementById('passwordConfirmError').classList.add('show');
            hasErrors = true;
        }

        if (hasErrors) {
            return;
        }

        saveCreateButton.disabled = true;
        saveCreateButton.textContent = 'Создание...';

        try {
            const fetchFn = window.auth && window.auth.fetch ? window.auth.fetch : fetch;
            const response = await fetchFn('/api/users', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password,
                    isAdmin: isAdmin
                })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'HTTP ' + response.status);
            }

            closeCreateUserModal();
            showStatus('Пользователь "' + username + '" успешно создан', 'success');
            loadUsers(includeDeletedCheckbox.checked);
        } catch (error) {
            if (error.message.includes('уже существует')) {
                document.getElementById('usernameError').textContent = error.message;
                document.getElementById('usernameError').classList.add('show');
            } else {
                showStatus('Ошибка создания пользователя: ' + error.message, 'error');
            }
        } finally {
            saveCreateButton.disabled = false;
            saveCreateButton.textContent = 'Создать';
        }
    }

    // ----- Удаление пользователя -----
    async function deleteUser(userId) {
        try {
            const fetchFn = window.auth && window.auth.fetch ? window.auth.fetch : fetch;
            const response = await fetchFn('/api/users/' + userId, {
                method: 'DELETE'
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'HTTP ' + response.status);
            }

            showStatus('Пользователь удалён', 'success');
            loadUsers(includeDeletedCheckbox.checked);
        } catch (error) {
            showStatus('Ошибка удаления: ' + error.message, 'error');
        }
    }

    // ----- Восстановление пользователя -----
    async function restoreUser(userId) {
        try {
            const fetchFn = window.auth && window.auth.fetch ? window.auth.fetch : fetch;
            const response = await fetchFn('/api/users/' + userId + '/restore', {
                method: 'PUT'
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'HTTP ' + response.status);
            }

            showStatus('Пользователь восстановлен', 'success');
            loadUsers(includeDeletedCheckbox.checked);
        } catch (error) {
            showStatus('Ошибка восстановления: ' + error.message, 'error');
        }
    }

    // ----- Показать статусное сообщение -----
    function showStatus(message, type) {
        statusMessage.textContent = message;
        statusMessage.className = 'status-message ' + type;
        setTimeout(function () {
            statusMessage.className = 'status-message';
        }, 5000);
    }

    // ----- Форматирование даты -----
    function formatDate(isoString) {
        try {
            const d = new Date(isoString);
            return d.toLocaleDateString('ru-RU') + ' ' + d.toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return isoString || '—';
        }
    }
})();
