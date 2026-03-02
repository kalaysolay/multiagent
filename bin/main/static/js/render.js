const API_BASE = '/render';

let currentImageData = null;
let currentSvgData = null;
let currentZoom = 1.0;
let originalImageSize = { width: 0, height: 0 };
let isFullscreen = false;

// Инициализация
document.addEventListener('DOMContentLoaded', function() {
    initEventListeners();
});

function initEventListeners() {
    // Кнопка рендеринга
    document.getElementById('renderButton').addEventListener('click', renderDiagram);
    
    // Кнопка валидации
    document.getElementById('validateButton').addEventListener('click', validatePlantUml);
    
    // Кнопка очистки
    document.getElementById('clearButton').addEventListener('click', clearInput);
    
    // Закрытие модального окна
    document.getElementById('closeModal').addEventListener('click', closeModal);
    
    // Закрытие по клику вне модального окна
    document.getElementById('modal').addEventListener('click', function(e) {
        if (e.target === this) {
            closeModal();
        }
    });
    
    // Закрытие по Escape
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            closeModal();
        }
    });
    
    // Кнопки скачивания
    document.getElementById('downloadPng').addEventListener('click', downloadPng);
    document.getElementById('downloadSvg').addEventListener('click', downloadSvg);
    
    // Элементы управления размером
    document.getElementById('zoomIn').addEventListener('click', () => adjustZoom(1.2));
    document.getElementById('zoomOut').addEventListener('click', () => adjustZoom(0.8));
    document.getElementById('resetZoom').addEventListener('click', resetZoom);
    document.getElementById('fullscreen').addEventListener('click', toggleFullscreen);
}

/**
 * Очищает PlantUML код от markdown блоков и исправляет экранирование
 */
function cleanPlantUmlCode(code) {
    if (!code) return '';
    
    let cleaned = String(code);
    
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

async function renderDiagram() {
    let plantUmlCode = document.getElementById('plantUmlInput').value.trim();
    
    if (!plantUmlCode) {
        showStatus('Пожалуйста, введите код PlantUML', 'error');
        return;
    }
    
    // Очищаем PlantUML код перед рендерингом
    plantUmlCode = cleanPlantUmlCode(plantUmlCode);
    
    const renderBtn = document.getElementById('renderButton');
    const btnText = renderBtn.querySelector('.btn-text');
    const btnLoader = renderBtn.querySelector('.btn-loader');
    
    // Показываем loader
    renderBtn.disabled = true;
    btnText.style.display = 'none';
    btnLoader.style.display = 'flex';
    
    try {
        // Рендерим в PNG
        const response = await fetch(`${API_BASE}/png`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ plantUml: plantUmlCode })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.error) {
            throw new Error(data.error);
        }
        
        // Сохраняем данные для скачивания
        currentImageData = data.image;
        
        // Также получаем SVG для скачивания
        try {
            const svgResponse = await fetch(`${API_BASE}/svg`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ plantUml: plantUmlCode })
            });
            
            if (svgResponse.ok) {
                const svgData = await svgResponse.json();
                currentSvgData = svgData.svg;
            }
        } catch (e) {
            console.warn('Не удалось получить SVG:', e);
        }
        
        // Отображаем диаграмму в модальном окне
        displayDiagram(data.image);
        showStatus('Диаграмма успешно отрендерена', 'success');
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка при рендеринге: ${error.message}`, 'error');
    } finally {
        renderBtn.disabled = false;
        btnText.style.display = 'block';
        btnLoader.style.display = 'none';
    }
}

async function validatePlantUml() {
    let plantUmlCode = document.getElementById('plantUmlInput').value.trim();
    
    if (!plantUmlCode) {
        showStatus('Пожалуйста, введите код PlantUML', 'error');
        return;
    }
    
    // Очищаем PlantUML код перед валидацией
    plantUmlCode = cleanPlantUmlCode(plantUmlCode);
    
    const validateBtn = document.getElementById('validateButton');
    const validationResult = document.getElementById('validationResult');
    const validationDetails = document.getElementById('validationDetails');
    
    validateBtn.disabled = true;
    validationResult.style.display = 'none';
    
    try {
        const response = await fetch(`${API_BASE}/validate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ plantUml: plantUmlCode })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Отображаем детальный результат валидации
        validationResult.style.display = 'block';
        
        if (data.valid) {
            showStatus('Синтаксис PlantUML корректен', 'success');
            validationResult.className = 'validation-result validation-success';
            validationDetails.innerHTML = `
                <div class="validation-item success">
                    <span class="validation-icon">✓</span>
                    <span class="validation-text">Синтаксис PlantUML корректен. Диаграмма может быть отрендерена.</span>
                </div>
            `;
        } else {
            showStatus('Обнаружены ошибки в синтаксисе PlantUML', 'error');
            validationResult.className = 'validation-result validation-error';
            const errorMessage = data.error || 'Обнаружены ошибки в синтаксисе';
            validationDetails.innerHTML = `
                <div class="validation-item error">
                    <span class="validation-icon">✗</span>
                    <span class="validation-text">${escapeHtml(errorMessage)}</span>
                </div>
            `;
        }
        
    } catch (error) {
        console.error('Error:', error);
        showStatus(`Ошибка при валидации: ${error.message}`, 'error');
        validationResult.style.display = 'block';
        validationResult.className = 'validation-result validation-error';
        validationDetails.innerHTML = `
            <div class="validation-item error">
                <span class="validation-icon">✗</span>
                <span class="validation-text">Ошибка при проверке: ${escapeHtml(error.message)}</span>
            </div>
        `;
    } finally {
        validateBtn.disabled = false;
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function displayDiagram(imageData) {
    const container = document.getElementById('diagramContainer');
    const img = document.createElement('img');
    img.src = imageData;
    img.alt = 'PlantUML Diagram';
    img.id = 'diagramImage';
    img.style.transform = `scale(${currentZoom})`;
    img.style.transformOrigin = 'center center';
    img.style.transition = 'transform 0.2s ease';
    
    // Сохраняем оригинальный размер после загрузки
    img.onload = function() {
        originalImageSize.width = this.naturalWidth;
        originalImageSize.height = this.naturalHeight;
    };
    
    container.innerHTML = '';
    container.appendChild(img);
    
    // Сбрасываем zoom при открытии новой диаграммы
    currentZoom = 1.0;
    updateZoomIndicator();
    
    // Показываем модальное окно
    document.getElementById('modal').style.display = 'flex';
}

function closeModal() {
    document.getElementById('modal').style.display = 'none';
    currentImageData = null;
    currentSvgData = null;
    currentZoom = 1.0;
    isFullscreen = false;
    
    // Выходим из полноэкранного режима если активен
    if (document.fullscreenElement) {
        document.exitFullscreen();
    }
}

function clearInput() {
    document.getElementById('plantUmlInput').value = '';
    showStatus('Поле очищено', 'info');
    setTimeout(() => {
        const statusEl = document.getElementById('statusMessage');
        statusEl.style.display = 'none';
    }, 2000);
}

function downloadPng() {
    if (!currentImageData) {
        showStatus('Нет данных для скачивания', 'error');
        return;
    }
    
    try {
        // Извлекаем base64 данные
        const base64Data = currentImageData.split(',')[1];
        const byteCharacters = atob(base64Data);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
            byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: 'image/png' });
        
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'diagram.png';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        showStatus('PNG файл скачан', 'success');
    } catch (error) {
        console.error('Error downloading PNG:', error);
        showStatus('Ошибка при скачивании PNG', 'error');
    }
}

