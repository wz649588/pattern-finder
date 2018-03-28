package consolegsydit;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.dom.CompilationUnit;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import partial.code.grapa.commit.CommitComparator;
import partial.code.grapa.version.detect.VersionDetector;
import edu.vt.cs.changes.ChangeDistillerClient;
import edu.vt.cs.changes.ChangeFact;
import edu.vt.cs.changes.CommitComparatorClient;
import edu.vt.cs.diffparser.ChangeParser;
import edu.vt.cs.editscript.TreeMatchRecorder;
import edu.vt.cs.sql.SqliteManager;

public class Application implements IApplication{

	boolean processDerby = true;
	
	boolean executeFromFirstBug = true;
	
	public static String predictionCmResultTable = null;
	
	public static String neoResultTable = null;
	public static String fpTable = null;
	public static String fieldReplacementTable = null;
	
	private static String getCodeFolder(String project) {
		String codeFolder = null;
		switch (project) {
		case "aries":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/aries/aries/";
			break;
		case "cassandra":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/cassandra/cassandra/";
			break;
		case "derby":
			codeFolder = "/Users/Vito/Documents/VT/2016fall/SE/20160828OriginalData/derby/derby/";
			break;
		case "mahout":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/mahout/mahout/";
			break;
		}
		return codeFolder;
	}
	
	public static String currentCommitType = null;
	
	// 02/28/2018
	public Object start8(IApplicationContext arg0) throws Exception {
		CommitComparatorClient.needExtraLibs = false;
		
		String commitsFolder = "/Users/Vito/Documents/VT/2017fall/change_classify/source/aries/";
		
		String commitTable = "classification_initial_commits_aries";
		editScriptTable = "classification_initial_graph_aries";
		String checkedBugTable = "classification_initial_commits_aries_checked";
		
		Connection conn = SqliteManager.getConnection();
		Statement stmt = conn.createStatement();
		
		if (this.executeFromFirstBug) {
			stmt.executeUpdate("DROP TABLE IF EXISTS " + editScriptTable);
			stmt.executeUpdate("DROP TABLE IF EXISTS " + checkedBugTable);
		}
		
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + editScriptTable
				+ " (commit_type TEXT, bug_name TEXT, graph_num INTEGER,"
				+ "graph_data TEXT, edit_script TEXT)");
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + checkedBugTable + " (bug_name TEXT)");
		
		ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + commitTable);
		rs.next();
		int totalNum = rs.getInt(1);
		rs.close();
		
		stmt.close();
		conn.close();
		
		
		
		for (int offset = 0; offset < totalNum; offset++) {
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			
			rs = stmt.executeQuery("SELECT bug_name,commit_type FROM " + commitTable + " LIMIT 1 OFFSET " + offset);
			rs.next();
			String bugName = rs.getString(1);
			String commitType = rs.getString(2);
			rs.close();
			
			currentCommitType = commitType;
			
			rs = stmt.executeQuery("SELECT COUNT(*) FROM " + checkedBugTable + " WHERE bug_name='" + bugName + "'");
			rs.next();
			boolean bugIsChecked = false;
			if (rs.getInt(1) > 0)
				bugIsChecked = true;
			rs.close();
			
			stmt.close();
			conn.close();
			
			if (bugIsChecked)
				continue;
			
//			bugName = "027e46f_New Feature_ARIES-118";
//			bugName = "27f3ecd_New Feature_ARIES-89";
			
			System.out.println(offset + 1 + "/" + totalNum);
			System.out.println(bugName);
			
			processBug("aries", commitsFolder, bugName);
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate(String.format("INSERT INTO %s (bug_name) VALUES (\"%s\")", checkedBugTable, bugName));
			stmt.close();
			conn.close();
			
			
		}
		
		
		
		return null;
	}
	
	/**
	 * AF+1CM predict other CMs, derby
	 * @param arg0
	 * @return
	 * @throws Exception
	 */
	public Object start6(IApplicationContext arg0) throws Exception {
		Connection conn = SqliteManager.getConnection();
		Statement stmt = conn.createStatement();
		
		// derby, aries, cassandra, mahout
		String project = "mahout";
		
		String codeFolder = null;
		switch (project) {
		case "aries":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/aries/aries/";
			break;
		case "cassandra":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/cassandra/cassandra/";
			break;
		case "derby":
			codeFolder = "/Users/Vito/Documents/VT/2016fall/SE/20160828OriginalData/derby/derby/";
			break;
		case "mahout":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/mahout/mahout/";
			break;
		}
		
		neoResultTable = "prediction_cm_neo_" + project;
		fpTable = "false_positives_" + project;
		fieldReplacementTable = "field_replacement_" + project;
		String checkedBugTable = "checked_bug_" + project;
		
		String commitNameTable = "prediction_neo_commits_" + project;
		if (project == "derby") {
			commitNameTable = "prediction_cm_bug";
		}
		
		
		if (executeFromFirstBug) {
			stmt.executeUpdate("DROP TABLE IF EXISTS " + neoResultTable);
//			stmt.executeUpdate("DROP TABLE IF EXISTS " + fieldReplacementTable);
			stmt.executeUpdate("DROP TABLE IF EXISTS " + fpTable);
			stmt.executeUpdate("DROP TABLE IF EXISTS " + checkedBugTable);
		}
		
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + neoResultTable + " (bug_name TEXT,"
				+ "af_class TEXT, af_name TEXT, field_access TEXT, cm_num INTEGER,"
				+ "predict_cm_num INTEGER, recall INTEGER, precision INTEGER, used_cm TEXT,"
				+ "used_cm_vars TEXT, tp TEXT, tp_vars TEXT, fp TEXT, fp_vars TEXT, fn TEXT, fn_vars TEXT,"
				+ "access_precision TEXT, access_detail TEXT)");
