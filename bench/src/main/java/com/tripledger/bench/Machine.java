package com.tripledger.bench;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

// "machine stated": what the numbers were measured on, so nobody compares them with a server blindly
record Machine(String os, String cpu, int cores, long ramBytes, String java, long maxHeapBytes) {

    static Machine describe() {
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
        String java = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");
        long ram = 0;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os2) {
            ram = os2.getTotalMemorySize();
        }
        return new Machine(os, cpuName(), Runtime.getRuntime().availableProcessors(), ram, java,
                Runtime.getRuntime().maxMemory());
    }

    // Windows puts a CPU description in the environment, Linux in /proc/cpuinfo; anything else: unknown
    private static String cpuName() {
        String windows = System.getenv("PROCESSOR_IDENTIFIER");
        if (windows != null && !windows.isBlank()) {
            return windows.trim();
        }
        try {
            Path cpuinfo = Path.of("/proc/cpuinfo");
            if (Files.exists(cpuinfo)) {
                for (String line : Files.readAllLines(cpuinfo)) {
                    if (line.startsWith("model name")) {
                        return line.substring(line.indexOf(':') + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "unknown";
    }
}
