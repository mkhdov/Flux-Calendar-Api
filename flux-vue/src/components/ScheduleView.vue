<template>
  <div class="schedule-view card">
    <div class="schedule-header p-4 border-bottom">
      <h3 class="mb-0">
        <i class="bi bi-list-check me-2"></i>
        Upcoming Events
      </h3>
    </div>

    <div v-if="sortedEvents.length === 0" class="empty-state p-5 text-center">
      <i class="bi bi-calendar-x display-1 text-muted mb-3"></i>
      <h4 class="text-muted">No events scheduled</h4>
      <p class="text-secondary">Create your first event to get started!</p>
    </div>

    <div v-else class="schedule-list">
      <div
        v-for="event in sortedEvents"
        :key="event.id"
        class="event-item"
        @click="$emit('event-click', event)"
      >
        <div class="event-date">
          <div class="month">{{ formatMonth(event.start) }}</div>
          <div class="day">{{ formatDay(event.start) }}</div>
          <div class="weekday">{{ formatWeekday(event.start) }}</div>
        </div>

        <div class="event-details">
          <h5 class="event-title">{{ event.title }}</h5>
          <div class="event-meta">
            <span class="event-time">
              <i class="bi bi-clock me-1"></i>
              {{ formatTime(event.start) }} - {{ formatTime(event.end) }}
            </span>
            <span v-if="event.location" class="event-location">
              <i class="bi bi-geo-alt me-1"></i>
              {{ event.location?.streetAddress || 'Location' }}
            </span>
          </div>
          <p v-if="event.description" class="event-description">
            {{ event.description }}
          </p>
        </div>

        <div class="event-actions">
          <button class="btn btn-sm btn-outline-primary" @click.stop="$emit('event-click', event)">
            <i class="bi bi-pencil"></i>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  events: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['event-click']);

const sortedEvents = computed(() => {
  return [...props.events].sort((a, b) => {
    const dateA = new Date(a.start);
    const dateB = new Date(b.start);
    return dateA - dateB;
  });
});

const formatMonth = (date) => {
  return new Date(date).toLocaleDateString('en-US', { month: 'short' });
};

const formatDay = (date) => {
  return new Date(date).getDate();
};

const formatWeekday = (date) => {
  return new Date(date).toLocaleDateString('en-US', { weekday: 'short' });
};

const formatTime = (date) => {
  return new Date(date).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
};
</script>

<style scoped>
.schedule-view {
  background: var(--bg-primary);
  border-radius: 12px;
  overflow: hidden;
}

.schedule-header {
  background: var(--bg-secondary);
}

.schedule-header h3 {
  color: var(--text-primary);
  font-weight: 700;
}

.schedule-list {
  max-height: 700px;
  overflow-y: auto;
}

.event-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s ease;
}

.event-item:last-child {
  border-bottom: none;
}

.event-item:hover {
  background: var(--bg-secondary);
  transform: translateX(4px);
}

.event-date {
  flex-shrink: 0;
  width: 80px;
  text-align: center;
  padding: 12px;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border-radius: 12px;
  color: white;
}

.event-date .month {
  font-size: 0.85rem;
  font-weight: 600;
  text-transform: uppercase;
  opacity: 0.9;
}

.event-date .day {
  font-size: 2rem;
  font-weight: 700;
  line-height: 1;
  margin: 4px 0;
}

.event-date .weekday {
  font-size: 0.85rem;
  opacity: 0.9;
}

.event-details {
  flex: 1;
  min-width: 0;
}

.event-title {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 8px;
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.event-meta i {
  color: var(--primary-color);
}

.event-description {
  font-size: 0.95rem;
  color: var(--text-secondary);
  margin: 8px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.event-actions {
  flex-shrink: 0;
}

.empty-state i {
  display: block;
}

/* Responsive */
@media (max-width: 768px) {
  .event-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .event-date {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-around;
  }

  .event-date .day {
    font-size: 1.5rem;
  }

  .event-meta {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
