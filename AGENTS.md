# AGENTS.md

## 1. Project overview

This repository contains a personal Android vocabulary and dictionary application.

The application lets users:

* create and maintain their own local vocabulary dictionary;
* search supported external dictionary providers;
* select dictionary definitions and edit them before saving;
* enter words, meanings, examples, notes, pronunciation information, and tags manually;
* organize saved entries with user-defined tags;
* use the saved dictionary without an internet connection;
* export and restore user-created data;
* add more source languages and dictionary providers later.

The application must not use machine translation or LLM-generated definitions. Dictionary information must come from human-edited dictionary data, supported external dictionary APIs, local open dictionary datasets, or direct user input.

The first implementation is a personal-use application. It does not require user accounts, cloud synchronization, analytics, advertisements, a backend, or a proxy server.

## 2. Development status and environment

Assume the developer may initially have none of the Android development tools installed.

Before implementing or building:

1. inspect the current repository and development environment;
2. report which required tools are installed and which are missing;
3. do not claim that a build or test succeeded unless the command was actually run successfully;
4. provide exact installation or configuration steps for missing tools;
5. use the Gradle Wrapper included in the repository;
6. never require a globally installed Gradle distribution.

Required development environment:

* latest stable Android Studio;
* Android SDK;
* compatible JDK;
* Kotlin;
* Gradle Wrapper;
* an Android emulator or USB-connected Android device.

Do not modify unrelated VS Code settings, globally installed Python packages, C toolchains, shell profiles, or operating-system configuration without an explicit request.

## 3. Technology choices

Use:

* Kotlin;
* Jetpack Compose;
* Material 3;
* Room Database;
* Kotlin Coroutines and Flow;
* Retrofit and OkHttp for HTTP APIs;
* Kotlin Serialization or Moshi for JSON parsing;
* Navigation Compose;
* DataStore for non-relational application settings;
* Hilt for dependency injection;
* KSP where supported;
* JUnit for unit tests;
* AndroidX testing libraries for Android tests;
* Compose UI tests for critical user flows.

Prefer current stable versions that are mutually compatible. Do not use alpha, beta, release-candidate, deprecated, or abandoned libraries unless there is a documented reason.

Use a version catalog for dependencies.

## 4. Architectural principles

Use a pragmatic clean architecture. Avoid both monolithic code and unnecessary abstraction.

Begin with one Android application module unless a second module solves a concrete current problem. Organize the source code so that future extraction into Gradle modules is straightforward.

Use package-by-feature at the presentation level and explicit boundaries between presentation, domain, and data responsibilities.

Recommended structure:

```text
app/src/main/java/<base-package>/
├── app/
│   ├── DictionaryApplication.kt
│   ├── MainActivity.kt
│   ├── AppNavigation.kt
│   └── di/
├── core/
│   ├── common/
│   ├── database/
│   ├── network/
│   ├── model/
│   └── ui/
├── feature/
│   ├── wordlist/
│   ├── worddetail/
│   ├── wordeditor/
│   ├── dictionarysearch/
│   ├── tags/
│   ├── review/
│   ├── backup/
│   └── settings/
├── dictionary/
│   ├── domain/
│   ├── provider/
│   │   ├── cambridge/
│   │   ├── jmdict/
│   │   ├── ccedict/
│   │   └── wiktionary/
│   └── registry/
└── vocabulary/
    ├── domain/
    └── data/
```

This structure is a guideline. Change it only when a simpler structure provides clearer ownership.

## 5. Dependency direction

Maintain these dependency rules:

* Compose UI depends on ViewModels and immutable UI state.
* ViewModels depend on use cases or repository interfaces.
* Domain code does not depend on Android UI, Retrofit, Room, or provider-specific response classes.
* Data implementations depend on Room, Retrofit, local files, and provider-specific code.
* External dictionary providers must not expose their raw API DTOs outside their provider package.
* Room entities must not be used directly as Compose UI models.
* Mapping between network DTOs, database entities, domain models, and UI models must be explicit.

Do not create a use-case class that only renames a single repository method unless it enforces a business rule, coordinates multiple operations, or improves testability.

## 6. Dictionary provider extensibility

All external and local dictionary sources must implement a common provider contract.

The provider abstraction must support:

* a stable provider identifier;
* provider display name;
* supported source languages;
* supported target languages or definition languages;
* online or offline capability;
* attribution and licensing metadata;
* search;
* optional exact-entry lookup;
* structured failure results;
* provider availability checks.

Example conceptual contract:

