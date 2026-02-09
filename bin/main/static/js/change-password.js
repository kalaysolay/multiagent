/**
 * change-password.js — логика страницы изменения пароля.
 * 
 * Позволяет текущему пользователю изменить свой пароль.
 * Требует указания старого пароля для подтверждения.
 */
(function () {
    'use strict';

    // ----- Ссылки на DOM-элементы -----
    const changePasswordForm = document.getElementById('changePasswordForm');
    const oldPassword = document.getElementById('oldPassword');
    const newPassword = document.getElementById('newPassword');
    const newPasswordConfirm = document.getElementById('newPasswordConfirm');
    const changePasswordButton = document.getElementById('changePasswordButton');
    const statusMessage = document.getElementById('statusMessage');

    // ----- Инициализация -----
    document.addEventListener('DOMContentLoaded', function () {
        // Проверяем аутентификацию
        if (!window.auth || !window.auth.isAuthenticated()) {
            window.location.href = '/login.html';
            return;
        }

        changePasswordForm.addEventListener('submit', handleSubmit);
    });

    // ----- Обработка отправки формы -----
    async function handleSubmit(e) {
        e.preventDefault();

        const oldPass = oldPassword.value;
        const newPass = newPassword.value;
        const newPassConfirm = newPasswordConfirm.value;

        // Очистка предыдущих ошибок
        clearFieldErrors();

        // Валидация
        let hasErrors = false;

        if (!oldPass) {
            showFieldError('oldPasswordError', 'Введите текущий пароль');
            hasErrors = true;
        }

        if (!newPass || newPass.length < 3) {
            showFieldError('newPasswordError', 'Новый пароль должен содержать минимум 3 символа');
            hasErrors = true;
        }

        if (newPass !== newPassConfirm) {
            showFieldError('newPasswordConfirmError', 'Пароли не совпадают');
            hasErrors = true;
        }

        if (hasErrors) {
            return;
        }

        // Отправка запроса
        changePasswordButton.disabled = true;
        changePasswordButton.textContent = 'Изменение...';

        try {
            await changePassword(oldPass, newPass);
        } finally {
            changePasswordButton.disabled = false;
            changePasswordButton.textContent = 'Изменить пароль';
        }
    }

    // ----- Изменение пароля через API -----
    async function changePassword(oldPasswordValue, newPasswordValue) {
        try {
            const fetchFn = window.auth && window.auth.fetch ? window.auth.fetch : fetch;
            const response = await fetchFn('/api/auth/password', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    oldPassword: oldPasswordValue,
                    newPassword: newPasswordValue
                })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'HTTP ' + response.status);
            }

            // Успех
            showStatus('Пароль успешно изменён', 'success');
            changePasswordForm.reset();
            
            // Перенаправление на главную через 2 секунды
            setTimeout(function () {
                window.location.href = '/index.html';
            }, 2000);
        } catch (error) {
            if (error.message.includes('Неверный старый пароль') || error.message.includes('старый пароль')) {
                showFieldError('oldPasswordError', 'Неверный текущий пароль');
            } else {
                showStatus('Ошибка изменения пароля: ' + error.message, 'error');
            }
        }
    }

    // ----- Показать ошибку поля -----
    function showFieldError(fieldId, message) {
        const errorElement = document.getElementById(fieldId);
        errorElement.textContent = message;
        errorElement.classList.add('show');
    }

    // ----- Очистить ошибки полей -----
    function clearFieldErrors() {
        document.getElementById('oldPasswordError').classList.remove('show');
        document.getElementById('newPasswordError').classList.remove('show');
        document.getElementById('newPasswordConfirmError').classList.remove('show');
    }

    // ----- Показать статусное сообщение -----
    function showStatus(message, type) {
        statusMessage.textContent = message;
        statusMessage.className = 'status-message ' + type;
        setTimeout(function () {
            statusMessage.className = 'status-message';
        }, 5000);
    }
})();
