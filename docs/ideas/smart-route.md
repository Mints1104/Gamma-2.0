# Smart route ("fill the wait") — parked idea

Status: mocked up and approved in principle, not built. Suggested by a user:

> if you're trying to go to something that's an 11min cd, instead of going straight there it could
> path you to other "secondary" tasks along the way

## The catch that shapes the design

The community cooldown charts are concave at range: the cooldown grows more slowly than the
distance (roughly 2 km ≈ 1 min, 10 km ≈ 7–8, 15 km ≈ 11, 25 km ≈ 15, 100 km ≈ 30). Splitting one long
jump into several hops therefore usually adds up to *more* total waiting. A naive "detour along
the way" makes you reach the target later.

So the feature is framed as: **fit in side tasks while the arrival at the target is no more than
N minutes later than going straight there.** N = 0 still works when side tasks lie almost on the
straight line, because short hops have very short cooldowns.

## Design

- **Scope:** quests only at first. Rockets could follow later; mixing quests and grunts in one
  route would cut across the separate Rockets and Quests lists, which is a bigger change.
- **Target:** whatever the overlay's ▶ would pick next today (`nextOverlayIndex` on the
  `questWalk`), so how targets are chosen doesn't change.
- **Side tasks:** quests that match a saved quest filter chosen in Settings ("Side tasks come
  from: <saved filter>"), separate from the live filter. Quests already visited are excluded
  (`questVisitId`).
- **Setting:** "Use smart route" toggle, plus "Arrive at most this much later" (0–10 min). When the
  toggle is off, ▶ behaves exactly as it does today.
- **Cooldown table:** distance (km) → minutes, interpolated linearly. Built in, marked as a community
  estimate; it could be made editable later. The mockup used
  `0:0, 1:1, 2:1, 4:2, 5:2, 6:3, 8:4, 10:7, 12:8, 15:11, 20:13, 25:15, 30:18, 40:22`.
- **Planner:** cheapest insertion. Start with `[current, target]`; repeatedly insert whichever
  candidate, at whichever position, gives the smallest total cooldown while it stays within
  `cooldown(direct) + budget`; stop when nothing fits. There are at most a few hundred candidates,
  so this is fast. It's a pure function in `helpers/`, unit-tested like `OverlayWalk`.
- **Overlay:** ▶ walks the planned route (side tasks, then the target) and shows each hop's
  distance and cooldown. Each stop is recorded like any ▶ today (visited quest,
  `LastLocations`, the walk's history, so ◀ still works). Re-plan after every ▶, because the list
  refreshes and stops disappear.
- **Distances:** use the app's `distanceMeters`.

## Known limits

- The app only knows where you teleported, not what you did there or when, so it can't be sure
  when a cooldown actually started.
- The cooldown charts are unofficial and vary between sources.

## Rough effort

Medium. The planner and cooldown table are small and easy to test. Most of the work is the
side-task filter setting and showing the route and cooldowns in the overlay.
