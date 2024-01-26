/*
 * Copyright (C) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.testing.compile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.REDUNDANT_OPTIONS_ERROR_TEMPLATE;
import static com.google.testing.compile.Compiler.javac;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Enclosed.class)
public class EnclosedCompilationTest {

  private static final JavaFileObject source1 =
      JavaFileObjects.forSourceLines(
          "test.Source1", // format one per line
          "package test;",
          "",
          "class Source1 {}");

  private static final JavaFileObject source2 =
      JavaFileObjects.forSourceLines(
          "test.Source2", // format one per line
          "package test;",
          "",
          "interface Source2 {}");

  private static final JavaFileObject brokenSource =
      JavaFileObjects.forSourceLines(
          "test.BrokenSource", // format one per line
          "package test;",
          "",
          "interface BrokenSource { what is this }");

  @RunWith(JUnit4.class)
  public static class CompilationTest {

    private static Compiler compilerWithGenerator() {
      return javac().withProcessors(new GeneratingProcessor("test.generated"));
    }

    @Test
    public void compiler() {
      Compiler compiler = compilerWithGenerator();
      Compilation compilation = compiler.compile(source1, source2);
      assertThat(compilation.compiler()).isEqualTo(compiler);
      assertThat(compilation.sourceFiles()).containsExactly(source1, source2).inOrder();
      assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
    }

    @Test
    public void compilerStatusFailure() {
      Compiler compiler = compilerWithGenerator();
      Compilation compilation = compiler.compile(brokenSource);
      assertThat(compilation.status()).isEqualTo(Compilation.Status.FAILURE);
      assertThat(compilation.errors()).hasSize(1);
      assertThat(compilation.errors().get(0).getLineNumber()).isEqualTo(3);
    }

    @Test
    public void generatedFilePath() {
      Compiler compiler = compilerWithGenerator();
      Compilation compilation = compiler.compile(source1, source2);
      assertThat(compilation.generatedFile(SOURCE_OUTPUT, "test/generated/Blah.java")).isPresent();
    }

    @Test
    public void generatedFilePackage() {
      Compiler compiler = compilerWithGenerator();
      Compilation compilation = compiler.compile(source1, source2);
      assertThat(
          compilation.generatedFile(SOURCE_OUTPUT, "test.generated", "Blah.java")).isPresent();
    }

    @Test
    public void generatedSourceFile() {
      Compiler compiler = compilerWithGenerator();
      Compilation compilation = compiler.compile(source1, source2);
      assertThat(compilation.generatedSourceFile("test.generated.Blah")).isPresent();
    }

    @Test
    public void generatedFiles_unsuccessfulCompilationThrows() {
      Compilation compilation =
          javac()
              .compile(
                  JavaFileObjects.forSourceLines(
                      "test.Bad", "package test;", "", "this doesn't compile!"));
      assertThat(compilation).failed();
      try {
        ImmutableList<JavaFileObject> unused = compilation.generatedFiles();
        fail();
      } catch (IllegalStateException expected) {
      }
    }

    @Test
    public void describeFailureDiagnostics_includesWarnings_whenCompilingWerror() {
      // Arrange
      Compiler compiler = javac().withOptions("-Xlint:cast", "-Werror");
      JavaFileObject source =
          JavaFileObjects.forSourceLines(
              "test.CastWarning", //
              "package test;", //
              "class CastWarning {", //
              "  int i = (int) 0;", //
              "}");

      // Act
      Compilation compilation = compiler.compile(source);

      // Assert
      assertThat(compilation).failed();
      assertThat(compilation.describeFailureDiagnostics()).contains("[cast] redundant cast to int");
    }
  }

  @RunWith(Parameterized.class)
  public static class RedundantOptionsTest {

    @Parameter
    public ImmutableList<String> redundantOptions;

    @Parameterized.Parameters
    public static Iterable<Object[]> parameters() {
      return ImmutableList.of(
          new Object[]{ImmutableList.of("--add-exports=val/val=val")},
          new Object[]{ImmutableList.of("--add-reads=val/val=val")},
          new Object[]{ImmutableList.of("--add-opens=val/val=val")},
          new Object[]{ImmutableList.of("--add-opens=val/val=val")},
          new Object[]{ImmutableList.of(
              "--add-opens=val/val=val",
              "--add-exports=val/val=val",
              "--add-reads=val/val=val")}
      );
    }

    @Test
    public void failsOnRedundantOptions() {
      ImmutableList<String> options = ImmutableList.<String>builder()
          .add("-Xlint:all")
          .addAll(redundantOptions)
          .build();
      Compiler compiler = javac().withOptions(options);
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> compiler.compile(source1, source2));
      assertThat(exception).hasMessageThat()
          .contains(String.format(REDUNDANT_OPTIONS_ERROR_TEMPLATE, redundantOptions));
    }

    @Test
    public void respectsIgnoreRedundantOptionsCheckFlag() {
      Compiler compiler = javac().withOptions(redundantOptions).ignoreRedundantOptionsCheck();
      Compilation compilation = compiler.compile(source1, source2);
      assertThat(compilation).succeeded();
    }
  }

}