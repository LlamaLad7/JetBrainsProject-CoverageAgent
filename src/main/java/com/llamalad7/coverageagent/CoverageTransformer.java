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

class CoverageTransformer implements ClassFileTransformer {
    // We assume these methods are not useful regarding coverage.
    private static final Set<String> EXCLUDED_METHODS = Set.of("<init>", "<clinit>");

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

    private void transformClass(ClassNode node) {
        for (var method : node.methods) {
            if (EXCLUDED_METHODS.contains(method.name) || hasInvisibleAnnotation(method, DoNotTrack.class)) {
                // Don't want to count this method
                continue;
            }
            if (method.instructions.size() == 0) {
                // The method is native or abstract, don't add code to it.
                continue;
            }
            var selfType = Type.getObjectType(node.name);
            method.instructions.insert(new InsnList() {{
                add(new LdcInsnNode(selfType));
                add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(CoverageAgentAPI.class),
                        "markExecutedClass",
                        "(Ljava/lang/Class;)V",
                        false
                ));
            }});
        }
    }

    private boolean hasInvisibleAnnotation(MethodNode method, Class<? extends Annotation> ann) {
        if (method.invisibleAnnotations == null) {
            return false;
        }
        var desc = Type.getDescriptor(ann);
        return method.invisibleAnnotations.stream().anyMatch(it -> it.desc.equals(desc));
    }
}
