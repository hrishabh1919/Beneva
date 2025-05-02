# Beneva

## Overview
Beneva is an Android application built with modern Android development practices. It helps users make conscious choices by providing information about products through barcode scanning.

## Features
- 🔍 Barcode Scanning: Scan product barcodes to get detailed information
- 🌱 Product Information: View product details including:
  - EcoScore rating
  - Ingredients list
  - Allergen information
  - Packaging details
  - Brand sustainability
- 🔐 Secure Authentication: Google Sign-in integration
- 📱 Modern UI: Material Design with smooth animations
- 🌐 Real-time Database: Firebase Firestore integration

## Prerequisites
- Android Studio Arctic Fox (2020.3.1) or newer
- JDK 11 or newer
- Android SDK 21 or newer
- Gradle 7.0 or newer
- Firebase Account
- Google Cloud Project

## Getting Started

### Installation
1. Clone the repository:
```bash
git clone https://github.com/raghavtanejax/Beneva
```

2. Open the project in Android Studio

3. Set up Firebase:
   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com)
   - Add your Android app to the Firebase project
   - Download the `google-services.json` file
   - Place `google-services.json` in the `app/` directory

4. Configure Firestore Database:
   - Enable Firestore in your Firebase project
   - Set up the following collection structure:
   ```json
   {
     "products": [
       {
         "barcode": "123456789",
         "name": "Product Name",
         "ingredients": ["ingredient1", "ingredient2"],
         "ecoScore": 75,
         "allergens": ["nuts", "milk"],
         "packaging": "Recyclable",
         "brandSustainability": "High"
       }
     ]
   }
   ```

5. Sync the project with Gradle files

6. Build and run the application

## Project Structure
```
app/
├── src/
│   ├── main/
│   │   ├── java/        # Java/Kotlin source files
│   │   │   ├── activities/    # Activity classes
│   │   │   ├── models/        # Data models
│   │   │   └── utils/         # Utility classes
│   │   ├── res/         # Resource files
│   │   │   ├── drawable/      # Images and icons
│   │   │   ├── layout/        # XML layouts
│   │   │   └── values/        # Strings, colors, styles
│   │   └── AndroidManifest.xml
│   └── test/           # Unit tests
├── build.gradle        # App-level build configuration
└── proguard-rules.pro  # ProGuard rules
```

## Dependencies
- CameraX: For camera functionality
- ML Kit: For barcode scanning
- Firebase: For authentication and database
- Material Design: For UI components
- Coroutines: For asynchronous operations

## Building the Project
To build the project, you can use either:
- Android Studio's Build menu
- Command line:
```bash
./gradlew build
```

## Running Tests
To run tests, you can use either:
- Android Studio's Run menu
- Command line:
```bash
./gradlew test        # For unit tests
./gradlew connectedAndroidTest  # For instrumentation tests
```

## Troubleshooting

### Barcode Scanning Issues
If products are not found in the database:
1. Verify the barcode format matches exactly in the database
2. Check Firestore security rules
3. Ensure internet connectivity
4. Verify Firebase configuration

### Camera Issues
If camera doesn't work:
1. Check camera permissions
2. Verify device compatibility
3. Check CameraX implementation

## Contributing
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License
This project is licensed under the MIT License - see the LICENSE file for details

## Contact
Your Name - Raghav, Hrishabh, Naitik, Harshit

Project Link: [https://github.com/raghavtanejax/Beneva](https://github.com/raghavtanejax/Beneva)