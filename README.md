# CyPOS Sales (SalesMan) - Android Mobile Salesman App

CyPOS Sales is a robust, enterprise-grade Android application designed for sales representatives and mobile POS operations. It streamlines the sales process with features like product cataloging, cart management, offline session handling, advanced security layers, and real-time location tracking.

## 🚀 Key Features

- **Advanced Security**: 
  - Integrated PIN and Biometric (Fingerprint/Face) authentication gate for fast app re-entry.
  - Secure Password Reset flow (Forgot Password & Reset Password).
- **Smart Session Management**: 
  - Automatic idle-timeout locking based on user preference.
  - Local session preservation for seamless offline/re-entry capability.
- **Accurate Location-Based Pricing**:
  - Automatic GPS coordinate fetching for every product and sales request.
  - Persistent location caching to ensure realistic pricing even when GPS is temporarily unavailable.
  - Mandatory location enforcement to prevent data inconsistency.
- **Sales & Order Management**:
  - Browse and search products with real-time stock levels.
  - Detailed product descriptions with image loading via Glide.
  - Multi-unit support (PKT, Unit, etc.) with alternate unit conversions.
  - Add to cart, flexible checkout, and **Parked Carts** (save and resume later).
  - Advanced Sales filtering by customer, product, and date.
- **Customer Management**: 
  - Create, search, and manage customers for personalized sales.
- **Reports & Analytics**:
  - Visualize sales data with interactive charts (MPAndroidChart).
  - Filterable sales reports for daily, monthly, or product-specific insights.
- **Enterprise Logging**: 
  - Full API request/response tracking for deep debugging.
  - User activity and system lifecycle logging.
  - One-click log export (Settings > Debug Support).
- **Theming & Config**:
  - System-wide Dark Mode support.
  - Dynamic API endpoint configuration.

## 🛠 Tech Stack

- **Language**: Java 11
- **Networking**: Retrofit 2 & OkHttp 3
- **Location**: Google Play Services Location (FusedLocationProviderClient)
- **Database**: SQLite (Custom `Db` helper)
- **UI & Visualization**:
  - Material Components for Android
  - MPAndroidChart (Reporting)
  - Glide (Image Loading)
  - Lottie & DotsIndicator (UI/UX enhancements)
- **Auth**: BiometricPrompt API
- **Utilities**: Toasty (Feedback), Gson (Serialization)

## 📦 Project Structure

```text
com.js.salesman
├── adapters         # RecyclerView adapters for products, sales, units, etc.
├── clients          # API Client & Retrofit Configuration
├── interfaces       # API Interface definitions
├── models           # POJO Data models (Product, Order, Customer, Responses)
├── services         # Background services (GPSService for batch tracking)
├── ui
│   ├── activities   # BaseActivity, MainActivity, AuthGate, Lock, Pin, Auth flow
│   ├── fragments    # Home, Product, Sales, Cart, Settings, Reports, Profile, etc.
│   └── views        # Custom UI components (GestureScrollView)
└── utils
    ├── managers     # Session, Settings, Log, GPS, and Prefs Managers
    ├── LocationUtils.java # Shared location fetching & caching logic
    └── Db.java      # Local Database management
```

## ⚙️ Setup & Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-repo/SalesMan.git
   ```
2. **Open in Android Studio**:
   - Select "Open an existing project".
   - Wait for Gradle Sync to complete.
3. **Configure Permissions**:
   - The app requires **Location Permission** (Fine & Coarse) for product pricing and sales tracking.
4. **Configure API**:
   - On first launch, use the `ConfigActivity` to set your server's base URL.
   - Alternatively, change it in **Settings > API Endpoint**.

## 🔐 Authentication Flow

1. **Initial Login**: Email/Password + Token generation.
2. **Fast Access**: Handled by `AuthGateActivity` (Biometric or PIN).
3. **Session Auto-Lock**: Triggers `LockActivity` after a configurable inactivity period.
4. **Recovery**: Integrated "Forgot Password" flow with OTP/Reset capabilities.

## 🤝 Contributing

We welcome contributions to improve CyPOS Sales! To contribute:

1. **Fork the Project** on GitHub.
2. **Clone your fork** to your local machine.
3. **Create a Feature Branch** (`git checkout -b feature/AmazingFeature`).
4. **Commit your Changes** with descriptive messages (`git commit -m 'Add some AmazingFeature'`).
5. **Push to the Branch** (`git push origin feature/AmazingFeature`).
6. **Open a Pull Request** against the main repository.

Please ensure your code follows the existing style, includes necessary comments, and does not break existing location or auth flows.

## 📞 Support & API Access

The API source code for this project is currently private. If you are interested in learning how to build a system like this, or if you would like to request access to the API source code, feel free to reach out.

**Contact me via WhatsApp**:
[+254 757 146 341](https://wa.me/254757146341?text=Hi,%20I%27m%20interested%20in%20learning%20about%20the%20CyPOS%20Sales%20system)

**Call/Support**: [+254 757 146 341]

---
*Developed with ❤️ by Tosby*
