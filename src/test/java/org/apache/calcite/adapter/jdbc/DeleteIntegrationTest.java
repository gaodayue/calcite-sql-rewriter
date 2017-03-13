package org.apache.calcite.adapter.jdbc;

import org.apache.calcite.adapter.jdbc.tools.JdbcRelBuilder;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.tools.Program;
import org.apache.calcite.util.Holder;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Function;
import io.pivotal.beach.calcite.programs.ForcedRulesProgram;
import io.pivotal.beach.calcite.programs.SequenceProgram;

public class DeleteIntegrationTest {
	private static final String virtualSchemaName = "calcite_sql_rewriter_integration_test"; // Should be "hr" - see TargetDatabase.java
	private static final String actualSchemaName = "calcite_sql_rewriter_integration_test";

	@SuppressWarnings("Guava") // Must conform to Calcite's API
	private Function<Holder<Program>, Void> program = SequenceProgram.prepend(
			new ForcedRulesProgram(new JdbcRelBuilder.FactoryFactory(),
					new JournalledInsertRule(),
					new JournalledUpdateRule(),
					new JournalledDeleteRule()
			)
	);

	@Before // TODO: find out how to make CalciteAssert run in a transaction then change this to BeforeClass
	public void rebuildTestDatabase() throws Exception {
		TargetDatabase.rebuild();
	}

	@Test
	public void testRewriting() {
		CalciteAssert
				.model(TargetDatabase.JOURNALLED_MODEL)
				.query("DELETE FROM \"" + virtualSchemaName + "\".\"depts\" WHERE \"deptno\"=3")
				.withHook(Hook.PROGRAM, program)
//				.explainContains("PLAN=JdbcToEnumerableConverter\n" + // TODO
//						"  JdbcTableModify(table=[[" + virtualSchemaName + ", depts_journal]], operation=[INSERT], flattened=[false])\n" +
//						"    JdbcValues(tuples=[[{ 696, 'Pivotal' }]])\n")
				.planUpdateHasSql("INSERT INTO \"" + actualSchemaName + "\".\"depts_journal\" (\"deptno\", \"department_name\", \"version_number\", \"subsequent_version_number\")\n" +
						"(SELECT \"deptno\", \"department_name\", CURRENT_TIMESTAMP AS \"version_number\", CURRENT_TIMESTAMP AS \"subsequent_version_number\"\n" +
						"FROM (SELECT \"deptno\", \"department_name\", \"version_number\", \"subsequent_version_number\", MAX(\"version_number\") OVER (PARTITION BY \"deptno\" ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS \"$f4\"\n" +
						"FROM \"" + actualSchemaName + "\".\"depts_journal\") AS \"t\"\n" +
						"WHERE \"version_number\" = \"$f4\" AND \"subsequent_version_number\" IS NULL AND \"deptno\" = 3)", 1);
	}

	@Test
	public void testDeletingAbsentRecord() {
		CalciteAssert
				.model(TargetDatabase.JOURNALLED_MODEL)
				.query("DELETE FROM \"" + virtualSchemaName + "\".\"depts\" WHERE \"deptno\"=999")
				.withHook(Hook.PROGRAM, program)
				.updates(0);
	}

	@Test
	public void testDeletingByNonKeyColumns() {
		CalciteAssert
				.model(TargetDatabase.JOURNALLED_MODEL)
				.query("DELETE FROM \"" + virtualSchemaName + "\".\"depts\" WHERE \"department_name\"='Dep3'")
				.withHook(Hook.PROGRAM, program)
				.planUpdateHasSql("INSERT INTO \"" + actualSchemaName + "\".\"depts_journal\" (\"deptno\", \"department_name\", \"version_number\", \"subsequent_version_number\")\n" +
						"(SELECT \"deptno\", \"department_name\", CURRENT_TIMESTAMP AS \"version_number\", CURRENT_TIMESTAMP AS \"subsequent_version_number\"\n" +
						"FROM (SELECT \"deptno\", \"department_name\", \"version_number\", \"subsequent_version_number\", MAX(\"version_number\") OVER (PARTITION BY \"deptno\" ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS \"$f4\"\n" +
						"FROM \"" + actualSchemaName + "\".\"depts_journal\") AS \"t\"\n" +
						"WHERE \"version_number\" = \"$f4\" AND \"subsequent_version_number\" IS NULL AND \"department_name\" = 'Dep3')", 1);
	}

}