package gov.fnal.eag.dtucker.desPhotoStds;

import gov.fnal.eag.dtucker.desPhotoStds.GlobalZPSolverPrepDC6;
import java.sql.SQLException;
import java.util.Date;


/**
 * GlobalZPSolverPrepRunDC6.java   The DES Collaboration  2011
 * This class instantiates an object of the GlobalZPSolverPrepDC6 class,
 * sets values for certain GlobalZPSolverPrepDC6 parameters, and invokes the 
 * GlobalZPSolverPrepDC6 "solve" method.  The GlobalZPSolverPrepDC6 "solve" method
 * creates an ASCII file containing a table of the unique star matches  
 * from all the overlapping tiles in a given coadd.  This file is input
 * for the GlobalZPSolverRun command, which solves for region-to-region
 * zeropoint offsets for a set of overlapping regions.  
 * 
 * @author dtucker
 */
public class GlobalZPSolverPrepRunDC6 {


    public static void main (String[] args) {

    	System.out.println("GlobalZPSolverPrepRunDC6");
    	
        System.out.print("arglist:  ");
        for (int i=0; i< args.length; i++) {
            System.out.print(args[i] + " ");
        }
        System.out.print("\n");
        
        GlobalZPSolverPrepDC6 gzpsPrep = new GlobalZPSolverPrepDC6();

        // Process any arguments passed to the main method.
        // Assume they are ordered "inputFileName outputFileName referenceImageID".
        if (args.length > 0) {
            String imageidListFileName = args[0];
            gzpsPrep.setImageidListFileName(imageidListFileName);   
            System.out.println("imageidListFileName="+imageidListFileName);
        } 
        if (args.length > 1) {
            String outputFileName = args[1];
            gzpsPrep.setOutputFileName(outputFileName);   
            System.out.println("outputFileName="+outputFileName);
        } 
        if (args.length > 2) {
            String project = args[2];
            gzpsPrep.setProject(project);   
            System.out.println("project="+project);
        } 
        if (args.length > 3) {
            int referenceImageID = Integer.parseInt(args[3]);
            gzpsPrep.setReferenceImageID(referenceImageID);   
            System.out.println("referenceImageID="+referenceImageID);
        } 

        gzpsPrep.setSqlDriver("oracle.jdbc.driver.OracleDriver");

        Date date = new Date();
        gzpsPrep.setDate(date);
        
        if (true) {
        	try {
        		gzpsPrep.solve();
        	} catch (ClassNotFoundException e) {
        		// TODO Auto-generated catch block
        		e.printStackTrace();
        	} catch (SQLException e) {
        		// TODO Auto-generated catch block
        		e.printStackTrace();
        	} catch (Exception e) {
        		// TODO Auto-generated catch block
        		e.printStackTrace();
        	}
        }

    }

}
