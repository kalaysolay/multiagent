// Утилита для работы с аутентификацией
(function() {
    'use strict';
    
    // Сохраняем оригинальный fetch
    const originalFetch = window.fetch;
    
    // Получить токен из localStorage
    function getAuthToken() {
        return localStorage.getItem('authToken');
    }
    
    // Проверить наличие токена
    function isAuthenticated() {
        return !!getAuthToken();
    }
    
    // Получить заголовки с токеном для API запросов
    function getAuthHeaders() {
        const token = getAuthToken();
        if (!token) {
            return {};
        }
        return {
            'Authorization': 'Bearer ' + token
        };
    }
    
    // Перехватываем все fetch запросы
    window.fetch = function(url, options = {}) {
        // Определяем, является ли это API запросом
        const urlString = typeof url === 'string' ? url : (url && url.url ? url.url : '');
        const isApiRequest = urlString.startsWith('/api/') || 
                            urlString.startsWith('/workflow/') || 
                            urlString.startsWith('/render/') ||
                            urlString.startsWith('/chat/') ||
                            urlString.startsWith('/git/') ||
                            urlString.startsWith('/vector-store/');
        
        // Для API запросов добавляем токен
        if (isApiRequest) {
            const token = getAuthToken();
            if (token) {
                options = options || {};
                options.headers = {
                    ...options.headers,
                    'Authorization': 'Bearer ' + token
                };
            }
        }
        
        // Выполняем запрос
        return originalFetch(url, options)
            .then(response => {
                // Если получили 401, перенаправляем на страницу входа
                if (response.status === 401 && isApiRequest) {
                    localStorage.removeItem('authToken');
                    localStorage.removeItem('username');
                    // Только если мы не на странице логина
                    if (!window.location.pathname.includes('login.html')) {
                        window.location.href = '/login.html';
                    }
                }
                return response;
            });
    };
    
    // Выполнить авторизованный fetch запрос (для обратной совместимости)
    async function authenticatedFetch(url, options = {}) {
        return window.fetch(url, options);
    }
    
    // Проверить, является ли текущий пользователь администратором
    function isAdmin() {
        return localStorage.getItem('isAdmin') === 'true';
    }
    
    // Выход из системы
    function logout() {
        localStorage.removeItem('authToken');
        localStorage.removeItem('username');
        localStorage.removeItem('isAdmin');
        window.location.href = '/login.html';
    }
    
    // Проверка аутентификации при загрузке страницы
    function checkAuth() {
        // Не проверяем на странице логина
        if (window.location.pathname.includes('login.html')) {
            return;
        }
        
        const token = getAuthToken();
        if (!token) {
            window.location.href = '/login.html';
            return;
        }
        
        // Проверяем валидность токена и обновляем флаг isAdmin
        fetch('/api/auth/me', {
            headers: {
                'Authorization': 'Bearer ' + token
            }
        })
        .then(response => {
            if (!response.ok) {
                localStorage.removeItem('authToken');
                localStorage.removeItem('username');
                localStorage.removeItem('isAdmin');
                window.location.href = '/login.html';
            }
            return response.json();
        })
        .then(data => {
            if (data && data.isAdmin !== undefined) {
                localStorage.setItem('isAdmin', data.isAdmin === true ? 'true' : 'false');
            }
        })
        .catch(() => {
            localStorage.removeItem('authToken');
            localStorage.removeItem('username');
            localStorage.removeItem('isAdmin');
            window.location.href = '/login.html';
        });
    }
    
    // Экспорт функций в глобальную область видимости
    window.auth = {
        getToken: getAuthToken,
        isAuthenticated: isAuthenticated,
        isAdmin: isAdmin,
        getAuthHeaders: getAuthHeaders,
        fetch: authenticatedFetch,
        logout: logout,
        checkAuth: checkAuth
    };
    
    // Автоматическая проверка при загрузке (кроме страницы логина)
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkAuth);
    } else {
        checkAuth();
    }
})();
