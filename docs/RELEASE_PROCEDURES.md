# Release Procedures

This document outlines the complete release process for WAMR4J, including preparation, validation, publishing, and post-release procedures.

## Overview

WAMR4J uses an automated release process with multiple validation checkpoints to ensure high-quality releases. The process includes:

1. **Preparation**: Version updates, validation, and tagging
2. **Automated Publishing**: CI/CD pipeline handles artifact building and publishing
3. **Validation**: Post-release verification and documentation updates
4. **Communication**: Release announcements and documentation updates

## Prerequisites

Before starting a release, ensure you have:

- [ ] Access to the repository with push permissions
- [ ] GPG key configured for artifact signing (for maintainers)
- [ ] OSSRH/Sonatype account credentials (for Maven Central publishing)
- [ ] Clean working directory with all changes committed
- [ ] All required changes merged to the release branch
- [ ] Updated CHANGELOG.md with release notes

## Automated Release Process

### Quick Release (Recommended)

For most releases, use the automated preparation script:

```bash
# Prepare release (automatically increments next development version)
./scripts/release/prepare-release.sh 1.2.0

# Or specify next development version explicitly
./scripts/release/prepare-release.sh 1.2.0 1.3.0-SNAPSHOT

# Preview what would be done (dry run)
./scripts/release/prepare-release.sh 1.2.0 --dry-run

# Prepare and push changes automatically
./scripts/release/prepare-release.sh 1.2.0 --push
```

This script will:
1. Validate the working directory and current branch
2. Run tests and static analysis
3. Update version to release version
4. Create and commit changelog updates
5. Create annotated Git tag
6. Update to next development version
7. Optionally push changes and tags

### Manual Step-by-Step Release

If you prefer manual control or encounter issues with the automated script:

#### Step 1: Pre-Release Validation

```bash
# Ensure clean working directory
git status

# Run full test suite
./mvnw clean test -B

# Run static analysis
./mvnw checkstyle:check spotbugs:check -B

# Test cross-platform native library builds
./mvnw package -P native-all-platforms -DskipTests -B
```

#### Step 2: Version Management

```bash
# Update to release version
./scripts/release/version-bump.sh 1.2.0 --commit

# Verify version update
./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout
```

#### Step 3: Create Release Tag

```bash
# Create annotated tag
git tag -a v1.2.0 -m "Release 1.2.0"

# Verify tag
git tag -l -n1 v1.2.0
```

#### Step 4: Trigger Automated Publishing

```bash
# Push tag to trigger release workflow
git push origin v1.2.0
```

#### Step 5: Post-Release Version Update

```bash
# Update to next development version
./scripts/release/version-bump.sh 1.2.1-SNAPSHOT --commit

# Push development version
git push origin
```

## CI/CD Release Workflow

The release workflow (`.github/workflows/release.yml`) is automatically triggered when:

1. A tag matching `v*.*.*` is pushed to the repository
2. Manual workflow dispatch from GitHub Actions UI

### Workflow Stages

1. **Validation**
   - Version format validation
   - CHANGELOG.md verification
   - Branch and working directory checks

2. **Multi-Platform Build**
   - Builds native libraries for all supported platforms
   - Runs comprehensive test suites
   - Generates JAR artifacts with native libraries

3. **Security Validation**
   - GPG signing of all artifacts
   - Signature verification
   - JAR integrity checks
   - Artifact corruption detection

4. **GitHub Release**
   - Creates GitHub release with generated release notes
   - Uploads platform-specific native library archives
   - Uploads complete JAR artifacts

5. **Maven Central Publishing**
   - Deploys signed artifacts to OSSRH staging
   - Validates staging repository
   - Provides staging repository URL for manual release

## Maven Central Publishing

### Staging and Release Process

WAMR4J uses Sonatype OSSRH for Maven Central publishing with a semi-automated process:

1. **Automated Staging**: CI/CD pipeline stages artifacts automatically
2. **Manual Release**: Repository maintainers manually promote from staging to release

### Manual Release from Staging

After the CI/CD pipeline completes successfully:

