package padtools.core.screen;

import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Jetpack Compose NavHost 遷移検出のテスト。
 */
public class ComposeNavScannerTest {

    @Test
    public void detectsNavHostStartDestinationAndRoutes() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(\"home\") { HomeScreen(navController) }\n"
                + "    composable(\"detail/{id}\") { DetailScreen() }\n"
                + "    composable(\"settings\") { SettingsScreen() }\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        assertEquals("home", r.getStartDestination());
        assertEquals(3, r.getRoutes().size());
        assertTrue(r.getRoutes().contains("home"));
        assertTrue(r.getRoutes().contains("detail/{id}"));
        assertTrue(r.getRoutes().contains("settings"));
    }

    @Test
    public void detectsNavigateFromComposableLambda() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(\"home\") {\n"
                + "      Button(onClick = { navController.navigate(\"detail\") }) { }\n"
                + "    }\n"
                + "    composable(\"detail\") { Text(\"Detail\") }\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        assertEquals(1, r.getTransitions().size());
        ScreenTransition t = r.getTransitions().get(0);
        assertEquals("home", t.getFromMethod());
        assertEquals("detail", t.getTargetSimpleName());
    }

    @Test
    public void detectsNavigateFromComposableFun() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun HomeScreen(navController: NavController) {\n"
                + "  Button(onClick = { navController.navigate(\"settings\") }) { }\n"
                + "}\n"
                + "@Composable\n"
                + "fun SettingsScreen(navController: NavController) {\n"
                + "  Button(onClick = { navController.popBackStack() }) { }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Screens.kt");
        assertEquals(1, r.getTransitions().size());
        ScreenTransition t = r.getTransitions().get(0);
        assertEquals("HomeScreen", t.getFromMethod());
        assertEquals("settings", t.getTargetSimpleName());
    }

    @Test
    public void multipleNavigateCallsAggregated() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(\"home\") {\n"
                + "      Column {\n"
                + "        Button(onClick = { navController.navigate(\"detail\") }) { }\n"
                + "        Button(onClick = { navController.navigate(\"settings\") }) { }\n"
                + "      }\n"
                + "    }\n"
                + "    composable(\"detail\") { }\n"
                + "    composable(\"settings\") { }\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        assertEquals(2, r.getTransitions().size());
        // 両方 home から発火
        for (ScreenTransition t : r.getTransitions()) {
            assertEquals("home", t.getFromMethod());
        }
    }

    @Test
    public void routeWithArgumentsAndNamedRoute() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(route = \"detail/{id}\") { DetailScreen() }\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        // route = "detail/{id}" 形式も検出
        assertTrue(r.getRoutes().contains("detail/{id}"));
    }

    @Test
    public void emptySrcReturnsEmptyResult() {
        ComposeNavScanner.Result r = new ComposeNavScanner().analyzeSource("", "X.kt");
        assertNotNull(r);
        assertEquals(0, r.getTransitions().size());
        assertEquals(0, r.getRoutes().size());
        assertEquals("", r.getStartDestination());
    }

    @Test
    public void packageAndClassNameInferred() {
        String src = "package com.example.app\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(\"home\") { navController.navigate(\"about\") }\n"
                + "    composable(\"about\") {}\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        assertEquals(1, r.getTransitions().size());
        // クラス/object 無しの top-level fun ファイルの場合は "NavGraph" がデフォルト名
        String fromFqn = r.getTransitions().get(0).getFromFqn();
        assertTrue(fromFqn.startsWith("com.example.app."));
    }

    @Test
    public void diagramRendersComposeRoutesAsNodes() {
        String src = "package com.x\n"
                + "@Composable\n"
                + "fun AppNav() {\n"
                + "  NavHost(navController, startDestination = \"home\") {\n"
                + "    composable(\"home\") { navController.navigate(\"detail\") }\n"
                + "    composable(\"detail\") {}\n"
                + "  }\n"
                + "}\n";
        ComposeNavScanner.Result r = new ComposeNavScanner()
                .analyzeSource(src, "Nav.kt");
        String puml = PlantUmlScreenFlowDiagram.render(r.getTransitions());
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("home"));
        assertTrue(puml.contains("detail"));
    }
}
