package gov.fnal.eag.dtucker.desPhotoStds;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;

//import jsky.science.MathUtilities;

/**
 * GlobalZPSolverPrepDC4.java   The DES Collaboration  2007
 * This class prepares an ASCII table of unique star matches 
 * from all the overlapping tiles in a given coadd.  This table 
 * is input for the GlobalZPSolverRun command, which solves for 
 * region-to-region zeropoint offsets for a set of overlapping 
 * regions.  
 * 
 * @author dtucker
 */

public class GlobalZPSolverPrepDC4 {

	// Instance variables dealing with the SQL database
	private String sqlDriver = "oracle.jdbc.driver.OracleDriver";
	private String url = "jdbc:oracle:thin:@//sky.ncsa.uiuc.edu:1521/";
	private String dbName = "des";
	private String user = "dummy";
	private String passwd = "dummy";

	// Instance variables dealing with this run of GlobalZPSolverPrepDC4	
	private Date date = new Date();
	private String outputFileName = "GlobalZPSolverPrepDC4Temp.dat";
	private int referenceImageID = -1;

	// Instance variables dealing with the observed data to be calibrated
	private String filter = "r";
	private double magLo = -100.0;
	private double magHi =  100.0;
	private String imageType = "coadd";
	private String imageNameFilter = "DES%";
	private String run = "%";
	private String project = "DES";
	private double raMin  =   0.00;
	private double raMax  = 360.00;
	private double decMin = -90.00;
	private double decMax =  90.00;
	private double sepTolerDeg = 1.5;

	// General instance variables... 
	private String[] filterList = { "u", "g", "r", "i", "z", "Y"};   // valid filters
	private String[] imageTypeList = {"coadd", "red", "remap"};      // valid image types
	private int verbose = 1;

