# Custom Deeplink Support Implementation Summary

## Overview
Successfully implemented custom deeplink support for the Gamma app, allowing users to choose between iPogo, Pokemod, or custom deeplink formats for teleporting to coordinates.

## Changes Made

### 1. New Files Created

#### `DeeplinkManager.kt`
- **Location**: `app/src/main/java/com/mints/projectgammatwo/data/DeeplinkManager.kt`
- **Purpose**: Centralized manager for deeplink preferences
- **Features**:
  - Singleton pattern for app-wide access
  - Support for three deeplink types:
    - **iPogo** (default): `https://ipogo.app/?coords=LAT,LNG`
    - **Pokemod**: `https://pk.md/LAT,LNG`
    - **Custom**: User-defined URL template with `%s` placeholder
  - Persistent storage using SharedPreferences
  - Methods:
    - `getDeeplinkType()`: Get current deeplink type
    - `setDeeplinkType(type: String)`: Set deeplink type
    - `getCustomUrl()`: Get custom URL template
    - `setCustomUrl(url: String)`: Set custom URL template
    - `generateDeeplink(lat: Double, lng: Double)`: Generate deeplink URL for coordinates

### 2. Modified Files

#### `ExportData.kt`
- Added two new fields for import/export functionality:
  - `deeplinkType: String = "ipogo"` (default value)
  - `deeplinkCustomUrl: String = ""`

#### `strings.xml`
- Added new strings for the deeplink settings UI:
  - `deeplink_method`: "Deeplink Method"
  - `deeplink_ipogo`: "iPogo"
  - `deeplink_pokemod`: "Pokemod"
  - `deeplink_custom`: "Custom"
  - `deeplink_custom_url_hint`: "Enter custom URL (use %s for coordinates)"
  - `deeplink_custom_url_example`: "Example: https://example.com/?coords=%s"
  - `deeplink_custom_url_saved`: "Custom deeplink URL saved"
  - `deeplink_custom_url_invalid`: "Custom URL should contain %s placeholder for coordinates"

#### `fragment_settings.xml`
- Added new UI section for deeplink method selection:
  - RadioGroup with three options (iPogo, Pokemod, Custom)
  - EditText for custom URL input (shown only when Custom is selected)
  - TextView showing example format (shown only when Custom is selected)

#### `SettingsFragment.kt`
- Added fields for deeplink UI components:
  - `radioGroupDeeplink`, `radioDeeplinkIpogo`, `radioDeeplinkPokemod`, `radioDeeplinkCustom`
  - `customDeeplinkUrl`, `customDeeplinkExample`
  - `deeplinkManager`
- Added `setupDeeplinkMethod()` function:
  - Loads and displays saved deeplink preferences
  - Handles radio button changes
  - Shows/hides custom URL input based on selection
  - Validates custom URL has `%s` placeholder
  - Saves preferences on change
- Updated `exportSettings()`:
  - Includes deeplink type and custom URL in export
- Updated `importSettings()`:
  - Restores deeplink preferences from import
  - Updates UI to reflect imported settings

#### Adapter Files (Updated to use DeeplinkManager)
All adapters now use `DeeplinkManager.getInstance(context).generateDeeplink(lat, lng)` instead of hardcoded iPogo URLs:

1. **InvasionsAdapter.kt**
   - Updated teleport button to use DeeplinkManager

2. **RaidsAdapter.kt**
   - Updated teleport button to use DeeplinkManager

3. **QuestAdapter.kt**
   - Updated teleport button to use DeeplinkManager

4. **DeletedQuestsAdapter.kt**
   - Updated teleport button to use DeeplinkManager

5. **DeletedInvasionsAdapter.kt**
   - Updated teleport button to use DeeplinkManager

#### Fragment Files

1. **FavoritesFragment.kt**
   - Updated `teleportToFavorite()` function to use DeeplinkManager

#### Manager Files

1. **FavoritesManager.kt**
   - Updated `teleportToLocation()` function to use DeeplinkManager

#### Service Files

1. **OverlayService.kt**
   - Updated `launchHome()` function to use DeeplinkManager
   - Updated `launchMap()` function to use DeeplinkManager
   - Updated `launchQuest()` function to use DeeplinkManager
   - Updated favorites overlay teleport callback to use DeeplinkManager

## How It Works

### User Flow

1. **Settings Configuration**:
   - User opens Settings tab
   - Scrolls to "Deeplink Method" section
   - Selects one of three options:
     - **iPogo** (default)
     - **Pokemod**
     - **Custom** (shows input field for custom URL)
   - If Custom is selected, enters URL template with `%s` placeholder (e.g., `https://myapp.com/?coords=%s`)
   - Preferences are saved automatically

2. **Using Deeplinks**:
   - When user clicks any "Teleport" button in the app (invasions, quests, raids, favorites, etc.)
   - DeeplinkManager generates the appropriate URL based on saved preference
   - URL is opened with `Intent.ACTION_VIEW`

3. **Import/Export**:
   - Deeplink preferences are included in settings export
   - When importing settings, deeplink preferences are restored
   - UI updates to reflect imported settings

### Technical Details

- **Default Behavior**: iPogo is the default deeplink type (maintains backward compatibility)
- **Persistence**: Settings are stored in SharedPreferences (`deeplink_prefs`)
- **Singleton Pattern**: DeeplinkManager uses singleton pattern for efficient memory usage
- **Validation**: Custom URLs are validated to ensure they contain the `%s` placeholder
- **Fallback**: If custom URL is invalid or empty, falls back to iPogo format

## Testing Recommendations

1. **Basic Functionality**:
   - Test switching between iPogo, Pokemod, and Custom
   - Verify custom URL input shows/hides correctly
   - Test custom URL validation

2. **Deeplink Generation**:
   - Test teleport from Invasions tab
   - Test teleport from Quests tab
   - Test teleport from Raids tab (if applicable)
   - Test teleport from Favorites tab
   - Test teleport from Journal/History
   - Test teleport from Overlay service

3. **Import/Export**:
   - Export settings with each deeplink type
   - Import settings and verify deeplink preferences are restored
   - Verify UI updates correctly after import

4. **Edge Cases**:
   - Test custom URL without `%s` placeholder
   - Test empty custom URL
   - Test very long custom URLs
   - Test special characters in custom URL

## Files Changed Summary

**New Files**: 1
- `DeeplinkManager.kt`

**Modified Files**: 13
- `ExportData.kt`
- `strings.xml`
- `fragment_settings.xml`
- `SettingsFragment.kt`
- `InvasionsAdapter.kt`
- `RaidsAdapter.kt`
- `QuestAdapter.kt`
- `DeletedQuestsAdapter.kt`
- `DeletedInvasionsAdapter.kt`
- `FavoritesFragment.kt`
- `FavoritesManager.kt`
- `OverlayService.kt`

## Build Status

✅ **BUILD SUCCESSFUL** - All changes compile without errors

Only warnings present are pre-existing deprecation warnings unrelated to this implementation.
