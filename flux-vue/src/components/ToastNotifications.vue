<template>
  <div class="toast-container position-fixed bottom-0 end-0 p-3">
    <transition-group name="toast">
      <div 
        v-for="toast in notifyStore.toasts" 
        :key="toast.id"
        class="toast show mb-2" 
        :class="`bg-${toast.type || 'primary'} text-white`"
        role="alert" 
      >
        <div class="d-flex">
          <div class="toast-body p-3">
            <i :class="getIcon(toast.type)" class="me-2"></i>
            {{ toast.message }}
          </div>
          <button 
            type="button" 
            class="btn-close btn-close-white me-2 m-auto" 
            @click="notifyStore.toasts = notifyStore.toasts.filter(t => t.id !== toast.id)"
          ></button>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { useNotifyStore } from '../stores/notify';

const notifyStore = useNotifyStore();

const getIcon = (type) => {
  switch (type) {
    case 'success': return 'bi bi-check-circle-fill';
    case 'danger': return 'bi bi-exclamation-triangle-fill';
    case 'warning': return 'bi bi-exclamation-circle-fill';
    default: return 'bi bi-info-circle-fill';
  }
};
</script>

<style scoped>
.toast-container {
  z-index: 2000;
}

.toast {
  border: none;
  border-radius: 12px;
  box-shadow: var(--shadow-xl);
  min-width: 250px;
  backdrop-filter: blur(10px);
}

.toast-enter-active, .toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
