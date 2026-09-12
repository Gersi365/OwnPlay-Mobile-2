# Build Baseline

This document records the initial OwnPlay Mobile 2 repository bootstrap baseline.

## Platform

- Android application ID: `app.ownplay.mobile`
- Minimum SDK: 26
- Compile SDK: 36
- Target SDK: 36
- JDK: 17

## Build tooling

- Android Gradle Plugin: 9.4.0
- Gradle: 9.6.0
- Kotlin: 2.4.20
- Compose BOM: 2026.04.01
- Activity Compose: 1.11.0

## Bootstrap scope

This stage establishes only the Android project, Kotlin/Compose compilation path, one minimal bootstrap activity, unit-test wiring, lint wiring, Android-test compilation, and CI validation that must not produce an APK.

The approved OwnPlay design system, navigation shell, media features, persistence, networking, playback, and downloads remain separate later stages.

## SDK and Compose baseline corrections

The initial Stage 0 draft attempted API level 37 because AGP 9.4 supports that maximum API level. Real GitHub Actions validation proved that the configured stable SDK repository did not expose `platforms;android-37`; installation failed before Gradle execution.

The repository therefore uses Android 16 / API level 36 as the current stable compile and target SDK baseline.

A subsequent CI run proved that Compose BOM `2026.08.00` resolves core Compose artifacts at 1.12.0, whose AAR metadata requires compile SDK 37 or later. The baseline was therefore pinned to the stable April 2026 Compose release (`2026.04.01`, core Compose 1.11) and Activity Compose 1.11.0, both aligned with the API 36 build baseline.

## Gradle wrapper status

The repository does not yet commit the Gradle wrapper binary. CI pins Gradle 9.6.0 through `gradle/actions/setup-gradle` so Stage 0 validation remains deterministic without generating or committing an unverified binary through ChatGPT connector writes.

Adding the standard Gradle wrapper is a remaining Stage 0 repository-tooling item and should be generated from the pinned Gradle 9.6.0 distribution, then validated before commit.
