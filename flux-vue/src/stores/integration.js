import { defineStore } from 'pinia';
import api from '../api/axios';
import { useAuthStore } from './auth';

export const useIntegrationStore = defineStore('integration', {
    state: () => ({
        googleConnected: false,
        zoomConnected: false,
        loading: false,
    }),
    actions: {
        async checkStatus() {
            const authStore = useAuthStore();
            if (!authStore.isAuthenticated) return;

            this.loading = true;
            try {
                const [googleRes, zoomRes] = await Promise.allSettled([
                    api.get('/auth/status'),
                    api.get('/zoom/status')
                ]);

                if (googleRes.status === 'fulfilled') {
                    this.googleConnected = googleRes.value.data.authenticated;
                }
                if (zoomRes.status === 'fulfilled') {
                    this.zoomConnected = zoomRes.value.data.connected;
                }
            } catch (error) {
                console.error('Failed to check integration status:', error);
            } finally {
                this.loading = false;
            }
        },
        connectGoogle() {
            const authStore = useAuthStore();
            if (!authStore.user?.id) return;
            window.location.href = `http://localhost:8083/auth/google?userId=${authStore.user.id}`;
        },
        connectZoom() {
            window.location.href = `http://localhost:8083/zoom/connect`;
        },
        async disconnectZoom() {
            try {
                await api.delete('/zoom/disconnect');
                this.zoomConnected = false;
            } catch (error) {
                console.error('Failed to disconnect Zoom:', error);
            }
        },
        async logoutGoogle() {
            try {
                await api.post('/auth/logout');
                this.googleConnected = false;
            } catch (error) {
                console.error('Failed to logout Google:', error);
            }
        }
    },
});
