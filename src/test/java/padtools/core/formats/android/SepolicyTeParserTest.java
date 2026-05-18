package padtools.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SepolicyTeParser のユニットテスト。
 */
public class SepolicyTeParserTest {

    @Test
    public void testEmpty() {
        SepolicyInfo info = SepolicyTeParser.parse("", "carservice.te");
        assertTrue(info.getTypes().isEmpty());
        assertTrue(info.getAllowRules().isEmpty());
        assertTrue(info.getNeverallowRules().isEmpty());
        assertTrue(info.getTransitions().isEmpty());
    }

    @Test
    public void testTypeDeclaration() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "type carservice, domain;\n"
                        + "type vehicle_hal_server, domain, mlstrustedsubject;\n",
                "carservice.te");
        assertEquals(2, info.getTypes().size());
        assertEquals("carservice", info.getTypes().get(0).getName());
        assertTrue(info.getTypes().get(0).isDomain());
        assertEquals(2, info.getTypes().get(1).getAttributes().size());
        assertTrue(info.getTypes().get(1).getAttributes().contains("mlstrustedsubject"));
    }

    @Test
    public void testSingleAllowRule() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "allow carservice vehicle_hal_server:binder call;\n",
                "carservice.te");
        assertEquals(1, info.getAllowRules().size());
        SepolicyRule r = info.getAllowRules().get(0);
        assertEquals("allow", r.getRuleType());
        assertEquals("carservice", r.getSourceType());
        assertEquals("vehicle_hal_server", r.getTargetType());
        assertEquals("binder", r.getObjectClass());
        assertEquals(1, r.getPermissions().size());
        assertEquals("call", r.getPermissions().get(0));
    }

    @Test
    public void testAllowWithBracePermissions() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "allow carservice vehicle_hal_server:binder { call transfer };\n",
                "carservice.te");
        SepolicyRule r = info.getAllowRules().get(0);
        assertEquals(2, r.getPermissions().size());
        assertTrue(r.getPermissions().contains("call"));
        assertTrue(r.getPermissions().contains("transfer"));
    }

    @Test
    public void testNeverallowRule() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "neverallow untrusted_app vehicle_hal_server:binder call;\n",
                "carservice.te");
        assertEquals(0, info.getAllowRules().size());
        assertEquals(1, info.getNeverallowRules().size());
        assertEquals("neverallow", info.getNeverallowRules().get(0).getRuleType());
    }

    @Test
    public void testTypeTransition() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "type_transition init bootanim_exec:process bootanim;\n",
                "init.te");
        assertEquals(1, info.getTransitions().size());
        SepolicyTransition t = info.getTransitions().get(0);
        assertEquals("init", t.getSourceType());
        assertEquals("bootanim_exec", t.getTargetType());
        assertEquals("process", t.getObjectClass());
        assertEquals("bootanim", t.getNewType());
    }

    @Test
    public void testCommentsAreIgnored() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "# header comment\n"
                        + "type carservice, domain; # inline comment\n"
                        + "/* block comment */\n"
                        + "allow carservice vehicle_hal:binder call;\n",
                "carservice.te");
        assertEquals(1, info.getTypes().size());
        assertEquals(1, info.getAllowRules().size());
    }

    @Test
    public void testMultipleStatementsInFile() {
        String src =
                "type carservice, domain;\n"
                        + "type vehicle_hal_server, domain;\n"
                        + "allow carservice vehicle_hal_server:binder { call transfer };\n"
                        + "allow carservice fwk_sensor_service:service_manager find;\n"
                        + "neverallow untrusted_app vehicle_hal_server:binder *;\n";
        SepolicyInfo info = SepolicyTeParser.parse(src, "carservice.te");
        assertEquals(2, info.getTypes().size());
        assertEquals(2, info.getAllowRules().size());
        assertEquals(1, info.getNeverallowRules().size());
    }

    @Test
    public void testParseRuleBodyDirectly() {
        SepolicyRule r = SepolicyTeParser.parseRuleBody("allow",
                "carservice vehicle_hal:binder call");
        assertNotNull(r);
        assertEquals("carservice", r.getSourceType());
        assertEquals("vehicle_hal", r.getTargetType());
        assertEquals("binder", r.getObjectClass());
    }

    @Test
    public void testMalformedRuleIsIgnored() {
        SepolicyInfo info = SepolicyTeParser.parse(
                "allow incomplete;\n"
                        + "allow carservice vehicle_hal:binder call;\n",
                "carservice.te");
        assertEquals(1, info.getAllowRules().size());
        assertEquals("carservice", info.getAllowRules().get(0).getSourceType());
    }
}
