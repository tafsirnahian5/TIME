# T!ME release rules (Phase 16).
#
# No renaming at all: enum constant names are what Room's converters, the serialized recurrence
# and habit schedules, DataStore settings and backups store, so an obfuscated name would no
# longer match data written by an earlier build. Shrinking and optimisation stay on.
-dontobfuscate

# Belt and braces for the same reason, should obfuscation ever be turned back on.
-keepclassmembers enum com.example.timeapp.** { *; }

# Room-generated database implementation is found by reflection-free codegen, but keep the
# entities' field names readable in crash reports.
-keep class com.example.timeapp.data.db.** { *; }
