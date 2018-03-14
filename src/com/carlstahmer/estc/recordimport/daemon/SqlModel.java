/**
 *	<p>Copyright (c) 2016, Carl Stahmer - <a href="http://www.carlstahmer.com">www.carlstahmer.com</a>.</p>
 *	
 *	<p>This file is part of the ESTC Record Importer package, a server 
 *	daemon that processes incoming MARC cataloging data stored in binary
 *	MARC, .csv, and .txt formats, checks the records for scope on date,
 *	language, and place of publication, and exports the filtered
 *	records as RDF suitable for linked data exchange.</p>
 *
 *	<p>The ESTC Record Importer is free software: you can redistribute it 
 *	and/or modify it under the terms of the GNU General Public License 
 *	as published by the Free Software Foundation, either version 3 of 
 *	the License, or (at your option) any later version.</p>
 *
 *	<p>The ESTC Record Importer is distributed in the hope that it will 
 *	be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
 *	of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *	GNU General Public License for more details.</p>
 *
 *	<p>You should have received a copy of the GNU General Public License  
 *	along with the ESTC Record Importer distribution.  If not, 
 *	see <a href="http://www.gnu.org/licenses/">http://www.gnu.org/licenses/</a>.</p>
 *
 *	<p>Development of this software was made possible through funding from 
 *	the Andrew W. Mellon Foundation which maintains a nonexclusive, 
 *  royalty-free, worldwide, perpetual, irrevocable license to distribute 
 *  this software either in wholoe or in part for scholarly and educational purposes.</p>
 */

package com.carlstahmer.estc.recordimport.daemon;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;


/**
 * @author cstahmer
 * 
 * <p>A class that acts as the SQL query model for the application.  All SQL queries 
 * for the entire application are constructed here.  There should be no SQL anywhere 
 * else in the application.</p>
 */
public class SqlModel {
	
	public Connection conn = null;
	public boolean connOpen = false;
	String dbserver;
	String dbname;
	String dbuser;
	String dbpass;
	
	/**
	 * <p>Constructor class that assigns values from passed Config object 
	 * to local variables needed to communicate with the database.</p>
	 *
	 * @param  config    an instance of the Conf class
	 */
	public SqlModel(Conf config) {
		dbserver = config.dbserver;
		dbname = config.dbname;
		dbuser = config.dbuser;
		dbpass = config.dbpass;
	}
	
////////////////////////////
// Open and Close Methods //
////////////////////////////
	
