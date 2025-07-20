# Spectra IntelliJ AI Plugin

IntelliJ IDEA plugin for Jira integration with AI capabilities.

## Features

- **Jira Issue Management**: Create and manage Jira issues directly from IntelliJ IDEA
- **Sprint Selection**: Browse and select sprints from your Jira board
- **Task Selection**: View and select tasks from active sprints
- **Tool Window**: Dedicated tool window for quick access to Jira functionality

## Requirements

- IntelliJ IDEA Ultimate 2024.1.1 or later
- Java 17 or later
- Jira Cloud or Server instance with API access

## Installation

1. Download the plugin from the releases page
2. Open IntelliJ IDEA
3. Go to `File > Settings > Plugins`
4. Click the gear icon and select "Install Plugin from Disk..."
5. Select the downloaded plugin file and restart IntelliJ IDEA

## Configuration

1. Open `File > Settings > Tools > Spectra Jira Settings`
2. Configure the following settings:
   - **Jira URL**: Your Jira instance URL (e.g., https://your-domain.atlassian.net)
   - **Username**: Your Jira username/email
   - **API Token**: Your Jira API token (create one in Jira Account Settings)
   - **Default Board ID**: The ID of your default Jira board
   - **Default Project Key**: Your default project key

## Usage

### Creating Issues
- Use the menu: `Tools > Spectra Jira > Create Issue`
- Or use the tool window: `View > Tool Windows > Spectra Jira`

### Selecting Sprints
- Use the menu: `Tools > Spectra Jira > Select Sprint`
- Or use the tool window button "Select Sprint"

### Selecting Tasks
- Use the menu: `Tools > Spectra Jira > Select Task`
- Or use the tool window button "Select Task"

## Development

### Building the Plugin

```bash
./mvnw clean compile
```

### Running in Development Mode

```bash
./mvnw org.jetbrains.intellij.plugins:intellij-platform-plugin:runIde
```

### Testing

```bash
./mvnw test
```

### Building Plugin Distribution

```bash
./mvnw clean package
```

## License

This project is licensed under the Apache License 2.0.

## 참고
- https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issues/#api-rest-api-3-issue-post
- https://docs.atlassian.com/jira-software/REST/7.0.4/#agile/1.0/issue-rankIssues