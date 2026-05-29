// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「シンボル X を消すと何が壊れるか」を {@link ReferenceIndex} 上の BFS で解析する。
 *
 * <p>起点シンボル (クラス FQN もしくは {@code FQN#method}) から逆方向に
 * 「呼び出し元」「継承元」「型参照元」を辿り、指定深さ {@code maxDepth} まで展開した
 * {@link ImpactGraph} を返す。</p>
 *
 * <p>影響度スコア (0.0〜1.0) は層数の単純減衰: {@code 1 / (layer + 1)}。
 * 浅い層の参照ほど削除リスクが大きいことを表す。</p>
 */
public final class ImpactAnalyzer {

    private final ReferenceIndex index;

    public ImpactAnalyzer(ReferenceIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        this.index = index;
    }

    /**
     * クラス FQN を起点に Impact Analysis を実行する。
     *
     * @param fqn クラスの完全修飾名
     * @param maxDepth BFS 深さ (1 以上推奨。0 なら起点ノードのみ)
     */
    public ImpactGraph analyzeClass(String fqn, int maxDepth) {
        ImpactGraph g = new ImpactGraph(fqn);
        if (fqn == null || fqn.isEmpty()) {
            return g;
        }
        List<ReferenceSite> direct = index.sitesForClass(fqn);
        runBfs(g, fqn, direct, maxDepth);
        return g;
    }

    /**
     * メソッド (FQN + 単純名) を起点に Impact Analysis を実行する。
     *
     * @param ownerFqn メソッドを宣言するクラスの FQN
     * @param methodName メソッド単純名 (引数なし)
     * @param maxDepth BFS 深さ
     */
    public ImpactGraph analyzeMethod(String ownerFqn, String methodName, int maxDepth) {
        String target = ownerFqn + "." + methodName;
        ImpactGraph g = new ImpactGraph(target);
        if (ownerFqn == null || methodName == null) {
            return g;
        }
        List<ReferenceSite> direct = index.sitesByMember(
                ReferenceKey.Kind.METHOD, ownerFqn, methodName);
        runBfs(g, target, direct, maxDepth);
        return g;
    }

    /** 直接参照リストから BFS で推移閉包を構築する。 */
    private void runBfs(ImpactGraph g, String targetLabel,
                         List<ReferenceSite> direct, int maxDepth) {
        // ノード: caller FQN (クラス単位で集約)
        // depth 0: target 自身
        Map<String, Integer> depthOf = new LinkedHashMap<>();
        depthOf.put(targetLabel, 0);
        g.addNode(targetLabel, 0, 1.0, "TARGET");

        Deque<String> frontier = new ArrayDeque<>();
        // depth 1 の直接参照元を投入
        for (ReferenceSite site : direct) {
            String caller = site.getCallerFqn();
            if (caller == null || caller.isEmpty()) {
                continue;
            }
            if (!depthOf.containsKey(caller)) {
                depthOf.put(caller, 1);
                g.addNode(caller, 1, scoreFor(1), reasonFor(site.getKind()));
                frontier.add(caller);
            }
            g.addEdge(caller, targetLabel, site.getKind().name(),
                    site.getCallerMethod(), site.getFile(), site.getLineHint());
        }
        // depth 2 以降 BFS
        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            int currentDepth = depthOf.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }
            // current を参照しているノードを辿る
            List<ReferenceSite> siteCurrent = index.sitesForClass(current);
            Set<String> seenAtThisStep = new LinkedHashSet<>();
            for (ReferenceSite site : siteCurrent) {
                String caller = site.getCallerFqn();
                if (caller == null || caller.isEmpty() || caller.equals(current)) {
                    continue;
                }
                if (!depthOf.containsKey(caller)) {
                    int nextDepth = currentDepth + 1;
                    depthOf.put(caller, nextDepth);
                    g.addNode(caller, nextDepth, scoreFor(nextDepth),
                            reasonFor(site.getKind()));
                    frontier.add(caller);
                }
                if (seenAtThisStep.add(caller + "->" + current
                        + "/" + site.getKind())) {
                    g.addEdge(caller, current, site.getKind().name(),
                            site.getCallerMethod(), site.getFile(),
                            site.getLineHint());
                }
            }
        }
    }

    private static double scoreFor(int layer) {
        return 1.0 / (layer + 1);
    }

    private static String reasonFor(ReferenceSite.Kind kind) {
        switch (kind) {
            case CALL: return "DIRECT_CALL";
            case EXTENDS: return "EXTENDS";
            case IMPLEMENTS: return "IMPLEMENTS";
            case TYPE_REFERENCE: return "TYPE_REFERENCE";
            case ANNOTATION: return "ANNOTATION";
            case IMPORT: return "IMPORT";
            default: return kind.name();
        }
    }
}
