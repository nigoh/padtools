package padtools.core.formats.uml;

import padtools.core.formats.java.JavaToken;
import java.util.*;

/** 式文・ローカル変数・インライン式 (ラムダ/匿名/メソッド参照) を解析する。 */
final class ExpressionParser {

    private final ParserState state;

    ExpressionParser(ParserState state) {
        this.state = state;
    }


        /**
         * ローカル変数宣言 {@code [final] Type varName [= initExpr];} を読んで
         * {@link JavaMethodInfo.LocalVar} を {@code out} に追加する。
         *
         * <p>initExpr にラムダ/匿名クラスが含まれる場合は
         * {@link JavaMethodInfo.LocalVar#getInlineMethods()} にコールバック本体を追加する。
         * initExpr 中のメソッド呼び出しは {@link JavaMethodInfo.Call} として別途生成せず、
         * initExpr 文字列に包含して 1 アクションノードとして表示する。</p>
         */
        void parseLocalVarDecl(List<JavaMethodInfo.Statement> out) {
            // optional "final"
            while (!state.atEnd() && state.peek().isKw("final")) {
                state.next();
            }
            // 型名: ドット連鎖 + ジェネリクス + 配列を含む
            int typeStart = state.peek().start;
            // ドット連鎖
            if (!state.atEnd() && state.peek().type == JavaToken.Type.IDENT) {
                state.next();
                while (!state.atEnd() && state.peek().is(".")
                        && state.idx + 1 < state.tokens.size()
                        && state.tokens.get(state.idx + 1).type == JavaToken.Type.IDENT
                        && (state.idx + 2 >= state.tokens.size() || !state.tokens.get(state.idx + 2).is("("))) {
                    state.next(); // '.'
                    state.next(); // IDENT
                }
            }
            // ジェネリクス <...>
            if (!state.atEnd() && state.peek().is("<")) {
                state.skipBalanced("<", ">");
            }
            // 配列 [] (空のみ)
            while (!state.atEnd() && state.peek().is("[")
                    && state.idx + 1 < state.tokens.size() && state.tokens.get(state.idx + 1).is("]")) {
                state.next();
                state.next();
            }
            int typeEnd = state.idx > 0 ? state.tokens.get(state.idx - 1).end : typeStart;
            String type = JavaParseSupport.normalizeType(state.src.substring(typeStart, typeEnd));

            // 変数名
            String varName = "";
            if (!state.atEnd() && state.peek().type == JavaToken.Type.IDENT) {
                varName = state.peek().text;
                state.next();
            }
            // 変数名の後の [] (C スタイル: int arr[])
            while (!state.atEnd() && state.peek().is("[")
                    && state.idx + 1 < state.tokens.size() && state.tokens.get(state.idx + 1).is("]")) {
                state.next();
                state.next();
            }

            // 初期化式
            String initExpr = "";
            List<JavaMethodInfo> captured = null;
            if (!state.atEnd() && state.peek().is("=")) {
                state.next(); // '='
                // Java 14+ switch 式: int r = switch(x) {...}; を構造化して取り込む
                if (!state.atEnd() && state.peek().isKw("switch") && state.idx + 1 < state.tokens.size()
                        && state.tokens.get(state.idx + 1).is("(")) {
                    int swStart = state.peek().start;
                    state.stmt.parseSwitch(out);
                    initExpr = state.idx > 0
                            ? state.src.substring(swStart, state.tokens.get(state.idx - 1).end).trim() : "switch(...)";
                } else {
                    // ラムダ/匿名クラスがあれば tryParseInlineExpression でコールバックを取り込む
                    int saveBefore = state.idx;
                    captured = tryParseInlineExpression(type, varName);
                    if (captured == null || captured.isEmpty()) {
                        state.idx = saveBefore;
                        // initExpr をソース文字列として収集
                        int initStart = state.atEnd() ? 0 : state.peek().start;
                        int initEndOff = initStart;
                        int depth = 0;
                        while (!state.atEnd()) {
                            JavaToken t = state.peek();
                            if (depth == 0 && (t.is(";") || t.is("}"))) {
                                break;
                            }
                            if (t.is("(") || t.is("[") || t.is("{")) {
                                depth++;
                            } else if (t.is(")") || t.is("]") || t.is("}")) {
                                if (depth > 0) {
                                    depth--;
                                } else {
                                    break;
                                }
                            }
                            initEndOff = t.end;
                            state.next();
                        }
                        initExpr = initEndOff > initStart
                                ? state.src.substring(initStart, initEndOff).trim() : "";
                    } else {
                        initExpr = "<lambda>";
                    }
                }
            }

            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }

