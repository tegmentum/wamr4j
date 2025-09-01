# Rollback and Hotfix Procedures

This document outlines emergency procedures for handling problematic releases, including rollback strategies, hotfix creation, and crisis management for WAMR4J.

## Table of Contents

- [Overview](#overview)
- [Issue Classification](#issue-classification)
- [Rollback Procedures](#rollback-procedures)
- [Hotfix Procedures](#hotfix-procedures)
- [Emergency Scripts](#emergency-scripts)
- [Communication Protocols](#communication-protocols)
- [Post-Incident Procedures](#post-incident-procedures)

## Overview

WAMR4J follows a structured approach to handling post-release issues:

1. **Assessment**: Classify the issue severity and impact
2. **Immediate Response**: Implement containment measures
3. **Resolution**: Apply appropriate rollback or hotfix procedures
4. **Communication**: Notify affected users and stakeholders
5. **Follow-up**: Post-incident analysis and prevention measures

## Issue Classification

### Severity Levels

#### Critical (P0) - Immediate Response Required
- **Security vulnerabilities** with known exploits
- **Data corruption** or loss scenarios
- **Complete system failures** preventing library use
- **Legal or compliance** violations

**Response Time**: Within 2 hours
**Resolution Time**: Within 24 hours

#### High (P1) - Urgent Response
- **Performance regressions** > 50% slower than previous version
- **API breaking changes** not documented in major version
- **Memory leaks** or resource exhaustion
- **Compatibility issues** with major platforms

**Response Time**: Within 8 hours
**Resolution Time**: Within 72 hours

#### Medium (P2) - Standard Response
- **Non-critical bugs** affecting some use cases
- **Minor performance issues** < 20% regression
- **Documentation errors** or omissions
- **Build or installation issues** on specific platforms

**Response Time**: Within 24 hours
**Resolution Time**: Within 1 week

#### Low (P3) - Planned Response
- **Enhancement requests** from the community
- **Code quality improvements** without functional impact
- **Minor documentation** updates
- **Non-urgent dependency** updates

**Response Time**: Within 1 week
**Resolution Time**: Next scheduled release

## Rollback Procedures

### Important Limitation

**Maven Central does not support artifact deletion or modification**. Once an artifact is published to Maven Central, it cannot be removed or changed. Therefore, "rollback" means:

1. Publishing a new version that reverts changes
2. Marking the problematic version as deprecated
3. Providing clear migration guidance

### Virtual Rollback Process

#### Step 1: Immediate Assessment

```bash
# Clone the repository and checkout the problematic release tag
git clone https://github.com/yourusername/wamr4j.git
cd wamr4j
git checkout v1.2.0  # problematic version

# Create emergency branch
git checkout -b emergency/rollback-v1.2.0

# Identify the last known good version
git log --oneline --decorate | grep "v1\."
```

#### Step 2: Create Rollback Version

```bash
# Get the last known good version
LAST_GOOD_VERSION="1.1.0"
ROLLBACK_VERSION="1.2.1"  # Next patch version

# Reset to last good state
git checkout "v${LAST_GOOD_VERSION}" -- .

# Update version to rollback version
./scripts/release/version-bump.sh "$ROLLBACK_VERSION"

# Create rollback commit
git add .
git commit -m "emergency: rollback to v${LAST_GOOD_VERSION} functionality

This rollback release reverts problematic changes introduced in v1.2.0.
Users should upgrade immediately from v1.2.0 to v${ROLLBACK_VERSION}.

Reverted changes:
- [List specific changes being reverted]

See: https://github.com/yourusername/wamr4j/issues/XXX"
```

#### Step 3: Emergency Release

```bash
# Create release tag
git tag -a "v${ROLLBACK_VERSION}" -m "Emergency rollback release ${ROLLBACK_VERSION}

This release reverts problematic changes from v1.2.0.
All users of v1.2.0 should upgrade immediately.

Security/Critical Issues:
- [List critical issues being addressed]

This is a rollback to v${LAST_GOOD_VERSION} functionality with version ${ROLLBACK_VERSION}."

# Push emergency branch and tag
git push origin emergency/rollback-v1.2.0
git push origin "v${ROLLBACK_VERSION}"
```

The CI/CD pipeline will automatically handle building and publishing the rollback release.

## Hotfix Procedures

For issues that can be fixed with minimal changes, create a hotfix release instead of a full rollback.

### Hotfix Creation Process

#### Step 1: Create Hotfix Branch

```bash
# Start from the problematic release
git checkout v1.2.0
git checkout -b hotfix/v1.2.1-critical-fix

# Or use the hotfix script
./scripts/release/create-hotfix.sh 1.2.0 1.2.1 "critical-security-fix"
```

#### Step 2: Apply Minimal Fixes

```bash
# Apply only the minimal necessary changes
# Focus on the specific issue, avoid scope creep
git add [modified files]
git commit -m "hotfix: fix critical security vulnerability in native loader

- Fix buffer overflow in native library path validation
- Add input sanitization for WebAssembly module names
- Validate file paths before native library loading

Fixes: CVE-2024-XXXX
Security-Level: Critical
Affects: All versions >= 1.0.0"
```

#### Step 3: Accelerated Testing

```bash
# Run focused tests related to the fix
./mvnw test -Dtest="*Security*,*Native*" -B

# Run minimal cross-platform validation
./mvnw package -P native-all-platforms -DskipTests -B

# Static security analysis
./mvnw spotbugs:check -B
```

#### Step 4: Emergency Release

```bash
# Update version
./scripts/release/version-bump.sh 1.2.1

# Create hotfix tag
git tag -a v1.2.1 -m "Hotfix release 1.2.1

CRITICAL SECURITY UPDATE - Upgrade Immediately

This hotfix addresses a critical security vulnerability:
- CVE-2024-XXXX: Buffer overflow in native library loading

Affected Versions: All versions >= 1.0.0
Fixed In: 1.2.1

All users must upgrade immediately to prevent potential security exploits."

# Push hotfix
git push origin hotfix/v1.2.1-critical-fix
git push origin v1.2.1
```

## Emergency Scripts

### Create Hotfix Script

Create a hotfix automation script:

```bash
# scripts/release/create-hotfix.sh
#!/bin/bash
set -euo pipefail

BASE_VERSION="$1"
HOTFIX_VERSION="$2"
DESCRIPTION="$3"

echo "Creating hotfix $HOTFIX_VERSION from $BASE_VERSION for: $DESCRIPTION"

# Checkout base version
git checkout "v$BASE_VERSION"
git checkout -b "hotfix/v$HOTFIX_VERSION-$DESCRIPTION"

echo "Hotfix branch created. Apply your fixes and then run:"
echo "  git add ."
echo "  git commit -m 'hotfix: $DESCRIPTION'"
echo "  ./scripts/release/version-bump.sh $HOTFIX_VERSION"
echo "  git tag -a v$HOTFIX_VERSION -m 'Hotfix $HOTFIX_VERSION: $DESCRIPTION'"
echo "  git push origin hotfix/v$HOTFIX_VERSION-$DESCRIPTION"
echo "  git push origin v$HOTFIX_VERSION"
```

### Emergency Release Validation

```bash
# scripts/release/emergency-validation.sh
#!/bin/bash
set -euo pipefail

VERSION="$1"
echo "Running emergency validation for version $VERSION"

# Critical path testing only
./mvnw test -Dtest="*Integration*,*Native*,*Security*" -B -q

# Security scan
./mvnw spotbugs:check -B -q

# Basic build test
./mvnw package -DskipTests -B -q

echo "Emergency validation completed for $VERSION"
```

## Communication Protocols

### Immediate Communication (Critical Issues)

#### 1. GitHub Security Advisory
For security issues, create a GitHub Security Advisory:

1. Go to repository Settings → Security → Advisories
2. Create draft advisory with CVSS score
3. Include affected versions and mitigation steps
4. Coordinate with security researchers if applicable

#### 2. Issue Tracker Alert
```markdown
# 🚨 CRITICAL SECURITY UPDATE REQUIRED - v1.2.1 🚨

**Affected Versions**: All versions >= 1.0.0
**Fixed In**: v1.2.1
**Severity**: Critical (CVSS 9.8)

## Issue
A critical security vulnerability has been discovered in WAMR4J's native library loading mechanism that could allow arbitrary code execution.

## Immediate Action Required
**All users must upgrade to v1.2.1 immediately.**

## Upgrade Instructions
```xml
<dependency>
    <groupId>ai.tegmentum.wamr4j</groupId>
    <artifactId>wamr4j</artifactId>
    <version>1.2.1</version>
</dependency>
```

## Timeline
- **Discovered**: [Date/Time]
- **Fixed**: [Date/Time]  
- **Released**: [Date/Time]
- **CVE Assigned**: CVE-2024-XXXX
```

#### 3. README Badge Update
Update README.md with security notice:

```markdown
> ⚠️ **SECURITY ALERT**: Critical vulnerability fixed in v1.2.1. Upgrade immediately. See [Security Advisory](link).
```

### Standard Communication (Non-Critical Issues)

#### Release Notes Template
```markdown
## Version 1.2.1 - Bug Fix Release

### Fixed
- Fixed memory leak in WebAssembly module cleanup
- Resolved compatibility issue with ARM64 macOS
- Corrected documentation errors in Panama API examples

### Migration
No breaking changes. Simple version update required.

### Upgrade
Update your dependency version to 1.2.1:
[Include dependency examples]
```

## Post-Incident Procedures

### 1. Incident Report

Create a detailed incident report within 48 hours:

```markdown
# Incident Report: [Title]

## Summary
Brief description of what happened and impact.

## Timeline
- **Discovered**: When and how the issue was discovered
- **Response Started**: When emergency procedures began  
- **Fix Deployed**: When the fix was released
- **Resolution**: When the issue was fully resolved

## Root Cause
Technical analysis of what caused the issue.

## Impact Assessment
- Affected users/systems
- Severity and scope
- Duration of impact

## Response Actions
- Immediate containment measures
- Fix development and testing
- Release and communication

## Lessons Learned
- What went well
- What could be improved
- Process improvements needed

## Action Items
- [ ] Process improvements to implement
- [ ] Additional tooling or automation needed
- [ ] Documentation updates required
- [ ] Team training or knowledge sharing
```

### 2. Process Improvements

After each incident, evaluate and improve:

- **Detection**: How can we catch similar issues earlier?
- **Response**: How can we respond faster?
- **Resolution**: How can we fix issues more effectively?
- **Prevention**: How can we prevent similar issues?

### 3. Documentation Updates

Update relevant documentation:
- [ ] Known issues list
- [ ] Troubleshooting guides  
- [ ] Release procedures
- [ ] Security guidelines

## Testing Emergency Procedures

### Simulation Exercises

Regularly test emergency procedures:

1. **Quarterly Drills**: Practice hotfix creation and release
2. **Annual Exercises**: Full rollback simulation
3. **Team Training**: Ensure all maintainers understand procedures

### Validation Checklist

Before implementing emergency procedures:

- [ ] Issue severity correctly classified
- [ ] Appropriate response procedure selected
- [ ] Key stakeholders notified
- [ ] Testing completed (appropriate to urgency)
- [ ] Communication plan ready
- [ ] Rollback plan prepared (if needed)

## Emergency Contacts

### Internal Team
- **Release Manager**: [Contact info]
- **Security Team**: [Contact info]
- **Technical Lead**: [Contact info]

### External
- **Sonatype OSSRH Support**: [Contact info]
- **GitHub Security Team**: [Contact info] (for critical security issues)
- **CVE Numbering Authority**: [Contact info]

---

**Remember**: In emergency situations, prioritize user safety and security over process perfection. Document decisions made under pressure and review them during post-incident analysis.