	public void solve() throws Exception {

		System.out.println("\n\nGlobalZPSolverPrepDC4");

		if (verbose > 0) {
			System.out.println("");
			System.out.println("The beginning...");
			System.out.println("");
		}

		// sanity check:  test for valid imageType...
		int imageTypeFlag = 0;
		for (int j = 0; j < imageTypeList.length; j++) {
			if (imageType.equals(imageTypeList[j])) {
				imageTypeFlag = 1;
				break;
			}
		}
		if (imageTypeFlag != 1) {
			System.out.println("Invalid imageType index.");
			System.out.print("Valid imageTypes are:  ");
			for (int j = 0; j < imageTypeList.length; j++) {
				System.out.print(imageTypeList[j] + " ");
			}
			System.out.println("");
			System.out.println("Throwing Exception!");
			System.out.println("");
			throw new Exception();
		}
		
		int filterIndex = -1;
		for (int j = 0; j < filterList.length; j++) {
			if (filter.equals(filterList[j])) {
				filterIndex = j;
				break;
			}
		}
		if (verbose > 0) {
			System.out.println("filterIndex for filter " + filter + " is "
					+ filterIndex + ".");
			System.out.println("");
		}
		// temporarily skip this for BCS red/remap images...
		if (project.equalsIgnoreCase("DES") || imageType.equalsIgnoreCase("coadd")) {
			if (filterIndex < 0) {
				System.out
						.println("Incompatible filter index.  Throwing Exception!");
				System.out.println("");
				throw new Exception();
			}
		}
						
		// set an upper limit separation tolerance for overlaps...
		if (imageType.equalsIgnoreCase("coadd")) {
			// for coadd images, 1.5deg is reasonable...
			sepTolerDeg = 1.5;
		} else {
			// for individual CCDs, 0.3deg is reasonable...
			sepTolerDeg = 0.3;
		}

		// Establish connection to database
		if (verbose > 0) {
			System.out.println("Establishing connection to database.");
			System.out.println("");
		}
		Class.forName(sqlDriver);
		String fullURL = url + dbName;
		Connection db = DriverManager.getConnection(fullURL, user, passwd);

		if (project.equalsIgnoreCase("BCS")) {
			raMin = 330.;  //raMin = 50.;
			raMax = 370.;  //raMax = 100.;
		}
		// This query identifies all images of a given imagetype, filter bandpass, 
		// imagename, and runiddesc.  These images will be looped over later to 
		// find all unique star matches in the image-to-image overlaps
		String query0 = "SELECT * FROM image WHERE imagetype='" + imageType + "' AND " +
				"band like'" + filter + "' AND imagename like '" + imageNameFilter + "' AND " +
				"run like '" + run + "' AND project='" + project + "' AND " +
				"(RA  BETWEEN " + raMin  + " AND " + raMax  + ") AND " +
				"(DEC BETWEEN " + decMin + " AND " + decMax + ") ORDER BY id";
				
		if (verbose > 0) {
			System.out.println("query0 = " + query0);
			System.out.println("");
		}

		// This query finds all unique objects matches in the overlap between two images.
		// For coadd images, we use fMatchImages_coadd; for red or remap images, we use fMatchImages...
		String query1;
		if (imageType.equalsIgnoreCase("coadd")) {
			query1 = "SELECT * FROM table(fMatchImages_coadd( ?, ?, 0.032))"; 
		} else {
			query1 = "SELECT * FROM table(fMatchImages( ?, ?, 0.75, 1.0, 0, 0.032, 'magerr_aper_3', 0.10))";
		}
		
		PreparedStatement st1 = db.prepareStatement(query1);
		if (verbose > 0) {
			System.out.println("query1 = " + query1);
			System.out.println("");
		}
		
		//For non-coadd queries...
		String query2 = "SELECT ra,dec FROM objects_all WHERE object_id=?";
		PreparedStatement st2 = db.prepareStatement(query2);
		if (verbose > 0) {
			System.out.println("query2 = " + query2);
			System.out.println("");
		}
		
		
		// We want to keep track of the images, their RAs and DECs, and their tilenames...
		ArrayList imageidList  = new ArrayList();
		ArrayList raList       = new ArrayList();
		ArrayList decList      = new ArrayList();
		ArrayList tilenameList = new ArrayList();

		// Execute query0...
		Statement st0 = db.createStatement();
		ResultSet rs0 = st0.executeQuery(query0);

		// Loop through the results of query0...
		while (rs0.next()) {
			int imageid = rs0.getInt("id");
			double ra = rs0.getDouble("ra");
			double dec = rs0.getDouble("dec");
			double equinox = rs0.getDouble("equinox");
			String tilename = rs0.getString("tilename");
			
			System.out.println(imageid + "\t" + ra + "\t" + dec + "\t" + equinox + "\t" + tilename);

			// If this is the first time we have run across this image, add its
			// imageid to the imageidList, its RA and DEC to the raList and decList,
			// and its tilename to the tilenameList...
			if (imageidList.contains(imageid) == false) {
				imageidList.add(new Integer(imageid));
				raList.add(new Double(ra));
				decList.add(new Double(dec));
				tilenameList.add(tilename);
			} 
			
		}

		rs0.close();
		st0.close();

		int imageidListSize = imageidList.size();

		// If the list contains the image "referenceImageID", tag that imageid as the one against
		// which to calibrate all the other images; if there is no "referenceImageID" in the 
		// imageidList, choose the first image in imageidList as the reference image.
		if (imageidList.contains((Integer) referenceImageID)) {
			System.out.println("Using " + referenceImageID + " as the reference image.");
		} else {
			System.out.println("Reference image " + referenceImageID + " not found...");
			referenceImageID = (Integer) imageidList.get(0);
			System.out.println("Using " + referenceImageID + " as the reference image, instead.");
		}
		
		System.out.println("");
		
		//Open up the output file...
		File outputFile = new File(outputFileName);
		FileWriter writer = new FileWriter(outputFile);
		
		//Write header comments into the output file...
		writer.write("# Reference image = " + referenceImageID + "\n");
		writer.write("#\n");
		writer.write("# Column  1:  regionid1        : id of region 1\n");
		writer.write("# Column  2:  regionRaCenDeg1  : ra of center of region 1 in degrees (can be a dummy variable)\n");
		writer.write("# Column  3:  regionDecCenDeg1 : dec of center of region 1 in degrees (can be a dummy variable)\n");
		writer.write("# Column  4:  regionQuality1   : quality of region 1 (1=good/do not solve for zeropoint; 0=bad/solve for zeropoint)\n");
		writer.write("# Column  5:  starid1          : id of star 1 in star1-star2 match\n");
		writer.write("# Column  6:  raDeg1           : ra of star 1 in degrees\n");
		writer.write("# Column  7:  decDeg1          : dec of star 1 in degrees\n");
		writer.write("# Column  8:  mag1             : measured magnitude of star 1\n");
		writer.write("# Column  9:  magErr1          : measured magnitude error of star 1\n");
		writer.write("# Column 10:  regionid2        : id of region 2\n");
		writer.write("# Column 11:  regionRaCenDeg2  : ra of center of region 2 in degrees (can be a dummy variable)\n");
		writer.write("# Column 12:  regionDecCenDeg2 : dec of center of region 2 in degrees (can be a dummy variable)\n");
		writer.write("# Column 13:  regionQuality2   : quality of region 2 (1=good/do not solve for zeropoint; 0=bad/solve for zeropoint)\n");
		writer.write("# Column 14:  starid2          : id of star 2 in star1-star2 match\n");
		writer.write("# Column 15:  raDeg2           : ra of star 2 in degrees\n");
		writer.write("# Column 16:  decDeg2          : dec of star 2 in degrees\n");
		writer.write("# Column 17:  mag2             : measured magnitude of star 2\n");
		writer.write("# Column 18:  magErr2          : measured magnitude error of star 2\n");
		writer.write("# Column 19:  sepArcsec12      : separation between star 1 and star 2 in arcsec\n");
		writer.write("#\n");
		writer.write("# (1)          (2)        (3)       (4)       (5)       (6)        (7)         (8)         (9)      (10)          (11)       (12)     (13)      (14)       (15)       (16)        (17)       (18)     (19)\n");

		if (verbose > 0) {
			//output the header comments to the screen...
			System.out.print("# Reference image = " + referenceImageID + "\n");
			System.out.print("#\n");
			System.out.print("# Column  1:  regionid1        : id of region 1\n");
			System.out.print("# Column  2:  regionRaCenDeg1  : ra of center of region 1 in degrees (can be a dummy variable)\n");
			System.out.print("# Column  3:  regionDecCenDeg1 : dec of center of region 1 in degrees (can be a dummy variable)\n");
			System.out.print("# Column  4:  regionQuality1   : quality of region 1 (1=good/do not solve for zeropoint; 0=bad/solve for zeropoint)\n");
			System.out.print("# Column  5:  starid1          : id of star 1 in star1-star2 match\n");
			System.out.print("# Column  6:  raDeg1           : ra of star 1 in degrees\n");
			System.out.print("# Column  7:  decDeg1          : dec of star 1 in degrees\n");
			System.out.print("# Column  8:  mag1             : measured magnitude of star 1\n");
			System.out.print("# Column  9:  magErr1          : measured magnitude error of star 1\n");
			System.out.print("# Column 10:  regionid2        : id of region 2\n");
			System.out.print("# Column 11:  regionRaCenDeg2  : ra of center of region 2 in degrees (can be a dummy variable)\n");
			System.out.print("# Column 12:  regionDecCenDeg2 : dec of center of region 2 in degrees (can be a dummy variable)\n");
			System.out.print("# Column 13:  regionQuality2   : quality of region 2 (1=good/do not solve for zeropoint; 0=bad/solve for zeropoint)\n");
			System.out.print("# Column 14:  starid2          : id of star 2 in star1-star2 match\n");
			System.out.print("# Column 15:  raDeg2           : ra of star 2 in degrees\n");
			System.out.print("# Column 16:  decDeg2          : dec of star 2 in degrees\n");
			System.out.print("# Column 17:  mag2             : measured magnitude of star 2\n");
			System.out.print("# Column 18:  magErr2          : measured magnitude error of star 2\n");
			System.out.print("# Column 19:  sepArcsec12      : separation between star 1 and star 2 in arcsec\n");
			System.out.print("#\n");
			System.out.print("# (1)          (2)        (3)           (4)       (5)        (6)         (7)         (8)       (9)          (10)       (11)         (12)       (13)       (14)        (15)       (16)     (17)       (18)     (19)\n");
		}
		
		String magName1;
		String magErrName1;
		String flagsName1;
		String stellarityName1;
		String magName2;
		String magErrName2;
		String flagsName2;
		String stellarityName2;
		
		if (imageType.equalsIgnoreCase("coadd")) {
			//coadd images:
			//Generate the names of the fields that we want to extract from the fMatchImages_coadd query...
			magName1    = "MAG_APER5_"+filter+"_1";
			magErrName1 = "MAGERR_APER5_"+filter+"_1";
			flagsName1   = "FLAGS_"+filter+"_1";
			stellarityName1 = "CLASS_STAR_"+filter+"_1";
			magName2    = "MAG_APER5_"+filter+"_2";
			magErrName2 = "MAGERR_APER5_"+filter+"_2";
			flagsName2   = "FLAGS_"+filter+"_2";
			stellarityName2 = "CLASS_STAR_"+filter+"_2";
		} else {
			//red or remap images:
			//Generate the names of the fields that we want to extract from the fMatchImages query...
			magName1    = "MAG_APER_5_1";
			magErrName1 = "MAGERR_APER_5_1";
			flagsName1   = "FLAGS_1";
			stellarityName1 = "CLASS_STAR_1";
			magName2    = "MAG_APER_5_2";
			magErrName2 = "MAGERR_APER_5_2";
			flagsName2   = "FLAGS_2";
			stellarityName2 = "CLASS_STAR_2";
		}
		
		// Loop over all the image pairs...
		for (int i=0; i<imageidListSize-1; i++) {
			
			// Grab the imageid, ra, dec, and tilename for this image from 
			// from the relevant ArrayLists...
			int imageid_i     = (Integer) imageidList.get(i);
			double ra_i       = (Double) raList.get(i);
			double dec_i      = (Double) decList.get(i);
			String tilename_i = (String) tilenameList.get(i);
			
			// If this image is the reference image, be sure to tag it as such...
			int referenceImageFlag_i = 0;
			if (imageid_i==referenceImageID) {
				referenceImageFlag_i = 1;
			}
			
			for (int j=i+1; j < imageidListSize; j++) {
			
				// Grab the imageid, ra, dec, and tilename for this image from 
				// from the relevant ArrayLists...
				int imageid_j     = (Integer) imageidList.get(j);
				double ra_j       = (Double) raList.get(j);
				double dec_j      = (Double) decList.get(j);
				String tilename_j = (String) tilenameList.get(j);

				// If this image is the reference image, be sure to tag it as such...
				int referenceImageFlag_j = 0;
				if (imageid_j==referenceImageID) {
					referenceImageFlag_j = 1;
				}

				// Calculate the separation in degrees between image i and image j...
				double sepDeg = this.getSepDeg(ra_i, dec_i, ra_j, dec_j);
				
				if (verbose > 2) {
					System.out.println(imageid_i + "\t" + imageid_j + "\t" + sepDeg);
				}
				
				// If the separation between the two images is greater than the separation tolerance,  
				// they should not overlap... skip...
				if (sepDeg > sepTolerDeg) {

					if (verbose > 2) {
						System.out.println("    sepDeg = " + sepDeg + " > " + sepTolerDeg + " deg...  skipping...");
					}
					
				} else {

					int ntot = 1;
					// Prepare and then execute query1 for this pair of images...
					st1.setInt(1, imageid_i);	
					st1.setInt(2, imageid_j);
					ResultSet rs1 = st1.executeQuery();

					// Loop through the results of query1 for this pair of images...
					while (rs1.next()) {
						
						int starid_i;
						int starid_j;
						double starRa_i;
						double starDec_i;
						double starRa_j;
						double starDec_j;
						if (imageType.equalsIgnoreCase("coadd")) {
						//coadd images:
							starid_i = rs1.getInt("COADD_OBJECTS_ID_1");
							starid_j = rs1.getInt("COADD_OBJECTS_ID_2");
							starRa_i = rs1.getDouble("ALPHA_J2000_1");
							starDec_i = rs1.getDouble("DELTA_J2000_1");
							starRa_j = rs1.getDouble("ALPHA_J2000_2");
							starDec_j = rs1.getDouble("DELTA_J2000_2");
						} else {
						//red or remap images:
							starid_i = rs1.getInt("OBJECT_ID_1");
							starid_j = rs1.getInt("OBJECT_ID_2");
							st2.setInt(1,starid_i);
							ResultSet rs2 = st2.executeQuery();
							rs2.next();
							starRa_i = rs2.getDouble("RA");
							starDec_i = rs2.getDouble("DEC");
							rs2.close();
							st2.setInt(1,starid_j);
							rs2 = st2.executeQuery();
							rs2.next();
							starRa_j = rs2.getDouble("RA");
							starDec_j = rs2.getDouble("DEC");
							rs2.close();
						}
						
						double mag_i = rs1.getDouble(magName1);
						double mag_j = rs1.getDouble(magName2);
						double magErr_i = rs1.getDouble(magErrName1);
						double magErr_j = rs1.getDouble(magErrName2);
						int flags_i = rs1.getInt(flagsName1);
						int flags_j = rs1.getInt(flagsName2);
						double stellarity_i = rs1.getDouble(stellarityName1);
						double stellarity_j = rs1.getDouble(stellarityName1);
						double sepArcsec = 3600.*this.getSepDeg(starRa_i, starDec_i, starRa_j, starDec_j);
						
						if (flags_i == 0 && flags_j == 0 && 
								stellarity_i > 0.50 && stellarity_j > 0.50 && 
								mag_i > magLo && mag_i < magHi &&
								mag_j > magLo && mag_j < magHi &&
								magErr_i > 0. && magErr_i < 0.10 &&
								magErr_j > 0. && magErr_j < 0.10)  {
						
							//This formatted input requireds java 1.5 or higher...
							String outputLine = String.format("%1$-10d   %2$8.4f   %3$8.4f   %4$3d   %5$10d   %6$8.4f   %7$8.4f   %8$8.3f   %9$8.3f    %10$-10d   %11$8.4f   %12$8.4f   %13$3d   %14$10d   %15$8.4f   %16$8.4f   %17$8.3f   %18$8.3f  %19$7.3f   \n", 
									imageid_i, ra_i, dec_i, referenceImageFlag_i, starid_i, starRa_i, starDec_i, mag_i, magErr_i, imageid_j, ra_j, dec_j, referenceImageFlag_j, starid_j, starRa_j, starDec_j, mag_j, magErr_j, sepArcsec);

							// Output outputLine to file...
							writer.write(outputLine);

							if (verbose > 0) {
								// Output outputLine to screen...
								System.out.print(ntot + "\t" + outputLine);
							}

							ntot++;
							
						}
					
					}
					
					rs1.close();
					
				}
			
			}
			
			if (verbose > 2) {
				System.out.println("");
			}
			
		}
		
		st1.close();
		
		writer.close();

		// Close up shop...
		
		if (verbose > 0) {
			System.out.println("\nResults have been written to the file " + outputFileName + "\n");
		}
		if (verbose > 0) {
			System.out.println("That's all, folks!");
			System.out.println("");
		}

	}

