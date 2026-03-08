<template>
  <div class="dashboard-container">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo">
          <i class="bi bi-calendar-check-fill"></i>
          <span v-if="!sidebarCollapsed">Flux Calendar</span>
        </div>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <i :class="sidebarCollapsed ? 'bi bi-chevron-right' : 'bi bi-chevron-left'"></i>
        </button>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="view in views"
          :key="view.id"
          class="nav-item"
          :class="{ active: currentView === view.id }"
          @click="currentView = view.id"
        >
          <i :class="view.icon"></i>
          <span v-if="!sidebarCollapsed">{{ view.label }}</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <button class="nav-item" @click="toggleTheme">
          <i :class="themeStore.isDark ? 'bi bi-sun-fill' : 'bi bi-moon-fill'"></i>
          <span v-if="!sidebarCollapsed">
            {{ themeStore.isDark ? 'Light Mode' : 'Dark Mode' }}
          </span>
        </button>
        <button class="nav-item" @click="handleLogout">
          <i class="bi bi-box-arrow-right"></i>
          <span v-if="!sidebarCollapsed">Logout</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <!-- Header -->
      <header class="dashboard-header">
        <div class="header-left">
          <h1 class="page-title">{{ views.find(v => v.id === currentView)?.label }}</h1>
        </div>
        <div class="header-right">
          <button class="btn btn-primary" @click="showEventModal = true">
            <i class="bi bi-plus-lg me-2"></i>
            New Event
          </button>
          <div class="user-menu">
            <div class="user-avatar">
              <i class="bi bi-person-circle"></i>
            </div>
          </div>
        </div>
      </header>

      <!-- Calendar Selector -->
      <div class="calendar-selector mb-4" v-if="calendarStore.calendars.length > 0">
        <label class="form-label">Select Calendar:</label>
        <select
          class="form-select"
          v-model="selectedCalendarId"
          @change="handleCalendarChange"
        >
          <option
            v-for="calendar in calendarStore.calendars"
            :key="calendar.id"
            :value="calendar.id"
          >
            {{ calendar.title }}
          </option>
        </select>
      </div>

      <!-- View Content -->
      <div class="view-content">
        <div v-if="loading" class="loading-container">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>

        <CalendarView
          v-else-if="currentView === 'calendar'"
          :events="calendarStore.events"
          @event-click="handleEventClick"
          @date-click="handleDateClick"
        />

        <ScheduleView
          v-else-if="currentView === 'schedule'"
          :events="calendarStore.events"
          @event-click="handleEventClick"
        />

        <DailyView
          v-else-if="currentView === 'daily'"
          :events="calendarStore.events"
          @event-click="handleEventClick"
        />

        <CalendarManagement
          v-else-if="currentView === 'calendars'"
        />

        <SettingsView
          v-else-if="currentView === 'settings'"
        />
      </div>
    </main>

    <!-- Event Modal -->
    <EventModal
      v-if="showEventModal"
      :event="selectedEvent"
      :initialDate="selectedDate"
      @close="closeEventModal"
      @save="handleEventSave"
      @delete="handleEventDelete"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useCalendarStore } from '../stores/calendar';
import { useThemeStore } from '../stores/theme';
import CalendarView from '../components/CalendarView.vue';
import ScheduleView from '../components/ScheduleView.vue';
import DailyView from '../components/DailyView.vue';
import EventModal from '../components/EventModal.vue';
import CalendarManagement from '../components/CalendarManagement.vue';
import SettingsView from '../views/Settings.vue';

const router = useRouter();
const authStore = useAuthStore();
const calendarStore = useCalendarStore();
const themeStore = useThemeStore();

const sidebarCollapsed = ref(false);
const currentView = ref('calendar');
const showEventModal = ref(false);
const selectedEvent = ref(null);
const selectedDate = ref(null);
const loading = ref(true);
const selectedCalendarId = ref(null);

