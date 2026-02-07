# Zoom Integration - API Testing Examples

This document provides example API requests for testing the Zoom integration.

## Prerequisites

1. Create a Zoom OAuth app at [Zoom App Marketplace](https://marketplace.zoom.us/)
2. Add environment variables to `.env`:
   ```
   ZOOM_CLIENT_ID=your_client_id
   ZOOM_CLIENT_SECRET=your_client_secret
   ZOOM_REDIRECT_URI=http://localhost:8083/zoom/callback
   ```
3. Start the calendar service

## 1. Connect Zoom Account

### Step 1: Initiate OAuth Flow
```bash
curl -X GET "http://localhost:8083/zoom/connect" \
  -H "X-User-Id: user-123" \
  -L
```

This will redirect you to Zoom's authorization page. After authorizing, Zoom will redirect back to your callback URL with an authorization code.

### Step 2: Handle Callback (Automatic)
The callback endpoint will automatically exchange the authorization code for tokens:
```
GET http://localhost:8083/zoom/callback?code=AUTHORIZATION_CODE&state=user-123
```

Response:
```json
{
  "success": true,
  "email": "user@example.com",
  "message": "Zoom connected successfully"
}
```

## 2. Check Connection Status

```bash
curl -X GET "http://localhost:8083/zoom/status" \
  -H "X-User-Id: user-123"
```

Response:
```json
{
  "connected": true
}
```

## 3. Create Event (First)

Before creating a Zoom meeting, you need an event:

```bash
curl -X POST "http://localhost:8083/events/calendar/calendar-123" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{
    "title": "Team Meeting",
    "description": "Weekly team sync",
    "startTime": "2026-02-01T10:00:00",
    "endTime": "2026-02-01T11:00:00",
    "colorHex": "#4285F4",
    "type": "MEETING",
    "allDay": false
  }'
```

Response will include the event ID, e.g., `event-456`

## 4. Create Zoom Meeting for Event

```bash
curl -X POST "http://localhost:8083/zoom/events/event-456/meeting" \
  -H "X-User-Id: user-123"
```

Response:
```json
{
  "success": true,
  "meeting": {
    "id": "conf-789",
    "type": "ZOOM",
    "zoomMeetingId": "123456789",
    "conferenceLink": "https://zoom.us/j/123456789",
    "startUrl": "https://zoom.us/s/123456789",
    "conferencePassword": "abc123",
    "hostEmail": "user@example.com",
    "duration": 60,
    "timezone": "America/New_York",
    "agenda": "Weekly team sync",
    "platformName": "Zoom"
  },
  "joinUrl": "https://zoom.us/j/123456789",
  "message": "Zoom meeting created successfully"
}
```

## 5. Update Zoom Meeting

Update the event first, then update the Zoom meeting:

```bash
# Update event
curl -X PUT "http://localhost:8083/events/event-456" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{
    "title": "Team Meeting - Updated",
    "description": "Weekly team sync with Q1 planning",
    "startTime": "2026-02-01T14:00:00",
    "endTime": "2026-02-01T15:30:00"
  }'

# Update Zoom meeting
curl -X PATCH "http://localhost:8083/zoom/events/event-456/meeting" \
  -H "X-User-Id: user-123"
```

Response:
```json
{
  "success": true,
  "meeting": {
    "id": "conf-789",
    "duration": 90,
    "agenda": "Weekly team sync with Q1 planning"
  },
  "message": "Zoom meeting updated successfully"
}
```

## 6. Delete Zoom Meeting

```bash
curl -X DELETE "http://localhost:8083/zoom/events/event-456/meeting" \
  -H "X-User-Id": user-123"
```

Response:
```json
{
  "success": true,
  "message": "Zoom meeting deleted successfully"
}
```

## 7. Disconnect Zoom Account

```bash
curl -X DELETE "http://localhost:8083/zoom/disconnect" \
  -H "X-User-Id: user-123"
```

Response:
```json
{
  "success": true,
  "message": "Zoom disconnected successfully"
}
```

---

## Member Management Examples

### 1. Invite Member to Event

```bash
curl -X GET "http://localhost:8083/members/share/event/event-456/newmember@example.com"
```

This sends an invitation email to the specified email address.

### 2. Join Event

```bash
curl -X POST "http://localhost:8083/members/join/event/event-456/newmember@example.com" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe"
  }'
```

Response:
```json
"member-id-123"
```

### 3. Get Event Members

```bash
curl -X GET "http://localhost:8083/members/event/event-456"
```

Response:
```json
[
  {
    "id": "member-001",
    "email": "owner@example.com",
    "fullName": "Event Owner",
    "isOwner": true,
    "createdAt": "2026-01-28T10:00:00"
  },
  {
    "id": "member-002",
    "email": "newmember@example.com",
    "fullName": "John Doe",
    "isOwner": false,
    "createdAt": "2026-01-28T14:30:00"
  }
]
```

### 4. Delete Member

```bash
curl -X GET "http://localhost:8083/members/delete/member-002?userId=owner-user-id"
```

Note: Only the event owner can delete members.

---

## Testing with Swagger UI

The easiest way to test all endpoints is using Swagger UI:

1. Start the calendar service
2. Navigate to: `http://localhost:8083/swagger-ui.html`
3. Expand the "Zoom Integration" or "Members" sections
4. Click "Try it out" on any endpoint
5. Fill in the parameters
6. Click "Execute"

Swagger UI provides:
- Interactive API testing
- Request/response schemas
- Example values
- Error responses

---

## Error Scenarios

### 1. Zoom Not Connected
```bash
curl -X POST "http://localhost:8083/zoom/events/event-456/meeting" \
  -H "X-User-Id: user-without-zoom"
```

Response (400):
```json
{
  "error": "Zoom not connected. Please connect your Zoom account."
}
```

### 2. Event Not Found
```bash
curl -X POST "http://localhost:8083/zoom/events/invalid-event-id/meeting" \
  -H "X-User-Id: user-123"
```

Response (404):
```json
{
  "error": "Event not found"
}
```

### 3. Meeting Already Exists
If you try to create a meeting for an event that already has a Zoom meeting, the operation will fail. Delete the existing meeting first.

---

## Notes

- Replace `user-123`, `event-456`, `calendar-123` with actual IDs from your system
- The `X-User-Id` header is used for testing. In production, this would be extracted from JWT token
- All timestamps should be in ISO 8601 format
- The service automatically handles token refresh, so meetings can be created/updated even if the access token expired

---

## Postman Collection

You can also import these requests into Postman. Create a new collection and add:

1. **Environment Variables**:
   - `base_url`: `http://localhost:8083`
   - `user_id`: `your-user-id`
   - `event_id`: `your-event-id`

2. **Headers** (Global):
   - `X-User-Id`: `{{user_id}}`

3. Import each curl command as a new request

This makes testing much easier with variable substitution!