	/**
	 * calculate separation between two positions on the celestial sphere
	 * 
	 * @param ra1,  ra  of first field (in deg)
	 * @param dec1, dec of first field (in deg)
	 * @param ra2,  ra  of second field (in deg)
	 * @param dec2, dec of second field (in deg)
	 * 
	 * @return sepDeg, separation in degrees
	 *
	 */
	public double getSepDeg(double raDeg1, double decDeg1, double raDeg2, double decDeg2) {

		//Conversion factors...
		double deg2Rad = Math.PI/180.;
		double rad2Deg = 180./Math.PI;
		
		//Convert RAs, DECs to radians
		double raRad1  = deg2Rad*raDeg1;
		double decRad1 = deg2Rad*decDeg1;
		double raRad2  = deg2Rad*raDeg2;
		double decRad2 = deg2Rad*decDeg2;
		
		//Calculate the separation between the two positions
		double cosSepRad = Math.sin(decRad1)*Math.sin(decRad2) + 
			Math.cos(decRad1)*Math.cos(decRad2)*Math.cos(raRad1-raRad2);
		
		double sepRad = Math.acos(cosSepRad);
		double sepDeg = rad2Deg*sepRad;
		
		return sepDeg;
		
	}
	
	//Getters and Setters for private variables...
	
