# Add project specific ProGuard rules here.

# Ktor's IntelliJ-debugger detection (io.ktor.util.debug) references desktop-JVM-only
# java.lang.management classes that don't exist on Android; that code path never runs there.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
