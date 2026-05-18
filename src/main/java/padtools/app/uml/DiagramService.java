package padtools.app.uml;

import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.PlantUmlClassDiagram;
import padtools.core.formats.uml.PlantUmlPackageDiagram;
import padtools.core.formats.uml.PlantUmlSequenceDiagram;

import java.util.List;

/**
 * {@link DiagramRequest} を受け取り、対応する PlantUML テキストを生成する。
 *
 * <p>既存の各 {@code PlantUml*Diagram.generate(...)} へディスパッチするだけの
 * 薄いラッパー。{@link ProjectAnalysisCache} から取り出した解析結果と組み合わせ、
 * GUI 側の UI イベントとレンダリングの間に挟む変換層として機能する。</p>
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
        return generatePuml(request, cache.getAnalysis(), cache.getClasses());
    }

    /**
     * 生データ版。テストや CLI 経路など、{@link ProjectAnalysisCache} を介さない場合に使用。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        switch (request.getKind()) {
            case CLASS: {
                PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlClassDiagram.generate(classes, o);
            }
            case PACKAGE: {
                PlantUmlPackageDiagram.Options o = new PlantUmlPackageDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlPackageDiagram.generate(classes, o);
            }
            case SEQUENCE: {
                String cls = request.getSequenceEntryClass();
                String method = request.getSequenceEntryMethod();
                if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Sequence diagram requires entry Class.method");
                }
                PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
                o.includeLegend = request.isIncludeLegend();
                return PlantUmlSequenceDiagram.generate(classes, cls, method, o);
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
            default:
                throw new IllegalStateException("Unknown diagram kind: " + request.getKind());
        }
    }
}
