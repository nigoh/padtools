package padtools;

import padtools.app.uml.DiagramPreset;
import padtools.core.formats.uml.PlantUmlClassDiagram;
import padtools.core.formats.uml.UmlGenerator;
import padtools.util.Option;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CLI から指定された、UML 系出力で上書きするオプション値の束。
 *
 * <p>{@code -A}/{@code -c}/{@code -q} の経路から共通で使う。
 * クラス図向け (showComments / showFields / publicOnly 等) とシーケンス図向け (seqDepth) の
 * 双方を保持する。{@link DiagramPreset} を CLI {@code --preset} で適用してから、
 * 個別フラグ ({@code --no-fields} / {@code --public-only} 等) で上書きする 2 段方式。</p>
 */
public final class UmlOverrides {

    DiagramPreset preset = DiagramPreset.BALANCED;

    boolean showComments = true;
    boolean showAnnotations = true;
    boolean showEnumConstants = true;
    boolean showFinal = true;
    boolean showFields = true;
    boolean showMethods = true;
    boolean showInheritance = true;
    boolean showImplementations = true;
    boolean showUsageRelations = true;
    boolean publicOnly = false;
    boolean excludeExternal = false;
    boolean interactiveLinks = false;
    boolean jetpack = false;

    PlantUmlClassDiagram.CommentStyle commentStyle =
            PlantUmlClassDiagram.CommentStyle.INLINE;
    Integer commentMaxLengthOverride; // null = preset 既定をそのまま使う
    Set<String> hiddenAnnotationsOverride; // null = preset 既定をそのまま使う
    public Set<String> excludedPackages = new LinkedHashSet<>();
    public UmlGenerator.ParseMode parseMode = UmlGenerator.ParseMode.FULL;

    public Integer seqDepth;

    /** クラス図向け Options に値を反映する。 */
    public void applyTo(PlantUmlClassDiagram.Options o) {
        o.showComments = showComments;
        o.showAnnotations = showAnnotations;
        o.showEnumConstants = showEnumConstants;
        o.showFinal = showFinal;
        o.showFields = showFields;
        o.showMethods = showMethods;
        o.showInheritance = showInheritance;
        o.showImplementations = showImplementations;
        o.showUsageRelations = showUsageRelations;
        o.publicOnly = publicOnly;
        o.excludeExternalLibraries = excludeExternal;
        o.commentStyle = commentStyle;
        if (commentMaxLengthOverride != null) {
            o.commentMaxLength = commentMaxLengthOverride;
        }
        if (hiddenAnnotationsOverride != null) {
            o.hiddenAnnotations = new HashSet<>(hiddenAnnotationsOverride);
        }
        o.interactiveLinks = interactiveLinks;
        if (o.jetpack != null) {
            o.jetpack.enabled = jetpack;
        }
    }

    /** シーケンス図向けにコメント関連オプションを適用する。 */
    public void applyTo(padtools.core.formats.uml.PlantUmlSequenceDiagram.Options o) {
        o.showComments = showComments;
        o.commentStyle = commentStyle;
    }

