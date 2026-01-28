class QaAgentChat {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        // this.urlInput = document.getElementById('urlInput'); // Removed from UI
        this.userInput = document.getElementById('userInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.modelSelect = document.getElementById('modelSelect'); // Added
        this.stompClient = null;
        this.currentAssistantMessage = null;
        this.isProcessing = false;
        
        // Modal elements
        this.loadPromptBtn = document.getElementById('loadPromptBtn');
        this.promptHistoryModal = document.getElementById('promptHistoryModal');
        this.closeModalBtn = this.promptHistoryModal.querySelector('.close-button');
        this.promptList = document.getElementById('promptList');
        
        this.connect();
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        
        // Auto-resize textarea
        this.userInput.addEventListener('input', () => {
            this.userInput.style.height = 'auto';
            this.userInput.style.height = (this.userInput.scrollHeight) + 'px';
        });

        // Add keypress listener to userInput for Enter key submission
        this.userInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) { // Prevent new line on Shift+Enter
                e.preventDefault(); // Prevent default new line behavior in textarea/input
                this.chatForm.dispatchEvent(new Event('submit'));
            }
        });

        // Event listeners for the modal
        this.loadPromptBtn.addEventListener('click', () => this.openPromptHistoryModal());
        this.closeModalBtn.addEventListener('click', () => this.closePromptHistoryModal());
        window.addEventListener('click', (event) => {
            if (event.target == this.promptHistoryModal) {
                this.closePromptHistoryModal();
            }
        });
    }
    
    connect() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null;
        
        this.stompClient.connect({}, (frame) => {
            console.log('WebSocket 연결됨, frame:', frame);
            this.loadModels(); // Added
            
            const sessionId = /\/([^/]+)\/websocket/.exec(socket._transport.url)[1];
            console.log('STOMP session ID:', sessionId);

            this.stompClient.subscribe('/user/queue/response', (message) => {
                console.log('Received response from server (STOMP /user/queue/response):', message.body);
                const response = JSON.parse(message.body);
                if (response.done === true) {
                    this.finalizeCurrentMessage();
                } else {
                    this.appendToCurrentMessage(response.content);
                }
            });

            this.stompClient.subscribe('/topic/response-' + sessionId, (message) => {
                console.log('Received response from server (STOMP /topic/response-' + sessionId + '):', message.body);
                const response = JSON.parse(message.body);
                if (response.done === true) {
                    this.finalizeCurrentMessage();
                } else {
                    // Only append if not already appended by /user/queue/response to avoid duplicates if both work
                    // But here we suspect /user/queue/response is NOT working.
                    this.appendToCurrentMessage(response.content);
                }
            });
            
            this.stompClient.subscribe('/user/queue/error', (message) => {
                console.log('Received error from server (STOMP /user/queue/error):', message.body);
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

            // Add a disabled placeholder option
            const placeholderOption = document.createElement('option');
            placeholderOption.value = '';
            placeholderOption.textContent = '모델 선택...'; // Placeholder text
            placeholderOption.disabled = true;
            placeholderOption.selected = true;
            placeholderOption.hidden = true; // Hide from dropdown options
            this.modelSelect.appendChild(placeholderOption);

            models.forEach(model => {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                this.modelSelect.appendChild(option);
            });
            console.log('Loaded models:', models);
            // Set default selected model (e.g., the first one or a specific one)
            if (models && models.length > 0) {
                // Ensure the default selection is one of the actual models, not the placeholder
                if (models.includes('gemini-2.5-flash')) {
                    this.modelSelect.value = 'gemini-2.5-flash';
                } else {
                    this.modelSelect.value = models[0];
                }
            }
        } catch (error) {
            console.error('모델 목록을 불러오는 데 실패했습니다:', error);
            // Optionally, disable model selection or show an error to the user
        }
    }
    
    handleSubmit(e) {
        e.preventDefault();
        if (this.isProcessing) {
            console.log('Already processing, ignoring submit');
            return;
        }
        
        const message = this.userInput.value.trim();
        const model = this.modelSelect.value; 
        
        if (!message) return;
        
        console.log('Submitting message:', message, 'model:', model);
        let url = '';
        const urlMatch = message.match(/https?:\/\/[^\s]+/);
        if (urlMatch) {
            url = urlMatch[0];
        }
        
        this.isProcessing = true;
        this.sendBtn.disabled = true;
        
        this.addMessage('user', (url ? `URL: ${url}\n` : '') + `요청: ${message}\n모델: ${model}`); 
        this.userInput.value = '';
        this.userInput.style.height = 'auto'; 
        
        console.log('Starting new assistant message container');
        this.currentAssistantMessage = this.addMessage('assistant', '');
        this.addTypingIndicator();
        
        if (this.stompClient && this.stompClient.connected) {
            this.stompClient.send('/app/chat', {}, JSON.stringify({ url, message, model })); 
        } else {
            console.error('STOMP client is not connected');
            this.showError('서버 연결이 끊어졌습니다. 다시 시도해주세요.');
        }
    }
    
    addMessage(role, content) {
        const div = document.createElement('div');
        div.className = `message ${role}`;
        
        let avatarContent = '';
        if (role === 'assistant') {
            avatarContent = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>';
        } else if (role === 'user') {
            avatarContent = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>';
        }

        div.innerHTML = `<div class="avatar">${avatarContent}</div><div class="content">${this.formatContent(content)}</div>`;
        this.messagesContainer.appendChild(div);
        this.scrollToBottom();
        return div.querySelector('.content');
    }
    
    appendToCurrentMessage(chunk) {
        if (chunk === undefined || chunk === null || chunk === "") return; 
        console.log('Appending chunk to UI (len=' + chunk.length + ')');
        
        if (!this.currentAssistantMessage) {
            console.log('No current assistant message container, creating one');
            this.currentAssistantMessage = this.addMessage('assistant', '');
        }

        this.removeTypingIndicator();
        const currentText = this.currentAssistantMessage.getAttribute('data-raw') || '';
        const newText = currentText + chunk;
        this.currentAssistantMessage.setAttribute('data-raw', newText);
        this.currentAssistantMessage.innerHTML = this.formatContent(newText);
        
        if (!this.isFinalizing) {
            this.addTypingIndicator();
        }
        this.scrollToBottom();
    }
    
    finalizeCurrentMessage() {
        console.log('Finalizing message');
        this.isFinalizing = true;
        this.removeTypingIndicator();
        this.currentAssistantMessage = null;
        this.isProcessing = false;
        this.isFinalizing = false;
        this.sendBtn.disabled = false;
        this.userInput.focus();
    }
    
    addTypingIndicator() {
        if (this.currentAssistantMessage && !this.currentAssistantMessage.querySelector('.typing')) {
            const indicator = document.createElement('span');
            indicator.className = 'typing';
            indicator.innerHTML = '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';
            this.currentAssistantMessage.appendChild(indicator);
            console.log('Typing indicator added');
        }
    }
    
    removeTypingIndicator() {
        const typing = this.currentAssistantMessage?.querySelector('.typing');
        if (typing) typing.remove();
    }
    
    showError(error) {
        console.error('Showing error:', error);
        this.removeTypingIndicator();
        if (this.currentAssistantMessage) {
            this.currentAssistantMessage.innerHTML = `<span class="error">❌ 오류: ${error}</span>`;
        } else {
            this.addMessage('assistant', `<span class="error">❌ 오류: ${error}</span>`);
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

    // New methods for prompt history functionality
    async openPromptHistoryModal() {
        this.promptHistoryModal.style.display = 'flex';
        await this.fetchPromptFiles();
    }

    closePromptHistoryModal() {
        this.promptHistoryModal.style.display = 'none';
        this.promptList.innerHTML = ''; // Clear list when closing
    }

    async fetchPromptFiles() {
        try {
            const response = await fetch('/api/prompts/history/files');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const files = await response.json();
            this.promptList.innerHTML = ''; // Clear previous list

            if (files.length === 0) {
                const listItem = document.createElement('li');
                listItem.textContent = '저장된 프롬프트가 없습니다.';
                this.promptList.appendChild(listItem);
            } else {
                files.forEach(filename => {
                    const listItem = document.createElement('li');
                    listItem.textContent = filename;
                    listItem.addEventListener('click', () => this.loadPromptContent(filename));
                    this.promptList.appendChild(listItem);
                });
            }
        } catch (error) {
            console.error('프롬프트 히스토리 파일을 불러오는 데 실패했습니다:', error);
            this.promptList.innerHTML = '<li>프롬프트 히스토리 파일을 불러오는 데 실패했습니다.</li>';
        }
    }

    async loadPromptContent(filename) {
        try {
            const response = await fetch(`/api/prompts/history/content/${filename}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const content = await response.text();
            this.userInput.value = content;
            // Trigger input event to auto-resize textarea
            this.userInput.dispatchEvent(new Event('input'));
            this.closePromptHistoryModal();
            this.userInput.focus();
        } catch (error) {
            console.error(`프롬프트 파일 '${filename}' 내용을 불러오는 데 실패했습니다:`, error);
            alert(`프롬프트 파일 '${filename}' 내용을 불러오는 데 실패했습니다.`);
        }
    }
}

document.addEventListener('DOMContentLoaded', () => new QaAgentChat());
