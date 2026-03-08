<template>
  <div class="calendar-view card p-4">
    <FullCalendar :options="calendarOptions" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import FullCalendar from '@fullcalendar/vue3';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import bootstrap5Plugin from '@fullcalendar/bootstrap5';
import { useThemeStore } from '../stores/theme';

const props = defineProps({
  events: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['event-click', 'date-click']);

const themeStore = useThemeStore();

const calendarOptions = computed(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin, bootstrap5Plugin],
  themeSystem: 'bootstrap5',
  initialView: 'dayGridMonth',
  headerToolbar: {
    left: 'prev,next today',
    center: 'title',
    right: 'dayGridMonth,timeGridWeek,timeGridDay',
  },
  buttonText: {
    today: 'Today',
    month: 'Month',
    week: 'Week',
    day: 'Day',
  },
  events: props.events,
  editable: true,
  selectable: true,
  selectMirror: true,
  dayMaxEvents: true,
  weekends: true,
  eventClick: handleEventClick,
  dateClick: handleDateClick,
  eventTimeFormat: {
    hour: '2-digit',
    minute: '2-digit',
    meridiem: 'short',
  },
  slotLabelFormat: {
    hour: '2-digit',
    minute: '2-digit',
    meridiem: 'short',
  },
  height: 'auto',
  contentHeight: 650,
  aspectRatio: 1.8,
}));

const handleEventClick = (info) => {
  emit('event-click', info.event);
};

const handleDateClick = (info) => {
  emit('date-click', info.dateStr);
};
</script>

<style>
/* FullCalendar customization */
.fc {
  font-family: 'Inter', sans-serif;
}

.fc .fc-button-primary {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  border: none;
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.fc .fc-button-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.fc .fc-button-primary:not(:disabled).fc-button-active {
  background: var(--primary-hover);
}

.fc .fc-toolbar-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-primary);
}

.fc-theme-bootstrap5 .fc-scrollgrid {
  border-color: var(--border-color);
}

.fc-theme-bootstrap5 th,
.fc-theme-bootstrap5 td {
  border-color: var(--border-color);
  background: var(--bg-primary);
}

.fc .fc-daygrid-day-number {
  color: var(--text-primary);
  font-weight: 600;
  padding: 8px;
}

.fc .fc-col-header-cell {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-weight: 700;
  text-transform: uppercase;
  font-size: 0.85rem;
  padding: 12px;
}

.fc .fc-daygrid-day.fc-day-today {
  background: rgba(99, 102, 241, 0.1) !important;
}

.fc .fc-event {
  border: none;
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.fc .fc-event:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

.fc .fc-event-title {
  font-weight: 600;
}

.fc .fc-daygrid-day:hover {
  background: var(--bg-secondary);
  cursor: pointer;
}

/* Dark theme adjustments */
[data-theme="dark"] .fc .fc-scrollgrid {
  border-color: var(--border-color);
}

[data-theme="dark"] .fc-theme-bootstrap5 th,
[data-theme="dark"] .fc-theme-bootstrap5 td {
  background: var(--bg-primary);
}

[data-theme="dark"] .fc .fc-col-header-cell {
  background: var(--bg-tertiary);
}

/* Responsive */
@media (max-width: 768px) {
  .fc .fc-toolbar {
    flex-direction: column;
    gap: 12px;
  }

  .fc .fc-toolbar-title {
    font-size: 1.4rem;
  }

  .fc .fc-button {
    padding: 6px 12px;
    font-size: 0.85rem;
  }
}
</style>
