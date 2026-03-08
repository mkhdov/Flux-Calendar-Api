import { defineStore } from 'pinia';
import api from '../api/axios';
import { useAuthStore } from './auth';

export const useCalendarStore = defineStore('calendar', {
    state: () => ({
        calendars: [],
        primaryCalendar: null,
        events: [],
        loading: false,
        error: null,
    }),
    actions: {
        async fetchCalendars() {
            const authStore = useAuthStore();
            if (!authStore.user?.id) {
                console.error("User ID not found in auth store");
                return;
            }

            this.loading = true;
            try {
                const response = await api.get(`/calendars/user/id/${authStore.user.id}`);
                this.calendars = response.data;

                if (this.calendars.length > 0) {
                    try {
                        const primaryRes = await api.get(`/calendars/user/primary/${authStore.user.id}`);
                        this.primaryCalendar = primaryRes.data;
                        if (this.primaryCalendar) {
                            await this.fetchEvents(this.primaryCalendar.id);
                        }
                    } catch (pError) {
                        console.warn("Could not fetch primary calendar specifically", pError);
                        this.primaryCalendar = this.calendars.find(c => c.isPrimary) || this.calendars[0];
                        await this.fetchEvents(this.primaryCalendar.id);
                    }
                }
            } catch (error) {
                this.error = error;
                console.error('Failed to fetch calendars:', error);
            } finally {
                this.loading = false;
            }
        },
        async fetchEvents(calendarId) {
            if (!calendarId) return;
            this.loading = true;
            try {
                const response = await api.get(`/events/calendar/${calendarId}`);
                this.events = response.data.map(event => ({
                    id: event.id,
                    title: event.title || 'Untitled Event',
                    start: event.startTime,
                    end: event.endTime,
                    allDay: event.allDay,
                    backgroundColor: event.colorHex,
                    borderColor: event.colorHex,
                    description: event.description,
                    extendedProps: {
                        status: event.status,
                        type: event.type,
                        location: event.location,
                        subtasks: event.subtasks,
                        attachments: event.attachments
                    }
                }));
            } catch (error) {
                this.error = error;
                console.error('Failed to fetch events:', error);
            } finally {
                this.loading = false;
            }
        },
        async addEvent(eventData, calendarId) {
            if (!calendarId) calendarId = this.primaryCalendar?.id;
            if (!calendarId) throw new Error("No calendar selected");

            try {
                const payload = {
                    title: eventData.title,
                    description: eventData.description,
                    startTime: eventData.startTime,
                    endTime: eventData.endTime,
                    allDay: eventData.allDay,
                    colorHex: eventData.colorHex,
                    type: eventData.type,
                    status: eventData.status,
                    date: eventData.date,
                    subtasks: eventData.subtasks || [],
                    attachments: eventData.attachments || []
                };
                const response = await api.post(`/events/calendar/${calendarId}`, payload);
                await this.fetchEvents(calendarId);
                return response.data;
            } catch (error) {
                console.error('Failed to add event:', error);
                throw error;
            }
        },
        async updateEvent(eventId, eventData, calendarId) {
            try {
                const payload = {
                    title: eventData.title,
                    description: eventData.description,
                    startTime: eventData.startTime,
                    endTime: eventData.endTime,
                    allDay: eventData.allDay,
                    colorHex: eventData.colorHex,
                    type: eventData.type,
                    status: eventData.status,
                    date: eventData.date
                };
                await api.put(`/events/${eventId}`, payload);
                await this.fetchEvents(calendarId);
            } catch (error) {
                console.error('Failed to update event:', error);
                throw error;
            }
        },
        async deleteEvent(eventId) {
            try {
                await api.delete(`/events/${eventId}`);
                this.events = this.events.filter(e => e.id !== eventId);
            } catch (error) {
                console.error('Failed to delete event:', error);
                throw error;
            }
        },
        async addCalendar(calendarData) {
            const authStore = useAuthStore();
            if (!authStore.user?.id) return;

            try {
                const payload = {
                    userId: authStore.user.id,
                    title: calendarData.title,
                    description: calendarData.description || '',
                    colorHex: calendarData.colorHex || '#6366f1',
                    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                    isPrimary: calendarData.isPrimary || false
                };
                const response = await api.post('/calendars', payload);
                this.calendars.push(response.data);
                return response.data;
            } catch (error) {
                console.error('Failed to add calendar:', error);
                throw error;
            }
        },
        async deleteCalendar(calendarId) {
            try {
                await api.delete(`/calendars/${calendarId}`);
                this.calendars = this.calendars.filter(c => c.id !== calendarId);
                if (this.primaryCalendar?.id === calendarId) {
                    this.primaryCalendar = this.calendars.find(c => c.isPrimary) || this.calendars[0] || null;
                }
            } catch (error) {
                console.error('Failed to delete calendar:', error);
                throw error;
            }
        }
    },
});
