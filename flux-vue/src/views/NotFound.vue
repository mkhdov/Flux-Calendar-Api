<template>
  <div class="not-found-container">
    <div class="not-found-content fade-in">
      <div class="error-animation mb-4">
        <div class="error-circle">
          <i class="bi bi-calendar-x-fill"></i>
        </div>
        <div class="floating-icons">
          <i class="bi bi-calendar float-1"></i>
          <i class="bi bi-clock float-2"></i>
          <i class="bi bi-bell float-3"></i>
        </div>
      </div>

      <h1 class="error-code mb-3">404</h1>
      <h2 class="error-title mb-3">Page Not Found</h2>
      <p class="error-message mb-4">
        Oops! The page you're looking for seems to have escaped from our calendar.
        It might have been rescheduled or deleted.
      </p>

      <div class="action-buttons">
        <router-link to="/dashboard" class="btn btn-primary btn-lg me-3">
          <i class="bi bi-house-door me-2"></i>
          Back to Dashboard
        </router-link>
        <button @click="goBack" class="btn btn-outline-primary btn-lg">
          <i class="bi bi-arrow-left me-2"></i>
          Go Back
        </button>
      </div>
    </div>

    <!-- Theme toggle -->
    <button class="theme-toggle" @click="toggleTheme">
      <i :class="themeStore.isDark ? 'bi bi-sun-fill' : 'bi bi-moon-fill'"></i>
    </button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { useThemeStore } from '../stores/theme';

const router = useRouter();
const themeStore = useThemeStore();

const goBack = () => {
  router.go(-1);
};

const toggleTheme = () => {
  themeStore.toggleTheme();
};
</script>

<style scoped>
.not-found-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.not-found-content {
  text-align: center;
  max-width: 600px;
  position: relative;
  z-index: 1;
}

.error-animation {
  position: relative;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-circle {
  width: 150px;
  height: 150px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse 2s ease-in-out infinite;
}

.error-circle i {
  font-size: 4rem;
  color: white;
}

.floating-icons {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
}

.floating-icons i {
  position: absolute;
  font-size: 2rem;
  color: rgba(255, 255, 255, 0.3);
}

.float-1 {
  top: 20%;
  left: 10%;
  animation: float 3s ease-in-out infinite;
}

.float-2 {
  top: 60%;
  right: 15%;
  animation: float 4s ease-in-out infinite 0.5s;
}

.float-3 {
  bottom: 10%;
  left: 20%;
  animation: float 3.5s ease-in-out infinite 1s;
}

.error-code {
  font-size: 8rem;
  font-weight: 900;
  color: white;
  text-shadow: 0 5px 20px rgba(0, 0, 0, 0.3);
  line-height: 1;
}

.error-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: white;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}

.error-message {
  font-size: 1.2rem;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons .btn {
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
}

.theme-toggle {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border: 2px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: white;
  transition: all 0.3s ease;
  z-index: 1000;
}

.theme-toggle:hover {
  transform: scale(1.1) rotate(10deg);
  background: rgba(255, 255, 255, 0.3);
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(10deg);
  }
}

@media (max-width: 768px) {
  .error-code {
    font-size: 5rem;
  }

  .error-title {
    font-size: 2rem;
  }

  .error-message {
    font-size: 1rem;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .btn {
    width: 100%;
    margin: 0 !important;
  }
}
</style>
