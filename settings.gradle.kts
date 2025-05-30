rootProject.name = "eden"

// Shared libraries
include(":shared:core")
include(":shared:database")
include(":shared:crypto")
include(":shared:events")
include(":shared:auth")
include(":shared:config")

// Services
include(":services:api-gateway")
include(":services:vault")
include(":services:flow")
include(":services:task")
include(":services:monitor")
include(":services:sync")
include(":services:insight")
include(":services:hub")

// Clients
include(":clients:web")
include(":clients:cli")
include(":clients:mobile")

// Tools and utilities
include(":tools:code-generation")
include(":tools:migration")
include(":tools:monitoring")