//		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + fieldReplacementTable
//				+ " (bug_name TEXT, af_class TEXT, af_name TEXT, field_access TEXT, cm TEXT,"
//				+ "rp_type TEXT, old_entity TEXT, new_entity TEXT)");
		
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + fpTable + " ("
				+ "bug_name TEXT, fp_package TEXT, fp_class TEXT, fp_method TEXT, fp_sig TEXT, fp_used_cm TEXT,"
				+ "af_class TEXT, af_name TEXT, af_sig TEXT)");
		
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + checkedBugTable + " (bug_name TEXT)");
		
		ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + commitNameTable);
		int totalRowNum = 0;
		if (rs.next())
			totalRowNum = rs.getInt(1);
		rs.close();
		stmt.close();
		conn.close();
		
		for (int offset = 0; offset < totalRowNum; offset++) {
			
			
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT bug_name FROM " + commitNameTable + " LIMIT 1 OFFSET " + offset);
			String bugName = null;
			if (rs.next())
				bugName = rs.getString(1);
			rs.close();
			
			rs = stmt.executeQuery("SELECT COUNT(*) FROM " + checkedBugTable + " WHERE bug_name='" + bugName + "'");
			rs.next();
			boolean bugIsChecked = false;
			if (rs.getInt(1) >= 1)
				bugIsChecked = true; 
			
			rs.close();
			stmt.close();
			conn.close();
			
			if (bugIsChecked)
				continue;
			
			System.out.println(bugName);
			processBug(project, codeFolder, bugName);
			
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate("INSERT INTO " + checkedBugTable + " (bug_name) VALUES ('" + bugName + "')");
			
		}
		
		return null;
	}
	
	private void processBug(String project, String codeFolder, String commitName) throws IOException {
		ChangeDistillerClient client = new ChangeDistillerClient();
		List<ChangeFact> cfList = client.parseChanges(codeFolder + commitName);
		CommitComparatorClient client2 = new CommitComparatorClient(project);
		client2.analyzeCommit(cfList, commitName);
	}
	
	
	/**
	 * This method processes projects other than Derby, like
	 * aries, cassandra, lucece, mahout.
	 * @param arg0
	 * @return
	 * @throws Exception
	 */
	public Object start5(IApplicationContext arg0) throws Exception {
		
		String project = "aries";
		String commitsTable = "prediction_cm_" + project + "_commits";
		predictionCmResultTable = "prediction_cm_result_" + project;
		
		String resultTable = predictionCmResultTable;
		String checkedBugTable = "checked_bug_" + project;
		
		String codeFolder = null;
		switch (project) {
		case "aries":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/aries/aries/";
			break;
		case "cassandra":
			codeFolder = "/Users/Vito/Documents/VT/2017spring/SE/ZhongData/cassandra/cassandra/";
			break;
		}
		
		Connection conn = SqliteManager.getConnection();
		Statement stmt = conn.createStatement();
		
		if (executeFromFirstBug) {
			stmt.executeUpdate("DROP TABLE IF EXISTS " + checkedBugTable);
			stmt.executeUpdate("DROP TABLE IF EXISTS " + resultTable);
		}
		
		String predictionTableSql = "CREATE TABLE IF NOT EXISTS " + resultTable
				+ " (bug_name TEXT, af_class TEXT, af_name TEXT, field_access TEXT, cm_num INTEGER, "
				+ "predict_cm_num INTEGER, recall INTEGER, precision INTEGER, decisive_template TEXT, "
				+ "tp TEXT, tp_vars TEXT, fp TEXT, fp_vars TEXT, fn TEXT, fn_vars TEXT)";
		stmt.executeUpdate(predictionTableSql);
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + checkedBugTable + " (bug_name TEXT)");
		
		ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + commitsTable);
		int rowNum = 0;
		if (rs.next())
			rowNum = rs.getInt(1);
		rs.close();
		stmt.close();
		conn.close();
		
		for (int offset = 0; offset < rowNum; offset++) {
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT bug_name FROM " + commitsTable + " LIMIT 1 OFFSET " + offset);
			String commitName = null;
			if (rs.next())
				commitName = rs.getString(1);
			rs.close();
			stmt.close();
			conn.close();
			
			System.out.println(commitName);
			
			ChangeDistillerClient client = new ChangeDistillerClient();
			List<ChangeFact> cfList = client.parseChanges(codeFolder + commitName);
			CommitComparatorClient client2 = new CommitComparatorClient(project);
			client2.analyzeCommit(cfList, commitName);
			
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate("INSERT INTO " + checkedBugTable + " (bug_name) VALUES (\"" + commitName + "\")");
			stmt.close();
			conn.close();
		}
		
		
		
		
		return null;
	}
	
	public Object start4(IApplicationContext arg0) throws Exception {
		Connection conn = SqliteManager.getConnection();
		Statement stmt = conn.createStatement();
		
		if (executeFromFirstBug) {
			stmt.executeUpdate("DROP TABLE IF EXISTS predict_cmam_initial_analysis");
		}
		
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS predict_cmam_initial_analysis "
				+ "(bug_name TEXT, am_class TEXT, am_name TEXT, am_sig TEXT, "
				+ "cm_class TEXT, cm_name TEXT, cm_sig TEXT)");
		
		ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM unique_bug");
		int rowNum = 0;
		if (rs.next())
			rowNum = rs.getInt(1);
		rs.close();
		stmt.close();
		conn.close();
		
		for (int offset = 0; offset < rowNum; offset++) {
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT bug_name FROM unique_bug LIMIT 1 OFFSET " + offset);
			String bugName = null;
			if (rs.next())
				bugName = rs.getString(1);
			rs.close();
			stmt.close();
			conn.close();
			System.out.println(bugName);
			processBug(bugName);
		}
		
		return null;
	}
	
	public static String editScriptTable;
	
	/**
	 * Extract the pattern from source code of different projects,
	 * i.e. aries, cassandra, derby, mahout
	 * @param arg0
	 * @return
	 * @throws Exception
	 */
	public Object start(IApplicationContext arg0) throws Exception {
		String[] projectList = {"aries", "cassandra", "derby", "mahout"}; 
		for (String project: projectList) {
		Connection connection = SqliteManager.getConnection();
		Statement statement = connection.createStatement();
		
//		String project = "aries";
		String codeFolder = getCodeFolder(project);
		
		editScriptTable = "edit_script_table_" + project;
		String checkedBugTable = "script_checked_bug_" + project;
		
		
		if (executeFromFirstBug) {
			statement.executeUpdate("DROP TABLE IF EXISTS " + checkedBugTable);
			statement.executeUpdate("DROP TABLE IF EXISTS " + editScriptTable);
		}
		
		statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + editScriptTable +
				" (bug_name TEXT, graph_num INTEGER, graph_data TEXT, edit_script TEXT)");
		statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + checkedBugTable + " (bug_name TEXT PRIMARY KEY)");
		
		String commitNameTable = "unique_commits_" + project;
		if (project == "derby") {
			commitNameTable = "unique_bug";
		}
		

		ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + commitNameTable);
		
		int totalNum = 0;
		if (rs.next())
			totalNum = rs.getInt(1);
		statement.close();
		connection.close();
		
		
		for (int offset = 0; offset < totalNum; offset++) {
			Connection conn = SqliteManager.getConnection();
			Statement stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT bug_name FROM " + commitNameTable + " LIMIT 1 OFFSET " + offset);
			String bugName = null;
			if (rs.next())
				bugName = rs.getString(1);
			rs.close();
			
			rs = stmt.executeQuery("SELECT COUNT(*) FROM " + checkedBugTable + " WHERE bug_name='" + bugName + "'");
			rs.next();
			boolean bugIsChecked = false;
			if (rs.getInt(1) > 0)
				bugIsChecked = true;
			rs.close();
			stmt.close();
			conn.close();
			
			if (bugIsChecked)
				continue;
			
			
			System.out.println(offset + 1 + "/" + totalNum);
			System.out.println(bugName);
			processBug(project, codeFolder, bugName);
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate(String.format("INSERT INTO %s (bug_name) VALUES (\"%s\")", checkedBugTable, bugName));
			stmt.close();
			conn.close();
			
			
		}
		}
		
		return null;
	}
	
	// for debugging and improving PPA
	public Object start7(IApplicationContext arg0) throws Exception {
		ExceptionHandler.initDatabaseTable(executeFromFirstBug);
		
		String[] projectList = {"cassandra"};
//		String[] projectList = {"aries", "cassandra", "derby", "mahout"}; 
		for (String project: projectList) {
		
		Connection connection = SqliteManager.getConnection();
		Statement statement = connection.createStatement();
		
		String codeFolder = getCodeFolder(project);
		
		editScriptTable = "improve_table_" + project;
		String checkedBugTable = "improve_checked_bug_" + project;
		
		
		if (executeFromFirstBug) {
			statement.executeUpdate("DROP TABLE IF EXISTS " + checkedBugTable);
			statement.executeUpdate("DROP TABLE IF EXISTS " + editScriptTable);
		}
		
		statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + editScriptTable +
				" (bug_name TEXT, graph_num INTEGER, graph_data TEXT, edit_script TEXT)");
		statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + checkedBugTable + " (bug_name TEXT PRIMARY KEY)");
		
		String commitNameTable = "improve_commits_" + project;
