# Oathkeeper

A privacy-respecting accountability app for Android that helps users maintain their commitment to avoid NSFW content through local, on-device monitoring.

## Overview

Oathkeeper monitors screen content using machine learning to detect potentially inappropriate material. When such content is detected, the app captures a pixelated screenshot and logs the event locally. All processing happens on-device - no data is ever sent to external servers. Users maintain full control over their data and can manually review and share reports with accountability partners.

## Key Features

- **On-Device ML**: All content analysis happens locally using TensorFlow Lite models
- **Smart Pixelation**: Detected person regions are pixelated while preserving context
- **Privacy-First**: No network calls, no cloud storage, no external data transmission
- **Tamper Detection**: Logs attempts to disable or circumvent the app
- **Local Reports**: Interactive HTML reports with export capabilities
- **Encrypted Storage**: AES-256 encryption for all screenshots and logs

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed system design.

## Development Roadmap

See [ROADMAP.md](ROADMAP.md) for implementation phases and timeline.

## Technical Documentation

- [Technical Specification](docs/TECHNICAL_SPEC.md) - Detailed implementation details
- [ML Models](docs/ML_MODELS.md) - Information about the machine learning pipeline

## Distribution

**Initial**: Sideload APK

**Future**: F-Droid

## License

See [LICENSE](LICENSE) file for details.

## Disclaimer

This app requires extensive permissions including screen capture and accessibility services. It is intended for users who explicitly consent to this level of monitoring for personal accountability purposes. The app respects user privacy by keeping all data local and encrypted.
