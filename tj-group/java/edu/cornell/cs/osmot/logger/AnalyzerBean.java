package edu.cornell.cs.osmot.logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.servlet.ServletContext;

/**
 * This class implements the servlet interface for getting log analysis.
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */

public class AnalyzerBean {
	
	/**
	 * Gets the AnalyzerBean
	 *
	 * @param app current ServletContext
	 * @return current AnalyzerBean instance 
	 */
	public synchronized static AnalyzerBean get(ServletContext app) {
		AnalyzerBean bean = (AnalyzerBean) app.getAttribute("analyzerBean");
		if (bean == null) {
			Logger.log("Creating an AnalyzerBean");
			bean = new AnalyzerBean();
			app.setAttribute("analyzerBean", bean);
			Logger.log("AnalyzerBean created");
		}

		return bean;
	}

	/**
	 * Evaluate interleaved rankings per day and per query.
	 *
	 * @return the evaluation
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException Signals that a SQL exception has occurred.
	 */
	public TreeMap<String, ArrayList<Integer>> getEvaluation() throws IOException, SQLException {
		return EvalInterleaving.getEvaluation();
	}
	
	/**
	 * Evaluate interleaved rankings per day and per click.
	 *
	 * @return the evaluation clicks
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException Signals that a SQL exception has occurred.
	 */
	public TreeMap<String, ArrayList<Integer>> getEvaluationClicks() throws IOException, SQLException {
		return EvalInterleaving.getEvaluationClicks();
	}
	
	/**
	 * Get explanation for each query.
	 *
	 * @return the evaluation explained
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException Signals that a SQL exception has occurred.
	 */
	public ArrayList<ArrayList<String>> getEvaluationExplained() throws IOException, SQLException {
		return EvalInterleaving.getEvaluationExplained();
	}
}
