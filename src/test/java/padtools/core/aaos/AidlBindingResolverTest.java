// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.aaos;

import org.junit.Test;
import padtools.core.formats.uml.AidlParser;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaStructureExtractor;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AidlBindingResolver} の AIDL → 実装紐付け検証。
 */
public class AidlBindingResolverTest {

    private static List<JavaClassInfo> mixed(String aidl, String... javaSources) {
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(AidlParser.parse(aidl, ErrorListener.silent()));
        for (String s : javaSources) {
            all.addAll(JavaStructureExtractor.extract(s, ErrorListener.silent()));
        }
        return all;
    }

    @Test
    public void resolvesSimpleNameStubExtension() {
        String aidl = "package com.x;\n"
                + "interface ICarFoo {\n"
                + "  void doIt();\n"
                + "}\n";
        String impl = "package com.x;\n"
                + "public class CarFooService extends ICarFoo.Stub {\n"
                + "  public void doIt() {}\n"
                + "}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        List<AidlBinding> impls = bindings.get("com.x.ICarFoo");
        assertNotNull(impls);
        assertEquals(1, impls.size());
        assertEquals("com.x.CarFooService", impls.get(0).getImplementationFqn());
    }

    @Test
    public void unboundAidlReportedWithEmptyList() {
        String aidl = "package com.x;\n"
                + "interface ICarBar { void f(); }\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl));
        assertTrue(bindings.containsKey("com.x.ICarBar"));
        assertEquals(0, bindings.get("com.x.ICarBar").size());
    }

    @Test
    public void resolvesViaImportFqn() {
        String aidl = "package com.x.car;\n"
                + "interface ICarHvac { void f(); }\n";
        String impl = "package com.svc;\n"
                + "import com.x.car.ICarHvac;\n"
                + "public class HvacService extends ICarHvac.Stub {\n"
                + "  public void f() {}\n"
                + "}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        List<AidlBinding> impls = bindings.get("com.x.car.ICarHvac");
        assertNotNull(impls);
        assertEquals(1, impls.size());
        assertEquals("com.svc.HvacService", impls.get(0).getImplementationFqn());
    }

    @Test
    public void markdownReportContainsBindingRow() {
        String aidl = "package com.x;\n"
                + "interface ICarFoo { void doIt(); }\n";
        String impl = "package com.x;\n"
                + "public class CarFooService extends ICarFoo.Stub {}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        String md = MarkdownAidlBindingReport.render(bindings);
        assertTrue(md.contains("ICarFoo"));
        assertTrue(md.contains("CarFooService"));
        assertTrue(md.contains("AIDL"));
    }
}