    /**
     * CLI 引数から {@link UmlOverrides} を組み立てる。
     * 不正値の場合は {@code System.exit(1)} で終了して null を返す。
     */
    public static UmlOverrides build(Option optNoComments, Option optNoAnnotations,
                               Option optNoEnumConstants, Option optNoFinal,
                               Option optCommentStyle, Option optSeqDepth,
                               Option optJetpack, Option optPreset,
                               Option optNoFields, Option optNoMethods,
                               Option optPublicOnly, Option optExcludeExternal,
                               Option optExcludePackage, Option optRelation,
                               Option optMode, Option optInteractiveSvg,
                               Option optHiddenAnnotations,
                               Option optCommentMaxLength) {
        UmlOverrides o = new UmlOverrides();

        // 1. preset 解釈 (なければ BALANCED)
        if (optPreset != null && !optPreset.getArguments().isEmpty()) {
            String raw = optPreset.getArguments().getLast();
            DiagramPreset p = DiagramPreset.fromCli(raw);
            if (p == DiagramPreset.CUSTOM && !"custom".equalsIgnoreCase(raw)) {
                System.err.println("Invalid --preset value: " + raw
                        + " (expected: minimal | balanced | detailed)");
                System.exit(1);
                return null;
            }
            o.preset = p;
        }

        // 2. preset の既定値を CLI フィールドに反映
        PlantUmlClassDiagram.Options tmp = new PlantUmlClassDiagram.Options();
        o.preset.applyTo(tmp);
        o.showComments = tmp.showComments;
        o.showAnnotations = tmp.showAnnotations;
        o.showEnumConstants = tmp.showEnumConstants;
        o.showFinal = tmp.showFinal;
        o.showFields = tmp.showFields;
        o.showMethods = tmp.showMethods;
        o.showInheritance = tmp.showInheritance;
        o.showImplementations = tmp.showImplementations;
        o.showUsageRelations = tmp.showUsageRelations;
        o.publicOnly = tmp.publicOnly;
        o.excludeExternal = tmp.excludeExternalLibraries;
        o.commentStyle = tmp.commentStyle;
        o.interactiveLinks = tmp.interactiveLinks;
        o.jetpack = tmp.jetpack != null && tmp.jetpack.enabled;

        // 3. 個別 CLI フラグで上書き
        if (optNoComments.isSet()) {
            o.showComments = false;
        }
        if (optNoAnnotations.isSet()) {
            o.showAnnotations = false;
        }
        if (optNoEnumConstants.isSet()) {
            o.showEnumConstants = false;
        }
        if (optNoFinal.isSet()) {
            o.showFinal = false;
        }
        if (optNoFields != null && optNoFields.isSet()) {
            o.showFields = false;
        }
        if (optNoMethods != null && optNoMethods.isSet()) {
            o.showMethods = false;
        }
        if (optPublicOnly != null && optPublicOnly.isSet()) {
            o.publicOnly = true;
        }
        if (optExcludeExternal != null && optExcludeExternal.isSet()) {
            o.excludeExternal = true;
        }
        if (optInteractiveSvg != null && optInteractiveSvg.isSet()) {
            o.interactiveLinks = true;
        }
        o.jetpack = o.jetpack || (optJetpack != null && optJetpack.isSet());

        // --comment-style
        if (!optCommentStyle.getArguments().isEmpty()) {
            String style = optCommentStyle.getArguments().getLast().toLowerCase(Locale.ROOT);
            if ("note".equals(style)) {
                o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
            } else if ("inline".equals(style)) {
                o.commentStyle = PlantUmlClassDiagram.CommentStyle.INLINE;
            } else {
                System.err.println("Invalid --comment-style: " + style
                        + " (expected: inline | note)");
                System.exit(1);
                return null;
            }
        }

        // --seq-depth
        if (!optSeqDepth.getArguments().isEmpty()) {
            String raw = optSeqDepth.getArguments().getLast();
            try {
                o.seqDepth = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                System.err.println("Invalid --seq-depth value: " + raw);
                System.exit(1);
                return null;
            }
        }

        // --comment-max-length
        if (optCommentMaxLength != null && !optCommentMaxLength.getArguments().isEmpty()) {
            String raw = optCommentMaxLength.getArguments().getLast();
            try {
                o.commentMaxLengthOverride = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                System.err.println("Invalid --comment-max-length value: " + raw);
                System.exit(1);
                return null;
            }
        }

        // --hidden-annotations CSV
        if (optHiddenAnnotations != null && !optHiddenAnnotations.getArguments().isEmpty()) {
            String csv = optHiddenAnnotations.getArguments().getLast();
            Set<String> set = new LinkedHashSet<>();
            for (String tok : csv.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
            o.hiddenAnnotationsOverride = set;
        }

        // --exclude-package (複数指定可: getArguments().getLast() ではなく全件)
        if (optExcludePackage != null) {
            List<String> argList = optExcludePackage.getArguments();
            for (String pkg : argList) {
                if (pkg != null && !pkg.isEmpty()) {
                    o.excludedPackages.add(pkg);
                }
            }
        }

        // --relation CSV
        if (optRelation != null && !optRelation.getArguments().isEmpty()) {
            String csv = optRelation.getArguments().getLast();
            // 個別フラグで指定された場合は preset の relation 既定を上書きする
            boolean inherit = false;
            boolean impl = false;
            boolean use = false;
            for (String tok : csv.split(",")) {
                String t = tok.trim().toLowerCase(Locale.ROOT);
                switch (t) {
                    case "inherit":
                    case "inheritance":
                    case "extends":
                        inherit = true;
                        break;
                    case "impl":
                    case "implementation":
                    case "implements":
                        impl = true;
                        break;
                    case "use":
                    case "usage":
                    case "uses":
                        use = true;
                        break;
                    case "":
                        break;
                    default:
                        System.err.println("Invalid --relation token: " + tok
                                + " (expected: inherit | impl | use)");
                        System.exit(1);
                        return null;
                }
            }
            o.showInheritance = inherit;
            o.showImplementations = impl;
            o.showUsageRelations = use;
        }

        // --mode
        if (optMode != null && !optMode.getArguments().isEmpty()) {
            String m = optMode.getArguments().getLast().toLowerCase(Locale.ROOT);
            if ("headers-only".equals(m) || "headers".equals(m)) {
                o.parseMode = UmlGenerator.ParseMode.HEADERS_ONLY;
            } else if ("full".equals(m)) {
                o.parseMode = UmlGenerator.ParseMode.FULL;
            } else {
                System.err.println("Invalid --mode value: " + m
                        + " (expected: headers-only | full)");
                System.exit(1);
                return null;
            }
        }

        return o;
    }

    /** バックワード互換: 既存テストや旧呼び出し向けの簡易シグネチャ。 */
    static UmlOverrides build(Option optNoComments, Option optNoAnnotations,
                               Option optNoEnumConstants, Option optNoFinal,
                               Option optCommentStyle, Option optSeqDepth,
                               Option optJetpack) {
        return build(optNoComments, optNoAnnotations, optNoEnumConstants, optNoFinal,
                optCommentStyle, optSeqDepth, optJetpack,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
