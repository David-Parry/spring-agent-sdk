# Contributing to Agent SDK

Thank you for your interest in contributing to Agent SDK! This document provides guidelines for contributing to this Spring Boot AI agent orchestration platform.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Understanding the Project Structure](#understanding-the-project-structure)
- [Types of Contributions](#types-of-contributions)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Contributing to internal-core](#contributing-to-internal-core)
- [Testing Guidelines](#testing-guidelines)
- [Code Style and Standards](#code-style-and-standards)
- [Pull Request Process](#pull-request-process)
- [Reporting Issues](#reporting-issues)
- [Community and Support](#community-and-support)

---

## Code of Conduct

We are committed to providing a welcoming and inclusive environment for all contributors. Please be respectful, constructive, and professional in all interactions.

### Our Standards

- **Be Respectful**: Treat everyone with respect and consideration
- **Be Constructive**: Provide helpful feedback and suggestions
- **Be Collaborative**: Work together to improve the project
- **Be Patient**: Remember that everyone has different skill levels and backgrounds

---

## Understanding the Project Structure

Agent SDK is organized into two distinct modules with different purposes:

### `app` Module - Customer Implementation Layer

**Location:** `/app/src/main/java/ai/qodo/command/app/`

**Purpose:** This module is where **customers of the Agent API use the SDK** to build their own integrations and workflows.

**Contains:**
- **Controllers** (`controllers/`) - Webhook endpoints for external services (Snyk, Jira, GitHub, etc.)
- **Handlers** (`handlers/`) - Post-agent completion logic and workflow orchestration
- **Configuration** (`config/`) - Customer-specific Spring configurations
- **DTOs** (`dto/`) - Data transfer objects for external service payloads

**Example Use Cases:**
- Adding a new webhook endpoint for GitHub, GitLab, or custom services
- Creating handlers to process AI agent results
- Implementing custom business logic for specific workflows
- Integrating with enterprise tools and services

**Note:** The `app` module is designed to be modified by customers. It serves as an **example implementation** showing how to use the Agent SDK framework.

### `internal-core` Module - Framework Layer

**Location:** `/internal-core/src/main/java/ai/qodo/command/internal/`

**Purpose:** This module contains the **core framework code** that powers the Agent SDK. Contributions here improve the framework for all users.

**Contains:**
- **Services** (`service/`) - Core messaging, WebSocket, agent orchestration
- **API Models** (`api/`) - Request/response data structures
- **MCP Integration** (`mcp/`) - Model Context Protocol client management
- **Metrics** (`metrics/`) - Prometheus metrics for monitoring
- **Actuators** (`actuator/`) - Custom health indicators
- **Configuration** (`config/`) - Core Spring Boot auto-configuration
- **Transformers** (`transformer/`) - Data transformation utilities
- **Controllers** (`controller/`) - Internal API endpoints

**⚠️ Important:** Changes to `internal-core` affect all users of the SDK. Contributions here require careful review and testing.

---

## Types of Contributions

We welcome various types of contributions:

### 1. Framework Enhancements (internal-core)

Improvements to the core framework that benefit all users:

- **New Features**: Add capabilities to the core framework (e.g., new messaging providers, enhanced metrics)
- **Performance Improvements**: Optimize core services, reduce latency, improve throughput
- **Bug Fixes**: Fix issues in core services, messaging, or MCP integration
- **Documentation**: Improve inline documentation, JavaDocs, and architecture guides
- **Testing**: Add unit tests, integration tests, or improve test coverage

**Examples:**
- Adding support for RabbitMQ or Kafka as messaging providers
- Implementing circuit breakers for MCP server connections
- Adding new Prometheus metrics for better observability
- Improving WebSocket connection resilience
- Enhancing agent session management

### 2. Example Implementations (app)

Contributions that demonstrate how to use the SDK:

- **Example Controllers**: Show how to integrate with popular services
- **Example Handlers**: Demonstrate common post-processing patterns
- **Example Agents**: Provide ready-to-use agent configurations
- **Integration Guides**: Document how to integrate with specific tools

**Examples:**
- GitHub webhook controller with signature validation
- Slack notification handler
- GitLab CI/CD integration example
- PagerDuty incident response workflow

### 3. Documentation

- **README Updates**: Keep the main README current and comprehensive
- **Architecture Documentation**: Explain design decisions and patterns
- **Tutorial Content**: Create step-by-step guides for common tasks
- **API Documentation**: Document public APIs and interfaces

### 4. Testing and Quality Assurance

- **Test Coverage**: Add missing tests for core functionality
- **Integration Tests**: Test end-to-end workflows
- **Performance Tests**: Benchmark critical paths
- **Security Testing**: Identify and fix security vulnerabilities

---

## Getting Started

### Prerequisites

Before contributing, ensure you have:

- **Java 21 or higher** installed
- **Gradle 8.x** (included via wrapper)
- **Docker and Docker Compose** (for integration testing)
- **Git** for version control
- **IDE** (IntelliJ IDEA, Eclipse, or VS Code recommended)

### Setting Up Your Development Environment

1. **Fork the Repository**

   ```bash
   # Fork on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/command-sdk.git
   cd command-sdk
   ```

2. **Add Upstream Remote**

   ```bash
   git remote add upstream https://github.com/davidparry/command-sdk.git
   ```

3. **Build the Project**

   ```bash
   ./gradlew clean build
   ```

4. **Run Tests**

   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ```

5. **Start Development Environment**

   ```bash
   # Start ActiveMQ and other dependencies
   docker-compose -f docker/docker-compose.yml up -d activemq
   
   # Run the application
   ./gradlew bootRun
   ```

6. **Verify Setup**

   ```bash
   # Check health endpoint
   curl http://localhost:8080/actuator/health
   
   # Check metrics
   curl http://localhost:8080/actuator/prometheus
   ```

---

## Development Workflow

### 1. Create a Feature Branch

```bash
# Update your fork
git fetch upstream
git checkout main
git merge upstream/main

# Create a feature branch
git checkout -b feature/your-feature-name
```

**Branch Naming Conventions:**
- `feature/` - New features (e.g., `feature/kafka-support`)
- `fix/` - Bug fixes (e.g., `fix/websocket-reconnect`)
- `docs/` - Documentation updates (e.g., `docs/contributing-guide`)
- `test/` - Test additions (e.g., `test/mcp-integration`)
- `refactor/` - Code refactoring (e.g., `refactor/message-publisher`)

### 2. Make Your Changes

Follow these guidelines:

- **Keep changes focused**: One feature or fix per branch
- **Write clean code**: Follow the code style guidelines below
- **Add tests**: Include unit and integration tests
- **Update documentation**: Keep docs in sync with code changes
- **Commit frequently**: Make small, logical commits

### 3. Write Good Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `chore`: Maintenance tasks

**Examples:**

```bash
feat(mcp): add circuit breaker for MCP server connections

Implement Resilience4j circuit breaker to handle MCP server failures
gracefully. This prevents cascading failures when MCP servers are
unavailable.

Closes #123
```

```bash
fix(websocket): handle reconnection after network interruption

Add exponential backoff retry logic for WebSocket reconnections.
Previously, the client would fail permanently after a network
interruption.

Fixes #456
```

### 4. Test Your Changes

```bash
# Run unit tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Run specific test class
./gradlew test --tests "WebSocketServiceTest"

# Check test coverage
./gradlew jacocoTestReport
```

### 5. Update Documentation

- Update README.md if adding new features
- Add JavaDoc comments for public APIs
- Update inline comments for complex logic
- Create or update architecture diagrams if needed

### 6. Push and Create Pull Request

```bash
# Push to your fork
git push origin feature/your-feature-name

# Create pull request on GitHub
```

---

## Contributing to internal-core

Contributions to the `internal-core` module require special attention as they affect all users of the SDK.

### Guidelines for internal-core Contributions

#### 1. Maintain Backward Compatibility

- **Don't break existing APIs**: Avoid changing method signatures or removing public methods
- **Deprecate before removing**: Mark old APIs as `@Deprecated` and provide migration paths
- **Version carefully**: Follow semantic versioning (MAJOR.MINOR.PATCH)

**Example:**

```java
// Good: Add new method, keep old one
@Deprecated(since = "2.0.0", forRemoval = true)
public void publishMessage(String topic, String message) {
    publishMessage(topic, message, Collections.emptyMap());
}

public void publishMessage(String topic, String message, Map<String, Object> headers) {
    // New implementation with headers support
}
```

#### 2. Design for Extensibility

- **Use interfaces**: Define contracts that can be implemented differently
- **Provide extension points**: Allow customers to customize behavior
- **Follow SOLID principles**: Single responsibility, open/closed, etc.

**Example:**

```java
// Good: Interface allows multiple implementations
public interface MessagePublisher {
    void publish(String topic, String message);
}

@Service
public class ActiveMQMessagePublisher implements MessagePublisher {
    // ActiveMQ-specific implementation
}

@Service
public class KafkaMessagePublisher implements MessagePublisher {
    // Kafka-specific implementation
}
```

#### 3. Add Comprehensive Tests

All `internal-core` changes must include:

- **Unit tests**: Test individual components in isolation
- **Integration tests**: Test interactions between components
- **Edge case tests**: Test error conditions and boundary cases

**Test Coverage Requirements:**
- Minimum 80% line coverage for new code
- 100% coverage for critical paths (messaging, agent orchestration)

**Example:**

```java
@SpringBootTest
class MessagePublisherTest {

    @Autowired
    private MessagePublisher messagePublisher;

    @Test
    void shouldPublishMessageSuccessfully() {
        // Arrange
        String topic = "test.topic";
        String message = "{\"type\":\"test\"}";

        // Act & Assert
        assertDoesNotThrow(() -> messagePublisher.publish(topic, message));
    }

    @Test
    void shouldHandlePublishFailureGracefully() {
        // Test error handling
    }
}
```

#### 4. Document Public APIs

All public classes, methods, and interfaces must have JavaDoc:

```java
/**
 * Service for publishing messages to the message broker.
 * 
 * <p>This service provides a unified interface for publishing messages
 * to various messaging providers (ActiveMQ, Kafka, etc.). It handles
 * connection management, error handling, and retry logic.
 * 
 * <p>Example usage:
 * <pre>{@code
 * messagePublisher.publish("event.topic", "{\"type\":\"webhook\"}");
 * }</pre>
 * 
 * @author Command SDK Team
 * @since 1.0.0
 * @see MessagePublisherConfig
 */
@Service
public class MessagePublisher {

    /**
     * Publishes a message to the specified topic.
     * 
     * @param topic the destination topic name
     * @param message the message content (typically JSON)
     * @throws MessagingException if the message cannot be published
     */
    public void publish(String topic, String message) {
        // Implementation
    }
}
```

#### 5. Consider Performance Impact

- **Profile your changes**: Use JProfiler, VisualVM, or similar tools
- **Benchmark critical paths**: Measure latency and throughput
- **Avoid blocking operations**: Use async/reactive patterns where appropriate
- **Optimize resource usage**: Monitor memory, CPU, and network usage

**Example:**

```java
// Good: Use virtual threads for concurrent operations
@Service
public class AgentOrchestrator {

    public CompletableFuture<TaskResponse> executeAgent(AgentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // Agent execution logic
        }, virtualThreadExecutor);
    }
}
```

#### 6. Add Metrics and Observability

New features should include:

- **Prometheus metrics**: Track key operations and performance
- **Health indicators**: Report component health status
- **Structured logging**: Use SLF4J with meaningful log levels

**Example:**

```java
@Service
public class McpServerManager {

    private final MeterRegistry meterRegistry;
    private final Counter serverInitCounter;

    public McpServerManager(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.serverInitCounter = Counter.builder("qodo.mcp.server.init")
            .description("Number of MCP server initializations")
            .tag("status", "success")
            .register(meterRegistry);
    }

    public void initializeServer(String serverName) {
        try {
            // Initialization logic
            serverInitCounter.increment();
            logger.info("Successfully initialized MCP server: {}", serverName);
        } catch (Exception e) {
            Counter.builder("qodo.mcp.server.init")
                .tag("status", "failure")
                .register(meterRegistry)
                .increment();
            logger.error("Failed to initialize MCP server: {}", serverName, e);
            throw e;
        }
    }
}
```

#### 7. Security Considerations

- **Validate all inputs**: Never trust external data
- **Sanitize outputs**: Prevent injection attacks
- **Use secure defaults**: Fail closed, not open
- **Handle secrets properly**: Never log sensitive data

**Example:**

```java
// Good: Validate webhook signatures
public boolean validateWebhookSignature(String payload, String signature, String secret) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expected = "sha256=" + bytesToHex(hash);
        
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        );
    } catch (Exception e) {
        logger.error("Signature validation failed", e);
        return false;
    }
}
```

### Areas Where Contributions Are Especially Welcome

#### High Priority

1. **Additional Messaging Providers**
   - Kafka support
   - RabbitMQ support
   - AWS SQS/SNS support
   - Google Cloud Pub/Sub support

2. **Enhanced Resilience**
   - Circuit breakers for MCP servers
   - Retry policies with exponential backoff
   - Bulkhead patterns for resource isolation
   - Rate limiting for external APIs

3. **Improved Observability**
   - Distributed tracing (OpenTelemetry)
   - Enhanced metrics (latency percentiles, error rates)
   - Structured logging with correlation IDs
   - Custom dashboards for Grafana

4. **Performance Optimizations**
   - Connection pooling improvements
   - Caching strategies for MCP tools
   - Batch processing for messages
   - Memory usage optimizations

#### Medium Priority

5. **Security Enhancements**
   - OAuth2/OIDC integration
   - API key management
   - Webhook signature validation improvements
   - Secrets management integration (Vault, AWS Secrets Manager)

6. **Developer Experience**
   - Better error messages
   - Improved logging
   - Development mode with hot reload
   - CLI tools for common tasks

7. **Testing Infrastructure**
   - Test containers for integration tests
   - Performance benchmarking suite
   - Chaos engineering tests
   - Contract testing for APIs

---

## Testing Guidelines

### Unit Tests

- **Location**: `src/test/java/` (same package as source)
- **Naming**: `ClassNameTest.java`
- **Framework**: JUnit 5
- **Mocking**: Mockito

**Example:**

```java
@ExtendWith(MockitoExtension.class)
class MessagePublisherTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @InjectMocks
    private ActiveMQMessagePublisher messagePublisher;

    @Test
    void shouldPublishMessageToTopic() {
        // Arrange
        String topic = "test.topic";
        String message = "{\"type\":\"test\"}";

        // Act
        messagePublisher.publish(topic, message);

        // Assert
        verify(jmsTemplate).convertAndSend(eq(topic), eq(message));
    }
}
```

### Integration Tests

- **Location**: `src/integrationTest/java/`
- **Naming**: `ClassNameIntegrationTest.java`
- **Framework**: Spring Boot Test
- **Containers**: Testcontainers for external dependencies

**Example:**

```java
@SpringBootTest
@Testcontainers
class MessagePublisherIntegrationTest {

    @Container
    static GenericContainer<?> activemq = new GenericContainer<>("apache/activemq-artemis:latest")
        .withExposedPorts(61616);

    @Autowired
    private MessagePublisher messagePublisher;

    @Test
    void shouldPublishAndConsumeMessage() {
        // End-to-end test
    }
}
```

### Test Coverage

Check coverage with:

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

**Coverage Requirements:**
- **internal-core**: Minimum 80% line coverage
- **app**: Minimum 60% line coverage (examples)
- **Critical paths**: 100% coverage (messaging, agent orchestration)

---

## Code Style and Standards

### Java Code Style

We follow standard Java conventions with some specific guidelines:

#### Formatting

- **Indentation**: 4 spaces (no tabs)
- **Line length**: Maximum 120 characters
- **Braces**: Always use braces, even for single-line blocks
- **Imports**: No wildcard imports, organize by package

**Example:**

```java
// Good
if (condition) {
    doSomething();
}

// Bad
if (condition) doSomething();
```

#### Naming Conventions

- **Classes**: PascalCase (e.g., `MessagePublisher`)
- **Methods**: camelCase (e.g., `publishMessage`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_ATTEMPTS`)
- **Packages**: lowercase (e.g., `ai.qodo.command.internal.service`)

