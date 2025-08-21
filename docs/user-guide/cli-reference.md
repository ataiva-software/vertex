# CLI Reference

The Vertex CLI (`vertex`) is the primary command-line interface for interacting with all Vertex DevOps Suite components.

## Installation

### Build from Source

```bash
# Clone the repository
git clone https://github.com/ataiva-software/vertex.git
cd vertex

# Build CLI for your platform
./gradlew :clients:cli:linkReleaseExecutableLinuxX64    # Linux
./gradlew :clients:cli:linkReleaseExecutableMacosX64    # macOS
./gradlew :clients:cli:linkReleaseExecutableMingwX64    # Windows

# Add to PATH (Linux/macOS example)
sudo ln -s $(pwd)/clients/cli/build/bin/linuxX64/releaseExecutable/vertex /usr/local/bin/vertex
```

### Verify Installation

```bash
vertex --help
vertex --version
```

## Global Options

All Vertex commands support these global options:

```bash
vertex [global-options] <command> [command-options]
```

### Global Options
- `-v, --verbose` - Enable verbose output
- `-c, --config <path>` - Specify configuration file path
- `-p, --profile <name>` - Use specific configuration profile (default: "default")
- `--api-url <url>` - Override API base URL

### Examples
```bash
# Verbose output
vertex --verbose auth login

# Custom configuration
vertex --config ~/.vertex/custom.yml vault list

# Different profile
vertex --profile production monitor status

# Custom API URL
vertex --api-url https://vertex.company.com auth login
```

## Authentication Commands

### `vertex auth`

Manage authentication and user sessions.

#### `vertex auth login`

Login to Vertex platform.

```bash
vertex auth login [options]
```

**Options:**
- `--email <email>` - Email address for login
- `--password <password>` - Password (not recommended, will prompt if omitted)

**Examples:**
```bash
# Interactive login (recommended)
vertex auth login

# Login with email (will prompt for password)
vertex auth login --email user@company.com

# Non-interactive login (not recommended for security)
vertex auth login --email user@company.com --password mypassword
```

#### `vertex auth logout`

Logout from Vertex platform.

```bash
vertex auth logout
```

#### `vertex auth whoami`

Display current user information.

```bash
vertex auth whoami
```

**Output:**
```
User: john.doe@company.com
Organization: ACME Corp
Role: Developer
Session Expires: 2030-12-31 23:59:59 UTC
```

## Secrets Management Commands

### `vertex vault`

Manage secrets with zero-knowledge encryption.

> **Note**: Vault commands are currently in development. Most functionality returns "to be implemented" messages.

#### `vertex vault set`

Store a secret.

```bash
vertex vault set <key> <value> [options]
```

**Options:**
- `--description <text>` - Description for the secret
- `--tags <tag1,tag2>` - Comma-separated tags
- `--ttl <duration>` - Time-to-live (e.g., "30d", "1h")

**Examples:**
```bash
# Store a simple secret
vertex vault set DATABASE_URL "postgresql://user:pass@host:5432/db"

# Store with metadata
vertex vault set API_KEY "sk-1234567890" \
  --description "Production API key" \
  --tags "production,api" \
  --ttl "90d"
```

#### `vertex vault get`

Retrieve a secret.

```bash
vertex vault get <key> [options]
```

**Options:**
- `--show-metadata` - Include metadata in output
- `--format <json|yaml|env>` - Output format

**Examples:**
```bash
# Get secret value
vertex vault get DATABASE_URL

# Get with metadata
vertex vault get API_KEY --show-metadata

# Export format
vertex vault get DATABASE_URL --format env
```

#### `vertex vault list`

List available secrets.

```bash
vertex vault list [options]
```

**Options:**
- `--tags <tag1,tag2>` - Filter by tags
- `--format <table|json|yaml>` - Output format

**Examples:**
```bash
# List all secrets
vertex vault list

# Filter by tags
vertex vault list --tags production

# JSON output
vertex vault list --format json
```

#### `vertex vault delete`

Delete a secret.

```bash
vertex vault delete <key> [options]
```

**Options:**
- `--force` - Skip confirmation prompt

**Examples:**
```bash
# Delete with confirmation
vertex vault delete OLD_API_KEY

# Force delete
vertex vault delete OLD_API_KEY --force
```

## Workflow Commands

### `vertex flow`

Manage workflow automation.

> **Note**: Flow commands are currently in development.

#### `vertex flow create`

Create a new workflow.

```bash
vertex flow create <name> [options]
```

**Options:**
- `--template <template>` - Use workflow template
- `--file <path>` - Workflow definition file

**Examples:**
```bash
# Create from template
vertex flow create deployment --template ci-cd

# Create from file
vertex flow create custom-workflow --file ./workflow.yml
```

#### `vertex flow run`

Execute a workflow.

```bash
vertex flow run <workflow> [options]
```

**Options:**
- `--env <environment>` - Target environment
- `--params <key=value>` - Workflow parameters
- `--wait` - Wait for completion

**Examples:**
```bash
# Run workflow
vertex flow run deployment --env staging

# Run with parameters
vertex flow run deployment --env production --params version=1.2.3

# Run and wait
vertex flow run deployment --wait
```

#### `vertex flow status`

Check workflow status.

```bash
vertex flow status [workflow-id]
```

**Examples:**
```bash
# List all workflow runs
vertex flow status

# Check specific workflow
vertex flow status wf-12345
```

## Task Orchestration Commands

### `vertex task`

Manage distributed task execution.