```kotlin
interface DictionaryProvider {
    val descriptor: DictionaryProviderDescriptor

    suspend fun search(
        query: DictionaryQuery
    ): DictionarySearchResult
}
```

Do not force every provider to support the same linguistic fields. Model optional capabilities explicitly.

Examples include:

* pronunciation;
* audio;
* reading;
* transliteration;
* grammatical gender;
* inflection;
* etymology;
* example sentences;
* translations;
* monolingual definitions.

Adding a new language or dictionary source should normally require:

1. a new provider implementation;
2. provider-specific DTOs or local parsers;
3. mappings to common domain models;
4. registration in the provider registry;
5. tests;
6. no modification to unrelated screens or existing providers.

Avoid `when` statements spread throughout the application for provider or language selection. Centralize provider discovery and selection in a registry.

Use BCP 47 language tags or another documented standard representation. Do not identify languages only by translated display names.

## 7. Initial language scope

Design for future support of:

* English;
* Japanese;
* Simplified and Traditional Chinese;
* German;
* French;
* Spanish;
* Russian;
* Arabic;
* Hindi;
* Latin.

Do not implement all languages in the first milestone.

Initial implementation priority:

1. manual entry for any language;
2. English provider support through Cambridge, subject to API access and license confirmation;
3. Japanese local dictionary support through JMdict, subject to license review;
4. Chinese local dictionary support through CC-CEDICT, subject to license review;
5. additional providers only after the core workflow is stable.

Latin must remain an extensibility target. Do not invent a Latin API or include unverified data.

## 8. Domain model requirements

Keep external dictionary search results separate from user-owned vocabulary entries.

External results are temporary source material. User vocabulary entries are persistent, editable user data.

A saved vocabulary entry should be able to contain:

* stable local ID;
* headword or expression;
* source language;
* optional reading or transliteration;
* optional pronunciation;
* one or more user-editable senses;
* part of speech;
* user-written meaning;
* optional original dictionary definition;
* example sentences;
* notes;
* source attribution;
* source entry identifier;
* user-created tags;
* favorite status;
* creation and modification timestamps;
* review metadata.

Do not overwrite user-edited content when refreshing provider data.

Definitions and senses must not be stored as one delimiter-separated string.

Tags have a many-to-many relationship with vocabulary entries.

Use database foreign keys, indices, transactions, and uniqueness constraints where appropriate.

## 9. Local-first data policy

User-created vocabulary data must be stored locally in Room.

The app must remain useful offline for:

* listing saved words;
* searching saved words;
* viewing and editing entries;
* managing tags;
* reviewing saved entries;
* exporting data.

Network access is required only for online dictionary searches and provider availability checks.

App deletion may delete local data, so backup and restore are required before the application is considered complete.

Initial backup format:

* versioned JSON for complete restoration;
* UTF-8;
* documented schema version;
* validation before import;
* import preview or conflict policy;
* no silent destructive replacement.

CSV export can be added separately for portability, but JSON is the canonical backup format.

## 10. API credentials and secrets

Never commit real API keys, tokens, credentials, keystores, passwords, or personal data.

The repository must contain:

* `.gitignore` rules for local secrets;
* placeholder configuration only;
* documentation explaining how each user obtains and enters their own API key.

For the personal-use version, users may enter their own API keys in the application settings.

Do not hard-code keys in source code, resources, Gradle files, `BuildConfig`, sample files, tests, screenshots, or documentation.

Store user-entered keys using an appropriate Android local credential-storage mechanism. Clearly document that client-side storage cannot provide the same secrecy as a server-side proxy.

Do not add a proxy server or backend unless explicitly requested.

## 11. Licensing and attribution

Treat dictionary data licensing as a product requirement.

For every provider or dataset, document:

* official source;
* data license or API terms;
* whether local storage is permitted;
* whether editing or derivative storage is permitted;
* required attribution;
* redistribution restrictions;
* caching restrictions;
* update requirements.

Do not download, bundle, cache, redistribute, or permanently store external dictionary content until its license permits the intended use.

Add a `docs/dictionary-sources.md` file containing this information.

Separate facts confirmed from official sources from assumptions that require confirmation.

If an API license does not permit permanent storage, do not silently persist its raw definitions. Instead, stop implementation of that persistence path and record the issue.

## 12. Clean-code rules

Write code optimized for readability, changeability, and testability.

Required practices:

* use meaningful names based on domain language;
* keep functions focused on one level of abstraction;
* prefer small immutable data classes;
* prefer composition over inheritance;
* make side effects explicit;
* keep I/O at architectural boundaries;
* use sealed interfaces or explicit result types for recoverable states;
* avoid boolean parameters whose meaning is unclear at the call site;
* avoid deeply nested conditionals;
* remove duplication when the shared concept is stable;
* do not abstract code merely because two lines currently look similar;
* avoid generic names such as `Manager`, `Helper`, `Util`, `Processor`, or `Common` unless the responsibility is precise;
* do not use global mutable state;
* do not use `GlobalScope`;
* do not block coroutine threads;
* do not swallow exceptions;
* do not use exceptions as ordinary control flow;
* do not leave unexplained magic numbers or strings;
* keep public APIs minimal;
* use comments to explain decisions and constraints, not to restate code;
* delete dead code instead of commenting it out.

Prefer files with one primary responsibility. Do not create arbitrary maximum line limits, but split files when they contain multiple reasons to change.

## 13. UI and state management

Use unidirectional data flow.

Each screen should expose:

* immutable `UiState`;
* explicit user actions or events;
* one ViewModel responsible for screen-level state;
* observable `StateFlow`;
* loading, empty, content, and error states where applicable.

Do not perform network or database operations directly in Composables.

Do not pass navigation controllers deep into the UI tree. Pass callbacks.

Preserve user input through configuration changes and process recreation where appropriate.

Initial screens:

1. vocabulary list;
2. add/edit vocabulary entry;
3. vocabulary detail;
4. external dictionary search;
5. tag management;
6. settings and API credentials;
7. backup and restore;
8. basic review screen, after core CRUD is stable.

Prioritize usability and a restrained Material 3 interface over decorative animations.

## 14. Testing requirements

Every meaningful feature must include tests appropriate to its layer.

At minimum:

* unit tests for domain rules and mappings;
* Room DAO tests;
* repository tests using fakes;
* provider response parsing tests with fixed local fixtures;
* ViewModel state-transition tests;
* backup serialization and restoration tests;
* tests verifying that user-edited meanings are not overwritten;
* tests for provider capability and language selection;
* tests for malformed API responses and network failures.

Do not make live paid API calls in automated tests.

Store sanitized, license-compatible response fixtures under test resources.

Before completing a task, run the most relevant available commands, such as:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

If Android SDK or another dependency prevents a command from running, report the exact blocker and do not describe the command as successful.

## 15. Documentation requirements

Maintain:

* `README.md`;
* `AGENTS.md`;
* `docs/architecture.md`;
* `docs/dictionary-sources.md`;
* `docs/setup.md`;
* `docs/decisions/` for significant architecture decisions;
* `CHANGELOG.md` once releases begin.

The README must explain:

* project purpose;
* current feature status;
* supported languages and providers;
* environment setup;
* how to build and install the APK;
* how users supply their own API keys;
* data storage and backup behavior;
* licensing and attribution;
* known limitations.

Documentation should be suitable for a development blog reader reproducing the project from a fresh computer.

## 16. Change procedure

For each non-trivial task:

1. inspect the existing repository;
2. summarize the relevant current structure;
3. state assumptions and unresolved licensing constraints;
4. propose a small implementation plan;
5. modify only files required by that plan;
6. add or update tests;
7. run available checks;
8. summarize changed files and verification results;
9. identify remaining blockers without hiding them.

Do not perform broad refactors while implementing a small feature unless the refactor is required for correctness.

When changing an interface, update every implementation and its tests in the same task.

Preserve backward compatibility for saved user data. Room schema changes must use reviewed migrations once the application has persistent releases. Do not use destructive migration as the normal solution.

## 17. Initial delivery boundary

The first milestone is not the complete multilingual application.

The first milestone should provide:

* a buildable Compose project;
* local Room persistence;
* manual vocabulary CRUD;
* multiple senses per entry;
* editable user tags;
* local search and tag filtering;
* basic settings;
* JSON export and import design, with implementation if scope permits;
* tests for the implemented behavior;
* architecture prepared for dictionary providers;
* no live dictionary API required for the first successful build.

Only after this milestone builds and tests successfully should the first external dictionary provider be implemented.

## 18. Prohibited shortcuts

Do not:

* generate fake API integrations;
* claim unsupported languages;
* scrape dictionary websites without explicit legal and technical review;
* invent API response fields;
* place all application logic in `MainActivity`;
* place all code in one package;
* couple Room entities directly to Compose;
* add AI, LLM, machine-translation, analytics, advertisements, login, or cloud services;
* add a backend or proxy;
* commit secrets;
* ignore API or dataset licenses;
* use destructive database migration to hide schema problems;
* replace tests with comments or TODOs;
* claim that commands were executed when they were not.
