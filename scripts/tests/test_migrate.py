import hashlib
import importlib.util
import shutil
import sqlite3
import subprocess
import tempfile
import threading
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL_DIR = ROOT / "deploy/charts/backend/files/db"
MIGRATE_PY = SQL_DIR / "migrate.py"
CLASSPATH_SCHEMA = ROOT / "backend/server/src/main/resources/db/pipeline-schema.sql"

spec = importlib.util.spec_from_file_location("migrate", MIGRATE_PY)
migrate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate)


class MigrateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "test.db")

    def tearDown(self):
        self.tmp.cleanup()

    def run_migrate(self, sql_dir=SQL_DIR):
        return migrate.run(self.db, str(sql_dir))

    def query(self, sql, params=()):
        with sqlite3.connect(self.db) as conn:
            return conn.execute(sql, params).fetchall()

    def test_fresh_database_seeds(self):
        self.assertEqual(self.run_migrate(), 0)

        count, enabled = self.query(
            "select count(*), sum(enabled) from pipeline_task_kind"
        )[0]
        self.assertEqual(count, 19)
        self.assertEqual(enabled, 18)

        template_count = self.query(
            "select count(*) from pipeline_task_kind where command_template <> ''"
        )[0][0]
        self.assertEqual(template_count, 8)

        options = self.query(
            "select options_json from pipeline_task_kind_param where param_key='DT_FAIL_ON'"
        )[0][0]
        self.assertIn("critical", options)

        default_command = self.query(
            "select default_command from pipeline_task_kind "
            "where kind_value='DEPENDENCY_ANALYSIS'"
        )[0][0]
        self.assertNotIn(" -q", default_command)

    def test_rerun_preserves_user_data(self):
        self.assertEqual(self.run_migrate(), 0)
        self.query(
            "update pipeline_task_kind set command_template='CUSTOM' where kind_value='CLONE'",
        )
        self.query(
            "update pipeline_task_kind set command_template='' where kind_value='FORMAT'",
        )
        self.query(
            "update pipeline_task_kind set default_command='CUSTOM_CMD' "
            "where kind_value='DEPENDENCY_ANALYSIS'",
        )

        self.assertEqual(self.run_migrate(), 0)

        values = dict(self.query(
            "select kind_value, command_template from pipeline_task_kind "
            "where kind_value in ('CLONE','FORMAT')"
        ))
        self.assertEqual(values["CLONE"], "CUSTOM")
        self.assertEqual(values["FORMAT"], "")
        self.assertEqual(
            self.query(
                "select default_command from pipeline_task_kind "
                "where kind_value='DEPENDENCY_ANALYSIS'"
            )[0][0],
            "CUSTOM_CMD",
        )

    def test_empty_template_preserved(self):
        self.assertEqual(self.run_migrate(), 0)
        self.query(
            "update pipeline_task_kind set command_template='' where kind_value='FORMAT'"
        )

        self.assertEqual(self.run_migrate(), 0)
        self.assertEqual(
            self.query(
                "select command_template from pipeline_task_kind where kind_value='FORMAT'"
            )[0][0],
            "",
        )

    def test_duplicate_column_continues(self):
        self.assertEqual(self.run_migrate(), 0)
        self.assertEqual(self.run_migrate(), 0)
        columns = {
            row[1] for row in self.query("pragma table_info(pipeline_task_kind)")
        }
        self.assertIn("cpu_request", columns)

    def test_checksum_change_reruns_05_safely(self):
        self.assertEqual(self.run_migrate(), 0)
        self.query(
            "update pipeline_task_kind set command_template='CUSTOM' where kind_value='CLONE'",
        )

        copy_dir = Path(self.tmp.name) / "sql"
        shutil.copytree(SQL_DIR, copy_dir)
        with (copy_dir / "05-pipeline-schema.sql").open("a", encoding="utf-8") as handle:
            handle.write("\n-- simulated checksum change\n")

        self.assertEqual(self.run_migrate(copy_dir), 0)
        self.assertEqual(
            self.query(
                "select command_template from pipeline_task_kind where kind_value='CLONE'"
            )[0][0],
            "CUSTOM",
        )

    def test_upgrade_old_defaults(self):
        self.assertEqual(self.run_migrate(), 0)
        old_default = (
            "mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom "
            "-Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress -q"
        )
        self.query(
            "update pipeline_task_kind set default_command=? where kind_value='DEPENDENCY_ANALYSIS'",
            (old_default,),
        )
        self.query(
            "update pipeline_task_kind set command_template='USER_EDIT' where kind_value='CLONE'",
        )
        self.query(
            "update pipeline_task_kind_param set options_json='[]' where param_key='DT_FAIL_ON'",
        )
        self.query(
            "delete from _schema_migrations where filename='16-upgrade-task-template-seeds.sql'"
        )

        self.assertEqual(self.run_migrate(), 0)

        new_default = self.query(
            "select default_command from pipeline_task_kind where kind_value='DEPENDENCY_ANALYSIS'"
        )[0][0]
        self.assertNotIn(" -q", new_default)
        self.assertEqual(
            self.query(
                "select command_template from pipeline_task_kind where kind_value='CLONE'"
            )[0][0],
            "USER_EDIT",
        )
        options = self.query(
            "select options_json from pipeline_task_kind_param where param_key='DT_FAIL_ON'"
        )[0][0]
        self.assertIn("critical", options)

        # Re-running is idempotent.
        self.assertEqual(self.run_migrate(), 0)
        self.assertEqual(
            self.query(
                "select default_command from pipeline_task_kind where kind_value='DEPENDENCY_ANALYSIS'"
            )[0][0],
            new_default,
        )

    def test_legacy_dependency_component_rebuild(self):
        with sqlite3.connect(self.db) as conn:
            conn.execute(
                "create table dependency_component(id integer primary key, run_id integer, name text)"
            )
            conn.execute(
                "insert into dependency_component(id, run_id, name) values(1, 2, 'legacy-row')"
            )

        self.assertEqual(self.run_migrate(), 0)

        self.assertEqual(
            self.query("select id, run_id, name from dependency_component_legacy"),
            [(1, 2, "legacy-row")],
        )
        columns = {row[1] for row in self.query("pragma table_info(dependency_component)")}
        self.assertTrue({"pipeline_id", "identity_key", "run_id", "purl", "name", "version"} <= columns)

    def test_destructive_statement_rejected(self):
        bad_dir = Path(self.tmp.name) / "bad-sql"
        bad_dir.mkdir()
        (bad_dir / "01-ok.sql").write_text("CREATE TABLE ok(id INTEGER);", encoding="utf-8")
        (bad_dir / "02-drop.sql").write_text("DROP TABLE ok;", encoding="utf-8")

        with self.assertRaises(migrate.MigrationError):
            self.run_migrate(bad_dir)

    def test_update_only_allowed_in_16(self):
        bad_dir = Path(self.tmp.name) / "bad-update"
        bad_dir.mkdir()
        (bad_dir / "01-update.sql").write_text(
            "CREATE TABLE ok(id INTEGER); UPDATE ok SET id=1;", encoding="utf-8"
        )
        with self.assertRaises(migrate.MigrationError):
            self.run_migrate(bad_dir)

    def test_failure_does_not_record_checksum(self):
        bad_dir = Path(self.tmp.name) / "bad-failure"
        bad_dir.mkdir()
        (bad_dir / "01-ok.sql").write_text("CREATE TABLE ok(id INTEGER);", encoding="utf-8")
        (bad_dir / "02-bad.sql").write_text("CREATE TABLE bad(;", encoding="utf-8")

        with self.assertRaises(sqlite3.Error):
            self.run_migrate(bad_dir)

        rows = self.query("select filename from _schema_migrations order by filename")
        self.assertEqual(rows, [("01-ok.sql",)])

    def test_legacy_ledger_merged(self):
        digest = migrate.checksum(SQL_DIR / "01-user-schema.sql")
        with sqlite3.connect(self.db) as conn:
            conn.execute(
                "create table schema_migrations(filename text primary key, checksum text, applied_at text)"
            )
            conn.execute(
                "insert into schema_migrations(filename, checksum, applied_at) "
                "values('01-user-schema.sql', ?, datetime('now'))",
                (digest,),
            )

        self.assertEqual(self.run_migrate(), 0)

        self.assertEqual(
            self.query(
                "select filename from _schema_migrations where filename='01-user-schema.sql'"
            ),
            [("01-user-schema.sql",)],
        )
        self.assertEqual(
            self.query("select count(*) from schema_migrations")[0][0],
            1,
        )

    def test_embedded_external_parity(self):
        self.assertEqual(self.run_migrate(), 0)
        embedded_db = str(Path(self.tmp.name) / "embedded.db")
        with sqlite3.connect(embedded_db) as conn:
            conn.executescript(CLASSPATH_SCHEMA.read_text(encoding="utf-8"))

        external = self.query(
            "select kind_value, enabled, command_template from pipeline_task_kind order by kind_value"
        )
        with sqlite3.connect(embedded_db) as conn:
            embedded = conn.execute(
                "select kind_value, enabled, command_template from pipeline_task_kind order by kind_value"
            ).fetchall()
        self.assertEqual(external, embedded)

        external_params = self.query(
            "select param_key, options_json from pipeline_task_kind_param "
            "where kind_value='DEPENDENCY_TRACK' order by param_key"
        )
        with sqlite3.connect(embedded_db) as conn:
            embedded_params = conn.execute(
                "select param_key, options_json from pipeline_task_kind_param "
                "where kind_value='DEPENDENCY_TRACK' order by param_key"
            ).fetchall()
        self.assertEqual(external_params, embedded_params)

    def test_shell_wrapper_from_any_dir(self):
        result = subprocess.run(
            ["bash", str(SQL_DIR / "migrate.sh"), self.db, str(SQL_DIR)],
            cwd=self.tmp.name,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_concurrent_migration(self):
        errors = []

        def worker():
            try:
                migrate.run(self.db, str(SQL_DIR))
            except Exception as exc:  # noqa: BLE001
                errors.append(exc)

        threads = [threading.Thread(target=worker) for _ in range(2)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        self.assertEqual(errors, [])
        self.assertEqual(
            self.query("select count(*) from _schema_migrations")[0][0],
            len(list(SQL_DIR.glob("*.sql"))),
        )
        self.assertEqual(self.query("pragma integrity_check")[0][0], "ok")


if __name__ == "__main__":
    unittest.main()
