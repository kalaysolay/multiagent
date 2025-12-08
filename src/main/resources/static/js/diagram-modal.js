const RENDER_API_BASE = '/render';

let currentImageData = null;
let currentSvgData = null;
let currentZoom = 1.0;
let originalImageSize = { width: 0, height: 0 };
let isFullscreen = false;

// Инициализация
document.addEventListener('DOMContentLoaded', function() {
    console.log('Diagram modal: DOMContentLoaded');
    initDiagramModalListeners();
    // Задержка для инициализации кнопок, чтобы убедиться, что все элементы загружены
    setTimeout(() => {
        if (window.initViewDiagramButtons) {
            window.initViewDiagramButtons();
        }
    }, 100);
});

function initDiagramModalListeners() {
    // Закрытие модального окна
    const closeBtn = document.getElementById('closeDiagramModal');
    if (closeBtn) {
        closeBtn.addEventListener('click', closeDiagramModal);
    }
    
    const modal = document.getElementById('diagramModal');
    if (modal) {
        // Закрытие по клику вне модального окна
        modal.addEventListener('click', function(e) {
            if (e.target === this) {
                closeDiagramModal();
            }
        });
        
        // Закрытие по Escape
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && modal.style.display !== 'none') {
                closeDiagramModal();
            }
        });
    }
    
    // Кнопки скачивания
    const downloadPng = document.getElementById('downloadPng');
    const downloadSvg = document.getElementById('downloadSvg');
    if (downloadPng) {
        downloadPng.addEventListener('click', downloadPngFile);
    }
    if (downloadSvg) {
        downloadSvg.addEventListener('click', downloadSvgFile);
    }
    
    // Элементы управления размером
    const zoomIn = document.getElementById('zoomIn');
    const zoomOut = document.getElementById('zoomOut');
    const resetZoom = document.getElementById('resetZoom');
    const fullscreen = document.getElementById('fullscreen');
    
    if (zoomIn) zoomIn.addEventListener('click', () => adjustZoom(1.2));
    if (zoomOut) zoomOut.addEventListener('click', () => adjustZoom(0.8));
    if (resetZoom) resetZoom.addEventListener('click', resetZoomLevel);
    if (fullscreen) fullscreen.addEventListener('click', toggleFullscreen);
}

// Делаем функцию доступной глобально
window.initViewDiagramButtons = function initViewDiagramButtons() {
    console.log('Initializing view diagram buttons...');
    
    // Кнопка для доменной модели
    const viewDomainBtn = document.getElementById('viewDomainDiagram');
    const domainOutput = document.getElementById('domainOutput');
    
    if (viewDomainBtn && domainOutput) {
        console.log('Setting up domain diagram button');
        // Проверка при загрузке
        updateButtonState(viewDomainBtn, domainOutput);
        
        // Проверка при изменении текста
        domainOutput.addEventListener('input', () => {
            updateButtonState(viewDomainBtn, domainOutput);
        });
        
        // Удаляем старые обработчики, если есть
        const newDomainBtn = viewDomainBtn.cloneNode(true);
        viewDomainBtn.parentNode.replaceChild(newDomainBtn, viewDomainBtn);
        
        newDomainBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const plantUmlCode = domainOutput.value.trim();
            console.log('Domain diagram button clicked, code length:', plantUmlCode.length);
            if (plantUmlCode) {
                if (window.renderAndShowDiagram) {
                    window.renderAndShowDiagram(plantUmlCode, 'Доменная модель');
                } else {
                    console.error('renderAndShowDiagram not found on window');
                }
            } else {
                console.warn('No PlantUML code in domainOutput');
            }
        });
    } else {
        console.warn('Domain button or output field not found');
    }
    
    // Кнопка для диаграммы прецедентов
    const viewUseCaseBtn = document.getElementById('viewUseCaseDiagram');
    const usecaseOutput = document.getElementById('usecaseOutput');
    
    if (viewUseCaseBtn && usecaseOutput) {
        console.log('Setting up usecase diagram button');
        updateButtonState(viewUseCaseBtn, usecaseOutput);
        usecaseOutput.addEventListener('input', () => {
            updateButtonState(viewUseCaseBtn, usecaseOutput);
        });
        
        const newUseCaseBtn = viewUseCaseBtn.cloneNode(true);
        viewUseCaseBtn.parentNode.replaceChild(newUseCaseBtn, viewUseCaseBtn);
        
        newUseCaseBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const plantUmlCode = usecaseOutput.value.trim();
            console.log('UseCase diagram button clicked, code length:', plantUmlCode.length);
            if (plantUmlCode) {
                if (window.renderAndShowDiagram) {
                    window.renderAndShowDiagram(plantUmlCode, 'Диаграмма прецедентов');
                } else {
                    console.error('renderAndShowDiagram not found on window');
                }
            }
        });
    }
    
    // Кнопка для MVC модели
    const viewMvcBtn = document.getElementById('viewMvcDiagram');
    const mvcOutput = document.getElementById('mvcOutput');
    
    if (viewMvcBtn && mvcOutput) {
        console.log('Setting up MVC diagram button');
        updateButtonState(viewMvcBtn, mvcOutput);
        mvcOutput.addEventListener('input', () => {
            updateButtonState(viewMvcBtn, mvcOutput);
        });
        
        const newMvcBtn = viewMvcBtn.cloneNode(true);
        viewMvcBtn.parentNode.replaceChild(newMvcBtn, viewMvcBtn);
        
        newMvcBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const plantUmlCode = mvcOutput.value.trim();
            console.log('MVC diagram button clicked, code length:', plantUmlCode.length);
            if (plantUmlCode) {
                if (window.renderAndShowDiagram) {
                    window.renderAndShowDiagram(plantUmlCode, 'MVC модель');
                } else {
                    console.error('renderAndShowDiagram not found on window');
                }
            }
        });
    }
    
    console.log('View diagram buttons initialized');
};