#### Code Organization

```java
public class ExampleService {

    // 1. Constants
    private static final String DEFAULT_TOPIC = "default.topic";

    // 2. Static fields
    private static final Logger logger = LoggerFactory.getLogger(ExampleService.class);

    // 3. Instance fields
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    // 4. Constructor
    public ExampleService(MessagePublisher messagePublisher, ObjectMapper objectMapper) {
        this.messagePublisher = messagePublisher;
        this.objectMapper = objectMapper;
    }

    // 5. Public methods
    public void processMessage(String message) {
        // Implementation
    }

    // 6. Private methods
    private void validateMessage(String message) {
        // Implementation
    }

    // 7. Inner classes (if needed)
    private static class MessageValidator {
        // Implementation
    }
}
```

#### Best Practices

1. **Use dependency injection**: Constructor injection preferred
2. **Favor immutability**: Use `final` fields and immutable collections
3. **Handle exceptions properly**: Don't swallow exceptions
4. **Use Optional**: For nullable return values
5. **Avoid null**: Use Optional, empty collections, or default values

**Example:**

```java
// Good: Constructor injection with final fields
@Service
public class AgentService {

    private final MessagePublisher messagePublisher;
    private final AgentRepository agentRepository;

    public AgentService(MessagePublisher messagePublisher, 
                       AgentRepository agentRepository) {
        this.messagePublisher = messagePublisher;
        this.agentRepository = agentRepository;
    }

    public Optional<Agent> findAgent(String agentId) {
        return agentRepository.findById(agentId);
    }
}
```