const views = [
  { id: 'calendar', label: 'Calendar', icon: 'bi bi-calendar3' },
  { id: 'schedule', label: 'Schedule', icon: 'bi bi-list-task' },
  { id: 'daily', label: 'Daily', icon: 'bi bi-calendar-day' },
  { id: 'calendars', label: 'Calendars', icon: 'bi bi-collection' },
  { id: 'settings', label: 'Settings', icon: 'bi bi-gear' },
];

onMounted(async () => {
  try {
    await calendarStore.fetchCalendars();
    if (calendarStore.primaryCalendar) {
      selectedCalendarId.value = calendarStore.primaryCalendar.id;
    }
  } catch (error) {
    console.error('Failed to load calendars:', error);
  } finally {
    loading.value = false;
  }
});

const handleCalendarChange = async () => {
  if (selectedCalendarId.value) {
    loading.value = true;
    try {
      await calendarStore.fetchEvents(selectedCalendarId.value);
    } finally {
      loading.value = false;
    }
  }
};

const handleEventClick = (event) => {
  selectedEvent.value = event;
  showEventModal.value = true;
};

const handleDateClick = (date) => {
  selectedDate.value = date;
  selectedEvent.value = null;
  showEventModal.value = true;
};

const closeEventModal = () => {
  showEventModal.value = false;
  selectedEvent.value = null;
  selectedDate.value = null;
};

const handleEventSave = async (eventData) => {
  try {
    if (selectedEvent.value) {
      // Update existing event
      // await calendarStore.updateEvent(selectedEvent.value.id, eventData);
    } else {
      // Create new event
      await calendarStore.addEvent(eventData, selectedCalendarId.value);
    }
    closeEventModal();
  } catch (error) {
    console.error('Failed to save event:', error);
  }
};

const handleEventDelete = async (eventId) => {
  try {
    await calendarStore.deleteEvent(eventId);
    closeEventModal();
  } catch (error) {
    console.error('Failed to delete event:', error);
  }
};

const toggleTheme = () => {
  themeStore.toggleTheme();
};

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};
</script>

<style scoped>
.dashboard-container {
  display: flex;
  min-height: 100vh;
  background: var(--bg-secondary);
}

/* Sidebar */
.sidebar {
  width: 280px;
  background: var(--bg-primary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  position: fixed;
  height: 100vh;
  z-index: 100;
}

.sidebar.collapsed {
  width: 80px;
}

.sidebar-header {
  padding: 24px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-color);
}

.logo i {
  font-size: 2rem;
}

.collapse-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 1.2rem;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.collapse-btn:hover {
  background: var(--bg-secondary);
  color: var(--primary-color);
}

.sidebar-nav {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 8px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 1rem;
  font-weight: 500;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.nav-item:hover {
  background: var(--bg-secondary);
  color: var(--primary-color);
}

.nav-item.active {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
}

.nav-item i {
  font-size: 1.3rem;
  min-width: 24px;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 12px;
}

.sidebar.collapsed .nav-item span {
  display: none;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--border-color);
}

/* Main Content */
.main-content {
  flex: 1;
  margin-left: 280px;
  transition: margin-left 0.3s ease;
}

.sidebar.collapsed ~ .main-content {
  margin-left: 80px;
}

.dashboard-header {
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  padding: 24px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 50;
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5rem;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.user-avatar:hover {
  transform: scale(1.05);
}

.view-content {
  padding: 32px;
  min-height: calc(100vh - 88px);
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.calendar-selector {
  padding: 0 32px;
  padding-top: 24px;
}

/* Responsive */
@media (max-width: 992px) {
  .sidebar {
    transform: translateX(-100%);
  }

  .sidebar.collapsed {
    transform: translateX(0);
    width: 80px;
  }

  .main-content {
    margin-left: 0;
  }

  .sidebar.collapsed ~ .main-content {
    margin-left: 80px;
  }
}

@media (max-width: 768px) {
  .dashboard-header {
    padding: 16px 20px;
  }

  .page-title {
    font-size: 1.4rem;
  }

  .view-content {
    padding: 20px;
  }

  .header-right .btn {
    padding: 8px 16px;
    font-size: 0.9rem;
  }
}
</style>
