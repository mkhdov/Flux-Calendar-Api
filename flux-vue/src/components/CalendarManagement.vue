<template>
  <div class="calendar-mgmt card fade-in">
    <div class="card-header p-4 d-flex justify-content-between align-items-center">
      <h4 class="mb-0 fw-bold">My Calendars</h4>
      <button class="btn btn-sm btn-primary" @click="showAddModal = true">
        <i class="bi bi-plus-lg me-1"></i> Add
      </button>
    </div>

    <div class="card-body p-0">
      <div v-if="calendarStore.calendars.length === 0" class="p-4 text-center text-muted">
        No calendars yet.
      </div>
      <ul v-else class="list-group list-group-flush">
        <li
          v-for="calendar in calendarStore.calendars"
          :key="calendar.id"
          class="list-group-item d-flex align-items-center justify-content-between p-3"
          :class="{ active: calendarStore.primaryCalendar?.id === calendar.id }"
        >
          <div class="d-flex align-items-center gap-3">
            <div
              class="color-dot"
              :style="{ backgroundColor: calendar.colorHex }"
            ></div>
            <div>
              <div class="fw-bold">{{ calendar.title }}</div>
              <small class="text-secondary" v-if="calendar.isPrimary">Primary</small>
            </div>
          </div>
          <div class="actions">
            <button
              class="btn btn-link text-danger p-0 ms-2"
              @click="confirmDelete(calendar)"
              v-if="!calendar.isPrimary"
            >
              <i class="bi bi-trash"></i>
            </button>
          </div>
        </li>
      </ul>
    </div>

    <!-- Add Calendar Modal -->
    <teleport to="body">
      <div v-if="showAddModal" class="modal-backdrop" @click.self="showAddModal = false">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">New Calendar</h5>
              <button class="btn-close" @click="showAddModal = false"></button>
            </div>
            <div class="modal-body">
              <div class="mb-3">
                <label class="form-label">Title</label>
                <input type="text" class="form-control" v-model="newCalendar.title" placeholder="Work, Personal, etc.">
              </div>
              <div class="mb-3">
                <label class="form-label">Description</label>
                <textarea class="form-control" v-model="newCalendar.description"></textarea>
              </div>
              <div class="mb-3">
                <label class="form-label">Color</label>
                <input type="color" class="form-control form-control-color w-100" v-model="newCalendar.colorHex">
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-outline-secondary" @click="showAddModal = false">Cancel</button>
              <button class="btn btn-primary" @click="handleAddCalendar" :disabled="!newCalendar.title || loading">
                <span v-if="loading" class="spinner-border spinner-border-sm me-2"></span>
                Create
              </button>
            </div>
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useCalendarStore } from '../stores/calendar';

const calendarStore = useCalendarStore();
const showAddModal = ref(false);
const loading = ref(false);

const newCalendar = ref({
  title: '',
  description: '',
  colorHex: '#6366f1',
  isPrimary: false
});

const handleAddCalendar = async () => {
  loading.value = true;
  try {
    await calendarStore.addCalendar(newCalendar.value);
    showAddModal.value = false;
    newCalendar.value = { title: '', description: '', colorHex: '#6366f1', isPrimary: false };
  } finally {
    loading.value = false;
  }
};

const confirmDelete = async (calendar) => {
  if (confirm(`Are you sure you want to delete "${calendar.title}"?`)) {
    await calendarStore.deleteCalendar(calendar.id);
  }
};
</script>

<style scoped>
.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.list-group-item {
  background: transparent;
  border-color: var(--border-color);
  color: var(--text-primary);
}

.list-group-item.active {
  background: rgba(99, 102, 241, 0.1);
  border-color: var(--primary-color);
  color: var(--text-primary);
}

.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1050;
}
</style>