### Spring Boot Conventions

- **Use @Autowired on constructors**: Or rely on implicit autowiring
- **Use @Value for configuration**: With default values
- **Use @ConfigurationProperties**: For complex configuration
- **Use @Slf4j**: For logging (if using Lombok)

**Example:**

```java
@Service
public class ConfigurableService {

    private final String apiUrl;
    private final int timeout;

    public ConfigurableService(
            @Value("${api.url:https://default.api.com}") String apiUrl,
            @Value("${api.timeout:30}") int timeout) {
        this.apiUrl = apiUrl;
        this.timeout = timeout;
    }
}
```

### Logging Guidelines

- **Use SLF4J**: Via `LoggerFactory.getLogger()`
- **Log levels**:
  - `ERROR`: Errors that need immediate attention
  - `WARN`: Potential issues or degraded functionality
  - `INFO`: Important business events
  - `DEBUG`: Detailed diagnostic information
  - `TRACE`: Very detailed diagnostic information

**Example:**

```java
private static final Logger logger = LoggerFactory.getLogger(MyService.class);

public void processRequest(String requestId) {
    logger.info("Processing request: {}", requestId);
    
    try {
        // Processing logic
        logger.debug("Request {} processed successfully", requestId);
    } catch (Exception e) {
        logger.error("Failed to process request: {}", requestId, e);
        throw e;
    }
}
```

