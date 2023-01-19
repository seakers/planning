import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.planning.EqualSimulator;
import seakers.planning.PlannerCoverageMetrics;
import seakers.planning.Simulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlannerExecCoverage {
    public static void main(String[] args) {
        double maxTorque = 4e-1;
        for(int i = 0; i < 1; i++) {
            Map<String,String> settings = new HashMap<>();
            settings.put("crosslinkEnabled","true");
            settings.put("downlinkEnabled","true");
            settings.put("downlinkSpeedMbps","1000.1");
            settings.put("cameraOnPower","0.0");
            settings.put("chargePower","5.0");
            settings.put("downlinkOnPower","0.0");
            settings.put("crosslinkOnPower","0.0");
            settings.put("chlBonusReward","100.0");
            settings.put("maxTorque",Double.toString(maxTorque));
            settings.put("planner","greedy_coverage");
            settings.put("resources","false");
            String filepath = "./src/test/resources/plannerData/sevendays_sixteensats_30deg";
            System.out.println("====================================================");
            System.out.println("Max torque: "+maxTorque);
            EqualSimulator simulator = new EqualSimulator(settings,filepath);
            Map<String,Map<GeodeticPoint, ArrayList<TimeIntervalArray>>> plannerAccesses = simulator.getPlannerAccesses();
            PlannerCoverageMetrics pcm = new PlannerCoverageMetrics(filepath,plannerAccesses);
            maxTorque *= 10;
        }
    }
}
