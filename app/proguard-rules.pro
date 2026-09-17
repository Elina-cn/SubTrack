# R8 rules for the release build. isMinifyEnabled and isShrinkResources are both on; see
# ARCHITECTURE section 24 for the measurements this file rests on.
#
# This file is deliberately empty of -keep rules.
#
# Room, Hilt, WorkManager, DataStore, Navigation, Compose and the coroutines runtime all ship
# their own rules inside their AAR/JAR (META-INF/proguard, META-INF/com.android.tools/r8), and
# R8 merges them into the configuration. The merged set is written to
# build/outputs/mapping/release/configuration.txt, which lists 70-odd rule sources.
#
# The build was first run with no project rules at all and the resulting release APK was driven
# on API 24, 29, 34 and 36: database open/insert/update/delete, the Hilt graph, DataStore
# preferences, the HiltWorker reminder job and its notification, all navigation destinations,
# java.time through desugaring and locale-aware money formatting. Nothing broke, so nothing is
# kept here. A preventive rule would only be a dead rule nobody dares delete later.
#
# If a rule ever becomes necessary, write it here with the failure it fixes in a comment above
# it - the stack trace, not a guess.