**Important:**
- Never log sensitive data (passwords, tokens, PII)
- Use parameterized logging (not string concatenation)
- Include context (request IDs, session IDs)
- Log at appropriate levels

---

## Pull Request Process

### Before Submitting

1. **Ensure all tests pass**:
   ```bash
   ./gradlew clean build test integrationTest
   ```

2. **Check code style**:
   ```bash
   ./gradlew checkstyleMain checkstyleTest
   ```

3. **Update documentation**:
   - README.md (if adding features)
   - JavaDoc comments
   - CHANGELOG.md (if exists)

4. **Rebase on latest main**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

### Pull Request Template

When creating a PR, include:

```markdown
## Description
Brief description of the changes

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Changes Made
- List of specific changes
- Another change
- etc.

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] My code follows the code style of this project
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] I have updated the documentation accordingly
- [ ] All new and existing tests passed
- [ ] I have rebased on the latest main branch

## Related Issues
Closes #123
Fixes #456
```

### Review Process

1. **Automated checks**: CI/CD pipeline runs tests and checks
2. **Code review**: Maintainers review the code
3. **Feedback**: Address review comments
4. **Approval**: At least one maintainer approval required
5. **Merge**: Maintainer merges the PR

### Review Criteria

Reviewers will check:

- **Functionality**: Does it work as intended?
- **Tests**: Are there adequate tests?
- **Code quality**: Is the code clean and maintainable?
- **Documentation**: Is it properly documented?
- **Performance**: Does it impact performance?
- **Security**: Are there security concerns?
- **Backward compatibility**: Does it break existing functionality?

