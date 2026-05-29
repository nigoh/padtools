// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin → JavaClassInfo ブリッジの単体テスト。
 */
public class KotlinLightScannerTest {

    @Test
    public void parsesSimpleClass() {
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  val name: String = \"\"\n"
                + "  fun greet(): String = \"hi\"\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals("com.x", c.getPackageName());
        assertEquals("Foo", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, c.getKind());
        assertEquals(1, c.getFields().size());
        assertEquals("name", c.getFields().get(0).getName());
        assertEquals("String", c.getFields().get(0).getType());
    }

    @Test
    public void parsesInterface() {
        String src = "package com.x\n"
                + "interface Listener {\n"
                + "  fun onTap(view: View): Unit\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(JavaClassInfo.Kind.INTERFACE, infos.get(0).getKind());
    }

    @Test
    public void parsesObjectAsClass() {
        String src = "package com.x\n"
                + "object Singleton {\n"
                + "  val flag: Boolean = true\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals("Singleton", infos.get(0).getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, infos.get(0).getKind());
    }

    @Test
    public void parsesPrimaryConstructorFields() {
        String src = "package com.x\n"
                + "data class User(val id: Long, val name: String, var email: String)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals(3, c.getFields().size());
        assertEquals("id", c.getFields().get(0).getName());
        assertEquals("Long", c.getFields().get(0).getType());
        assertEquals("name", c.getFields().get(1).getName());
        assertEquals("email", c.getFields().get(2).getName());
    }

    @Test
    public void capturesClassAnnotations() {
        String src = "package com.x\n"
                + "@Entity(tableName = \"users\")\n"
                + "data class User(@PrimaryKey val id: Long, val name: String)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertTrue("Must capture @Entity",
                c.getAnnotations().stream().anyMatch(a -> a.startsWith("@Entity")));
        // Primary constructor parameter annotation
        JavaFieldInfo idField = c.getFields().get(0);
        assertTrue("Must capture @PrimaryKey",
                idField.getAnnotations().stream().anyMatch(a -> a.startsWith("@PrimaryKey")));
    }

    @Test
    public void capturesMethodAnnotations() {
        String src = "package com.x\n"
                + "@Dao\n"
                + "interface UserDao {\n"
                + "  @Query(\"SELECT * FROM users\")\n"
                + "  fun findAll(): List<User>\n"
                + "  @Insert\n"
                + "  fun insert(u: User)\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals(2, c.getMethods().size());
        assertTrue(c.getMethods().get(0).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("@Query")));
        assertTrue(c.getMethods().get(1).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("@Insert")));
    }

    @Test
    public void capturesImports() {
        String src = "package com.x\n"
                + "import androidx.room.Entity\n"
                + "import androidx.room.PrimaryKey\n"
                + "import java.util.*\n"
                + "@Entity\n"
                + "class Foo\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(3, infos.get(0).getImports().size());
        assertTrue(infos.get(0).getImports().contains("androidx.room.Entity"));
        assertTrue(infos.get(0).getImports().contains("java.util.*"));
    }

    @Test
    public void parsesMultipleTopLevelClasses() {
        String src = "package com.x\n"
                + "class A\n"
                + "interface B\n"
                + "data class C(val x: Int)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(3, infos.size());
        assertEquals("A", infos.get(0).getSimpleName());
        assertEquals("B", infos.get(1).getSimpleName());
        assertEquals(JavaClassInfo.Kind.INTERFACE, infos.get(1).getKind());
        assertEquals("C", infos.get(2).getSimpleName());
        assertEquals(1, infos.get(2).getFields().size());
    }

    @Test
    public void parsesFunctionParametersAndReturnType() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun doIt(input: String, count: Int = 1): Boolean {\n"
                + "    return true\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(1, infos.get(0).getMethods().size());
        assertEquals("doIt", infos.get(0).getMethods().get(0).getName());
        assertEquals("Boolean", infos.get(0).getMethods().get(0).getReturnType());
        assertEquals(2, infos.get(0).getMethods().get(0).getParameterTypes().size());
        assertEquals("String", infos.get(0).getMethods().get(0).getParameterTypes().get(0));
        assertEquals("Int", infos.get(0).getMethods().get(0).getParameterTypes().get(1));
    }

    @Test
    public void parsesPackageWithoutSemicolon() {
        String src = "package com.x.y.z\nclass Foo\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals("com.x.y.z", infos.get(0).getPackageName());
    }

    @Test
    public void emptyInputReturnsEmptyList() {
        assertEquals(0, KotlinLightScanner.scan("", ErrorListener.silent()).size());
        assertNotNull(KotlinLightScanner.scan(null, ErrorListener.silent()));
    }
}
