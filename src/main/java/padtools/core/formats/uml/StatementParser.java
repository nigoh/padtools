package padtools.core.formats.uml;

import padtools.core.formats.java.JavaToken;
import java.util.*;

/** メソッド本体の文・制御構造を解析する。 */
final class StatementParser {

    private final ParserState state;

    StatementParser(ParserState state) {
        this.state = state;
    }


        /**
         * メソッド本体 ({@code {} は呼び出し前に消費済み) を再帰的に解析し、
         * 文ツリーを {@link JavaMethodInfo#getStatements()} に格納する。
         * 終了時には対応する {@code }} まで消費している。
         */
        void extractCallsInBody(JavaMethodInfo m) {
            // bodyStart: 開き '{' の end オフセット（消費済みなので idx-1 が '{' トークン）
            // これより前のコメント（メソッド宣言コメント等）はスキップする
            int bodyStart = (state.idx > 0) ? state.tokens.get(state.idx - 1).end : 0;
            state.nextBodyCommentIdx = 0;
            while (state.nextBodyCommentIdx < state.comments.size()
                    && state.comments.get(state.nextBodyCommentIdx).start < bodyStart) {
                state.nextBodyCommentIdx++;
            }
            parseStatementBlock(m.getStatements());
        }


        /** 開き {@code {} は消費済み、対応する {@code }} を消費して戻る。 */
        void parseStatementBlock(List<JavaMethodInfo.Statement> out) {
            while (!state.atEnd()) {
                if (state.peek().is("}")) {
                    state.next();
                    return;
                }
                parseStatement(out);
            }
        }


        /** 1 つの文を読む。 */
        void parseStatement(List<JavaMethodInfo.Statement> out) {
            // 現在のトークン位置より前にあるコメントを InlineComment として出力
            state.emitPrecedingComments(out);
            if (state.peek().is(";")) {
                state.next();
                return;
            }
            if (state.peek().is("{")) {
                state.next();
                parseStatementBlock(out);
                return;
            }
            // ローカルクラス / record / interface / enum: 修飾子・アノテーション前置を許容
            int localDeclKw = state.peekLocalClassDeclKeyword();
            if (localDeclKw >= 0) {
                parseLocalClassDecl();
                return;
            }
            if (state.peek().isKw("if")) {
                parseIf(out);
                return;
            }
            if (state.peek().isKw("while")) {
                parseWhile(out);
                return;
            }
            if (state.peek().isKw("for")) {
                parseFor(out);
                return;
            }
            if (state.peek().isKw("do")) {
                parseDoWhile(out);
                return;
            }
            if (state.peek().isKw("switch")) {
                parseSwitch(out);
                return;
            }
            if (state.peek().isKw("try")) {
                parseTry(out);
                return;
            }
            if (state.peek().isKw("synchronized")) {
                parseSynchronized(out);
                return;
            }
            if (state.peek().isKw("return")) {
                state.expr.parseReturn(out);
                return;
            }
            if (state.peek().isKw("throw")) {
                state.expr.parseThrow(out);
                return;
            }
            if (state.peek().isKw("break")) {
                parseBreak(out);
                return;
            }
            if (state.peek().isKw("continue")) {
                parseContinue(out);
                return;
            }
            // `yield expr;` は switch アーム内でのみ文として意味を持つ
            // (Java 14+ switch 式)。switch の外では IDENT として扱う。
            if (state.switchDepth > 0 && state.peek().isKw("yield") && state.looksLikeYieldStatement()) {
                state.expr.parseYield(out);
                return;
            }
            // ローカル変数宣言: [final] Type varName [= expr];
            if (state.looksLikeLocalVarDecl()) {
                state.expr.parseLocalVarDecl(out);
                return;
            }
            state.expr.parseExpressionStatement(out);
        }


