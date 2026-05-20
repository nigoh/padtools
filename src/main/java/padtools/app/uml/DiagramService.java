package padtools.app.uml;

import padtools.core.formats.android.AndroidLayoutInfo;
import padtools.core.formats.android.AndroidNavigationGraphInfo;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.android.PlantUmlLayoutDiagram;
import padtools.core.formats.android.PlantUmlManifestDiagram;
import padtools.core.formats.android.PlantUmlNavigationGraphDiagram;
import padtools.core.formats.uml.ClassIndex;
import padtools.core.formats.uml.DependencyJarIndex;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaFieldInfo;
import padtools.core.formats.uml.PlantUmlClassDiagram;
import padtools.core.formats.uml.PlantUmlActivityDiagram;
import padtools.core.formats.uml.PlantUmlCommonClassesDiagram;
import padtools.core.formats.uml.PlantUmlModuleDiagram;
import padtools.core.formats.uml.PlantUmlPackageDiagram;
import padtools.core.formats.uml.PlantUmlSequenceDiagram;
import padtools.util.ErrorListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link DiagramRequest} を受け取り、対応する PlantUML テキストを生成する。
 *
 * <p>既存の各 {@code PlantUml*Diagram.generate(...)} へディスパッチするだけの
 * 薄いラッパー。{@link ProjectAnalysisCache} から取り出した解析結果と組み合わせ、
 * GUI 側の UI イベントとレンダリングの間に挟む変換層として機能する。</p>
 *
 * <p>大規模プロジェクト対応として {@link DiagramScope} によるフィルタリングと、
 * 必要なクラスだけ Stage B (詳細) に昇格させる処理 ({@link ClassIndex#detail}) を担う。</p>
 */
public final class DiagramService {

    private DiagramService() {
    }

    /**
     * 図種に応じた PlantUML テキストを返す。
     *
     * @param request 図種・スコープ等
     * @param cache   プロジェクト解析結果 (ロード済みである必要がある)
     * @return PlantUML テキスト (@startuml ... @enduml を含む)
     */
    public static String generatePuml(DiagramRequest request, ProjectAnalysisCache cache) {
        if (cache == null || !cache.isLoaded()) {
            throw new IllegalStateException("ProjectAnalysisCache is not loaded");
        }
        return generatePuml(request, cache.getAnalysis(), cache.getClasses(),
                cache.getIndex(), cache.getDependencyIndex());
    }

    /**
     * 生データ版。テストや CLI 経路など、{@link ProjectAnalysisCache} を介さない場合に使用。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes) {
        return generatePuml(request, analysis, classes, null, null);
    }

    /**
     * インデックス込み版。スコープ適用時の Stage B 昇格に {@link ClassIndex} を使う。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes,
                                       ClassIndex index) {
        return generatePuml(request, analysis, classes, index, null);
    }

    /**
     * 依存 JAR インデックスを併用する版。シーケンス図/クラス図で {@code <<external>>} /
     * {@code <<missing>>} ステレオタイプを描画するために
     * {@link DependencyJarIndex} を引き渡す。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes,
                                       ClassIndex index,
                                       DependencyJarIndex depIndex) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        switch (request.getKind()) {
            case CLASS: {
                List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                        index != null ? index.moduleMap() : null);
                int originalTotal = (classes != null) ? classes.size() : 0;
                int scopedTotal = scoped.size();
                int maxClasses = request.getScope() != null ? request.getScope().getMaxClasses() : 0;
                if (index != null) {
                    scoped = promoteToDetailed(scoped, index);
                }
                PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                o.maxClasses = maxClasses;
                o.interactiveLinks = request.isInteractiveLinks();
                // Setting → Options (GUI で永続化されたクラス図設定の既定)
                applyClassDiagramSettings(o);
                // Scope → Options (Scope に明示値があれば上書き)
                applyScopeToClassOptions(request.getScope(), o);
                if (scopedTotal < originalTotal) {
                    o.footerWarning = "scope filter: " + scopedTotal + " of "
                            + originalTotal + " classes";
                }
                return PlantUmlClassDiagram.generate(scoped, o);
            }
            case PACKAGE: {
                List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                        index != null ? index.moduleMap() : null);
                PlantUmlPackageDiagram.Options o = new PlantUmlPackageDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlPackageDiagram.generate(scoped, o);
            }
            case SEQUENCE: {
                String cls = request.getSequenceEntryClass();
                String method = request.getSequenceEntryMethod();
                if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Sequence diagram requires entry Class.method");
                }
                List<JavaClassInfo> source = classes != null
                        ? promoteToDetailed(classes, index) : classes;
                PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                o.dependencyIndex = depIndex;
                applySequenceCommentSettings(o);
                Set<String> hidden = request.getSequenceHiddenParticipants();
                if (hidden != null && !hidden.isEmpty()) {
                    o.hiddenParticipants = hidden;
                }
                return PlantUmlSequenceDiagram.generate(source, cls, method, o);
            }
            case ACTIVITY: {
                String cls = request.getActivityEntryClass();
                String method = request.getActivityEntryMethod();
                if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Activity diagram requires entry Class.method");
                }
                List<JavaClassInfo> source = classes != null
                        ? promoteToDetailed(classes, index) : classes;
                PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlActivityDiagram.generate(source, cls, method, o);
            }
            case COMPONENT: {
                PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlComponentDiagram.generate(analysis, o);
            }
            case DEPENDENCY: {
                PlantUmlGradleDependencyGraph.Options o =
                        new PlantUmlGradleDependencyGraph.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlGradleDependencyGraph.generate(analysis, o);
            }
            case MANIFEST: {
                PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlManifestDiagram.generate(analysis, o);
            }
            case COMMON: {
                List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                        index != null ? index.moduleMap() : null);
                if (index != null) {
                    scoped = promoteToDetailed(scoped, index);
                }
                PlantUmlCommonClassesDiagram.Options o =
                        new PlantUmlCommonClassesDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlCommonClassesDiagram.generate(scoped, o);
            }
            case LAYOUT: {
                String key = request.getLayoutKey();
                if (key == null || key.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Layout diagram requires layoutKey (select a layout file)");
                }
                if (analysis == null) {
                    throw new IllegalStateException(
                            "Layout diagram requires Android project analysis");
                }
                AndroidLayoutInfo layout = analysis.findLayoutByKey(key);
                if (layout == null) {
                    throw new IllegalArgumentException(
                            "Layout not found for key: " + key);
                }
                PlantUmlLayoutDiagram.Options o = new PlantUmlLayoutDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlLayoutDiagram.generate(layout, o);
            }
            case NAVIGATION: {
                String navKey = request.getNavigationGraphKey();
                if (navKey == null || navKey.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Navigation diagram requires navigationGraphKey"
                                    + " (select a navigation file)");
                }
                if (analysis == null) {
                    throw new IllegalStateException(
                            "Navigation diagram requires Android project analysis");
                }
                AndroidNavigationGraphInfo nav = analysis.findNavigationByKey(navKey);
                if (nav == null) {
                    throw new IllegalArgumentException(
                            "Navigation graph not found for key: " + navKey);
                }
                PlantUmlNavigationGraphDiagram.Options o =
                        new PlantUmlNavigationGraphDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlNavigationGraphDiagram.generate(nav, o);
            }
            case MODULE: {
                PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlModuleDiagram.generate(classes, o);
            }
            default:
                throw new IllegalStateException("Unknown diagram kind: " + request.getKind());
        }
    }

    /**
     * シーケンス図のコメント表示・修飾設定を {@link padtools.SettingManager} から
     * 読み出して {@link PlantUmlSequenceDiagram.Options} に反映する。
     * SettingManager が利用できない場合 (テスト / 単体実行) は既定値のまま。
     */
    private static void applySequenceCommentSettings(PlantUmlSequenceDiagram.Options o) {
        try {
            padtools.Setting s = padtools.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            o.showComments = s.isSequenceShowComments();
            if ("NOTE".equalsIgnoreCase(s.getSequenceCommentStyle())) {
                o.commentStyle = padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.NOTE;
            } else {
                o.commentStyle = padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
            }
            if ("PARTICIPANT_TOP".equalsIgnoreCase(s.getSequenceCommentPlacement())) {
                o.commentPlacement =
                        PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
            } else {
                o.commentPlacement =
                        PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
            }
            o.qualifyMethodNames = s.isSequenceQualifyMethodNames();
        } catch (RuntimeException ignored) {
            // 設定取得失敗時は既定値のまま (showComments=true, INLINE, AT_CALL_SITE)
        }
    }

    /**
     * GUI で永続化されたクラス図設定 ({@code classDiagram.*} キー) を
     * {@link PlantUmlClassDiagram.Options} に反映する。
     * {@link padtools.SettingManager} が利用できない場合 (テスト / 単体実行) は既定値のまま。
     */
    private static void applyClassDiagramSettings(PlantUmlClassDiagram.Options o) {
        try {
            padtools.Setting s = padtools.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            o.showFields = s.isClassDiagramShowFields();
            o.showMethods = s.isClassDiagramShowMethods();
            o.showAnnotations = s.isClassDiagramShowAnnotations();
            o.publicOnly = s.isClassDiagramPublicOnly();
            o.excludeExternalLibraries = s.isClassDiagramExcludeExternal();
            o.commentMaxLength = s.getClassDiagramCommentMaxLength();
            String csv = s.getClassDiagramHiddenAnnotations();
            if (csv != null) {
                java.util.Set<String> set = new java.util.LinkedHashSet<>();
                for (String tok : csv.split(",")) {
                    String t = tok.trim();
                    if (!t.isEmpty()) {
                        set.add(t);
                    }
                }
                o.hiddenAnnotations = new java.util.HashSet<>(set);
            }
        } catch (RuntimeException ignored) {
            // 設定取得失敗時は既定値のまま
        }
    }

    /**
     * スコープに保持された関連線フィルタ・可視性フィルタを
     * {@link PlantUmlClassDiagram.Options} に転写する。
     * クラスリストの除外は {@link #applyScope} で完了している前提なので、
     * Options 側の {@code excludeExternalLibraries} は触らない (二重除外を避ける)。
     */
    static void applyScopeToClassOptions(DiagramScope scope, PlantUmlClassDiagram.Options o) {
        if (scope == null || o == null) {
            return;
        }
        java.util.EnumSet<RelationKind> kinds = scope.getRelationKinds();
        if (!kinds.containsAll(java.util.EnumSet.allOf(RelationKind.class))) {
            o.showInheritance = kinds.contains(RelationKind.INHERITANCE);
            o.showImplementations = kinds.contains(RelationKind.IMPLEMENTATION);
            o.showUsageRelations = kinds.contains(RelationKind.USAGE);
        }
        if (scope.getVisibilityFilter() == VisibilityFilter.PUBLIC_ONLY) {
            o.publicOnly = true;
        }
    }

    /**
     * スコープに従って ClassInfo リストを絞り込む。
     *
     * <p>順序: module → package include → package exclude → external libraries →
     * regex → seed + BFS by neighborHops。
     * maxClasses 上限は呼び出し側 (PlantUmlClassDiagram.Options.maxClasses) で適用する。</p>
     */
    static List<JavaClassInfo> applyScope(List<JavaClassInfo> classes, DiagramScope scope,
                                          Map<String, String> qnToModule) {
        if (classes == null || classes.isEmpty() || scope == null || scope.isEmpty()) {
            return classes != null ? classes : new ArrayList<>();
        }
        List<JavaClassInfo> result = new ArrayList<>(classes);

        // 1. module フィルタ
        if (!scope.getIncludedModules().isEmpty() && qnToModule != null && !qnToModule.isEmpty()) {
            Set<String> mods = scope.getIncludedModules();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String mod = qnToModule.get(c.getQualifiedName());
                if (mod != null && mods.contains(mod)) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 2. package include (前方一致または完全一致)
        if (!scope.getIncludedPackages().isEmpty()) {
            Set<String> pkgs = scope.getIncludedPackages();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                for (String allowed : pkgs) {
                    if (pkg.equals(allowed) || pkg.startsWith(allowed + ".")) {
                        next.add(c);
                        break;
                    }
                }
            }
            result = next;
        }

        // 2.5. package exclude (前方一致または完全一致)
        if (!scope.getExcludedPackages().isEmpty()) {
            Set<String> excluded = scope.getExcludedPackages();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                boolean drop = false;
                for (String ex : excluded) {
                    if (pkg.equals(ex) || pkg.startsWith(ex + ".")) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 2.6. exclude external libraries (Origin + prefix の 2 段判定)
        if (scope.isExcludeExternalLibraries()) {
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                JavaClassInfo.Origin origin = c.getOrigin();
                if (origin == JavaClassInfo.Origin.EXTERNAL_JAR
                        || origin == JavaClassInfo.Origin.MISSING_JAR) {
                    continue;
                }
                if (padtools.core.formats.uml.ExternalPackageMatcher.isExternal(
                        c.getPackageName(), scope.getExternalPackagePrefixes())) {
                    continue;
                }
                next.add(c);
            }
            result = next;
        }

        // 3. regex フィルタ
        Pattern p = scope.getClassNameRegex();
        if (p != null) {
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                if (p.matcher(c.getSimpleName()).find()
                        || p.matcher(c.getQualifiedName()).find()) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 4. seed + neighborHops BFS
        if (!scope.getSeedQualifiedNames().isEmpty()) {
            result = bfsNeighbors(result, scope.getSeedQualifiedNames(),
                    scope.getNeighborHops());
        }

        return result;
    }

    /** シード集合から継承/実装/フィールド型を辿り、N hop 以内のクラスのみを返す。 */
    private static List<JavaClassInfo> bfsNeighbors(List<JavaClassInfo> pool,
                                                     Set<String> seeds, int hops) {
        // pool 内のクラスを QN とシンプル名の両方で引けるようマップ化
        Map<String, JavaClassInfo> byQn = new HashMap<>();
        Map<String, String> bySimple = new HashMap<>();
        for (JavaClassInfo c : pool) {
            byQn.put(c.getQualifiedName(), c);
            bySimple.putIfAbsent(c.getSimpleName(), c.getQualifiedName());
        }
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        Map<String, Integer> depth = new HashMap<>();
        for (String s : seeds) {
            if (byQn.containsKey(s)) {
                frontier.add(s);
                depth.put(s, 0);
                visited.add(s);
            } else {
                // simple 名一致をフォールバック
                String qn = bySimple.get(s);
                if (qn != null) {
                    frontier.add(qn);
                    depth.put(qn, 0);
                    visited.add(qn);
                }
            }
        }
        while (!frontier.isEmpty()) {
            String qn = frontier.poll();
            int d = depth.getOrDefault(qn, 0);
            if (d >= hops) {
                continue;
            }
            JavaClassInfo c = byQn.get(qn);
            if (c == null) {
                continue;
            }
            for (String n : neighbors(c, byQn, bySimple)) {
                if (visited.add(n)) {
                    depth.put(n, d + 1);
                    frontier.add(n);
                }
            }
        }
        List<JavaClassInfo> result = new ArrayList<>(visited.size());
        for (String qn : visited) {
            JavaClassInfo c = byQn.get(qn);
            if (c != null) {
                result.add(c);
            }
        }
        return result;
    }

    private static Set<String> neighbors(JavaClassInfo c,
                                          Map<String, JavaClassInfo> byQn,
                                          Map<String, String> bySimple) {
        Set<String> out = new LinkedHashSet<>();
        addNeighbor(c.getSuperClass(), byQn, bySimple, out);
        for (String iface : c.getInterfaces()) {
            addNeighbor(iface, byQn, bySimple, out);
        }
        if (c.getFields() != null) {
            for (JavaFieldInfo f : c.getFields()) {
                if (f.getType() != null) {
                    // 単純な型名抽出 (ジェネリクスや配列は雑に分解)
                    for (String tok : f.getType().split("[<>,\\[\\]\\s?]+")) {
                        addNeighbor(tok.trim(), byQn, bySimple, out);
                    }
                }
            }
        }
        return out;
    }

    private static void addNeighbor(String type, Map<String, JavaClassInfo> byQn,
                                     Map<String, String> bySimple, Set<String> out) {
        if (type == null || type.isEmpty()) {
            return;
        }
        if (byQn.containsKey(type)) {
            out.add(type);
            return;
        }
        String qn = bySimple.get(type);
        if (qn != null) {
            out.add(qn);
        }
    }

    /** スコープで残ったクラスを Stage B 詳細に昇格させた新リストを返す。 */
    private static List<JavaClassInfo> promoteToDetailed(List<JavaClassInfo> scoped,
                                                          ClassIndex index) {
        if (index == null || scoped == null || scoped.isEmpty()) {
            return scoped;
        }
        List<JavaClassInfo> result = new ArrayList<>(scoped.size());
        Set<String> seen = new HashSet<>();
        for (JavaClassInfo c : scoped) {
            if (c.isDetailed()) {
                result.add(c);
                continue;
            }
            String qn = c.getQualifiedName();
            if (!seen.add(qn)) {
                continue;
            }
            JavaClassInfo d = index.detail(qn, ErrorListener.silent());
            result.add(d != null ? d : c);
        }
        return result;
    }
}