	/**
	 * @return Returns the sqlDriver.
	 */
	public String getSqlDriver() {
		return sqlDriver;
	}

	/**
	 * @param sqlDriver
	 *            The sqlDriver to set.
	 */
	public void setSqlDriver(String sqlDriver) {
		this.sqlDriver = sqlDriver;
	}

	/**
	 * @return Returns the dbName.
	 */
	public String getDbName() {
		return dbName;
	}

	/**
	 * @param dbName
	 *            The dbName to set.
	 */
	public void setDbName(String dbName) {
		this.dbName = dbName;
	}


	/**
	 * @return Returns the url.
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url
	 *            The url to set.
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return Returns the user.
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @param user
	 *            The user to set.
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * @return Returns the ccdid.
	 */

	/**
	 * @return Returns the filter.
	 */
	public String getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            The filter to set.
	 */
	public void setFilter(String filter) {
		this.filter = filter;
	}

	/**
	 * @return Returns the filterList.
	 */
	public String[] getFilterList() {
		return filterList;
	}

	/**
	 * @param filterList
	 *            The filterList to set.
	 */
	public void setFilterList(String[] filterList) {
		this.filterList = filterList;
	}

	/**
	 * @return Returns the verbose.
	 */
	public int getVerbose() {
		return verbose;
	}

