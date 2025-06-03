rootProject.name = "eden"

// Shared libraries
include(":shared:core")
include(":shared:database")
include(":shared:crypto")
include(":shared:events")
include(":shared:auth")
include(":shared:config")
include(":shared:testing")
include(":shared:monitoring")
include(":shared:deployment")
include(":shared:analytics")
include(":shared:cloud")

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

// Testing modules
include(":integration-tests")
include(":e2e-tests")
include(":performance-tests")