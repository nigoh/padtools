package padtools.core.formats.uml.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 解析インデックス DB のスキーマ初期化。
 *
 * <p>migration は持たない方針 ({@code schema_version} 不一致時は DB を退避して
 * 再構築する)。本クラスは「空 DB に対する現行スキーマの一括 CREATE」だけを担う。</p>
 *
 * <p>{@link #SCHEMA_VERSION} を変更したら、起動時に {@link IndexDatabase} が
 * 旧 DB を {@code index.db.discarded-v<N>-<timestamp>} に退避して
 * 再構築するので、CREATE 文を素直に増やすだけでよい。</p>
 */
public final class SchemaInitializer {

    /**
     * 現行スキーマバージョン。
     *
     * <ul>
     *   <li>v1 (PR1): {@code meta} のみ</li>
     *   <li>v2 (PR2): {@code modules / files / classes / class_interfaces /
     *                   class_imports / fields / methods} を追加</li>
     * </ul>
     */
    public static final int SCHEMA_VERSION = 2;

    private SchemaInitializer() {
    }

    /** 空 DB に対して現行スキーマを CREATE し、meta 行を投入する。 */
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
            // 0) meta
            st.executeUpdate(
                "CREATE TABLE meta ("
                + "  key   TEXT PRIMARY KEY,"
                + "  value TEXT NOT NULL"
                + ")"
            );

            // 1) modules (Gradle ":app", ":car-lib" 等)
            st.executeUpdate(
                "CREATE TABLE modules ("
                + "  id   INTEGER PRIMARY KEY,"
                + "  name TEXT NOT NULL UNIQUE,"
                + "  path TEXT NOT NULL"
                + ")"
            );

            // 2) files (.java / .aidl / AndroidManifest.xml / build.gradle ...)
            st.executeUpdate(
                "CREATE TABLE files ("
                + "  id          INTEGER PRIMARY KEY,"
                + "  path        TEXT NOT NULL UNIQUE,"
                + "  module_id   INTEGER REFERENCES modules(id) ON DELETE SET NULL,"
                + "  kind        TEXT NOT NULL,"
                + "  mtime       INTEGER NOT NULL,"
                + "  size        INTEGER NOT NULL,"
                + "  parsed_at   INTEGER NOT NULL,"
                + "  parse_error TEXT"
                + ")"
            );
            st.executeUpdate("CREATE INDEX idx_files_kind   ON files(kind)");
            st.executeUpdate("CREATE INDEX idx_files_module ON files(module_id)");

            // 3) classes (class/interface/enum/annotation/AIDL_INTERFACE/record)
            st.executeUpdate(
                "CREATE TABLE classes ("
                + "  id             INTEGER PRIMARY KEY,"
                + "  qn             TEXT NOT NULL UNIQUE,"
                + "  simple_name    TEXT NOT NULL,"
                + "  package_name   TEXT NOT NULL,"
                + "  kind           TEXT NOT NULL,"
                + "  enclosing      TEXT,"
                + "  super_class    TEXT,"
                + "  modifiers      TEXT,"
                + "  annotations    TEXT,"
                + "  aaos_category  TEXT,"
                + "  android_comp   TEXT,"
                + "  jetpack_stereo TEXT,"
                + "  origin         TEXT NOT NULL,"
                + "  jar_path       TEXT,"
                + "  detailed       INTEGER NOT NULL,"
                + "  comment        TEXT,"
                + "  file_id        INTEGER REFERENCES files(id) ON DELETE CASCADE"
                + ")"
            );
            st.executeUpdate("CREATE INDEX idx_classes_simple  ON classes(simple_name)");
            st.executeUpdate("CREATE INDEX idx_classes_package ON classes(package_name)");
            st.executeUpdate("CREATE INDEX idx_classes_super   ON classes(super_class)");
            st.executeUpdate("CREATE INDEX idx_classes_file    ON classes(file_id)");

            // 4) class_interfaces (multi-valued)
            st.executeUpdate(
                "CREATE TABLE class_interfaces ("
                + "  class_id INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
                + "  iface_qn TEXT NOT NULL,"
                + "  PRIMARY KEY (class_id, iface_qn)"
                + ")"
            );
            st.executeUpdate("CREATE INDEX idx_class_iface_iface ON class_interfaces(iface_qn)");

            // 5) class_imports
            st.executeUpdate(
                "CREATE TABLE class_imports ("
                + "  class_id  INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
                + "  imp       TEXT NOT NULL,"
                + "  is_static INTEGER NOT NULL,"
                + "  PRIMARY KEY (class_id, imp)"
                + ")"
            );

            // 6) fields
            st.executeUpdate(
                "CREATE TABLE fields ("
                + "  id          INTEGER PRIMARY KEY,"
                + "  class_id    INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
                + "  name        TEXT NOT NULL,"
                + "  type        TEXT,"
                + "  visibility  TEXT NOT NULL,"
                + "  modifiers   TEXT,"
                + "  annotations TEXT"
                + ")"
            );
            st.executeUpdate("CREATE INDEX idx_fields_class ON fields(class_id)");
            st.executeUpdate("CREATE INDEX idx_fields_name  ON fields(name)");

            // 7) methods (シグネチャまで。本体ツリーは Stage B でソース再パースで取り直す)
            st.executeUpdate(
                "CREATE TABLE methods ("
                + "  id           INTEGER PRIMARY KEY,"
                + "  class_id     INTEGER NOT NULL REFERENCES classes(id) ON DELETE CASCADE,"
                + "  name         TEXT NOT NULL,"
                + "  return_type  TEXT,"
                + "  visibility   TEXT NOT NULL,"
                + "  modifiers    TEXT,"
                + "  annotations  TEXT,"
                + "  param_types  TEXT,"
                + "  param_names  TEXT,"
                + "  throws_types TEXT,"
                + "  is_abstract  INTEGER NOT NULL,"
                + "  comment      TEXT"
                + ")"
            );
            st.executeUpdate("CREATE INDEX idx_methods_class ON methods(class_id)");
            st.executeUpdate("CREATE INDEX idx_methods_name  ON methods(name)");
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
