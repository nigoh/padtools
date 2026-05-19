package padtools.core.formats.uml.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 解析インデックス DB のスキーマ初期化。
 *
 * <p>migration は持たない方針 ({@code schema_version} 不一致時は DB を退避して
 * 再構築する)。本クラスは「空 DB に対する V1 スキーマの一括 CREATE」だけを担う。</p>
 *
 * <p>PR1 では {@code meta} テーブルのみを作成する。残りのテーブル
 * (files / classes / refs / manifests / components / aidl_* / external_endpoints)
 * は後続 PR で追加する。</p>
 */
public final class SchemaInitializer {

    public static final int SCHEMA_VERSION = 1;

    private SchemaInitializer() {
    }

    /** 空 DB に対して V1 スキーマを CREATE し、meta 行を投入する。 */
    public static void initialize(Connection conn, String projectRoot, String toolVersion)
            throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            createTables(conn);
            insertMeta(conn, projectRoot, toolVersion);
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE meta ("
                + "  key   TEXT PRIMARY KEY,"
                + "  value TEXT NOT NULL"
                + ")"
            );
        }
    }

    private static void insertMeta(Connection conn, String projectRoot, String toolVersion)
            throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key, value) VALUES (?, ?)")) {
            putMeta(ps, "schema_version", Integer.toString(SCHEMA_VERSION));
            putMeta(ps, "project_root", projectRoot == null ? "" : projectRoot);
            putMeta(ps, "tool_version", toolVersion == null ? "" : toolVersion);
            putMeta(ps, "created_at", Long.toString(now));
            putMeta(ps, "updated_at", Long.toString(now));
        }
    }

    private static void putMeta(PreparedStatement ps, String key, String value) throws SQLException {
        ps.setString(1, key);
        ps.setString(2, value);
        ps.executeUpdate();
    }
}
