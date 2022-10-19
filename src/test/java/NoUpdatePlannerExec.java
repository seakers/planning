import seakers.planning.NoUpdateSimulator;
import seakers.planning.Simulator;

import java.util.HashMap;
import java.util.Map;

public class NoUpdatePlannerExec {
    public static void main(String[] args) {
        Map<String,String> settings = new HashMap<>();
        settings.put("crosslinkEnabled","false");
        settings.put("downlinkEnabled","true");
        settings.put("downlinkSpeedMbps","10.0");
        settings.put("cameraOnPower","500.0");
        settings.put("chargePower","1.0");
        settings.put("downlinkOnPower","5.0");
        settings.put("crosslinkOnPower","0.0");
        settings.put("chlBonusReward","100.0");
        settings.put("maxTorque","4e-6");
        settings.put("planner","stupidRuleBased");
        double naiveSum = 0;
        double naiveCount = 0;
        int numSims = 1;
        for (int i = 0; i < numSims; i++) {
            NoUpdateSimulator simulator = new NoUpdateSimulator(settings);
            naiveSum += simulator.getResults().get("naive");
            naiveCount += simulator.getResults().get("chl count naive");
        }
        System.out.println("naive avg: "+naiveSum/numSims);
        System.out.println("naiveCount avg: "+naiveCount/numSims);
    }
}
