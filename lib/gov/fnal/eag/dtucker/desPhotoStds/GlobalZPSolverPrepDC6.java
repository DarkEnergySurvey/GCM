package gov.fnal.eag.dtucker.desPhotoStds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import cern.colt.list.DoubleArrayList;


/**
 * GlobalZPSolverPrepDC6.java   The DES Collaboration  2011
 * This class prepares an ASCII table of unique star matches 
 * from all the overlapping images in a given imageid list.  
 * This table is input for the GlobalZPSolverRunDC6 command, 
 * which solves for region-to-region zeropoint offsets for a 
 * set of overlapping images.  
 * 
 * @author dtucker
 */

public class GlobalZPSolverPrepDC6 {

	// Instance variables dealing with the SQL database	
	private static String desdmFileName = ".desdm";
	private static String sqlDriver = "oracle.jdbc.driver.OracleDriver";
	private static String urlPrefix = "jdbc:oracle:thin:@//"; 
	private static String urlPort = "1521";

	// Instance variables dealing with this run of GlobalZPSolverPrepDC6	
	private Date date             = new Date();
	private String imageidListFileName = "/Users/dtucker/Desktop/SqlQuery/BCSSingleEpochImageList_5h30m_2_g.txt";
	private String outputFileName = "GlobalZPSolverPrepDC6Temp.dat";
	private int referenceImageID  = -1;

	// Instance variables dealing with the observed data to be calibrated
	private String project     = "DES";
	private double sepTolerDeg = 0.3;

