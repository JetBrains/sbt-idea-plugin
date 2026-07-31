// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copied from IntelliJ IDEA Community Edition (commit 7a35a7d7fe64), original package org.jetbrains.jps.devkit.threadingModelHelper.
// The only changes are the package name and the ASM package (org.jetbrains.org.objectweb.asm -> org.objectweb.asm).
package org.jetbrains.sbtidea.instrumentation.threadingModelHelper;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

@ApiStatus.Internal
public interface TMHAssertionGenerator {
  boolean isMyAnnotation(String annotationDescriptor);

  AnnotationVisitor getAnnotationChecker(int api, Runnable onShouldGenerateAssertion);

  void generateAssertion(MethodVisitor writer, int methodStartLineNumber);
}