function downloadSvg() {
    if (!currentSvgData) {
        showStatus('SVG данные недоступны', 'error');
        return;
    }
    
    try {
        const blob = new Blob([currentSvgData], { type: 'image/svg+xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'diagram.svg';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        showStatus('SVG файл скачан', 'success');
    } catch (error) {
        console.error('Error downloading SVG:', error);
        showStatus('Ошибка при скачивании SVG', 'error');
    }
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

// Функции управления размером диаграммы
function adjustZoom(factor) {
    currentZoom *= factor;
    currentZoom = Math.max(0.1, Math.min(5.0, currentZoom)); // Ограничиваем от 10% до 500%
    
    const img = document.getElementById('diagramImage');
    if (img) {
        img.style.transform = `scale(${currentZoom})`;
        updateZoomIndicator();
    }
}

function resetZoom() {
    currentZoom = 1.0;
    const img = document.getElementById('diagramImage');
    if (img) {
        img.style.transform = `scale(${currentZoom})`;
        updateZoomIndicator();
    }
}

function updateZoomIndicator() {
    const indicator = document.getElementById('zoomLevel');
    if (indicator) {
        indicator.textContent = Math.round(currentZoom * 100) + '%';
    }
}

function toggleFullscreen() {
    const modal = document.getElementById('modal');
    const modalContent = modal.querySelector('.modal-content');
    
    if (!isFullscreen) {
        // Входим в полноэкранный режим
        if (modalContent.requestFullscreen) {
            modalContent.requestFullscreen();
        } else if (modalContent.webkitRequestFullscreen) {
            modalContent.webkitRequestFullscreen();
        } else if (modalContent.msRequestFullscreen) {
            modalContent.msRequestFullscreen();
        }
        isFullscreen = true;
    } else {
        // Выходим из полноэкранного режима
        if (document.exitFullscreen) {
            document.exitFullscreen();
        } else if (document.webkitExitFullscreen) {
            document.webkitExitFullscreen();
        } else if (document.msExitFullscreen) {
            document.msExitFullscreen();
        }
        isFullscreen = false;
    }
}

// Обработчик изменения полноэкранного режима
document.addEventListener('fullscreenchange', function() {
    isFullscreen = !!document.fullscreenElement;
});

document.addEventListener('webkitfullscreenchange', function() {
    isFullscreen = !!document.webkitFullscreenElement;
});

document.addEventListener('msfullscreenchange', function() {
    isFullscreen = !!document.msFullscreenElement;
});