> **Note**: Task commands are currently in development.

#### `vertex task submit`

Submit a task for execution.

```bash
vertex task submit <task-file> [options]
```

**Options:**
- `--priority <high|normal|low>` - Task priority
- `--schedule <cron>` - Schedule task execution
- `--wait` - Wait for completion

**Examples:**
```bash
# Submit task
vertex task submit build-job.yaml

# High priority task
vertex task submit urgent-task.yaml --priority high

# Scheduled task
vertex task submit backup.yaml --schedule "0 2 * * *"
```

#### `vertex task status`

Check task status.

```bash
vertex task status [task-id]
```

#### `vertex task logs`

View task execution logs.

```bash
vertex task logs <task-id> [options]
```

**Options:**
- `--follow` - Follow log output
- `--tail <lines>` - Show last N lines

## Monitoring Commands

### `vertex monitor`

Manage monitoring and alerting.

> **Note**: Monitor commands are currently in development.

#### `vertex monitor create`

Create a monitoring check.

```bash
vertex monitor create [options]
```

**Options:**
- `--url <url>` - URL to monitor
- `--interval <duration>` - Check interval
- `--timeout <duration>` - Request timeout

**Examples:**
```bash
# Create uptime check
vertex monitor create --url https://api.example.com --interval 1m

# Custom timeout
vertex monitor create --url https://slow-api.com --timeout 30s
```

#### `vertex monitor status`

View monitoring status.

```bash
vertex monitor status [check-id]
```

## Multi-Cloud Commands

### `vertex sync`

Manage multi-cloud resources and costs.

> **Note**: Sync commands are currently in development.

#### `vertex sync costs`

View cloud costs.

```bash
vertex sync costs [options]
```

**Options:**
- `--provider <aws,gcp,azure>` - Filter by provider
- `--period <this-month|last-month>` - Time period

**Examples:**
```bash
# All providers, current month
vertex sync costs

# Specific provider
vertex sync costs --provider aws

# Last month
vertex sync costs --period last-month
```

#### `vertex sync optimize`

Get optimization recommendations.

```bash
vertex sync optimize [options]
```

**Options:**
- `--auto-apply` - Automatically apply safe optimizations
- `--recommendations` - Show recommendations only

## Analytics Commands

### `vertex insight`

Manage analytics and insights.

> **Note**: Insight commands are currently in development.

#### `vertex insight dashboard`

Manage dashboards.

```bash
vertex insight dashboard [options]
```

**Options:**
- `--name <name>` - Dashboard name
- `--create` - Create new dashboard
- `--list` - List dashboards

#### `vertex insight metrics`

View and export metrics.

```bash
vertex insight metrics [options]
```

**Options:**
- `--export <csv|json>` - Export format
- `--period <duration>` - Time period

## Service Discovery Commands

### `vertex hub`

Manage service discovery and configuration.

> **Note**: Hub commands are currently in development.

#### `vertex hub register`

Register a service.

```bash
vertex hub register [options]
```

**Options:**
- `--name <name>` - Service name
- `--port <port>` - Service port
- `--health <url>` - Health check URL

#### `vertex hub discover`

Discover services.

```bash
vertex hub discover [options]
```

**Options:**
- `--service <name>` - Service name to discover

## Configuration Commands

### `vertex config`

Manage CLI configuration.

> **Note**: Config commands are currently in development.

#### `vertex config set`

Set configuration value.

```bash
vertex config set <key> <value>
```

#### `vertex config get`

Get configuration value.

```bash
vertex config get <key>
```

#### `vertex config list`

List all configuration.

```bash
vertex config list
```

## Configuration File

The Vertex CLI uses a configuration file located at `~/.vertex/config.yml`:

```yaml
# Default configuration
default:
  api_url: "http://localhost:8080"
  timeout: "30s"
  format: "table"

# Production configuration
production:
  api_url: "https://vertex.company.com"
  timeout: "60s"
  format: "json"

# Authentication tokens (managed automatically)
auth:
  access_token: "..."
  refresh_token: "..."
  expires_at: "2030-12-31T23:59:59Z"
```

## Environment Variables

The CLI respects these environment variables:

- `EDEN_API_URL` - API base URL
- `EDEN_CONFIG_PATH` - Configuration file path
- `EDEN_PROFILE` - Configuration profile
- `EDEN_TOKEN` - Authentication token
- `EDEN_TIMEOUT` - Request timeout

## Exit Codes

The Vertex CLI uses standard exit codes:

- `0` - Success
- `1` - General error
- `2` - Authentication error
- `3` - Permission denied
- `4` - Not found
- `5` - Network error

## Examples

### Complete Workflow Example

```bash
# 1. Login
vertex auth login --email developer@company.com

# 2. Store secrets
vertex vault set DATABASE_URL "postgresql://localhost/myapp"
vertex vault set API_KEY "sk-1234567890"

# 3. Create and run workflow
vertex flow create deployment --template ci-cd
vertex flow run deployment --env staging --wait

# 4. Monitor the application
vertex monitor create --url https://staging.myapp.com

# 5. Check costs
vertex sync costs --this-month
```

### Batch Operations

```bash
# Store multiple secrets
vertex vault set DB_HOST "localhost"
vertex vault set DB_PORT "5432"
vertex vault set DB_NAME "myapp"

# List and export
vertex vault list --format json > secrets.json
```

---

**Note**: Many CLI commands are currently in development and return "to be implemented" messages. Check the [Project Status](../development/project-status.md) for current implementation status.