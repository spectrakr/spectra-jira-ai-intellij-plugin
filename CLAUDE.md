# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Spectra IntelliJ AI Plugin** that provides Jira integration for IntelliJ IDEA. The plugin enables developers to create Jira issues, select sprints, and manage tasks directly from their IDE without leaving the development environment.

## Development Commands

### Building the Plugin
```bash
# Build the plugin
./gradlew build

# Build without daemon (if needed)
./gradlew build --no-daemon
```

### Running in Development Mode
```bash
# Run IntelliJ IDEA with the plugin for testing
./gradlew runIde
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test jacocoTestReport
```

### Plugin Distribution
```bash
# Create plugin distribution zip
./gradlew buildPlugin

# Verify plugin structure
./gradlew verifyPlugin
```

**Note**: The README.md incorrectly mentions Maven commands (`./mvnw`), but this project uses Gradle. Always use `./gradlew` commands.

## Architecture Overview

The plugin follows a **layered architecture** with clear separation of concerns:

### Core Components
- **Service Layer**: `JiraService` handles all Jira REST API communications using OkHttp
- **Model Layer**: `JiraIssue` and `JiraSprint` represent Jira entities
- **UI Layer**: Actions, Dialogs, and Tool Window provide user interface
- **Settings Layer**: `JiraSettings` and `JiraConfigurable` manage configuration

### Component Flow
```
Actions/Tool Window → JiraService → Jira REST API
      ↓                    ↓
   Dialogs            JiraSettings
      ↓                    ↓
   Models            Configuration UI
```

## Key Technical Details

### IntelliJ Platform Integration
- **Plugin ID**: `com.spectra.intellij.ai`
- **Target IDE**: IntelliJ IDEA Ultimate 2023.3.6
- **Java Version**: 17
- **Platform Version**: Builds 233 to 242.*

### Dependencies
- **OkHttp 4.12.0**: HTTP client for Jira API calls
- **Gson 2.10.1**: JSON parsing
- **Apache Commons Lang3 3.12.0**: Utility functions
- **JUnit 5.10.0**: Testing framework
- **Mockito 5.5.0**: Mocking framework

### Threading Model
All Jira API calls use `CompletableFuture` for asynchronous execution to prevent IDE freezing. UI updates are performed on the EDT (Event Dispatch Thread).

### Authentication
Uses Jira Basic Authentication with username/API token. API tokens are stored securely using IntelliJ's persistent state component.

## Plugin Structure

### Main Package: `com.spectra.intellij.ai`

- **`actions/`**: IDE actions for menus and tool window
  - `CreateJiraIssueAction`: Opens issue creation dialog
  - `SelectSprintAction`: Shows sprint selection dialog
  - `SelectTaskAction`: Shows task selection from active sprint

- **`dialog/`**: UI dialogs extending `DialogWrapper`
  - `CreateIssueDialog`: Form for creating new Jira issues
  - `SprintSelectionDialog`: List of available sprints
  - `TaskSelectionDialog`: List of tasks in selected sprint

- **`model/`**: Data models for Jira entities
  - `JiraIssue`: Represents Jira issue with key, summary, description, etc.
  - `JiraSprint`: Represents Jira sprint with id, name, state, dates

- **`service/`**: Business logic layer
  - `JiraService`: Handles all Jira API interactions asynchronously

- **`settings/`**: Configuration management
  - `JiraSettings`: Persistent settings storage
  - `JiraConfigurable`: Settings UI panel

- **`toolwindow/`**: Tool window implementation
  - `JiraToolWindowFactory`: Creates the tool window
  - `JiraToolWindowContent`: Main UI with buttons and status display

## Configuration Files

### `plugin.xml`
- Defines actions in Tools menu and custom "Spectra Jira" group
- Registers tool window as "Spectra Jira" on the right side
- Configures settings under Tools section
- Declares dependencies on platform and Java modules

### `build.gradle`
- Uses IntelliJ Plugin Development plugin version 1.17.4
- Configures compatibility for IDE builds 233 to 242.*
- Sets up test framework with JUnit Platform
- Includes signing and publishing configuration for plugin distribution

## Development Guidelines

### Adding New Features
1. Create models in `model/` package if new data structures are needed
2. Implement business logic in `JiraService` with async methods
3. Create UI components in `dialog/` package extending `DialogWrapper`
4. Add actions in `actions/` package implementing `AnAction`
5. Register new actions in `plugin.xml`

### Error Handling
- Use `Messages.showErrorDialog()` for user-facing errors
- Log errors using IntelliJ's logging framework
- Provide meaningful error messages for network failures

### Testing
- Mock `JiraService` for UI component tests
- Use `CompletableFuture` test utilities for async operations
- Test both success and failure scenarios for API calls

### UI Guidelines
- Follow IntelliJ UI guidelines and use `DialogWrapper` for dialogs
- Use `Messages` class for notifications
- Ensure all long-running operations are asynchronous
- Update UI on EDT thread only