//		String commitNameTable = "empirical_bug_" + project;

		ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + commitNameTable);
		
		int totalNum = 0;
		if (rs.next())
			totalNum = rs.getInt(1);
		statement.close();
		connection.close();
		
		
		for (int offset = 0; offset < totalNum; offset++) {
			Connection conn = SqliteManager.getConnection();
			Statement stmt = conn.createStatement();
			rs = stmt.executeQuery("SELECT bug_name, solved, skip FROM " + commitNameTable + " LIMIT 1 OFFSET " + offset);
			String bugName = null;
			String solved = null;
			String skip = null;
			if (rs.next()) {
				bugName = rs.getString(1);
				solved = rs.getString(2);
				skip = rs.getString(3);
			}
			rs.close();
			

			

			
			if ("y".equals(solved)) {
//				System.out.println("This commit is solved");
//				continue;
			}
			if ("y".equals(skip)) {
//				System.out.println("This commit is skipped");
//				continue;
			}
			
			rs = stmt.executeQuery("SELECT COUNT(*) FROM " + checkedBugTable + " WHERE bug_name='" + bugName + "'");
			rs.next();
			boolean bugIsChecked = false;
			if (rs.getInt(1) > 0)
				bugIsChecked = true;
			rs.close();
			stmt.close();
			conn.close();
			
			if (bugIsChecked)
				continue;
			
			/*
			 * 0108748_CASSANDRA-9097
			 * CleanupMessage.java
			 */
			bugName = "0108748_CASSANDRA-9097";
			
			System.out.println(offset + 1 + "/" + totalNum);
			System.out.println(bugName);
			
			ExceptionHandler.setCurrentCommit(bugName);
		
			
			processBug(project, codeFolder, bugName);
			conn = SqliteManager.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate(String.format("INSERT INTO %s (bug_name) VALUES (\"%s\")", checkedBugTable, bugName));
			stmt.close();
			conn.close();
			
			break;
			
		}
		}
		
		return null;
	}
	
	/**
	 * Process CM-AF prediction for derby
	 * @param arg0
	 * @return
	 * @throws Exception
	 */
	public Object start2(IApplicationContext arg0) throws Exception {
		Connection connection = SqliteManager.getConnection();
		Statement statement = connection.createStatement();
		
		if (executeFromFirstBug) {
			statement.executeUpdate("DROP TABLE IF EXISTS checked_bug");
			statement.executeUpdate("DROP TABLE IF EXISTS prediction_cm");
		}
		
		String predictionTableSql = "CREATE TABLE IF NOT EXISTS prediction_cm "
				+ "(bug_name TEXT, af_class TEXT, af_name TEXT, field_access TEXT, cm_num INTEGER, "
				+ "predict_cm_num INTEGER, recall INTEGER, precision INTEGER, decisive_template TEXT, "
				+ "tp TEXT, tp_vars TEXT, fp TEXT, fp_vars TEXT, fn TEXT, fn_vars TEXT)";
		statement.executeUpdate(predictionTableSql);
		
		String checkedBugTableSql = "CREATE TABLE IF NOT EXISTS checked_bug (name TEXT PRIMARY KEY)";
		statement.executeUpdate(checkedBugTableSql);
		
		String query = "SELECT bug_name FROM prediction_cm_bug WHERE bug_name NOT IN (SELECT name FROM checked_bug)";
		ResultSet rs = statement.executeQuery(query);
		List<String> bugList = new ArrayList<>();
		while (rs.next()) {
			bugList.add(rs.getString("bug_name"));
		}
		connection.close();
		
		for (String bugName: bugList) {
			
			System.out.println(bugName);
			processBug(bugName);
			Connection conn = SqliteManager.getConnection();
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(String.format("INSERT INTO checked_bug (name) VALUES (\"%s\")", bugName));
			conn.close();
		}
		
		return null;
	}
	
