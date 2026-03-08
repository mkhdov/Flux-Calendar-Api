<template>
  <div class="login-container">
    <div class="login-card fade-in">
      <div class="login-header text-center mb-4">
        <div class="logo-container mb-3">
          <i class="bi bi-calendar-check-fill logo-icon"></i>
        </div>
        <h2 class="fw-bold mb-2">Welcome Back</h2>
        <p class="text-secondary">Sign in to your Flux Calendar account</p>
      </div>

      <form @submit.prevent="handleLogin">
        <div class="mb-3">
          <label for="email" class="form-label">Email Address</label>
          <div class="input-group">
            <span class="input-group-text bg-transparent">
              <i class="bi bi-envelope"></i>
            </span>
            <input
              type="email"
              class="form-control"
              id="email"
              v-model="credentials.email"
              placeholder="you@example.com"
              required
            />
          </div>
        </div>

        <div class="mb-3">
          <label for="password" class="form-label">Password</label>
          <div class="input-group">
            <span class="input-group-text bg-transparent">
              <i class="bi bi-lock"></i>
            </span>
            <input
              :type="showPassword ? 'text' : 'password'"
              class="form-control"
              id="password"
              v-model="credentials.password"
              placeholder="Enter your password"
              required
            />
            <button
              class="btn btn-outline-secondary"
              type="button"
              @click="showPassword = !showPassword"
            >
              <i :class="showPassword ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
            </button>
          </div>
        </div>

        <div v-if="error" class="alert alert-danger" role="alert">
          {{ error }}
        </div>

        <button
          type="submit"
          class="btn btn-primary w-100 mb-3"
          :disabled="loading"
        >
          <span v-if="loading" class="spinner-border spinner-border-sm me-2"></span>
          {{ loading ? 'Signing in...' : 'Sign In' }}
        </button>

        <div class="text-center">
          <p class="mb-0">
            Don't have an account?
            <router-link to="/register" class="text-decoration-none fw-semibold">
              Sign Up
            </router-link>
          </p>
        </div>
      </form>
    </div>

    <!-- Theme toggle -->
    <button class="theme-toggle" @click="toggleTheme">
      <i :class="themeStore.isDark ? 'bi bi-sun-fill' : 'bi bi-moon-fill'"></i>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useThemeStore } from '../stores/theme';
import { useNotifyStore } from '../stores/notify';

const router = useRouter();
const authStore = useAuthStore();
const themeStore = useThemeStore();
const notify = useNotifyStore();

const credentials = ref({
  email: '',
  password: '',
});

const showPassword = ref(false);
const loading = ref(false);
const error = ref('');

const handleLogin = async () => {
  loading.value = true;
  error.value = '';

  try {
    await authStore.login(credentials.value);
    notify.success('Welcome back!');
    router.push('/dashboard');
  } catch (err) {
    error.value = err.response?.data?.message || 'Invalid email or password. Please try again.';
    notify.error(error.value);
  } finally {
    loading.value = false;
  }
};

const toggleTheme = () => {
  themeStore.toggleTheme();
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  position: relative;
}

.login-card {
  background: var(--bg-primary);
  padding: 40px;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 450px;
}

.logo-container {
  display: inline-block;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
}

.logo-icon {
  font-size: 3rem;
  color: white;
}

.input-group-text {
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.theme-toggle {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--bg-primary);
  border: 2px solid var(--border-color);
  box-shadow: var(--shadow-lg);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: var(--text-primary);
  transition: all 0.3s ease;
}

.theme-toggle:hover {
  transform: scale(1.1) rotate(10deg);
  box-shadow: var(--shadow-xl);
}

@media (max-width: 576px) {
  .login-card {
    padding: 30px 20px;
  }
}
</style>
