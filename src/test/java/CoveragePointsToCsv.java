import org.apache.commons.math3.util.FastMath;
import org.orekit.bodies.GeodeticPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Map;

public class CoveragePointsToCsv {
    public static String filepath;
    public static Map<GeodeticPoint, Double> globalRewardGrid;
    public static void main(String[] args) {
        filepath = "./src/test/resources/plannerData";
        loadRewardGrid();
        try {
            FileWriter csvWriter = new FileWriter("coveragePointsForChlorophyll.csv");
            for (GeodeticPoint gp : globalRewardGrid.keySet()) {
                ArrayList<Double> row = new ArrayList<>();
                row.add(FastMath.toDegrees(gp.getLongitude()));
                row.add(FastMath.toDegrees(gp.getLatitude()));
                String rowData = getListAsCsvString(row);
                csvWriter.append(rowData);
                csvWriter.append("\n");
            }
            csvWriter.flush();
            csvWriter.close();
        } catch (Exception e ) {
            System.out.println(e);
        }

    }

    public static void loadRewardGrid() {
        File directory = new File(filepath);
        try{
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(new File(filepath+"/coveragePoints.dat"));
                ObjectInputStream oi = new ObjectInputStream(fi);

                globalRewardGrid =  (Map<GeodeticPoint,Double>) oi.readObject();

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadObservations: "+e.getMessage());
        }
    }

    public static String getListAsCsvString(ArrayList<Double> list){

        StringBuilder sb = new StringBuilder();
        for(Double str:list){
            if(sb.length() != 0){
                sb.append(",");
            }
            sb.append(Double.toString(str));
        }
        return sb.toString();
    }
}
