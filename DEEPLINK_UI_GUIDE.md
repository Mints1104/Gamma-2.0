# Deeplink Settings UI Guide

## Settings Screen - Deeplink Section

The new deeplink section appears in the Settings tab with the following layout:

```
┌─────────────────────────────────────────┐
│  Settings                                │
├─────────────────────────────────────────┤
│                                          │
│  ... (existing settings)                 │
│                                          │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                          │
│  Deeplink Method                         │
│                                          │
│  ○ iPogo                                 │
│  ○ Pokemod                               │
│  ○ Custom                                │
│                                          │
│  [When Custom is selected:]              │
│  ┌────────────────────────────────────┐ │
│  │ Enter custom URL (use %s for      │ │
│  │ coordinates)                       │ │
│  └────────────────────────────────────┘ │
│                                          │
│  Example: https://example.com/?coords=%s │
│                                          │
└─────────────────────────────────────────┘
```

## Behavior

### When "iPogo" is selected (DEFAULT):
- Custom URL input field is HIDDEN
- Teleport buttons will use: `https://ipogo.app/?coords=LAT,LNG`
- Example: `https://ipogo.app/?coords=40.7128,-74.0060`

### When "Pokemod" is selected:
- Custom URL input field is HIDDEN
- Teleport buttons will use: `https://pk.md/LAT,LNG`
- Example: `https://pk.md/40.7128,-74.0060`

### When "Custom" is selected:
- Custom URL input field is VISIBLE
- Example text is VISIBLE
- User enters URL template with `%s` placeholder
- Teleport buttons will use the custom template
- Example input: `https://myspoofer.com/goto?coords=%s`
- Generated URL: `https://myspoofer.com/goto?coords=40.7128,-74.0060`

## Validation

When user enters a custom URL and loses focus on the input field:
- ✅ If URL contains `%s`: Shows "Custom deeplink URL saved" toast
- ❌ If URL does NOT contain `%s`: Shows warning toast but still saves (will append coords at end)
- 🔄 If URL is empty: Falls back to iPogo format

## Where It's Used

The selected deeplink method is used in ALL teleport buttons throughout the app:

1. **Home Tab (Invasions)**
   - Main list teleport button
   - Overlay teleport button

2. **Quests Tab**
   - Quest list teleport button
   - Overlay teleport button

3. **Raids Tab** (if applicable)
   - Raid list teleport button

4. **Favorites Tab**
   - Favorites list teleport button
   - Overlay favorites teleport button

5. **Journal/History**
   - Deleted invasions teleport button
   - Deleted quests teleport button

6. **Home Coordinates**
   - Teleport to home button

## Import/Export

When exporting settings, the deeplink configuration is included:
```json
{
  "deeplinkType": "custom",
  "deeplinkCustomUrl": "https://myapp.com/?coords=%s",
  ...other settings...
}
```

When importing settings:
- Deeplink preferences are restored
- UI radio buttons update automatically
- If "custom" type, the custom URL field is shown and populated
