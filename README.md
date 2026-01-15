# Hytale Gradle Plugin
A Gradle plugin to streamline Hytale server development.

## Usage
### Applying the Plugin

```kotlin
plugins {
    id("fr.smolder.hytale.dev") version "0.0.1"
}
```

### Configuration
Configure the plugin using the `hytale` block:

```kotlin
hytale {
    // Optional: Override Hytale installation path
    hytalePath.set("C:/Users/You/AppData/Roaming/Hytale")

    // Optional: patch line (defaults to "live")
    patchLine.set("1.2.3")

    // Optional: game version (defaults to "latest")
    gameVersion.set("latest")
    
    // Auto-update manifest.json during build? (defaults to true)
    autoUpdateManifest.set(true)
    
    // Memory configuration
    minMemory.set("2G")
    maxMemory.set("4G")

    // You can modify the default arguments or add your own
    serverArgs.add("--hello-world")
}
```

### Tasks
- `./gradlew runServer`: Starts the Hytale server with the configured environment.
- `./gradlew build`: Builds the project and updates the `manifest.json`.

## Contributing
Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License
This project is licensed under the MIT License.