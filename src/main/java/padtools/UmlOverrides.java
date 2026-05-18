package padtools;

import padtools.util.Option;

/**
 * CLI から指定された、UML 系出力で上書きするオプション値の束。
 *
 * <p>{@code -A}/{@code -c}/{@code -q} の経路から共通で使う。
 * クラス図向け ({@code showComments} 等) とシーケンス図向け ({@code seqDepth}) の双方を保持する。</p>
 */
final class UmlOverrides {

    boolean showComments = true;
    boolean showAnnotations = true;
    boolean showEnumConstants = true;
    boolean showFinal = true;
    boolean jetpack = false;
    padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle commentStyle =
            padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
    Integer seqDepth;

    void applyTo(padtools.core.formats.uml.PlantUmlClassDiagram.Options o) {
        o.showComments = showComments;
        o.showAnnotations = showAnnotations;
        o.showEnumConstants = showEnumConstants;
        o.showFinal = showFinal;
        o.commentStyle = commentStyle;
        if (o.jetpack != null) {
            o.jetpack.enabled = jetpack;
        }
    }

    /**
     * CLI 引数から {@link UmlOverrides} を組み立てる。
     * 不正値の場合は {@code System.exit(1)} で終了して null を返す。
     */
    static UmlOverrides build(Option optNoComments, Option optNoAnnotations,
                               Option optNoEnumConstants, Option optNoFinal,
                               Option optCommentStyle, Option optSeqDepth,
                               Option optJetpack) {
        UmlOverrides o = new UmlOverrides();
        o.showComments = !optNoComments.isSet();
        o.showAnnotations = !optNoAnnotations.isSet();
        o.showEnumConstants = !optNoEnumConstants.isSet();
        o.showFinal = !optNoFinal.isSet();
        o.jetpack = optJetpack != null && optJetpack.isSet();
        if (!optCommentStyle.getArguments().isEmpty()) {
            String style = optCommentStyle.getArguments().getLast().toLowerCase();
            if ("note".equals(style)) {
                o.commentStyle =
                        padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.NOTE;
            } else if ("inline".equals(style)) {
                o.commentStyle =
                        padtools.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
            } else {
                System.err.println("Invalid --comment-style: " + style
                        + " (expected: inline | note)");
                System.exit(1);
                return null;
            }
        }
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
        return o;
    }
}