//	@Override
	public Object start1(IApplicationContext arg0) throws Exception {
		ChangeDistillerClient client = new ChangeDistillerClient();
		String folderName = null;
		if (processDerby) {
			/////////// File bugFile = new File("/Users/nm8247/Software/workspaceForGrapa/grapa/bugNames2.txt");
			File bugFile = new File("/Users/Vito/git/pattern-finder/grapa/bugNames2.txt");
//			File bugFile = new File("/Users/Vito/Documents/VT/2016fall/SE/bugname.txt");
			
			String emptyCGNodeLog = "/Users/Vito/Documents/VT/2016fall/SE/emptyCGNode.txt";
			Path logPath = FileSystems.getDefault().getPath(emptyCGNodeLog);
			Files.deleteIfExists(logPath);
			
			// --- DO NOT DELETE THIS BLOCK ---
			// ------------ BEGIN -------------
//			Path peerOutputPath = FileSystems.getDefault().getPath("/Users/Vito/Documents/VT/2016fall/SE/peerVars.csv");
//			Files.deleteIfExists(peerOutputPath);
//			String peerHeader = "bugName,AFClass,AFName,cmNum,allMethodNum,classNum,completeVarNum,partialVarNum,completeVars(numInAllM),partialVars(numInCM/numInAllM)\n";
//			Files.write(peerOutputPath, peerHeader.getBytes(), CREATE);
			// ------------- END --------------
			
			String bugNameLog = "/Users/Vito/Documents/VT/2016fall/SE/log_gsydit.txt";
			Path bugNameLogPath = FileSystems.getDefault().getPath(bugNameLog);
			String lastBugName = null;
			if (Files.notExists(bugNameLogPath)) {
				Files.createFile(bugNameLogPath);
			} else {
				Scanner scanner = new Scanner(bugNameLogPath);
				while (scanner.hasNextLine()) {
					lastBugName = scanner.nextLine();
				}
				scanner.close();
			}
			
			
			
			
			String predictLog = "/Users/Vito/Documents/VT/2016fall/SE/predictCM.csv";
			Path predictLogPath = FileSystems.getDefault().getPath(predictLog);
			if (Files.notExists(predictLogPath)) {
				String predictHeader = "bugName,AFClass,AFName,CM_num,predictCM_num,recall(%),precision(%),TP,varsInTP,FP,varsInFP,FN,varsInFN\n";
				Files.write(predictLogPath, predictHeader.getBytes(), CREATE);
			}
//			Files.deleteIfExists(predictLogPath);
			
			BufferedReader reader = null;
			reader = new BufferedReader(new FileReader(bugFile));
			String line = null;			
			int i = 0;
			boolean beyondLastExecution = false;
			while ( (line = reader.readLine()) != null) {
				//if ( (i++) < 1394) {
				//	continue;	
				//}				
				folderName = line.trim();
				
				// deal with memory leak issue
				if (lastBugName == null || folderName.equals(lastBugName)) {
					beyondLastExecution = true;
					continue;
				}
				if (!beyondLastExecution)
					continue;
				
				System.out.println(folderName);
				
				String logInfo = folderName + "\n";
				try {
					Files.write(logPath, logInfo.getBytes(), CREATE, APPEND);
				} catch (IOException e) {
					System.err.println(e.getMessage());
				}
				
				/////// List<ChangeFact> cfList = client.parseChanges("/Users/nm8247/Documents/experiment_data/zhonghao/derby/derby/" + folderName);
//				List<ChangeFact> cfList = client.parseChanges("/Users/Vito/Documents/VT/2016fall/SE/20160828OriginalData/derby/derby/" + folderName);
//				CommitComparatorClient client2 = new CommitComparatorClient();
//				client2.analyzeCommit(cfList, folderName);
				processBug(folderName);
				Files.write(bugNameLogPath, (folderName+"\n").getBytes(), APPEND);
				
				// delete empty directory
				String resultDir = "/Users/Vito/Documents/VT/2016fall/SE/grapa_results/derby/" + folderName;
				Path resultPath = FileSystems.getDefault().getPath(resultDir);
				try {
					Files.delete(resultPath);
				} catch (NoSuchFileException x) {
//					System.out.print(folderName + ": no such file");
				} catch (DirectoryNotEmptyException x) {
//					System.out.println(x.getMessage());
				}
			}
			reader.close();
		} else{
			folderName = "nuxeo_data";
			List<ChangeFact> cfList = client.parseChanges("/Users/nm8247/Software/subject_projects/" + folderName);
			CommitComparatorClient client2 = new CommitComparatorClient("nuxeo", "lucene");
			client2.analyzeCommitForMigration(cfList, folderName, "2.3.2", "4.7.0");
		}		
		return null;
	}
	
	private void processBug(String folderName) throws IOException {
		ChangeDistillerClient client = new ChangeDistillerClient();
		List<ChangeFact> cfList = client.parseChanges("/Users/Vito/Documents/VT/2016fall/SE/20160828OriginalData/derby/derby/" + folderName);
		CommitComparatorClient client2 = new CommitComparatorClient();
		client2.analyzeCommit(cfList, folderName);
	}
	
	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}
}
