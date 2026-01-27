class QaAgentChat {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.urlInput = document.getElementById('urlInput');
        this.userInput = document.getElementById('userInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.modelSelect = document.getElementById('modelSelect'); // Added
        this.stompClient = null;
        this.currentAssistantMessage = null;
        this.isProcessing = false;
        
        this.connect();
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
    }
    
    connect() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null;
        
        this.stompClient.connect({}, (frame) => {
            console.log('WebSocket 연결됨');
            this.loadModels(); // Added
            
            this.stompClient.subscribe('/user/queue/response', (message) => {
                const response = JSON.parse(message.body);
                if (response.done) {
                    this.finalizeCurrentMessage();
                } else {
                    this.appendToCurrentMessage(response.content);
                }
            });
            
            this.stompClient.subscribe('/user/queue/error', (message) => {
                const error = JSON.parse(message.body);
                this.showError(error.error);
            });
        }, (error) => {
            console.error('WebSocket 연결 실패:', error);
            setTimeout(() => this.connect(), 3000);
        });
    }

    async loadModels() { // Added
        try {
            const response = await fetch('/api/models');
            const models = await response.json();
            
            this.modelSelect.innerHTML = ''; // Clear existing options
            models.forEach(model => {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                this.modelSelect.appendChild(option);
            });
            // Set default selected model (e.g., the first one or a specific one)
            if (models.length > 0) {
                this.modelSelect.value = 'gemini-2.5-flash'; // Or models[0] if you prefer the first in list
            }
        } catch (error) {
            console.error('모델 목록을 불러오는 데 실패했습니다:', error);
            // Optionally, disable model selection or show an error to the user
        }
    }
    
    handleSubmit(e) {
        e.preventDefault();
        if (this.isProcessing) return;
        
        const url = this.urlInput.value.trim();
        const message = this.userInput.value.trim();
        const model = this.modelSelect.value; // Get selected model // Added
        
        if (!url || !message) return;
        
        this.isProcessing = true;
        this.sendBtn.disabled = true;
        
        this.addMessage('user', `URL: ${url}\n요청: ${message}\n모델: ${model}`); // Updated
        this.userInput.value = '';
        // URL input is intentionally kept to facilitate repeated tests on the same URL
        
        this.currentAssistantMessage = this.addMessage('assistant', '');
        this.addTypingIndicator();
        
        this.stompClient.send('/app/chat', {}, JSON.stringify({ url, message, model })); // Updated
    }
    
    addMessage(role, content) {
        const div = document.createElement('div');
        div.className = `message ${role}`;
        
        let avatarEmoji = '';
        if (role === 'assistant') {
            avatarEmoji = '🤖';
        } else if (role === 'user') {
            avatarEmoji = '👤'; // Or a different emoji for user
        }

        div.innerHTML = `<div class="avatar">${avatarEmoji}</div><div class="content">${this.formatContent(content)}</div>`;
        this.messagesContainer.appendChild(div);
        this.scrollToBottom();
        return div.querySelector('.content');
    }
    
    appendToCurrentMessage(chunk) {
        if (this.currentAssistantMessage) {
            this.removeTypingIndicator();
            const currentText = this.currentAssistantMessage.getAttribute('data-raw') || '';
            const newText = currentText + chunk;
            this.currentAssistantMessage.setAttribute('data-raw', newText);
            this.currentAssistantMessage.innerHTML = this.formatContent(newText);
            this.scrollToBottom();
            this.addTypingIndicator(); // Re-add typing indicator at the end
        }
    }
    
    finalizeCurrentMessage() {
        this.removeTypingIndicator();
        this.currentAssistantMessage = null;
        this.isProcessing = false;
        this.sendBtn.disabled = false;
        this.userInput.focus();
    }
    
    addTypingIndicator() {
        if (this.currentAssistantMessage && !this.currentAssistantMessage.querySelector('.typing')) {
            const indicator = document.createElement('span');
            indicator.className = 'typing';
            indicator.textContent = '●●●';
            this.currentAssistantMessage.appendChild(indicator);
        }
    }
    
    removeTypingIndicator() {
        const typing = this.currentAssistantMessage?.querySelector('.typing');
        if (typing) typing.remove();
    }
    
    showError(error) {
        this.removeTypingIndicator();
        if (this.currentAssistantMessage) {
            this.currentAssistantMessage.innerHTML = `<span class="error">❌ 오류: ${error}</span>`;
        }
        this.finalizeCurrentMessage();
    }
    
    formatContent(content) {
        if (!content) return '';
        return content
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/\n/g, '<br>')
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/\[(High|Medium|Low)\]/g, '<span class="badge badge-$1">[$1]</span>');
    }
    
    scrollToBottom() {
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }
}

document.addEventListener('DOMContentLoaded', () => new QaAgentChat());

