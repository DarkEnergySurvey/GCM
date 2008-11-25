package gov.fnal.eag.dtucker.desPhotoStds;

import gov.fnal.eag.dtucker.desPhotoStds.GlobalZPSolverDC4;
import java.sql.SQLException;
import java.util.Date;

/**
* GlobalZPSolverRunDC4.java   The DES Collaboration  2008
* This class instantiates an object of the GlobalZPSolverDC4 class,
* sets values for certain GlobalZPSolverDC4 parameters, and invokes 
* the GlobalZPSolverDC4 "solve" method.  The GlobalZPSolverDC4 
* "solve" method defines methods for solving the region-to-region 
* zeropoint offsets for a set of overlapping regions using the 
* methodology of Glazebrook et al. (1994).
* 
* @author dtucker
*/

public class GlobalZPSolverRunDC4 {

    public static void main (String[] args) {

    	System.out.println("GlobalZPSolverRunDC4");
    	
        System.out.print("arglist:  ");
        for (int i=0; i< args.length; i++) {
            System.out.print(args[i] + " ");
        }
        System.out.print("\n");
        
        GlobalZPSolverDC4 globalZP = new GlobalZPSolverDC4();

        // Process any arguments passed to the main method.
        // Assume they are ordered "starMatchFileName outputFileName verbose".
        if (args.length > 0) {
            String starMatchFileName = args[0];
            globalZP.setStarMatchFileName(starMatchFileName);   
            System.out.println("starMatchFileName="+starMatchFileName);
        } 
        if (args.length > 1) {
            String outputFileName = args[1];
            globalZP.setOutputFileName(outputFileName);   
            System.out.println("outputFileName="+outputFileName);
        } 
         if (args.length > 2) {
            int verbose = Integer.parseInt(args[2]);
            globalZP.setVerbose(verbose);   
            System.out.println("verbose="+verbose);
        } 

        Date date = new Date();
        globalZP.setDate(date);
        
        try {
            globalZP.solve();
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
