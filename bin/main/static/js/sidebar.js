/**
 * Модуль для динамической генерации бокового меню (сайдбара) на всех страницах портала.
 * 
 * Этот модуль решает проблему дублирования HTML кода сайдбара на каждой странице.
 * Теперь меню генерируется централизованно, что упрощает поддержку и добавление новых разделов.
 * 
 * Основные возможности:
 * - Автоматическое определение активного пункта меню на основе текущего URL
 * - Динамическое отображение раздела "Администрирование" только для администраторов
 * - Единая точка управления структурой меню
 * 
 * @module sidebar
 */
(function() {
    'use strict';

    /**
     * Конфигурация структуры меню.
     * 
     * Каждый раздел содержит:
     * - title: заголовок раздела (отображается в меню)
     * - items: массив пунктов меню
     * 
     * Каждый пункт меню содержит:
     * - text: текст ссылки
     * - href: URL страницы
     * - activePaths: массив путей, при которых этот пункт должен быть активным
     *   (например, ['/index.html', '/'] означает, что пункт активен на главной странице)
     * 
     * @type {Object}
     */
    const menuConfig = {
        // Раздел "Документация"
        documentation: {
            title: 'Документация',
            items: [
                {
                    text: 'User Story',
                    href: '/index.html',
                    activePaths: ['/index.html', '/', '/index.html#'] // Активен на главной странице
                },
                {
                    text: 'Требования',
                    href: '#',
                    activePaths: [] // Пока не реализовано
                },
                {
                    text: 'Статьи',
                    href: '#',
                    activePaths: [] // Пока не реализовано
                },
                {
                    text: 'Рендерер PUML',
                    href: '/render.html',
                    activePaths: ['/render.html'] // Активен на странице рендерера
                }
            ]
        },
        // Раздел "Агенты"
        agents: {
            title: 'Агенты',
            items: [
                {
                    text: 'ICONIX Agent',
                    href: '/iconix-agent-list.html',
                    activePaths: ['/iconix-agent-list.html', '/iconix-agent-detail.html'] // Активен на страницах ICONIX агента
                },
                {
                    text: 'Анализатор требований',
                    href: '#',
                    activePaths: [] // Пока не реализовано
                },
                {
                    text: 'Jira Agent',
                    href: '#',
                    activePaths: [] // Пока не реализовано
                },
                {
                    text: 'Git Analyser Agent',
                    href: '/git-analyser.html',
                    activePaths: ['/git-analyser.html'] // Активен на странице Git Analyser
                }
            ]
        },
        // Раздел "Чаты"
        chats: {
            title: 'Чаты',
            items: [
                {
                    text: 'Chat with LLM',
                    href: '/chat.html',
                    activePaths: ['/chat.html'] // Активен на странице чата
                }
            ]
        },
        // Раздел "Администрирование" (отображается только для администраторов)
        admin: {
            title: 'Администрирование',
            items: [
                {
                    text: 'Промпты',
                    href: '/prompts.html',
                    activePaths: ['/prompts.html']
                },
                {
                    text: 'Векторное хранилище',
                    href: '/vector-store.html',
                    activePaths: ['/vector-store.html']
                }
            ]
        }
    };

    /**
     * Определяет, является ли указанный путь активным для текущей страницы.
     * 
     * Функция сравнивает текущий путь страницы (window.location.pathname) с массивом
     * путей, при которых пункт меню должен быть активным.
     * 
     * Примеры:
     * - Если текущая страница '/chat.html', а activePaths = ['/chat.html'], вернёт true
     * - Если текущая страница '/index.html', а activePaths = ['/index.html', '/'], вернёт true
     * 
     * @param {string[]} activePaths - Массив путей, при которых пункт меню активен
     * @param {string} currentPath - Текущий путь страницы (по умолчанию window.location.pathname)
     * @returns {boolean} true, если текущий путь совпадает с одним из activePaths
     */
    function isActivePath(activePaths, currentPath) {
        // Если массив путей пуст, пункт никогда не будет активным
        if (!activePaths || activePaths.length === 0) {
            return false;
        }

        // Нормализуем текущий путь: убираем начальный и конечный слэш для корректного сравнения
        const normalizedCurrentPath = currentPath.replace(/^\/|\/$/g, '') || 'index.html';
        
        // Проверяем каждый путь из массива activePaths
        return activePaths.some(path => {
            // Нормализуем путь из конфигурации
            const normalizedPath = path.replace(/^\/|\/$/g, '') || 'index.html';
            
            // Сравниваем нормализованные пути
            return normalizedCurrentPath === normalizedPath;
        });
    }

    /**
     * Генерирует HTML для одного пункта меню.
     * 
     * Создаёт элемент <li> с ссылкой <a>, добавляя класс 'active',
     * если текущая страница соответствует одному из activePaths пункта.
     * 
     * @param {Object} item - Объект пункта меню из конфигурации
     * @param {string} currentPath - Текущий путь страницы
     * @returns {string} HTML строка для пункта меню
     */
    function renderMenuItem(item, currentPath) {
        // Определяем, должен ли этот пункт быть активным
        const isActive = isActivePath(item.activePaths, currentPath);
        
        // Формируем класс для ссылки: 'menu-item' всегда, 'active' - если пункт активен
        const menuItemClass = 'menu-item' + (isActive ? ' active' : '');
        
        // Генерируем HTML для пункта меню
        return `<li><a href="${item.href}" class="${menuItemClass}">${item.text}</a></li>`;
    }

    /**
     * Генерирует HTML для одного раздела меню.
     * 
     * Создаёт структуру:
     * <div class="menu-section">
     *   <div class="menu-section-title">Заголовок раздела</div>
     *   <ul class="menu-items">
     *     ... пункты меню ...
     *   </ul>
     * </div>
     * 
     * @param {Object} section - Объект раздела из конфигурации (содержит title и items)
     * @param {string} currentPath - Текущий путь страницы
     * @returns {string} HTML строка для раздела меню
     */
    function renderMenuSection(section, currentPath) {
        // Генерируем HTML для всех пунктов меню в разделе
        const menuItemsHtml = section.items
            .map(item => renderMenuItem(item, currentPath))
            .join('\n                        '); // Отступы для читаемости HTML

        // Формируем полный HTML раздела
        return `
                <div class="menu-section">
                    <div class="menu-section-title">${section.title}</div>
                    <ul class="menu-items">
                        ${menuItemsHtml}
                    </ul>
                </div>`;
    }

    /**
     * Генерирует полный HTML сайдбара.
     * 
     * Создаёт структуру:
     * <aside class="sidebar">
     *   <div class="sidebar-header">...</div>
     *   <nav class="sidebar-menu">...</nav>
     * </aside>
     * 
     * Включает все разделы меню из конфигурации, кроме раздела "Администрирование",
     * который добавляется только для администраторов.
     * 
     * @param {string} currentPath - Текущий путь страницы
     * @param {boolean} isAdmin - Флаг, указывающий, является ли пользователь администратором
     * @returns {string} HTML строка для всего сайдбара
     */
    function renderSidebar(currentPath, isAdmin) {
        // Генерируем HTML для основных разделов (Документация, Агенты, Чаты)
        const documentationSection = renderMenuSection(menuConfig.documentation, currentPath);
        const agentsSection = renderMenuSection(menuConfig.agents, currentPath);
        const chatsSection = renderMenuSection(menuConfig.chats, currentPath);

        // Генерируем HTML для раздела "Администрирование" только если пользователь - администратор
        let adminSectionHtml = '';
        if (isAdmin) {
            // Раздел администрирования не имеет id="adminSection" и style="display: none",
            // так как он добавляется только для админов
            adminSectionHtml = renderMenuSection(menuConfig.admin, currentPath);
        }

        // Формируем полный HTML сайдбара
        return `
        <aside class="sidebar">
            <div class="sidebar-header">
                <img src="/images/analyzer-logo.png" alt="ANALYZER Logo" class="logo">
                <h2>Портал аналитика</h2>
            </div>
            
            <nav class="sidebar-menu">
                ${documentationSection}
                
                ${agentsSection}
                
                ${chatsSection}
                
                ${adminSectionHtml}
            </nav>
        </aside>`;
    }

    /**
     * Инициализирует сайдбар на странице.
     * 
     * Эта функция:
     * 1. Ожидает загрузки модуля auth.js (который предоставляет window.auth)
     * 2. Получает текущий путь страницы
     * 3. Проверяет, является ли пользователь администратором
     * 4. Генерирует HTML сайдбара и вставляет его в контейнер
     * 
     * Функция вызывается автоматически при загрузке DOM или после загрузки auth.js.
     */
    function init() {
        // Ищем контейнер для сайдбара на странице
        const sidebarContainer = document.getElementById('sidebar-container');
        
        // Если контейнер не найден, значит на этой странице не нужен сайдбар
        if (!sidebarContainer) {
            return;
        }

        // Получаем текущий путь страницы
        const currentPath = window.location.pathname;

        // Проверяем, загружен ли модуль auth.js
        // window.auth предоставляется модулем auth.js и содержит функции для работы с аутентификацией
        let isAdmin = false;
        if (window.auth && typeof window.auth.isAdmin === 'function') {
            // Если модуль auth.js загружен, проверяем права администратора
            isAdmin = window.auth.isAdmin();
        } else {
            // Если auth.js ещё не загружен, ждём его загрузки
            // Это может произойти, если sidebar.js загрузился раньше auth.js
            // В этом случае повторно вызываем init() после небольшой задержки
            setTimeout(init, 50);
            return;
        }

        // Генерируем HTML сайдбара
        const sidebarHtml = renderSidebar(currentPath, isAdmin);

        // Вставляем сгенерированный HTML в контейнер
        sidebarContainer.innerHTML = sidebarHtml;
    }

    // Инициализируем сайдбар при загрузке DOM
    // DOMContentLoaded срабатывает, когда HTML документ полностью загружен и распарсен
    if (document.readyState === 'loading') {
        // Если DOM ещё загружается, ждём события DOMContentLoaded
        document.addEventListener('DOMContentLoaded', init);
    } else {
        // Если DOM уже загружен, вызываем init() сразу
        // Это может произойти, если скрипт загрузился после полной загрузки страницы
        init();
    }
})();
