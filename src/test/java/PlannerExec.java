import seakers.planning.Simulator;

import java.util.HashMap;
import java.util.Map;

public class PlannerExec {
    public static void main(String[] args) {
        Map<String,String> settings = new HashMap<>();
        settings.put("crosslinkEnabled","false");
        settings.put("downlinkEnabled","true");
        settings.put("downlinkSpeedMbps","1000.1");
        settings.put("cameraOnPower","0.0");
        settings.put("chargePower","5.0");
        settings.put("downlinkOnPower","0.0");
        settings.put("crosslinkOnPower","0.0");
        settings.put("chlBonusReward","100.0");
        settings.put("maxTorque","4e-3");
        settings.put("planner","dumbMcts");
        double smartSum = 0;
        double naiveSum = 0;
        double smartCount = 0;
        double naiveCount = 0;
        int numSims = 1;
        for (int i = 0; i < numSims; i++) {
            Simulator simulator = new Simulator(settings);
            smartSum += simulator.getResults().get("smart");
            naiveSum += simulator.getResults().get("naive");
            smartCount += simulator.getResults().get("chl count smart");
            naiveCount += simulator.getResults().get("chl count naive");
        }
        System.out.println("smart avg: "+smartSum/numSims);
        System.out.println("naive avg: "+naiveSum/numSims);
        System.out.println("smartCount avg: "+smartCount/numSims);
        System.out.println("naiveCount avg: "+naiveCount/numSims);
    }
}
