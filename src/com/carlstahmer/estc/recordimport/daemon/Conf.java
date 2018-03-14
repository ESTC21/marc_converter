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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.yaml.snakeyaml.Yaml;

/**
 * @author cstahmer
 *  
 * <p>A configuration class that contains code for processing a .yml configuration file
 * and loading all configuration variables into class objects.  By creating a local
 * instance of this class or being passed an instance, other classes and functions gain
 * access to configuration variable values.</p>
 */
public class Conf {

	String listenDir;
	String writeDir;
	int processType;
	String export;
	String exportJena;
	String daemon;
	int runInterval;
	String orgcode;
	String dbserver;
	String dbname;
	String dbuser;
	String dbpass;
	String langscope;
	ArrayList<String> languageScope = new ArrayList<String>();
	boolean debug;
	boolean console;
	boolean liberal;
	int lDateScopeBound;
	int uDateScopebound;
	ArrayList<String> fiveHundyDateFields = new ArrayList<String>();
	String[] libcodes;
	String libcodesCSV;
	String sitedomain;
	
	public Conf() {
		listenDir = "";
		writeDir = "";
		processType = 0;
		export = "";
		exportJena = "";
		daemon = "";
		runInterval = 10000000;
		orgcode = "";
		dbserver = "";
		dbname = "";
		dbuser = "";
		dbpass = "";
		langscope = "eng,enm";
		debug = false;
		console = false;
		liberal = true;
		lDateScopeBound = 1473;
		uDateScopebound = 1800;
		fiveHundyDateFields.add("500");
		fiveHundyDateFields.add("501");
		fiveHundyDateFields.add("504");
		libcodesCSV = "estcstar,estc";
		sitedomain = "estc.bl.uk";
		
	}
	
	/**
	 * <p>An initialization class that tells the object to read the .yml configuration file
	 * and load all values.  Must be called before trying to access any class properties.</p>
	 * 
	 * @return      			a boolean value indicating whether the configuration .yml has been successfully loaded.
	 */
	public boolean loadConf() {
			
		boolean loaded = false;
		// Load Configuration YAML
		try {
			
			InputStream yamlInput = new FileInputStream(new File("config.yml"));
			System.out.println("Found configuration file...");
			Yaml yaml = new Yaml();
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) yaml.load(yamlInput);
			listenDir = (String) map.get("listendir");
			writeDir = (String) map.get("writedir");
			runInterval = (Integer) map.get("runinterval");
			orgcode = (String) map.get("orgcode");
			dbserver = (String) map.get("dbserver");
			dbname = (String) map.get("dbname");
			dbuser = (String) map.get("dbuser");
			dbpass = (String) map.get("dbpass");
			String tempLangscope = (String) map.get("langscope");
			if (tempLangscope.length() > 0) {
				langscope = tempLangscope;
			}
			if (langscope.length() > 0) {
				setLangCodes();
			}
			int liberalvalue = (Integer) map.get("liberal");
			if (liberalvalue == 1) {
				liberal = true;
			}
			libcodesCSV = (String) map.get("estccodes");
			libcodes = libcodesCSV.split("\\s*,\\s*");
			sitedomain = (String) map.get("sitedomain");
			
			loaded = true;
		
		} catch (FileNotFoundException e) {
		    System.err.println("Configuration FileNotFoundException: " + e.getMessage());
		    loaded = false;
		}
		
