# Agent Instructions

This file contains instructions for AI agents working on the Oathkeeper project.

## Documentation Updates (MANDATORY)

**ALWAYS update documentation when making code changes.** This is a critical requirement.

### When to Update

Update documentation in these scenarios:

1. **Adding new files/classes**: Add to PROGRESS.md deliverables and project structure
2. **Changing functionality**: Update TECHNICAL_SPEC.md with new implementation details
3. **Adding error handling**: Document user-facing errors and validation logic
4. **Creating new utilities**: Add to appropriate documentation sections
5. **Changing build/config**: Update relevant setup instructions

### Files to Update

| Change Type | Files to Update |
|------------|----------------|
| New Kotlin/Java file | PROGRESS.md (deliverables, structure, file list) |
| New feature/utility | TECHNICAL_SPEC.md (add class documentation) |
| Error handling | TECHNICAL_SPEC.md, PROGRESS.md (notes) |
| Model/tool changes | tools/README.md |
| UI changes | Relevant documentation sections |
| Permission changes | TECHNICAL_SPEC.md (AndroidManifest section) |

### Documentation Structure

- **PROGRESS.md**: Current implementation status, completed features, project structure
- **TECHNICAL_SPEC.md**: Detailed technical implementation, class documentation, code examples
- **tools/README.md**: Setup instructions for developers, model conversion
- **ROADMAP.md**: Future phases and planned features (read-only, don't update)
- **ARCHITECTURE.md**: System design (read-only unless architecture changes)

### Example Updates

**Adding a new utility class:**
```
PROGRESS.md:
- Add to "New Files Created" list
- Add to project structure tree
- Add to deliverables table if it's a key feature

TECHNICAL_SPEC.md:
- Add new section documenting the class
- Include code example showing usage
- Add to "Utility Classes" list
```

**Adding validation/error handling:**
```
PROGRESS.md:
- Note the validation in deliverables
- Add to "Key Implementation Details" if significant

TECHNICAL_SPEC.md:
- Document the validation logic
- Describe user-facing error messages
- Note any fatal errors that block app startup
```

## Project Context

- **Language**: Kotlin (Android)
- **Architecture**: MVVM pattern with Service layer
- **ML**: TensorFlow Lite for on-device inference
- **Database**: SQLCipher (encrypted SQLite)
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)

## Critical Requirements

1. All processing must be on-device (no network calls)
2. All data must be encrypted at rest
3. AccessibilityService requires Android 11+ for screenshots
4. Model file `nsfw_mobilenet_v2.tflite` is required in assets/
5. Always request permissions appropriately

## Testing Reminders

- Test on Android 11+ (AccessibilityService screenshot API requires API 30)
- Run lint/typecheck if available (ask user for command if unknown)
- Verify model file exists before testing ML features
- Check permission flows on fresh installs
