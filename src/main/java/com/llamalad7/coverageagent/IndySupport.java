package com.llamalad7.coverageagent;

import java.lang.invoke.*;

/**
 * Used by the generated `notify` methods in each monitored class.
 */
public class IndySupport {
    private static final MethodHandle EMPTY_HANDLE = MethodHandles.empty(MethodType.methodType(void.class, Class.class));
    private static final MethodHandle IMPL_HANDLE;

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        var callSite = new MutableCallSite(type);
        // Passing the call-site itself allows us to rebind it later.
        var handle = IMPL_HANDLE.bindTo(callSite);
        callSite.setTarget(handle);
        return callSite;
    }

    /**
     * This is what each call-site will initially refer to. We notify the API of the newly executed class, and then
     * bind the call-site to an empty handle, so it does nothing on subsequent executions. In especially hot code,
     * the JIT should realise that the call-site is now an unreachable object, and inline its now empty handler,
     * removing any possible performance penalty.
     */
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
