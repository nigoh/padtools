package padtools.core.formats.uml;

import padtools.core.formats.java.JavaToken;
import java.util.*;

/** Java ソースの宣言 (package/import/クラス/モジュール/メンバ) を解析する。 */
final class DeclarationParser {

    private final ParserState state;

    DeclarationParser(ParserState state) {
        this.state = state;
    }


        // --- ファイルレベル ---

        void parseFile() {
            while (!state.atEnd()) {
                if (state.peek().isKw("package")) {
                    state.next();
                    state.packageName = state.readDottedName();
                    state.consume(";");
                    continue;
                }
                if (state.peek().isKw("import")) {
                    state.next(); // import
                    boolean isStatic = state.peek().isKw("static");
                    if (isStatic) {
                        state.next();
                    }
                    String imp = state.readImportName();
                    if (!imp.isEmpty()) {
                        state.imports.add(isStatic ? "static " + imp : imp);
                    }
                    state.consume(";");
                    continue;
                }
                int declStart = state.atEnd() ? -1 : state.peek().start;
                List<String> ann = state.readAnnotations();
                List<String> mods = state.readModifiers();
                if (state.atEnd()) {
                    break;
                }
                String comment = state.findCommentBefore(declStart);
                if (state.peek().is("@") && state.peek(1).isKw("interface")) {
                    state.next(); // @
                    parseClassDecl(JavaClassInfo.Kind.ANNOTATION, mods, ann, comment);
                    continue;
                }
                if (state.peek().isKw("class")) {
                    parseClassDecl(JavaClassInfo.Kind.CLASS, mods, ann, comment);
                    continue;
                }
                if (state.peek().isKw("interface")) {
                    parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, ann, comment);
                    continue;
                }
                if (state.peek().isKw("enum")) {
                    parseClassDecl(JavaClassInfo.Kind.ENUM, mods, ann, comment);
                    continue;
                }
                if (state.peek().isKw("record")) {
                    parseClassDecl(JavaClassInfo.Kind.RECORD, mods, ann, comment);
                    continue;
                }
                // module-info.java の宣言。`module Foo { ... }` または
                // `open module Foo { ... }` (JLS §7.7)。`module`/`open` は
                // 文脈依存キーワード (IDENT) なので isKw で識別する。
                if (state.peek().isKw("module")
                        || (state.peek().isKw("open") && state.peek(1).isKw("module"))) {
                    parseModuleDecl(mods, ann, comment);
                    continue;
                }
                state.next();
            }
            state.resolvePendingFieldAssignments();
        }


        void parseClassDecl(JavaClassInfo.Kind kind, List<String> mods,
                                     List<String> annotations, String comment) {
            state.next(); // class/interface/enum
            String name = "Anonymous";
            if (state.peek().type == JavaToken.Type.IDENT) {
                name = state.next().text;
            }
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(kind);
            info.setSimpleName(name);
            info.setPackageName(state.packageName);
            info.getImports().addAll(state.imports);
            info.getModifiers().addAll(mods);
            info.getAnnotations().addAll(annotations);
            info.setComment(comment);
            if (!state.classStack.isEmpty()) {
                String outerName = state.buildEnclosingPath();
                info.setEnclosingClass(outerName);
            }

            // ジェネリック型パラメータ <T extends ...>
            if (state.peek().is("<")) {
                state.skipBalanced("<", ">");
            }
            // record のコンパクトコンストラクタ引数: record Foo(int x, int y)
            if (kind == JavaClassInfo.Kind.RECORD && state.peek().is("(")) {
                state.skipBalanced("(", ")");
            }
            // extends ... / implements ...
            while (!state.atEnd() && !state.peek().is("{") && !state.peek().is(";")) {
                if (state.peek().isKw("extends")) {
                    state.next();
                    if (kind == JavaClassInfo.Kind.INTERFACE) {
                        // interface extends は複数の親
                        info.getInterfaces().addAll(state.readTypeList());
                    } else {
                        info.setSuperClass(state.readTypeName());
                    }
                } else if (state.peek().isKw("implements") || state.peek().isKw("permits")) {
                    state.next();
                    info.getInterfaces().addAll(state.readTypeList());
                } else {
                    state.next();
                }
            }
            if (state.peek().is(";")) {
                state.next();
                state.results.add(info);
                return;
            }
            if (!state.peek().is("{")) {
                state.results.add(info);
                return;
            }
            state.next(); // {
            state.classStack.add(info);
            try {
                if (kind == JavaClassInfo.Kind.ENUM) {
                    parseEnumConstants(info);
                }
                parseClassBody(info);
            } finally {
                state.classStack.remove(state.classStack.size() - 1);
            }
            state.results.add(info);
        }


        /**
         * {@code [open] module a.b.c { ... }} を解析する (JLS §7.7)。
         *
         * <p>本体は {@code requires} / {@code exports} / {@code opens} /
         * {@code uses} / {@code provides ... with ...} の各ディレクティブで
         * 構成される。{@code module}/{@code open}/{@code requires} 等は
         * 文脈依存キーワード (IDENT) なので {@link JavaToken#isKw(String)}
         * で識別する。</p>
         */
        void parseModuleDecl(List<String> mods, List<String> annotations,
                                      String comment) {
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(JavaClassInfo.Kind.MODULE);
            info.setPackageName(state.packageName);
            info.getModifiers().addAll(mods);
            info.getAnnotations().addAll(annotations);
            info.setComment(comment);

            if (state.peek().isKw("open")) {
                state.next();
                info.getModifiers().add("open");
            }
            if (!state.peek().isKw("module")) {
                // 万一誤検出だった場合のセーフネット
                state.results.add(info);
                return;
            }
            state.next(); // module
            String moduleName = state.readDottedName();
            info.setSimpleName(moduleName);

            if (!state.peek().is("{")) {
                // 本体が無い場合は名前だけ記録して終わる
                if (state.peek().is(";")) {
                    state.next();
                }
                state.results.add(info);
                return;
            }
            state.next(); // {
            parseModuleBody(info);
            state.results.add(info);
        }


        /** {@code module} の本体ディレクティブを {@code }} まで読む。 */
        void parseModuleBody(JavaClassInfo info) {
            while (!state.atEnd() && !state.peek().is("}")) {
                if (state.peek().is(";")) {
                    state.next();
                    continue;
                }
                // 各ディレクティブは contextual keyword で始まる
                if (state.peek().isKw("requires")) {
                    state.next();
                    List<String> dmods = new ArrayList<>();
                    // requires の `transitive`/`static` は contextual keyword なので
                    // 次のトークンが IDENT (= モジュール名の継続) のときだけ修飾子扱い
                    while ((state.peek().isKw("transitive") || state.peek().isKw("static"))
                            && state.peek(1).type == JavaToken.Type.IDENT) {
                        dmods.add(state.next().text);
                    }
                    String target = state.readDottedName();
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.REQUIRES, target, dmods, null));
                    state.consume(";");
                    continue;
                }
                if (state.peek().isKw("exports") || state.peek().isKw("opens")) {
                    JavaModuleDirective.Kind k = state.peek().isKw("exports")
                            ? JavaModuleDirective.Kind.EXPORTS
                            : JavaModuleDirective.Kind.OPENS;
                    state.next();
                    String pkg = state.readDottedName();
                    List<String> tgts = new ArrayList<>();
                    if (state.peek().isKw("to")) {
                        state.next();
                        tgts.addAll(state.readDottedNameList());
                    }
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            k, pkg, null, tgts));
                    state.consume(";");
                    continue;
                }
                if (state.peek().isKw("uses")) {
                    state.next();
                    String service = state.readDottedName();
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.USES, service, null, null));
                    state.consume(";");
                    continue;
                }
                if (state.peek().isKw("provides")) {
                    state.next();
                    String service = state.readDottedName();
                    List<String> impls = new ArrayList<>();
                    if (state.peek().isKw("with")) {
                        state.next();
                        impls.addAll(state.readDottedNameList());
                    }
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.PROVIDES, service, null, impls));
                    state.consume(";");
                    continue;
                }
                // 未知のトークンは 1 つ消費して継続 (前方互換)
                state.next();
            }
            if (!state.atEnd() && state.peek().is("}")) {
                state.next();
            }
        }


        /** enum 定数を ; or } まで読み取り、引数/無名サブクラス body は名前のみ拾って中身はスキップ。 */
        void parseEnumConstants(JavaClassInfo cls) {
            while (!state.atEnd()) {
                if (state.peek().is("}")) {
                    return;
                }
                if (state.peek().is(";")) {
                    state.next();
                    return;
                }
                if (state.peek().is(",")) {
                    state.next();
                    continue;
                }
                state.readAnnotations();
                if (state.atEnd()) {
                    return;
                }
                if (state.peek().type != JavaToken.Type.IDENT) {
                    state.next();
                    continue;
                }
                String constName = state.next().text;
                cls.getEnumConstants().add(constName);
                if (state.peek().is("(")) {
                    state.skipBalanced("(", ")");
                }
                if (state.peek().is("{")) {
                    state.skipBalanced("{", "}");
                }
            }
        }


        void parseClassBody(JavaClassInfo cls) {
            while (!state.atEnd()) {
                if (state.peek().is("}")) {
                    state.next();
                    return;
                }
                if (state.peek().is(";")) {
                    state.next();
                    continue;
                }
                int declStart = state.peek().start;
                List<String> annotations = state.readAnnotations();
                List<String> mods = state.readModifiers();
                if (state.atEnd() || state.peek().is("}")) {
                    continue;
                }
                String comment = state.findCommentBefore(declStart);

                if (state.peek().isKw("class")) {
                    parseClassDecl(JavaClassInfo.Kind.CLASS, mods, annotations, comment);
                    continue;
                }
                if (state.peek().isKw("interface")) {
                    parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, annotations, comment);
                    continue;
                }
                if (state.peek().isKw("enum")) {
                    parseClassDecl(JavaClassInfo.Kind.ENUM, mods, annotations, comment);
                    continue;
                }
                if (state.peek().isKw("record")) {
                    parseClassDecl(JavaClassInfo.Kind.RECORD, mods, annotations, comment);
                    continue;
                }
                if (state.peek().is("@") && state.peek(1).isKw("interface")) {
                    state.next();
                    parseClassDecl(JavaClassInfo.Kind.ANNOTATION, mods, annotations, comment);
                    continue;
                }
                if (state.peek().is("{")) {
                    state.skipBalanced("{", "}");
                    continue;
                }
                if (!parseMember(cls, mods, annotations, comment)) {
                    state.next();
                }
            }
        }


        boolean parseMember(JavaClassInfo cls, List<String> mods,
                                     List<String> annotations, String comment) {
            int save = state.idx;
            if (state.peek().is("<")) {
                state.skipBalanced("<", ">");
            }
            // 型→名前→( なら method、型→名前→;|= なら field
            int depth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (depth == 0) {
                    if (t.is(";")) {
                        state.idx = save;
                        parseFieldDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("=")) {
                        state.idx = save;
                        parseFieldDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("(")) {
                        state.idx = save;
                        parseMethodDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("{") || t.is("}")) {
                        state.idx = save;
                        return false;
                    }
                }
                if (t.is("(") || t.is("[") || t.is("<")) {
                    depth++;
                } else if (t.is(")") || t.is("]")) {
                    if (depth > 0) {
                        depth--;
                    }
                } else if (t.is(">") || t.is(">>") || t.is(">>>")) {
                    depth = Math.max(0, depth - t.text.length());
                }
                state.next();
            }
            state.idx = save;
            return false;
        }


        void parseFieldDecl(JavaClassInfo cls, List<String> mods,
                                     List<String> annotations, String comment) {
            int startPos = state.peek().start;
            int lastIdentEnd = startPos;
            String fieldName = "";
            // 型 + 最初の名前を読み込み (; / = / , に到達するまで)
            int depth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (depth == 0 && (t.is(";") || t.is("=") || t.is(","))) {
                    break;
                }
                if (t.is("(") || t.is("[") || t.is("<")) {
                    depth++;
                } else if (t.is(")") || t.is("]")) {
                    if (depth > 0) {
                        depth--;
                    }
                } else if (t.is(">") || t.is(">>") || t.is(">>>")) {
                    depth = Math.max(0, depth - t.text.length());
                }
                if (depth == 0 && t.type == JavaToken.Type.IDENT) {
                    fieldName = t.text;
                    lastIdentEnd = t.start;
                }
                state.next();
            }
            String type = state.src.substring(startPos, lastIdentEnd).trim();
            JavaFieldInfo first = addField(cls, mods, annotations, comment, fieldName, type);
            // 初期化子が匿名クラス/ラムダなら本体を吸い上げて f.inlineMethods に格納する。
            if (!state.atEnd() && state.peek().is("=")) {
                state.next(); // '=' を消費
                state.expr.tryParseInlineInitializer(first);
                state.skipUntilCommaOrSemicolonRespectingBlocks();
            }
            // 追加変数 (int a, b, c = 1)
            while (!state.atEnd() && state.peek().is(",")) {
                state.next(); // ','
                if (state.atEnd() || state.peek().type != JavaToken.Type.IDENT) {
                    break;
                }
                String name2 = state.next().text;
                // 配列ブラケット `b[]` を型に追加
                String type2 = type;
                while (!state.atEnd() && state.peek().is("[") && state.peek(1).is("]")) {
                    state.next();
                    state.next();
                    type2 = type2 + "[]";
                }
                JavaFieldInfo extra = addField(cls, mods, annotations, comment, name2, type2);
                if (!state.atEnd() && state.peek().is("=")) {
                    state.next();
                    state.expr.tryParseInlineInitializer(extra);
                    state.skipUntilCommaOrSemicolonRespectingBlocks();
                }
            }
            // ; までスキップ
            state.skipUntilSemicolonRespectingBlocks();
        }


        JavaFieldInfo addField(JavaClassInfo cls, List<String> mods,
                                        List<String> annotations, String comment,
                                        String name, String type) {
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(name);
            f.setType(JavaParseSupport.normalizeType(JavaParseSupport.stripAnnotations(type)));
            f.setVisibility(Visibility.fromModifiers(mods));
            f.setStatic(mods.contains("static"));
            f.setFinal(mods.contains("final"));
            f.getAnnotations().addAll(annotations);
            f.setComment(comment);
            cls.getFields().add(f);
            return f;
        }


        void parseMethodDecl(JavaClassInfo cls, List<String> mods,
                                      List<String> annotations, String comment) {
            if (state.peek().is("<")) {
                state.skipBalanced("<", ">");
            }
            int startPos = state.peek().start;
            int lastIdentEnd = startPos;
            String methodName = "";
            while (!state.atEnd() && !state.peek().is("(")) {
                JavaToken t = state.peek();
                if (t.type == JavaToken.Type.IDENT) {
                    methodName = t.text;
                    lastIdentEnd = t.start;
                }
                state.next();
            }
            if (state.atEnd()) {
                return;
            }
            String returnType = JavaParseSupport.stripAnnotations(state.src.substring(startPos, lastIdentEnd).trim());
            boolean isConstructor = returnType.isEmpty()
                    || returnType.equals(methodName)
                    || cls.getSimpleName().equals(methodName);

            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(methodName);
            m.setReturnType(isConstructor ? "" : JavaParseSupport.normalizeType(returnType));
            m.setVisibility(Visibility.fromModifiers(mods));
            m.setStatic(mods.contains("static"));
            m.setAbstract(mods.contains("abstract")
                    || cls.getKind() == JavaClassInfo.Kind.INTERFACE
                    || cls.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE);
            m.setConstructor(isConstructor);
            m.getAnnotations().addAll(annotations);
            m.setComment(comment);
            // パラメータ
            parseParameters(m);
            // throws Foo, Bar.Baz, com.x.Quux
            if (!state.atEnd() && state.peek().isKw("throws")) {
                state.next();
                m.getThrowsTypes().addAll(state.readTypeList());
            }
            // 残り (アノテーション付きパラメータ後の改行ノイズなど) を { または ; までスキップ
            while (!state.atEnd() && !state.peek().is("{") && !state.peek().is(";")) {
                state.next();
            }
            if (state.peek().is(";")) {
                state.next();
                cls.getMethods().add(m);
                return;
            }
            if (!state.peek().is("{")) {
                cls.getMethods().add(m);
                return;
            }
            JavaToken openBrace = state.peek();
            state.next();
            state.stmt.extractCallsInBody(m);
            // extractCallsInBody は対応する '}' を消費して戻る。
            // 直前に消費したトークン (= '}') の start を本体終端とする。
            int bodyStart = openBrace.end;
            int bodyEnd = state.idx > 0 ? state.tokens.get(state.idx - 1).start : openBrace.end;
            state.collectBodyComments(m, bodyStart, bodyEnd);
            cls.getMethods().add(m);
        }


        void parseParameters(JavaMethodInfo m) {
            if (!state.peek().is("(")) {
                return;
            }
            state.next();
            int parenDepth = 1;
            int angleDepth = 0;
            int paramStart = state.peek().start;
            int paramEnd = paramStart;
            List<int[]> ranges = new ArrayList<>();
            while (!state.atEnd() && parenDepth > 0) {
                JavaToken t = state.peek();
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        if (paramEnd > paramStart) {
                            ranges.add(new int[]{paramStart, paramEnd});
                        }
                        state.next();
                        break;
                    }
                } else if (t.is("<")) {
                    angleDepth++;
                } else if (t.is(">")) {
                    angleDepth = Math.max(0, angleDepth - 1);
                } else if (t.is(">>") || t.is(">>>")) {
                    angleDepth = Math.max(0, angleDepth - t.text.length());
                } else if (t.is(",") && parenDepth == 1 && angleDepth == 0) {
                    ranges.add(new int[]{paramStart, paramEnd});
                    state.next();
                    paramStart = state.peek().start;
                    paramEnd = paramStart;
                    continue;
                }
                paramEnd = t.end;
                state.next();
            }
            for (int[] r : ranges) {
                String raw = state.src.substring(r[0], r[1]).trim();
                // アノテーション・修飾子を取り除き、最後の識別子をパラメータ名、それ以前を型とする
                String[] sp = JavaParseSupport.stripAnnotations(raw).trim().split("\\s+");
                if (sp.length == 0 || sp[0].isEmpty()) {
                    continue;
                }
                String paramName;
                String type;
                if (sp.length == 1) {
                    type = sp[0];
                    paramName = "";
                } else {
                    paramName = sp[sp.length - 1];
                    StringBuilder tb = new StringBuilder();
                    for (int i = 0; i < sp.length - 1; i++) {
                        if (tb.length() > 0) {
                            tb.append(' ');
                        }
                        tb.append(sp[i]);
                    }
                    type = tb.toString();
                }
                // final 等の修飾子を型から除外
                type = type.replaceAll("(^|\\s)(final|in|out|inout)(\\s|$)", " ").trim();
                m.getParameterTypes().add(JavaParseSupport.normalizeType(type));
                m.getParameterNames().add(paramName);
            }
        }
}
