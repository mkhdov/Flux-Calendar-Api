<template>
  <div class="settings-view fade-in">
    <div class="row">
      <div class="col-lg-8 mx-auto">
        <!-- Profile Section -->
        <div class="card mb-4">
          <div class="card-header p-4">
            <h4 class="mb-0 fw-bold">Personal Information</h4>
          </div>
          <div class="card-body p-4">
            <div class="d-flex align-items-center gap-4 mb-4">
              <div class="avatar-large">
                <i class="bi bi-person-circle"></i>
              </div>
              <div>
                <h3 class="mb-1">{{ authStore.user?.firstName }} {{ authStore.user?.lastName }}</h3>
                <p class="text-secondary mb-0">{{ authStore.user?.email }}</p>
              </div>
            </div>
            
            <form @submit.prevent="updateProfile">
              <div class="row">
                <div class="col-md-6 mb-3">
                  <label class="form-label">First Name</label>
                  <input type="text" class="form-control" v-model="profileData.firstName">
                </div>
                <div class="col-md-6 mb-3">
                  <label class="form-label">Last Name</label>
                  <input type="text" class="form-control" v-model="profileData.lastName">
                </div>
              </div>
              <div class="mb-3">
                <label class="form-label">Email</label>
                <input type="email" class="form-control" v-model="profileData.email" disabled>
                <small class="text-muted">Email cannot be changed.</small>
              </div>
              <button class="btn btn-primary" type="submit" :disabled="loading">
                Update Profile
              </button>
            </form>
          </div>
        </div>

        <!-- Integrations Section -->
        <div class="card mb-4">
          <div class="card-header p-4">
            <h4 class="mb-0 fw-bold">Integrations</h4>
          </div>
          <div class="card-body p-4">
            <div class="integration-item d-flex align-items-center justify-content-between p-3 mb-3 border rounded">
              <div class="d-flex align-items-center gap-3">
                <div class="integration-icon google">
                  <i class="bi bi-google"></i>
                </div>
                <div>
                  <h5 class="mb-0">Google Calendar</h5>
                  <p class="text-secondary mb-0 small">Sync events with your Google account</p>
                </div>
              </div>
              <div>
                <button 
                  v-if="!integrationStore.googleConnected" 
                  class="btn btn-outline-primary"
                  @click="integrationStore.connectGoogle"
                >
                  Connect
                </button>
                <div v-else class="d-flex align-items-center gap-2">
                  <span class="badge bg-success">Connected</span>
                  <button class="btn btn-sm btn-link text-danger" @click="integrationStore.logoutGoogle">Disconnect</button>
                </div>
              </div>
            </div>

            <div class="integration-item d-flex align-items-center justify-content-between p-3 border rounded">
              <div class="d-flex align-items-center gap-3">
                <div class="integration-icon zoom">
                  <i class="bi bi-camera-video-fill"></i>
                </div>
                <div>
                  <h5 class="mb-0">Zoom</h5>
                  <p class="text-secondary mb-0 small">Automatically create Zoom meetings for events</p>
                </div>
              </div>
              <div>
                <button 
                  v-if="!integrationStore.zoomConnected" 
                  class="btn btn-outline-primary"
                  @click="integrationStore.connectZoom"
                >
                  Connect
                </button>
                <div v-else class="d-flex align-items-center gap-2">
                  <span class="badge bg-success">Connected</span>
                  <button class="btn btn-sm btn-link text-danger" @click="integrationStore.disconnectZoom">Disconnect</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Preferences -->
        <div class="card">
          <div class="card-header p-4">
            <h4 class="mb-0 fw-bold">Preferences</h4>
          </div>
          <div class="card-body p-4">
            <div class="d-flex align-items-center justify-content-between">
              <div>
                <h5 class="mb-1">Appearance</h5>
                <p class="text-secondary mb-0">Switch between light and dark modes</p>
              </div>
              <div class="form-check form-switch">
                <input 
                  class="form-check-input" 
                  type="checkbox" 
                  :checked="themeStore.isDark"
                  @change="themeStore.toggleTheme"
                >
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useIntegrationStore } from '../stores/integration';
import { useThemeStore } from '../stores/theme';

const authStore = useAuthStore();
const integrationStore = useIntegrationStore();
const themeStore = useThemeStore();

const loading = ref(false);
const profileData = ref({
  firstName: authStore.user?.firstName || '',
  lastName: authStore.user?.lastName || '',
  email: authStore.user?.email || '',
});

onMounted(() => {
  integrationStore.checkStatus();
});

const updateProfile = async () => {
  loading.value = true;
  // Implement profile update API call if needed
  setTimeout(() => {
    loading.value = false;
    alert('Profile updated successfully!');
  }, 500);
};
</script>

<style scoped>
.avatar-large {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 3rem;
}

.integration-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.integration-icon.google {
  background: #f1f5f9;
  color: #4285F4;
}

.integration-icon.zoom {
  background: #EBF5FF;
  color: #2D8CFF;
}

.form-switch .form-check-input {
  width: 3em;
  height: 1.5em;
  cursor: pointer;
}
</style>
