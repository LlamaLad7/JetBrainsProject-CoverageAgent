package com.llamalad7.coverageagent;

import java.lang.instrument.Instrumentation;

class CoverageAgent {
    private static String packagePrefix;

    public static void premain(String args, Instrumentation instrumentation) {
        packagePrefix = args;
        instrumentation.addTransformer(new CoverageTransformer());
    }

    static boolean shouldVisitClass(String name) {
        return name.startsWith(packagePrefix);
    }
}
