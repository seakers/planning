import seakers.planning.Planner;

import java.util.HashMap;
import java.util.Map;

public class PlannerExec {
    public static void main(String[] args) {
        Map<String,String> allSettings = new HashMap<>();
        allSettings.put("crosslinkEnabled","true");
        allSettings.put("downlinkEnabled","true");
        Map<String,String> downSettings = new HashMap<>();
        downSettings.put("crosslinkEnabled","false");
        downSettings.put("downlinkEnabled","true");
        double allSmartSum = 0;
        double allNaiveSum = 0;
        double allSmartCount = 0;
        double allNaiveCount = 0;
        double downSmartSum = 0;
        double downNaiveSum = 0;
        double downSmartCount = 0;
        double downNaiveCount = 0;
        int numSims = 10;
        for (int i = 0; i < numSims; i++) {
            System.out.println("All");
            Planner allPlanner = new Planner(downSettings);
            System.out.println("Down");
            Planner downPlanner = new Planner(downSettings);
            allSmartSum += allPlanner.getResults().get("smart");
            allNaiveSum += allPlanner.getResults().get("naive");
            allSmartCount += allPlanner.getResults().get("chl count smart");
            allNaiveCount += allPlanner.getResults().get("chl count naive");
            downSmartSum += downPlanner.getResults().get("smart");
            downNaiveSum += downPlanner.getResults().get("naive");
            downSmartCount += downPlanner.getResults().get("chl count smart");
            downNaiveCount += downPlanner.getResults().get("chl count naive");
        }
        System.out.println("allSmart avg: "+allSmartSum/numSims);
        System.out.println("allNaive avg: "+allNaiveSum/numSims);
        System.out.println("allSmartCount avg: "+allSmartCount/numSims);
        System.out.println("allNaiveCount avg: "+allNaiveCount/numSims);
        System.out.println("downSmart avg: "+downSmartSum/numSims);
        System.out.println("downNaive avg: "+downNaiveSum/numSims);
        System.out.println("downSmartCount avg: "+downSmartCount/numSims);
        System.out.println("downNaiveCount avg: "+downNaiveCount/numSims);
    }
}