	/**
	 * <p>Opens a SQL Connection.</p>
	 */
	public boolean openConnection() {
		
		connOpen = false;
		
		do {
			try {
				//currentTime();
				String strConnString = "jdbc:mysql://"+dbserver+"/"+dbname+"?user="+dbuser+"&password="+dbpass;
		        conn = DriverManager.getConnection(strConnString);
		        connOpen = true;
		        //currentTime();
		    } catch (SQLException ex) {
		        // handle any errors
		    	System.out.println("pass: " + dbpass);
		        System.out.println("SQLException SqlModel.java openConnection(): " + ex.getMessage());
		        System.out.println("SQLState: " + ex.getSQLState());
		        System.out.println("VendorError: " + ex.getErrorCode());
		        try {
					TimeUnit.SECONDS.sleep(30);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		} while (connOpen == false);
		
		return true;
		
	}
	
	/**
	 * <p>Closes a SQL Connection.</p>
	 */
	public boolean closeConnection() {
		try {
			conn.close();
			connOpen = false;
			return true;
		} catch (SQLException ex) {
			return false;
		}
		
		
	}
	
/////////////////////////
// Data Model Methods //
////////////////////////
	
	/////////////////////////////////////////////////////////////////
	//                   SELECT FUNCTIONS                          //
	/////////////////////////////////////////////////////////////////
	
	/**
	 * <p>Selects a file type id based upon a filename suffix 
	 * and mapping provided in database.</p>
	 *
	 * @param  	suffix		the suffix to lookup
	 * @return				A file_types ID from the db.  Returns 0 if not found.
	 */
	public int selectFileTypeID(String suffix) {
		String strSql = "SELECT file_type_id FROM file_type_suffixes WHERE suffix LIKE '" + suffix + "'";
		int fileTypeID = qSelectInt(strSql);
		return fileTypeID;
	}
	
	/**
	 * <p>Selects a "file" record from the database.  Checks the institutional code, 
	 * filename, and file system file modification date to see if this is a file that 
	 * has already been processed.  If it finds a record for this file, it returns 
	 * the file list id from the database.  If it does not find the file in the database,
	 * it returns 0.</p>
	 *
	 * @param  	currCode	the MARC institutional code for the creator of the file
	 * @param  	fileName  	the filename of the file
	 * @param	modDate		the system last modified time stamp of the file
	 * @return				A file ID if found or 0 if not found
	 */
	public int selectFileRecordStrict(String currCode, String fileName, long modDate) {
		String strSql = "SELECT id FROM files" +
				" WHERE institution_code LIKE '" + currCode +
				"' AND filename LIKE '" + fileName + 
				"' AND modification_date >= " + String.valueOf(modDate) + ";";
		int recordId = qSelectInt(strSql);	
		return recordId;
	}
	
	/**
	 * <p>Selects a "record has images" record from the database.  </p>
	 *
	 * @param  	record_id	the record id
	 * @return				a vector of values
	 */
	public String selectImageRecord(int record_id) {
		String strSql = "SELECT image_old_path FROM records_has_images" +
				" WHERE records_id = " + record_id + ";";
		String recordId = qSelectString(strSql);	
		return recordId;
	}
	
	/**
	 * <p>Selects or creates subject ID from the estc_subject_terms table</p>
	 *
	 * @param  	record_id	the record id
	 * @return				a vector of values
	 */
	public String selectSubjectID(String subjectLiteral) {
		String strSql = "SELECT id FROM estc_subject_terms" +
				" WHERE term LIKE \"" + subjectLiteral + "\";";
		String recordId = qSelectString(strSql);	
		if (recordId != null && recordId.length() > 0) {
			return recordId;
		} else {
			int retRecordID = 0;
			String strInsertSql = "INSERT INTO estc_subject_terms " +
					"(term)" +
					" VALUES" + 
					" (\"" + subjectLiteral + "\");";
			retRecordID  = qInsert(strInsertSql);
			recordId = String.valueOf(retRecordID);
			return recordId;
		}
	}
	
	/**
	 * <p>Selects or creates subject ID from the estc_subject_terms table</p>
	 *
	 * @param  	record_id	the record id
	 * @return				a vector of values
	 */
	public String selectAgentID(String agentLiteral) {
		String strSql = "SELECT id FROM estc_agents" +
				" WHERE local_label LIKE \"" + agentLiteral + "\";";
		String recordId = qSelectString(strSql);	
		if (recordId != null && recordId.length() > 0) {
			return recordId;
		} else {
			int retRecordID = 0;
			String strInsertSql = "INSERT INTO estc_agents " +
					"(local_label)" +
					" VALUES" + 
					" (\"" + agentLiteral + "\");";
			retRecordID  = qInsert(strInsertSql);
			recordId = String.valueOf(retRecordID);
			return recordId;
		}
	}
	
	/**
	 * <p>Selects a "file" record from the database.  Checks the institutional code
	 * and filename only to see if this is a file that is already in the system.  Ignores
	 * file modification date.  Simply a check of filename and institution.  If it does 
	 * not find the file in the database it returns 0.</p>
	 *
	 * @param  	currCode	the MARC institutional code for the creator of the file
	 * @param  	fileName  	the filename of the file
	 * @return				A file ID if found or 0 if not found
	 */
	public int selectFileRecord(String currCode, String fileName) {
		String strSql = "SELECT id FROM files" +
				" WHERE institution_code LIKE '" + currCode +
				"' AND filename LIKE '" + fileName + "';";
		int recordId = qSelectInt(strSql);	
		return recordId;
	}
	
	
	/**
	 * <p>Selects the name of a file from the db based on a file record id.</p>
	 *
	 * @param  	fileId		the id from the files table
	 * @return				An array containing the file name and institutional code
	 * 						of the library it belongs to
	 */
	public ArrayList<HashMap<String,String>> selectFileInfoById(int fileId) {
		String strSql = "SELECT institution_code, filename FROM files" +
				" WHERE id = " + fileId;
		ArrayList<HashMap<String,String>> tableResults = qSelectGeneric(strSql);
		return tableResults;
	}	
	
	/**
	 * <p>Selects a record from the records table and returns the id.  Records are
	 * matched on the institutional code, the type of record, and the control identifier.</p>
	 *
	 * @param  	currCode			the MARC institutional code for the creator of the file
	 * @param  	recType  			the type of record.  1 = bib, 2 = holding
	 * @param	controlIdentifier	the record control identifier
	 * @return						A record id.  Returns 0 if no match found.
	 */
	public int selectRecordRecord(String currCode, int recType, String controlIdentifier) {
		String strSql = "SELECT records.id FROM records" +
				" JOIN files ON files.id = records.file_id" +
				" WHERE files.institution_code LIKE '" + currCode +
				"' AND records.control_identifier LIKE '" + controlIdentifier + 
				"' AND records.type LIKE '" + String.valueOf(recType) + "';";
		int recordId = qSelectInt(strSql);		
		return recordId;
	}
	
	/**
	 * <p>Selects holding records associated with a bib records.</p>
	 *
	 * @param  	estdID		the ESTC ID to match
	 * @return				An array containing id's of associated holding records
	 */
	public ArrayList<HashMap<String,String>> selectHoldingRecordIDsOld(String estcID) {
		String strSql = "SELECT records.id FROM records " +
							"JOIN records_has_fields ON records_has_fields.record_id = records.id " +
							"WHERE records.type = 2 AND records_has_fields.field LIKE \"001\" " +
							"AND records_has_fields.shortval LIKE \"" + estcID + "\"";
		//System.out.println("strsql: " + strSql);
		ArrayList<HashMap<String,String>> tableResults = qSelectGeneric(strSql);
		return tableResults;
	}
	
	/**
	 * <p>Selects holding records associated with a bib records.</p>
	 *
	 * @param  	estdID		the ESTC ID to match
	 * @return				An array containing id's of associated holding records
	 */
	public ArrayList<HashMap<String,String>> selectHoldingRecordIDs(String estcID) {
		String strSql = "SELECT DISTINCT `records_has_fields`.`record_id` " +
							"FROM `records_has_fields` " +
							"WHERE `records_has_fields`.`record_id` IN " +
								"(SELECT `records_has_fields`.`record_id` " +
								"FROM `records_has_fields` " +
								"WHERE `records_has_fields`.`field` LIKE \"001\" " +
								"AND `records_has_fields`.`value` LIKE \"" + estcID + "\") " +
							"AND `records_has_fields`.`record_id` IN " +
								"(SELECT `records_has_fields`.`record_id` " +
								"FROM `records_has_fields` " +
								"WHERE `records_has_fields`.`field` LIKE \"852\")";
		//System.out.println("strsql: " + strSql);
		ArrayList<HashMap<String,String>> tableResults = qSelectGeneric(strSql);
		return tableResults;
	}
	
	/**
	 * <p>Selects all subfields for a given field.</p>
	 *
	 * @param  	feildID		the ESTC ID to match
	 * @return				An array containing id's of associated holding records
	 */
	public ArrayList<HashMap<String,String>> selectSubFields(int feildID) {
		String strSql = "SELECT fields_has_subfields.* FROM fields_has_subfields" + 
							" WHERE fields_has_subfields.field_id = " + feildID;
		ArrayList<HashMap<String,String>> tableResults = qSelectGeneric(strSql);
		return tableResults;
	}
	
	/**
	 * <p>Selects all the subfields for a given field.
	 * I think the one above doesn't work right.</p>
	 *
	 * @param  	feildID		the field whose subfield I want
	 * @return				An array of hashes of all subfields
	 */
	public ArrayList<HashMap<String,String>> selectAllSubfields(int feildID) {
		
		// initialize required objects
		ArrayList<HashMap<String,String>> recordRows = new ArrayList<HashMap<String,String>>();
		HashMap<String,String> recordColumns = new HashMap<String, String>();
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		
		
		// define query
		String strSql = "SELECT fields_has_subfields.* FROM fields_has_subfields" + 
							" WHERE fields_has_subfields.field_id = " + feildID;
		
		// run query and process results
		resultSet = qSelectGenericMultiRow(strSql);
		
		for (int i=0; i < resultSet.size(); i++) {
    		recordRows.add(resultSet.get(i));
		}

        // return result
		return recordRows;
	}	
	
	/**
	 * <p>Selects a control identifier for a record from the records table</p>
	 *
	 * @param  	recordId			the id of the record
	 * @return						A control identifier for the record.
	 */
	public String selectRecordControlId(int recordId) {
		String controlId = "";
		String strSql = "SELECT control_identifier FROM records" +
				" WHERE id = " + recordId;
		controlId = qSelectString(strSql);		
		return controlId;
	}
	
	/**
	 * <p>Selects a file ID for a record from the records table</p>
	 *
	 * @param  	recordId			the id of the record
	 * @return						A control identifier for the record.
	 */
	public int selectRecordFileId(int recordId) {
		int fileId = 0;
		String strSql = "SELECT file_id FROM records" +
				" WHERE id = " + recordId;
		fileId = qSelectInt(strSql);		
		return fileId;
	}
	
	
	/**
	 * <p>Selects the last modified datetimestamp for a record</p>
	 *
	 * @param  	recordId	The record id	
	 * @return				The moddate for the record.
	 */
	public double selectRecordMod(int recordId) {
		String strSql = "SELECT moddate FROM records" +
				" WHERE id = " + recordId + ";";
		
		double stamp = qSelectDouble(strSql);		
		return stamp;
	}	
	
	/**
	 * <p>Selects the field type for a field record in the
	 * records_has_fields table</p>
	 *
	 * @param  	recordId	The recordHasFieldsId id	
	 * @return				The field type for the record.
	 */
	public String selectFieldType(int recordHasFieldsId) {
		String strSql = "SELECT field FROM records_has_fields" +
				" WHERE id = " + recordHasFieldsId + ";";
		String type = qSelectString(strSql);		
		return type;
	}
	
	/**
	 * <p>Select all fields in the records_has_fields table that are associated 
	 * with a record from the records table.</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want
	 * @return				an Array of field ids
	 */
	public ArrayList<Integer> selectAssocfieldIds(int recordId) {		
		
		// initialize required objects
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		ArrayList<Integer> recordRows = new ArrayList<Integer>();
		
		// define query
		String strSql = "SELECT id FROM records_has_fields" +
				" WHERE record_id = " + recordId;
		
		// run query and process results
		resultSet = qSelectGeneric(strSql);
		
		for (int i=0; i < resultSet.size(); i++) {
			HashMap<String,String> thisRecord = new HashMap<String,String>();
			thisRecord = resultSet.get(i);
			recordRows.add(Integer.valueOf(thisRecord.get("id")));
		}
        
        // return results
		return recordRows;
	}
	
	
	/**
	 * <p>Select all fields in the records_has_fields table that are associated 
	 * with a record from the records table.  The return is a List of Hashes.  
	 * For each hash slice [0] = the field tag and slice [1] = the string value.</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want
	 * @return				an ArrayList of Hashes. For each hash slice [0] = the field tag and slice [1] = the string value.
	 */
	public ArrayList<HashMap<String,String>> selectRecordFields(int recordId) {
				
		// initialize required objects
		ArrayList<HashMap<String,String>> recordRows = new ArrayList<HashMap<String,String>>();
		HashMap<String,String> recordColumns = new HashMap<String, String>();
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		
		
		// define query
		String strSql = "SELECT records_has_fields.* FROM records_has_fields" +
				" WHERE records_has_fields.record_id = " + recordId +
				" ORDER BY records_has_fields.field";
		
		// run query and process results
		resultSet = qSelectGeneric(strSql);
		
		for (int i=0; i < resultSet.size(); i++) {
			HashMap<String,String> thisRecord = new HashMap<String,String>();
			thisRecord = resultSet.get(i);
    		recordColumns.put("field", thisRecord.get("field"));
    		recordColumns.put("subfield", thisRecord.get("subfield"));
    		recordColumns.put("value", thisRecord.get("value"));			
    		recordRows.add(recordColumns);	
		}

        // return result
		return recordRows;
	}		
	
	/**
	 * <p>Select the ids of all fields in the records_has_fields table that are associated 
	 * with a record from the records table.  The return is an ArrayList of ids.</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want
	 * @return				ArrayList of IDs
	 */
	public ArrayList<Integer> selectRecordFieldIds(int recordId) {
				
		// initialize required objects
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		ArrayList<Integer> fields = new ArrayList<Integer>();
		
		// define query
		String strSql = "SELECT id FROM records_has_fields" +
				" WHERE records_has_fields.record_id = " + recordId + ";";
		
		//System.out.println("Dup Assoc Field SQL: [" + strSql + "]");
		
		// run query and process results
		resultSet = qSelectGeneric(strSql);
		
		for (int i=0; i < resultSet.size(); i++) {
			HashMap<String,String> thisRecord = new HashMap<String,String>();
			thisRecord = resultSet.get(i);
			fields.add(Integer.valueOf(thisRecord.get("id")));
		}	
		
        // return result
		return fields;
	}
	
	/**
	 * <p>Select all 852 fields in the records_has_fields table that are associated 
	 * with a record from the records table.</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want
	 * @return				an Array of field ids
	 */
	public ArrayList<Integer> selectEightFiftyTwoFields(int recordId) {		
		
		// initialize required objects
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		ArrayList<Integer> recordRows = new ArrayList<Integer>();
		
		// define query
		String strSql = "SELECT records_has_fields.id FROM records_has_fields" +
							" WHERE records_has_fields.record_id = " +  recordId +
							" AND records_has_fields.field LIKE \"852\"";
		
		// run query and process results
		resultSet = qSelectGeneric(strSql);
		
		for (int i=0; i < resultSet.size(); i++) {
			HashMap<String,String> thisRecord = new HashMap<String,String>();
			thisRecord = resultSet.get(i);
			recordRows.add(Integer.valueOf(thisRecord.get("id")));
		}
        
        // return results
		return recordRows;
	}
	
	
	/**
	 * <p>Select all records in the records table where records.processed == 0</p>
	 *
	 * @return	an ArrayList of record.id(s)
	 */
	public ArrayList<Integer> selectRecordsToScope() {
				
		// initialize required objects
		ArrayList<Integer> resultSetList = new ArrayList<Integer>();
		
		
		// define query
		String strSql = "SELECT records.id FROM records" +
				" WHERE records.scoped = 0" +
				" ORDER BY records.id ASC";
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
	
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);

	        while (resultSet.next()) {
	        		resultSetList.add(resultSet.getInt(1));
	        }

	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectString: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
		    try {
		    	stmt.close();
		    } catch (SQLException sqlEx) { } // ignore

		}
		
		if (connOpen) {
			this.closeConnection();
		}

        // return result
		return resultSetList;
	}

