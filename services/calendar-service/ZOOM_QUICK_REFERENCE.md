# Zoom Integration - Quick Reference

## 🚀 Quick Start

### 1. Setup Zoom App (One Time)
1. Go to https://marketplace.zoom.us/
2. Create OAuth app
3. Add scopes: `meeting:write`, `meeting:read`, `user:read`
4. Set redirect: `http://localhost:8083/zoom/callback`

### 2. Configure Environment
```bash
# Add to .env
ZOOM_CLIENT_ID=your_client_id
ZOOM_CLIENT_SECRET=your_client_secret
ZOOM_REDIRECT_URI=http://localhost:8083/zoom/callback
```

### 3. Run Service
```bash
cd services/calendar-service
mvn spring-boot:run
```

## 📡 API Quick Reference

### Connect Zoom (First Time Only)
```bash
# 1. Initiate OAuth
GET /zoom/connect
Header: X-User-Id: user-123

# 2. User authorizes → Zoom redirects to callback
# 3. Done! User connected
```

### Create Event with Zoom
```bash
POST /events/calendar/{calendarId}/add-zoom
Header: X-User-Id: user-123
Header: Content-Type: application/json
Body: {
  "title": "Meeting Title",
  "startTime": "2026-02-01T10:00:00",
  "endTime": "2026-02-01T11:00:00",
  "type": "MEETING"
}
```

### Add Zoom to Existing Event
```bash
POST /events/add-zoom/{eventId}
Header: X-User-Id: user-123
```

### Remove Zoom from Event
```bash
DELETE /events/remove-zoom/{eventId}
Header: X-User-Id: user-123
```

### Check Connection Status
```bash
GET /zoom/status
Header: X-User-Id: user-123
```

## 🔍 Key Differences: EventService vs ZoomController

### Via EventService (Recommended for Event Flow)
- ✅ Creates event AND meeting in one call
- ✅ Part of normal event creation flow
- ✅ Auto-creates owner member
- ✅ Integrated with event lifecycle

**Endpoints:**
- `POST /events/calendar/{calendarId}/add-zoom` - Create event + Zoom
- `POST /events/add-zoom/{id}` - Add to existing event
- `DELETE /events/remove-zoom/{id}` - Remove from event

### Via ZoomController (Direct Zoom Management)
- ✅ Direct Zoom API access
- ✅ For standalone meeting management
- ✅ More Zoom-specific control
- ✅ OAuth management

**Endpoints:**
- `POST /zoom/events/{eventId}/meeting` - Create Zoom meeting only
- `PATCH /zoom/events/{eventId}/meeting` - Update Zoom meeting
- `DELETE /zoom/events/{eventId}/meeting` - Delete Zoom meeting

## 🎯 Common Use Cases

### Use Case 1: Create New Event with Zoom
**Use:** `POST /events/calendar/{calendarId}/add-zoom`
```bash
curl -X POST "http://localhost:8083/events/calendar/cal-123/add-zoom" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{"title":"Team Sync","startTime":"2026-02-01T10:00:00","endTime":"2026-02-01T11:00:00","type":"MEETING"}'
```

### Use Case 2: Add Zoom to Event Created Without It
**Use:** `POST /events/add-zoom/{id}`
```bash
curl -X POST "http://localhost:8083/events/add-zoom/event-456" \
  -H "X-User-Id: user-123"
```

### Use Case 3: Remove Zoom but Keep Event
**Use:** `DELETE /events/remove-zoom/{id}`
```bash
curl -X DELETE "http://localhost:8083/events/remove-zoom/event-456" \
  -H "X-User-Id: user-123"
```

### Use Case 4: Update Zoom Meeting Details
**Use:** `PATCH /zoom/events/{eventId}/meeting`
```bash
# First update the event
curl -X PUT "http://localhost:8083/events/event-456" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated Title"}'

# Then update Zoom meeting
curl -X PATCH "http://localhost:8083/zoom/events/event-456/meeting" \
  -H "X-User-Id: user-123"
```

## ⚠️ Important Rules

1. **All-Day Events**: Cannot have Zoom meetings
2. **User Must Connect**: User must connect Zoom via `/zoom/connect` first
3. **Token Auto-Refresh**: Tokens refresh automatically
4. **One Meeting Per Event**: Can't have both Google Meet and Zoom
5. **X-User-Id Required**: All Zoom endpoints need this header

## 🐛 Troubleshooting

### "Zoom not connected"
```bash
# Connect user's Zoom account first
GET /zoom/connect
```

### "Cannot add Zoom to all-day events"
```bash
# Change event to non-all-day
PUT /events/{id}
Body: {"allDay": false, "startTime": "...", "endTime": "..."}

# Then add Zoom
POST /events/add-zoom/{id}
```

### "Event already has a Zoom meeting"
```bash
# Remove existing Zoom first
DELETE /events/remove-zoom/{id}

# Or just use the existing meeting
```

### "Invalid access token"
```bash
# Tokens refresh automatically
# If still failing, reconnect Zoom
DELETE /zoom/disconnect
GET /zoom/connect
```

## 📚 Documentation Locations

- **Full Integration Guide**: `ZOOM_INTEGRATION.md`
- **Event Integration**: `ZOOM_EVENT_INTEGRATION.md`
- **API Examples**: `API_TESTING_EXAMPLES.md`
- **Complete Summary**: `COMPLETE_ZOOM_INTEGRATION_SUMMARY.md`
- **Swagger UI**: `http://localhost:8083/swagger-ui.html`

## 🔗 Related Endpoints

### Event Management
- `POST /events/calendar/{id}` - Create event without meeting
- `POST /events/calendar/{id}/add-meet` - Create with Google Meet
- `PUT /events/{id}` - Update event
- `DELETE /events/{id}` - Delete event

### Member Management
- `GET /members/share/event/{eventId}/{email}` - Invite member
- `POST /members/join/event/{eventId}/{email}` - Join event
- `GET /members/event/{eventId}` - List members

### Conference Management
- `POST /conference/{id}/add-meet` - Add Google Meet
- `DELETE /conference/{id}/remove-meet` - Remove Google Meet

## 💡 Pro Tips

1. **Testing**: Use Swagger UI for interactive testing
2. **Logs**: Check logs for detailed error messages
3. **Redis**: Monitor Redis for token management
4. **Database**: Check `conferences` table for meeting data
5. **Postman**: Import curl commands as Postman collection

## 📞 Support

Check documentation files in `/services/calendar-service/`:
- ZOOM_INTEGRATION.md
- ZOOM_EVENT_INTEGRATION.md
- API_TESTING_EXAMPLES.md
- COMPLETE_ZOOM_INTEGRATION_SUMMARY.md