1. Visit [Sonatype OSSRH](https://s01.oss.sonatype.org/)
2. Log in with OSSRH credentials
3. Navigate to "Staging Repositories"
4. Find the `aitagmentum-XXXX` staging repository
5. Select the repository and click "Close"
6. Wait for validation rules to complete
7. If validation passes, click "Release"
8. Artifacts will be synchronized to Maven Central within 2-4 hours

### Verification Commands

```bash
# Check staging repository status
./mvnw nexus-staging:rc-list -P release

# Close staging repository (if needed manually)
./mvnw nexus-staging:rc-close -P release -DstagingRepositoryId=aitagmentum-XXXX

# Release from staging (if needed manually)
./mvnw nexus-staging:rc-release -P release -DstagingRepositoryId=aitagmentum-XXXX
```

## Post-Release Procedures

### 1. Verify Maven Central Availability

Wait 2-4 hours after release, then verify:

```bash
# Check if artifacts are available
curl -I "https://repo1.maven.org/maven2/ai/tegmentum/wamr4j/wamr4j/1.2.0/wamr4j-1.2.0.pom"

# Or use Maven dependency check in a test project
mvn dependency:get -Dartifact=ai.tegmentum.wamr4j:wamr4j:1.2.0
```

### 2. Update Documentation

- [ ] Update README.md with new version numbers
- [ ] Update installation instructions
- [ ] Update compatibility matrix if needed
- [ ] Review and update API documentation

### 3. Release Communication

- [ ] Create GitHub release announcement
- [ ] Update project website (if applicable)
- [ ] Notify community through appropriate channels
- [ ] Update relevant issue trackers

### 4. Monitoring and Follow-up

- [ ] Monitor for issues in the first 24-48 hours
- [ ] Review download statistics
- [ ] Address any immediate bug reports
- [ ] Plan next development cycle

## Release Types

### Major Releases (X.0.0)

- Breaking API changes
- Significant new features
- Architecture changes
- Requires extensive testing and documentation updates

### Minor Releases (X.Y.0)

- New features with backward compatibility
- Performance improvements
- New platform support
- Comprehensive testing required

### Patch Releases (X.Y.Z)

- Bug fixes
- Security updates
- Minor improvements
- Expedited release process possible

### Snapshot Releases (X.Y.Z-SNAPSHOT)

- Development builds
- Continuous integration artifacts
- Not promoted to Maven Central release repositories

## Security Considerations

### GPG Signing

All release artifacts are signed with GPG keys:

- **Repository Key**: Used for CI/CD pipeline signing
- **Maintainer Keys**: Used for manual releases and critical updates
- **Key Rotation**: Annual key rotation and validation

### Secret Management

Required secrets for CI/CD:

- `GPG_PRIVATE_KEY`: Base64-encoded GPG private key
- `GPG_PASSPHRASE`: GPG key passphrase
- `OSSRH_USERNAME`: Sonatype OSSRH username
- `OSSRH_PASSWORD`: Sonatype OSSRH password

### Artifact Integrity

- All artifacts include SHA-256 and SHA-512 checksums
- GPG signatures for all primary artifacts
- JAR verification during build process
- Native library integrity validation

## Troubleshooting

### Common Issues

#### GPG Signing Failures
```bash
# Verify GPG key is properly configured
gpg --list-secret-keys

# Test GPG signing manually
echo "test" | gpg --clearsign
```

#### Maven Central Staging Issues
```bash
# List all staging repositories
./mvnw nexus-staging:rc-list -P release

# Drop failed staging repository
./mvnw nexus-staging:rc-drop -P release -DstagingRepositoryId=aitagmentum-XXXX
```

#### Native Library Build Failures
```bash
# Test native build on specific platform
cd wamr4j-native
cargo build --release --target x86_64-unknown-linux-gnu

# Check cross-compilation toolchain
rustup target list --installed
```

### Emergency Procedures

#### Release Rollback
If a critical issue is discovered immediately after release:

1. **Do NOT delete from Maven Central** (not possible and breaks reproducibility)
2. Create hotfix release with incremented version
3. Mark problematic version as deprecated in documentation
4. Communicate issue and mitigation to users

#### Hotfix Release Process
For critical security or stability issues:

1. Create hotfix branch from release tag
2. Apply minimal necessary fixes
3. Follow expedited release process
4. Skip non-essential validation for urgent issues
5. Communicate urgency and changes clearly

## Release Checklist

### Pre-Release
- [ ] All features complete and tested
- [ ] Documentation updated
- [ ] CHANGELOG.md updated with release notes
- [ ] Version numbers updated
- [ ] GPG keys and Maven credentials configured
- [ ] Clean working directory

### Release Execution
- [ ] Run automated preparation script or manual steps
- [ ] Verify release tag created
- [ ] Monitor CI/CD pipeline execution
- [ ] Verify artifact staging in OSSRH
- [ ] Manually release from staging to Maven Central

### Post-Release
- [ ] Verify Maven Central availability
- [ ] Update development version
- [ ] Update documentation and README
- [ ] Create release announcement
- [ ] Monitor for issues
- [ ] Plan next development cycle

## Support and Contacts

- **Release Manager**: [Contact information]
- **CI/CD Issues**: [GitHub Actions logs and issue tracker]
- **Maven Central Issues**: [Sonatype support]
- **Security Issues**: [Security contact information]

---

For questions or issues with the release process, please consult this documentation first, then create an issue in the project repository with detailed information about the problem.