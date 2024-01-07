package com.llamalad7.coverageagent;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.Set;

class CoverageTransformer implements ClassFileTransformer {
    // We assume these methods are not useful regarding coverage.
    private static final Set<String> EXCLUDED_METHODS = Set.of("<init>", "<clinit>");

    // This isn't a legal identifier in Java or Kotlin, but the JVM is fine with it.
    // This ensures it won't overlap with any existing members.
    private static final String NOTIFY_METHOD = "\\coverageAgent$notify";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (loader == null || classfileBuffer == null || !CoverageAgent.shouldVisitClass(className)) {
            return null;
        }
        var node = new ClassNode();
        new ClassReader(classfileBuffer).accept(node, 0);
        transformClass(node);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private void transformClass(ClassNode clazz) {
        if (clazz.version < Opcodes.V1_7) {
            // We need INDY!
            clazz.version = Opcodes.V1_7;
        }
        var isInterface = (clazz.access & Opcodes.ACC_INTERFACE) != 0;
        if (isInterface && clazz.version < Opcodes.V1_8) {
            // Interface methods cannot have bodies before Java 8, so skip this class.
            return;
        }
        for (var method : clazz.methods) {
            if (EXCLUDED_METHODS.contains(method.name) || hasInvisibleAnnotation(method, DoNotTrack.class)) {
                // Don't want to count this method.
                continue;
            }
            if (method.instructions.size() == 0) {
                // The method is native or abstract, don't add code to it.
                continue;
            }

            // Call the notify method at the start.
            method.instructions.insert(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            clazz.name,
                            NOTIFY_METHOD,
                            "()V",
                            isInterface
                    )
            );
        }
        makeNotifyMethod(clazz);
    }

    private static void makeNotifyMethod(ClassNode clazz) {
        // This will notify the agent of our class, if we haven't done so already.
        var method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, // public for simplicity when dealing with interfaces compiled with old java versions
                NOTIFY_METHOD,
                "()V",
                null,
                null
        );
        var selfType = Type.getObjectType(clazz.name);
        method.instructions = new InsnList() {{
            add(new LdcInsnNode(selfType));
            add(new InvokeDynamicInsnNode(
                    "notify",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Class.class)),
                    new Handle(
                            Opcodes.H_INVOKESTATIC,
                            Type.getInternalName(IndySupport.class),
                            "bootstrap",
                            Type.getMethodDescriptor(
                                    Type.getType(CallSite.class),
                                    Type.getType(MethodHandles.Lookup.class),
                                    Type.getType(String.class),
                                    Type.getType(MethodType.class)
                            ),
                            false
                    )
            ));
            add(new InsnNode(Opcodes.RETURN));
        }};
        clazz.methods.add(method);
    }

    private boolean hasInvisibleAnnotation(MethodNode method, Class<? extends Annotation> ann) {
        if (method.invisibleAnnotations == null) {
            return false;
        }
        var desc = Type.getDescriptor(ann);
        return method.invisibleAnnotations.stream().anyMatch(it -> it.desc.equals(desc));
    }
}
