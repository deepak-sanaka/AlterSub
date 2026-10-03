# R8 rules for AlterSub release builds.
#
# Nothing here keeps whole packages: the app and NanoHTTPD use no reflection, and the services and
# activities in the manifest are kept automatically. Libraries (OkHttp, coroutines, AndroidX) ship their
# own consumer rules. Add a targeted rule only when a release build is shown to need one.

# Keep line numbers in crash reports, but hide the original source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
