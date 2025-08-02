# Changelog

All notable changes to the Spectra IntelliJ AI Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-02

### Added
- Initial release with basic Jira integration functionality
- Jira issue creation with sprint selection
- Dynamic issue type selection based on project configuration
- Automatic configuration persistence using IntelliJ's settings system
- Integration with Jira Cloud API v3
- Support for Atlassian Document Format (ADF) for issue descriptions
- Tool window for easy access to Jira functionality
- Menu integration under Tools menu
- Settings page for Jira connection configuration
- Error handling with user-friendly messages
- Automatic sprint assignment (with fallback if assignment fails)
- Custom plugin repository support for auto-updates
- GitHub Actions workflow for automated releases

### Technical Details
- Built for IntelliJ IDEA 2023.3+ (build 233+)
- Requires Java 17+
- Uses OkHttp for HTTP communication
- Uses Gson for JSON processing
- Implements PersistentStateComponent for settings storage

### Configuration
- Pre-configured Jira URL: `https://enomix.atlassian.net/`
- Supports API token authentication
- Configurable default board ID and project key

---

## Template for Future Releases

## [Unreleased]

### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security