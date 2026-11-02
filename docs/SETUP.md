# Setup guide

1. [Install Dashboard](#1-install-dashboard)
2. [Location tracking with Traccar](#2-location-tracking-with-traccar)
3. [Building your own APK](#3-building-your-own-apk)
4. [Custom shortcuts](#4-custom-shortcuts)

## 1. Install Dashboard

1. Download the latest APK from the [releases](../../../releases) and install it on the head
   unit (from a USB stick with a file manager, or `adb install`).
2. Press Home. Android asks which launcher to use: pick **Dashboard → Always** (or **Just
   once** to try it first).
3. Allow location access when Dashboard asks. It is used for the weather and for tracking.

Settings are behind the gear at the top right of the app drawer (tap the clock to open the
drawer). Released builds have no Settings PIN; see [section 3](#3-building-your-own-apk) to set
one.

## 2. Location tracking with Traccar

Dashboard can send the car's position to a [Traccar](https://www.traccar.org/) server every 5
seconds, so you can see where the car is and where it has been from a browser or the Traccar
Manager app. It does the job of the Traccar Client app, so you don't need that app on the head
unit.

### What you need

- A Traccar server reachable from the internet: your own install
  ([download](https://www.traccar.org/download/), Docker, or a VPS), or a hosted plan. The
  free demo servers (`demo.traccar.org` and others) are fine for a quick test, but they are
  public: don't use them for real tracking.
- Mobile data on the head unit (a SIM card or a phone hotspot).

### Step 1: add the car in Traccar

1. Open the Traccar web interface and log in.
2. Add a device (the **+** button on the device list).
3. Give it a name ("Car") and an **Identifier**, for example `car` or a random string.
   Dashboard sends this identifier with every position, so it has to match exactly.

### Step 2: point Dashboard at the server

Open the app drawer (tap the clock), tap the gear, scroll down to **Traccar settings** and set:

| Setting | Value |
|---|---|
| **Server** | Your server's OsmAnd address, e.g. `http://traccar.example.com:5055`. Port 5055 is Traccar's default for the OsmAnd protocol, which Dashboard uses (not the web interface's port 8082). If you put the OsmAnd port behind an HTTPS reverse proxy, use that URL instead, e.g. `https://track.example.com`. |
| **Device ID** | The identifier from step 1. |
| **Location tracking** | On. |

### Step 3: check that it works

- A location pin appears in the top right corner of the home screen while tracking is on.
  It **flashes cyan** each time positions reach the server.
- Tap the **weather** strip to send the current position right away.
- In Traccar the device turns online and the car shows on the map.

### Good to know

- Positions that can't be sent (no signal, server down) are kept on the head unit, up to about
  a day of driving, and go out in order once the connection is back.
- Tracking starts by itself whenever the home screen shows, so it keeps working after the head
  unit wakes from sleep or reboots. A notification shows while it runs.
- Speed is sent in knots, as the OsmAnd protocol expects; Traccar converts it.
- If you used Traccar Client before, uninstall it or turn it off, so two apps don't report
  the same device.

### Troubleshooting

If the pin never flashes:

1. Check the server URL: it needs `http://` or `https://`, the OsmAnd port (5055 unless you
   changed it), and no path.
2. Check that the Device ID matches the identifier in Traccar exactly.
3. Make sure the port is open in the server's firewall. From a computer, with your Device
   ID in `id=`, this should return `200 OK` and put a position on the map in Traccar:

   ```bash
   curl -i -X POST "http://traccar.example.com:5055/?id=car&lat=48.8584&lon=2.2945&timestamp=$(date +%s)"
   ```

4. Make sure Dashboard has location access (Android Settings → Apps → Dashboard →
   Permissions) and the head unit has a GPS fix.

## 3. Building your own APK

Requirements: JDK 17+ (Android Studio's bundled `jbr` works) and the Android SDK with
platform 35.

1. Create `android/local.properties` with `sdk.dir=<path to the Android SDK>` (Android Studio
   does this for you).
2. Copy [`android/defaults.properties.example`](../android/defaults.properties.example) to
   `android/defaults.properties` and fill it in. That file is gitignored, so your server,
   home point and PIN stay out of git. Every key is optional:

   | Key | What it does |
   |---|---|
   | `tracking.server` | Default Traccar server. When set, tracking starts switched on. |
   | `tracking.deviceId` | Default Device ID. |
   | `home` | Home point as `latitude, longitude`, for opening the parking app on arrival. |
   | `settings.pinSha256` | PIN in front of Settings (see below). Empty = no PIN. |
   | `parking.package` | An app to use while parking (a camera viewer, say). Enables the `parking` shortcut. |
   | `parking.onArrival` | `true` opens that app when you arrive home and shows its settings. |
   | `shortcuts` | The shortcut bar, see [section 4](#4-custom-shortcuts). |

   These are only the defaults of a fresh install; the values in Settings can still be
   changed on the head unit.

3. Build and install:

   ```bash
   cd android
   ./gradlew assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

Release builds are signed with your machine's debug keystore. Android only updates an
installed app with an APK signed by the same key, so to update a release from GitHub with
your own build (or the other way round), uninstall first.

**Settings PIN.** To keep someone else (a garage, say) from switching tracking off, put the
SHA-256 of `dashboard-settings:` followed by your PIN in `settings.pinSha256`. For PIN 1234:

```bash
printf 'dashboard-settings:1234' | sha256sum
```

Five wrong PINs lock Settings for 30 seconds. It only guards Dashboard's own settings;
Android's settings stay open.

## 4. Custom shortcuts

### Pick and order the built-in ones

The bar shows the ids listed in `shortcuts` in `android/defaults.properties`, left to right:

```properties
shortcuts=front,radio,spotify,traccar,waze
```

| Id | Label | Opens |
|---|---|---|
| `front` | Front view | `com.syu.frontvideo` (Teyes front camera) |
| `radio` | Radio | `com.syu.radio` (Teyes radio) |
| `spotify` | Spotify | `com.spotify.music` |
| `traccar` | Traccar | `org.traccar.client` |
| `waze` | Waze | `com.waze` |
| `parking` | Parking | the app in `parking.package` |

Unknown ids are skipped. Six shortcuts fit the 1024x600 screen with labels on one line.
Tapping a shortcut for an app that isn't installed shows "… is not installed".

### Add your own

A new shortcut needs a label, the app's package name and an icon. For example, Google Maps:

1. **Find the package name.** It is the `id=` part of the app's Play Store link
   (`play.google.com/store/apps/details?id=com.google.android.apps.maps`), or list the
   installed apps over adb:

   ```bash
   adb shell pm list packages | grep -i maps
   ```

2. **Add the label and package** to `android/app/src/main/res/values/strings.xml`:

   ```xml
   <string name="action_maps">Maps</string>
   <string name="package_maps" translatable="false">com.google.android.apps.maps</string>
   ```

3. **Add an icon** as `android/app/src/main/res/drawable/ic_maps.xml`. To match the others,
   use a 24x24 vector with a thin cyan outline and no fill. Copy
   [`ic_waze.xml`](../android/app/src/main/res/drawable/ic_waze.xml) and replace its
   `pathData`; outline icons from [Material Symbols](https://fonts.google.com/icons) or
   [Lucide](https://lucide.dev/) work well (convert SVGs with Android Studio's
   *New → Vector Asset*, then set `android:strokeColor="@color/accent"`).

4. **Register it** in `allShortcuts` in
   [`MainActivity.kt`](../android/app/src/main/java/com/izosa/home/car/dashboard/MainActivity.kt),
   with the id you will use in `shortcuts`:

   ```kotlin
   "maps" to Shortcut(R.string.action_maps, R.drawable.ic_maps, R.string.package_maps),
   ```

5. **Use it** in `android/defaults.properties`, then build and install as in
   [section 3](#3-building-your-own-apk):

   ```properties
   shortcuts=front,radio,spotify,maps,waze
   ```
