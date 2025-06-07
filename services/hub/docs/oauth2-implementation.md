# OAuth2 Implementation in Eden Hub Service

This document describes the OAuth2 implementation in the Eden Hub service, which enables secure integration with third-party services.

## Overview

The OAuth2 implementation in the Hub service provides a secure way to authenticate and authorize access to external services. It follows the OAuth2 authorization code flow, which is the most secure OAuth2 flow for server-side applications.

## Key Components

### 1. OAuth2 Flow Creation

The `createOAuth2Flow` method in `IntegrationEngine` initiates the OAuth2 authorization process:

```kotlin
suspend fun createOAuth2Flow(
    config: OAuth2Config,
    integrationId: String
): HubResult<String>
```

This method:
- Generates a secure random state parameter to prevent CSRF attacks
- Builds the authorization URL with the appropriate parameters
- Stores the state parameter for later validation
- Returns the authorization URL that the user should visit

### 2. OAuth2 Callback Handling

The `handleOAuth2Callback` method processes the callback from the OAuth2 provider:

```kotlin
suspend fun handleOAuth2Callback(
    integrationId: String,
    code: String,
    state: String,
    config: OAuth2Config
): HubResult<OAuth2Token>
```

This method:
- Validates the state parameter to ensure it matches the one generated during flow creation
- Exchanges the authorization code for an access token
- Cleans up the stored state parameter
- Returns the OAuth2 token information

### 3. OAuth2 Token Refresh

The `refreshOAuth2Token` method refreshes an expired OAuth2 token:

```kotlin
suspend fun refreshOAuth2Token(
    refreshToken: String,
    config: OAuth2Config
): HubResult<OAuth2Token>
```

This method:
- Uses the refresh token to obtain a new access token
- Returns the updated token information

## Security Features

### State Parameter Management

The implementation includes robust state parameter management to prevent CSRF attacks:

1. **Generation**: Secure random state parameters are generated using cryptographically secure random number generators.
2. **Storage**: States are stored with expiration times (10 minutes) to limit the window of vulnerability.
3. **Validation**: State parameters are validated using constant-time comparison to prevent timing attacks.
4. **Cleanup**: States are automatically removed after validation or when they expire.

### Token Security

OAuth2 tokens are handled securely:

1. **Encryption**: Tokens are encrypted before storage using the platform's encryption service.
2. **Limited Exposure**: Access tokens are never exposed to the client-side code.
3. **Automatic Refresh**: Tokens are automatically refreshed when they expire.

## Configuration

OAuth2 integrations are configured using the `OAuth2Config` class:

```kotlin
data class OAuth2Config(
    val clientId: String,
    val clientSecret: String,
    val authorizationUrl: String,
    val tokenUrl: String,
    val redirectUri: String,
    val scopes: List<String>
)
```

## Implementation Details

### Token Exchange

The token exchange process:

1. Makes an HTTP POST request to the token endpoint
2. Includes the authorization code, client ID, client secret, and redirect URI
3. Processes the JSON response to extract the access token, refresh token, and expiration time
4. Handles errors appropriately

### Token Refresh

The token refresh process:

1. Makes an HTTP POST request to the token endpoint
2. Includes the refresh token, client ID, and client secret
3. Processes the JSON response to extract the new access token and, if provided, a new refresh token
4. Handles errors appropriately

## Error Handling

The implementation includes comprehensive error handling:

1. **Network Errors**: Timeouts, connection failures, and other network issues are caught and reported.
2. **Authorization Errors**: Invalid codes, expired tokens, and other authorization issues are handled gracefully.
3. **Server Errors**: Unexpected responses from OAuth2 providers are properly handled.

## Best Practices

The implementation follows these OAuth2 best practices:

1. **HTTPS Only**: All OAuth2 communication occurs over HTTPS.
2. **State Parameter**: A secure random state parameter is used to prevent CSRF attacks.
3. **Limited Scope**: Only the minimum required scopes are requested.
4. **Token Storage**: Tokens are encrypted before storage.
5. **Automatic Refresh**: Tokens are automatically refreshed when they expire.
6. **Error Handling**: Comprehensive error handling ensures a smooth user experience.

## Testing

The OAuth2 implementation includes comprehensive tests:

1. **Unit Tests**: Individual components are tested in isolation.
2. **Integration Tests**: The complete OAuth2 flow is tested with mock OAuth2 providers.
3. **Security Tests**: Security features are tested to ensure they provide the expected protection.