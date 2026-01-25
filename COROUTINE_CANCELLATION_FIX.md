# Coroutine Cancellation Fix - "Failed to Fetch Invasions" Error

## Problem Description

The HomeViewModel (and other ViewModels) were showing intermittent errors like:
- "Failed to fetch invasions: StandaloneCoroutine was cancelled"
- User had to refresh again for it to work

## Root Cause

The issue was in how the ViewModels handled coroutine cancellation:

### What Was Happening:

1. **Multiple Fetch Calls**: When `fetchInvasions()` was called multiple times quickly (e.g., during sort mode changes, pull-to-refresh, or rapid user actions), the code intentionally cancels the previous fetch operation:
   ```kotlin
   fetchJob?.cancel()  // Cancel ongoing fetch
   fetchJob = viewModelScope.launch { ... }  // Start new fetch
   ```

2. **Incorrect Exception Handling**: The catch block was catching ALL exceptions, including `CancellationException`:
   ```kotlin
   } catch (e: Exception) {
       e(TAG, "Error fetching invasions: ${e.message}", e)
       _error.value = "Failed to fetch invasions: ${e.message}"  // ❌ Wrong!
   }
   ```

3. **CancellationException is Normal**: In Kotlin coroutines, when a coroutine is cancelled, it throws a `CancellationException`. This is **normal cooperative cancellation** and should NOT be treated as an error.

### Why This Caused Issues:

- When the user triggered a refresh while a previous fetch was still running, the old coroutine was cancelled
- The catch block treated this cancellation as an error
- User saw "Failed to fetch invasions: StandaloneCoroutine was cancelled" 
- The new fetch might succeed, but the error message was already shown
- On subsequent refreshes, the timing was different, so it sometimes worked

## Solution

Handle `CancellationException` separately in all ViewModels to avoid treating normal coroutine cancellation as an error:

```kotlin
import kotlinx.coroutines.CancellationException  // Import added

// In the catch block:
} catch (e: CancellationException) {
    // Coroutine was cancelled - this is normal behavior, don't treat it as an error
    Log.d(TAG, "Fetch invasions was cancelled")
    throw e // Re-throw to properly propagate cancellation
} catch (e: Exception) {
    e(TAG, "Error fetching invasions: ${e.message}", e)
    _error.value = "Failed to fetch invasions: ${e.message}"
}
```

### Key Points:

1. **Catch CancellationException First**: Order matters - catch it before the general `Exception` catch
2. **Don't Show Error**: Cancellation is not an error, so don't set `_error.value`
3. **Re-throw**: Re-throw the exception to properly propagate cancellation up the coroutine hierarchy
4. **Log for Debugging**: Still log it at debug level for troubleshooting

## Files Fixed

1. ✅ `HomeViewModel.kt` - Fixed in `fetchInvasions()`
2. ✅ `RaidsViewModel.kt` - Fixed in `fetchRaids()`
3. ✅ `QuestsViewModel.kt` - Fixed in `fetchQuests()` and `fetchSpindaFormsFromApi()`

## Testing Recommendations

Test these scenarios to verify the fix:

1. **Rapid Refresh**: Pull to refresh multiple times quickly
2. **Sort Mode Changes**: Switch between "Sort by Distance" and "Sort by Time" rapidly
3. **Navigation**: Navigate away from HomeFragment and back quickly
4. **Background/Foreground**: Switch apps while data is loading

Expected behavior: No more "coroutine was cancelled" error messages

## Best Practices for Coroutine Exception Handling

**Always handle CancellationException separately:**

```kotlin
viewModelScope.launch {
    try {
        // Your code
    } catch (e: CancellationException) {
        // Log and re-throw - don't treat as error
        Log.d(TAG, "Operation was cancelled")
        throw e
    } catch (e: SpecificException) {
        // Handle specific exceptions
    } catch (e: Exception) {
        // Handle general exceptions
    }
}
```

## References

- [Kotlin Coroutines - Cancellation and Timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html)
- [Android Developers - Coroutines Best Practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