	/**
	 * <p>Select all BIB records in the records table 
	 * where records.processed == 0</p>
	 *
	 * @return	an ArrayList of record.id(s)
	 */
	public ArrayList<Integer> selectUnprocessedBibs() {
				
		// initialize required objects
		ArrayList<Integer> resultSetList = new ArrayList<Integer>();
		
		
		// define query
		String strSql = "SELECT records.id FROM records" +
				" WHERE records.processed = 0" +
				" AND records.type = 1" +
				" ORDER BY records.id ASC";
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
	
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);

	        while (resultSet.next()) {
	        		resultSetList.add(resultSet.getInt(1));
	        }

	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectString: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
		    try {
		    	stmt.close();
		    } catch (SQLException sqlEx) { } // ignore

		}
		
		if (connOpen) {
			this.closeConnection();
		}

        // return result
		return resultSetList;
	}
	
	
	/**
	 * <p>Select all BIB records in the records table 
	 * where records.exported == 0</p>
	 *
	 * @return	an ArrayList of record.id(s)
	 */
	public ArrayList<Integer> selectUnExportedBibs() {
				
		// initialize required objects
		ArrayList<Integer> resultSetList = new ArrayList<Integer>();
		
		
		// define query
		String strSql = "SELECT records.id FROM records" +
				" WHERE records.exported = 0" +
				" AND records.type IN (1,3)" +
				" ORDER BY records.id ASC LIMIT 50000";

		// define query unmatched only
//		String strSql = "SELECT records.id FROM records" +
//				" WHERE records.exported = 0" +
//				" AND records.type = 3" +
//				" ORDER BY records.id ASC";
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
	
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);

	        while (resultSet.next()) {
	        		resultSetList.add(resultSet.getInt(1));
	        }

	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectString: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
		    try {
		    	stmt.close();
		    } catch (SQLException sqlEx) { } // ignore

		}
		
		if (connOpen) {
			this.closeConnection();
		}

        // return result
		return resultSetList;
	}
	
	/**
	 * <p>Select all holding records</p>
	 *
	 * @return	an ArrayList of record.id(s)
	 */
	public ArrayList<HashMap<String,String>> selectHoldingRecords() {
		String strSql = "SELECT records.id, records.file_id," + 
				" records.control_identifier, records.type FROM records" +
				" WHERE records.type = 2" +
				" ORDER BY records.id ASC";
		ArrayList<HashMap<String,String>> tableResults = qSelectGenericMultiRow(strSql);
		return tableResults;
	}
	
	
	/**
	 * <p>Select the 008 string for a record</p>
	 * 
	 * @param	intRecordId		the record ID of the MARC record
	 *
	 * @return	a String containing the value of the 008 field
	 */	
	public String getZeroZeroEight(int intRecordId) {
		String retString = "";
		String strSQL = "SELECT value FROM records_has_fields WHERE record_id = " + intRecordId + " AND field LIKE '008'";
		retString = qSelectString(strSQL);
		return retString;
	}
	
	
	/**
	 * <p>Select field by number for a record</p>
	 * 
	 * @param	intRecordId		the record ID of the MARC record
	 * @param	fieldID			The field ID you want to get
	 *
	 * @return	a String containing the value of the identifies field field
	 */	
	public String getFieldByNumber(int intRecordId, String fieldID) {
		String retString = "";
		String strSQL = "SELECT value FROM records_has_fields WHERE record_id = " + intRecordId + " AND field LIKE '" + fieldID + "'";
		retString = qSelectString(strSQL);
		return retString;
	}

	/**
	 * <p>Select field by number for a record</p>
	 * 
	 * @param	intRecordId		the record ID of the MARC record
	 * @param	fieldID			The field ID you want to get
	 *
	 * @return	a String containing the value of the identifies field field
	 */	
	public ArrayList<HashMap<String,String>> getFieldInfo(int intRecordId, String fieldID) {
		String strSQL = "SELECT id, value FROM records_has_fields WHERE record_id = " + intRecordId + " AND field LIKE '" + fieldID + "'";
		ArrayList<HashMap<String,String>> tableResults = qSelectGenericMultiRow(strSQL);
		return tableResults;
	}
	
	
	/**
	 * <p>Select field by number for a record</p>
	 * 
	 * @param	intRecordId		the record ID of the MARC record
	 * @param	fieldID			The field ID you want to get
	 *
	 * @return	a String containing the value of the identifies field field
	 */	
	public String getFieldByID(int fieldID) {
		String retString = "";
		String strSQL = "SELECT value FROM records_has_fields WHERE id = " + fieldID;
		retString = qSelectString(strSQL);
		return retString;
	}
	
	
	/**
	 * <p>Select field:sub_field value for an identified 
	 * record, field, and subfield</p>
	 *
	 * @param  	recordId	the id of the record whose field you want
	 * @param	fieldNumber	the MARC field you are interested in
	 * @param 	subField	the sub-field you want the value of
	 * 
	 * @return				an ArrayList of sub-field values
	 */
	public ArrayList<String> selectSubFieldValues(int recordId, String fieldNumber, String subField) {		
		
		// initialize required objects
		ArrayList<String> subValues = new ArrayList<String>();
		ArrayList<Integer> fieldResultSet = null;
		
		// define query
		String strSql = "SELECT id FROM records_has_fields" +
				" WHERE record_id = " + recordId + " AND field LIKE '" + fieldNumber + "'";
		
		// run query and process results
		fieldResultSet = qSelectIntList(strSql);
		
		// for each matching field result
		for (Integer fid : fieldResultSet) {
			// now pull the value of the subfield I need and
			// put it in the return array
			
			// define the sub-field query
			String strSubSql = "SELECT value FROM fields_has_subfields" +
					" WHERE field_id = " + fid + " AND subfield LIKE '" + subField + "'";
			
			// run query and process results
			subValues.addAll(qSelectStringList(strSubSql));
		}

        // return results
		return subValues;
	}
	
	/**
	 * <p>Select field:sub_field value for an identified 
	 * record, field, and subfield</p>
	 *
	 * @param  	recordId	the id of the record whose field you want
	 * @param	fieldNumber	the MARC field you are interested in
	 * @param 	subField	the sub-field you want the value of
	 * 
	 * @return				an ArrayList of sub-field values
	 */
	public ArrayList<String> selectSubFieldValuesByID(int fieldNumber, String subField) {		
		
		// initialize required objects
		ArrayList<String> subValues = new ArrayList<String>();

		String strSubSql = "SELECT value FROM fields_has_subfields" +
				" WHERE field_id = " + fieldNumber + " AND subfield LIKE '" + subField + "'";
		subValues.addAll(qSelectStringList(strSubSql));

        // return results
		return subValues;
	}
	

	
	/**
	 * <p>Select the record type</p>
	 *
	 * @return	the record type.  1=bib / 2 = holding
	 */	
	public int getRecordType(int intRecordId) {
		int retInt = 0;
		String strSQL = "SELECT type FROM records WHERE id = " + intRecordId;
		retInt = qSelectInt(strSQL);
		return retInt;
	}	
	
	/**
	 * <p>A genreic object for querying the db for a single numeric
	 * value such as an id field, etc.  Field select list must
	 * contain only a single field.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query with a single SELECT field of type INTEGER
	 * @return			The integer value of the return column
	 */	
	private int qSelectInt(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		int retId = 0;

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);
	        if (resultSet.next()) {
	        	retId = resultSet.getInt(1);
	        }
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { 
			    // handle any errors
			    System.out.println("SQLException SqlModel.java qSelectInt-A: " + sqlEx.getMessage());
			    System.out.println("SQLState: " + sqlEx.getSQLState());
			    System.out.println("VendorError: " + sqlEx.getErrorCode());	        	
	        } 
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectInt: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retId;
	}	
	
	/**
	 * <p>A genreic object for querying the db for a single numeric
	 * value such as an id field, etc.  Field select list must
	 * contain only a single field.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query with a single SELECT field of type INTEGER
	 * @return			The integer value of the return column
	 */	
	private double qSelectDouble(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		double retVal = 0;

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);
	        if (resultSet.next()) {
	        	retVal = resultSet.getLong(1);
	        }
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectLong: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
		    try {
		    	stmt.close();
		    } catch (SQLException sqlEx) { } // ignore

		}
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retVal;
	}
	
	/**
	 * <p>A genreic object for querying the db for multiple return
	 * values from a single numeric field. Field select list must 
	 * contain only a single field.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query with a single SELECT field of type INTEGER
	 * @return			The returned list of integers
	 */	
	private ArrayList<Integer> qSelectIntList(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		ArrayList<Integer> retId = new ArrayList<Integer>();

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);
        	while (resultSet.next()) {
        		retId.add(resultSet.getInt(1));
        	}
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { 
			    // handle any errors
			    System.out.println("SQLException SqlModel.java qSelectInt-A: " + sqlEx.getMessage());
			    System.out.println("SQLState: " + sqlEx.getSQLState());
			    System.out.println("VendorError: " + sqlEx.getErrorCode());	        	
	        } 
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectInt: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retId;
	}
		
	
	
	/**
	 * <p>A genreic object for querying the db for a single String
	 * value. Field select list must contain only a single field.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query with a single SELECT field of type STRING
	 * @return			The string value of the return column
	 */		
	private String qSelectString(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		String retString = "";

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);
	        if (resultSet.next()) {
	        	retString = resultSet.getString(1);
	        }
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectString: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		} finally {
		    try {
		    	stmt.close();
		    } catch (SQLException sqlEx) { } // ignore

		}
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retString;
	}
	
	/**
	 * <p>A genreic object for querying the db for multiple return
	 * values from a single String field. Field select list must 
	 * contain only a single field.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query with a single SELECT field of type String
	 * @return			The returned list of String values
	 */	
	private ArrayList<String> qSelectStringList(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		ArrayList<String> retString = new ArrayList<String>();

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);
        	while (resultSet.next()) {
        		retString.add(resultSet.getString(1));
        	}
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { 
			    // handle any errors
			    System.out.println("SQLException SqlModel.java qSelectInt-A: " + sqlEx.getMessage());
			    System.out.println("SQLState: " + sqlEx.getSQLState());
			    System.out.println("VendorError: " + sqlEx.getErrorCode());	        	
	        } 
		    
		
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectInt: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retString;
	}
	
	/**
	 * <p>A generic object for executing a SELECT querying 
	 * against the db that returns a single row/record.  Each slice
	 * of the Array List holds one FieldName:Value pair from
	 * the returned record.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query
	 * @return			A resultSet object containing the result of the query
	 */	
	private ArrayList<HashMap<String,String>> qSelectGeneric(String strSql) {
		
		
		ArrayList<HashMap<String,String>> retList = new ArrayList<HashMap<String,String>>();
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		ResultSetMetaData rsmd = null;

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);	
	        rsmd = resultSet.getMetaData();
	        int colCount = rsmd.getColumnCount();
        	while (resultSet.next()) {
		        for (int i=1; i < (colCount + 1); i++) {
		        	HashMap<String, String> fieldHash = new HashMap<String, String>();
		            fieldHash.put(rsmd.getColumnName(i), resultSet.getString(i));
		            retList.add(fieldHash);
		        }
        	}
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectGeneric: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retList;
	}
	
	
	
	
	/**
	 * <p>A generic object for executing a SELECT querying 
	 * against the db that returns multiple fields from 
	 * multiple rows.  Basically mimics a resultSet</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query
	 * @return			A resultSet object containing the result of the query
	 */	
	private ArrayList<HashMap<String,String>> qSelectGenericMultiRow(String strSql) {
		
		
		// ArrayList<ArrayList<HashMap<String,String>>> retList = new ArrayList<ArrayList<HashMap<String,String>>>();
		ArrayList<HashMap<String,String>> retList = new ArrayList<HashMap<String,String>>();
		
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		ResultSetMetaData rsmd = null;

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);	
	        rsmd = resultSet.getMetaData();
	        int colCount = rsmd.getColumnCount();
        	while (resultSet.next()) {
        		HashMap<String, String> fieldHash = new HashMap<String, String>();
		        for (int i=1; i < (colCount + 1); i++) {
		            fieldHash.put(rsmd.getColumnName(i), resultSet.getString(i));
		        }
		        retList.add(fieldHash);
        	}
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectGeneric: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retList;
	}
	
	
	/**
	 * <p>A generic object for executing a SELECT querying 
	 * against the db that returns multiple fields from 
	 * multiple rows.  Basically mimics a resultSet</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query
	 * @return			A resultSet object containing the result of the query
	 */	
	private ArrayList<HashMap<String,String>> qSelectGenericMultiRowTwo(String strSql) {
		
		
		// ArrayList<ArrayList<HashMap<String,String>>> retList = new ArrayList<ArrayList<HashMap<String,String>>>();
		ArrayList<HashMap<String,String>> retList = new ArrayList<HashMap<String,String>>();
		
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		Statement stmt = null;
		ResultSet resultSet = null;
		ResultSetMetaData rsmd = null;

		
		// run query
		try {
			stmt = conn.createStatement();
	        resultSet = stmt.executeQuery(strSql);	
	        rsmd = resultSet.getMetaData();
	        int colCount = rsmd.getColumnCount();
        	while (resultSet.next()) {
        		System.out.println("Processing Row");
        		HashMap<String, String> fieldHash = new HashMap<String, String>();
		        for (int i=1; i < (colCount + 1); i++) {
		        	System.out.println("Adding column " + rsmd.getColumnName(i) + " to has");
		            fieldHash.put(rsmd.getColumnName(i), resultSet.getString(i));
		        }
		        System.out.println("Adding Hash to ArrayList");
		        retList.add(fieldHash);
		        System.out.println("ArrayList Size: " + retList.size());
        	}
	        try {
	        	resultSet.close();
	        } catch (SQLException sqlEx) { } // ignore
		} catch (SQLException ex){
		    // handle any errors
		    System.out.println("SQLException SqlModel.java qSelectGeneric: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		}	
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return retList;
	}
	
	
	/////////////////////////////////////////////////////////////////
	//                   INSERT FUNCTIONS                          //
	/////////////////////////////////////////////////////////////////
	
	/**
	 * <p>Inserts a new File record into the database.</p>
	 *
	 * @param  	currCode	the MARC institutional code for the creator of the file
	 * @param  	fileName  	the filename of the file
	 * @param	modDate		the system last modified time stamp of the file
	 * @param	fileType	1=marc, 2=csv, 3=text, 4=excel
	 * @return				A file ID if found or 0 if not found
	 */
	public int insertFileRecord(String currCode, String fileName, long modDate, int fileType) {
		// define query
		String strSql = "INSERT INTO files " +
				"(institution_code, filename, modification_date, type)" +
				" VALUES" + 
				" ('" + currCode + "', '" + fileName + "', " + modDate + ", " + fileType + ");";
		int recordId = qInsert(strSql);
		return recordId;
	}
	
	/**
	 * <p>Inserts a marc-record record entry into the records table.</p>
	 *
	 * @param  	fileId				the id from the files table for the file that the marc record was read from
	 * @param  	recType  			the type of record.  1 = bib, 2 = holding
	 * @param	controlIdentifier	the record control identifier
	 * @return						The record if of the inserted record.  Returns 0 on failure.
	 */
	public int insertRecordRecord(int fileId, int recType, String controlIdentifier, double moddate) {
		String strSql;
		// first check to see if there is a recycled record ID available
		String recycleSql = "SELECT foreign_key FROM recycled_fields WHERE type = 1";
		int useId = qSelectInt(recycleSql);	
		if (useId > 0) {
			strSql = "INSERT INTO records " +
					"(id, file_id, control_identifier, type, moddate)" +
					" VALUES" + 
					" (" + useId + ", " + fileId + ", '" + controlIdentifier + "', " + recType + ", " + moddate + ");";
			String strDeleteRecycleSql = "DELETE from recycled_fields WHERE type = 1 AND foreign_key = " + useId + ";";
			qUpdate(strDeleteRecycleSql);
		} else {
			strSql = "INSERT INTO records " +
					"(file_id, control_identifier, type, moddate)" +
					" VALUES" + 
					" (" + fileId + ", '" + controlIdentifier + "', " + recType + ", " + moddate + ");";
		}
		int recordId = qInsert(strSql);
		return recordId;
	}
	
	/**
	 * <p>Insert a field associated with a record into the records_has_fields table.</p>
	 *
	 * @param  	recordId	the id of the associated record
	 * @param  	fieldVal	the marc tag for the field
	 * @param  	valueVal	the data contained in the field as string
	 * @param  	fieldType	an id from the field_typeds table indicated whether a control or data field.  1 = control, 2 = data.
	 * @return				the id of the inserted field
	 */
	public int insertFieldRecord(int recordId, String fieldVal, String valueVal, int fieldType) {
		valueVal = valueVal.replace("'", "''");
		String strSql;
		// first check to see if there is a recycled record ID available
		String recycleSql = "SELECT foreign_key FROM recycled_fields WHERE type = 2";
		int useId = qSelectInt(recycleSql);	
		if (useId > 0) {
			strSql = "INSERT INTO records_has_fields " +
					"(id, record_id, field, value, type)" +
					" VALUES" + 
					" (" + useId + ", " + recordId + ", '" + fieldVal + "', '" + valueVal + "', " + fieldType + ");";
			String strDeleteRecycleSql = "DELETE from recycled_fields WHERE type = 2 AND foreign_key = " + useId + ";";
			qUpdate(strDeleteRecycleSql);
		} else {
			strSql = "INSERT INTO records_has_fields " +
					"(record_id, field, value, type)" +
					" VALUES" + 
					" (" + recordId + ", '" + fieldVal + "', '" + valueVal + "', " + fieldType + ");";
		}
		int insertId = qInsert(strSql);
		return insertId;
	}
	
	/**
	 * <p>Insert a subfield associated with a field into the fields_has_subfields table.</p>
	 *
	 * @param  	fieldId			the id of the associated field from the fields table
	 * @param  	subfieldTag		the marc tag for the sub-field
	 * @param  	subfieldVal		the data contained in the sub-field as string
	 * @return					the id of the inserted sub-field
	 */
	public int insertSubfieldRecord(int fieldId, String subfieldTag, String subfieldVal) {
		subfieldVal = subfieldVal.replace("'", "''");
		String strSql;
		// first check to see if there is a recycled record ID available
		String recycleSql = "SELECT foreign_key FROM recycled_fields WHERE type = 3";
		int useId = qSelectInt(recycleSql);	
		if (useId > 0) {
			strSql = "INSERT INTO fields_has_subfields " +
					"(id, field_id, subfield, value)" +
					" VALUES" + 
					" (" + useId + ", " + fieldId + ", '" + subfieldTag + "', '" + subfieldVal + "');";
			String strDeleteRecycleSql = "DELETE from recycled_fields WHERE type = 3 AND foreign_key = " + useId + ";";
			qUpdate(strDeleteRecycleSql);
		} else {
			strSql = "INSERT INTO fields_has_subfields " +
					"(field_id, subfield, value)" +
					" VALUES" + 
					" (" + fieldId + ", '" + subfieldTag + "', '" + subfieldVal + "');";
		}
		int insertId = qInsert(strSql);
		return insertId;
	}
	
	/**
	 * <p>A generic object for executing an INSERT 
	 * against the db.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query
	 * @return			A boolean value indicating success or failure
	 */
	private int qInsert(String strSql) {
		
		// initialize required objects
		Statement stmt = null;
		ResultSet rs = null;
		int insertId = 0;
		
		if (!connOpen) {
			this.openConnection();
		}

		try {

		    stmt = conn.createStatement();
		    stmt.executeUpdate(strSql);
		    rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
		    if (rs.next()) {
		    	insertId = rs.getInt(1);
		    }
		    rs.close();
		    
		} catch (SQLException ex){
			    // handle any errors
			    System.out.println("SQLException SqlModel.java qInsert: " + ex.getMessage());
			    System.out.println("SQLState: " + ex.getSQLState());
			    System.out.println("VendorError: " + ex.getErrorCode());
			    System.out.println("SQL Statement: " + strSql);

		} finally {

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException ex) {
		            // ignore
		        }
		    }
		}
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return insertId;
	}

	/////////////////////////////////////////////////////////////////
	//                   UPDATE FUNCTIONS                          //
	/////////////////////////////////////////////////////////////////
	
	/**
	 * <p>Updates the system last modified date of a file record.</p>
	 *
	 * @param  	strFileId		the file id to update
	 * @param  	fileModDate  	the new mod date
	 * @return				1 = success, 0 = failure
	 */
	public boolean updateFileModDate(int intFileId, long fileModDate) {
		String strSql = "UPDATE files " +
				"SET modification_date = " + fileModDate +
				" WHERE id = " + intFileId+ ";";
		boolean retFlag = qUpdate(strSql);
		return retFlag;
	}
	
	/**
	 * <p>Update the last modified date on a record.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @param	moddate		the last modified date
	 * @return				true on success, false on failure
	 */
	public boolean updateRecordRecordModdate(int recordId, double moddate) {
		String strSql = "UPDATE records" +
				" SET moddate = " + String.valueOf(moddate) +
				" WHERE id = " + String.valueOf(recordId);
		boolean marked = qUpdate(strSql);
		return marked;
	}
	
	
	/**
	 * <p>Updates a record status to exported.</p>
	 *
	 * @param  	strFileId		the file id to update
	 * @param  	fileModDate  	the new mod date
	 * @return				1 = success, 0 = failure
	 */
	public boolean updateExported(int recordID) {
		String strSql = "UPDATE records " +
				"SET exported = 1 WHERE id = " + recordID+ ";";
		boolean retFlag = qUpdate(strSql);
		return retFlag;
	}
	
	/**
	 * <p>A generic object for executing an UPDATE 
	 * against the db.</p>
	 *
	 * @param  	strSql	A well formed SQL SELECT query
	 * @return			A boolean value indicating success or failure
	 */		
	private boolean qUpdate(String strSql) {
		
		if (!connOpen) {
			this.openConnection();
		}
		
		// initialize required objects
		boolean success = false;
		Statement stmt = null;
		
		try {

		    stmt = conn.createStatement();
		    stmt.executeUpdate(strSql);
		    success = true;   
		} catch (SQLException ex){
			    // handle any errors
			    System.out.println("SQLException SqlModel.java qUpdate: " + ex.getMessage());
			    System.out.println("SQLState: " + ex.getSQLState());
			    System.out.println("VendorError: " + ex.getErrorCode());

		} finally {

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException ex) {
		            // ignore
		        }
		    }
		}
		
		if (connOpen) {
			this.closeConnection();
		}
		
		return success;
	}

	/////////////////////////////////////////////////////////////////
	//                   DELETE FUNCTIONS                          //
	/////////////////////////////////////////////////////////////////
	
	/**
	 * <p>Deletes a record</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want to delete
	 * @return				true on success, false on failure
	 */
	public boolean deleteRecordRecord(int recordId) {
		// first add the record id to the recycle bin
		String recycleSql = "INSERT into recycled_fields (foreign_key, type) VALUES (" + recordId + ", 1)";
		qInsert(recycleSql);
		
        // now delete the fields
		String strSql = "DELETE FROM records" +
				" WHERE id = " + recordId;
		boolean success = qUpdate(strSql);
		return success;
	}		
	
	/**
	 * <p>Deletes all fields associated with a designated record</p>
	 *
	 * @param  	recordId	the id of the record whose fields you want to delete
	 * @return				true on success, false on failure
	 */
	public boolean deleteRecordFields(int recordId) {
		
		// initialize needed objects
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
	
		// first add all field ids to the recycle bin
		String strSqlFields = "SELECT id FROM records_has_fields" +
				" WHERE record_id = " + recordId;
		
		resultSet = qSelectGeneric(strSqlFields);

		for (int i=0; i < resultSet.size(); i++) {
			HashMap<String,String> thisRecord = new HashMap<String,String>();
			thisRecord = resultSet.get(i);
			String recycleSql = "INSERT into recycled_fields (foreign_key, type) VALUES (" + thisRecord.get("id") + ", 2)";
			qInsert(recycleSql);
		}	

        // now delete the fields
		String strSql = "DELETE FROM records_has_fields" +
				" WHERE record_id = " + recordId;
		boolean success = qUpdate(strSql);
		return success;
	}

	
	/**
	 * <p>Deletes all sub-fields associated with a designated field</p>
	 *
	 * @param  	fieldId		the id of the field whose sub-fields you want to delete
	 * @return				true on success, false on failure
	 */
	public boolean deleteSubFields(int fieldId) {
		
		// initialize objects
		ArrayList<HashMap<String,String>> resultSet = new ArrayList<HashMap<String,String>>();
		boolean success = false;
		
		// first add all subfield ids to the recycle bin
		String strSqlFields = "SELECT id FROM fields_has_subfields" +
				" WHERE field_id = " + fieldId;
		
		//System.out.println("Getting Subfields");
		resultSet = qSelectGeneric(strSqlFields);
		//System.out.println("Got Subfields");
		
		if (resultSet.size() > 0) {
		
			for (int i=0; i < resultSet.size(); i++) {
				HashMap<String,String> thisRecord = new HashMap<String,String>();
				thisRecord = resultSet.get(i);
				// System.out.println("Adding Subfield with ID " + thisRecord.get("id") + " to the recycle bin");
				String recycleSql = "INSERT into recycled_fields (foreign_key, type) VALUES (" + thisRecord.get("id") + ", 3)";
				// System.out.println("DebugRecycleSQL: " + recycleSql);
				qInsert(recycleSql);
				//if (intRecycle > 0) {
				//	System.out.println("Successfuylly Added Subfield " + thisRecord.get("id") + " to recycle bin with id " + intRecycle);
				//} else {
				//	System.out.println("Failed to add subfield " + thisRecord.get("id") + " to recycle bin.");
				//}
			}
			
			
			
			// now delete the subfield
			String strSql = "DELETE FROM fields_has_subfields" +
					" WHERE field_id = " + fieldId;
			//System.out.println("Deleting all Subfields");
			success = qUpdate(strSql);
			//if (success) {
			//	System.out.println("Subfields Deleted");
			//} else {
			//	System.out.println("Failed to delete subfields for field " + fieldId);
			//}
			
		} else {
			System.out.println("No Subfields for this field");
			success = true;
		}
		
		return success;
	}
	