function updateButtonState(button, textarea) {
    const hasContent = textarea.value.trim().length > 0;
    button.disabled = !hasContent;
    // Помечаем кнопку, что обработчик установлен
    if (!button.hasAttribute('data-handler-attached')) {
        button.setAttribute('data-handler-attached', 'true');
    }
}

// Делаем функцию доступной глобально
window.renderAndShowDiagram = async function renderAndShowDiagram(plantUmlCode, title) {
    console.log('renderAndShowDiagram called with title:', title, 'code length:', plantUmlCode?.length);
    if (!plantUmlCode) {
        return;
    }
    
    const modal = document.getElementById('diagramModal');
    const modalHeader = modal.querySelector('.modal-header h2');
    const container = document.getElementById('diagramContainer');
    
    // Обновляем заголовок
    if (modalHeader) {
        modalHeader.textContent = title || 'Отрендеренная диаграмма';
    }
    
    // Показываем модальное окно с загрузкой
    container.innerHTML = '<div style="color: white; font-size: 18px;">Рендеринг диаграммы...</div>';
    modal.style.display = 'flex';
    
    try {
        // Рендерим в PNG
        const response = await fetch(`${RENDER_API_BASE}/png`, {
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
            const svgResponse = await fetch(`${RENDER_API_BASE}/svg`, {
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
        
        // Отображаем диаграмму
        displayDiagram(data.image);
        
    } catch (error) {
        console.error('Error:', error);
        container.innerHTML = `<div style="color: #ff4444; font-size: 16px; padding: 20px;">Ошибка при рендеринге: ${error.message}</div>`;
    }
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
}

function closeDiagramModal() {
    const modal = document.getElementById('diagramModal');
    if (modal) {
        modal.style.display = 'none';
        currentImageData = null;
        currentSvgData = null;
        currentZoom = 1.0;
        isFullscreen = false;
        
        // Выходим из полноэкранного режима если активен
        if (document.fullscreenElement) {
            document.exitFullscreen();
        }
    }
}

function downloadPngFile() {
    if (!currentImageData) {
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
    } catch (error) {
        console.error('Error downloading PNG:', error);
    }
}

function downloadSvgFile() {
    if (!currentSvgData) {
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
    } catch (error) {
        console.error('Error downloading SVG:', error);
    }
}

function adjustZoom(factor) {
    currentZoom *= factor;
    currentZoom = Math.max(0.1, Math.min(5.0, currentZoom)); // Ограничиваем от 10% до 500%
    
    const img = document.getElementById('diagramImage');
    if (img) {
        img.style.transform = `scale(${currentZoom})`;
        updateZoomIndicator();
    }
}

function resetZoomLevel() {
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
    const modal = document.getElementById('diagramModal');
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

