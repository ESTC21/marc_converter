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
 *  this software either in whole or in part for scholarly and educational purposes.</p>
 */

package com.carlstahmer.estc.recordimport.daemon;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author cstahmer
 * 
 * <p>Class for exporting a records from the SQL DB
 * to an RDF file.</p>
 */

/*
 * Quick note.  You shouldn't actually have to change any code
 * until line 534, where it starts constructing the RDF file.  
 * 
 */

public class ExportJenaRDF {
	
	Conf configObj;
	SqlModel sqlObj;
	Logger logger;
	String rdfHeader;
	String rdfAbout;
	String rdfString;
	String rdfFooter;

	public ExportJenaRDF(Conf config, SqlModel sqlModObj) {
		configObj = config;
		sqlObj = sqlModObj;
		logger = new Logger(config);
	}
	
	public boolean makeJenaRDFAllBibs(String domainURI) {
		boolean success = false;
		
		// Get all records for this domain and process
		ArrayList<Integer> recordsQueue = sqlObj.selectUnExportedBibs();
		for (int i=0;i < recordsQueue.size();i++) {
			int workingRecordID = recordsQueue.get(i);
			makeJennaRDF(workingRecordID, domainURI);
		}
		
		return success;
	}
	
	// create an RDF representation of a resource and write it 
	// to a file
	public boolean makeJennaRDF(int recordID, String domainURI) {
		boolean ret = false;
		
		// set the id for the item.  Right now we're using the record control
		// id as the id.  Before we are done we'll convert this to an
		// OCLC Identifier.  (You don't need to worry about what that is
		// right now.  Just want you to know that we'll be changing this
		// bit of code in the near future to load a different field in the
		// itemID variable.)
		String itemID;
		itemID = sqlObj.selectRecordControlId(recordID);

		
		System.out.println("Processing record " + recordID + " item " + itemID);

		// construct unique identifier (URI) for the item
		String uniqueRI = "http://" + domainURI + "/" + itemID;
		
		// setup needed output variables
		String finalDate = "";
		String finalDateString = "";
		String finalTitle = "Utitled or Title not Known";
		ArrayList<String> subjectTerms = new ArrayList<String>();
		ArrayList<String> coverage = new ArrayList<String>();
		ArrayList<ArrayList<String>> authorArray = new ArrayList<ArrayList<String>>();
		ArrayList<String> fiveHundNotes = new ArrayList<String>();
		ArrayList<String> fiveHundTenNotes = new ArrayList<String>();
		ArrayList<String> surrogateSub = new ArrayList<String>();
		String prodInfo = ""; // dct:publisher
		String creationEpoch = ""; // dct:created
		ArrayList<String> languageCode = new ArrayList<String>(); //   dc:language
		ArrayList<String> associatedPlaces = new ArrayList<String>(); //   dc:coverage		
		ArrayList<String> contentCarrierTypes = new ArrayList<String>(); //   dct:type	
		
		
		/*
		 * All of the code from here until indicated shouldn't need
		 * to be touched.  It will load and pre-process all values
		 * needed and put them into the variables initialized above.
		 * All you need to do is figure out to write the values of
		 * These variables out to a Jena style RDF.
		 */
		
		// Get all of the fields associated with this record
		ArrayList<Integer> fieldsArray = sqlObj.selectRecordFieldIds(recordID);
		for (int ix=0;ix < fieldsArray.size();ix++) {
			// loop through the fields and process
			Integer fieldID = fieldsArray.get(ix);
			String fieldType = sqlObj.selectFieldType(fieldID);
			
			int i = 0;
			
			if (fieldType.equals("008")) { 
				// if 008 date
				// get raw value
				String rawZeroZeroEight = sqlObj.getFieldByNumber(recordID, "008");
				if (rawZeroZeroEight != null && rawZeroZeroEight.length() > 13 ) {
					String one = String.valueOf(rawZeroZeroEight.charAt(7));
					String two = String.valueOf(rawZeroZeroEight.charAt(8));
					String three = String.valueOf(rawZeroZeroEight.charAt(9));
					String four = String.valueOf(rawZeroZeroEight.charAt(10));
					String five = String.valueOf(rawZeroZeroEight.charAt(11));
					String six = String.valueOf(rawZeroZeroEight.charAt(12));
					String seven = String.valueOf(rawZeroZeroEight.charAt(13));
					String eight = String.valueOf(rawZeroZeroEight.charAt(14));
					String startDate = one + two + three + four;
					startDate = startDate.replaceAll("[^\\d.]", "");
					String endDate = five + six + seven + eight;
					endDate = endDate.replaceAll("[^\\d.]", "");

					if (startDate  != null && startDate.length() == 4) {
						finalDate = startDate;
						finalDateString = finalDate;
						if ( endDate != null && endDate.length() == 4) {
							finalDate = finalDate + "-" + endDate;
							finalDateString = finalDate + "-" + endDate;
						}
					}
				}
			} else if (fieldType.equals("041") || fieldType.equals("765")) {
				// if 041 or 765 - Language Code - ArrayList<String> languageCode = new ArrayList<String>(); //   dc:language
				// get subfields
				String fCode = "";
				ArrayList<String> subFieldAl = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAl.size();i++) {
					fCode = fixCarrots(fixAmper(subFieldAl.get(i)));
				}
				if (fCode != null && fCode.length() > 0) {
					languageCode.add(fCode);
				}
			} else if (fieldType.equals("100") || fieldType.equals("700")) {
				// if 100 author or 700
				String thisAuthor = "";
				// get raw value
				String rawValue = sqlObj.getFieldByID(fieldID);;
				// get subfields
				String subA = "";
				String subB = "";
				String subC = "";
				String subD = "";
				String subE = "";
				
				ArrayList<String> retVal = new ArrayList<String>();
				
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					subA = subFieldA.get(i);
				}
				ArrayList<String> subFieldB = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (i=0;i < subFieldB.size();i++) {
					subB =  subFieldB.get(i);
				}
				ArrayList<String> subFieldC = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (i=0;i < subFieldC.size();i++) {
					subC =  subFieldC.get(i);
				}
				ArrayList<String> subFieldD = sqlObj.selectSubFieldValuesByID(fieldID, "d");
				for (i=0;i < subFieldD.size();i++) {
					subD =  subFieldD.get(i);
				}
				ArrayList<String> subFieldE = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldE.size();i++) {
					subE =  subFieldE.get(i);
				}
				
