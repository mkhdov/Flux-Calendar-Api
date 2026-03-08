<template>
  <div class="register-container">
    <div class="register-card fade-in">
      <div class="register-header text-center mb-4">
        <div class="logo-container mb-3">
          <i class="bi bi-calendar-plus-fill logo-icon"></i>
        </div>
        <h2 class="fw-bold mb-2">Create Account</h2>
        <p class="text-secondary">Start organizing your schedule today</p>
      </div>

      <form @submit.prevent="handleRegister">
        <div class="row mb-3">
          <div class="col-md-6">
            <label for="firstName" class="form-label">First Name</label>
            <input
              type="text"
              class="form-control"
              id="firstName"
              v-model="formData.firstName"
              placeholder="John"
              required
            />
          </div>
          <div class="col-md-6">
            <label for="lastName" class="form-label">Last Name</label>
            <input
              type="text"
              class="form-control"
              id="lastName"
              v-model="formData.lastName"
              placeholder="Doe"
              required
            />
          </div>
        </div>

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
              v-model="formData.email"
              placeholder="you@example.com"
              required
            />
          </div>
        </div>

        <div class="mb-3">
          <label for="birthday" class="form-label">Birthday</label>
          <div class="input-group">
            <span class="input-group-text bg-transparent">
              <i class="bi bi-calendar-date"></i>
            </span>
            <input
              type="date"
              class="form-control"
              id="birthday"
              v-model="formData.birthday"
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
              v-model="formData.password"
              placeholder="Create a strong password"
              required
              minlength="8"
            />
            <button
              class="btn btn-outline-secondary"
              type="button"
              @click="showPassword = !showPassword"
            >
              <i :class="showPassword ? 'bi bi-eye-slash' : 'bi bi-eye'"></i>
            </button>
          </div>
          <small class="text-muted">Must be at least 8 characters</small>
        </div>

        <div v-if="error" class="alert alert-danger" role="alert">
          {{ error }}
        </div>

        <div v-if="success" class="alert alert-success" role="alert">
          {{ success }}
        </div>

        <button
          type="submit"
          class="btn btn-primary w-100 mb-3"
          :disabled="loading"
        >
          <span v-if="loading" class="spinner-border spinner-border-sm me-2"></span>
          {{ loading ? 'Creating Account...' : 'Sign Up' }}
        </button>

        <div class="text-center">
          <p class="mb-0">
            Already have an account?
            <router-link to="/login" class="text-decoration-none fw-semibold">
              Sign In
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

const formData = ref({
  firstName: '',
  lastName: '',
  email: '',
  password: '',
  birthday: '',
});

const showPassword = ref(false);
const loading = ref(false);
const error = ref('');
const success = ref('');

const handleRegister = async () => {
  loading.value = true;
  error.value = '';
  success.value = '';

  try {
    await authStore.register(formData.value);
    const msg = 'Account created successfully! Please check your email to verify your account.';
    success.value = msg;
    notify.success(msg);
    setTimeout(() => {
      router.push('/login');
    }, 3000);
  } catch (err) {
    error.value = err.response?.data?.message || 'Registration failed. Please try again.';
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
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  padding: 20px;
  position: relative;
}

.register-card {
  background: var(--bg-primary);
  padding: 40px;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 550px;
}

.logo-container {
  display: inline-block;
  padding: 20px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
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
  .register-card {
    padding: 30px 20px;
  }
}
</style>
