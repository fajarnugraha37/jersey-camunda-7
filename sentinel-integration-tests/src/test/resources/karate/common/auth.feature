Feature: Shared authentication helper

  Scenario:
    * def credentials = __arg
    * if (!credentials || !credentials.username) karate.fail('username is required')
    * def password = credentials.password || defaultPassword
    Given url keycloakBaseUrl
    And path 'realms', realm, 'protocol', 'openid-connect', 'token'
    And form field client_id = clientId
    And form field grant_type = 'password'
    And form field username = credentials.username
    And form field password = password
    When method post
    Then status 200
    * def accessToken = response.access_token
    * def refreshToken = response.refresh_token
    * def expiresIn = response.expires_in
