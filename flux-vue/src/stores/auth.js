import { defineStore } from 'pinia';
import api from '../api/axios';
import { jwtDecode } from 'jwt-decode';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('token') || null,
        user: JSON.parse(localStorage.getItem('user')) || null,
    }),
    getters: {
        isAuthenticated: (state) => !!state.token,
        currentUserId: (state) => state.user?.id || null, // Assuming decoded payload has 'id' or 'sub' is id
    },
    actions: {
        async login(credentials) {
            try {
                const response = await api.post('/api/auth/login', credentials);
                const { accessToken } = response.data;
                this.token = accessToken;
                localStorage.setItem('token', accessToken);

                // Decode token to get user info if possible
                const decoded = jwtDecode(accessToken);
                // Assuming the token payload contains user info. If not, we might need a separate call.
                // For now, let's assume 'sub' is the ID or similar.
                // If sub is email, we might have an issue if backend needs ID.
                // Let's store the whole decoded payload as user for now.
                this.user = { id: decoded.sub, ...decoded };
                localStorage.setItem('user', JSON.stringify(this.user));

                return true;
            } catch (error) {
                console.error('Login failed:', error);
                throw error;
            }
        },
        async register(userData) {
            // Format birthday to LocalDate string if needed (YYYY-MM-DD)
            const data = { ...userData };
            if (data.birthday instanceof Date) {
                data.birthday = data.birthday.toISOString().split('T')[0];
            }
            try {
                const response = await api.post('/api/auth/signup', data);
                return response.data;
            } catch (error) {
                console.error('Registration failed:', error);
                throw error;
            }
        },
        logout() {
            this.token = null;
            this.user = null;
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.reload();
        },
    },
});