				String seperator = " ";
				if (subA  != null && subA.length() > 0) {
					if (subB != null && subB.length() == 0 && subC != null && subC.length() == 0 && subD != null && subD.length() == 0) {
						String trimmed = removeFinalComma(subA);
						thisAuthor = trimmed;
					} else {
						thisAuthor = subA;
						if (subB != null && subB.length() > 0) {
							if (subC != null && subC.length() == 0 && subD != null && subD.length() == 0) {
								String trimmedB = removeFinalComma(subB);
								thisAuthor = thisAuthor + seperator + trimmedB;
							} else {
								thisAuthor = thisAuthor + seperator + subB;
							}
						}
						if (subC != null && subC.length() > 0) {
							if (subD != null && subD.length() == 0) {
								String trimmedC = removeFinalComma(subC);
								thisAuthor = thisAuthor + seperator + trimmedC;
							} else {
								thisAuthor = thisAuthor + seperator + subC;
							}
						}	
						if (subD != null && subD.length() > 0) {
								thisAuthor = thisAuthor + seperator + subD;
						}
					}
				} else if (rawValue != null && rawValue.length() > 0) {
					thisAuthor = rawValue;
				}
				
				thisAuthor = fixCarrots(fixAmper(thisAuthor));
				
				if (subE != null && subE.length() == 0) {
					subE = "AUT";
				}
				
				String upperE = fixCarrots(fixAmper(subE.toUpperCase()));
				retVal.add(upperE);
				retVal.add(thisAuthor);