	// General instance variables... 
	private int verbose = 3;

	
	public void solve() throws Exception {

		System.out.println("\n\nGlobalZPSolverPrepDC6");

		if (verbose > 0) {
			System.out.println("");
			System.out.println("The beginning...");
			System.out.println("");
		}


		// Grab db connection info from the .desdm file...
		String dbUser = "";
		String dbPasswd = "";
		String dbServer = "";
		String dbName = "";

		File desdmFile = new File(System.getProperty("user.home"),desdmFileName);
		desdmFile.exists(); 
		desdmFile.canRead();
		String desdmFullFileName = desdmFile.getCanonicalPath();

		if (!desdmFile.exists()) {
			System.out.println(desdmFullFileName + " does not exist!  Exiting now!");
			return;
		}

		if (!desdmFile.canRead()) {
			System.out.println(desdmFullFileName + " can not be read!  Exiting now!");
			return;
		}

		FileReader fileReader = new FileReader(desdmFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;

		while ((line = reader.readLine()) != null) {

			if (line.length() == 0) {
				continue;
			}
			if (line.charAt(0) == '#') {
				continue;
			}

			StringTokenizer st = new StringTokenizer(line);
			int nTokens = st.countTokens();
			if (nTokens != 2) {
				continue;
			}

			String param = st.nextToken();
			String value = st.nextToken();

			if (param.equals("DB_USER")) {
				dbUser = value;
			} else if (param.equals("DB_PASSWD")) {
				dbPasswd = value;
			} else if (param.equals("DB_SERVER")) {
				dbServer = value;
			} else if (param.equals("DB_NAME")) {
				dbName = value;
			}

			//System.out.println(line);

		}

		reader.close();


		// Establish connection to database
		if (verbose > 0) {
			System.out.println("# Establishing connection to database.");
			System.out.println("");
		}
		Class.forName(sqlDriver);
		String url = urlPrefix+dbServer+":"+urlPort+"/";
		String fullURL = url + dbName;
		Connection db = DriverManager.getConnection(fullURL, dbUser, dbPasswd);
		

		//Prepare set of queries to be used...
		
		//This query grabs the ra, dec for each image...
		String query0;
		query0 = "SELECT im.ra,im.dec,im.exposureid,exp.telra,exp.teldec FROM image im, exposure exp WHERE im.exposureid=exp.id and im.id=?";
		PreparedStatement st0 = db.prepareStatement(query0);
		if (verbose > 0) {
			System.out.println("query0 = " + query0);
			System.out.println("");
		}

		String query1;
		query1 = "SELECT * FROM objects where imageid=? and flags <= 0 and spread_model < 0.10 and (magerr_psf > 0.00 and magerr_psf < 0.05)";
		PreparedStatement st1 = db.prepareStatement(query1);
		if (verbose > 0) {
			System.out.println("query1 = " + query1);
			System.out.println("");
		}
				

		//Loop through the imageid list file, reading in the imageids into an ArrayList...

		//String imageidListFileName = "/Users/dtucker/Desktop/SqlQuery/BCSSingleEpochImageList_5h30m_2_g.txt";

		ArrayList imageidList = new ArrayList();
		
    	File imageidListFile = new File(imageidListFileName);
 
    	if (imageidListFile.exists() == false || imageidListFile.canRead() == false) {
    		if (verbose > 0) {
   				System.out.println("** "+ imageidListFileName + " either does not exist or cannot be read ** \n");
   			}
   			System.exit(2);
    	}
    	
    	//Read in image list file...
    	if (verbose > 0) {
    		System.out.println("Reading contents of imageid list file " + imageidListFileName + "...");
    	}

    	fileReader = new FileReader(imageidListFile);
    	reader = new BufferedReader(fileReader);
    		
    	while ((line = reader.readLine()) != null) {
    			
    		if (line.length() >= 1 && line.charAt(0) != '#' ) {
    				
    			if (verbose > 1) {
    				//System.out.println(line);
    			}
    			// ***Assumes a CSV file***...
     			StringTokenizer st = new StringTokenizer(line,",");
     			int nTokens = st.countTokens();
     			if (nTokens >= 1) {
     				int imageid = Integer.parseInt(st.nextToken());
     				if (imageidList.contains(imageid) == false) {
     					imageidList.add(new Integer(imageid));
     					
     				}
     			
     			}		
    			   			
    		}

    	}
    	
    	reader.close();
    	
		int imageidListSize = imageidList.size();
			
		
		// Grab and record image ra,dec, and exposure id,telra,teldec for each image in imageidList...

		Map imageRaMap      = new TreeMap();
		Map imageDecMap     = new TreeMap();
		Map imageExpMap     = new TreeMap();

		ArrayList expidList = new ArrayList();
		Map expTelRaMap     = new TreeMap();
		Map expTelDecMap    = new TreeMap();
		Map expImageListMap = new TreeMap();

		
		for (int i=0; i<imageidListSize; i++) {

			// Prepare and then execute query0 for this image...
			int imageid = (Integer) imageidList.get(i); 
			st0.setInt(1, imageid);	
			ResultSet rs0 = st0.executeQuery();
				
			// We expect 1 row returned, but there may be cases
			// in which no rows are returned.  If the latter, 
			// remove imageid from the imageidList...
			double imageRa;
			double imageDec;
			int    expid;
			double expTelRa;
			double expTelDec;
			
			if (!rs0.next()) {  
				//double-check this:
				System.out.println(imageid + "\t Cannot find image in database...  Removing from analysis..."); 
		        int index = imageidList.indexOf((Integer) imageid);
		        imageidList.remove(index);

			}  else {  
				
				imageRa = rs0.getDouble("ra");
				imageDec = rs0.getDouble("dec");

				expid = rs0.getInt("exposureid");
				expTelRa = rs0.getDouble("telra");
				expTelDec = rs0.getDouble("teldec");

				// Sometimes, the TELRA, TELDEC for the exposure time is completely off;
				// so, in these cases, set expTelRa, expTelDec to this images ra, dec...
				if (expTelRa > 360 || expTelRa < -360) {
					expTelRa = imageRa;
				}
				if (expTelDec > 90 || expTelDec < -90) {
					expTelDec = imageDec;
				}
						    
				imageRaMap.put(imageid,(Double) imageRa);
				imageDecMap.put(imageid,(Double) imageDec);
				imageExpMap.put(imageid,(Integer) expid);

				if (expidList.contains(expid) == false) {
					expidList.add(new Integer(expid));
					expTelRaMap.put(expid,(Double) expTelRa);
					expTelDecMap.put(expid,(Double) expTelDec);
					ArrayList expImageList = new ArrayList();
					expImageList.add(new Integer(imageid));
					expImageListMap.put(expid, expImageList);	
				} else {
					ArrayList expImageList = (ArrayList) expImageListMap.get(expid);
					expImageList.add(new Integer(imageid));
					expImageListMap.put(expid, expImageList);
				}

				System.out.println(imageid + "\t" + imageRa + "\t" + imageDec + "\t" + expid + "\t" + expTelRa + "\t" + expTelDec);

			}
			
			rs0.close();
		
		}
		
		st0.close();
		
		int expidListSize = expidList.size();
		
		System.out.println("imageidListSize  = " + imageidListSize);
		System.out.println("imageRaMapSize   = " + imageRaMap.size());
		System.out.println("imageDecMapSize  = " + imageDecMap.size());
		System.out.println("expidListSize    = " + expidListSize);
		System.out.println("expTelRaMapSize  = " + expTelRaMap.size());
		System.out.println("expTelDecMapSize = " + expTelDecMap.size());

		
		if (verbose> 2) {
			System.out.println("");
			for (int i=0; i<expidListSize; i++) {
				int expid = (Integer) expidList.get(i);
				ArrayList expImageList = (ArrayList) expImageListMap.get(expid);
				for (int ii=0; ii<expImageList.size(); ii++) {
					int imageid = (Integer) expImageList.get(ii);
					System.out.println(expid + "\t" + imageid);
				}
				System.out.println("");
			}
		}
		
		//System.exit(3);

		
		
		
		
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
		writer.write("# (1)          (2)        (3)       (4)         (5)          (6)        (7)         (8)        (9)       (10)          (11)       (12)    (13)         (14)         (15)       (16)       (17)        (18)     (19)\n");

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
			System.out.print("# (1)          (2)        (3)            (4)          (5)          (6)         (7)         (8)       (9)          (10)       (11)         (12)       (13)          (14)           (15)       (16)     (17)       (18)     (19)\n");
		}
		
		String magName1;
		String magErrName1;
		String flagsName1;
		String stellarityName1;
		String magName2;
		String magErrName2;
		String flagsName2;
		String stellarityName2;
		
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

		
		// Loop over all the exposure pairs...
		for (int i=0; i<expidListSize-1; i++) {
			
			int expid_i              = (Integer) expidList.get(i);
			double expTelRa_i        = (Double) expTelRaMap.get((Integer) expid_i);
			double expTelDec_i       = (Double) expTelDecMap.get((Integer) expid_i);
			ArrayList expImageList_i = (ArrayList) expImageListMap.get(expid_i);

			for (int j=i+1; j < expidListSize; j++) {
				
				int expid_j              = (Integer) expidList.get(j);
				double expTelRa_j        = (Double) expTelRaMap.get((Integer) expid_j);
				double expTelDec_j       = (Double) expTelDecMap.get((Integer) expid_j);
				ArrayList expImageList_j = (ArrayList) expImageListMap.get(expid_j);
				
				double expSepDeg = this.getSepDeg(expTelRa_i, expTelDec_i, expTelRa_j, expTelDec_j);
								
				if (expSepDeg < 1.0) {
					
					System.out.println(expid_i + "\t" + expid_j + "\t" + expSepDeg + ":");
					
					for (int ii=0; ii<expImageList_i.size()-1; ii++) {
						
						// Grab the imageid and ra, dec for this image from 
						// from the relevant ArrayLists...
						int imageid_ii     = (Integer) expImageList_i.get(ii);
						double imageRa_ii  = (Double) imageRaMap.get((Integer) imageid_ii);
						double imageDec_ii = (Double) imageDecMap.get((Integer) imageid_ii);
						
						// If this image is the reference image, be sure to tag it as such...
						int referenceImageFlag_ii = 0;
						if (imageid_ii==referenceImageID) {
							referenceImageFlag_ii = 1;
						}

						
						for (int jj=0; jj<expImageList_j.size()-1; jj++) {
							
							// Grab the imageid and ra, dec for this image from 
							// from the relevant ArrayLists...
							int imageid_jj     = (Integer) expImageList_j.get(jj);
							double imageRa_jj  = (Double) imageRaMap.get((Integer) imageid_jj);
							double imageDec_jj = (Double) imageDecMap.get((Integer) imageid_jj);
							
							// If this image is the reference image, be sure to tag it as such...
							int referenceImageFlag_jj = 0;
							if (imageid_jj==referenceImageID) {
								referenceImageFlag_jj = 1;
							}

							
							double imageSepDeg = this.getSepDeg(imageRa_ii, imageDec_ii, imageRa_jj, imageDec_jj);
						
							if (imageSepDeg < sepTolerDeg) {
								
								//System.out.println("   " + imageid_ii + "\t" + imageid_jj + "\t" + imageSepDeg);
																
								ArrayList starIdList1     = new ArrayList();
								ArrayList starRaList1     = new ArrayList();
								ArrayList starDecList1    = new ArrayList();
								ArrayList starMagList1    = new ArrayList();
								ArrayList starMagerrList1 = new ArrayList();
								
								ArrayList starIdList2     = new ArrayList();
								ArrayList starRaList2     = new ArrayList();
								ArrayList starDecList2    = new ArrayList();
								ArrayList starMagList2    = new ArrayList();
								ArrayList starMagerrList2 = new ArrayList();

								int nMatchObjects = 0;
								
								// Prepare and then execute query1 for imageid_i...
								st1.setInt(1, imageid_ii);	
								ResultSet rs1 = st1.executeQuery();
								
								// Loop through the results of query1...
								int objectcount_ii = 0;
								while (rs1.next()) {
									
									long lstarid1 = rs1.getLong("object_id");
									String starid1 = ""+lstarid1+"";
									double ra1 = rs1.getDouble("ra");
									double dec1 = rs1.getDouble("dec");
									double mag1 = rs1.getDouble("mag_psf");
									double magerr1 = rs1.getDouble("magerr_psf");
									
									starIdList1.add(starid1);	
									starRaList1.add(new Double(ra1));
									starDecList1.add(new Double(dec1));
									starMagList1.add(new Double(mag1));
									starMagerrList1.add(new Double(magerr1));
									
									objectcount_ii++;
									
								}
								rs1.close();

								// Prepare and then execute query1 for imageid_j...
								st1.setInt(1, imageid_jj);	
								rs1 = st1.executeQuery();
								
								// Loop through the results of query1...
								int objectcount_jj = 0;
								while (rs1.next()) {
									
									long lstarid2 = rs1.getLong("object_id");
									String starid2 = ""+lstarid2+"";
									double ra2 = rs1.getDouble("ra");
									double dec2 = rs1.getDouble("dec");
									double mag2 = rs1.getDouble("mag_psf");
									double magerr2 = rs1.getDouble("magerr_psf");
									
									starIdList2.add(starid2);	
									starRaList2.add(new Double(ra2));
									starDecList2.add(new Double(dec2));
									starMagList2.add(new Double(mag2));
									starMagerrList2.add(new Double(magerr2));
									
									objectcount_jj++;
									
								}
								rs1.close();
								
								for (int iii=0; iii<objectcount_ii; iii++) {
							
									String starid_iii  = (String) starIdList1.get(iii);
									double starRa_iii  = (Double) starRaList1.get(iii);
									double starDec_iii = (Double) starDecList1.get(iii);
									double starMag_iii = (Double) starMagList1.get(iii);
									double starMagerr_iii = (Double) starMagerrList1.get(iii);

									for (int jjj=0; jjj<objectcount_jj; jjj++) {
										
										String starid_jjj  = (String) starIdList2.get(jjj);
										double starRa_jjj  = (Double) starRaList2.get(jjj);
										double starDec_jjj = (Double) starDecList2.get(jjj);
										double starMag_jjj = (Double) starMagList2.get(jjj);
										double starMagerr_jjj = (Double) starMagerrList2.get(jjj);
										
										// Calculate the separation in degrees between image i and image j...
										double sepArcsec = 3600.*this.getSepDeg(starRa_iii, starDec_iii, starRa_jjj, starDec_jjj);
										
										if (sepArcsec < 2.0) {
											
											//This formatted input requireds java 1.5 or higher...
											//String outputLine = imageid_ii + "\t" +  imageRa_ii + "\t" + imageDec_ii + "\t" + referenceImageFlag_ii + "\t" + starid_iii + "\t" + starRa_iii + "\t" + starDec_iii + "\t" + starMag_iii + "\t" + starMagerr_iii + "\t" + imageid_jj + "\t" + imageRa_jj + "\t" + imageDec_jj + "\t" + referenceImageFlag_jj + "\t" + starid_jjj + "\t" + starRa_jjj + "\t" + starDec_jjj + "\t" + starMag_jjj + "\t" + starMagerr_jjj + "\t" + sepArcsec + "\n";

											String outputLine = String.format("%1$-10d   %2$8.4f   %3$8.4f   %4$3d   %5$14s   %6$8.4f   %7$8.4f   %8$8.3f   %9$8.3f    %10$-10d   %11$8.4f   %12$8.4f   %13$3d   %14$14s   %15$8.4f   %16$8.4f   %17$8.3f   %18$8.3f  %19$7.3f   \n", 
													imageid_ii, imageRa_ii, imageDec_ii, referenceImageFlag_ii, starid_iii, starRa_iii, starDec_iii, starMag_iii, starMagerr_iii, imageid_jj, imageRa_jj, imageDec_jj, referenceImageFlag_jj, starid_jjj, starRa_jjj, starDec_jjj, starMag_jjj, starMagerr_jjj, sepArcsec);

											// Output outputLine to file...
											writer.write(outputLine);
											
											nMatchObjects++;
											
										}
										
									}
									
								}
								
								Date date = new Date();
								System.out.println("   " + imageid_ii + "\t (" + objectcount_ii + ") \t " + imageid_jj + "\t (" + objectcount_jj + ") \t " + imageSepDeg + "\t (" + nMatchObjects +")\t" + date.toString());								
																
							}
							
						}
						
					}
				
					System.out.println("");
					
				}

			}

		}
				
		
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

	public double getSepTolerDeg() {
		return sepTolerDeg;
	}

	public void setSepTolerDeg(double sepTolerDeg) {
		this.sepTolerDeg = sepTolerDeg;
	}

	public static String getDesdmFileName() {
		return desdmFileName;
	}

	public static void setDesdmFileName(String desdmFileName) {
		GlobalZPSolverPrepDC6.desdmFileName = desdmFileName;
	}

	public static String getUrlPrefix() {
		return urlPrefix;
	}

	public static void setUrlPrefix(String urlPrefix) {
		GlobalZPSolverPrepDC6.urlPrefix = urlPrefix;
	}

	public static String getUrlPort() {
		return urlPort;
	}

	public static void setUrlPort(String urlPort) {
		GlobalZPSolverPrepDC6.urlPort = urlPort;
	}

	public String getImageidListFileName() {
		return imageidListFileName;
	}

	public void setImageidListFileName(String imageidListFileName) {
		this.imageidListFileName = imageidListFileName;
	}

}
