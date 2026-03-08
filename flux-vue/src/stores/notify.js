import { defineStore } from 'pinia';

export const useNotifyStore = defineStore('notify', {
    state: () => ({
        toasts: [],
    }),
    actions: {
        show(message, type = 'primary', duration = 3000) {
            const id = Date.now();
            this.toasts.push({ id, message, type });
            setTimeout(() => {
                this.toasts = this.toasts.filter(t => t.id !== id);
            }, duration);
        },
        success(message) { this.show(message, 'success'); },
        error(message) { this.show(message, 'danger'); },
        warn(message) { this.show(message, 'warning'); },
    },
});
