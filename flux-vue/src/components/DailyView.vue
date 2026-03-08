<template>
  <div class="daily-view">
    <div class="date-selector card mb-4 p-3">
      <div class="d-flex align-items-center justify-content-between">
        <button class="btn btn-outline-primary" @click="previousDay">
          <i class="bi bi-chevron-left"></i>
        </button>

        <div class="current-date text-center">
          <h3 class="mb-0">{{ formatDate(selectedDate) }}</h3>
          <p class="text-secondary mb-0">{{ formatFullDate(selectedDate) }}</p>
        </div>

        <button class="btn btn-outline-primary" @click="nextDay">
          <i class="bi bi-chevron-right"></i>
        </button>
      </div>

      <div class="mt-3">
        <input
          type="date"
          class="form-control"
          v-model="dateInput"
          @change="handleDateChange"
        />
      </div>
    </div>

    <div class="daily-content">
      <div v-if="todayEvents.length === 0" class="empty-state card p-5 text-center">
        <i class="bi bi-calendar-event display-1 text-muted mb-3"></i>
        <h4 class="text-muted">No events for this day</h4>
        <p class="text-secondary">Click the + button to add a new event</p>
      </div>

      <div v-else class="timeline">
        <div
          v-for="event in todayEvents"
          :key="event.id"
          class="timeline-item card card-hover"
          @click="$emit('event-click', event)"
        >
          <div class="timeline-marker"></div>
          <div class="timeline-content p-4">
            <div class="d-flex justify-content-between align-items-start mb-3">
              <div>
                <h4 class="event-title mb-2">{{ event.title }}</h4>
                <div class="event-time">
                  <i class="bi bi-clock me-2"></i>
                  {{ formatTime(event.start) }} - {{ formatTime(event.end) }}
                  <span class="ms-2 badge bg-primary">
                    {{ calculateDuration(event.start, event.end) }}
                  </span>
                </div>
              </div>
            </div>

            <p v-if="event.description" class="event-description mb-3">
              {{ event.description }}
            </p>

            <div class="event-details">
              <div v-if="event.location" class="detail-item">
                <i class="bi bi-geo-alt me-2"></i>
                {{ event.location?.streetAddress || 'Location' }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  events: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['event-click']);

const selectedDate = ref(new Date());
const dateInput = ref(formatDateInput(new Date()));

const todayEvents = computed(() => {
  return props.events.filter((event) => {
    const eventDate = new Date(event.start);
    return isSameDay(eventDate, selectedDate.value);
  }).sort((a, b) => new Date(a.start) - new Date(b.start));
});

function formatDateInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDate(date) {
  return date.toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });
}

function formatFullDate(date) {
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function formatTime(date) {
  return new Date(date).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function calculateDuration(start, end) {
  const diff = new Date(end) - new Date(start);
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));

  if (hours > 0 && minutes > 0) {
    return `${hours}h ${minutes}m`;
  } else if (hours > 0) {
    return `${hours}h`;
  } else {
    return `${minutes}m`;
  }
}

function isSameDay(date1, date2) {
  return (
    date1.getFullYear() === date2.getFullYear() &&
    date1.getMonth() === date2.getMonth() &&
    date1.getDate() === date2.getDate()
  );
}

function previousDay() {
  const newDate = new Date(selectedDate.value);
  newDate.setDate(newDate.getDate() - 1);
  selectedDate.value = newDate;
  dateInput.value = formatDateInput(newDate);
}

function nextDay() {
  const newDate = new Date(selectedDate.value);
  newDate.setDate(newDate.getDate() + 1);
  selectedDate.value = newDate;
  dateInput.value = formatDateInput(newDate);
}

function handleDateChange() {
  selectedDate.value = new Date(dateInput.value);
}
</script>

<style scoped>
.daily-view {
  max-width: 900px;
  margin: 0 auto;
}

.date-selector {
  background: var(--bg-primary);
}

.current-date h3 {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-primary);
}

.timeline {
  position: relative;
  padding-left: 40px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, var(--primary-color), var(--secondary-color));
  border-radius: 2px;
}

.timeline-item {
  position: relative;
  margin-bottom: 24px;
  cursor: pointer;
}

.timeline-marker {
  position: absolute;
  left: -28px;
  top: 24px;
  width: 16px;
  height: 16px;
  background: var(--primary-color);
  border: 3px solid var(--bg-primary);
  border-radius: 50%;
  box-shadow: 0 0 0 4px var(--bg-secondary);
  z-index: 1;
}

.timeline-content {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.event-title {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--text-primary);
}

.event-time {
  color: var(--text-secondary);
  font-weight: 500;
  display: flex;
  align-items: center;
}

.event-time i {
  color: var(--primary-color);
}

.event-description {
  color: var(--text-secondary);
  line-height: 1.6;
}

.event-details {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.detail-item {
  color: var(--text-secondary);
  font-size: 0.95rem;
}

.detail-item i {
  color: var(--primary-color);
}

.empty-state i {
  display: block;
}

/* Responsive */
@media (max-width: 768px) {
  .timeline {
    padding-left: 30px;
  }

  .timeline::before {
    left: 15px;
  }

  .timeline-marker {
    left: -23px;
  }

  .current-date h3 {
    font-size: 1.4rem;
  }

  .event-title {
    font-size: 1.1rem;
  }
}
</style>
