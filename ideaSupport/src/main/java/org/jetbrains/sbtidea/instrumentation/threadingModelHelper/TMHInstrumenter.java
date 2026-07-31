// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copied from IntelliJ IDEA Community Edition (commit 7a35a7d7fe64), original package org.jetbrains.jps.devkit.threadingModelHelper.
// The only changes are the package name, the ASM package (org.jetbrains.org.objectweb.asm -> org.objectweb.asm),
// Opcodes.API_VERSION (a JetBrains ASM addition) -> Opcodes.ASM9 and the import of FailSafeMethodVisitor
// (originally com.intellij.compiler.instrumentation.FailSafeMethodVisitor).
package org.jetbrains.sbtidea.instrumentation.threadingModelHelper;

import org.jetbrains.sbtidea.instrumentation.notNullVerification.FailSafeMethodVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TMHInstrumenter {
  public static boolean instrument(ClassReader classReader,
                                   ClassVisitor classWriter,
                                   Set<? extends TMHAssertionGenerator> generators,
                                   boolean generateLineNumbers) {
    AnnotatedMethodsCollector collector = new AnnotatedMethodsCollector(generators);
    int options = ClassReader.SKIP_FRAMES;
    if (!generateLineNumbers) {
      options |= ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG;
    }
    classReader.accept(collector, options);
    if (collector.annotatedMethods.isEmpty()) {
      return false;
    }
    Instrumenter instrumenter = new Instrumenter(classWriter, collector.annotatedMethods);
    classReader.accept(instrumenter, 0);
    return true;
  }

  private static final class AnnotatedMethodsCollector extends ClassVisitor {
    final Set<? extends TMHAssertionGenerator> assertionGenerators;
    final Map<MethodKey, InstrumentationInfo> annotatedMethods = new HashMap<>();

    AnnotatedMethodsCollector(Set<? extends TMHAssertionGenerator> assertionGenerators) {
      super(Opcodes.ASM9);
      this.assertionGenerators = assertionGenerators;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String name, final String methodDescriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM9) {
        private final MethodKey methodKey = new MethodKey(name, methodDescriptor);
        private boolean annotated = false;
        private boolean firstLineNumberVisited = false;

        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          for (TMHAssertionGenerator assertionGenerator : assertionGenerators) {
            if (assertionGenerator.isMyAnnotation(annotationDescriptor)) {
              return assertionGenerator.getAnnotationChecker(Opcodes.ASM9, () -> {
                annotatedMethods.put(methodKey, new InstrumentationInfo(assertionGenerator));
                annotated = true;
              });
            }
          }
          return super.visitAnnotation(annotationDescriptor, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
          super.visitLineNumber(line, start);
          if (annotated && !firstLineNumberVisited) {
            annotatedMethods.get(methodKey).methodStartLineNumber = line;
            firstLineNumberVisited = true;
          }
        }
      };
    }
  }

  private static final class Instrumenter extends ClassVisitor {
    private final Map<MethodKey, InstrumentationInfo> myAnnotatedMethods;

    Instrumenter(ClassVisitor writer, Map<MethodKey, InstrumentationInfo> annotatedMethods) {
      super(Opcodes.ASM9, writer);
      myAnnotatedMethods = annotatedMethods;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      InstrumentationInfo instrumentationInfo = myAnnotatedMethods.get(new MethodKey(name, descriptor));
      if (instrumentationInfo == null) {
        return super.visitMethod(access, name, descriptor, signature, exceptions);
      }
      return new FailSafeMethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
        @Override
        public void visitCode() {
          instrumentationInfo.assertionGenerator.generateAssertion(mv, instrumentationInfo.methodStartLineNumber);
          super.visitCode();
        }
      };
    }
  }

  private static final class MethodKey {
    final String name;
    final String descriptor;

    private MethodKey(String name, String descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + name.hashCode();
      result = 31 * result + descriptor.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this ||
             obj instanceof MethodKey && ((MethodKey)obj).name.equals(name) && ((MethodKey)obj).descriptor.equals(descriptor);
    }
  }

  private static final class InstrumentationInfo {
    final TMHAssertionGenerator assertionGenerator;
    int methodStartLineNumber = -1;

    private InstrumentationInfo(TMHAssertionGenerator generator) {assertionGenerator = generator;}
  }
}
