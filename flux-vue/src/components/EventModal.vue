<template>
  <teleport to="body">
    <div class="modal-backdrop fade-in" @click.self="handleClose">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h3 class="modal-title">
              <i class="bi bi-calendar-plus me-2"></i>
              {{ isEdit ? 'Edit Event' : 'Create Event' }}
            </h3>
            <button class="btn-close" @click="handleClose">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>

          <form @submit.prevent="handleSubmit">
            <div class="modal-body">
              <div class="mb-3">
                <label for="title" class="form-label">Event Title *</label>
                <input
                  type="text"
                  class="form-control"
                  id="title"
                  v-model="formData.title"
                  placeholder="Enter event title"
                  required
                />
              </div>

              <div class="mb-3">
                <label for="description" class="form-label">Description *</label>
                <textarea
                  class="form-control"
                  id="description"
                  rows="3"
                  v-model="formData.description"
                  placeholder="Event description"
                  required
                ></textarea>
              </div>

              <div class="row mb-3">
                <div class="col-md-6">
                  <label for="start" class="form-label">Start Date & Time *</label>
                  <input
                    type="datetime-local"
                    class="form-control"
                    id="start"
                    v-model="formData.startTime"
                    required
                  />
                </div>
                <div class="col-md-6">
                  <label for="end" class="form-label">End Date & Time *</label>
                  <input
                    type="datetime-local"
                    class="form-control"
                    id="end"
                    v-model="formData.endTime"
                    required
                  />
                </div>
              </div>

              <div class="mb-3">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="checkbox"
                    id="allDay"
                    v-model="formData.allDay"
                  />
                  <label class="form-check-label" for="allDay">
                    All-day event
                  </label>
                </div>
              </div>

              <div class="mb-3">
                <label for="type" class="form-label">Event Type</label>
                <select class="form-select" id="type" v-model="formData.type">
                  <option value="MEETING">Meeting</option>
                  <option value="TASK">Task</option>
                  <option value="REMINDER">Reminder</option>
                  <option value="EVENT">Event</option>
                </select>
              </div>

              <div class="mb-3">
                <label for="color" class="form-label">Color</label>
                <div class="color-picker">
                  <input
                    type="color"
                    class="form-control form-control-color"
                    id="color"
                    v-model="formData.colorHex"
                  />
                  <input
                    type="text"
                    class="form-control"
                    v-model="formData.colorHex"
                    placeholder="#4285F4"
                  />
                </div>
              </div>

              <div class="mb-3">
                <label for="status" class="form-label">Status</label>
                <select class="form-select" id="status" v-model="formData.status">
                  <option value="PENDING">Pending</option>
                  <option value="CONFIRMED">Confirmed</option>
                  <option value="CANCELLED">Cancelled</option>
                  <option value="COMPLETED">Completed</option>
                </select>
              </div>

              <div v-if="error" class="alert alert-danger" role="alert">
                {{ error }}
              </div>
            </div>

            <div class="modal-footer">
              <button
                v-if="isEdit"
                type="button"
                class="btn btn-danger me-auto"
                @click="handleDelete"
                :disabled="loading"
              >
                <i class="bi bi-trash me-2"></i>
                Delete
              </button>
              <button
                type="button"
                class="btn btn-outline-secondary"
                @click="handleClose"
                :disabled="loading"
              >
                Cancel
              </button>
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="loading"
              >
                <span v-if="loading" class="spinner-border spinner-border-sm me-2"></span>
                {{ isEdit ? 'Update Event' : 'Create Event' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';

const props = defineProps({
  event: {
    type: Object,
    default: null,
  },
  initialDate: {
    type: String,
    default: null,
  },
});

const emit = defineEmits(['close', 'save', 'delete']);

const isEdit = computed(() => !!props.event);

const formData = ref({
  title: '',
  description: '',
  startTime: '',
  endTime: '',
  allDay: false,
  type: 'EVENT',
  colorHex: '#4285F4',
  status: 'PENDING',
  date: '',
  subtasks: [],
  attachments: [],
});

const loading = ref(false);
const error = ref('');

onMounted(() => {
  if (props.event) {
    // Edit mode - populate form with event data
    formData.value = {
      title: props.event.title || '',
      description: props.event.description || '',
      startTime: formatDateTimeLocal(props.event.start),
      endTime: formatDateTimeLocal(props.event.end),
      allDay: props.event.allDay || false,
      type: props.event.extendedProps?.type || 'EVENT',
      colorHex: props.event.backgroundColor || '#4285F4',
      status: props.event.extendedProps?.status || 'PENDING',
      date: formatDate(props.event.start),
      subtasks: [],
      attachments: [],
    };
  } else if (props.initialDate) {
    // Create mode with initial date
    const date = new Date(props.initialDate);
    const startTime = new Date(date);
    startTime.setHours(9, 0, 0, 0);
    const endTime = new Date(date);
    endTime.setHours(10, 0, 0, 0);

    formData.value.startTime = formatDateTimeLocal(startTime);
    formData.value.endTime = formatDateTimeLocal(endTime);
    formData.value.date = formatDate(date);
  } else {
    // Default to current date/time
    const now = new Date();
    const oneHourLater = new Date(now.getTime() + 60 * 60 * 1000);
    formData.value.startTime = formatDateTimeLocal(now);
    formData.value.endTime = formatDateTimeLocal(oneHourLater);
    formData.value.date = formatDate(now);
  }
});

function formatDateTimeLocal(date) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function formatDate(date) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const handleSubmit = async () => {
  loading.value = true;
  error.value = '';

  try {
    // Validate
    if (new Date(formData.value.endTime) <= new Date(formData.value.startTime)) {
      error.value = 'End time must be after start time';
      loading.value = false;
      return;
    }

    // Prepare data for backend
    const eventData = {
      ...formData.value,
      date: formatDate(formData.value.startTime),
    };

    emit('save', eventData);
  } catch (err) {
    error.value = err.message || 'Failed to save event';
  } finally {
    loading.value = false;
  }
};

const handleDelete = () => {
  if (confirm('Are you sure you want to delete this event?')) {
    emit('delete', props.event.id);
  }
};

const handleClose = () => {
  emit('close');
};
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-dialog {
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-content {
  background: var(--bg-primary);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  padding: 24px 32px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.btn-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 1.5rem;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.btn-close:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.modal-body {
  padding: 32px;
}

.form-label {
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.color-picker {
  display: flex;
  gap: 12px;
  align-items: center;
}

.form-control-color {
  width: 60px;
  height: 45px;
  padding: 4px;
}

.modal-footer {
  padding: 20px 32px;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* Responsive */
@media (max-width: 768px) {
  .modal-header,
  .modal-body,
  .modal-footer {
    padding: 20px;
  }

  .modal-title {
    font-size: 1.3rem;
  }
}
</style>