//////////////////////
// Logging  methods //
//////////////////////
		
	/**
	 * <p>Writes a new log message to the database runlog table.</p>
	 *
	 * @param  	messageType	[1] = error, [2] = info, [3] = debug
	 * @param  	fileName  	the filename of the file initiating the message
	 * @param	lineNumber	the line number from the file where the message is initiated
	 * @param	messageText	the message
	 */
	public int insertLogMessage(int messageType, String fileName, int lineNumber, String messageText) {
		String strSql = "INSERT INTO runlog " +
				"(type, file, line, message)" +
				" VALUES" + 
				" (" + messageType + ", '" + fileName + "', " + lineNumber + ", '" + messageText + "');";
		int insertId = qInsert(strSql);
		return insertId;
	}
	
				
	/////////////////////////////////////////////////////////////////
	//                   SET FLAGS FUNCTIONS                       //
	/////////////////////////////////////////////////////////////////
	
	/**
	 * <p>Flags a record entry in the records table as having been processed, meaning
	 * that all of the data contained in the original marc recrod has been saved to
	 * the fields and subfields tables.  This identifies the record as suitable for 
	 * output publishing.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordProcessed(int recordId) {
		String strSql = "UPDATE records" +
				" SET processed = 1" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}	
	

	/**
	 * <p>Flags a record entry in the records table as having not been processed, meaning
	 * that all of the data contained in the original marc recrod has yet to be saved to
	 * the fields and subfields tables.  This identifies the record as not yet ready for 
	 * output publishing.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordUnProcessed(int recordId) {
		String strSql = "UPDATE records" +
				" SET processed = 0" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}
	
	/**
	 * <p>Flags a record entry in the records table as having been filtered for scope,
	 * meaning that it passed all scope tests.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordScoped(int recordId) {
		String strSql = "UPDATE records" +
				" SET scoped = 1" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}	
	

	/**
	 * <p>Flags a record entry in the records table as having not been filtered for
	 * scope, meaning it has yet to pass scope tests.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordUnScoped(int recordId) {
		String strSql = "UPDATE records" +
				" SET scoped = 0" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}
	
	/**
	 * <p>Flags a record entry in the records table as having been exported.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordExported(int recordId) {
		String strSql = "UPDATE records" +
				" SET exported = 1" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}	
	

	/**
	 * <p>Flags a record entry in the records table as not having been exported.</p>
	 *
	 * @param  	recordId	the id of the record to flag
	 * @return				true on success, false on failure
	 */
	public boolean setRecordRecordNotExported(int recordId) {
		String strSql = "UPDATE records" +
				" SET exported = 0" +
				" WHERE id = " + recordId;
		boolean marked = qUpdate(strSql);
		return marked;
	}
	
	
	public void currentTime() {
	        Calendar cal = Calendar.getInstance();
	        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
	        System.out.println( sdf.format(cal.getTime()) );
	}
	
	
}