        /**
         * メソッド本体内のローカル型宣言 ({@code class}/{@code record}/
         * {@code interface}/{@code enum}) を読み、トップレベル/メンバ宣言と同じく
         * {@link #results} に追加する。enclosingClass には現在の最も内側のクラス名を
         * 設定する。
         */
        void parseLocalClassDecl() {
            int declStart = state.peek().start;
            List<String> annotations = state.readAnnotations();
            List<String> mods = state.readModifiers();
            String comment = state.findCommentBefore(declStart);
            if (state.peek().isKw("class")) {
                state.decl.parseClassDecl(JavaClassInfo.Kind.CLASS, mods, annotations, comment);
            } else if (state.peek().isKw("interface")) {
                state.decl.parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, annotations, comment);
            } else if (state.peek().isKw("enum")) {
                state.decl.parseClassDecl(JavaClassInfo.Kind.ENUM, mods, annotations, comment);
            } else if (state.peek().isKw("record")) {
                state.decl.parseClassDecl(JavaClassInfo.Kind.RECORD, mods, annotations, comment);
            } else {
                // 万一一致しない場合は文として進める
                state.next();
            }
        }


        /** {@code if (...)} 単体 (else 連鎖含む) を 1 つの {@link JavaMethodInfo.Block} として読む。 */
        void parseIf(List<JavaMethodInfo.Statement> out) {
            state.next(); // if
            String cond = consumeParens(out);
            JavaMethodInfo.Block block = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.IF);
            JavaMethodInfo.Branch first = new JavaMethodInfo.Branch("if", cond);
            block.getBranches().add(first);
            out.add(block);
            parseSubStatement(first.getBody());
            while (!state.atEnd() && state.peek().isKw("else")) {
                state.next(); // else
                if (state.peek().isKw("if")) {
                    state.next(); // if
                    String c2 = consumeParens(out);
                    JavaMethodInfo.Branch ei = new JavaMethodInfo.Branch("else if", c2);
                    block.getBranches().add(ei);
                    parseSubStatement(ei.getBody());
                } else {
                    JavaMethodInfo.Branch e = new JavaMethodInfo.Branch("else", "");
                    block.getBranches().add(e);
                    parseSubStatement(e.getBody());
                    break;
                }
            }
        }


        void parseWhile(List<JavaMethodInfo.Statement> out) {
            state.next(); // while
            String cond = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.WHILE);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("while", cond);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }


        void parseFor(List<JavaMethodInfo.Statement> out) {
            state.next(); // for
            String header = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.FOR);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("for", header);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }


        void parseDoWhile(List<JavaMethodInfo.Statement> out) {
            state.next(); // do
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.DO_WHILE);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("do", "");
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
            if (!state.atEnd() && state.peek().isKw("while")) {
                state.next(); // while
                String cond = consumeParens(out);
                br.setLabel(cond);
                if (state.peek().is(";")) {
                    state.next();
                }
            }
        }


        void parseSynchronized(List<JavaMethodInfo.Statement> out) {
            state.next(); // synchronized
            String lock = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.SYNCHRONIZED);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("synchronized", lock);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }


        void parseSwitch(List<JavaMethodInfo.Statement> out) {
            state.next(); // switch
            String cond = consumeParens(out);
            JavaMethodInfo.Block sw = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.SWITCH);
            // SWITCH 本体の式自体は最初の Branch に "switch" として保持
            JavaMethodInfo.Branch head = new JavaMethodInfo.Branch("switch", cond);
            sw.getBranches().add(head);
            out.add(sw);
            if (!state.peek().is("{")) {
                return;
            }
            state.next(); // {
            state.switchDepth++;
            try {
                JavaMethodInfo.Branch currentCase = null;
                while (!state.atEnd() && !state.peek().is("}")) {
                    if (state.peek().isKw("case") || state.peek().isKw("default")) {
                        String type;
                        String label;
                        if (state.peek().isKw("default")) {
                            state.next();
                            type = "default";
                            label = "";
                        } else {
                            state.next(); // case
                            int s = state.peek().start;
                            int e = s;
                            // パターン case の `when` ガード (Java 21+) も含めて
                            // ラベル末端 (`:` か `->`) まで読む。括弧深度を加味して
                            // ラムダ等での `,` を case 区切りと誤認しない。
                            int paren = 0;
                            while (!state.atEnd()
                                    && !(paren == 0 && (state.peek().is(":") || state.peek().is("->")))) {
                                JavaToken t = state.peek();
                                if (t.is("(") || t.is("[") || t.is("{")) {
                                    paren++;
                                } else if (t.is(")") || t.is("]") || t.is("}")) {
                                    if (paren > 0) {
                                        paren--;
                                    }
                                }
                                e = t.end;
                                state.next();
                            }
                            type = "case";
                            label = state.src.substring(s, e).trim();
                        }
                        if (!state.atEnd() && (state.peek().is(":") || state.peek().is("->"))) {
                            state.next();
                        }
                        currentCase = new JavaMethodInfo.Branch(type, label);
                        sw.getBranches().add(currentCase);
                    } else if (currentCase != null) {
                        parseStatement(currentCase.getBody());
                    } else {
                        // case の前に何かある異常系: 1 トークン進めるのみ
                        state.next();
                    }
                }
                if (!state.atEnd() && state.peek().is("}")) {
                    state.next();
                }
            } finally {
                state.switchDepth--;
            }
        }


        void parseTry(List<JavaMethodInfo.Statement> out) {
            state.next(); // try
            // try-with-resources の括弧があれば読み飛ばす (内部の呼び出しは out に追加)
            if (state.peek().is("(")) {
                consumeParens(out);
            }
            JavaMethodInfo.Block t = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.TRY);
            JavaMethodInfo.Branch tryBranch = new JavaMethodInfo.Branch("try", "");
            t.getBranches().add(tryBranch);
            out.add(t);
            parseSubStatement(tryBranch.getBody());
            while (!state.atEnd() && state.peek().isKw("catch")) {
                state.next();
                String except = consumeParens(out);
                JavaMethodInfo.Branch c = new JavaMethodInfo.Branch("catch", except);
                t.getBranches().add(c);
                parseSubStatement(c.getBody());
            }
            if (!state.atEnd() && state.peek().isKw("finally")) {
                state.next();
                JavaMethodInfo.Branch f = new JavaMethodInfo.Branch("finally", "");
                t.getBranches().add(f);
                parseSubStatement(f.getBody());
            }
        }


        /** {@code if(cond)} の後ろなど、単一文または {@code {}} ブロックを読む。 */
        void parseSubStatement(List<JavaMethodInfo.Statement> out) {
            if (state.atEnd()) {
                return;
            }
            if (state.peek().is("{")) {
                state.next();
                parseStatementBlock(out);
            } else {
                parseStatement(out);
            }
        }


        /** {@code break [label];} 文を読む。 */
        void parseBreak(List<JavaMethodInfo.Statement> out) {
            state.next(); // break
            String label = "";
            if (!state.atEnd() && state.peek().type == JavaToken.Type.IDENT) {
                label = state.peek().text;
                state.next();
            }
            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }
            out.add(new JavaMethodInfo.Break(label));
        }


        /** {@code continue [label];} 文を読む。 */
        void parseContinue(List<JavaMethodInfo.Statement> out) {
            state.next(); // continue
            String label = "";
            if (!state.atEnd() && state.peek().type == JavaToken.Type.IDENT) {
                label = state.peek().text;
                state.next();
            }
            if (!state.atEnd() && state.peek().is(";")) {
                state.next();
            }
            out.add(new JavaMethodInfo.Continue(label));
        }


        /**
         * 開き {@code (} で始まる括弧内容を読み、ソース文字列としての中身を返す。
         * 内部に呼び出し式があれば {@code out} に追加 (条件式評価で実行される呼び出しは
         * 制御ブロックの直前に出るので、シーケンス図上もそのように見える)。
         */
        String consumeParens(List<JavaMethodInfo.Statement> out) {
            if (!state.peek().is("(")) {
                return "";
            }
            int openEnd = state.peek().end;
            state.next(); // (
            int depth = 1;
            int closeStart = openEnd;
            while (!state.atEnd() && depth > 0) {
                JavaToken t = state.peek();
                if (t.is("(")) {
                    depth++;
                } else if (t.is(")")) {
                    depth--;
                    if (depth == 0) {
                        closeStart = t.start;
                        state.next();
                        return state.src.substring(openEnd, closeStart).trim();
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
            return state.src.substring(openEnd, closeStart).trim();
        }
}
