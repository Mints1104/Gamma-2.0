# Deeplink Implementation - Test Checklist

## Pre-Testing Setup
- [ ] Install/Update the app on test device
- [ ] Open Settings tab to verify new Deeplink Method section appears

## Feature Tests

### 1. Settings UI - Basic Functionality
- [ ] Verify "Deeplink Method" section appears in Settings
- [ ] Verify three radio options are present: iPogo, Pokemod, Custom
- [ ] Verify iPogo is selected by default
- [ ] Select Pokemod - verify custom URL field remains hidden
- [ ] Select Custom - verify custom URL input field appears
- [ ] Select Custom - verify example text appears
- [ ] Select iPogo again - verify custom URL field hides
- [ ] Enter custom URL: `https://test.com/?coords=%s`
- [ ] Tap outside input field - verify "Custom deeplink URL saved" toast appears
- [ ] Enter invalid URL without %s: `https://test.com/`
- [ ] Tap outside input field - verify warning toast appears but URL is saved

### 2. Deeplink Generation - iPogo Mode
Set deeplink to iPogo, then test teleport from:
- [ ] Home tab - Invasion item teleport button
- [ ] Home tab - Overlay invasion teleport
- [ ] Quests tab - Quest item teleport button
- [ ] Quests tab - Overlay quest teleport
- [ ] Favorites tab - Favorite item teleport button
- [ ] Favorites tab - Overlay favorite teleport
- [ ] Journal - Deleted invasion teleport button
- [ ] Journal - Deleted quest teleport button
- [ ] Settings - Home coordinates teleport

**Expected**: All should open URLs like `https://ipogo.app/?coords=40.7128,-74.0060`

### 3. Deeplink Generation - Pokemod Mode
Set deeplink to Pokemod, then test teleport from:
- [ ] Home tab - Invasion item teleport button
- [ ] Quests tab - Quest item teleport button
- [ ] Favorites tab - Favorite item teleport button

**Expected**: All should open URLs like `https://pk.md/40.7128,-74.0060`

### 4. Deeplink Generation - Custom Mode
Set deeplink to Custom with URL: `https://myapp.com/goto?loc=%s`
Then test teleport from:
- [ ] Home tab - Invasion item teleport button
- [ ] Quests tab - Quest item teleport button
- [ ] Favorites tab - Favorite item teleport button

**Expected**: All should open URLs like `https://myapp.com/goto?loc=40.7128,-74.0060`

### 5. Settings Persistence
- [ ] Set deeplink to Pokemod
- [ ] Close and reopen the app
- [ ] Open Settings - verify Pokemod is still selected
- [ ] Set deeplink to Custom with URL: `https://custom.com/%s`
- [ ] Close and reopen the app
- [ ] Open Settings - verify Custom is selected and URL is preserved

### 6. Import/Export - iPogo
- [ ] Set deeplink to iPogo
- [ ] Export settings
- [ ] Verify exported JSON contains: `"deeplinkType": "ipogo"`
- [ ] Change to Pokemod
- [ ] Import the saved settings
- [ ] Verify deeplink reverts to iPogo
- [ ] Verify UI shows iPogo selected

### 7. Import/Export - Pokemod
- [ ] Set deeplink to Pokemod
- [ ] Export settings
- [ ] Verify exported JSON contains: `"deeplinkType": "pokemod"`
- [ ] Change to iPogo
- [ ] Import the saved settings
- [ ] Verify deeplink reverts to Pokemod
- [ ] Verify UI shows Pokemod selected

### 8. Import/Export - Custom
- [ ] Set deeplink to Custom
- [ ] Enter URL: `https://example.com/?coords=%s`
- [ ] Export settings
- [ ] Verify exported JSON contains:
  - `"deeplinkType": "custom"`
  - `"deeplinkCustomUrl": "https://example.com/?coords=%s"`
- [ ] Change to iPogo
- [ ] Import the saved settings
- [ ] Verify deeplink reverts to Custom
- [ ] Verify UI shows Custom selected
- [ ] Verify custom URL field shows: `https://example.com/?coords=%s`

### 9. Edge Cases
- [ ] Enter very long custom URL (200+ characters) - verify it saves and works
- [ ] Enter custom URL with special characters: `https://app.com/?q=%s&test=1#anchor`
- [ ] Enter custom URL without %s - verify fallback behavior (appends at end)
- [ ] Enter empty custom URL - verify fallback to iPogo
- [ ] Test with coordinates having many decimal places (e.g., 40.71280123,-74.00601234)
- [ ] Test with negative coordinates
- [ ] Test with coordinates at extremes (0,0), (90,180), (-90,-180)

### 10. Overlay Service
- [ ] Enable overlay
- [ ] Set deeplink to Pokemod
- [ ] Use overlay to teleport to invasion - verify Pokemod URL
- [ ] Use overlay to teleport to quest - verify Pokemod URL
- [ ] Use overlay to teleport to favorite - verify Pokemod URL
- [ ] Use overlay home button - verify Pokemod URL

### 11. Regression Testing
Verify existing functionality still works:
- [ ] GPS Joystick teleport method still works (old hidden setting)
- [ ] All other settings export/import correctly
- [ ] Favorites add/edit/delete works
- [ ] Filters work correctly
- [ ] Data sources work correctly
- [ ] Overlay customization works correctly

## Known Issues / Limitations
Document any issues found during testing:

1. 

2. 

3. 

## Test Results Summary

**Test Date**: ________________

**Tester**: ________________

**Device**: ________________

**App Version**: ________________

**Overall Result**: ☐ Pass  ☐ Fail  ☐ Pass with Issues

**Notes**:


