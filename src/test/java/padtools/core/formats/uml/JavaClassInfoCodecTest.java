package padtools.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * JavaClassInfoCodec の往復テスト。
 */
public class JavaClassInfoCodecTest {

    @Test
    public void testRoundTripMinimal() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.foo");
        c.setSimpleName("Bar");
        c.setKind(JavaClassInfo.Kind.CLASS);

        String enc = JavaClassInfoCodec.encodeHeader(c);
        JavaClassInfo back = JavaClassInfoCodec.decodeHeader(enc);
        assertNotNull(back);
        assertEquals("com.foo", back.getPackageName());
        assertEquals("Bar", back.getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, back.getKind());
        assertFalse("復元時は detailed=false", back.isDetailed());
    }

    @Test
    public void testRoundTripWithLists() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("p");
        c.setSimpleName("X");
        c.setKind(JavaClassInfo.Kind.INTERFACE);
        c.setSuperClass("Y");
        c.getInterfaces().add("I1");
        c.getInterfaces().add("I2");
        c.getModifiers().add("public");
        c.getModifiers().add("abstract");
        c.getAnnotations().add("Deprecated");
        c.setAaosCategory("CarManager");
        c.setAndroidComponentType("Activity");
        c.setEnclosingClass("Outer");

        String enc = JavaClassInfoCodec.encodeHeader(c);
        JavaClassInfo back = JavaClassInfoCodec.decodeHeader(enc);
        assertEquals("Y", back.getSuperClass());
        assertEquals(2, back.getInterfaces().size());
        assertEquals("I1", back.getInterfaces().get(0));
        assertEquals("I2", back.getInterfaces().get(1));
        assertEquals(2, back.getModifiers().size());
        assertEquals("CarManager", back.getAaosCategory());
        assertEquals("Activity", back.getAndroidComponentType());
        assertEquals("Outer", back.getEnclosingClass());
        assertEquals(JavaClassInfo.Kind.INTERFACE, back.getKind());
        assertEquals("p.Outer.X", back.getQualifiedName());
    }

    @Test
    public void testRoundTripWithSpecialChars() {
        // TAB / 改行 / カンマ / バックスラッシュを含む値が壊れないこと
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("p");
        c.setSimpleName("Wei\trd"); // simpleName に TAB
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("Note\\(\"hi\")");
        c.getAnnotations().add("Has,Comma");

        String enc = JavaClassInfoCodec.encodeHeader(c);
        JavaClassInfo back = JavaClassInfoCodec.decodeHeader(enc);
        assertEquals("Wei\trd", back.getSimpleName());
        assertEquals(2, back.getAnnotations().size());
        assertEquals("Note\\(\"hi\")", back.getAnnotations().get(0));
        assertEquals("Has,Comma", back.getAnnotations().get(1));
    }

    @Test
    public void testDecodeNullOrEmpty() {
        assertEquals(null, JavaClassInfoCodec.decodeHeader(null));
        assertEquals(null, JavaClassInfoCodec.decodeHeader(""));
    }
}