	/**
	 * @param verbose
	 *            The verbose to set.
	 */
	public void setVerbose(int verbose) {
		this.verbose = verbose;
	}

	/**
	 * @return Returns the date.
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * @param date
	 *            The date to set.
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	/**
	 * @return Returns the passwd.
	 */
	public String getPasswd() {
		return passwd;
	}

	/**
	 * @param passwd
	 *            The passwd to set.
	 */
	public void setPasswd(String passwd) {
		this.passwd = passwd;
	}


	/**
	 * @return Returns the magHi.
	 */
	public double getMagHi() {
		return magHi;
	}

	/**
	 * @param magHi
	 *            The magHi to set.
	 */
	public void setMagHi(double magHi) {
		this.magHi = magHi;
	}

	/**
	 * @return Returns the magLo.
	 */
	public double getMagLo() {
		return magLo;
	}

	/**
	 * @param magLo
	 *            The magLo to set.
	 */
	public void setMagLo(double magLo) {
		this.magLo = magLo;
	}


	/**
	 * @return Returns the imageNameFilter.
	 */
	public String getImageNameFilter() {
		return imageNameFilter;
	}

	/**
	 * @param imageNameFilter
	 *            The imageNameFilter to set.
	 */
	public void setImageNameFilter(String imageNameFilter) {
		this.imageNameFilter = imageNameFilter;
	}

