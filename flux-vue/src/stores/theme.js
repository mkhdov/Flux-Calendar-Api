import { defineStore } from 'pinia';

export const useThemeStore = defineStore('theme', {
    state: () => ({
        isDark: localStorage.getItem('theme') === 'dark',
    }),
    actions: {
        toggleTheme() {
            this.isDark = !this.isDark;
            this.applyTheme();
        },
        applyTheme() {
            if (this.isDark) {
                document.documentElement.setAttribute('data-theme', 'dark');
                localStorage.setItem('theme', 'dark');
            } else {
                document.documentElement.removeAttribute('data-theme');
                localStorage.setItem('theme', 'light');
            }
        },
        initTheme() {
            this.applyTheme();
        },
    },
});
