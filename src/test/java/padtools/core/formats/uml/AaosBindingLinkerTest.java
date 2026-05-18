package padtools.core.formats.uml;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AaosBindingLinker のユニットテスト。
 */
public class AaosBindingLinkerTest {

    @Test
    public void testNullAndEmptyReturnsEmpty() {
        assertTrue(AaosBindingLinker.link(null).isEmpty());
        assertTrue(AaosBindingLinker.link(new ArrayList<>()).isEmpty());
    }

    @Test
    public void testLinksManagerAidlServiceByNaming() {
        // 命名規約: CarFooManager / ICarFoo / CarFooService
        String managerSrc =
                "package android.car.foo;\n"
                        + "public class CarFooManager { ICarFoo mService; }\n";
        String aidlSrc =
                "package android.car.foo;\n"
                        + "interface ICarFoo { void doFoo(); }\n";
        String serviceSrc =
                "package com.android.car;\n"
                        + "public class CarFooService extends ICarFoo.Stub { public void doFoo() {} }\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(managerSrc));
        all.addAll(AidlParser.parse(aidlSrc));
        all.addAll(JavaStructureExtractor.extract(serviceSrc));
        List<AaosBinding> bindings = AaosBindingLinker.link(all);
        assertEquals(1, bindings.size());
        AaosBinding b = bindings.get(0);
        assertEquals("Foo", b.getTopic());
        assertEquals("android.car.foo.CarFooManager", b.getManagerFqn());
        assertEquals("android.car.foo.ICarFoo", b.getAidlFqn());
        assertEquals("com.android.car.CarFooService", b.getServiceFqn());
        // 命名 + フィールド型一致 + Service の implements 一致 → confidence 3
        assertEquals(3, b.getConfidence());
    }

    @Test
    public void testLinksWhenAidlOnly() {
        // Service が見つからないケース: Manager と AIDL のみリンクされる
        String managerSrc =
                "package android.car.foo;\n"
                        + "public class CarFooManager { ICarFoo mService; }\n";
        String aidlSrc =
                "package android.car.foo;\n"
                        + "interface ICarFoo { void doFoo(); }\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(managerSrc));
        all.addAll(AidlParser.parse(aidlSrc));
        List<AaosBinding> bindings = AaosBindingLinker.link(all);
        assertEquals(1, bindings.size());
        AaosBinding b = bindings.get(0);
        assertEquals("Foo", b.getTopic());
        assertNull(b.getServiceFqn());
        assertEquals("android.car.foo.ICarFoo", b.getAidlFqn());
    }

    @Test
    public void testIgnoresClassesOutsideAaosPackages() {
        // 命名規約に一致しても AAOS パッケージ外ならリンクされない
        String managerSrc =
                "package com.example.bar;\n"
                        + "public class CarFooManager { ICarFoo s; }\n";
        String aidlSrc =
                "package com.example.bar;\n"
                        + "interface ICarFoo { }\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(managerSrc));
        all.addAll(AidlParser.parse(aidlSrc));
        assertTrue(AaosBindingLinker.link(all).isEmpty());
    }

    @Test
    public void testMinConfidenceFiltersWeakMatches() {
        // 命名一致だけ (フィールド/implements の裏付けなし) で confidence=1
        String managerSrc =
                "package android.car.foo;\n"
                        + "public class CarBarManager { }\n";
        String aidlSrc =
                "package android.car.foo;\n"
                        + "interface ICarBar { }\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(managerSrc));
        all.addAll(AidlParser.parse(aidlSrc));
        // minConfidence=1 → 採用
        assertEquals(1, AaosBindingLinker.link(all, 1).size());
        // minConfidence=2 → 除外
        assertTrue(AaosBindingLinker.link(all, 2).isEmpty());
    }

    @Test
    public void testHandlesIServiceSuffixVariant() {
        // ICar<Topic>Service という命名も AIDL として認識する
        String managerSrc =
                "package android.car.audio;\n"
                        + "public class CarAudioManager { ICarAudioService mService; }\n";
        String aidlSrc =
                "package android.car.audio;\n"
                        + "interface ICarAudioService { void play(); }\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(managerSrc));
        all.addAll(AidlParser.parse(aidlSrc));
        List<AaosBinding> bindings = AaosBindingLinker.link(all);
        assertEquals(1, bindings.size());
        assertEquals("Audio", bindings.get(0).getTopic());
        assertEquals("android.car.audio.ICarAudioService",
                bindings.get(0).getAidlFqn());
    }

    @Test
    public void testTopicOfManager() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car");
        c.setSimpleName("CarPropertyManager");
        c.setAaosCategory("CarManager");
        assertEquals("Property", AaosBindingLinker.topicOf(c));
    }

    @Test
    public void testTopicOfReturnsNullForNonAaos() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.example");
        c.setSimpleName("CarFooManager");
        c.setAaosCategory("CarManager");
        assertNull(AaosBindingLinker.topicOf(c));
    }

    @Test
    public void testImplementsOrExtendsAcceptsStub() {
        JavaClassInfo c = new JavaClassInfo();
        c.getInterfaces().add("ICarFoo.Stub");
        assertTrue(AaosBindingLinker.implementsOrExtends(c, "ICarFoo"));
    }
}