            JavaMethodInfo.LocalVar localVar = new JavaMethodInfo.LocalVar(type, varName, initExpr);
            if (captured != null) {
                localVar.getInlineMethods().addAll(captured);
            }
            out.add(localVar);
        }


        /**
         * {@code return [expr];} 文を読む。式中の呼び出しは {@link JavaMethodInfo.Call} として
         * {@code out} に追加し、最後に {@link JavaMethodInfo.Return} を 1 件追加する。
         */
        void parseReturn(List<JavaMethodInfo.Statement> out) {
            state.next(); // return
            int startPos = state.atEnd() ? 0 : state.peek().start;
            int endPos = startPos;
            int parenDepth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (parenDepth == 0 && t.is(";")) {
                    break;
                }
                if (parenDepth == 0 && t.is("}")) {
                    break;
                }
                // Java 14+ switch 式: return switch(x) {...}; を構造化して取り込む
                if (t.isKw("switch") && state.peek(1).is("(")) {
                    endPos = t.end;
                    state.stmt.parseSwitch(out);
                    continue;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // 匿名クラス本体・ラムダ本体・配列初期化など
                    state.next();
                    state.stmt.parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && state.peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = state.idx > 0 && state.tokens.get(state.idx - 1).isKw("new");
                    if (!JavaParseSupport.isControlKeyword(name) && !afterNew) {
                        String receiver = state.findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                endPos = t.end;
                state.next();
            }
            String expr = endPos > startPos ? state.src.substring(startPos, endPos).trim() : "";
            out.add(new JavaMethodInfo.Return(expr));
            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }
        }


        /**
         * {@code throw expr;} 文を読む。式中の呼び出しは {@link JavaMethodInfo.Call} として
         * {@code out} に追加し、最後に {@link JavaMethodInfo.Throw} を 1 件追加する。
         */
        void parseThrow(List<JavaMethodInfo.Statement> out) {
            state.next(); // throw
            int startPos = state.atEnd() ? 0 : state.peek().start;
            int endPos = startPos;
            int parenDepth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (parenDepth == 0 && t.is(";")) {
                    break;
                }
                if (parenDepth == 0 && t.is("}")) {
                    break;
                }
                // Java 14+ switch 式: throw switch(x) {...}; を構造化して取り込む
                if (t.isKw("switch") && state.peek(1).is("(")) {
                    endPos = t.end;
                    state.stmt.parseSwitch(out);
                    continue;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    state.next();
                    state.stmt.parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && state.peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = state.idx > 0 && state.tokens.get(state.idx - 1).isKw("new");
                    if (!JavaParseSupport.isControlKeyword(name) && !afterNew) {
                        String receiver = state.findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                endPos = t.end;
                state.next();
            }
            String expr = endPos > startPos ? state.src.substring(startPos, endPos).trim() : "";
            out.add(new JavaMethodInfo.Throw(expr));
            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }
        }


        /**
         * {@code yield expr;} 文 (Java 14+ switch 式) を読む。
         * 式中の呼び出しは {@link JavaMethodInfo.Call} として {@code out} に追加し、
         * 最後に {@link JavaMethodInfo.Yield} を 1 件追加する。
         * {@code yield} は文脈依存キーワード (IDENT) なので、呼び出し側で
         * switch アーム内であることを確認してから呼ぶこと。
         */
        void parseYield(List<JavaMethodInfo.Statement> out) {
            state.next(); // yield
            int startPos = state.atEnd() ? 0 : state.peek().start;
            int endPos = startPos;
            int parenDepth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (parenDepth == 0 && t.is(";")) {
                    break;
                }
                if (parenDepth == 0 && t.is("}")) {
                    break;
                }
                // yield switch(...) のネスト switch 式も構造化して取り込む
                if (t.isKw("switch") && state.peek(1).is("(")) {
                    endPos = t.end;
                    state.stmt.parseSwitch(out);
                    continue;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // ラムダ本体・匿名クラス・配列初期化など
                    state.next();
                    state.stmt.parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && state.peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = state.idx > 0 && state.tokens.get(state.idx - 1).isKw("new");
                    if (!JavaParseSupport.isControlKeyword(name) && !afterNew) {
                        String receiver = state.findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                endPos = t.end;
                state.next();
            }
            String expr = endPos > startPos ? state.src.substring(startPos, endPos).trim() : "";
            out.add(new JavaMethodInfo.Yield(expr));
            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }
        }


        /**
         * 通常の式文 ({@code ;} 終端) または特殊文を読む。途中に出現する呼び出しを
         * {@code out} に追加する。ブロック {@code {} に出会ったら再帰的に文として展開し、
         * 同じ {@code out} に呼び出しを追加する (匿名クラス本体・ラムダ本体・配列初期化など)。
         *
         * <p>文頭で {@code [this.] IDENT = <lambda|匿名クラス|メソッド参照>} のパターンを
         * 検出した場合は、囲っているクラスに同名フィールドがあればそのフィールドの
         * {@link JavaFieldInfo#getInlineMethods()} にコールバック本体を取り込む
         * (コンストラクタ内/任意メソッド内のフィールド代入対応)。</p>
         */
        void parseExpressionStatement(List<JavaMethodInfo.Statement> out) {
            // 文頭の "[this.] IDENT = <inline>" を検出
            tryCaptureFieldAssignmentInline();
            int parenDepth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (parenDepth == 0 && t.is(";")) {
                    state.next();
                    return;
                }
                if (parenDepth == 0 && t.is("}")) {
                    return;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // 匿名クラス本体・ラムダ本体・配列初期化など。
                    // 内部の呼び出しはフラットに out に追加する (構造化はしない)。
                    state.next();
                    state.stmt.parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && state.peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = state.idx > 0
                            && state.tokens.get(state.idx - 1).isKw("new");
                    // Java 14+ の switch 式 (`int x = switch(y){...};` /
                    // `foo(switch(y){...})`) を構造化して取り込む。switch 自身は
                    // 呼び出しではないので Call 化はせず、parseSwitch に委譲。
                    if ("switch".equals(name) && !afterNew) {
                        state.stmt.parseSwitch(out);
                        continue;
                    }
                    if (!JavaParseSupport.isControlKeyword(name) && !afterNew) {
                        String receiver = state.findReceiver();
                        JavaMethodInfo.Call call = new JavaMethodInfo.Call(receiver, name);
                        out.add(call);
                        // 引数の先頭が lambda/匿名クラス/メソッド参照ならコールバックとして
                        // この Call.inlineMethods に紐づける (setOnClickListener(...) 等)
                        state.next(); // IDENT
                        if (!state.atEnd() && state.peek().is("(")) {
                            state.next(); // '('
                            // 先に定数シンボル ({@code FOO.BAR_BAZ}) を probe-only で確認
                            tryCaptureFirstConstantArgument(call);
                            tryCaptureFirstInlineArgument(call);
                        }
                        // 上で `(` を 1 つ消費したが、外側の parenDepth とは別管理にした
                        // ため、ここで `)` まで読み進める間の depth 整合性を保つ必要がある。
                        // 簡略化のため parenDepth に +1 して `)` での -1 を待つ。
                        parenDepth++;
                        continue;
                    }
                }
                // メソッド参照: Foo::bar / obj.field::bar (`::new` は除外)
                if (t.is("::") && state.peek(1).type == JavaToken.Type.IDENT) {
                    String name = state.peek(1).text;
                    if (!"new".equals(name)) {
                        String receiver = state.findReceiverBeforeColonColon();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                state.next();
            }
        }


        /**
         * 文頭で {@code [this.] IDENT = <inline>} の代入パターンを検出した場合、
         * 同名フィールドの inlineMethods にコールバック本体を取り込み、
         * 代入の右辺 (inline 部分) を消費する。{@code =} は消費するが文末 {@code ;}
         * は呼び出し元 ({@link #parseExpressionStatement}) で処理させる。
         *
         * <p>該当パターンに見えない場合は idx を変更しない。</p>
         */
        void tryCaptureFieldAssignmentInline() {
            if (state.classStack.isEmpty()) {
                return;
            }
            int save = state.idx;
            String targetName = null;
            // `this . IDENT =` あるいは `IDENT =` を検出
            if (state.peek().isKw("this") && state.peek(1).is(".")
                    && state.peek(2).type == JavaToken.Type.IDENT
                    && state.peek(3).is("=") && !state.peek(3).is("==")) {
                state.idx += 2; // this .
                targetName = state.peek().text;
                state.idx += 2; // IDENT =
            } else if (state.peek().type == JavaToken.Type.IDENT
                    && state.peek(1).is("=") && !state.peek(1).is("==")) {
                targetName = state.peek().text;
                state.idx += 2;
            } else {
                return;
            }
            // 右辺で inline 式を試す
            JavaClassInfo cls = state.classStack.get(state.classStack.size() - 1);
            JavaFieldInfo field = JavaParseSupport.findFieldByName(cls, targetName);
            String hint = field != null ? field.getType() : null;
            List<JavaMethodInfo> captured = tryParseInlineExpression(hint, targetName);
            if (captured == null || captured.isEmpty()) {
                state.idx = save;
                return;
            }
            if (field != null) {
                field.getInlineMethods().addAll(captured);
            } else {
                // フィールドがクラスに見つからない場合は遅延解決のため pendingAssignments
                // に積んでおき、クラス本体パース完了後にマッチさせる
                state.pendingFieldAssignments.add(
                        new ParserState.PendingAssignment(cls, targetName, captured));
            }
            // 右辺は inline 部分のみ消費した。文末 `;` は外側で処理。
        }


        /**
         * 呼び出しの最初の引数がラムダ/匿名クラスなら、それを {@code call.inlineMethods}
         * に取り込む。呼び出し直前の {@code (} は既に消費済み。該当しなければ idx を変更しない。
         *
         * <p>メソッド参照 ({@code Foo::bar}) は引数として現れたときは parseExpressionStatement
         * の {@code ::} ハンドラで親メソッドの呼び出しリストに記録するため、ここでは扱わない。</p>
         */
        void tryCaptureFirstInlineArgument(JavaMethodInfo.Call call) {
            // ラムダ・匿名クラスのみを対象とする (メソッド参照は除外)
            if (!state.peek().isKw("new") && !looksLikeLambdaStart()) {
                return;
            }
            int save = state.idx;
            List<JavaMethodInfo> captured = tryParseInlineExpression(null, call.getMethodName());
            if (captured == null || captured.isEmpty()) {
                state.idx = save;
                return;
            }
            call.getInlineMethods().addAll(captured);
        }


        /**
         * 呼び出しの第 1 引数が定数シンボル参照 (例:
         * {@code VehiclePropertyIds.HVAC_TEMPERATURE_SET},
         * {@code Manifest.permission.READ_PHONE_STATE},
         * 単独 {@code MAX_VALUE}) の場合に、その文字列を
         * {@link JavaMethodInfo.Call#setFirstArgLabel} に格納する。
         *
         * <p>シーケンス図のラベルで {@code getProperty(HVAC_TEMPERATURE_SET)} のように
         * 引数を併記するため。判定基準は「ドット区切りの IDENT 列で、末尾セグメントが
         * UPPERCASE_WITH_UNDERSCORES 形式」。本メソッドは probe-only で idx を進めない
         * (引数本体の消費は呼び出し元の parseExpressionStatement に任せる)。</p>
         */
        void tryCaptureFirstConstantArgument(JavaMethodInfo.Call call) {
            if (state.atEnd() || state.peek().type != JavaToken.Type.IDENT) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(state.peek().text);
            int probe = state.idx + 1;
            while (probe + 1 < state.tokens.size()
                    && state.tokens.get(probe).is(".")
                    && state.tokens.get(probe + 1).type == JavaToken.Type.IDENT) {
                sb.append('.').append(state.tokens.get(probe + 1).text);
                probe += 2;
            }
            // 引数の終端: ',' または ')' のいずれかが直後でないと「単一の定数参照」とは
            // 言えない (`FOO + 1` や `FOO.method()` のような複合式を誤検出しないため)
            if (probe >= state.tokens.size()) {
                return;
            }
            JavaToken nxt = state.tokens.get(probe);
            if (!nxt.is(",") && !nxt.is(")")) {
                return;
            }
            String full = sb.toString();
            int lastDot = full.lastIndexOf('.');
            String lastSegment = lastDot < 0 ? full : full.substring(lastDot + 1);
            if (!JavaParseSupport.looksLikeConstantSymbol(lastSegment)) {
                return;
            }
            call.setFirstArgLabel(full);
        }


        /**
         * フィールド初期化子が {@code new SomeType(...) {...}} (匿名クラス) または
         * {@code args -> body} (ラムダ) または {@code Foo::bar} (メソッド参照) の場合、
         * その本体内に出現するメソッドを {@link JavaFieldInfo#getInlineMethods()} に格納する。
         *
         * <p>{@code =} は呼び出し側で既に消費済み。本メソッドは消費したトークンを
         * 巻き戻して呼び出し側の {@link #skipUntilSemicolonRespectingBlocks()} に
         * 安全に引き継ぐ責務を持つ (異常検出時は何もしない)。</p>
         */
        void tryParseInlineInitializer(JavaFieldInfo f) {
            List<JavaMethodInfo> captured = tryParseInlineExpression(f.getType(), f.getName());
            if (captured != null && !captured.isEmpty()) {
                f.getInlineMethods().addAll(captured);
            }
        }


        /**
         * 現在位置から「関数を変数として設定」する式 (匿名クラス / ラムダ / メソッド参照)
         * を検出して、本体から抽出したメソッド一覧を返す。検出できなければ idx を元に戻して
         * null を返す。
         *
         * @param samTypeHint SAM 型のヒント (フィールド型・パラメータ型など)。null/空でも動く。
         * @param nameHint    ヒント用の変数名 (フィールド名/受信側など)。null 可。
         */
        List<JavaMethodInfo> tryParseInlineExpression(String samTypeHint, String nameHint) {
            int save = state.idx;
            // 匿名クラス判定: new TypeName (args) { ... }
            if (state.peek().isKw("new")) {
                int probe = state.idx + 1;
                // 型名 (a.b.C<T>) をスキップ
                while (probe < state.tokens.size()) {
                    JavaToken t = state.tokens.get(probe);
                    if (t.type == JavaToken.Type.IDENT || t.is(".")) {
                        probe++;
                        continue;
                    }
                    break;
                }
                // ジェネリック型引数
                if (probe < state.tokens.size() && state.tokens.get(probe).is("<")) {
                    probe = state.skipBalancedAt(probe, "<", ">");
                }
                // 配列の場合 (new int[]{...}) は無視
                if (probe < state.tokens.size() && state.tokens.get(probe).is("[")) {
                    return null;
                }
                // 引数 (...)
                if (probe < state.tokens.size() && state.tokens.get(probe).is("(")) {
                    probe = state.skipBalancedAt(probe, "(", ")");
                } else {
                    return null;
                }
                // 直後が { なら匿名クラス本体
                if (probe < state.tokens.size() && state.tokens.get(probe).is("{")) {
                    state.idx = probe;
                    state.next(); // '{' を消費
                    JavaClassInfo dummy = new JavaClassInfo();
                    dummy.setSimpleName("$anon");
                    // classStack には push しない (inner-class 判定の副作用回避)
                    state.decl.parseClassBody(dummy);
                    return new ArrayList<>(dummy.getMethods());
                }
                state.idx = save;
                return null;
            }
            // ラムダ判定: x -> ... または (a, b) -> ...
            if (looksLikeLambdaStart()) {
                consumeLambdaParams();
                if (state.atEnd() || !state.peek().is("->")) {
                    state.idx = save;
                    return null;
                }
                state.next(); // '->'
                String samName = JavaParseSupport.resolveSamMethodName(samTypeHint, nameHint);
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(samName);
                m.setVisibility(Visibility.PUBLIC);
                if (!state.atEnd() && state.peek().is("{")) {
                    state.next();
                    state.stmt.parseStatementBlock(m.getStatements());
                } else {
                    // expression-bodied lambda: 文末 ';' は消費せず呼び出し元に残す
                    parseLambdaExpressionBody(m.getStatements());
                }
                List<JavaMethodInfo> out = new ArrayList<>();
                out.add(m);
                return out;
            }
            // メソッド参照判定: IDENT (. IDENT)* :: IDENT  (`::new` は除外)
            if (looksLikeMethodReferenceStart()) {
                String receiver = consumeMethodReferenceReceiver();
                if (!state.peek().is("::")) {
                    state.idx = save;
                    return null;
                }
                state.next(); // '::'
                if (state.atEnd() || state.peek().type != JavaToken.Type.IDENT
                        || "new".equals(state.peek().text)) {
                    state.idx = save;
                    return null;
                }
                String refTarget = state.next().text;
                String samName = JavaParseSupport.resolveSamMethodName(samTypeHint, nameHint);
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(samName);
                m.setVisibility(Visibility.PUBLIC);
                // 本体は単一呼び出しに展開しておく (シーケンス図がそのまま辿れる)
                m.getStatements().add(new JavaMethodInfo.Call(receiver, refTarget));
                List<JavaMethodInfo> out = new ArrayList<>();
                out.add(m);
                return out;
            }
            return null;
        }


        /**
         * 現在位置から「ラムダ式の開始」に見えるか? を peek で判定する。
         * 単一識別子の後に {@code ->} が来るか、釣り合った {@code (...)} の後に {@code ->}
         * が来るパターンを検出する。
         */
        boolean looksLikeLambdaStart() {
            if (state.peek().type == JavaToken.Type.IDENT && state.peek(1).is("->")) {
                return true;
            }
            if (state.peek().is("(")) {
                int after = state.skipBalancedAt(state.idx, "(", ")");
                if (after < state.tokens.size() && state.tokens.get(after).is("->")) {
                    return true;
                }
            }
            return false;
        }


        /** ラムダのパラメータ部 (識別子 1 個または {@code (a, b)}) を消費する。 */
        void consumeLambdaParams() {
            if (state.peek().is("(")) {
                state.skipBalanced("(", ")");
            } else if (state.peek().type == JavaToken.Type.IDENT) {
                state.next();
            }
        }


        /** 現在位置から {@code IDENT (. IDENT)* ::} の形で始まっていそうかを peek で判定する。 */
        boolean looksLikeMethodReferenceStart() {
            if (state.peek().type != JavaToken.Type.IDENT) {
                return false;
            }
            int probe = state.idx + 1;
            while (probe + 1 < state.tokens.size()
                    && state.tokens.get(probe).is(".")
                    && state.tokens.get(probe + 1).type == JavaToken.Type.IDENT) {
                probe += 2;
            }
            return probe < state.tokens.size() && state.tokens.get(probe).is("::");
        }


        /** {@link #looksLikeMethodReferenceStart()} が true の前提で受信側を消費して文字列化する。 */
        String consumeMethodReferenceReceiver() {
            StringBuilder sb = new StringBuilder();
            sb.append(state.next().text);
            while (!state.atEnd() && state.peek().is(".")
                    && state.idx + 1 < state.tokens.size()
                    && state.tokens.get(state.idx + 1).type == JavaToken.Type.IDENT) {
                state.next(); // '.'
                sb.append('.').append(state.next().text);
            }
            return sb.toString();
        }


        /**
         * expression-bodied ラムダの本体 ({@code (...) -> EXPR} の EXPR 部) を読む。
         * 通常の {@link #parseExpressionStatement} と異なり、終端の {@code ;} は消費しない
         * (フィールド宣言の終端 {@code ;} はフィールドパーサ側で処理させるため)。
         * 呼び出し引数の中に現れた expression-bodied ラムダ ({@code foo(v -> bar(), 1)})
         * にも対応するため、parenDepth=0 で外側の {@code )} / {@code ,} に出会ったら
         * 消費せずに戻る。
         */
        void parseLambdaExpressionBody(List<JavaMethodInfo.Statement> out) {
            int parenDepth = 0;
            while (!state.atEnd()) {
                JavaToken t = state.peek();
                if (parenDepth == 0 && (t.is(";") || t.is("}")
                        || t.is(")") || t.is(","))) {
                    return;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                }
                if (t.type == JavaToken.Type.IDENT && state.peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = state.idx > 0
                            && state.tokens.get(state.idx - 1).isKw("new");
                    if (!JavaParseSupport.isControlKeyword(name) && !afterNew) {
                        String receiver = state.findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                state.next();
            }
        }
}