				authorArray.add(retVal);
			} else if (fieldType.equals("110") || fieldType.equals("710")) {
				// corporate author
				ArrayList<String> retValc = new ArrayList<String>();
				String subAc = "";
				String subEc = "";
				ArrayList<String> subFieldAc = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAc.size();i++) {
					subAc = fixCarrots(subFieldAc.get(i));
				}
				ArrayList<String> subFieldEc = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldEc.size();i++) {
					subEc =  subFieldEc.get(i);
				}
				
				String upperEc = fixCarrots(fixAmper(subEc.toUpperCase()));
				retValc.add(upperEc);
				retValc.add(subAc);
				authorArray.add(retValc);
			} else if (fieldType.equals("111") || fieldType.equals("711")) {
				String thisAuthorm = "";
				// get subfields
				String subAm = "";
				String subBm = "";
				String subCm = "";
				String subDm = "";
				String subJm = "";
				ArrayList<String> retValm = new ArrayList<String>();
				
				ArrayList<String> subFieldAm = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAm.size();i++) {
					subAm = subFieldAm.get(i);
				}
				ArrayList<String> subFieldBm = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (i=0;i < subFieldBm.size();i++) {
					subBm =  subFieldBm.get(i);
				}
				ArrayList<String> subFieldCm = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (i=0;i < subFieldCm.size();i++) {
					subCm =  subFieldCm.get(i);
				}
				ArrayList<String> subFieldDm = sqlObj.selectSubFieldValuesByID(fieldID, "d");
				for (i=0;i < subFieldDm.size();i++) {
					subDm =  subFieldDm.get(i);
				}
				ArrayList<String> subFieldJm = sqlObj.selectSubFieldValuesByID(fieldID, "j");
				for (i=0;i < subFieldJm.size();i++) {
					subJm =  subFieldJm.get(i);
				}
				
				String seperatorm = ", ";
				if (subAm  != null && subAm.length() > 0) {
					thisAuthorm = subAm;
					if (subBm != null && subBm.length() > 0 ) {
						thisAuthorm = thisAuthorm + seperatorm + subBm;
					} 
					if (subCm != null && subCm.length() > 0 ) {
						thisAuthorm = thisAuthorm + seperatorm + subCm;
					} 
					if (subDm != null && subDm.length() > 0 ) {
						thisAuthorm = thisAuthorm + seperatorm + subDm;
					} 
				} else {
					thisAuthorm = "Authored at Unknown Meeting";
				}
				
				thisAuthorm = fixCarrots((fixAmper(thisAuthorm)));
				
				if (subJm != null && subJm.length() == 0) {
					subJm = "AUT";
				}
				
				String upperJm = fixCarrots(fixAmper(subJm.toUpperCase()));
				retValm.add(upperJm);
				retValm.add(thisAuthorm);

				authorArray.add(retValm);
			} else if (fieldType.equals("245")) {
				// if 245 title
				
				// get raw value
				String rawValue = sqlObj.getFieldByID(fieldID);;
				// get subfields
				String titleA = "";
				String titleB = "";
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					titleA = fixCarrots(fixAmper(subFieldA.get(i)));
				}
				ArrayList<String> subFieldB = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (i=0;i < subFieldB.size();i++) {
					titleB =  fixCarrots(fixAmper(subFieldB.get(i)));
				}
				
				if (titleA != null && titleA.length() > 0) {
					finalTitle = titleA;
					if (titleB != null && titleB.length() > 0) {
						finalTitle = finalTitle + " " + titleB;
					}
				} else if (rawValue != null && rawValue.length() > 0) {
					finalTitle = fixCarrots(fixAmper(rawValue));
				} else {
					finalTitle = "Utitled or Title not Known";
				}

			} else if (fieldType.equals("336") || fieldType.equals("338")) {
				// IF Field 336 - Content Type - ArrayList<String> contentCarrierTypes // dct:type
				// get subfields
				String subAct = "";
				ArrayList<String> subFieldAct = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAct.size();i++) {
					subAct = subFieldAct.get(i);
					if (subAct  != null && subAct.length() > 0) {
						contentCarrierTypes.add(fixCarrots(fixAmper(subAct)));
					}
				}
				
			} else if (fieldType.equals("362")) {
				// IF Field 362 - Sequence Dates
				
				// get subfields
				String seqNote = "";
				ArrayList<String> subFieldAsq = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				ArrayList<String> subFieldZsq = sqlObj.selectSubFieldValuesByID(fieldID, "z");
				for (i=0;i < subFieldAsq.size();i++) {
					seqNote = "Date Sequence: " + subFieldAsq.get(i) + ".";
					if (subFieldZsq.size() >= subFieldAsq.size()) {
						seqNote = " Source: " + subFieldZsq.get(i) + ".";
					}
					if (seqNote != null && seqNote.length() > 0) {
						fiveHundNotes.add(fixCarrots(fixAmper(seqNote)));
					}
				}
			} else if (fieldType.equals("370")) {
				// IF Field 370 - Associated Place -- ArrayList<String> associatedPlaces; // dc:coverage + 500 note
				
				String subCap = "";
				String subFap = "";
				String subGap = "";
				String subIap = "";
				String subSap = "";
				String subTap = "";
				String subVap = "";

				ArrayList<String> subFieldCap = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldCap.size();i++) {
					subCap = subFieldCap.get(i);
				}
				ArrayList<String> subFieldFap = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldFap.size();i++) {
					subFap =  subFieldFap.get(i);
				}
				ArrayList<String> subFieldGap = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldGap.size();i++) {
					subGap =  subFieldGap.get(i);
				}
				ArrayList<String> subFieldIap = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldIap.size();i++) {
					subIap =  subFieldIap.get(i);
				}
				ArrayList<String> subFieldSap = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldSap.size();i++) {
					subSap = subFieldSap.get(i);
				}
				ArrayList<String> subFieldTap = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldTap.size();i++) {
					subTap =  subFieldTap.get(i);
				}
				ArrayList<String> subFieldVap = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldVap.size();i++) {
					subVap = subFieldVap.get(i);
				}
				
				int didBefore = 0;
				String thisApNote = "";
				if (subCap  != null && subCap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Associated Country: " + subCap;
					didBefore++;
					associatedPlaces.add(fixCarrots((fixAmper(subCap))));
				}
				if (subFap  != null && subFap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Other Associated Place: " + subFap;
					didBefore++;
					associatedPlaces.add(fixCarrots(fixAmper(subFap)));
				}	
				if (subGap  != null && subGap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Place of Origin: " + subGap;
					didBefore++;
					associatedPlaces.add(fixCarrots(fixAmper(subGap)));
				}	
				if (subIap  != null && subIap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Relationship: " + subIap;
					didBefore++;
				}	
				if (subSap  != null && subSap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Start: " + subSap;
					didBefore++;
				}	
				if (subTap  != null && subTap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "End: " + subTap;
					didBefore++;
				}
				if (subVap  != null && subVap.length() > 0) {
					if (didBefore != 0) {
						thisApNote = thisApNote + ". ";
					}
					thisApNote = thisApNote + "Source: " + subVap;
					didBefore++;
				}
				if (didBefore != 0) {
					thisApNote = thisApNote + ".";
				}
				
				fiveHundNotes.add(fixCarrots(fixAmper(thisApNote)));
			
			} else if (fieldType.equals("388")) {
				// If Field 388 - Time Period of Creation - String creationEpoch = ""; // dct:created
				
				String thisTPC = "";
				ArrayList<String> subFielAtpc = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFielAtpc.size();i++) {
					thisTPC = subFielAtpc.get(i);
				}
				if (thisTPC  != null && thisTPC.length() > 0) {
					creationEpoch = thisTPC;
				}
			} else if (fieldType.matches("5\\d\\d")) {	
				// 5xx notes
				
				// get subfields
				String note = "";
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					note = subFieldA.get(i);
				}
				
				if (note != null && note.length() > 0) {
					String baseNote = fixCarrots(fixAmper(note));
					if (fieldType.equals("504")) {
						baseNote = "Bibliography: " + baseNote;
					} else if (fieldType.equals("505")) {
						baseNote = "Formatted Contents: " + baseNote;
					} else if (fieldType.equals("506")) {
						baseNote = "Restrictions on Access: " + baseNote;
					} else if (fieldType.equals("507")) {
						baseNote = "Scale Note for Graphic Material: " + baseNote;
					} else if (fieldType.equals("508")) {
						baseNote = "Creation/Production Credits: " + baseNote;
					} else if (fieldType.equals("510")) {
						baseNote = "Citation/References: " + baseNote;
					} else if (fieldType.equals("511")) {
						baseNote = "Participant or Performer: " + baseNote;
					} else if (fieldType.equals("513")) {
						baseNote = "Type of Report and Period Covered: " + baseNote;
					} else if (fieldType.equals("514")) {
						baseNote = "Data Quality Note: " + baseNote;
					} else if (fieldType.equals("515")) {
						baseNote = "Numbering Peculiarities: " + baseNote;
					} else if (fieldType.equals("516")) {
						baseNote = "Type of Computer File or Data: " + baseNote;
					} else if (fieldType.equals("518")) {
						baseNote = "Date/Time and Place of an Event: " + baseNote;
					} else if (fieldType.equals("520")) {
						baseNote = "Summary, etc.: " + baseNote;
					} else if (fieldType.equals("521")) {
						baseNote = "Target Audience: " + baseNote;
					} else if (fieldType.equals("522")) {
						baseNote = "Geographic Coverage: " + baseNote;
					} else if (fieldType.equals("524")) {
						baseNote = "Preferred Citation of Described Materials: " + baseNote;
					} else if (fieldType.equals("525")) {
						baseNote = "Supplement: " + baseNote;
					} else if (fieldType.equals("526")) {
						baseNote = "Study Program Information: " + baseNote;
					} else if (fieldType.equals("530")) {
						baseNote = "Additional Physical Form: " + baseNote;
					} else if (fieldType.equals("533")) {
						baseNote = "Reproduction: " + baseNote;
					} else if (fieldType.equals("534")) {
						baseNote = "Original Version: " + baseNote;
					} else if (fieldType.equals("535")) {
						baseNote = "Location of Originals/Duplicates: " + baseNote;
					} else if (fieldType.equals("536")) {
						baseNote = "Funding Information: " + baseNote;
					} else if (fieldType.equals("538")) {
						baseNote = "System Details: " + baseNote;
					} else if (fieldType.equals("540")) {
						baseNote = "Immediate Source of Acquisition: " + baseNote;
					} else if (fieldType.equals("542")) {
						baseNote = "Information Relating to Copyright Status: " + baseNote;
					} else if (fieldType.equals("544")) {
						baseNote = "Location of Other Archival Materials: " + baseNote;
					} else if (fieldType.equals("545")) {
						baseNote = "Biographical or Historical Data: " + baseNote;
					} else if (fieldType.equals("546")) {
						baseNote = "Language: " + baseNote;
					} else if (fieldType.equals("547")) {
						baseNote = "Former Title Complexity: " + baseNote;
					} else if (fieldType.equals("550")) {
						baseNote = "Issuing Body: " + baseNote;
					} else if (fieldType.equals("552")) {
						baseNote = "Entity and Attribute Information: " + baseNote;
					} else if (fieldType.equals("555")) {
						baseNote = "Cumulative Index/Finding Aids: " + baseNote;
					} else if (fieldType.equals("555")) {
						baseNote = "Information About Documentation: " + baseNote;
					} else if (fieldType.equals("561")) {
						baseNote = "Ownership and Custodial History: " + baseNote;
					} else if (fieldType.equals("562")) {
						baseNote = "Copy and Version Identification: " + baseNote;
					} else if (fieldType.equals("563")) {
						baseNote = "Binding Information: " + baseNote;
					} else if (fieldType.equals("565")) {
						baseNote = "Case File Characteristics: " + baseNote;
					} else if (fieldType.equals("567")) {
						baseNote = "Methodology: " + baseNote;
					} else if (fieldType.equals("580")) {
						baseNote = "Linking Entry Complexity: " + baseNote;
					} else if (fieldType.equals("581")) {
						baseNote = "Publications About Described Materials: " + baseNote;
					} else if (fieldType.equals("583")) {
						baseNote = "Action: " + baseNote;
					} else if (fieldType.equals("584")) {
						baseNote = "Accumulation and Frequency of Use: " + baseNote;
					} else if (fieldType.equals("585")) {
						baseNote = "Exhibitions: " + baseNote;
					} else if (fieldType.equals("586")) {
						baseNote = "Awards: " + baseNote;
					} else if (fieldType.equals("588")) {
						baseNote = "Source of Description: " + baseNote;
					}
					
					if (fieldType.matches("59\\d")) {	
						baseNote = "Local Note: " + baseNote;
					}
					
					if (baseNote != null && baseNote.length() > 0) {
						fiveHundNotes.add(baseNote);
					}
				}
			} else if (fieldType.equals("600")) {
				// do 600 (subject term - personal name)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("c");
				subFieldsToInclude.add("d");
				String thisSubjectString = getSubject(recordID, "600", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("610")) {
				// do 610 (subject term - corporate name)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "610", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("611")) {
				// do 611 (subject term - meeting)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				subFieldsToInclude.add("c");
				String thisSubjectString = getSubject(recordID, "611", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("630")) {
				// do 630 (subject term - Uniform Title)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "630", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} if (fieldType.equals("648")) {
				// do 648 (subject term - Chronological Term)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "648", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("650")) {
				// do 650 (subject term - Topical Term)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				subFieldsToInclude.add("c");
				subFieldsToInclude.add("d");
				String thisSubjectString = getSubject(recordID, "650", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("651")) {
				// do 651 (subject term - Geographic Name)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "651", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
					// coverage.add(fixPeriods(fixAmper(thisSubjectString)));
				}

			} else if (fieldType.equals("653")) {
				// do 653 (subject term - Uncontrolled)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "653", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("654")) {
				// do 654 (subject term - Faceted topical term)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				String thisSubjectString = getSubject(recordID, "654", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("656")) {
				// do 656 (subject term - Occupation)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "656", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("657")) {
				// do 657 (subject term - Function)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, "657", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("658")) {
				// do 658 (subject term - Curriculum Objective)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				String thisSubjectString = getSubject(recordID, "658", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("662")) {
				// do 662 (subject term - Hierarchical place name)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				subFieldsToInclude.add("c");
				subFieldsToInclude.add("d");
				subFieldsToInclude.add("f");
				subFieldsToInclude.add("g");
				subFieldsToInclude.add("h");
				String thisSubjectString = getSubject(recordID, "662", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}
			} else if (fieldType.equals("751")) {
				// dc:coverage
				
				String workingValue = "";
				// get raw value
				String rawValue = sqlObj.getFieldByID(fieldID);;
				// get subfields
				String subA = "";
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					subA = subFieldA.get(i);
				}
				if (subA != null && subA.length() > 0) {
					workingValue = subA;
				} else if (rawValue != null && rawValue.length() > 0) {
					workingValue = rawValue;
				}
				coverage.add(fixCarrots(fixPeriods(fixAmper(workingValue))));
			} else if (fieldType.equals("752")) {
				// do 752 (subject term - Hierarchical Geographic Name) - dc:coverage

				// get subfields
				String subA = "";
				String subB = "";
				String subC = "";
				String subD = "";
				String subF = "";
				String subG = "";
				String subH = "";
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					subA = subFieldA.get(i);
				}
				ArrayList<String> subFieldB = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (i=0;i < subFieldB.size();i++) {
					subB = subFieldB.get(i);
				}
				ArrayList<String> subFieldC = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (i=0;i < subFieldC.size();i++) {
					subC = subFieldC.get(i);
				}	
				ArrayList<String> subFieldD = sqlObj.selectSubFieldValuesByID(fieldID, "d");
				for (i=0;i < subFieldD.size();i++) {
					subD = subFieldD.get(i);
				}
				ArrayList<String> subFieldF = sqlObj.selectSubFieldValuesByID(fieldID, "f");
				for (i=0;i < subFieldF.size();i++) {
					subF = subFieldF.get(i);
				}
				ArrayList<String> subFieldG = sqlObj.selectSubFieldValuesByID(fieldID, "g");
				for (i=0;i < subFieldG.size();i++) {
					subG = subFieldG.get(i);
				}
				ArrayList<String> subFieldH = sqlObj.selectSubFieldValuesByID(fieldID, "h");
				for (i=0;i < subFieldH.size();i++) {
					subH = subFieldH.get(i);
				}
				
				if (subA != null && subA.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subA))));
				}
				if (subB != null && subB.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subB))));
				}
				if (subC != null && subC.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subC))));
				}
				if (subD != null && subD.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subD))));
				}
				if (subF != null && subF.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subF))));
				}
				if (subG != null && subG.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subG))));
				}
				if (subH != null && subH.length() > 0) {
					coverage.add(fixCarrots(fixPeriods(fixAmper(subH))));
				}
			}
			
			else if (fieldType.equals("856")) {
				// digital surrogates
				surrogateSub = sqlObj.selectSubFieldValuesByID(fieldID, "u");
				
			}

		}
		
		/*
		 * This is the end of the read-in part of the code.  The
		 * part you'll be working on most is below here, where you
		 * control the format of the output.  This is where
		 * you'll have to modify things so that it writes out to
		 * the Jena specification.
		 */

		// construct header - you may need to add stuff here depending
		// on the Jena RDF specification
		rdfHeader = "<rdf:RDF xmlns:gl=\"http://bl.uk.org/schema#\">\n";
		
		// construct the rdf:about.  You may need to change this 
		// to work with the Jena RDF syntax.  
		rdfAbout = "    <rdf:Description rdf:about=\"" + uniqueRI + "\">\n";
		
		// construct footer - you may need to add stuff here depending
		// on the Jena RDF specification
		rdfFooter = rdfFooter + "     </rdf:Description>\n</rdf:RDF>";
		
		
		int itn = 0; // instantiate increment variable used for lists
		rdfString = rdfString + "        <dct:title>" + finalTitle + "</dct:title>\n";
		
		for (int iat=0;iat < authorArray.size();iat++) {
			ArrayList<String> thisCont = authorArray.get(iat);
			rdfString = rdfString + formatContributor(thisCont.get(0), thisCont.get(1));
		}
		
		if (finalDate != null && finalDate.length() > 0) {
			rdfString = rdfString + "        <dc:date>\n             <collex:date>\n";
			rdfString = rdfString + "                  <rdfs:label>" + finalDateString + "</rdfs:label>\n";
			rdfString = rdfString + "                  <rdfs:value>" + finalDateString + "</rdfs:value>\n";
			rdfString = rdfString + "             </collex:date>\n        </dc:date>\n";
			
		}
		
		//String prodInfo = ""; // dct:publisher
		if (prodInfo != null && prodInfo.length() > 0) {
			rdfString = rdfString + "        <dct:publisher>" + prodInfo + "</dct:publisher>\n";
		}
		
		//ArrayList<String> contentCarrierTypes = new ArrayList<String>(); // dct:type
		if (contentCarrierTypes != null && contentCarrierTypes.size() > 0) {
			for (itn=0;itn < contentCarrierTypes.size();itn++) {
				rdfString = rdfString + "        <dct:type>" + contentCarrierTypes.get(itn) + "</dct:type>\n";
			}
		}

		//String creationEpoch = ""; // dct:created
		if (creationEpoch != null && creationEpoch.length() > 0) {
			rdfString = rdfString + "        <dct:created>" + creationEpoch + "</dct:created>\n";
		}

		// ArrayList<String> languageCode = new ArrayList<String>(); //   dc:language
		if (languageCode != null && languageCode.size() > 0) {
			for (itn=0;itn < languageCode.size();itn++) {
				rdfString = rdfString + "        <dc:language>" + languageCode.get(itn) + "</dc:language>\n";
			}
		}
		
		// ArrayList<String> associatedPlaces = new ArrayList<String>(); //   dc:coverage	
		if (associatedPlaces != null && associatedPlaces.size() > 0) {
			for (itn=0;itn < associatedPlaces.size();itn++) {
				rdfString = rdfString + "        <dc:coverage>" + associatedPlaces.get(itn) + "</dc:coverage>\n";
			}
		}
		// end new fields
		
		if (coverage != null && coverage.size() > 0) {
			for (int ic=0;ic < coverage.size();ic++) {
				rdfString = rdfString + "        <dc:coverage>" + coverage.get(ic) + "</dc:coverage>\n";
			}
		}

		if (subjectTerms != null && subjectTerms.size() > 0) {
			for (int ist=0;ist < subjectTerms.size();ist++) {
				rdfString = rdfString + fixPeriods(subjectTerms.get(ist));
			}
		}
		if (fiveHundTenNotes != null && fiveHundTenNotes.size() > 0) {
			for (int isn=0;isn < fiveHundTenNotes.size();isn++) {
				rdfString = rdfString + "        <dct:isReferencedBy>" + fiveHundTenNotes.get(isn) + "</dct:isReferencedBy>\n";
			}
		}
		
		if (fiveHundNotes != null && fiveHundNotes.size() > 0) {
			for (int isn=0;isn < fiveHundNotes.size();isn++) {
				rdfString = rdfString + "        <dct:description>" + fiveHundNotes.get(isn) + "</dct:description>\n";
			}
		}

		// add digital surrogates
		for (int ids=0;ids < surrogateSub.size();ids++) {
			String digSur = surrogateSub.get(ids);
			if (digSur != null && digSur.length() > 0) {
				rdfString = rdfString + "        <scm:url>" + fixCarrots(fixAmper(digSur)) + "</scm:url>\n";
			}
		}
		
		String bibRDF = rdfHeader + rdfAbout + rdfString + rdfFooter;

		/*
		 * As a debug function I've currently commented out the 
		 * code that writes the actual file to disk, and I'm just
		 * printing the rdf output to the console for viewing.  Once
		 * it's working you'll want to delete the system.write and 
		 * uncomment the codet to save to file.
		 */
		
		System.out.println(bibRDF);
		
		/*
		
		try {
			PrintWriter bibWriter = new PrintWriter( configObj.writeDir + "/bib_" + itemID + ".rdf", "UTF-8");
			bibWriter.print(bibRDF);
			bibWriter.close();
			// mark the record as exported
			sqlObj.updateExported(recordID);
		} catch (FileNotFoundException e) {
			System.out.println("Error exporting record " + recordID + " holding item " + itemID);
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			System.out.println("Error exporting record " + recordID + " holding item " + itemID);
			e.printStackTrace();
		}
		
		*/
		
		
		// System.out.println(bibRDF);
		System.out.println("Processed bib record " + recordID + " item " + itemID);
		
		return ret;
	}
	
	// create an RDF string for a resource
	public String getSubject(int recordID, String field, ArrayList<String> subFields, String separator) {
		String ret = "";
		String workingString = "";
		
		// get raw value and field id
		String rawValue = sqlObj.getFieldByNumber(recordID, field);
		ArrayList<HashMap<String,String>> RetInfo = sqlObj.getFieldInfo(recordID, field);
		String FieldID = "";
		for (int sfa=0;sfa < RetInfo.size();sfa++) {
			HashMap<String,String> thisHash = RetInfo.get(sfa); 
			FieldID = thisHash.get("id");
		}
		
		if (FieldID != null && FieldID.length() > 0) {
			
			int FieldIdAsInt = Integer.parseInt(FieldID);
			
			for (int ia=0;ia < subFields.size();ia++) {
				String localWorking = "";
				ArrayList<String> subFieldIt = sqlObj.selectSubFieldValuesByID(FieldIdAsInt, subFields.get(ia));
				for (int iab=0;iab < subFieldIt.size();iab++) {
					localWorking = subFieldIt.get(iab);
				}
				if (localWorking != null && localWorking.length() > 0) {
					if (workingString != null && workingString.length() > 0) {
						workingString = workingString + separator;
					}
					workingString = workingString + localWorking;
				}
			}			
			
		}
		
		if ((workingString != null) && (workingString.length() == 0) && (rawValue != null) && (rawValue.length() > 0)) {
			workingString = rawValue;
		}
		
		if (workingString != null && workingString.length() > 0) {
			
			
			// fix collapseString so that it is an id from a subjects table
			// in the db
			
			//String collapseString = workingString.replaceAll("[^\\s]", "");
			String estcTermId = sqlObj.selectSubjectID(workingString);
			
			ret = ret + "        <dct:subject>\n";
			ret = ret + "                <scm:about rdfs:resource=\"http://estc.bl.uk/subjects/" + estcTermId + "\">\n";
			ret = ret + "                     <rdfs:label>" + fixCarrots(fixPeriods(fixAmper(workingString))) + "</rdfs:label>\n";
			ret = ret + "                </scm:about>\n";
			ret = ret + "        </dct:subject>\n";
		}

		return ret;
	}
	
	public String formatContributor(String relator, String value) {
		String ret="";
		if (relator != null && relator.length() > 0) {
			if (!relator.contains(" ")) {
				if (value != null && value.length() > 0) {
					// get unique ESTC agent ID
					String estcAgents = sqlObj.selectAgentID(value);
					if (estcAgents != null && estcAgents.length() > 0) {
						ret = "        <dct:creator>\n";
						String cleanedRelator = fixPeriods(relator);
						ret = ret + "            <scm:contributor rdfs:resource=\"http://estcbluk/agents/" + estcAgents + "\">\n";
						ret = ret + "                <role:" + cleanedRelator + ">" + value + "</role:" + cleanedRelator + ">\n";
						ret = ret + "            </scm:contributor>\n";
						ret = ret + "        </dct:creator>\n";
					}
				}	
			}
		}
		return ret;
	}
	
	public String removeFinalComma(String str) {
	    String ret = str.substring(0, str.length()-1);
	    return ret;
	}
	
	public String getSubfieldValue(ArrayList<HashMap<String,String>> thisRow, String subfield) {
		String retVal = null;
		int isfr = 0;
		while (isfr < thisRow.size()) {
			HashMap<String,String> holdingRecordSubFieldsRes = thisRow.get(isfr);
			String subfieldvalue = holdingRecordSubFieldsRes.get("subfield");
			if (subfieldvalue.equals(subfield)) {
				retVal = holdingRecordSubFieldsRes.get("value");
			}
			isfr++;
		}
		
		return retVal;
		
	}
	public ArrayList<String> getSubfieldValueList(ArrayList<HashMap<String,String>> thisRow, String subfield) {
		ArrayList<String> retVals = new ArrayList<String>();
		int isfr = 0;
		while (isfr < thisRow.size()) {
			HashMap<String,String> holdingRecordSubFieldsRes = thisRow.get(isfr);
			String subfieldvalue = holdingRecordSubFieldsRes.get("subfield");
			if (subfieldvalue.equals(subfield)) {
				retVals.add(fixAmper(holdingRecordSubFieldsRes.get("value")));
			}
			isfr++;
		}
		
		return retVals;
		
	}
	
	public static String fixAmper(String str) {
		String retStr = "";
		if ( str != null && str.length() > 0) {
			retStr = str.replace("&", "&amp;");
		}
		return retStr;
	}
	
	public static String fixPeriods(String str) {
		String retStr = "";
		if ( str != null && str.length() > 0) {
			retStr = str.replace(".", "");
		}
		return retStr;
	}
	
	public static String fixCarrots(String str) {
		String retStr = "";
		if ( str != null && str.length() > 0) {
			retStr = str.replaceAll("<", "&lt;");
			retStr = retStr.replaceAll(">", "&gt;");
		}
		return retStr;
	}

}