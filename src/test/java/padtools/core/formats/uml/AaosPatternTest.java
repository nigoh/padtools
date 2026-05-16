package padtools.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AaosPattern のユニットテスト。
 */
public class AaosPatternTest {

    private static JavaClassInfo make(String pkg, String name, JavaClassInfo.Kind kind) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(kind);
        return c;
    }

    @Test
    public void testCarManagerPattern() {
        JavaClassInfo c = make("android.car.audio", "CarAudioManager",
                JavaClassInfo.Kind.CLASS);
        assertEquals("CarManager", AaosPattern.categorize(c));
    }

    @Test
    public void testCarServicePattern() {
        JavaClassInfo c = make("com.android.car", "CarAudioService",
                JavaClassInfo.Kind.CLASS);
        assertEquals("CarService", AaosPattern.categorize(c));
    }

    @Test
    public void testAidlInterfacePattern() {
        JavaClassInfo c = make("android.car", "ICarAudio",
                JavaClassInfo.Kind.INTERFACE);
        assertEquals("ICarInterface", AaosPattern.categorize(c));
    }

    @Test
    public void testAidlKindAlwaysCategorized() {
        JavaClassInfo c = make("com.x", "Anything",
                JavaClassInfo.Kind.AIDL_INTERFACE);
        assertEquals("AIDL", AaosPattern.categorize(c));
    }

    @Test
    public void testNonAaosPackage() {
        JavaClassInfo c = make("com.example.foo", "CarThingManager",
                JavaClassInfo.Kind.CLASS);
        // パッケージが AAOS じゃないので CarManager 分類されない
        assertNull(AaosPattern.categorize(c));
    }

    @Test
    public void testAaosApiAnnotationDetection() {
        JavaClassInfo c = make("android.car", "Helper", JavaClassInfo.Kind.CLASS);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("foo");
        m.getAnnotations().add("AddedIn(majorVersion=33)");
        c.getMethods().add(m);
        assertEquals("AaosApi", AaosPattern.categorize(c));
    }

    @Test
    public void testIsAaosApiAnnotation() {
        assertTrue(AaosPattern.isAaosApiAnnotation("AddedIn"));
        assertTrue(AaosPattern.isAaosApiAnnotation("AddedIn(majorVersion=33)"));
        assertTrue(AaosPattern.isAaosApiAnnotation("ApiRequirements"));
        assertTrue(AaosPattern.isAaosApiAnnotation("android.car.annotation.AddedIn"));
        assertFalse(AaosPattern.isAaosApiAnnotation("Override"));
        assertFalse(AaosPattern.isAaosApiAnnotation(""));
        assertFalse(AaosPattern.isAaosApiAnnotation(null));
    }

    @Test
    public void testIsInAaosPackage() {
        assertTrue(AaosPattern.isInAaosPackage("android.car"));
        assertTrue(AaosPattern.isInAaosPackage("android.car.audio"));
        assertTrue(AaosPattern.isInAaosPackage("com.android.car.internal"));
        assertFalse(AaosPattern.isInAaosPackage("com.example"));
        assertFalse(AaosPattern.isInAaosPackage(""));
        assertFalse(AaosPattern.isInAaosPackage(null));
    }

    @Test
    public void testNullClassInfo() {
        assertNull(AaosPattern.categorize(null));
    }
}