	/**
	 * @return Returns the imageType.
	 */
	public String getImageType() {
		return imageType;
	}

	/**
	 * @param imageType
	 *            The imageType to set.
	 */
	public void setImageType(String imageType) {
		this.imageType = imageType;
	}

	/**
	 * @return Returns the runiddesc.
	 */
	public String getRun() {
		return run;
	}

	/**
	 * @param runiddesc
	 *            The runiddesc to set.
	 */
	public void setRun(String run) {
		this.run = run;
	}

	public String getOutputFileName() {
		return outputFileName;
	}

	public void setOutputFileName(String outputFileName) {
		this.outputFileName = outputFileName;
	}

	public int getReferenceImageID() {
		return referenceImageID;
	}

	public void setReferenceImageID(int referenceImageID) {
		this.referenceImageID = referenceImageID;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public double getDecMax() {
		return decMax;
	}

	public void setDecMax(double decMax) {
		this.decMax = decMax;
	}

	public double getDecMin() {
		return decMin;
	}

	public void setDecMin(double decMin) {
		this.decMin = decMin;
	}

	public double getRaMax() {
		return raMax;
	}

	public void setRaMax(double raMax) {
		this.raMax = raMax;
	}

	public double getRaMin() {
		return raMin;
	}

	public void setRaMin(double raMin) {
		this.raMin = raMin;
	}

	public String[] getImageTypeList() {
		return imageTypeList;
	}

	public void setImageTypeList(String[] imageTypeList) {
		this.imageTypeList = imageTypeList;
	}

	public double getSepTolerDeg() {
		return sepTolerDeg;
	}

	public void setSepTolerDeg(double sepTolerDeg) {
		this.sepTolerDeg = sepTolerDeg;
	}

}