		return loaded;
	}
	
	/**
	 * <p>Checks for passed command line arguments and replaces .yml loaded values with those
	 * entered at the command line as appropriate.</p>
	 *
	 * @param  args    	command line String[] args array
	 * @return      	a boolean value indicate whether or not method executed successfully
	 */
	public boolean checkArgs(String[] args) {
		
		boolean loaded = false;
	
		try {
			Options options = new Options();
			options.addOption("listendir", true, "full directory path to listen directory");
			options.addOption("writedir", true, "full directory path to write output records");
			options.addOption("export", false, "run ESTC RDF export process");
			options.addOption("exportJena", false, "run Jena RDF export process");
			options.addOption("daemon", false, "run as daemon process");
			options.addOption("orgcode", true, "the marc orgcode of the cataloge owner");
			options.addOption("sitedomain", true, "the domain to use for URI minting for objects");
			options.addOption("runinterval", true, "the time to wait between process spawning");
			options.addOption("dbserver", true, "the sql server");
			options.addOption("dbname", true, "the sql server database name");
			options.addOption("dbuser", true, "the sql user");
			options.addOption("dbpass", true, "the sql user");
			options.addOption("langscope", true, "a csv list of MARC language codes for in-scope languages");
			options.addOption("debug", false, "run in debug mode - verbose logging");
			options.addOption("console", false, "write log to console instead of database");
			options.addOption("liberal", false, "keep records with missing control data");
			options.addOption("libcodes", false, "csv list of institutional codes that represent ESTC bib records");
			options.addOption("help", false, "get help");
							
			
			CommandLineParser parser = new BasicParser();
			CommandLine cmd = parser.parse( options, args);
			if(cmd.hasOption("listendir")) {
				String ldirVal = cmd.getOptionValue("listendir");
				if(ldirVal != null) {
					listenDir = ldirVal;
				}
			} 
			if (cmd.hasOption("writedir")) {
				String wdirVal = cmd.getOptionValue("writedir");
				if(wdirVal != null) {
					writeDir = wdirVal;
				}
			}
			if (cmd.hasOption("export")) {
				processType = 1;
			}
			if (cmd.hasOption("exportJena")) {
				processType = 2;
			}
			if (cmd.hasOption("daemon")) {
				processType = 3;
			}			
			if (cmd.hasOption("orgcode")) {
				String ocVal = cmd.getOptionValue("orgcode");
				if(ocVal != null) {
					orgcode = ocVal;
				}
			} 
			if (cmd.hasOption("runinterval")) {
				String riVal = cmd.getOptionValue("runinterval");
				if(riVal != null) {
					runInterval = Integer.parseInt(riVal);
				}
			}
			
			if (cmd.hasOption("dbserver")) {
				String srvVal = cmd.getOptionValue("dbserver");
				if(srvVal != null) {
					dbserver = srvVal;
				}
			}
			if (cmd.hasOption("dbname")) {
				String dbnameVal = cmd.getOptionValue("dbname");
				if(dbnameVal != null) {
					dbname = dbnameVal;
				}
			}
			if (cmd.hasOption("dbuser")) {
				String dbuserVal = cmd.getOptionValue("dbuser");
				if(dbuserVal != null) {
					dbuser = dbuserVal;
				}
			}
			if (cmd.hasOption("dbpass")) {
				String dbpassVal = cmd.getOptionValue("dbpass");
				if(dbpassVal != null) {
					dbpass = dbpassVal;
				}
			}
			if (cmd.hasOption("debug")) {
				debug = true;
			}
			if (cmd.hasOption("console")) {
				console = true;
			}
			if (cmd.hasOption("liberal")) {
				liberal = true;
			}
			if (cmd.hasOption("langscope")) {
				String langscopeVal = cmd.getOptionValue("langscope");
				if(langscopeVal != null) {
					langscope = langscopeVal;
				}
			}
			if (cmd.hasOption("estccodes")) {
				String libcodesVal = cmd.getOptionValue("estccodes");
				if(libcodesVal != null) {
					libcodesCSV = libcodesVal;
				}
			}
			if (cmd.hasOption("sitedomain")) {
				String sitedomainVal = cmd.getOptionValue("sitedomain");
				if(sitedomainVal != null) {
					sitedomain = sitedomainVal;
				}
			}
			if (cmd.hasOption("help")) {
					String HelpString = "Requires the presence of a config.yml in the application root to run correcly. ";
					HelpString = HelpString + "Values in the config can be overwritten at runtime via command line ";
					HelpString = HelpString + "arguments as follows:\n\n";
					HelpString = HelpString + "-listendir [/dirctory/path/of/listen/directory]\n";
					HelpString = HelpString + "-writedir [/directory/path/of/output/directory]\n";
					HelpString = HelpString + "-runinterval [the interval to wait between listinging attempts]\n";
					HelpString = HelpString + "-orgcode [the marc org code to use in constucting the new records]\n";
					HelpString = HelpString + "-dbserver [the sql database server]\n";
					HelpString = HelpString + "-dbname [the sql database name]\n";
					HelpString = HelpString + "-dbuser [the sql database user]\n";
					HelpString = HelpString + "-dbpass [the sql database password]\n";
					HelpString = HelpString + "-debug [runs application in debug mode - verbose logging]\n";
					HelpString = HelpString + "-console [writes log output to console instead of database]\n";
					HelpString = HelpString + "-help [runs this help message]\n\n";
					HelpString = HelpString + "Config.yml file must be in place even if you are supplying information ";
					HelpString = HelpString + "via the command line.";
					System.out.println(HelpString);
					System.exit(0);
			}
			
			if (langscope.length() > 0) {
				setLangCodes();
			}

			loaded = true;
			
		}  catch (ParseException e) {
			System.err.println("ERROR:\tCommand Line Argument Error: " + e.getMessage());
			loaded = false;
		}
			
		return loaded;	
			
	}

	
	/**
	 * <p>A private method used by other Conf methods to convert the csv in-scope
	 * language string (set by yml or command-line) to an ArrayList of acceptable
	 * MARC language codes.</p>
	 */
	private void setLangCodes() {
		languageScope.clear();
		String[] languageScopeArray = langscope.split(",");
		// String[] languageScope = langscope.split(",");
		for (int i=0;i<languageScopeArray.length;i++) {
			String langCode = languageScopeArray[i].toString();
			languageScope.add(langCode);
		}
	}
	
}
