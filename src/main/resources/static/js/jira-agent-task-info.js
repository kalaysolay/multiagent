(function() {
    'use strict';

    const API_ANALYZE = '/api/jira/analyze/task';

    document.addEventListener('DOMContentLoaded', function() {
        const issueKeyInput = document.getElementById('issueKeyInput');
        const analyzeBtn = document.getElementById('analyzeButton');
        const resultEl = document.getElementById('analysisResult');

        if (!analyzeBtn || !resultEl) return;

        analyzeBtn.addEventListener('click', async function() {
            const issueKey = issueKeyInput && issueKeyInput.value ? issueKeyInput.value.trim() : '';
            if (!issueKey) {
                resultEl.textContent = 'Введите номер задачи (например PROJ-12345).';
                resultEl.className = 'jira-agent-result error';
                return;
            }

            const btnText = analyzeBtn.querySelector('.btn-text');
            const btnLoader = analyzeBtn.querySelector('.btn-loader');
            analyzeBtn.disabled = true;
            if (btnText) btnText.style.display = 'none';
            if (btnLoader) btnLoader.style.display = 'flex';
            resultEl.textContent = 'Запрос к LLM...';
            resultEl.className = 'jira-agent-result placeholder';

            try {
                const token = localStorage.getItem('authToken');
                const headers = {
                    'Content-Type': 'application/json'
                };
                if (token) headers['Authorization'] = 'Bearer ' + token;

                const response = await fetch(API_ANALYZE, {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({ issueKey: issueKey })
                });
                const data = await response.json().catch(function() { return {}; });

                if (!response.ok) {
                    resultEl.textContent = data.error || 'Ошибка ' + response.status;
                    resultEl.className = 'jira-agent-result error';
                    return;
                }
                resultEl.textContent = data.response != null ? data.response : '';
                resultEl.className = 'jira-agent-result';
            } catch (e) {
                resultEl.textContent = 'Ошибка запроса: ' + (e.message || String(e));
                resultEl.className = 'jira-agent-result error';
            } finally {
                analyzeBtn.disabled = false;
                if (btnText) btnText.style.display = 'block';
                if (btnLoader) btnLoader.style.display = 'none';
            }
        });
    });
})();
