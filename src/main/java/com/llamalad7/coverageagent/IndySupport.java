package com.llamalad7.coverageagent;

import java.lang.invoke.*;

public class IndySupport {
    private static final MethodHandle EMPTY_HANDLE = MethodHandles.empty(MethodType.methodType(void.class, Class.class));
    private static final MethodHandle IMPL_HANDLE;

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        var callSite = new MutableCallSite(type);
        var handle = IMPL_HANDLE.bindTo(callSite);
        callSite.setTarget(handle);
        return callSite;
    }

    private static void impl(MutableCallSite callSite, Class<?> clazz) {
        CoverageAgentAPI.markExecutedClass(clazz);
        callSite.setTarget(EMPTY_HANDLE);
    }

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(IndySupport.class, MethodHandles.lookup());
            IMPL_HANDLE = lookup.findStatic(IndySupport.class, "impl", MethodType.methodType(void.class, MutableCallSite.class, Class.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
