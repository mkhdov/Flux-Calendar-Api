# Zoom Meeting Integration Documentation

## Overview

This document describes the Zoom meeting integration in the Flux Calendar Service. The integration allows users to create, update, and delete Zoom meetings directly from calendar events.

## Architecture

### Components

1. **Conference Entity** (`Conference.java`)
   - Extended to support both Google Meet and Zoom meetings
   - Stores Zoom-specific fields like meeting ID, join URL, start URL, password, etc.
   - Uses `ConferenceType.ZOOM` enum value

2. **ZoomApiClient** (`ZoomApiClient.java`)
   - Handles all direct API calls to Zoom
   - Manages OAuth 2.0 authentication
   - Provides methods for meeting CRUD operations

3. **ZoomMeetingService** (`ZoomMeetingService.java`)
   - Business logic layer for Zoom integration
   - Manages token storage and refresh in Redis
   - Coordinates between Event, Conference, and Zoom API

4. **ZoomController** (`ZoomController.java`)
   - REST API endpoints for Zoom operations
   - Swagger documentation for all endpoints

## Setup

### 1. Create Zoom App

1. Go to [Zoom App Marketplace](https://marketplace.zoom.us/)
2. Click "Develop" → "Build App"
3. Choose "OAuth" type app
4. Configure OAuth settings:
   - Redirect URL: `http://your-domain/zoom/callback`
   - Scopes: `meeting:write`, `meeting:read`, `user:read`

### 2. Environment Variables

Add the following environment variables to your `.env` file:

```bash
ZOOM_CLIENT_ID=your_zoom_client_id
ZOOM_CLIENT_SECRET=your_zoom_client_secret
ZOOM_REDIRECT_URI=http://localhost:8083/zoom/callback
```

### 3. Database Migration

The database migration script `V9__add_zoom_fields_to_conferences.sql` will automatically add the required fields to the `conferences` table:

- `zoom_meeting_id`
- `start_url`
- `host_email`
- `duration`
- `timezone`
- `agenda`
- `settings`

## API Endpoints

### OAuth Connection

#### Connect Zoom Account
```
GET /zoom/connect
Headers:
  X-User-Id: {userId}

Response: 302 Redirect to Zoom authorization page
```

#### OAuth Callback
```
GET /zoom/callback?code={authorization_code}&state={user_id}
Headers:
  X-User-Id: {userId}

Response:
{
  "success": true,
  "email": "user@example.com",
  "message": "Zoom connected successfully"
}
```

#### Disconnect Zoom Account
```
DELETE /zoom/disconnect
Headers:
  X-User-Id: {userId}

Response:
{
  "success": true,
  "message": "Zoom disconnected successfully"
}
```

#### Check Connection Status
```
GET /zoom/status
Headers:
  X-User-Id: {userId}

Response:
{
  "connected": true
}
```

### Meeting Management

#### Create Zoom Meeting
```
POST /zoom/events/{eventId}/meeting
Headers:
  X-User-Id: {userId}

Response:
{
  "success": true,
  "meeting": {
    "id": "conf-123",
    "type": "ZOOM",
    "zoomMeetingId": "123456789",
    "conferenceLink": "https://zoom.us/j/123456789",
    "startUrl": "https://zoom.us/s/123456789",
    "conferencePassword": "abc123",
    "duration": 60,
    "timezone": "UTC"
  },
  "joinUrl": "https://zoom.us/j/123456789",
  "message": "Zoom meeting created successfully"
}
```

#### Update Zoom Meeting
```
PATCH /zoom/events/{eventId}/meeting
Headers:
  X-User-Id: {userId}

Response:
{
  "success": true,
  "meeting": {...},
  "message": "Zoom meeting updated successfully"
}
```

#### Delete Zoom Meeting
```
DELETE /zoom/events/{eventId}/meeting
Headers:
  X-User-Id: {userId}

Response:
{
  "success": true,
  "message": "Zoom meeting deleted successfully"
}
```

## Data Flow

### Creating a Zoom Meeting

1. User requests to create a Zoom meeting for an event
2. Service checks if user has valid Zoom access token in Redis
3. If token expired, automatically refreshes using refresh token
4. Service builds meeting request from event details
5. Calls Zoom API to create meeting
6. Stores meeting details in Conference table
7. Links conference to event
8. Returns meeting details including join URL

### Token Management

- Access tokens are stored in Redis with key: `zoom:token:{userId}`
- Refresh tokens are stored in Redis with key: `zoom:refresh:{userId}`
- Access tokens expire after ~1 hour (set to expire 5 minutes early for safety)
- Tokens are automatically refreshed when needed

## Meeting Settings

Default meeting settings:
- Host video: ON
- Participant video: ON
- Join before host: OFF
- Mute upon entry: ON
- Waiting room: ON
- Auto recording: Cloud

These can be customized in `ZoomMeetingService.buildMeetingRequest()`.

## Error Handling

The service handles the following error scenarios:

1. **Event not found**: Returns `ResourceNotFoundException`
2. **Zoom not connected**: Returns error message asking user to connect Zoom
3. **Token expired**: Automatically refreshes token
4. **API errors**: Logs error and throws `RuntimeException` with details
5. **Conference not found**: Returns `ResourceNotFoundException` when updating/deleting

## Testing

Comprehensive tests are provided:

### Unit Tests
- `ZoomApiClientTest`: Tests API client methods
- `ZoomMeetingServiceTest`: Tests service layer logic
- `MemberServiceTest`: Tests member management

### Integration Tests
- `ZoomControllerTest`: Tests REST endpoints
- `MemberControllerTest`: Tests member endpoints

Run tests:
```bash
mvn test -Dtest=*Zoom*
mvn test -Dtest=*Member*
```

## Swagger Documentation

Access Swagger UI at: `http://localhost:8083/swagger-ui.html`

All Zoom and Member endpoints are documented with:
- Operation summaries and descriptions
- Parameter descriptions
- Response codes and meanings
- Request/response schemas

## Security Considerations

1. **OAuth 2.0**: Secure authentication flow
2. **Token Storage**: Tokens stored in Redis with expiration
3. **HTTPS**: Always use HTTPS in production
4. **State Parameter**: Use state parameter in OAuth to prevent CSRF
5. **User Authorization**: Verify user owns the event before creating/modifying meetings

## Troubleshooting

### Common Issues

1. **"Zoom not connected" error**
   - User needs to connect their Zoom account via `/zoom/connect`

2. **Token refresh fails**
   - User needs to reconnect Zoom account
   - Check if Zoom app credentials are correct

3. **Meeting creation fails**
   - Verify Zoom app has required scopes
   - Check if user's Zoom account has meeting creation permissions
   - Verify event has valid start and end times

4. **Database errors**
   - Ensure migration V9 has run successfully
   - Check database connection

## Future Enhancements

Potential improvements:
1. Webhook integration for real-time meeting updates
2. Support for recurring meetings
3. Meeting recording management
4. Participant management
5. Meeting analytics and reporting
6. Calendar sync with Zoom calendar

## References

- [Zoom API Documentation](https://marketplace.zoom.us/docs/api-reference/introduction)
- [Zoom OAuth Documentation](https://marketplace.zoom.us/docs/guides/auth/oauth)
- [Zoom Meeting APIs](https://marketplace.zoom.us/docs/api-reference/zoom-api/methods#operation/meetingCreate)
