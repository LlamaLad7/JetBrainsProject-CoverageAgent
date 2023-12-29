package com.llamalad7.coverageagent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class CoverageTransformer implements ClassFileTransformer {
    // We assume these methods are not useful regarding coverage.
    private static final Set<String> EXCLUDED_METHODS = Set.of("<init>", "<clinit>");

    // These aren't legal identifiers in Java or Kotlin, but the JVM is fine with them.
    // This ensures they won't overlap with any existing members, and as a bonus, they seem to be invisible to
    // reflection.
    private static final String NOTIFIED_FIELD = "\\coverageAgent$notified";
    private static final String NOTIFY_METHOD = "\\coverageAgent$notify";

    private static final Type ATOMIC_BOOLEAN = Type.getType(AtomicBoolean.class);

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
        var isInterface = (clazz.access & Opcodes.ACC_INTERFACE) != 0;
        if (isInterface && clazz.version < Opcodes.V1_8) {
            // Interface methods cannot have bodies before Java 8, so skip this class.
            return;
        }
        var clinitFound = false;
        for (var method : clazz.methods) {
            if (method.name.equals("<clinit>")) {
                initializeNotified(clazz, method);
                clinitFound = true;
            }
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
        if (!clinitFound) {
            // We have to create it ourselves.
            var clinit = new MethodNode(
                    Opcodes.ACC_STATIC,
                    "<clinit>",
                    "()V",
                    null,
                    null
            );
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            initializeNotified(clazz, clinit);
            clazz.methods.add(clinit);
        }
        makeNotifyMethod(clazz);
    }

    private static void initializeNotified(ClassNode clazz, MethodNode clinit) {
        // This will store whether we have already notified the agent of our class's execution.
        clazz.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                NOTIFIED_FIELD,
                ATOMIC_BOOLEAN.getDescriptor(),
                null,
                null
        ));
        // Equivalent to `coverageAgent$notified = new AtomicBoolean();`
        clinit.instructions.insert(new InsnList() {{
            add(new TypeInsnNode(Opcodes.NEW, ATOMIC_BOOLEAN.getInternalName()));
            add(new InsnNode(Opcodes.DUP));
            add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    ATOMIC_BOOLEAN.getInternalName(),
                    "<init>",
                    "()V",
                    false
            ));
            add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    clazz.name,
                    NOTIFIED_FIELD,
                    ATOMIC_BOOLEAN.getDescriptor()
            ));
        }});
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
        // Equivalent to `if (!coverageAgent$notified.getAndSet(true)) CoverageAgentAPI.markExecutedClass(OurClass.class);`
        method.instructions = new InsnList() {{
            var after = new LabelNode();
            add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    clazz.name,
                    NOTIFIED_FIELD,
                    ATOMIC_BOOLEAN.getDescriptor()
            ));
            add(new InsnNode(Opcodes.ICONST_1));
            add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    ATOMIC_BOOLEAN.getInternalName(),
                    "getAndSet",
                    "(Z)Z",
                    false
            ));
            add(new JumpInsnNode(Opcodes.IFNE, after));
            add(new LdcInsnNode(selfType));
            add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(CoverageAgentAPI.class),
                    "markExecutedClass",
                    "(Ljava/lang/Class;)V",
                    false
            ));
            add(after);
            // Unfortunately we need to add this frame ourselves, since COMPUTE_FRAMES would cause a lot of random
            // and potentially dangerous classloading.
            add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
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
