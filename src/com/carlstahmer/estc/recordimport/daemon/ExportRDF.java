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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cstahmer
 * 
 * <p>Class for exporting a records from the SQL DB
 * to an RDF file.</p>
 */

public class ExportRDF {
	
	Conf configObj;
	SqlModel sqlObj;
	Logger logger;
	String rdfHeader;
	String rdfAbout;
	String rdfString;
	String rdfFooter;

	public ExportRDF(Conf config, SqlModel sqlModObj) {
		configObj = config;
		sqlObj = sqlModObj;
		logger = new Logger(config);
	}
	
	public boolean makeRDFAllBibs(String domainURI) {
		boolean success = false;
		
		// loop through all bib records and send to makeRDF for each
		
		ArrayList<Integer> recordsQueue = sqlObj.selectUnExportedBibs();
		for (int i=0;i < recordsQueue.size();i++) {
			int workingRecordID = recordsQueue.get(i);
			makeRDF(workingRecordID, domainURI);
		}
		
		return success;
	}
	
	// create an RDF string for a resource
	public boolean makeRDF(int recordID, String domainURI) {
		boolean ret = false;
		
		// get the record type holding/bib
		int recordType = sqlObj.getRecordType(recordID);
		
		// set the id for the item.  ESTCID for bibs and record IDs for holdings
		String itemID;
		if (recordType == 1) {
			itemID = sqlObj.selectRecordControlId(recordID);
		} else {
			itemID = String.valueOf(recordID);
		}
		
		System.out.println("Processing record " + recordID + " item " + itemID);

		// get the library code for the record
		ArrayList<HashMap<String,String>> tableResults = sqlObj.selectFileInfoById(sqlObj.selectRecordFileId(recordID));
		HashMap<String,String> recordInfoRecord = tableResults.get(0);
		String instCode = recordInfoRecord.get("institution_code");
	
		//String itemID = "use an ESTC ID if this is a bib record, otherwise use the record ID";
		
		// make header
		rdfHeader = "<rdf:RDF xmlns:gl=\"http://bl.uk.org/schema#\"\n";
		rdfHeader = rdfHeader + "    xmlns:bf=\"http://bibframe.org/vocab/\"\n";
		rdfHeader = rdfHeader + "    xmlns:collex=\"http://www.collex.org/schema#\"\n";
		rdfHeader = rdfHeader + "    xmlns:dc=\"http://purl.org/dc/elements/1.1/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:dct=\"http://purl.org/dc/terms/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:estc=\"http://estc21.ucr.edu/schema#\"\n";
		rdfHeader = rdfHeader + "    xmlns:foaf=\"http://xmlns.com/foaf/0.1/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:geo=\"http://www.w3.org/2003/01/geo/wgs84_pos/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:isbdu=\"http://iflastandards.info/ns/isbd/unc/elements/\"\n";
		rdfHeader = rdfHeader + "    xmlns:rdau=\"http://rdaregistry.info/Elements/u/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n";
		rdfHeader = rdfHeader + "    xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:reg=\"http://metadataregistry.org/uri/profile/RegAp/#\"\n";
		rdfHeader = rdfHeader + "    xmlns:relators=\"http://id.loc.gov/vocabulary/relators/\"\n";
		rdfHeader = rdfHeader + "    xmlns:role=\"http://www.loc.gov/loc.terms/relators/\"\n";
		rdfHeader = rdfHeader + "    xmlns:scm=\"http://schema.org/\"\n";
		rdfHeader = rdfHeader + "    xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" >\n";
		
		// make footer
		rdfFooter = "    </estc:estc>\n";
		rdfFooter = rdfFooter + "</rdf:RDF>";
		
		// make unique identifier for about and parent associations
		String uniqueRI = "http://" + domainURI + "/" + itemID;
		String parentAssoc = "        <bf:instanceOf>" + uniqueRI + "</bf:instanceOf>\n";
		
		// make the record itself
		rdfAbout = "    <estc:estc rdf:about=\"" + uniqueRI + "\">\n";
		rdfString = "        <collex:federation>ESTC</collex:federation>\n";
		rdfString = rdfString + "        <collex:archive>" + instCode + "</collex:archive>\n";
		
		// setup needed output variables
		String finalTitle = "Utitled or Title not Known";
		String finalDate = "";
		String finalDateString = "";
		ArrayList<String> subjectTerms = new ArrayList<String>();
		String genre = "";
		ArrayList<String> coverage = new ArrayList<String>();
		ArrayList<ArrayList<String>> authorArray = new ArrayList<ArrayList<String>>();
		ArrayList<String> fiveHundNotes = new ArrayList<String>();
		ArrayList<String> fiveHundTenNotes = new ArrayList<String>();
		ArrayList<String> surrogateSub = new ArrayList<String>();
		//String estcID = "";
		
		
		// newly added fields
		String abrvTitle = ""; // rdau:abbreviatedTitle
		String uniformTitleTwoForty = ""; // rdau:titleOfResource
		String seriesUniformTitle = ""; // rdau:titleProperOfSeries
		String variantTitle = ""; // rdau:variantTitle
		String formerTitle = ""; // rdau:earlierTitleProper
		String editionStatement = ""; // rdau:editionStatement
		String prodInfo = ""; // dct:publisher
		String formerPubFreq = ""; // rdau:noteOnFrequency
		String physDesc = ""; // dct:format
		String creationEpoch = ""; // dct:created
		String estcThumbnail = ""; // collex:thumbnail rdf:resource=""
		String dcRights = ""; // dct:rights
		String imprintString = ""; // dct:publisher
		// String dublinPublisher = ""; // role publisher dct:publisher $b
		ArrayList<String> seriesStatment = new ArrayList<String>(); //   isbdu:P1041  hasNoteOnSeriesAndMultipartMonographicResources
		ArrayList<String> uniformTitle = new ArrayList<String>(); //   rdau:titleOfResource
		ArrayList<String> languageCode = new ArrayList<String>(); //   dc:language
		ArrayList<String> associatedPlaces = new ArrayList<String>(); //   dc:coverage		
		ArrayList<String> contentCarrierTypes = new ArrayList<String>(); //   dct:type	
		ArrayList<String> eraStrings = new ArrayList<String>(); // dct:date
		
		
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
			} else if (fieldType.equals("130") || fieldType.equals("730") || fieldType.equals("240")) {
				// if 130 & 730 & 240 - Uniform Title - uniformTitle - rdau:titleOfResource
				ArrayList<String> subFieldAut = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAut.size();i++) {
					uniformTitle.add(fixCarrots(fixAmper(subFieldAut.get(i))));
				}
			} else if (fieldType.equals("210")) {
				// if 210  abbreviated title - rdau:abbreviatedTitle
				
				// get raw value
				String rawAbrevTitleValue = sqlObj.getFieldByID(fieldID);;
				// get subfields
				String abrevTitleA = "";
				ArrayList<String> subFieldA = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldA.size();i++) {
					abrevTitleA = fixCarrots(fixAmper(subFieldA.get(i)));
				}
				if (abrevTitleA != null && abrevTitleA.length() > 0) {
					abrvTitle = abrevTitleA;
				} else if (rawAbrevTitleValue != null && rawAbrevTitleValue.length() > 0) {
					finalTitle = fixCarrots(fixAmper(rawAbrevTitleValue));
				}
			} else if (fieldType.equals("243")) {
				// if 243 - Collective Uniform Title - String seriesUniformTitle = ""; // rdau:titleProperOfSeries
				
				ArrayList<String> subFieldtps = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldtps.size();i++) {
					seriesUniformTitle = fixCarrots(fixAmper(subFieldtps.get(i)));
				}
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

			} else if (fieldType.equals("246")) {
				// if 246 - Varying Form of Title - String variantTitle = ""; // rdau:variantTitle
				
				// get subfields
				String varTitleA = "";
				String varTitleB = "";
				String finalVTitle = "";
				ArrayList<String> subFieldAv = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAv.size();i++) {
					varTitleA = fixCarrots(fixAmper(subFieldAv.get(i)));
				}
				ArrayList<String> subFielBv = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFielBv.size();i++) {
					varTitleB = fixCarrots(fixAmper(subFielBv.get(i)));
				}
				if (varTitleA != null && varTitleA.length() > 0) {
					finalVTitle = varTitleA;
					if (varTitleB != null && varTitleB.length() > 0) {
						finalVTitle = finalVTitle + " - " + varTitleB;
					}
					variantTitle = fixCarrots(fixAmper(finalVTitle));
				}
			} else if (fieldType.equals("247")) {
				// if 247 - Former Title - String formerTitle = ""; // rdau:earlierTitleProper
				
				// get subfields
				String fTitleA = "";
				String fTitleB = "";
				String finalFTitle = "";
				ArrayList<String> subFieldAf = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAf.size();i++) {
					fTitleA = fixCarrots(fixAmper(subFieldAf.get(i)));
				}
				ArrayList<String> subFielBf = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFielBf.size();i++) {
					fTitleB = fixCarrots(fixAmper(subFielBf.get(i)));
				}
				if (fTitleA != null && fTitleA.length() > 0) {
					finalFTitle = fTitleA;
					if (fTitleB != null && fTitleB.length() > 0) {
						finalFTitle = finalFTitle + " - " + fTitleB;
					}
					formerTitle = fixCarrots(fixAmper(finalFTitle));
				}
			} else if (fieldType.equals("250")) {
				// if FIELD 250 - Edition Statement - String editionStatement = ""; // rdau:editionStatement
				
				ArrayList<String> subFieldAes = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int iales=0;iales < subFieldAes.size();iales++) {
					editionStatement = fixCarrots(fixAmper(subFieldAes.get(iales)));
				}
				
			} else if (fieldType.equals("260")) {
				// if FIELD 260 - Imprint - String imprintString = ""; // bibo:issuer + role publisher dct:publisher $b
				String impSubA = "";
				String impSubB = "";
				String impSubC = "";
				ArrayList<String> subFieldAimp = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int imp=0;imp < subFieldAimp.size();imp++) {
					impSubA = fixAmper(subFieldAimp.get(imp));
				}
				ArrayList<String> subFieldBimp = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (int impb=0;impb < subFieldBimp.size();impb++) {
					impSubB = fixAmper(subFieldBimp.get(impb));
				}
				ArrayList<String> subFieldCimp = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (int impc=0;impc < subFieldCimp.size();impc++) {
					impSubC = fixAmper(subFieldCimp.get(impc));
				}
				
				int beenHereBefore = 0;
				int beenInPlaceBefore = 0;
				String impressionString = "";
				if (impSubA  != null && impSubA.length() > 0) {
					if (beenHereBefore > 0) {
						impressionString = impressionString + " ";
					}
					impressionString = impressionString + impSubA;
					beenHereBefore++;
					beenInPlaceBefore++;
				}
				if (impSubB  != null && impSubB.length() > 0) {
					if (beenHereBefore > 0) {
						impressionString = impressionString + " ";
					}
					impressionString = impressionString + impSubB;
					beenHereBefore++;
					// dublinPublisher = fixCarrots(fixAmper(impSubB));
				}
				if (impSubC  != null && impSubC.length() > 0) {
					if (beenHereBefore > 0) {
						if (beenInPlaceBefore > 0) {
							impressionString = impressionString + " ";
						} else {
							impressionString = impressionString + " ";
						}
					}
					impressionString = impressionString + impSubC;
					beenHereBefore++;
				}
				
				
				
				if (impressionString  != null && impressionString.length() > 0) {
					imprintString = fixCarrots(fixAmper(impressionString));
				}	
			} else if (fieldType.equals("264")) {
				// if FIELD 264 - Production info (like imprint for manufactured goods) - String prodInfo = ""; // dct:publisher
				
				ArrayList<String> retValman = new ArrayList<String>();
				ArrayList<String> subFieldAman = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int impman=0;impman < subFieldAman.size();impman++) {
					prodInfo = fixCarrots(fixAmper(subFieldAman.get(impman)));
				}
				ArrayList<String> subFieldBman = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int impmanb=0;impmanb < subFieldBman.size();impmanb++) {
					String manSubB = "";
					manSubB = fixCarrots(fixAmper(subFieldBman.get(impmanb)));
					if (manSubB  != null && manSubB.length() > 0) {
						retValman.add("CRE");
						retValman.add(manSubB);
						authorArray.add(retValman);
					}
					
				}			
			} else if (fieldType.equals("300")) {
				// IF Field 300 - Physical Description - String physDesc = ""; // dct:format
				
				int valueBefore = 0;
				String thisPDNote = "";
				// get subfields
				String subApd = "";
				String subBpd = "";
				String subCpd = "";
				String subEpd = "";
				String subFpd = "";
				String subGpd = "";
				
				ArrayList<String> subFieldApd = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldApd.size();i++) {
					subApd = subFieldApd.get(i);
				}
				ArrayList<String> subFieldBpd = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (i=0;i < subFieldBpd.size();i++) {
					subBpd =  subFieldBpd.get(i);
				}
				ArrayList<String> subFieldCpd = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (i=0;i < subFieldCpd.size();i++) {
					subCpd =  subFieldCpd.get(i);
				}
				ArrayList<String> subFieldEpd = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (i=0;i < subFieldEpd.size();i++) {
					subEpd =  subFieldEpd.get(i);
				}
				ArrayList<String> subFieldFpd = sqlObj.selectSubFieldValuesByID(fieldID, "f");
				for (i=0;i < subFieldFpd.size();i++) {
					subFpd =  subFieldFpd.get(i);
				}
				ArrayList<String> subFieldGpd = sqlObj.selectSubFieldValuesByID(fieldID, "g");
				for (i=0;i < subFieldGpd.size();i++) {
					subGpd =  subFieldGpd.get(i);
				}
				
				
				if (subApd  != null && subApd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + subApd;
					valueBefore++;
				}
				if (subBpd  != null && subBpd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + subBpd;
					valueBefore++;
				}
				if (subCpd  != null && subCpd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + "Dimensions: " + subCpd;
					valueBefore++;
				}
				if (subEpd  != null && subEpd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + "Accompanying material: " + subEpd;
					valueBefore++;
				}	
				if (subFpd  != null && subFpd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + "Type of unit: " + subFpd;
					valueBefore++;
				}
				if (subGpd  != null && subGpd.length() > 0) {
					if (valueBefore != 0) {
						thisPDNote = thisPDNote + ". ";
					}
					thisPDNote = thisPDNote + "Size of unit: " + subGpd;
					valueBefore++;
				}				
				if (valueBefore != 0) {
					thisPDNote = thisPDNote + ".";
				}

				physDesc = fixCarrots(fixAmper(thisPDNote));
			} else if (fieldType.equals("321")) {
				// if FIELD 321 - Former Publication Frequency - String formerPubFreq = ""; // rdau:noteOnFrequency
				
				ArrayList<String> subFieldAps = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAps.size();i++) {
					formerPubFreq = fixCarrots(fixAmper(subFieldAps.get(i)));
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
			} else if (fieldType.equals("490")) {
				// IF Field 490 - Series Statement -- Array<String> seriesStatment   isbdu:P1041  hasNoteOnSeriesAndMultipartMonographicResources
				
				String subAss = "";
				String subLss = "";
				String subVss = "";
				String subXss = "";

				ArrayList<String> subFieldAss = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (i=0;i < subFieldAss.size();i++) {
					subAss = subFieldAss.get(i);
				}
				ArrayList<String> subFieldLss = sqlObj.selectSubFieldValuesByID(fieldID, "l");
				for (i=0;i < subFieldLss.size();i++) {
					subLss =  subFieldLss.get(i);
				}
				ArrayList<String> subFieldVss = sqlObj.selectSubFieldValuesByID(fieldID, "v");
				for (i=0;i < subFieldVss.size();i++) {
					subVss =  subFieldVss.get(i);
				}
				ArrayList<String> subFieldXss = sqlObj.selectSubFieldValuesByID(fieldID, "x");
				for (i=0;i < subFieldXss.size();i++) {
					subXss =  subFieldXss.get(i);
				}
				
				int didBeforeSs = 0;
				String thisSsNote = "";
				if (subAss  != null && subAss.length() > 0) {
					if (didBeforeSs != 0) {
						thisSsNote = thisSsNote + ". ";
					}
					thisSsNote = thisSsNote + "Series Statement: " + subAss;
					didBeforeSs++;
				}
				if (subLss  != null && subLss.length() > 0) {
					if (didBeforeSs != 0) {
						thisSsNote = thisSsNote + ". ";
					}
					thisSsNote = thisSsNote + "Library of Congress Call Number: " + subLss;
					didBeforeSs++;
				}
				if (subVss  != null && subVss.length() > 0) {
					if (didBeforeSs != 0) {
						thisSsNote = thisSsNote + ". ";
					}
					thisSsNote = thisSsNote + "Volume/Sequence Number: " + subVss;
					didBeforeSs++;
				}
				
				if (subXss  != null && subXss.length() > 0) {
					if (didBeforeSs != 0) {
						thisSsNote = thisSsNote + ". ";
					}
					thisSsNote = thisSsNote + "International Standard Serial Number: " + subXss;
					didBeforeSs++;
				}
				
				
				if (thisSsNote  != null && thisSsNote.length() > 0) {
					seriesStatment.add(fixCarrots(fixAmper(thisSsNote)));
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
					
					if (fieldType.equals("540")) {
						dcRights = baseNote;
						baseNote = "";
					}
					
					if (baseNote != null && baseNote.length() > 0) {
						fiveHundNotes.add(baseNote);
					}
				}
			} else if (fieldType.equals("600") || fieldType.equals("610") || fieldType.equals("611")) {
				// do 600 (subject term - personal name)
				
				
				String sixhundySubA = "";
				String sixhundySubB = "";
				String sixhundySubC = "";
				String sixhundySubD = "";
				String sixhundySubE = "";
				String sixhundySubF = "";
				String sixhundySubG = "";
				String sixhundySubH = "";
				String sixhundySubI = "";
				String sixhundySubJ = "";
				String sixhundySubK = "";
				String sixhundySubL = "";
				String sixhundySubM = "";
				String sixhundySubN = "";
				String sixhundySubO = "";
				String sixhundySubP = "";
				String sixhundySubQ = "";
				String sixhundySubR = "";
				String sixhundySubS = "";
				String sixhundySubT = "";
				String sixhundySubU = "";
				String sixhundySubV = "";
				String sixhundySubW = "";
				String sixhundySubX = "";
				String sixhundySubY = "";
				String sixhundySubZ = "";
				
				ArrayList<String> subFieldAsx = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int imp=0;imp < subFieldAsx.size();imp++) {
					sixhundySubA = fixAmper(subFieldAsx.get(imp));
				}
				ArrayList<String> subFieldBsx = sqlObj.selectSubFieldValuesByID(fieldID, "b");
				for (int impb=0;impb < subFieldBsx.size();impb++) {
					sixhundySubB = fixAmper(subFieldBsx.get(impb));
				}
				ArrayList<String> subFieldCxs = sqlObj.selectSubFieldValuesByID(fieldID, "c");
				for (int impc=0;impc < subFieldCxs.size();impc++) {
					sixhundySubC = fixAmper(subFieldCxs.get(impc));
				}
				ArrayList<String> subFieldDxs = sqlObj.selectSubFieldValuesByID(fieldID, "d");
				for (int imp=0;imp < subFieldDxs.size();imp++) {
					sixhundySubD = fixAmper(subFieldDxs.get(imp));
				}
				ArrayList<String> subFieldEsx = sqlObj.selectSubFieldValuesByID(fieldID, "e");
				for (int impb=0;impb < subFieldEsx.size();impb++) {
					sixhundySubE = fixAmper(subFieldEsx.get(impb));
				}
				ArrayList<String> subFieldFxs = sqlObj.selectSubFieldValuesByID(fieldID, "f");
				for (int impc=0;impc < subFieldFxs.size();impc++) {
					sixhundySubF = fixAmper(subFieldFxs.get(impc));
				}				
				ArrayList<String> subFieldGxs = sqlObj.selectSubFieldValuesByID(fieldID, "g");
				for (int imp=0;imp < subFieldGxs.size();imp++) {
					sixhundySubG = fixAmper(subFieldGxs.get(imp));
				}
				ArrayList<String> subFieldHxs = sqlObj.selectSubFieldValuesByID(fieldID, "h");
				for (int impb=0;impb < subFieldHxs.size();impb++) {
					sixhundySubH = fixAmper(subFieldHxs.get(impb));
				}
				ArrayList<String> subFieldIxs = sqlObj.selectSubFieldValuesByID(fieldID, "i");
				for (int impc=0;impc < subFieldIxs.size();impc++) {
					sixhundySubI = fixAmper(subFieldIxs.get(impc));
				}				
				ArrayList<String> subFieldJxs = sqlObj.selectSubFieldValuesByID(fieldID, "j");
				for (int imp=0;imp < subFieldJxs.size();imp++) {
					sixhundySubJ = fixAmper(subFieldJxs.get(imp));
				}
				ArrayList<String> subFieldKxs = sqlObj.selectSubFieldValuesByID(fieldID, "k");
				for (int impb=0;impb < subFieldKxs.size();impb++) {
					sixhundySubK = fixAmper(subFieldKxs.get(impb));
				}
				ArrayList<String> subFieldLxs = sqlObj.selectSubFieldValuesByID(fieldID, "l");
				for (int impc=0;impc < subFieldLxs.size();impc++) {
					sixhundySubL = fixAmper(subFieldLxs.get(impc));
				}				
				ArrayList<String> subFieldMxs = sqlObj.selectSubFieldValuesByID(fieldID, "m");
				for (int imp=0;imp < subFieldMxs.size();imp++) {
					sixhundySubM = fixAmper(subFieldMxs.get(imp));
				}
				ArrayList<String> subFieldNxs = sqlObj.selectSubFieldValuesByID(fieldID, "n");
				for (int impb=0;impb < subFieldNxs.size();impb++) {
					sixhundySubN = fixAmper(subFieldNxs.get(impb));
				}
				ArrayList<String> subFieldOxs = sqlObj.selectSubFieldValuesByID(fieldID, "o");
				for (int impc=0;impc < subFieldOxs.size();impc++) {
					sixhundySubO = fixAmper(subFieldOxs.get(impc));
				}				
				ArrayList<String> subFieldPxs = sqlObj.selectSubFieldValuesByID(fieldID, "p");
				for (int imp=0;imp < subFieldPxs.size();imp++) {
					sixhundySubP = fixAmper(subFieldPxs.get(imp));
				}
				ArrayList<String> subFieldQxs = sqlObj.selectSubFieldValuesByID(fieldID, "q");
				for (int impb=0;impb < subFieldQxs.size();impb++) {
					sixhundySubQ = fixAmper(subFieldQxs.get(impb));
				}
				ArrayList<String> subFieldRxs = sqlObj.selectSubFieldValuesByID(fieldID, "r");
				for (int impc=0;impc < subFieldRxs.size();impc++) {
					sixhundySubR = fixAmper(subFieldRxs.get(impc));
				}				
				ArrayList<String> subFieldSxs = sqlObj.selectSubFieldValuesByID(fieldID, "s");
				for (int imp=0;imp < subFieldSxs.size();imp++) {
					sixhundySubS = fixAmper(subFieldSxs.get(imp));
				}
				ArrayList<String> subFieldTxs = sqlObj.selectSubFieldValuesByID(fieldID, "t");
				for (int impb=0;impb < subFieldTxs.size();impb++) {
					sixhundySubT = fixAmper(subFieldTxs.get(impb));
				}
				ArrayList<String> subFieldUxs = sqlObj.selectSubFieldValuesByID(fieldID, "u");
				for (int impc=0;impc < subFieldUxs.size();impc++) {
					sixhundySubU = fixAmper(subFieldUxs.get(impc));
				}				
				ArrayList<String> subFieldVxs = sqlObj.selectSubFieldValuesByID(fieldID, "v");
				for (int imp=0;imp < subFieldVxs.size();imp++) {
					sixhundySubV = fixAmper(subFieldVxs.get(imp));
				}
				ArrayList<String> subFieldWxs = sqlObj.selectSubFieldValuesByID(fieldID, "w");
				for (int impb=0;impb < subFieldWxs.size();impb++) {
					sixhundySubW = fixAmper(subFieldWxs.get(impb));
				}
				ArrayList<String> subFieldXxs = sqlObj.selectSubFieldValuesByID(fieldID, "x");
				for (int impc=0;impc < subFieldXxs.size();impc++) {
					sixhundySubX = fixAmper(subFieldXxs.get(impc));
				}				
				ArrayList<String> subFieldYxs = sqlObj.selectSubFieldValuesByID(fieldID, "y");
				for (int imp=0;imp < subFieldYxs.size();imp++) {
					sixhundySubY = fixAmper(subFieldYxs.get(imp));
				}
				ArrayList<String> subFieldZxs = sqlObj.selectSubFieldValuesByID(fieldID, "z");
				for (int impb=0;impb < subFieldZxs.size();impb++) {
					sixhundySubZ = fixAmper(subFieldZxs.get(impb));
				}
				
				String sixhundyString = "";
				boolean enteredItemSx = false;				
				if (sixhundySubA != null && sixhundySubA.length() > 0) {
					sixhundyString = fixCarrots(fixAmper(sixhundySubA));
					enteredItemSx = true;
				}
				if (sixhundySubB != null && sixhundySubB.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + ", ";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubB));
					enteredItemSx = true;
				}
				if (sixhundySubC != null && sixhundySubC.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + ", ";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubC));
					enteredItemSx = true;
				}
				if (sixhundySubD != null && sixhundySubD.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + ", ";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubD));
					enteredItemSx = true;
				}
				if (sixhundySubE != null && sixhundySubE.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + ", ";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubE));
					enteredItemSx = true;
				}
				if (sixhundySubF != null && sixhundySubF.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubF));
					enteredItemSx = true;
				}
				if (sixhundySubG != null && sixhundySubG.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubG));
					enteredItemSx = true;
				}
				if (sixhundySubH != null && sixhundySubH.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubH));
					enteredItemSx = true;
				}
				if (sixhundySubI != null && sixhundySubI.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubI));
					enteredItemSx = true;
				}		
				if (sixhundySubJ != null && sixhundySubJ.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubJ));
					enteredItemSx = true;
				}
				if (sixhundySubK != null && sixhundySubK.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubK));
					enteredItemSx = true;
				}
				if (sixhundySubL != null && sixhundySubL.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubL));
					enteredItemSx = true;
				}
				if (sixhundySubM != null && sixhundySubM.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubM));
					enteredItemSx = true;
				}	
				if (sixhundySubN != null && sixhundySubN.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubN));
					enteredItemSx = true;
				}
				if (sixhundySubO != null && sixhundySubO.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubO));
					enteredItemSx = true;
				}
				if (sixhundySubP != null && sixhundySubP.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubP));
					enteredItemSx = true;
				}
				if (sixhundySubQ != null && sixhundySubQ.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubQ));
					enteredItemSx = true;
				}					
				if (sixhundySubR != null && sixhundySubR.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubR));
					enteredItemSx = true;
				}
				if (sixhundySubS != null && sixhundySubS.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubS));
					enteredItemSx = true;
				}
				if (sixhundySubT != null && sixhundySubT.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubT));
					enteredItemSx = true;
				}
				if (sixhundySubU != null && sixhundySubU.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubU));
					enteredItemSx = true;
				}	
				if (sixhundySubV != null && sixhundySubV.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubV));
					enteredItemSx = true;
				}
				if (sixhundySubW != null && sixhundySubW.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubW));
					enteredItemSx = true;
				}
				if (sixhundySubX != null && sixhundySubX.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubX));
					enteredItemSx = true;
				}
				if (sixhundySubY != null && sixhundySubY.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubY));
					enteredItemSx = true;
				}
				if (sixhundySubZ != null && sixhundySubZ.length() > 0) {
					if (enteredItemSx) {
						sixhundyString = sixhundyString + "--";
					}
					sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubZ));
					enteredItemSx = true;
				}
				if (enteredItemSx) {
					String estcTermId = sqlObj.selectSubjectID(sixhundyString);
					String sendString = "        <dct:subject>\n";
					sendString = sendString + "                <scm:about rdfs:resource=\"http://estc.bl.uk/subjects/" + estcTermId + "\">\n";
					sendString = sendString + "                     <rdfs:label>" + sixhundyString + "</rdfs:label>\n";
					sendString = sendString + "                </scm:about>\n";
					sendString = sendString + "        </dct:subject>\n";
					subjectTerms.add(sendString);
				}

			} else if (fieldType.equals("630")) {
				// do 630 (subject term - Uniform Title)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, fieldID, "630", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

				
				
				
				
			} else if (fieldType.equals("648")) {
				// do 648 (subject term - Chronological Term) goes to dct:date eraStrings
				
				String sixFourEightSubA = "";
				ArrayList<String> subFieldAsfa = sqlObj.selectSubFieldValuesByID(fieldID, "a");
				for (int imp=0;imp < subFieldAsfa.size();imp++) {
					sixFourEightSubA = fixAmper(subFieldAsfa.get(imp));
				}			
				if (sixFourEightSubA != null && sixFourEightSubA.length() > 0) {
					eraStrings.add(fixCarrots(fixAmper(sixFourEightSubA)));
				}
				
				//ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				//subFieldsToInclude.add("a");
				//String thisSubjectString = getSubject(recordID, fieldID, "648", subFieldsToInclude, " ");
				//if (thisSubjectString != null && thisSubjectString.length() > 0) {
				//	eraStrings.add(thisSubjectString);
				//}

			} else if (fieldType.equals("650")) {
				// do 650 (subject term - Topical Term)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				subFieldsToInclude.add("c");
				subFieldsToInclude.add("d");
				String thisSubjectString = getSubject(recordID, fieldID, "650", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("651")) {
				// do 651 (subject term - Geographic Name)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, fieldID, "651", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
					// coverage.add(fixPeriods(fixAmper(thisSubjectString)));
				}

			} else if (fieldType.equals("653")) {
				// do 653 (subject term - Uncontrolled)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, fieldID, "653", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("654")) {
				// do 654 (subject term - Faceted topical term)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				String thisSubjectString = getSubject(recordID, fieldID, "654", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("655")) {
				// collex:genre
				
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
				
				// remove trailing period
				genre = fixCarrots(fixPeriods(fixAmper(workingValue)));
			} else if (fieldType.equals("656")) {
				// do 656 (subject term - Occupation)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, fieldID, "656", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("657")) {
				// do 657 (subject term - Function)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				String thisSubjectString = getSubject(recordID, fieldID, "657", subFieldsToInclude, " ");
				if (thisSubjectString != null && thisSubjectString.length() > 0) {
					subjectTerms.add(thisSubjectString);
				}

			} else if (fieldType.equals("658")) {
				// do 658 (subject term - Curriculum Objective)
				
				ArrayList<String> subFieldsToInclude = new ArrayList<String>();
				subFieldsToInclude.add("a");
				subFieldsToInclude.add("b");
				String thisSubjectString = getSubject(recordID, fieldID, "658", subFieldsToInclude, " ");
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
				String thisSubjectString = getSubject(recordID, fieldID, "662", subFieldsToInclude, " ");
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
				
				String hierarchicalPlace = "";
				boolean enteredItem = false;				
				if (subA != null && subA.length() > 0) {
					hierarchicalPlace = fixCarrots(fixPeriods(fixAmper(subA)));
					enteredItem = true;
					// coverage.add(fixCarrots(fixPeriods(fixAmper(subA))));
				}
				if (subB != null && subB.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subB)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subB))));
				}
				if (subC != null && subC.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subC)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subC))));
				}
				if (subD != null && subD.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subD)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subD))));
				}
				if (subF != null && subF.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subF)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subF))));
				}
				if (subG != null && subG.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subG)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subG))));
				}
				if (subH != null && subH.length() > 0) {
					if (enteredItem) {
						hierarchicalPlace = hierarchicalPlace + "--";
					}
					hierarchicalPlace = hierarchicalPlace + fixCarrots(fixPeriods(fixAmper(subH)));
					enteredItem = true;
					//coverage.add(fixCarrots(fixPeriods(fixAmper(subH))));
				}
				if (enteredItem) {
					coverage.add(hierarchicalPlace);
				}
			}
			
			else if (fieldType.equals("856")) {
				// digital surrogates
				surrogateSub = sqlObj.selectSubFieldValuesByID(fieldID, "u");
				
			}
			String rawThumbnail = sqlObj.selectImageRecord(recordID);
			if (rawThumbnail != null && rawThumbnail.length() > 0) {
				estcThumbnail = "http://estc21.ucr.edu/assets" + rawThumbnail;
			}

		}
		
		// build the content part of the RDF
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
		
		// newly added fields
		//ArrayList<String> uniformTitle = new ArrayList<String>(); //   rdau:titleOfResource
		if (uniformTitle != null && uniformTitle.size() > 0) {
			for (itn=0;itn < uniformTitle.size();itn++) {
				rdfString = rdfString + "        <rdau:titleOfResource>" + uniformTitle.get(itn) + "</rdau:titleOfResource>\n";
			}
		}
		
		// String uniformTitleTwoForty = ""; // rdau:titleOfResource
		if (uniformTitleTwoForty != null && uniformTitleTwoForty.length() > 0) {
			rdfString = rdfString + "        <rdau:titleOfResource>" + uniformTitleTwoForty + "</rdau:titleOfResource>\n";
		}
		
		// String abrvTitle = ""; // rdau:abbreviatedTitle
		if (abrvTitle != null && abrvTitle.length() > 0) {
			rdfString = rdfString + "        <rdau:abbreviatedTitle>" + abrvTitle + "</rdau:abbreviatedTitle>\n";
		}
		
		//String seriesUniformTitle = ""; // rdau:titleProperOfSeries
		if (seriesUniformTitle != null && seriesUniformTitle.length() > 0) {
			rdfString = rdfString + "        <rdau:titleProperOfSeries>" + seriesUniformTitle + "</rdau:titleProperOfSeries>\n";
		}
		
		//String variantTitle = ""; // rdau:variantTitle
		if (variantTitle != null && variantTitle.length() > 0) {
			rdfString = rdfString + "        <rdau:variantTitle>" + variantTitle + "</rdau:variantTitle>\n";
		}
		
		//String formerTitle = ""; // rdau:earlierTitleProper
		if (formerTitle != null && formerTitle.length() > 0) {
			rdfString = rdfString + "        <rdau:earlierTitleProper>" + formerTitle + "</rdau:earlierTitleProper>\n";
		}
		
		//String editionStatement = ""; // rdau:editionStatement
		if (editionStatement != null && editionStatement.length() > 0) {
			rdfString = rdfString + "        <rdau:editionStatement>" + editionStatement + "</rdau:editionStatement>\n";
		}
		
		//String prodInfo = ""; // dct:publisher
		if (prodInfo != null && prodInfo.length() > 0) {
			rdfString = rdfString + "        <dct:publisher>" + prodInfo + "</dct:publisher>\n";
		}
		
		//String formerPubFreq = ""; // rdau:noteOnFrequency
		if (formerPubFreq != null && formerPubFreq.length() > 0) {
			rdfString = rdfString + "        <rdau:noteOnFrequency>" + formerPubFreq + "</rdau:noteOnFrequency>\n";
		}
	
		//String physDesc = ""; // dct:format
		if (physDesc != null && physDesc.length() > 0) {
			rdfString = rdfString + "        <dct:format>" + physDesc + "</dct:format>\n";
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
		
		//String Era
		//ArrayList<String> eraStrings = new ArrayList<String>(); //   dct:date
		if (eraStrings != null && eraStrings.size() > 0) {
			for (itn=0;itn < eraStrings.size();itn++) {
				rdfString = rdfString + "        <dct:date>" + eraStrings.get(itn) + "</dct:date>\n";
			}
		}
		
		//String imprintString = ""; // dct:publisher 
		if (imprintString != null && imprintString.length() > 0) {
			rdfString = rdfString + "        <dct:publisher>" + imprintString + "</dct:publisher>\n";
		}
		
		//String dublinPublisher = ""; // role publisher dct:publisher $b
		//if (dublinPublisher != null && dublinPublisher.length() > 0) {
		//	rdfString = rdfString + "        <dct:publisher>" + dublinPublisher + "</dct:publisher>\n";
		//}
		
		//String estcThumbnail = ""; // collex:thumbnail rdf:resource=""
		if (estcThumbnail != null && estcThumbnail.length() > 0) {
			rdfString = rdfString + "        <collex:thumbnail rdf:resource=\"" + estcThumbnail + "\" />\n";
		}
		
		//String dcRights = ""; // dct:rights
		if (dcRights != null && dcRights.length() > 0) {
			rdfString = rdfString + "        <dct:rights>" + dcRights + "</dct:rights>\n";
		}
		
		//ArrayList<String> seriesStatment = new ArrayList<String>(); //   isbdu:P1041  hasNoteOnSeriesAndMultipartMonographicResources
		if (seriesStatment != null && seriesStatment.size() > 0) {
			for (itn=0;itn < seriesStatment.size();itn++) {
				rdfString = rdfString + "        <isbdu:P1041>" + seriesStatment.get(itn) + "</isbdu:P1041>\n";
			}
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
		if (genre != null && genre.length() > 0) {
			rdfString = rdfString + "        <collex:genre>" + genre + "</collex:genre>\n";
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
		
		// put loop to build holding records here
		ArrayList<String> children = new ArrayList<String>();
		ArrayList<HashMap<String,String>> holdingRecords = sqlObj.selectHoldingRecordIDs(itemID);
		int ihr = 0;
		while (ihr < holdingRecords.size()) {
			String uniqueHoldingID = "";
			HashMap<String,String> holdingRecordResults = holdingRecords.get(ihr);
			int holdingRecordID = Integer.parseInt(holdingRecordResults.get("record_id"));
			//String holdingRecordID = holdingRecordResults.get("id");
			// now get all the 852 fields for the record
			ArrayList<Integer> eightFiftyTwos = sqlObj.selectEightFiftyTwoFields(holdingRecordID);
			int ihf = 0;
			while (ihf < eightFiftyTwos.size()) {
				int eightFiftyTwofieldID = eightFiftyTwos.get(ihf);
				ArrayList<HashMap<String,String>> holdingSubs  = sqlObj.selectAllSubfields(eightFiftyTwofieldID);
				// get the a subfield value (Location - in form of library code)
				String aVal = fixCarrots(fixAmper(getSubfieldValue(holdingSubs, "a")));
				// get the b subfield value (Sublocation or collection)
				String bVal = fixCarrots(fixAmper(getSubfieldValue(holdingSubs, "b")));
				// get the j subfield value (Shelving Control Number)
				String jVal = fixCarrots(fixAmper(getSubfieldValue(holdingSubs, "j")));
				// get the r subfield value (unique id)
				String rVal = fixCarrots(fixAmper(getSubfieldValue(holdingSubs, "r")));
				uniqueHoldingID = aVal + rVal;
				uniqueHoldingID = uniqueHoldingID.replaceAll("\\s+","");
				// get list of q values (physical location)
				ArrayList<String> qVals = getSubfieldValueList(holdingSubs, "q");
				
				
				// get the unique holding info
				String uniqueHRI = "http://" + domainURI + "/" + uniqueHoldingID;
				String rdfAboutHolding = "    <estc:estc rdf:about=\"" + uniqueHRI + "\">\n";
				children.add(uniqueHRI);
				String rdfStringAdditions = "";
				int ihq = 0;
				while (ihq < qVals.size()) {
					String subLocationValue = qVals.get(ihq);
					if (subLocationValue != null && subLocationValue.length() > 0) {
						rdfStringAdditions = rdfStringAdditions + "        <dct:description>" + fixCarrots(fixAmper(subLocationValue)) + "</dct:description>\n";
					}
					ihq++;
				}
				rdfStringAdditions = "        <role:OWN>" + aVal + "</role:OWN>\n";
				rdfStringAdditions = rdfStringAdditions + "        <role:RPS>" + bVal + "</role:RPS>\n";
				rdfStringAdditions = rdfStringAdditions + "        <bf:shelfMark>" + jVal + "</bf:shelfMark>\n";
								
				// now construct an rdf output for this holding:
				String holdingRDF = rdfHeader + rdfAboutHolding + rdfString + rdfStringAdditions + parentAssoc + rdfFooter;

				try {
					// write out the rdf
					String writeFileName = configObj.writeDir + "/hold_" + uniqueHoldingID + ".rdf";
					System.out.println("File Write Directory: " + writeFileName);
					PrintWriter holdingWriter = new PrintWriter( configObj.writeDir + "/hold_" + uniqueHoldingID + ".rdf", "UTF-8");
					holdingWriter.print(holdingRDF);
					holdingWriter.close();
					
				} catch (FileNotFoundException e) {
					System.out.println("Error exporting record " + holdingRecordID + " holding item " + uniqueHoldingID);
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					System.out.println("Error exporting record " + holdingRecordID + " holding item " + uniqueHoldingID);
					e.printStackTrace();
				}
				
				//System.out.println(holdingRDF);
				System.out.println("Processed holding record " + holdingRecordID + " item " + uniqueHoldingID);

				// need to keep this so that the loop works right
				ihf++;
			}
			
			// mark the holding record as exported
			sqlObj.updateExported(holdingRecordID);
			ihr++;
		
		}
		
		if (fiveHundNotes != null && fiveHundNotes.size() > 0) {
			for (int isn=0;isn < fiveHundNotes.size();isn++) {
				rdfString = rdfString + "        <dct:description>" + fiveHundNotes.get(isn) + "</dct:description>\n";
			}
		}

		// add child associations if any
		int ihch = 0;
		while (ihch < children.size()) {
			rdfString = rdfString + "        <bf:hasInstance>" + children.get(ihch) + "</bf:hasInstance>\n";
			ihch++;
		}

		// add digital surrogates
		for (int ids=0;ids < surrogateSub.size();ids++) {
			String digSur = surrogateSub.get(ids);
			if (digSur != null && digSur.length() > 0) {
				rdfString = rdfString + "        <scm:url>" + fixCarrots(fixAmper(digSur)) + "</scm:url>\n";
			}
		}
		
		String bibRDF = rdfHeader + rdfAbout + rdfString + rdfFooter;
		
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
		
		
		// System.out.println(bibRDF);
		System.out.println("Processed bib record " + recordID + " item " + itemID);
		
		return ret;
	}
	
	// create an RDF string for a resource
	public String getSubject(int recordID, int fieldID, String field, ArrayList<String> subFields, String separator) {
		String ret = "";
		String sixhundyString = "";
		boolean enteredItemSx = false;
		
		String sixhundySubA = "";
		String sixhundySubB = "";
		String sixhundySubC = "";
		String sixhundySubD = "";
		String sixhundySubE = "";
		String sixhundySubF = "";
		String sixhundySubG = "";
		String sixhundySubH = "";
		String sixhundySubI = "";
		String sixhundySubJ = "";
		String sixhundySubK = "";
		String sixhundySubL = "";
		String sixhundySubM = "";
		String sixhundySubN = "";
		String sixhundySubO = "";
		String sixhundySubP = "";
		String sixhundySubQ = "";
		String sixhundySubR = "";
		String sixhundySubS = "";
		String sixhundySubT = "";
		String sixhundySubU = "";
		String sixhundySubV = "";
		String sixhundySubW = "";
		String sixhundySubX = "";
		String sixhundySubY = "";
		String sixhundySubZ = "";
		
		ArrayList<String> subFieldAsx = sqlObj.selectSubFieldValuesByID(fieldID, "a");
		for (int imp=0;imp < subFieldAsx.size();imp++) {
			sixhundySubA = fixAmper(subFieldAsx.get(imp));
		}
		ArrayList<String> subFieldBsx = sqlObj.selectSubFieldValuesByID(fieldID, "b");
		for (int impb=0;impb < subFieldBsx.size();impb++) {
			sixhundySubB = fixAmper(subFieldBsx.get(impb));
		}
		ArrayList<String> subFieldCxs = sqlObj.selectSubFieldValuesByID(fieldID, "c");
		for (int impc=0;impc < subFieldCxs.size();impc++) {
			sixhundySubC = fixAmper(subFieldCxs.get(impc));
		}
		ArrayList<String> subFieldDxs = sqlObj.selectSubFieldValuesByID(fieldID, "d");
		for (int imp=0;imp < subFieldDxs.size();imp++) {
			sixhundySubD = fixAmper(subFieldDxs.get(imp));
		}
		ArrayList<String> subFieldEsx = sqlObj.selectSubFieldValuesByID(fieldID, "e");
		for (int impb=0;impb < subFieldEsx.size();impb++) {
			sixhundySubE = fixAmper(subFieldEsx.get(impb));
		}
		ArrayList<String> subFieldFxs = sqlObj.selectSubFieldValuesByID(fieldID, "f");
		for (int impc=0;impc < subFieldFxs.size();impc++) {
			sixhundySubF = fixAmper(subFieldFxs.get(impc));
		}				
		ArrayList<String> subFieldGxs = sqlObj.selectSubFieldValuesByID(fieldID, "g");
		for (int imp=0;imp < subFieldGxs.size();imp++) {
			sixhundySubG = fixAmper(subFieldGxs.get(imp));
		}
		ArrayList<String> subFieldHxs = sqlObj.selectSubFieldValuesByID(fieldID, "h");
		for (int impb=0;impb < subFieldHxs.size();impb++) {
			sixhundySubH = fixAmper(subFieldHxs.get(impb));
		}
		ArrayList<String> subFieldIxs = sqlObj.selectSubFieldValuesByID(fieldID, "i");
		for (int impc=0;impc < subFieldIxs.size();impc++) {
			sixhundySubI = fixAmper(subFieldIxs.get(impc));
		}				
		ArrayList<String> subFieldJxs = sqlObj.selectSubFieldValuesByID(fieldID, "j");
		for (int imp=0;imp < subFieldJxs.size();imp++) {
			sixhundySubJ = fixAmper(subFieldJxs.get(imp));
		}
		ArrayList<String> subFieldKxs = sqlObj.selectSubFieldValuesByID(fieldID, "k");
		for (int impb=0;impb < subFieldKxs.size();impb++) {
			sixhundySubK = fixAmper(subFieldKxs.get(impb));
		}
		ArrayList<String> subFieldLxs = sqlObj.selectSubFieldValuesByID(fieldID, "l");
		for (int impc=0;impc < subFieldLxs.size();impc++) {
			sixhundySubL = fixAmper(subFieldLxs.get(impc));
		}				
		ArrayList<String> subFieldMxs = sqlObj.selectSubFieldValuesByID(fieldID, "m");
		for (int imp=0;imp < subFieldMxs.size();imp++) {
			sixhundySubM = fixAmper(subFieldMxs.get(imp));
		}
		ArrayList<String> subFieldNxs = sqlObj.selectSubFieldValuesByID(fieldID, "n");
		for (int impb=0;impb < subFieldNxs.size();impb++) {
			sixhundySubN = fixAmper(subFieldNxs.get(impb));
		}
		ArrayList<String> subFieldOxs = sqlObj.selectSubFieldValuesByID(fieldID, "o");
		for (int impc=0;impc < subFieldOxs.size();impc++) {
			sixhundySubO = fixAmper(subFieldOxs.get(impc));
		}				
		ArrayList<String> subFieldPxs = sqlObj.selectSubFieldValuesByID(fieldID, "p");
		for (int imp=0;imp < subFieldPxs.size();imp++) {
			sixhundySubP = fixAmper(subFieldPxs.get(imp));
		}
		ArrayList<String> subFieldQxs = sqlObj.selectSubFieldValuesByID(fieldID, "q");
		for (int impb=0;impb < subFieldQxs.size();impb++) {
			sixhundySubQ = fixAmper(subFieldQxs.get(impb));
		}
		ArrayList<String> subFieldRxs = sqlObj.selectSubFieldValuesByID(fieldID, "r");
		for (int impc=0;impc < subFieldRxs.size();impc++) {
			sixhundySubR = fixAmper(subFieldRxs.get(impc));
		}				
		ArrayList<String> subFieldSxs = sqlObj.selectSubFieldValuesByID(fieldID, "s");
		for (int imp=0;imp < subFieldSxs.size();imp++) {
			sixhundySubS = fixAmper(subFieldSxs.get(imp));
		}
		ArrayList<String> subFieldTxs = sqlObj.selectSubFieldValuesByID(fieldID, "t");
		for (int impb=0;impb < subFieldTxs.size();impb++) {
			sixhundySubT = fixAmper(subFieldTxs.get(impb));
		}
		ArrayList<String> subFieldUxs = sqlObj.selectSubFieldValuesByID(fieldID, "u");
		for (int impc=0;impc < subFieldUxs.size();impc++) {
			sixhundySubU = fixAmper(subFieldUxs.get(impc));
		}				
		ArrayList<String> subFieldVxs = sqlObj.selectSubFieldValuesByID(fieldID, "v");
		for (int imp=0;imp < subFieldVxs.size();imp++) {
			sixhundySubV = fixAmper(subFieldVxs.get(imp));
		}
		ArrayList<String> subFieldWxs = sqlObj.selectSubFieldValuesByID(fieldID, "w");
		for (int impb=0;impb < subFieldWxs.size();impb++) {
			sixhundySubW = fixAmper(subFieldWxs.get(impb));
		}
		ArrayList<String> subFieldXxs = sqlObj.selectSubFieldValuesByID(fieldID, "x");
		for (int impc=0;impc < subFieldXxs.size();impc++) {
			sixhundySubX = fixAmper(subFieldXxs.get(impc));
		}				
		ArrayList<String> subFieldYxs = sqlObj.selectSubFieldValuesByID(fieldID, "y");
		for (int imp=0;imp < subFieldYxs.size();imp++) {
			sixhundySubY = fixAmper(subFieldYxs.get(imp));
		}
		ArrayList<String> subFieldZxs = sqlObj.selectSubFieldValuesByID(fieldID, "z");
		for (int impb=0;impb < subFieldZxs.size();impb++) {
			sixhundySubZ = fixAmper(subFieldZxs.get(impb));
		}
						
		if (sixhundySubA != null && sixhundySubA.length() > 0) {
			sixhundyString = fixCarrots(fixAmper(sixhundySubA));
			enteredItemSx = true;
		}
		if (sixhundySubB != null && sixhundySubB.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubB));
			enteredItemSx = true;
		}
		if (sixhundySubC != null && sixhundySubC.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubC));
			enteredItemSx = true;
		}
		if (sixhundySubD != null && sixhundySubD.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubD));
			enteredItemSx = true;
		}
		if (sixhundySubE != null && sixhundySubE.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + ", ";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubE));
			enteredItemSx = true;
		}
		if (sixhundySubF != null && sixhundySubF.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubF));
			enteredItemSx = true;
		}
		if (sixhundySubG != null && sixhundySubG.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubG));
			enteredItemSx = true;
		}
		if (sixhundySubH != null && sixhundySubH.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubH));
			enteredItemSx = true;
		}
		if (sixhundySubI != null && sixhundySubI.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubI));
			enteredItemSx = true;
		}		
		if (sixhundySubJ != null && sixhundySubJ.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubJ));
			enteredItemSx = true;
		}
		if (sixhundySubK != null && sixhundySubK.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubK));
			enteredItemSx = true;
		}
		if (sixhundySubL != null && sixhundySubL.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubL));
			enteredItemSx = true;
		}
		if (sixhundySubM != null && sixhundySubM.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubM));
			enteredItemSx = true;
		}	
		if (sixhundySubN != null && sixhundySubN.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubN));
			enteredItemSx = true;
		}
		if (sixhundySubO != null && sixhundySubO.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubO));
			enteredItemSx = true;
		}
		if (sixhundySubP != null && sixhundySubP.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubP));
			enteredItemSx = true;
		}
		if (sixhundySubQ != null && sixhundySubQ.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubQ));
			enteredItemSx = true;
		}					
		if (sixhundySubR != null && sixhundySubR.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubR));
			enteredItemSx = true;
		}
		if (sixhundySubS != null && sixhundySubS.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubS));
			enteredItemSx = true;
		}
		if (sixhundySubT != null && sixhundySubT.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubT));
			enteredItemSx = true;
		}
		if (sixhundySubU != null && sixhundySubU.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubU));
			enteredItemSx = true;
		}	
		if (sixhundySubV != null && sixhundySubV.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubV));
			enteredItemSx = true;
		}
		if (sixhundySubW != null && sixhundySubW.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubW));
			enteredItemSx = true;
		}
		if (sixhundySubX != null && sixhundySubX.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubX));
			enteredItemSx = true;
		}
		if (sixhundySubY != null && sixhundySubY.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubY));
			enteredItemSx = true;
		}
		if (sixhundySubZ != null && sixhundySubZ.length() > 0) {
			if (enteredItemSx) {
				sixhundyString = sixhundyString + "--";
			}
			sixhundyString = sixhundyString + fixCarrots(fixAmper(sixhundySubZ));
			enteredItemSx = true;
		}

		if (sixhundyString != null && sixhundyString.length() > 0) {
			
			//String collapseString = workingString.replaceAll("[^\\s]", "");
			String estcTermId = sqlObj.selectSubjectID(sixhundyString);
			
			ret = ret + "        <dct:subject>\n";
			ret = ret + "                <scm:about rdfs:resource=\"http://estc.bl.uk/subjects/" + estcTermId + "\">\n";
			ret = ret + "                     <rdfs:label>" + fixCarrots(fixPeriods(fixAmper(sixhundyString))) + "</rdfs:label>\n";
			ret = ret + "                </scm:about>\n";
			ret = ret + "        </dct:subject>\n";
		}

		return ret;
	}
	
	public String formatContributor(String relator, String value) {
		String ret="";
		if (relator != null && relator.length() > 0) {
			if (value != null && value.length() > 0) {
				// get unique ESTC agent ID
				String estcAgents = sqlObj.selectAgentID(value);
				if (estcAgents != null && estcAgents.length() > 0) {
					ret = "        <dct:creator>\n";
					String cleanedRelator = fixPeriods(relator);
					
					if (cleanedRelator == null || cleanedRelator.length() == 0) {
						cleanedRelator = "AUT";
					}
					
					// test the relator value
					cleanedRelator = cleanedRelator.trim();
					String finalRelator = "CTB";
					
				    String[] rolesArray = {"Expert", 
				    		"Abridger", 
				    		"Actor", 
				    		"Adapter", 
				    		"Addressee", 
				    		"Analyst", 
				    		"Animator", 
				    		"Annotator", 
				    		"Appellant", 
				    		"Appellee", 
				    		"Applicant", 
				    		"Architect", 
				    		"Arranger", 
				    		"Art copyist", 
				    		"Art director", 
				    		"Artist", 
				    		"Artistic director", 
				    		"Assignee", 
				    		"Associated name", 
				    		"Attributed name", 
				    		"Auctioneer", 
				    		"Author", 
				    		"Author in quotations or text abstracts", 
				    		"Author of afterword, colophon, etc.", 
				    		"Author of dialog", 
				    		"Author of introduction, etc.", 
				    		"Autographer", 
				    		"Bibliographic antecedent ", 
				    		"Binder", 
				    		"Binding designer", 
				    		"Blurb writer", 
				    		"Book designer", 
				    		"Book producer", 
				    		"Bookjacket designer", 
				    		"Bookplate designer", 
				    		"Bookseller", 
				    		"Braille embosser", 
				    		"Broadcaster", 
				    		"Calligrapher", 
				    		"Cartographer ", 
				    		"Caster", 
				    		"Censor", 
				    		"Choreographer", 
				    		"Cinematographer", 
				    		"Client", 
				    		"Collection registrar", 
				    		"Collector", 
				    		"collotyper", 
				    		"Colorist", 
				    		"Commentator", 
				    		"Commentator for written text", 
				    		"Compiler", 
				    		"Complainant", 
				    		"Complainant-appellant", 
				    		"Complainant-appellee", 
				    		"Composer", 
				    		"Compositor", 
				    		"Cenceptor", 
				    		"Conductor", 
				    		"Conservator", 
				    		"Consultant", 
				    		"Consultant to a project", 
				    		"Contestant", 
				    		"Contestant-appellant", 
				    		"Contestant-appelee ", 
				    		"Contestee", 
				    		"Contestee-appellant", 
				    		"Contestee-appellee", 
				    		"Contractor", 
				    		"Contributor", 
				    		"Copyright claimant", 
				    		"Copyright holder", 
				    		"Corrector", 
				    		"Correspondent", 
				    		"Costume designer", 
				    		"Court governed", 
				    		"Court reporter", 
				    		"Cover designer", 
				    		"Creator", 
				    		"Curator", 
				    		"Dancer", 
				    		"Data contributor", 
				    		"Data manager", 
				    		"Dedicatee", 
				    		"Dedicator", 
				    		"Defendant", 
				    		"Defendant-appellant", 
				    		"Defendant-appellee", 
				    		"Degree granting institution", 
				    		"Degree supervisor", 
				    		"Delineator", 
				    		"Depicted", 
				    		"Depositor", 
				    		"Designer", 
				    		"Director", 
				    		"Dissertant", 
				    		"Distribution place", 
				    		"Distributor", 
				    		"Donor", 
				    		"Draftsman", 
				    		"Dubious author", 
				    		"Editor", 
				    		"Editor of compilation", 
				    		"Editor of moving image work", 
				    		"Electrician", 
				    		"Electrotyper", 
				    		"Enacting jurisdiction ", 
				    		"Engineer", 
				    		"Engraver", 
				    		"Etcher", 
				    		"Event place", 
				    		"Expert", 
				    		"Facsimilist", 
				    		"Field director", 
				    		"Fim director", 
				    		"Film distributor ", 
				    		"Film editor", 
				    		"Film producer", 
				    		"Filmmaker", 
				    		"First party", 
				    		"Forger", 
				    		"Former owner", 
				    		"Funder", 
				    		"Geographic information specialist", 
				    		"Honoree", 
				    		"Host", 
				    		"Host institution", 
				    		"Illuminator", 
				    		"Illustrator", 
				    		"Inscriber", 
				    		"Instrumentalist", 
				    		"Interviewee", 
				    		"Interviewer", 
				    		"Inventor", 
				    		"Issuing body", 
				    		"Judge", 
				    		"Jurisdiction governed", 
				    		"Laboratory", 
				    		"Laboratory director", 
				    		"Landscape architect", 
				    		"Lead", 
				    		"Lender", 
				    		"Libelant", 
				    		"Libelant-appellant", 
				    		"Libelant-appellee", 
				    		"Libelee", 
				    		"Libelee-appellant", 
				    		"Libelee-appellee", 
				    		"Librettist", 
				    		"Licensee", 
				    		"Licensor", 
				    		"Lighting designer", 
				    		"Lithographer", 
				    		"Lyricist", 
				    		"Manufacture place", 
				    		"Manufacturer", 
				    		"Marbler", 
				    		"Markup editor", 
				    		"Medium", 
				    		"Metadata contact", 
				    		"Metal-engraver", 
				    		"Minute taker", 
				    		"Moderator", 
				    		"Monitor", 
				    		"Music copyist", 
				    		"Musical director", 
				    		"Musician", 
				    		"Narrator", 
				    		"Onscreen presenter", 
				    		"Opponent ", 
				    		"Organizer", 
				    		"Originator", 
				    		"Other", 
				    		"Owner", 
				    		"Panelist", 
				    		"Papermaker", 
				    		"Patent applicant", 
				    		"Patent holder", 
				    		"Patron", 
				    		"Performer", 
				    		"Permitting agency", 
				    		"Photographer", 
				    		"Plaintiff", 
				    		"Plaintiff-appellant", 
				    		"Plaintiff-appellee", 
				    		"Platemaker", 
				    		"Praeses", 
				    		"Presenter", 
				    		"Printer", 
				    		"Printer of plates", 
				    		"Printmaker", 
				    		"Process of contact", 
				    		"Producer", 
				    		"Production company", 
				    		"Production designer", 
				    		"Production manager", 
				    		"Production personnel", 
				    		"Production place", 
				    		"Programmer", 
				    		"Project director", 
				    		"Proofreader", 
				    		"Provider", 
				    		"Publication place", 
				    		"Publisher", 
				    		"Publishing director", 
				    		"Puppeteer", 
				    		"Radio director", 
				    		"Radio producer", 
				    		"Recording engineer", 
				    		"Recordist", 
				    		"redaktor", 
				    		"renderer", 
				    		"reporter", 
				    		"respository", 
				    		"Reasearch team head", 
				    		"Research team member", 
				    		"Researcher", 
				    		"Respondent", 
				    		"Respondent-appellant", 
				    		"Respondent-appellee", 
				    		"Responsible part", 
				    		"Restager", 
				    		"Restorationist", 
				    		"Reviewer", 
				    		"Rubricator", 
				    		"Scenarist", 
				    		"Scientific advisor", 
				    		"Screenwriter", 
				    		"Scribe", 
				    		"Sculptor", 
				    		"Second party", 
				    		"Secretary", 
				    		"Seller", 
				    		"Set designer", 
				    		"Setting", 
				    		"Signer", 
				    		"Singer", 
				    		"Sound designer", 
				    		"Speaker", 
				    		"Sponsor", 
				    		"Stage director", 
				    		"Stage manager", 
				    		"Standards body", 
				    		"Stereotyper", 
				    		"Storyteller", 
				    		"Supporting host", 
				    		"Surveyor", 
				    		"Teacher", 
				    		"Technical director", 
				    		"Television director", 
				    		"Television producer", 
				    		"Thesis advisor", 
				    		"Transcriber", 
				    		"Translator", 
				    		"Type designer", 
				    		"Typographer", 
				    		"University Place", 
				    		"Videographer", 
				    		"Voice actor", 
				    		"Witness", 
				    		"Wood engraver", 
				    		"Woodcutter", 
				    		"Writer of accompanying material", 
				    		"Writer of added commentary", 
				    		"Writer of added lyrics", 
				    		"Writer of added text", 
				    		"Writer of introduction", 
				    		"Writer of preface", 
				    		"Writer of supplementary textual content"};
				    
				    String[] codesArray = {"EXP",  
				    		"ABR", 
				    		"ACT", 
				    		"ADP", 
				    		"RCP", 
				    		"ANL", 
				    		"ANM", 
				    		"ANN", 
				    		"APL", 
				    		"APE", 
				    		"APP", 
				    		"ARC", 
				    		"ARR", 
				    		"ACP", 
				    		"ADI", 
				    		"ART", 
				    		"ARD", 
				    		"ASG", 
				    		"ASN", 
				    		"ATT", 
				    		"AUC", 
				    		"AUT", 
				    		"AQT", 
				    		"AFT", 
				    		"AUD", 
				    		"AUI", 
				    		"ATO", 
				    		"ANT", 
				    		"BND", 
				    		"BDD", 
				    		"BLW", 
				    		"BKD", 
				    		"BKP", 
				    		"BJD", 
				    		"BPD", 
				    		"BSL", 
				    		"BRL", 
				    		"BRD", 
				    		"CLL", 
				    		"CTG", 
				    		"CAS", 
				    		"CNS", 
				    		"CHR", 
				    		"CNG", 
				    		"CLI", 
				    		"COR", 
				    		"COL", 
				    		"CLT", 
				    		"CLR", 
				    		"CMM", 
				    		"CWT", 
				    		"COM", 
				    		"CPL", 
				    		"CPT", 
				    		"CPE", 
				    		"CMP", 
				    		"CMT", 
				    		"CCP", 
				    		"CND", 
				    		"CON", 
				    		"CSL", 
				    		"CSP", 
				    		"COS", 
				    		"COT", 
				    		"COE", 
				    		"CTS", 
				    		"CTT", 
				    		"CTE", 
				    		"CTR", 
				    		"CTB", 
				    		"CPC", 
				    		"CPH", 
				    		"CRR", 
				    		"CRP", 
				    		"CST", 
				    		"COU", 
				    		"CRT", 
				    		"COV", 
				    		"CRE", 
				    		"CUR", 
				    		"DNC", 
				    		"DTC", 
				    		"DTM", 
				    		"DTE", 
				    		"DTO", 
				    		"DFD", 
				    		"DFT", 
				    		"DFE", 
				    		"DGG", 
				    		"DGS", 
				    		"DLN", 
				    		"DPC", 
				    		"DPT", 
				    		"DSR", 
				    		"DRT", 
				    		"DIS", 
				    		"DBP", 
				    		"DST", 
				    		"DNR", 
				    		"DRM", 
				    		"DUB", 
				    		"EDT", 
				    		"EDC", 
				    		"EDM", 
				    		"ELG", 
				    		"ELT", 
				    		"ENJ", 
				    		"ENG", 
				    		"EGR", 
				    		"ETR", 
				    		"EVP", 
				    		"EXP", 
				    		"FAC", 
				    		"FLD", 
				    		"FMD", 
				    		"FDS", 
				    		"FLM", 
				    		"FMP", 
				    		"FMK", 
				    		"FPY", 
				    		"FRG", 
				    		"FMO", 
				    		"FND", 
				    		"GIS", 
				    		"HNR", 
				    		"HST", 
				    		"HIS", 
				    		"ILU", 
				    		"ILL", 
				    		"INS", 
				    		"ITR", 
				    		"IVE", 
				    		"IVR", 
				    		"INV", 
				    		"ISB", 
				    		"JUD", 
				    		"JUG", 
				    		"LBR", 
				    		"LDR", 
				    		"LSA", 
				    		"LED", 
				    		"LEN", 
				    		"LIL", 
				    		"LIT", 
				    		"LIE", 
				    		"LEL", 
				    		"LET", 
				    		"LEE", 
				    		"LBT", 
				    		"LSE", 
				    		"LSO", 
				    		"LGD", 
				    		"LTG", 
				    		"LYR", 
				    		"MFP", 
				    		"MFR", 
				    		"MRB", 
				    		"MRK", 
				    		"MED", 
				    		"MDC", 
				    		"MTE", 
				    		"MTK", 
				    		"MOD", 
				    		"MON", 
				    		"MCP", 
				    		"MSD", 
				    		"MUS", 
				    		"NRT", 
				    		"OSP", 
				    		"OPN", 
				    		"ORM", 
				    		"ORG", 
				    		"OTH", 
				    		"OWN", 
				    		"PAN", 
				    		"PPM", 
				    		"PTA", 
				    		"PTH", 
				    		"PAT", 
				    		"PRF", 
				    		"PMA", 
				    		"PHT", 
				    		"PTF", 
				    		"PTT", 
				    		"PTE", 
				    		"PLT", 
				    		"PRA", 
				    		"PRE", 
				    		"PRT", 
				    		"POP", 
				    		"PRM", 
				    		"PRC", 
				    		"PRO", 
				    		"PRN", 
				    		"PRS", 
				    		"PMN", 
				    		"PRD", 
				    		"PRP", 
				    		"PRG", 
				    		"PDR", 
				    		"PFR", 
				    		"PRV", 
				    		"PUP", 
				    		"PBL", 
				    		"PBD", 
				    		"PPT", 
				    		"RDD", 
				    		"RPC", 
				    		"RCE", 
				    		"RCD", 
				    		"RED", 
				    		"REN", 
				    		"RPT", 
				    		"RPS", 
				    		"RTH", 
				    		"RTM", 
				    		"RES", 
				    		"RSP", 
				    		"RST", 
				    		"RSE", 
				    		"RPY", 
				    		"RSG", 
				    		"RSR", 
				    		"REV", 
				    		"RBR", 
				    		"SCE", 
				    		"SAD", 
				    		"AUS", 
				    		"SCR", 
				    		"SCL", 
				    		"SPY", 
				    		"SEC", 
				    		"SLL", 
				    		"STD", 
				    		"STG", 
				    		"SGN", 
				    		"SNG", 
				    		"SDS", 
				    		"SPK", 
				    		"SPN", 
				    		"SGD", 
				    		"STM", 
				    		"STN", 
				    		"STR", 
				    		"STL", 
				    		"SHT", 
				    		"SRV", 
				    		"TCH", 
				    		"TCD", 
				    		"TLD", 
				    		"TLP", 
				    		"THS", 
				    		"TRC", 
				    		"TRL", 
				    		"TYD", 
				    		"TYG", 
				    		"UVP", 
				    		"VDG", 
				    		"VAC", 
				    		"WIT", 
				    		"WDE", 
				    		"WDC", 
				    		"WAM", 
				    		"WAC", 
				    		"WAL", 
				    		"WAT", 
				    		"WIN", 
				    		"WPR", 
				    		"WST"};
				    
				    cleanedRelator = cleanedRelator.toUpperCase();
				    
				    Boolean cleancode = false;
				    for(int i=0; i< codesArray.length; i++){ 
				    	if(codesArray[i] == cleanedRelator) {
				    		cleancode = true;
				    	}
				    }
				    
				    
				    if (cleancode == false) {
				    	
				    	for(int i=0; i< rolesArray.length; i++){ 
					    	
					    	String patternString = "^" + rolesArray[i] + "$";
						    Pattern rolep = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
						    Matcher rolem = rolep.matcher(cleanedRelator);
					    	
						    if (rolem.find()) {
						    	finalRelator = codesArray[i];
							}
						    
					    }
					    
					} else {
						finalRelator = cleanedRelator;
					}
				    
					ret = ret + "            <scm:contributor rdfs:resource=\"http://estcbluk/agents/" + estcAgents + "\">\n";
					ret = ret + "                <role:" + finalRelator + ">" + value + "</role:" + finalRelator + ">\n";
					ret = ret + "            </scm:contributor>\n";
					ret = ret + "        </dct:creator>\n";
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