---

## Reporting Issues

### Before Reporting

1. **Search existing issues**: Check if it's already reported
2. **Verify it's a bug**: Ensure it's not a configuration issue
3. **Gather information**: Collect logs, stack traces, and reproduction steps

### Issue Template

```markdown
## Description
Clear description of the issue

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Expected Behavior
What should happen

## Actual Behavior
What actually happens

## Environment
- OS: [e.g., macOS 14.0]
- Java Version: [e.g., Java 21]
- Command SDK Version: [e.g., 1.0.0]
- Spring Boot Version: [e.g., 3.5.6]

## Logs
```
Paste relevant logs here
```

## Additional Context
Any other relevant information
```

### Issue Labels

- `bug`: Something isn't working
- `enhancement`: New feature or request
- `documentation`: Documentation improvements
- `good first issue`: Good for newcomers
- `help wanted`: Extra attention needed
- `question`: Further information requested
- `wontfix`: This will not be worked on

---

## Community and Support

### Getting Help

- **GitHub Discussions**: Ask questions and share ideas
- **GitHub Issues**: Report bugs and request features
- **Documentation**: Check README.md and inline docs
- **Examples**: Review the `app` module for examples

### Communication Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and discussions
- **Pull Requests**: Code contributions and reviews

### Recognition

Contributors will be:
- Listed in CONTRIBUTORS.md (if created)
- Mentioned in release notes
- Credited in commit history

---

## License

By contributing to Agent SDK, you agree that your contributions will be licensed under the same license as the project (GNU Affero General Public License v3.0 or later).

All source files in this project include the following copyright header:

```java
/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
```

For more details, see the [LICENSE.md](LICENSE.md) file in the root of this repository.

---

## Questions?

If you have questions about contributing, please:

1. Check this CONTRIBUTING.md guide
2. Review the README.md
3. Search existing GitHub issues
4. Open a new GitHub discussion

Thank you for contributing to Agent SDK! 🚀
